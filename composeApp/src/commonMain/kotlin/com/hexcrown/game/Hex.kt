package com.hexcrown.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

// Territory (docs/HEXCROWN.md §2.1 and §4): pointy-top hexes of radius 96, stored as packed ints per chunk.

const val HEX_R = 96f
const val HEX_NEUTRAL = 0
const val HEX_OURS = 1
const val HEX_BURNT = 2
const val BURN_AFTER = 3f // seconds of enemy presence
const val EXTINGUISH_IN = 1.5f
const val BURNT_LOST_AFTER = 20f
const val INCOME_EVERY = 4f
const val DEPLOY_MS = 120 // each hex of the flower unfolds in 120 ms, one after the other

private const val CHUNK = 24
private const val TABLE = 8
private const val OFFSET = CHUNK * TABLE / 2
private const val MAX_HEX = 1024
private val SQRT3 = sqrt(3f)
private val DQ = intArrayOf(1, 1, 0, -1, -1, 0)
private val DR = intArrayOf(0, -1, -1, 0, 1, 1)

/** A hex position, packed so it never allocates. */
@kotlin.jvm.JvmInline
value class Hex(val key: Int) {
    val q get() = (key shr 16) - OFFSET
    val r get() = (key and 0xFFFF) - OFFSET
    val x get() = HEX_R * SQRT3 * (q + r / 2f)
    val y get() = HEX_R * 1.5f * r
    companion object { fun of(q: Int, r: Int) = Hex(((q + OFFSET) shl 16) or (r + OFFSET)) }
}

interface Terrain {
    fun hexAt(x: Float, y: Float): Hex
    fun walkable(x: Float, y: Float): Boolean
}

/**
 * Cell bits: 0-1 state, 2 banner, 3 enemy seen this frame, 4-18 timer A in ms (enemy presence, then burn time),
 * 19-30 timer B in ms (unfolding, then extinguishing).
 */
class HexGrid : Terrain {
    private val chunks = arrayOfNulls<IntArray>(TABLE * TABLE)
    private val list = IntArray(MAX_HEX) // keys of every non-neutral hex
    var count = 0; private set
    /** Bumped when a hex changes state or finishes unfolding: the renderer rebuilds its cached paths. */
    var version = 0; private set
    private var incomeAcc = 0f
    // what the last update did, read by Game for sounds and effects
    var burned = 0; private set
    var extinguished = 0; private set
    var lost = 0; private set

    override fun hexAt(x: Float, y: Float): Hex {
        val fq = (x * SQRT3 / 3f - y / 3f) / HEX_R
        val fr = y * 2f / 3f / HEX_R
        val fs = -fq - fr
        var q = round(fq); var r = round(fr); val s = round(fs)
        val dq = abs(q - fq); val dr = abs(r - fr); val ds = abs(s - fs)
        if (dq > dr && dq > ds) q = -r - s else if (dr > ds) r = -q - s
        return Hex.of(q.toInt(), r.toInt())
    }

    override fun walkable(x: Float, y: Float) = x >= 0f && y >= 0f && x <= World.W && y <= World.H

    private fun cell(h: Hex): Int = chunks[chunkIndex(h)]?.get(cellIndex(h)) ?: 0
    private fun chunkIndex(h: Hex) = ((h.key shr 16) / CHUNK) * TABLE + (h.key and 0xFFFF) / CHUNK
    private fun cellIndex(h: Hex) = ((h.key shr 16) % CHUNK) * CHUNK + (h.key and 0xFFFF) % CHUNK
    private fun store(h: Hex, v: Int) {
        val i = chunkIndex(h)
        val c = chunks[i] ?: IntArray(CHUNK * CHUNK).also { chunks[i] = it } // allocated once per chunk, when first claimed
        c[cellIndex(h)] = v
    }

    fun state(h: Hex) = cell(h) and 3
    fun stateAt(x: Float, y: Float) = state(hexAt(x, y))
    fun hasBanner(h: Hex) = cell(h) and 4 != 0
    /** Milliseconds left before this hex has finished unfolding (0 when done). */
    fun unfolding(h: Hex): Int { val c = cell(h); return if (c and 3 == HEX_OURS) c ushr 19 else 0 }
    fun at(i: Int) = Hex(list[i])
    val owned: Int get() { var n = 0; for (i in 0 until count) if (state(Hex(list[i])) == HEX_OURS) n++; return n }

    fun clear() {
        for (i in 0 until count) store(Hex(list[i]), 0)
        count = 0; incomeAcc = 0f; version++
    }

