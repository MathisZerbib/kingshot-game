package fr.kingshot.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val game = remember { Game() }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val renderer = remember(measurer, density.density) { Renderer(measurer, density.density) }
    renderer.sprites = rememberSprites()
    val frame = remember { mutableLongStateOf(0L) }
    var uiState by remember { mutableIntStateOf(game.state) }
    val focus = remember { FocusRequester() }
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
    renderer.topInset = topInset
    game.input.radiusPx = with(density) { 56.dp.toPx() }

    // Game loop: one update per display vsync (60, 90, 120 Hz...), driven by real delta time.
    LaunchedEffect(game) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0L) game.update((now - last) / 1_000_000_000f)
                last = now
                frame.longValue = now
                if (game.state != uiState) uiState = game.state
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF8DC24F))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                val down = ev.type == KeyEventType.KeyDown
                if (ev.type != KeyEventType.KeyDown && ev.type != KeyEventType.KeyUp) return@onKeyEvent false
                val inp = game.input
                when (ev.key) {
                    Key.W, Key.Z, Key.DirectionUp -> inp.up = down
                    Key.S, Key.DirectionDown -> inp.down = down
                    Key.A, Key.Q, Key.DirectionLeft -> inp.left = down
                    Key.D, Key.DirectionRight -> inp.right = down
                    Key.Spacebar, Key.Enter -> {
                        if (down && game.state != STATE_PLAY) game.start()
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(game) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val id = first.id
                    game.input.touchStart(first.position.x, first.position.y, first.position.y < size.height * 0.6f)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == id } ?: break
                        if (!ch.pressed) break
                        game.input.touchMove(ch.position.x, ch.position.y)
                        ch.consume()
                    }
                    game.input.touchEnd()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Reading the frame state here only invalidates the draw phase: no recomposition per frame.
            frame.longValue
            renderer.draw(this, game)
        }
        when (uiState) {
            STATE_MENU -> Overlay(
                title = "KINGSHOT",
                subtitle = "Le vrai jeu. Zéro pub.",
                body = "Déplace le roi en glissant le doigt (ou ZQSD / flèches).\n" +
                    "Tes archers tirent tout seuls. Ramasse l'or des chevaliers\n" +
                    "et dépose-le sur les chantiers : archers, tours, palissades.\n" +
                    "Protège le donjon le plus longtemps possible !",
                info = if (game.best > 0) "Record : vague ${game.best}" else null,
                button = "JOUER",
            ) { game.start(); focus.requestFocus() }
            STATE_OVER -> Overlay(
                title = "LE DONJON EST TOMBÉ",
                subtitle = "Vague ${game.wave} · ${game.kills} chevaliers vaincus",
                body = null,
                info = if (game.newRecord) "Nouveau record !" else "Record : vague ${game.best}",
                button = "REJOUER",
            ) { game.start(); focus.requestFocus() }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, size: Int, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = TextAlign.Center, lineHeight = (size * 1.4f).sp),
    )
}

@Composable
private fun Overlay(title: String, subtitle: String, body: String?, info: String?, button: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label(title, Color(0xFFFFC531), 40, FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Label(subtitle, Color.White, 18, FontWeight.Bold)
            if (body != null) {
                Spacer(Modifier.height(18.dp))
                Label(body, Color(0xFFE8E8E8), 14)
            }
            if (info != null) {
                Spacer(Modifier.height(14.dp))
                Label(info, Color(0xFF9EE37D), 16, FontWeight.Bold)
            }
            Spacer(Modifier.height(26.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFB627))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 44.dp, vertical = 14.dp),
            ) {
                Label(button, Color(0xFF3A2400), 24, FontWeight.Black)
            }
        }
    }
}
