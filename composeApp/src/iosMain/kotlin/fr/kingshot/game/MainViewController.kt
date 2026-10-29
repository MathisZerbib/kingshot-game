package fr.kingshot.game

import androidx.compose.ui.graphics.asComposeImageBitmap

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults

actual object Storage {
    actual fun loadBest(): Int = NSUserDefaults.standardUserDefaults.integerForKey("kingshot.best").toInt()
    actual fun saveBest(value: Int) = NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), "kingshot.best")
    actual fun loadInt(key: String): Int = NSUserDefaults.standardUserDefaults.integerForKey("kingshot.$key").toInt()
    actual fun saveInt(key: String, value: Int) = NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), "kingshot.$key")
    actual fun loadGame(): String? = NSUserDefaults.standardUserDefaults.stringForKey("kingshot.game")
    actual fun saveGame(value: String?) =
        if (value == null) NSUserDefaults.standardUserDefaults.removeObjectForKey("kingshot.game")
        else NSUserDefaults.standardUserDefaults.setObject(value, "kingshot.game")
}

/** Entry point used by the Xcode project (see iosApp/). */
fun MainViewController() = ComposeUIViewController { App() }

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
