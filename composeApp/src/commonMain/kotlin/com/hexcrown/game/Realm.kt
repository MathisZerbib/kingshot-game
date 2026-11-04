package com.hexcrown.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// « Royaume » (docs/HEXCROWN.md §4): the home map of level 1 in the middle, 7 generated regions in a ring around it.

const val REGIONS = 7
const val BLOCK_NONE: Byte = 0
const val BLOCK_WATER: Byte = 1
const val BLOCK_ROCK: Byte = 2
const val CHUNK_RADIUS = 2000f // 12 hexes
const val REALM_VERSION = 1

/** Varints in a growing buffer: the realm save. */
class VarWriter {
    private var b = ByteArray(4096)
    var size = 0; private set
    fun byte(v: Int) { if (size == b.size) b = b.copyOf(size * 2); b[size++] = v.toByte() }
    fun int(v: Int) { var x = (v shl 1) xor (v shr 31); while (x and 0x7F.inv() != 0) { byte((x and 0x7F) or 0x80); x = x ushr 7 }; byte(x) }
    fun bool(v: Boolean) = byte(if (v) 1 else 0)
    fun bytes() = b.copyOf(size)
}

class VarReader(private val b: ByteArray) {
    private var p = 0
    fun byte() = b[p++].toInt() and 255
    fun int(): Int { var r = 0; var s = 0; while (true) { val c = byte(); r = r or ((c and 0x7F) shl s); if (c and 0x80 == 0) break; s += 7 }; return (r ushr 1) xor -(r and 1) }
    fun bool() = byte() != 0
}

/** Decor of one region: same flat formats as World.trees / rocks / props, drawn only while the chunk is active. */
class RealmChunk(val x: Float, val y: Float, val trees: FloatArray, val rocks: FloatArray, val props: FloatArray, val mountains: FloatArray) {
    var active = false
}

class RealmCamp(val region: Int, val x: Float, val y: Float, val main: Boolean, val spawner: Spawner, var garrison: Int) {
    var alive = 0 // emitted and still standing
    var cleared = false // garrison dead and our banner on it: a friendly ruin
}

class RealmVillage(val region: Int, val x: Float, val y: Float, val cost: Int) {
    var rebuilt = false
    var timer = 0f
}

class RealmBoss(val nameKey: String, val bossType: Int, val campA: Int, val campB: Int) {
    var dead = false
    var enemy = -1 // pool index while on the field
    var gen = 0
    var towardsB = true
}

class Realm(val seed: Int) {
    val centerX = FloatArray(REGIONS)
    val centerY = FloatArray(REGIONS)
    val crowned = BooleanArray(REGIONS)
    val blocked = ByteArray(HEX_SPAN * HEX_SPAN)
    val explored = BooleanArray(HEX_SPAN * HEX_SPAN)
    /** Bumped when the fog or the water to draw changes. */
    var fogVersion = 0
    val chunks: Array<RealmChunk>
    val camps: Array<RealmCamp>
    val villages: Array<RealmVillage>
    val bosses: Array<RealmBoss>
    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float
    private val grid = HexGrid() // only for its pixel-to-hex maths

