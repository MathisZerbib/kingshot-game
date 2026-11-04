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

    @Test fun anErasedRealmStaysErased() {
        val g = Game()
        Storage.saveText("realm", null); Storage.saveText("realm_prev", null)
        g.startRealm(CLASS_WARRIOR)
        g.realm!!.crowned[0] = true
        g.onBackground()
        Storage.saveText("realm_prev", Storage.loadText("realm"))
        Storage.saveText("realm", null)
        val fresh = Game()
        fresh.startRealm(CLASS_WARRIOR)
        assertEquals(false, fresh.realm!!.crowned[0])
        Storage.saveText("realm", "abimee")
        val back = Game()
        back.startRealm(CLASS_WARRIOR)
        assertEquals(true, back.realm!!.crowned[0])
        Storage.saveText("realm", null); Storage.saveText("realm_prev", null)
    }
}
