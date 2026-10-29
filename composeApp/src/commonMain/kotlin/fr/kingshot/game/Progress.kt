package fr.kingshot.game

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
val TALENT_NAMES = arrayOf("Cadence", "Aimant", "Chantiers", "Murs", "Bourse", "Vitesse", "Butin boss", "Donjon", "Escouade")
val TALENT_DESC = arrayOf(
    "Archers et tours -5 % de recharge", "Portée de l'aimant +10 %", "Chantiers -4 %", "PV des murs +10 %",
    "+10 pièces au départ", "Roi +4 % plus rapide", "Butin des boss +15 %", "PV du donjon +10 %", "+1 archer de départ tous les 2 rangs",
)

object Talents {
    fun level(t: Int) = Storage.loadInt("tal_$t")
    fun cost(t: Int) = 40 * (level(t) + 1)
    fun buy(t: Int): Boolean {
        val lv = level(t)
        val c = cost(t)
        if (lv >= TALENT_MAX || Meta.treasure < c) return false
        Meta.treasure -= c
        Storage.saveInt("tal_$t", lv + 1)
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
        MIS_KILLS -> "Vaincre ${target(s)} ennemis"
        MIS_COINS -> "Ramasser ${target(s)} pièces"
        MIS_BUILD -> "Construire ${target(s)} fois"
        MIS_WAVES -> "Repousser ${target(s)} vagues"
        MIS_BOSSES -> "Abattre ${target(s)} boss"
        else -> "Atteindre le niveau ${target(s)}"
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
    /** Streak if claimed today (yesterday's claim continues it, otherwise it restarts at 1). */
    val nextStreak get() = if (Storage.loadInt("daily_day") == today() - 1) Storage.loadInt("daily_streak") + 1 else 1
    val reward get() = 30 * nextStreak.coerceAtMost(7)
    fun claim(): Int {
        if (!available) return 0
        val r = reward
        Storage.saveInt("daily_streak", nextStreak)
        Storage.saveInt("daily_day", today())
        Meta.treasure += r
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
