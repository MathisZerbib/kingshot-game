package fr.kingshot.game

import fr.kingshot.game.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Sound effect ids; [names] are the files in composeResources/files/sfx/<name>.wav. */
object Sfx {
    const val SHOOT = 0
    const val HIT = 1
    const val HIT_FLESH = 2
    const val COIN = 3
    const val COIN_STACK = 4
    const val BUILD = 5
    const val GATE = 6
    const val WAVE_HORN = 7
    const val BOSS_ROAR = 8
    const val ABILITY = 9
    const val VICTORY = 10
    const val DEFEAT = 11
    const val CLICK = 12
    const val LEVEL_UP = 13
    const val KING_HURT = 14
    const val WALL_BREAK = 15
    const val RECRUIT = 16
    const val FORGE = 17
    const val REPAIR = 18
    val names = arrayOf(
        "shoot", "hit", "hit_flesh", "coin", "coin_stack", "build", "gate", "wave_horn", "boss_roar", "ability",
        "victory", "defeat", "click", "level_up", "king_hurt", "wall_break", "recruit", "forge", "repair",
    )
    /** Minimum seconds between two plays of the same sound (keeps 30 archers from producing white noise). */
    val minGap = floatArrayOf(
        0.07f, 0.05f, 0.06f, 0.12f, 0.08f, 0.15f, 0.5f, 2f, 1f, 0.3f,
        2f, 2f, 0.1f, 2f, 0.4f, 0.5f, 0.2f, 0.2f, 0.2f,
    )
    /** Discreet mix (Mathis: "less sounds, more minimalistic"): longer gaps, so the battle hum stays in the background. */
    val minGapDiscreet = floatArrayOf(
        0.35f, 0.25f, 0.25f, 0.4f, 1.5f, 0.3f, 0.8f, 3f, 2f, 0.5f,
        3f, 3f, 0.12f, 3f, 0.6f, 0.8f, 0.6f, 0.6f, 0.6f,
    )
    /** Volume multiplier per sound in the discreet mix: combat and coin sounds are quiet, jingles and alerts keep their level. */
    val gainDiscreet = floatArrayOf(
        0.22f, 0.3f, 0.3f, 0.28f, 0.3f, 0.55f, 0.4f, 0.8f, 0.8f, 0.6f,
        0.85f, 0.85f, 0.45f, 0.7f, 0.7f, 0.6f, 0.45f, 0.45f, 0.4f,
    )
    /** Combat sounds that share one small budget (at most [Audio.COMBAT_BUDGET] of them per 0.5 s). */
    val combat = booleanArrayOf(
        true, true, true, true, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false,
    )
}

/** Music tracks; files in composeResources/files/music/<name>.m4a (AAC, looped). */
object Music {
    const val NONE = -1
    const val MENU = 0
    const val BATTLE = 1
    const val BOSS = 2
    val names = arrayOf("menu", "battle", "boss")
}

object Haptic {
    const val LIGHT = 0
    const val MEDIUM = 1
    const val HEAVY = 2
}

/**
 * Platform audio backend: pooled short effects (overlapping, pitch-shifted) and one looping music track.
 * Every method must be cheap and safe to call from the game loop; failures are swallowed.
 */
expect class AudioEngine() {
    /** Registers a WAV (16-bit PCM) effect for [id]. */
    fun loadSfx(id: Int, wav: ByteArray)
    /** Registers a music file (AAC .m4a; WAV also accepted) for [track]. */
    fun loadMusic(track: Int, data: ByteArray)
    fun play(id: Int, volume: Float, pitch: Float)
    /** Starts (or keeps) the looping [track]; Music.NONE stops. */
    fun music(track: Int)
    fun setVolumes(sfx: Float, music: Float)
    fun vibrate(kind: Int)
    /** App went to the background / came back: pause or resume the music. */
    fun pause()
    fun resume()
    fun release()
}

/**
 * Game-facing audio: rate limiting per effect, settings gating, lazy platform engine.
 * Call [init] once from a coroutine (App.kt), then [play]/[music]/[haptic] anywhere. [time] must be fed with the
 * game clock each frame so the per-sound gaps work.
 */
object Audio {
    /** Discreet mix on by default: fewer, quieter effects and the music in the background. */
    val discreet: Boolean get() = !Prefs.sfxFull
    const val COMBAT_BUDGET = 3
    private const val MASTER_SFX = 0.8f
    private const val MUSIC_DISCREET = 0.42f
    private var engine: AudioEngine? = null
    private val lastPlay = FloatArray(Sfx.names.size) { -10f }
    private var combatWindow = -1f
    private var combatCount = 0
    private var shootSkip = 0
    var time = 0f
    private var currentTrack = Music.NONE
    var ready = false
        private set

    @OptIn(ExperimentalResourceApi::class)
    suspend fun init() {
        if (engine != null) return
        val e = AudioEngine()
        for (i in Sfx.names.indices) {
            runCatching { e.loadSfx(i, Res.readBytes("files/sfx/${Sfx.names[i]}.wav")) }
        }
        for (i in Music.names.indices) {
            runCatching { e.loadMusic(i, Res.readBytes("files/music/${Music.names[i]}.m4a")) }
        }
        e.setVolumes(sfxVolume(), musicVolume())
        engine = e
        ready = true
        if (currentTrack != Music.NONE && !Prefs.musicOff) e.music(currentTrack)
    }

    private fun sfxVolume() = if (Prefs.sfxOff) 0f else if (discreet) MASTER_SFX else 1f
    private fun musicVolume() = if (Prefs.musicOff) 0f else if (discreet) MUSIC_DISCREET else 1f

    fun play(id: Int, volume: Float = 1f, pitch: Float = 1f) {
        if (Prefs.sfxOff) return
        if (!discreet) {
            if (time - lastPlay[id] < Sfx.minGap[id]) return
            lastPlay[id] = time
            engine?.play(id, volume, pitch)
            return
        }
        if (time - lastPlay[id] < Sfx.minGapDiscreet[id]) return
        if (id == Sfx.SHOOT && ++shootSkip % 3 != 0) return // one arrow in three is heard
        if (Sfx.combat[id]) {
            if (time - combatWindow > 0.5f) { combatWindow = time; combatCount = 0 }
            if (++combatCount > COMBAT_BUDGET) return
        }
        lastPlay[id] = time
        engine?.play(id, volume * Sfx.gainDiscreet[id], pitch)
    }

    /** Plays with a random pitch around 1 (±[spread]) so repeated effects do not sound mechanical. */
    fun playVaried(id: Int, volume: Float = 1f, spread: Float = 0.1f, rnd: kotlin.random.Random) {
        play(id, volume, 1f + (rnd.nextFloat() * 2f - 1f) * spread)
    }

    fun music(track: Int) {
        currentTrack = track
        val e = engine ?: return
        if (Prefs.musicOff || track == Music.NONE) e.music(Music.NONE) else e.music(track)
    }

    fun haptic(kind: Int) {
        if (!Prefs.vibrationOff) engine?.vibrate(kind)
    }

    /** Re-applies the preferences (call after the settings panel toggled sound or music). */
    fun applySettings() {
        val e = engine ?: return
        e.setVolumes(sfxVolume(), musicVolume())
        if (Prefs.musicOff) e.music(Music.NONE) else if (currentTrack != Music.NONE) e.music(currentTrack)
    }

    fun pause() { engine?.pause() }
    fun resume() { engine?.resume() }
}
