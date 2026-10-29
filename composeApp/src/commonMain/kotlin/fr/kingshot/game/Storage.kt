package fr.kingshot.game

/** Tiny persistent store for the best wave reached. */
expect object Storage {
    fun loadBest(): Int
    fun saveBest(value: Int)
    /** Game in progress (see Game.saveGame), null when there is none. */
    fun loadGame(): String?
    fun saveGame(value: String?)
    /** Small persistent counters (crowns, hero upgrades). */
    fun loadInt(key: String): Int
    fun saveInt(key: String, value: Int)
}
