package com.hexcrown.game

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
    private var nextPlayer: MediaPlayer? = null
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

    private var loopStream = 0

    actual fun loop(id: Int, volume: Float) {
        val s = soundIds.getOrNull(id) ?: return
        if (s <= 0) return
        val v = (volume * sfxVol).coerceIn(0f, 1f)
        if (loopStream != 0) pool.setVolume(loopStream, v, v)
        else loopStream = pool.play(s, v, v, 0, -1, 1f) // 0 while the sound is still loading: the next frame retries
    }

    actual fun stopLoop(id: Int) {
        if (loopStream != 0) pool.stop(loopStream)
        loopStream = 0
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
            player = newPlayer(f.path).apply {
                // battle music breathes: 20 s of silence between loops; the Realm chains two players, AAC looping leaves a gap
                when (track) {
                    Music.BATTLE -> setOnCompletionListener { mp -> handler.postDelayed({ if (player === mp && !pausedByApp) runCatching { mp.start() } }, 20_000) }
                    Music.REALM -> chainNext(this, f.path)
                    else -> isLooping = true
                }
                if (!pausedByApp) start()
            }
        }.onFailure { stopPlayer() }
    }

    private fun newPlayer(path: String) = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        setDataSource(path)
        setVolume(musicVol, musicVol)
        prepare()
    }

    /** Seamless loop: when [cur] ends, the prepared next player takes over and prepares its own successor. */
    private fun chainNext(cur: MediaPlayer, path: String) {
        val next = newPlayer(path)
        nextPlayer = next
        cur.setNextMediaPlayer(next)
        cur.setOnCompletionListener { done ->
            if (player !== done) return@setOnCompletionListener
            done.release()
            player = next
            runCatching { chainNext(next, path) }
        }
    }

    private fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        runCatching { nextPlayer?.release() }
        player = null
        nextPlayer = null
    }

    actual fun setVolumes(sfx: Float, music: Float) {
        sfxVol = sfx
        musicVol = music
        runCatching { player?.setVolume(music, music) }
        runCatching { nextPlayer?.setVolume(music, music) }
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
