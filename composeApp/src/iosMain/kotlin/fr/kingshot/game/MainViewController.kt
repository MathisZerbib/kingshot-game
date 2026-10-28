package fr.kingshot.game

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults

actual object Storage {
    actual fun loadBest(): Int = NSUserDefaults.standardUserDefaults.integerForKey("kingshot.best").toInt()
    actual fun saveBest(value: Int) = NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), "kingshot.best")
}

/** Entry point used by the Xcode project (see iosApp/). */
fun MainViewController() = ComposeUIViewController { App() }
