package fr.kingshot.game

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/** AVAudioPlayer pools for effects (a few players per sound so they overlap), one looping player for the music. */
@OptIn(ExperimentalForeignApi::class)
actual class AudioEngine actual constructor() {
    private class Pool(val data: NSData) {
        val players = ArrayList<AVAudioPlayer>()
        var next = 0
    }

    private val sfx = HashMap<Int, Pool>()
    private val musicData = HashMap<Int, NSData>()
    private var player: AVAudioPlayer? = null
    private var track = Music.NONE
    private var sfxVol = 1f
    private var musicVol = 1f
    private var pausedByApp = false
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    init {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, error = null)
            session.setActive(true, error = null)
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    actual fun loadSfx(id: Int, wav: ByteArray) {
        if (wav.isEmpty()) return
        sfx[id] = Pool(wav.toNSData())
    }

    actual fun loadMusic(track: Int, data: ByteArray) {
        if (data.isEmpty()) return
        musicData[track] = data.toNSData()
    }

    private fun newPlayer(data: NSData, loops: Int): AVAudioPlayer? = runCatching {
        AVAudioPlayer(data = data, error = null).apply {
            numberOfLoops = loops.toLong()
            enableRate = true
            prepareToPlay()
        }
    }.getOrNull()

    actual fun play(id: Int, volume: Float, pitch: Float) {
        if (sfxVol <= 0f) return
        val pool = sfx[id] ?: return
        var p: AVAudioPlayer? = null
        for (q in pool.players) if (!q.playing) { p = q; break }
        if (p == null) {
            if (pool.players.size < 4) {
                p = newPlayer(pool.data, 0) ?: return
                pool.players.add(p)
            } else {
                p = pool.players[pool.next % pool.players.size]
                pool.next++
                p.stop()
            }
        }
        p.volume = (volume * sfxVol).coerceIn(0f, 1f)
        p.rate = pitch.coerceIn(0.5f, 2f)
        p.currentTime = 0.0
        p.play()
    }

    actual fun music(track: Int) {
        if (track == this.track && player != null) {
            if (track != Music.NONE && !pausedByApp && player?.playing == false) player?.play()
            return
        }
        player?.stop()
        player = null
        this.track = track
        if (track == Music.NONE) return
        val data = musicData[track] ?: return
        player = newPlayer(data, -1)?.also {
            it.volume = musicVol
            if (!pausedByApp) it.play()
        }
    }

    actual fun setVolumes(sfx: Float, music: Float) {
        sfxVol = sfx
        musicVol = music
        player?.volume = music
    }

    actual fun vibrate(kind: Int) {
        when (kind) {
            Haptic.LIGHT -> light.impactOccurred()
            Haptic.MEDIUM -> medium.impactOccurred()
            else -> heavy.impactOccurred()
        }
    }

    actual fun pause() {
        pausedByApp = true
        player?.pause()
    }

    actual fun resume() {
        pausedByApp = false
        if (track != Music.NONE) player?.play()
    }

    actual fun release() {
        player?.stop()
        player = null
        sfx.clear()
    }
}
