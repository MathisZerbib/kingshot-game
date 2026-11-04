package com.hexcrown.game

// Local achievements (docs/CONTENT.md): 25 coins each, 100 for the starred ones, shown one at a time.

object Ach {
    val ids = arrayOf(
        "first_banner", "seven_hex", "forty_hex", "no_burn", "charge_10", "charge_trample", "combo_20", "combo_50", "boss_1", "boss_10",
        "relic_first", "relic_codex", "night_wave", "rain_win", "level_5", "level_10", "level_20", "endless_20", "archers_12", "towers_6",
        "coins_1000", "talents_max", "all_classes", "skin_1", "streak_7", "missions_30", "wolves_tamed", "repair_walls", "whirl_8", "arrows_rain",
    )
    private val starred = setOf("forty_hex", "combo_50", "relic_codex")
    private val queue = ArrayDeque<String>()
    /** Toast on screen, and its age in seconds (shown for 2.4 s). */
    var current: String? = null; private set
    var age = 0f; private set

    fun done(id: String) = Storage.loadInt("ach_$id") == 1
    val count: Int get() = ids.count { done(it) }
    fun reward(id: String) = if (id in starred) 100 else 25

    fun unlock(id: String) {
        if (done(id)) return
        Storage.saveInt("ach_$id", 1)
        Meta.treasure += reward(id)
        queue.addLast(id)
    }

    /** Never during a boss: the queue waits. */
    fun tick(dt: Float, bossOnField: Boolean) {
        if (current != null) {
            age += dt
            if (age >= 2.4f) current = null
            return
        }
        if (bossOnField || queue.isEmpty()) return
        current = queue.removeFirst()
        age = 0f
        Audio.play(Sfx.ACHIEVEMENT)
    }
}
