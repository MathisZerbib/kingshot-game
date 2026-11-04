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
const val HEX_SPAN = CHUNK * TABLE // hexes per side of the whole table
private const val OFFSET = CHUNK * TABLE / 2
private const val MAX_HEX = 1024
private val SQRT3 = sqrt(3f)
val DQ = intArrayOf(1, 1, 0, -1, -1, 0)
val DR = intArrayOf(0, -1, -1, 0, 1, 1)

/** A hex position, packed so it never allocates. */
@kotlin.jvm.JvmInline
value class Hex(val key: Int) {
    val q get() = (key shr 16) - OFFSET
    val r get() = (key and 0xFFFF) - OFFSET
    val x get() = HEX_R * SQRT3 * (q + r / 2f)
    val y get() = HEX_R * 1.5f * r
    /** Position in a flat HEX_SPAN x HEX_SPAN array. */
    val index get() = (key shr 16) * HEX_SPAN + (key and 0xFFFF)
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
    /** Bumped when the set of standing banners changes: the flow field only depends on that. */
    var bannerVersion = 0; private set
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
        count = 0; incomeAcc = 0f; version++; bannerVersion++
    }

    private fun claim(h: Hex, banner: Boolean, deployMs: Int) {
        val c = cell(h)
        if (c and 3 == HEX_NEUTRAL) { if (count >= MAX_HEX) return; list[count++] = h.key }
        val keepBanner = c and 4
        store(h, HEX_OURS or (if (banner) 4 else keepBanner) or (deployMs shl 19))
    }

    /** The king has held this neutral hex: it is ours, and a banner stands in the middle of any full group of 7 around it. */
    fun claimOne(h: Hex) {
        claim(h, false, DEPLOY_MS)
        autoBanner(h)
        for (i in 0..5) autoBanner(Hex.of(h.q + DQ[i], h.r + DR[i]))
        version++
    }

    private fun autoBanner(c: Hex) {
        if (cell(c) and 3 != HEX_OURS || hasBanner(c)) return
        for (i in 0..5) {
            val n = Hex.of(c.q + DQ[i], c.r + DR[i])
            if (cell(n) and 3 != HEX_OURS || hasBanner(n)) return // not a full flower yet, or one of its hexes already holds a banner
            for (k in 0..5) if (hasBanner(Hex.of(n.q + DQ[k], n.r + DR[k]))) return // groups do not share hexes
        }
        store(c, cell(c) or 4)
        bannerVersion++
    }

    /** Claims the hex and its 6 neighbours; [animated] unfolds them one by one. */
    fun plant(h: Hex, animated: Boolean = true) {
        claim(h, true, if (animated) DEPLOY_MS else 0)
        for (i in 0..5) claim(Hex.of(h.q + DQ[i], h.r + DR[i]), false, if (animated) DEPLOY_MS * (i + 2) else 0)
        version++; bannerVersion++
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
                    burned++; version++; if (banner != 0) bannerVersion++
                } else store(h, HEX_OURS or banner or (a shl 4) or (b shl 19))
            } else {
                b = if (kingAlive && h.key == king) min(0xFFF, b + ms) else 0
                a = min(0x7FFF, a + ms)
                if (b >= (EXTINGUISH_IN * 1000f).toInt()) {
                    store(h, HEX_OURS or banner)
                    extinguished++; version++; if (banner != 0) bannerVersion++
                } else if (a >= (BURNT_LOST_AFTER * 1000f).toInt()) {
                    store(h, 0)
                    list[i] = list[--count]
                    lost++; version++; if (banner != 0) bannerVersion++
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

    /** Bit-packed per chunk for the realm save: chunk count, then for each used chunk its index and 3 bits per cell. */
    fun packTo(w: VarWriter) {
        var used = 0
        for (c in chunks) if (c != null) used++
        w.int(used)
        for (i in chunks.indices) {
            val c = chunks[i] ?: continue
            w.int(i)
            var acc = 0; var bits = 0
            for (cell in c) {
                acc = acc or ((cell and 7) shl bits); bits += 3
                while (bits >= 8) { w.byte(acc and 255); acc = acc ushr 8; bits -= 8 }
            }
            if (bits > 0) w.byte(acc and 255)
        }
    }

    fun unpackFrom(r: VarReader) {
        clear()
        repeat(r.int()) {
            val i = r.int()
            var acc = 0; var bits = 0
            val q = i / TABLE; val rr = i % TABLE
            for (cq in 0 until CHUNK) for (cr in 0 until CHUNK) {
                while (bits < 3) { acc = acc or (r.byte() shl bits); bits += 8 }
                val v = acc and 7; acc = acc ushr 3; bits -= 3
                val st = v and 3
                if (st != HEX_NEUTRAL && count < MAX_HEX) {
                    val h = Hex(((q * CHUNK + cq) shl 16) or (rr * CHUNK + cr))
                    list[count++] = h.key
                    store(h, st or (v and 4))
                }
            }
        }
        version++; bannerVersion++
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
        version++; bannerVersion++
    }
}

/** Flow field: from every hex, the neighbour to walk to in order to reach the nearest of our banners. */
object Flow {
    private const val NONE: Byte = -1
    private val field = ByteArray(HEX_SPAN * HEX_SPAN) { NONE }
    private val queue = IntArray(HEX_SPAN * HEX_SPAN)
    private val dist = ShortArray(HEX_SPAN * HEX_SPAN)
    /** How many banners the last search started from. */
    var sources = 0; private set

    /** Multi-source search from our banners over the walkable hexes. Preallocated; only run when the territory changes. */
    fun compute(grid: HexGrid, blocked: ByteArray) {
        field.fill(NONE)
        dist.fill(-1)
        var head = 0; var tail = 0
        for (i in 0 until grid.count) {
            val h = grid.at(i)
            if (!grid.hasBanner(h) || grid.state(h) != HEX_OURS) continue
            queue[tail++] = h.index
            dist[h.index] = 0
        }
        sources = tail
        while (head < tail) {
            val at = queue[head++]
            val q = at / HEX_SPAN; val r = at % HEX_SPAN
            for (d in 0..5) {
                val nq = q + DQ[d]; val nr = r + DR[d]
                if (nq < 0 || nr < 0 || nq >= HEX_SPAN || nr >= HEX_SPAN) continue
                val n = nq * HEX_SPAN + nr
                if (dist[n] >= 0 || blocked[n] != 0.toByte()) continue
                dist[n] = (dist[at] + 1).toShort()
                field[n] = ((d + 3) % 6).toByte() // from n, walk back towards the source
                queue[tail++] = n
            }
        }
    }

    /** Unit direction at (x, y) written into [out]; false when no banner can be reached (or we stand on one). */
    fun dir(grid: HexGrid, x: Float, y: Float, out: FloatArray): Boolean {
        val h = grid.hexAt(x, y)
        val d = field[h.index].toInt()
        if (d < 0) return false
        val next = Hex.of(h.q + DQ[d], h.r + DR[d])
        val dx = next.x - x; val dy = next.y - y
        val l = max(0.001f, sqrt(dx * dx + dy * dy))
        out[0] = dx / l; out[1] = dy / l
        return true
    }
}

class Camp(val x: Float, val y: Float, val kinds: IntArray, val every: Float)

/** Emits a camp's enemies at its pace. */
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
