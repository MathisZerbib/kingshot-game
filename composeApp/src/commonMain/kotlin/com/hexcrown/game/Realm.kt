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
const val REALM_VERSION = 2

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
class RealmChunk(
    val x: Float, val y: Float, val trees: FloatArray, val rocks: FloatArray, val props: FloatArray,
    /** x, y, 0 mountain / 1 hills: the seams between regions. */
    val mountains: FloatArray,
    /** x, y: red houses around the main camp. */
    val homes: FloatArray,
    /** x, y, 0-1 tuft / 2-3 flower: flat decor. */
    val flats: FloatArray,
) {
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
    /** Polylines (x, y, x, y...) along the walkable paths between home and the camps, and between neighbouring camps. */
    val roads: Array<FloatArray>
    private val roadHex = BooleanArray(HEX_SPAN * HEX_SPAN)
    private val grid = HexGrid() // only for its pixel-to-hex maths

    init {
        val rnd = Random(seed)
        var lx = 0f; var ly = 0f; var hx = 2000f; var hy = 2400f
        val campList = ArrayList<RealmCamp>()
        val villageList = ArrayList<RealmVillage>()
        val treeLists = List(REGIONS) { ArrayList<Float>() }; val rockLists = List(REGIONS) { ArrayList<Float>() }
        val propLists = List(REGIONS) { ArrayList<Float>() }; val mountainLists = List(REGIONS) { ArrayList<Float>() }
        val homeLists = List(REGIONS) { ArrayList<Float>() }; val flatLists = List(REGIONS) { ArrayList<Float>() }
        val spin = rnd.nextFloat() * 2f * PI.toFloat()
        for (i in 0 until REGIONS) {
            val a = spin + i * 2f * PI.toFloat() / REGIONS
            val dist = 4600f + i * 250f // the further along the ring, the further and the harder
            val cx = World.CASTLE_X + cos(a) * dist
            val cy = World.CASTLE_Y + sin(a) * dist
            centerX[i] = cx; centerY[i] = cy
            lx = minOf(lx, cx - CHUNK_RADIUS); ly = minOf(ly, cy - CHUNK_RADIUS)
            hx = maxOf(hx, cx + CHUNK_RADIUS); hy = maxOf(hy, cy + CHUNK_RADIUS)
            val trees = treeLists[i]; val rocks = rockLists[i]; val props = propLists[i]; val mountains = mountainLists[i]
            campList.add(camp(i, cx, cy, true))
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
        }
        // the ring always holds 12 camps and 5 villages: any shortfall goes to the last regions
        var fill = REGIONS - 1
        while (campList.size < 12) { val a = rnd.nextFloat() * 6.28f; campList.add(camp(fill, centerX[fill] + cos(a) * 700f, centerY[fill] + sin(a) * 700f, false)); fill = if (fill > 2) fill - 1 else REGIONS - 1 }
        fill = 1
        while (villageList.size < 5) { val a = rnd.nextFloat() * 6.28f; villageList.add(RealmVillage(fill, centerX[fill] + cos(a) * 800f, centerY[fill] + sin(a) * 800f, 40 + 20 * fill)); fill = fill % (REGIONS - 1) + 1 }
        camps = campList.toTypedArray(); villages = villageList.toTypedArray()
        minX = lx; minY = ly; maxX = hx; maxY = hy
        // never a wall in front of what the player has to reach
        for (c in camps) clear(c.x, c.y, 2)
        for (v in villages) clear(v.x, v.y, 2)
        for (i in 0 until REGIONS) clear(centerX[i], centerY[i], 2)
        roads = buildRoads()
        buildSeams(mountainLists, spin)
        // the tile props may have landed on a road: drop them, then scatter the rest of the decor around
        for (i in 0 until REGIONS) scatter(i, rnd, treeLists[i], rockLists[i], propLists[i], flatLists[i], homeLists[i])
        chunks = Array(REGIONS) { i ->
            RealmChunk(centerX[i], centerY[i], offRoad(treeLists[i]), offRoad(rockLists[i]), offRoad(propLists[i]), mountainLists[i].toFloatArray(), homeLists[i].toFloatArray(), offRoad(flatLists[i]))
        }
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
        return RealmCamp(region, x, y, main, Spawner(Camp(x, y, kinds, every)), (if (main) 8 else 6) + region * 3)
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
            mountains.add(c.x); mountains.add(c.y); mountains.add(0f)
        }
    }

    private fun buildRoads(): Array<FloatArray> {
        val out = ArrayList<FloatArray>()
        val from = IntArray(HEX_SPAN * HEX_SPAN)
        val queue = IntArray(HEX_SPAN * HEX_SPAN)
        fun road(ax: Float, ay: Float, bx: Float, by: Float) {
            // breadth-first over the walkable hexes, from b back to a
            val start = grid.hexAt(bx, by).index; val goal = grid.hexAt(ax, ay).index
            from.fill(-1)
            var head = 0; var tail = 0
            queue[tail++] = start; from[start] = start
            while (head < tail && from[goal] < 0) {
                val at = queue[head++]
                val q = at / HEX_SPAN; val r = at % HEX_SPAN
                for (d in 0..5) {
                    val nq = q + DQ[d]; val nr = r + DR[d]
                    if (nq < 0 || nr < 0 || nq >= HEX_SPAN || nr >= HEX_SPAN) continue
                    val n = nq * HEX_SPAN + nr
                    if (from[n] >= 0 || blocked[n] != BLOCK_NONE) continue
                    from[n] = at
                    queue[tail++] = n
                }
            }
            if (from[goal] < 0) return
            val pts = ArrayList<Float>()
            var at = goal
            while (true) {
                roadHex[at] = true
                val h = Hex.of(at / HEX_SPAN - HEX_SPAN / 2, at % HEX_SPAN - HEX_SPAN / 2)
                pts.add(h.x); pts.add(h.y)
                if (at == start) break
                at = from[at]
            }
            out.add(pts.toFloatArray())
        }
        val mains = camps.filter { it.main }.sortedBy { it.region }
        for (c in mains) road(World.CASTLE_X, World.CASTLE_Y, c.x, c.y)
        for (k in mains.indices) { val a = mains[k]; val b = mains[(k + 1) % mains.size]; road(a.x, a.y, b.x, b.y) }
        for (c in camps) if (!c.main) { val m = mains[c.region]; road(m.x, m.y, c.x, c.y) }
        for (v in villages) { val m = mains[v.region]; road(m.x, m.y, v.x, v.y) }
        return out.toTypedArray()
    }

    /** Rock lines between neighbouring regions, with a gap wherever a road passes. */
    private fun buildSeams(mountains: List<MutableList<Float>>, spin: Float) {
        for (i in 0 until REGIONS) {
            val a = spin + (i + 0.5f) * 2f * PI.toFloat() / REGIONS
            var d = 2700f
            var alternate = false
            while (d < 7400f) {
                val h = grid.hexAt(World.CASTLE_X + cos(a) * d, World.CASTLE_Y + sin(a) * d)
                d += 150f
                if (nearRoad(h) || nearPlace(h)) continue
                if (blocked[h.index] != BLOCK_NONE) continue
                blocked[h.index] = BLOCK_ROCK
                mountains[i].add(h.x); mountains[i].add(h.y); mountains[i].add(if (alternate) 1f else 0f)
                alternate = !alternate
            }
        }
    }

    private fun nearRoad(h: Hex): Boolean {
        if (roadHex[h.index]) return true
        for (k in 0..5) if (roadHex[Hex.of(h.q + DQ[k], h.r + DR[k]).index]) return true
        return false
    }

    private fun nearPlace(h: Hex): Boolean {
        for (c in camps) if (hypot(c.x - h.x, c.y - h.y) < 420f) return true
        for (v in villages) if (hypot(v.x - h.x, v.y - h.y) < 420f) return true
        return false
    }

    private fun scatter(i: Int, rnd: Random, trees: MutableList<Float>, rocks: MutableList<Float>, props: MutableList<Float>, flats: MutableList<Float>, homes: MutableList<Float>) {
        val cx = centerX[i]; val cy = centerY[i]
        val density = REGION_DENSITY[i]
        fun spot(): FloatArray { val a = rnd.nextFloat() * 6.28f; val r = 240f + rnd.nextFloat() * (CHUNK_RADIUS - 240f); return floatArrayOf(cx + cos(a) * r, cy + sin(a) * r) }
        repeat((26 * density).toInt()) { // groves of 3-7
            val p = spot()
            repeat(3 + rnd.nextInt(5)) { trees.add(p[0] + (rnd.nextFloat() - 0.5f) * 220f); trees.add(p[1] + (rnd.nextFloat() - 0.5f) * 160f); trees.add(0.7f + rnd.nextFloat() * 0.6f) }
        }
        repeat((28 * density).toInt()) { val p = spot(); rocks.add(p[0]); rocks.add(p[1]); rocks.add(0.5f + rnd.nextFloat() * 0.7f) }
        repeat((30 * density).toInt()) { val p = spot(); props.add(p[0]); props.add(p[1]); props.add(if (rnd.nextBoolean()) PROP_STUMP else PROP_BUSH) }
        repeat((320 * density).toInt()) { val p = spot(); flats.add(p[0]); flats.add(p[1]); flats.add(rnd.nextInt(4).toFloat()) }
        repeat(3) { k -> // red houses around the main camp
            val a0 = rnd.nextFloat() * 0.6f + k * 2.1f
            // turn around the camp until the house clears the road; a walled-in camp keeps the first spot
            val a = (0 until 8).map { a0 + it * 0.785f }.firstOrNull { an ->
                val h = grid.hexAt(cx + cos(an) * 360f, cy + sin(an) * 260f)
                !roadHex[h.index] && blocked[h.index] == BLOCK_NONE
            } ?: a0
            homes.add(cx + cos(a) * 360f); homes.add(cy + sin(a) * 260f)
        }
    }

    /** Keeps decor off the roads, off the camps and off blocked hexes. */
    private fun offRoad(l: List<Float>): FloatArray {
        val out = ArrayList<Float>(l.size)
        var i = 0
        while (i + 2 < l.size) {
            val x = l[i]; val y = l[i + 1]
            val h = grid.hexAt(x, y)
            if (!roadHex[h.index] && blocked[h.index] == BLOCK_NONE && !nearCamp(x, y)) { out.add(x); out.add(y); out.add(l[i + 2]) }
            i += 3
        }
        return out.toFloatArray()
    }

    private fun nearCamp(x: Float, y: Float): Boolean {
        for (c in camps) if (hypot(c.x - x, c.y - y) < 240f) return true
        for (v in villages) if (hypot(v.x - x, v.y - y) < 200f) return true
        return false
    }

    /** Nearest region for a point, or -1 on the home map (which keeps its own ground). */
    fun regionAt(x: Float, y: Float): Int {
        if (hypot(x - World.CASTLE_X, y - World.CASTLE_Y) < 2100f) return -1
        var best = 0; var bd = Float.MAX_VALUE
        for (i in 0 until REGIONS) { val dx = x - centerX[i]; val dy = y - centerY[i]; val d = dx * dx + dy * dy; if (d < bd) { bd = d; best = i } }
        return best
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
        for (c in camps) { w.int(c.garrison); w.bool(c.cleared) } // those on the field are saved one by one by Game
        for (v in villages) w.bool(v.rebuilt)
        for (b in bosses) { w.bool(b.dead); w.bool(b.towardsB) }
        var acc = 0; var bits = 0
        for (e in explored) { if (e) acc = acc or (1 shl bits); if (++bits == 8) { w.byte(acc); acc = 0; bits = 0 } }
        if (bits > 0) w.byte(acc)
    }

    companion object {
        private val REGION_DENSITY = floatArrayOf(1f, 1.2f, 0.7f, 1.5f, 1f, 0.5f, 0.4f)
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
            for (b in m.bosses) { b.dead = r.bool(); b.towardsB = r.bool() }
            var acc = 0; var bits = 0
            for (i in m.explored.indices) { if (bits == 0) { acc = r.byte(); bits = 8 }; m.explored[i] = acc and 1 != 0; acc = acc ushr 1; bits-- }
            m.fogVersion++
            return m
        }
    }
}
