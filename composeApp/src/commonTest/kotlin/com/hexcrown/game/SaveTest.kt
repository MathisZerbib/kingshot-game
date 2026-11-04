package com.hexcrown.game

import kotlin.test.Test
import kotlin.test.assertEquals

class SaveTest {
    @Test fun damagedSaveFallsBackOnThePreviousOne() {
        val g = Game()
        g.newGame(CLASS_WARRIOR)
        g.onBackground()
        val good = Storage.loadGame()!!
        val kept = good.split(";").toMutableList().also { it[1] = "3" }.joinToString(";")
        Storage.saveText("game_prev", kept)
        Storage.saveGame("v1;" + "x;".repeat(20)) // the shape of a save, unreadable
        val h = Game()
        h.start()
        assertEquals(3, h.level)
        Storage.saveGame(null); Storage.saveText("game_prev", null)
    }
}
