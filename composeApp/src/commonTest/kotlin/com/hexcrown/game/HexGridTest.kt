package com.hexcrown.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexGridTest {
    private val step = 0.1f
    private val away = -5000f

    private fun HexGrid.run(seconds: Float, enemyOn: Hex? = null, kingOn: Hex? = null): Int {
        var coins = 0
        repeat((seconds / step + 0.5f).toInt()) {
            if (enemyOn != null) markEnemy(enemyOn.x, enemyOn.y)
            coins += update(step, kingOn?.x ?: away, kingOn?.y ?: away, true)
        }
        return coins
    }

    @Test fun pixelsRoundTrip() {
        val g = HexGrid()
        for (q in -9..14) for (r in -2..18) {
            val h = Hex.of(q, r)
            assertEquals(h.key, g.hexAt(h.x, h.y).key)
            assertEquals(h.key, g.hexAt(h.x + 60f, h.y + 30f).key, "still inside the hex")
        }
    }

    @Test fun plantClaimsAFlowerAndPaysIncome() {
        val g = HexGrid()
        val c = g.hexAt(1000f, 1650f)
        g.plant(c)
        assertEquals(7, g.count)
        assertTrue(g.hasBanner(c))
        assertEquals(HEX_OURS, g.state(c))
        assertEquals(DEPLOY_MS * 7, g.unfolding(Hex.of(c.q + DQ_LAST.first, c.r + DQ_LAST.second)))
        assertEquals(7, g.run(INCOME_EVERY + step)) // +1 coin per hex every 4 s (one step of margin: the timer sums floats)
        assertEquals(0, g.unfolding(c))
    }

    @Test fun enemyBurnsKingExtinguishesThenItFalls() {
        val g = HexGrid()
        val c = Hex.of(5, 5)
        g.plant(c, animated = false)
        g.run(BURN_AFTER - 0.5f, enemyOn = c)
        assertEquals(HEX_OURS, g.state(c))
        g.run(1f, kingOn = null) // the enemy left: the count starts again
        g.run(BURN_AFTER + 0.2f, enemyOn = c)
        assertEquals(HEX_BURNT, g.state(c))
        assertEquals(6, g.run(INCOME_EVERY)) // no income from the burnt hex
        g.run(EXTINGUISH_IN + 0.2f, kingOn = c)
        assertEquals(HEX_OURS, g.state(c))
        g.run(BURN_AFTER + 0.2f, enemyOn = c)
        assertEquals(HEX_BURNT, g.state(c))
        g.run(BURNT_LOST_AFTER + 0.2f)
        assertEquals(HEX_NEUTRAL, g.state(c))
        assertEquals(6, g.count)
    }

    @Test fun saveAndLoadKeepStatesAndBanners() {
        val g = HexGrid()
        val c = Hex.of(3, 3)
        g.plant(c, animated = false)
        g.run(BURN_AFTER + 0.2f, enemyOn = c)
        val sb = StringBuilder(); g.save(sb)
        val h = HexGrid(); h.load(sb.toString())
        assertEquals(7, h.count)
        assertEquals(HEX_BURNT, h.state(c))
        assertTrue(h.hasBanner(c))
        h.load("")
        assertEquals(0, h.count)
    }

    private companion object { val DQ_LAST = 0 to 1 }
}
