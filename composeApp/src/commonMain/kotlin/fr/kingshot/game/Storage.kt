package fr.kingshot.game

/** Tiny persistent store for the best wave reached. */
expect object Storage {
    fun loadBest(): Int
    fun saveBest(value: Int)
}
