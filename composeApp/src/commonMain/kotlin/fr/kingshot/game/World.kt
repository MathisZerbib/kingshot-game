package fr.kingshot.game

import kotlin.math.hypot

/** Mutable 2D vector reused to avoid allocations in the game loop. */
class Vec {
    var x = 0f
    var y = 0f
}

/** Polyline path that enemies follow, sampled by distance travelled. */
class GamePath(pts: FloatArray) {
    val n = pts.size / 2
    val xs = FloatArray(n) { pts[it * 2] }
    val ys = FloatArray(n) { pts[it * 2 + 1] }
    private val cum = FloatArray(n)
    val length: Float

    init {
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        length = cum[n - 1]
    }

    fun sample(s: Float, out: Vec) {
        val ss = s.coerceIn(0f, length)
        var i = 1
        while (i < n - 1 && cum[i] < ss) i++
        val seg = cum[i] - cum[i - 1]
        val t = if (seg > 0f) (ss - cum[i - 1]) / seg else 0f
        out.x = xs[i - 1] + (xs[i] - xs[i - 1]) * t
        out.y = ys[i - 1] + (ys[i] - ys[i - 1]) * t
    }

    /** Unit tangent at distance s. */
    fun tangent(s: Float, out: Vec) {
        val ss = s.coerceIn(0f, length)
        var i = 1
        while (i < n - 1 && cum[i] < ss) i++
        val dx = xs[i] - xs[i - 1]
        val dy = ys[i] - ys[i - 1]
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        out.x = dx / l
        out.y = dy / l
    }
}

/** Static map layout: roads, cliffs, decoration and building spots. */
object World {
    const val W = 2000f
    const val H = 2400f

    const val CASTLE_X = 1000f
    const val CASTLE_Y = 1650f
    const val CASTLE_R = 95f

    const val HOUSE_X = 690f
    const val HOUSE_Y = 1440f

    const val KING_START_X = 820f
    const val KING_START_Y = 1720f

    /** Enemy roads. 0 = north-east, 1 = east, 2 = south. */
    val paths = arrayOf(
        GamePath(floatArrayOf(1720f, -80f, 1580f, 380f, 1270f, 690f, 1160f, 1120f, 1030f, 1545f)),
        GamePath(floatArrayOf(2100f, 1280f, 1720f, 1340f, 1420f, 1560f, 1095f, 1650f)),
        GamePath(floatArrayOf(640f, 2500f, 780f, 2120f, 960f, 1900f, 1000f, 1750f)),
    )

    /** Purely decorative roads (x0,y0,x1,y1...). */
    val decoRoads = arrayOf(
        floatArrayOf(-50f, 1980f, 420f, 1860f, 760f, 1750f, 900f, 1690f),
        floatArrayOf(690f, 1500f, 760f, 1640f, 900f, 1680f),
        floatArrayOf(1160f, 1120f, 1500f, 1180f, 2060f, 1050f),
    )

    /** Cliff blocks: x, y, w, d (ground footprint), height. */
    val cliffs = arrayOf(
        floatArrayOf(-40f, -40f, 760f, 520f, 210f),
        floatArrayOf(-40f, 440f, 430f, 420f, 230f),
        floatArrayOf(600f, -40f, 420f, 300f, 170f),
        floatArrayOf(330f, 470f, 300f, 230f, 150f),
        floatArrayOf(-40f, 820f, 250f, 520f, 200f),
        floatArrayOf(1850f, 1700f, 260f, 400f, 160f),
    )

    /** Towers spots (x,y) next to each road. */
    val towerSpots = floatArrayOf(1330f, 1010f, 1560f, 1600f, 1120f, 2040f)

    /** Palisade location along each road, as a fraction of its length. */
    val wallFractions = floatArrayOf(0.66f, 0.42f, 0.42f)

    val trees: FloatArray
    val rocks: FloatArray
    val grassPatches: FloatArray

    init {
        val rnd = kotlin.random.Random(7)
        val t = ArrayList<Float>()
        var tries = 0
        while (t.size < 46 * 3 && tries < 4000) {
            tries++
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H
            if (!isFreeForDecor(x, y, 120f)) continue
            t.add(x); t.add(y); t.add(0.8f + rnd.nextFloat() * 0.6f)
        }
        trees = t.toFloatArray()
        val r = ArrayList<Float>()
        tries = 0
        while (r.size < 24 * 3 && tries < 4000) {
            tries++
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H
            if (!isFreeForDecor(x, y, 70f)) continue
            r.add(x); r.add(y); r.add(0.6f + rnd.nextFloat() * 0.8f)
        }
        rocks = r.toFloatArray()
        val g = ArrayList<Float>()
        repeat(90) {
            g.add(rnd.nextFloat() * W); g.add(rnd.nextFloat() * H); g.add(60f + rnd.nextFloat() * 140f); g.add(rnd.nextFloat())
        }
        grassPatches = g.toFloatArray()
    }

    fun insideCliff(x: Float, y: Float, margin: Float): Boolean {
        for (c in cliffs) {
            if (x > c[0] - margin && x < c[0] + c[2] + margin && y > c[1] - margin && y < c[1] + c[3] + margin) return true
        }
        return false
    }

    private fun isFreeForDecor(x: Float, y: Float, clearance: Float): Boolean {
        if (insideCliff(x, y, 30f)) return false
        // keep the base area clear
        if (hypot(x - CASTLE_X, y - CASTLE_Y) < 520f) return false
        val v = Vec()
        for (p in paths) {
            var s = 0f
            while (s <= p.length) {
                p.sample(s, v)
                if (hypot(x - v.x, y - v.y) < clearance) return false
                s += 40f
            }
        }
        for (road in decoRoads) {
            var i = 0
            while (i < road.size) {
                if (hypot(x - road[i], y - road[i + 1]) < clearance + 40f) return false
                i += 2
            }
        }
        var i = 0
        while (i < towerSpots.size) {
            if (hypot(x - towerSpots[i], y - towerSpots[i + 1]) < 200f) return false
            i += 2
        }
        return true
    }
}
