package fr.kingshot.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

/** SoundPool for effects (overlapping, pitch via rate), MediaPlayer for the looping music, Vibrator for haptics. */
actual class AudioEngine actual constructor() {
    private val context: Context? get() = Storage.context
    private val pool = SoundPool.Builder()
        .setMaxStreams(14)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = IntArray(64) { -1 }
    private val musicFiles = HashMap<Int, File>()
    private var player: MediaPlayer? = null
    private var track = Music.NONE
    private var sfxVol = 1f
    private var musicVol = 1f
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pausedByApp = false

    private fun cacheFile(name: String, data: ByteArray): File? {
        val dir = context?.cacheDir ?: return null
        val f = File(dir, name)
        if (!f.exists() || f.length() != data.size.toLong()) f.writeBytes(data)
        return f
    }

    actual fun loadSfx(id: Int, wav: ByteArray) {
        val f = cacheFile("sfx_$id.wav", wav) ?: return
        soundIds[id] = pool.load(f.path, 1)
    }

    actual fun loadMusic(track: Int, data: ByteArray) {
        val f = cacheFile("music_$track.m4a", data) ?: return
        musicFiles[track] = f
    }

    actual fun play(id: Int, volume: Float, pitch: Float) {
        val s = soundIds.getOrNull(id) ?: return
        if (s <= 0 || sfxVol <= 0f) return
        val v = (volume * sfxVol).coerceIn(0f, 1f)
        pool.play(s, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    actual fun music(track: Int) {
        if (track == this.track && player != null) {
            if (track != Music.NONE && !pausedByApp) runCatching { if (player?.isPlaying == false) player?.start() }
            return
        }
        stopPlayer()
        this.track = track
        if (track == Music.NONE) return
        val f = musicFiles[track] ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(f.path)
                // battle music breathes: 20 s of silence between loops (menu and boss keep looping)
                isLooping = track != Music.BATTLE
                setOnCompletionListener { mp -> handler.postDelayed({ if (player === mp && !pausedByApp) runCatching { mp.start() } }, 20_000) }
                setVolume(musicVol, musicVol)
                prepare()
                if (!pausedByApp) start()
            }
        }.onFailure { stopPlayer() }
    }

    private fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    actual fun setVolumes(sfx: Float, music: Float) {
        sfxVol = sfx
        musicVol = music
        runCatching { player?.setVolume(music, music) }
    }

    actual fun vibrate(kind: Int) {
        val ctx = context ?: return
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vib.hasVibrator()) return
        val (ms, amp) = when (kind) {
            Haptic.LIGHT -> 12L to 90
            Haptic.MEDIUM -> 25L to 160
            else -> 45L to 255
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        }
    }

    actual fun pause() {
        pausedByApp = true
        runCatching { if (player?.isPlaying == true) player?.pause() }
        pool.autoPause()
    }

    actual fun resume() {
        pausedByApp = false
        runCatching { if (track != Music.NONE && player?.isPlaying == false) player?.start() }
        pool.autoResume()
    }

    actual fun release() {
        stopPlayer()
        pool.release()
    }
}
