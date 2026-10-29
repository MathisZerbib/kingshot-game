package fr.kingshot.game

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