    init {
        val rnd = Random(seed)
        var lx = 0f; var ly = 0f; var hx = 2000f; var hy = 2400f
        val campList = ArrayList<RealmCamp>()
        val villageList = ArrayList<RealmVillage>()
        val chunkList = ArrayList<RealmChunk>()
        val spin = rnd.nextFloat() * 2f * PI.toFloat()
        for (i in 0 until REGIONS) {
            val a = spin + i * 2f * PI.toFloat() / REGIONS
            val dist = 4600f + i * 250f // the further along the ring, the further and the harder
            val cx = World.CASTLE_X + cos(a) * dist
            val cy = World.CASTLE_Y + sin(a) * dist
            centerX[i] = cx; centerY[i] = cy
            lx = minOf(lx, cx - CHUNK_RADIUS); ly = minOf(ly, cy - CHUNK_RADIUS)
            hx = maxOf(hx, cx + CHUNK_RADIUS); hy = maxOf(hy, cy + CHUNK_RADIUS)
            val trees = ArrayList<Float>(); val rocks = ArrayList<Float>(); val props = ArrayList<Float>(); val mountains = ArrayList<Float>()
            campList.add(camp(i, cx, cy, true))
            // sparse trees and rocks everywhere, away from the camp, so no region feels bare
            repeat(45) {
                val sa = rnd.nextFloat() * 6.28f; val sr = 420f + rnd.nextFloat() * (CHUNK_RADIUS - 420f)
                if (it % 4 == 0) { rocks.add(cx + cos(sa) * sr); rocks.add(cy + sin(sa) * sr); rocks.add(0.6f + rnd.nextFloat() * 0.6f) }
                else { trees.add(cx + cos(sa) * sr); trees.add(cy + sin(sa) * sr); trees.add(0.7f + rnd.nextFloat() * 0.5f) }
            }
            // 6 tiles around the main camp; each keeps away from its neighbours, so any two fit side by side
            val order = TILES.toMutableList().also { it.shuffle(rnd) }
            for (k in 0 until 6) {
                val ta = a + k * PI.toFloat() / 3f
                val tx = cx + cos(ta) * 1250f
                val ty = cy + sin(ta) * 1250f
                when (order[k]) {
                    TILE_FOREST -> forest(tx, ty, rnd, trees)
                    TILE_RIVER -> river(tx, ty, rnd)
                    TILE_RUINS -> ruins(tx, ty, rnd, rocks, props)
                    TILE_VILLAGE -> if (villageList.size < 5 && i >= 1) villageList.add(RealmVillage(i, tx, ty, 40 + 20 * i)) else forest(tx, ty, rnd, trees)
                    TILE_CAMP -> if (campList.size < 12 && i >= 2) campList.add(camp(i, tx, ty, false)) else ruins(tx, ty, rnd, rocks, props)
                    else -> pass(tx, ty, rnd, mountains)
                }
            }
            chunkList.add(RealmChunk(cx, cy, trees.toFloatArray(), rocks.toFloatArray(), props.toFloatArray(), mountains.toFloatArray()))
        }
        // the ring always holds 12 camps and 5 villages: any shortfall goes to the last regions
        var fill = REGIONS - 1
        while (campList.size < 12) { val a = rnd.nextFloat() * 6.28f; campList.add(camp(fill, centerX[fill] + cos(a) * 700f, centerY[fill] + sin(a) * 700f, false)); fill = if (fill > 2) fill - 1 else REGIONS - 1 }
        fill = 1
        while (villageList.size < 5) { val a = rnd.nextFloat() * 6.28f; villageList.add(RealmVillage(fill, centerX[fill] + cos(a) * 800f, centerY[fill] + sin(a) * 800f, 40 + 20 * fill)); fill = fill % (REGIONS - 1) + 1 }
        camps = campList.toTypedArray(); villages = villageList.toTypedArray(); chunks = chunkList.toTypedArray()
        minX = lx; minY = ly; maxX = hx; maxY = hy
        // never a wall in front of what the player has to reach
        for (c in camps) clear(c.x, c.y, 2)
        for (v in villages) clear(v.x, v.y, 2)
        for (i in 0 until REGIONS) clear(centerX[i], centerY[i], 2)
        bosses = arrayOf(
            RealmBoss("boss.0", 0, 1, 2), RealmBoss("boss.1", 1, 3, 4), RealmBoss("boss.2", 1, 5, 6), RealmBoss("boss.3", 2, 7, 8),
        )
    }

    private fun camp(region: Int, x: Float, y: Float, main: Boolean): RealmCamp {
        val kinds = when {
            region <= 1 -> intArrayOf(KIND_SOLDIER, KIND_SOLDIER, KIND_WOLF)
            region <= 3 -> intArrayOf(KIND_SOLDIER, KIND_WOLF, KIND_ARCHER, KIND_BRUTE)
            else -> intArrayOf(KIND_SOLDIER, KIND_ARCHER, KIND_SHIELD, KIND_BRUTE, KIND_WOLF)
        }
        val every = (5f - region * 0.45f).coerceAtLeast(2f)
        return RealmCamp(region, x, y, main, Spawner(Camp(x, y, kinds, every)), (if (main) 14 else 8) + region * 3)
    }

    private fun setBlock(x: Float, y: Float, v: Byte) { blocked[grid.hexAt(x, y).index] = v }

    private fun clear(x: Float, y: Float, radius: Int) {
        val c = grid.hexAt(x, y)
        for (dq in -radius..radius) for (dr in -radius..radius) {
            if (kotlin.math.abs(dq + dr) > radius) continue
            blocked[Hex.of(c.q + dq, c.r + dr).index] = BLOCK_NONE
        }
    }

    private fun forest(x: Float, y: Float, rnd: Random, trees: MutableList<Float>) {
        repeat(26) {
            val a = rnd.nextFloat() * 6.28f; val r = 60f + rnd.nextFloat() * 500f
            trees.add(x + cos(a) * r); trees.add(y + sin(a) * r * 0.8f); trees.add(0.8f + rnd.nextFloat() * 0.5f)
        }
    }

