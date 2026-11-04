package com.hexcrown.game

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
    private var sfxOffCache = -1
    var sfxOff: Boolean
        get() { if (sfxOffCache < 0) sfxOffCache = Storage.loadInt("sfx_off"); return sfxOffCache == 1 }
        set(v) { sfxOffCache = if (v) 1 else 0; Storage.saveInt("sfx_off", sfxOffCache) }
    var musicOff: Boolean
        get() = Storage.loadInt("music_off") == 1
        set(v) = Storage.saveInt("music_off", if (v) 1 else 0)
    /** Full sound mix; default (0) is the discreet mix. */
    private var sfxFullCache = -1
    var sfxFull: Boolean
        get() { if (sfxFullCache < 0) sfxFullCache = Storage.loadInt("sfx_full"); return sfxFullCache == 1 }
        set(v) { sfxFullCache = if (v) 1 else 0; Storage.saveInt("sfx_full", sfxFullCache) }
    private var showFpsCache = -1
    var showFps: Boolean
        get() { if (showFpsCache < 0) showFpsCache = Storage.loadInt("show_fps"); return showFpsCache == 1 }
        set(v) { showFpsCache = if (v) 1 else 0; Storage.saveInt("show_fps", showFpsCache) }
    private var vibOffCache = -1
    var vibrationOff: Boolean
        get() { if (vibOffCache < 0) vibOffCache = Storage.loadInt("vib_off"); return vibOffCache == 1 }
        set(v) { vibOffCache = if (v) 1 else 0; Storage.saveInt("vib_off", vibOffCache) }
    /** 0 Normal, 1 Difficile (default), 2 Cauchemar. Stored +1 so a missing value means Difficile. */
    var difficulty: Int
        get() = (Storage.loadInt("difficulty") - 1).let { if (it < 0) 1 else it.coerceIn(0, 2) }
        set(v) = Storage.saveInt("difficulty", v + 1)
    private var cap60Cache = -1
    var cap60: Boolean
        get() { if (cap60Cache < 0) cap60Cache = Storage.loadInt("cap60"); return cap60Cache == 1 }
        set(v) { cap60Cache = if (v) 1 else 0; Storage.saveInt("cap60", cap60Cache); PlatformHooks.applyFrameCap?.invoke(v) }

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
