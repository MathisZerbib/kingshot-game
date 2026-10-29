package fr.kingshot.game

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

/** javax.sound: WAV effects as short-lived clips; music only when the data is a WAV (AAC is not decoded here). */
actual class AudioEngine actual constructor() {
    private val sfx = HashMap<Int, ByteArray>()
    private val musicData = HashMap<Int, ByteArray>()
    private var musicClip: Clip? = null
    private var track = Music.NONE
    private var sfxVol = 1f
    private var musicVol = 1f
    private var live = 0

    actual fun loadSfx(id: Int, wav: ByteArray) {
        if (wav.size > 12 && wav[0] == 'R'.code.toByte()) sfx[id] = wav
    }

    actual fun loadMusic(track: Int, data: ByteArray) {
        if (data.size > 12 && data[0] == 'R'.code.toByte()) musicData[track] = data
    }

    private fun gain(clip: Clip, volume: Float) {
        runCatching {
            val g = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            g.value = (20f * log10(volume.coerceIn(0.0001f, 1f))).coerceIn(g.minimum, g.maximum)
        }
    }

    actual fun play(id: Int, volume: Float, pitch: Float) {
        val data = sfx[id] ?: return
        if (sfxVol <= 0f || live > 24) return
        runCatching {
            val clip = AudioSystem.getClip()
            clip.open(AudioSystem.getAudioInputStream(ByteArrayInputStream(data)))
            gain(clip, volume * sfxVol)
            live++
            clip.addLineListener { if (it.type == LineEvent.Type.STOP) { clip.close(); live-- } }
            clip.start()
        }
    }

    actual fun music(track: Int) {
        if (track == this.track && musicClip != null) return
        musicClip?.stop(); musicClip?.close(); musicClip = null
        this.track = track
        val data = musicData[track] ?: return
        runCatching {
            val clip = AudioSystem.getClip()
            clip.open(AudioSystem.getAudioInputStream(ByteArrayInputStream(data)))
            gain(clip, musicVol)
            clip.loop(Clip.LOOP_CONTINUOUSLY)
            musicClip = clip
        }
    }

    actual fun setVolumes(sfx: Float, music: Float) {
        sfxVol = sfx
        musicVol = music
        musicClip?.let { gain(it, music) }
    }

    actual fun vibrate(kind: Int) {}
    actual fun pause() { musicClip?.stop() }
    actual fun resume() { musicClip?.loop(Clip.LOOP_CONTINUOUSLY) }
    actual fun release() { musicClip?.close(); musicClip = null }
}
