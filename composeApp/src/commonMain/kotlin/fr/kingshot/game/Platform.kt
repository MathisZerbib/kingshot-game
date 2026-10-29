package fr.kingshot.game

/** Hooks between the platform shell (Activity, window) and the shared game. */
object PlatformHooks {
    /** Set by App: called when the app goes to the background (pause + save). */
    var onBackground: (() -> Unit)? = null
    /** Set by App: called when the app is back in front. */
    var onForeground: (() -> Unit)? = null
    /** Set by the platform: applies the frame-rate preference (true = cap at 60 Hz). */
    var applyFrameCap: ((Boolean) -> Unit)? = null
}

/** Player preferences, stored as small ints (0 = default). */
object Prefs {
    var sfxOff: Boolean
        get() = Storage.loadInt("sfx_off") == 1
        set(v) = Storage.saveInt("sfx_off", if (v) 1 else 0)
    var musicOff: Boolean
        get() = Storage.loadInt("music_off") == 1
        set(v) = Storage.saveInt("music_off", if (v) 1 else 0)
    var vibrationOff: Boolean
        get() = Storage.loadInt("vib_off") == 1
        set(v) = Storage.saveInt("vib_off", if (v) 1 else 0)
    /** 0 Normal, 1 Difficile (default), 2 Cauchemar. Stored +1 so a missing value means Difficile. */
    var difficulty: Int
        get() = (Storage.loadInt("difficulty") - 1).let { if (it < 0) 1 else it.coerceIn(0, 2) }
        set(v) = Storage.saveInt("difficulty", v + 1)
    var cap60: Boolean
        get() = Storage.loadInt("cap60") == 1
        set(v) { Storage.saveInt("cap60", if (v) 1 else 0); PlatformHooks.applyFrameCap?.invoke(v) }

    /** Wipes the treasure, upgrades, record and saved game; preferences stay. */
    fun resetProgress() {
        Storage.saveGame(null)
        Storage.saveBest(0)
        Storage.saveInt("treasure", 0)
        for (c in 0..2) for (u in 0..2) Storage.saveInt("up_${c}_$u", 0)
        for (t in 0 until 9) Storage.saveInt("tal_$t", 0)
        for (k in listOf("xp", "st_best_level", "st_kills", "st_games", "st_time", "mis_init", "daily_day", "daily_streak")) Storage.saveInt(k, 0)
        for (t in 0 until 6) Storage.saveInt("mis_next_$t", 0)
        Missions.ensure()
    }
}
