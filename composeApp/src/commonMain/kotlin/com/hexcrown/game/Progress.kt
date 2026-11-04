package com.hexcrown.game

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Long-term progression kept across games: talents, hero XP, missions, daily reward, statistics.
// Everything is read from Storage when a game starts or a menu opens, never per frame.

const val TAL_FIRE_RATE = 0 // archers and towers shoot faster
const val TAL_MAGNET = 1
const val TAL_PRICES = 2 // cheaper building sites
const val TAL_WALL_HP = 3
const val TAL_START_COINS = 4
const val TAL_KING_SPEED = 5
const val TAL_BOSS_LOOT = 6
const val TAL_KEEP_HP = 7
const val TAL_START_ARCHERS = 8
const val TALENT_MAX = 5
val TALENT_NAMES = LArr("talent.0", "talent.1", "talent.2", "talent.3", "talent.4", "talent.5", "talent.6", "talent.7", "talent.8")
val TALENT_DESC = LArr("talentdesc.0", "talentdesc.1", "talentdesc.2", "talentdesc.3", "talentdesc.4", "talentdesc.5", "talentdesc.6", "talentdesc.7", "talentdesc.8")

object Talents {
    fun level(t: Int) = Storage.loadInt("tal_$t")
    fun cost(t: Int) = 40 * (level(t) + 1)
    fun buy(t: Int): Boolean {
        val lv = level(t)
        val c = cost(t)
        if (lv >= TALENT_MAX || Meta.treasure < c) return false
        Meta.treasure -= c
        Storage.saveInt("tal_$t", lv + 1)
        if (lv + 1 >= 5) Ach.unlock("talents_max")
        return true
    }
}

/** Hero experience: level = 1 + floor(sqrt(xp / 40)); each level adds 1 % king damage. */
object Hero {
    var xp: Int
        get() = Storage.loadInt("xp")
        set(v) = Storage.saveInt("xp", v)
    fun levelOf(xp: Int): Int { var l = 1; while (40 * l * l <= xp) l++; return l }
    val level get() = levelOf(xp)
    /** XP needed to reach the next level, and progress inside the current one (0..1). */
    fun progress(): Float { val x = xp; val l = levelOf(x); val a = 40 * (l - 1) * (l - 1); val b = 40 * l * l; return (x - a).toFloat() / (b - a) }
}

// Missions: 3 active slots, each with a type, a target and progress; completed ones are claimed on the menu.
const val MIS_KILLS = 0
const val MIS_COINS = 1
const val MIS_BUILD = 2
const val MIS_WAVES = 3
const val MIS_BOSSES = 4
const val MIS_LEVEL = 5 // reach a level (progress = best level reached)
private val MIS_TARGETS = arrayOf(intArrayOf(50, 150, 400, 1000), intArrayOf(300, 1000, 3000), intArrayOf(5, 15, 40), intArrayOf(10, 30, 80), intArrayOf(1, 3, 10), intArrayOf(2, 3, 4, 5))

object Missions {
    const val SLOTS = 3
    fun type(s: Int) = Storage.loadInt("mis_t_$s")
    fun tier(s: Int) = Storage.loadInt("mis_r_$s")
    fun progress(s: Int) = Storage.loadInt("mis_p_$s")
    fun target(s: Int) = MIS_TARGETS[type(s)].let { it[tier(s).coerceIn(0, it.size - 1)] }
    fun reward(s: Int) = 40 + tier(s) * 50
    fun done(s: Int) = progress(s) >= target(s)
    fun label(s: Int): String = when (type(s)) {
        MIS_KILLS -> L.f("mis.kills", target(s))
        MIS_COINS -> L.f("mis.coins", target(s))
        MIS_BUILD -> L.f("mis.build", target(s))
        MIS_WAVES -> L.f("mis.waves", target(s))
        MIS_BOSSES -> L.f("mis.bosses", target(s))
        else -> L.f("mis.level", target(s))
    }

    /** First launch: three different missions. */
    fun ensure() {
        if (Storage.loadInt("mis_init") == 1) return
        for (s in 0 until SLOTS) { Storage.saveInt("mis_t_$s", s); Storage.saveInt("mis_r_$s", 0); Storage.saveInt("mis_p_$s", 0) }
        Storage.saveInt("mis_init", 1)
    }

    fun add(kind: Int, amount: Int) {
        for (s in 0 until SLOTS) if (type(s) == kind && !done(s)) Storage.saveInt("mis_p_$s", progress(s) + amount)
    }

    fun reach(kind: Int, value: Int) {
        for (s in 0 until SLOTS) if (type(s) == kind && progress(s) < value) Storage.saveInt("mis_p_$s", value)
    }

    /** Pays the reward and replaces the mission by the next tier of another type. */
    fun claim(s: Int): Int {
        if (!done(s)) return 0
        val r = reward(s)
        Meta.treasure += r
        val done = Storage.loadInt("st_missions") + 1
        Storage.saveInt("st_missions", done)
        if (done >= 30) Ach.unlock("missions_30")
        var t = (type(s) + 1) % MIS_TARGETS.size
        while ((0 until SLOTS).any { it != s && type(it) == t }) t = (t + 1) % MIS_TARGETS.size
        val nextTier = (Storage.loadInt("mis_next_$t")).coerceAtMost(MIS_TARGETS[t].size - 1)
        Storage.saveInt("mis_next_$t", nextTier + 1)
        Storage.saveInt("mis_t_$s", t); Storage.saveInt("mis_r_$s", nextTier); Storage.saveInt("mis_p_$s", 0)
        if (t == MIS_LEVEL) reach(MIS_LEVEL, Stats.bestLevel)
        return r
    }
}

object Daily {
    @OptIn(ExperimentalTime::class)
    private fun today() = (Clock.System.now().toEpochMilliseconds() / 86_400_000L).toInt()
    val available get() = Storage.loadInt("daily_day") != today()
    /** Yesterday's claim continues the streak; one missed day is forgiven once a week; otherwise it restarts at 1. */
    val nextStreak: Int get() {
        val gap = today() - Storage.loadInt("daily_day")
        val kept = gap == 1 || (gap == 2 && today() - Storage.loadInt("daily_forgiven") >= 7)
        return if (kept) Storage.loadInt("daily_streak") + 1 else 1
    }
    val reward get() = 60 + 20 * nextStreak.coerceAtMost(7)
    fun claim(): Int {
        if (!available) return 0
        val r = reward
        val n = nextStreak
        if (today() - Storage.loadInt("daily_day") == 2 && n > 1) Storage.saveInt("daily_forgiven", today())
        Storage.saveInt("daily_streak", n)
        Storage.saveInt("daily_day", today())
        Meta.treasure += r
        if (n >= 7) { Ach.unlock("streak_7"); if (n % 7 == 0) Storage.saveInt("free_relic", 1) }
        return r
    }
}

object Stats {
    val bestLevel get() = maxOf(1, Storage.loadInt("st_best_level"))
    val kills get() = Storage.loadInt("st_kills")
    val games get() = Storage.loadInt("st_games")
    val playSeconds get() = Storage.loadInt("st_time")
    val bosses get() = Storage.loadInt("st_bosses")
    val stars: Int get() { var n = 0; for (l in 1..20) n += Storage.loadInt("stars_$l"); return n }

    /** Folds a finished (or abandoned) run into the totals. */
    fun record(level: Int, kills: Int, seconds: Int) {
        if (level > bestLevel) Storage.saveInt("st_best_level", level)
        Storage.saveInt("st_kills", this.kills + kills)
        Storage.saveInt("st_games", games + 1)
        Storage.saveInt("st_time", playSeconds + seconds)
    }
}
