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