    /** A short stretch of water with one ford in the middle. */
    private fun river(x: Float, y: Float, rnd: Random) {
        val horizontal = rnd.nextBoolean()
        for (k in -3..3) {
            if (k == 0) continue
            val px = x + (if (horizontal) k * 166f else 0f); val py = y + (if (horizontal) 0f else k * 144f)
            setBlock(px, py, BLOCK_WATER)
        }
    }

    private fun ruins(x: Float, y: Float, rnd: Random, rocks: MutableList<Float>, props: MutableList<Float>) {
        repeat(8) {
            val a = rnd.nextFloat() * 6.28f; val r = 40f + rnd.nextFloat() * 420f
            rocks.add(x + cos(a) * r); rocks.add(y + sin(a) * r * 0.8f); rocks.add(0.7f + rnd.nextFloat() * 0.7f)
        }
        repeat(5) {
            val a = rnd.nextFloat() * 6.28f; val r = 40f + rnd.nextFloat() * 380f
            props.add(x + cos(a) * r); props.add(y + sin(a) * r * 0.8f); props.add(PROP_STUMP)
        }
    }

    /** Two rock walls and a gap between them. */
    private fun pass(x: Float, y: Float, rnd: Random, mountains: MutableList<Float>) {
        val horizontal = rnd.nextBoolean()
        for (k in -3..3) {
            if (k == -1 || k == 0 || k == 1) continue
            val px = x + (if (horizontal) k * 166f else 0f); val py = y + (if (horizontal) 0f else k * 144f)
            setBlock(px, py, BLOCK_ROCK)
            val c = grid.hexAt(px, py)
            mountains.add(c.x); mountains.add(c.y)
        }
    }

    /** Lifts the fog around a hex; true when something new appeared. */
    fun reveal(h: Hex, radius: Int) {
        var changed = false
        for (dq in -radius..radius) for (dr in -radius..radius) {
            if (kotlin.math.abs(dq + dr) > radius) continue
            val i = Hex.of(h.q + dq, h.r + dr).index
            if (!explored[i]) { explored[i] = true; changed = true }
        }
        if (changed) fogVersion++
    }

    /** Marks the chunks within reach of the camera; what is outside is neither updated nor drawn. */
    fun updateActive(x: Float, y: Float, reach: Float) {
        for (c in chunks) c.active = hypot(c.x - x, c.y - y) <= CHUNK_RADIUS + reach
    }

    /** The home map counts as active when the king is near it; a point outside any chunk (roads between regions) follows the king's distance. */
    fun isActive(px: Float, py: Float, kingX: Float, kingY: Float, reach: Float) = hypot(px - kingX, py - kingY) <= CHUNK_RADIUS + reach

    fun packTo(w: VarWriter) {
        w.int(REALM_VERSION); w.int(seed)
        for (c in crowned) w.bool(c)
        for (c in camps) { w.int(c.garrison + c.alive); w.bool(c.cleared) } // what stood on the field goes home: enemies are not saved one by one
        for (v in villages) w.bool(v.rebuilt)
        for (b in bosses) w.bool(b.dead)
        var acc = 0; var bits = 0
        for (e in explored) { if (e) acc = acc or (1 shl bits); if (++bits == 8) { w.byte(acc); acc = 0; bits = 0 } }
        if (bits > 0) w.byte(acc)
    }

    companion object {
        private const val TILE_FOREST = 0
        private const val TILE_RIVER = 1
        private const val TILE_RUINS = 2
        private const val TILE_VILLAGE = 3
        private const val TILE_CAMP = 4
        private const val TILE_PASS = 5
        private val TILES = listOf(TILE_FOREST, TILE_RIVER, TILE_RUINS, TILE_VILLAGE, TILE_CAMP, TILE_PASS)

        /** Rebuilds the map from the seed, then applies the saved state. Null on an unknown version. */
        fun unpack(r: VarReader): Realm? {
            if (r.int() != REALM_VERSION) return null
            val m = Realm(r.int())
            for (i in m.crowned.indices) m.crowned[i] = r.bool()
            for (c in m.camps) { c.garrison = r.int(); c.cleared = r.bool() }
            for (v in m.villages) v.rebuilt = r.bool()
            for (b in m.bosses) b.dead = r.bool()
            var acc = 0; var bits = 0
            for (i in m.explored.indices) { if (bits == 0) { acc = r.byte(); bits = 8 }; m.explored[i] = acc and 1 != 0; acc = acc ushr 1; bits-- }
            m.fogVersion++
            return m
        }
    }
}