    private fun claim(h: Hex, banner: Boolean, deployMs: Int) {
        val c = cell(h)
        if (c and 3 == HEX_NEUTRAL) { if (count >= MAX_HEX) return; list[count++] = h.key }
        val keepBanner = c and 4
        store(h, HEX_OURS or (if (banner) 4 else keepBanner) or (deployMs shl 19))
    }

    /** Claims the hex and its 6 neighbours; [animated] unfolds them one by one. */
    fun plant(h: Hex, animated: Boolean = true) {
        claim(h, true, if (animated) DEPLOY_MS else 0)
        for (i in 0..5) claim(Hex.of(h.q + DQ[i], h.r + DR[i]), false, if (animated) DEPLOY_MS * (i + 2) else 0)
        version++
    }

    /** Call for each active enemy, before [update]. */
    fun markEnemy(x: Float, y: Float) {
        val h = hexAt(x, y)
        val c = cell(h)
        if (c and 3 != HEX_NEUTRAL) store(h, c or 8)
    }

    /** Advances timers; returns the coins earned (+1 per owned hex every 4 s). [burnDelay] lets relics slow the burn. */
    fun update(dt: Float, kingX: Float, kingY: Float, kingAlive: Boolean, burnDelay: Float = BURN_AFTER): Int {
        burned = 0; extinguished = 0; lost = 0
        val ms = (dt * 1000f + 0.5f).toInt()
        val king = hexAt(kingX, kingY).key
        var i = 0
        while (i < count) {
            val h = Hex(list[i])
            val c = cell(h)
            val banner = c and 4
            val seen = c and 8 != 0
            var a = (c ushr 4) and 0x7FFF
            var b = c ushr 19
            if (c and 3 == HEX_OURS) {
                if (b > 0) { b = max(0, b - ms); if (b == 0) version++ }
                a = if (seen) min(0x7FFF, a + ms) else 0
                if (a >= (burnDelay * 1000f).toInt()) {
                    store(h, HEX_BURNT or banner)
                    burned++; version++
                } else store(h, HEX_OURS or banner or (a shl 4) or (b shl 19))
            } else {
                b = if (kingAlive && h.key == king) min(0xFFF, b + ms) else 0
                a = min(0x7FFF, a + ms)
                if (b >= (EXTINGUISH_IN * 1000f).toInt()) {
                    store(h, HEX_OURS or banner)
                    extinguished++; version++
                } else if (a >= (BURNT_LOST_AFTER * 1000f).toInt()) {
                    store(h, 0)
                    list[i] = list[--count]
                    lost++; version++
                    continue
                } else store(h, HEX_BURNT or banner or (a shl 4) or (b shl 19))
            }
            i++
        }
        incomeAcc += dt
        if (incomeAcc < INCOME_EVERY) return 0
        incomeAcc -= INCOME_EVERY
        return owned
    }

    /** "q,r,state,banner" per hex; timers restart on load. */
    fun save(sb: StringBuilder) {
        for (i in 0 until count) {
            val h = Hex(list[i])
            if (i > 0) sb.append('|')
            sb.append(h.q).append(',').append(h.r).append(',').append(state(h)).append(',').append(if (hasBanner(h)) 1 else 0)
        }
    }

    fun load(s: String) {
        clear()
        if (s.isEmpty()) return
        for (part in s.split('|')) {
            val f = part.split(',')
            val h = Hex.of(f[0].toInt(), f[1].toInt())
            if (count >= MAX_HEX || cell(h) and 3 != HEX_NEUTRAL) continue
            list[count++] = h.key
            store(h, f[2].toInt().coerceIn(HEX_OURS, HEX_BURNT) or (if (f[3] == "1") 4 else 0))
        }
        version++
    }
}

/** Direction to walk from (x, y) towards a target, written into [out] (no allocation per call). */
object Flow {
    // shortcut: straight line, upgrade to a per-chunk flow field with the Royaume mode (levels keep their scripted roads)
    fun dir(x: Float, y: Float, tx: Float, ty: Float, out: FloatArray) {
        val dx = tx - x; val dy = ty - y
        val l = max(0.001f, sqrt(dx * dx + dy * dy))
        out[0] = dx / l; out[1] = dy / l
    }
}

class Camp(val x: Float, val y: Float, val kinds: IntArray, val every: Float)

/** Emits a camp's enemies at its pace. shortcut: interface only for now, the levels still spawn through Game.updateWaves, upgrade with the Royaume mode. */
class Spawner(val camp: Camp) {
    private var t = 0f
    private var next = 0
    /** Returns the enemy kind to spawn now, or -1. */
    fun update(dt: Float): Int {
        t += dt
        if (t < camp.every || camp.kinds.isEmpty()) return -1
        t -= camp.every
        return camp.kinds[next++ % camp.kinds.size]
    }
}
