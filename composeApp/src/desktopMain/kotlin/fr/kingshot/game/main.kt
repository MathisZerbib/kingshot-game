package fr.kingshot.game

import androidx.compose.ui.graphics.asComposeImageBitmap

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

actual object Storage {
    private val prefs = Preferences.userRoot().node("fr/kingshot/game")
    actual fun loadBest(): Int = prefs.getInt("best", 0)
    actual fun saveBest(value: Int) = prefs.putInt("best", value)
    actual fun loadInt(key: String): Int = prefs.getInt(key, 0)
    actual fun saveInt(key: String, value: Int) = prefs.putInt(key, value)
    actual fun loadGame(): String? = prefs.get("game", null)
    actual fun saveGame(value: String?) = if (value == null) prefs.remove("game") else prefs.put("game", value)
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kingshot",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App()
    }
}

actual fun imageFromPixels(w: Int, h: Int, argb: IntArray): androidx.compose.ui.graphics.ImageBitmap {
    val bytes = ByteArray(w * h * 4)
    for (i in argb.indices) {
        val c = argb[i]
        bytes[i * 4] = (c and 255).toByte(); bytes[i * 4 + 1] = (c shr 8 and 255).toByte()
        bytes[i * 4 + 2] = (c shr 16 and 255).toByte(); bytes[i * 4 + 3] = (c ushr 24).toByte()
    }
    val bmp = org.jetbrains.skia.Bitmap()
    bmp.allocPixels(org.jetbrains.skia.ImageInfo(w, h, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.UNPREMUL))
    bmp.installPixels(bytes)
    return bmp.asComposeImageBitmap()
}
