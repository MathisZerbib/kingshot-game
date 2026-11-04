package com.hexcrown.game

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmTest {
    @Test fun sameSeedSameWorld() {
        val a = Realm(42); val b = Realm(42)
        assertContentEquals(a.blocked, b.blocked)
        assertEquals(a.camps.map { it.x }, b.camps.map { it.x })
        assertEquals(a.villages.map { it.y }, b.villages.map { it.y })
        assertTrue(!Realm(43).blocked.contentEquals(a.blocked) || Realm(43).camps[0].x != a.camps[0].x)
    }

    @Test fun contentCounts() {
        for (seed in 0..30) {
            val m = Realm(seed)
            assertEquals(12, m.camps.size, "seed $seed")
            assertEquals(7, m.camps.count { it.main })
            assertEquals(5, m.villages.size)
            assertEquals(4, m.bosses.size)
            for (b in m.bosses) { assertTrue(b.campA < m.camps.size && b.campB < m.camps.size) }
        }
    }

    @Test fun everythingCanBeReachedFromHome() {
        for (seed in 0..30) {
            val m = Realm(seed)
            val grid = HexGrid()
            grid.plant(grid.hexAt(World.CASTLE_X, World.CASTLE_Y), animated = false)
            Flow.compute(grid, m.blocked)
            val out = FloatArray(2)
            for (c in m.camps) assertTrue(Flow.dir(grid, c.x, c.y, out), "seed $seed camp ${c.x},${c.y} cut off")
            for (v in m.villages) assertTrue(Flow.dir(grid, v.x, v.y, out), "seed $seed village cut off")
        }
    }

    @Test fun flowLeadsToTheBanner() {
        val m = Realm(7)
        val grid = HexGrid()
        grid.plant(grid.hexAt(World.CASTLE_X, World.CASTLE_Y), animated = false)
        Flow.compute(grid, m.blocked)
        val out = FloatArray(2)
        var x = m.camps[0].x; var y = m.camps[0].y
        var steps = 0
        while (Flow.dir(grid, x, y, out) && steps++ < 20000) {
            x += out[0] * 20f; y += out[1] * 20f
            assertEquals(BLOCK_NONE, m.blocked[grid.hexAt(x, y).index], "walked into an obstacle at $x,$y")
        }
        assertEquals(grid.hexAt(World.CASTLE_X, World.CASTLE_Y).key, grid.hexAt(x, y).key)
    }

    @Test fun roadsAndDecorKeepClear() {
        for (seed in 0..10) {
            val m = Realm(seed)
            assertTrue(m.roads.size >= 20, "seed $seed roads ${m.roads.size}")
            val grid = HexGrid()
            for (r in m.roads) { var i = 0; while (i < r.size) { assertEquals(BLOCK_NONE, m.blocked[grid.hexAt(r[i], r[i + 1]).index], "road on an obstacle"); i += 2 } }
            for (c in m.chunks) {
                assertEquals(6, c.homes.size)
                assertEquals(0, c.mountains.size % 3, "mountain records are x, y, kind")
                var i = 0
                while (i < c.trees.size) { assertEquals(BLOCK_NONE, m.blocked[grid.hexAt(c.trees[i], c.trees[i + 1]).index]); i += 3 }
            }
            assertTrue(m.chunks.sumOf { it.mountains.size } > 0, "seams")
        }
    }

    @Test fun saveRoundTrip() {
        val m = Realm(9)
        m.crowned[2] = true; m.camps[3].garrison = 4; m.camps[3].alive = 2; m.camps[1].cleared = true; m.villages[0].rebuilt = true; m.bosses[1].dead = true
        m.reveal(Hex.of(10, 10), 3)
        val grid = HexGrid(); grid.plant(Hex.of(10, 10), animated = false); grid.plant(Hex.of(30, 40), animated = false)
        val w = VarWriter(); m.packTo(w); grid.packTo(w)
        val r = VarReader(w.bytes())
        val n = assertNotNull(Realm.unpack(r))
        assertEquals(9, n.seed); assertTrue(n.crowned[2]); assertEquals(4, n.camps[3].garrison); assertTrue(n.camps[1].cleared)
        assertTrue(n.villages[0].rebuilt); assertTrue(n.bosses[1].dead); assertContentEquals(m.explored, n.explored)
        val g2 = HexGrid(); g2.unpackFrom(r)
        assertEquals(grid.count, g2.count)
        assertEquals(HEX_OURS, g2.state(Hex.of(30, 40))); assertTrue(g2.hasBanner(Hex.of(30, 40)))
        assertTrue(w.size < 12000, "save stays small: ${w.size}")
    }
}
