package fr.kingshot.game

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

actual object Storage {
    private val prefs = Preferences.userRoot().node("fr/kingshot/game")
    actual fun loadBest(): Int = prefs.getInt("best", 0)
    actual fun saveBest(value: Int) = prefs.putInt("best", value)
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
