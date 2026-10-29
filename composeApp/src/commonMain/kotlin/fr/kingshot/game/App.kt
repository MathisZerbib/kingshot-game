package fr.kingshot.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import fr.kingshot.game.resources.Res
import fr.kingshot.game.resources.logo
import fr.kingshot.game.resources.portrait_builder
import fr.kingshot.game.resources.portrait_ranger
import fr.kingshot.game.resources.portrait_warrior
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val baseSprites = rememberSprites()
    val shopV = Shop.version.intValue
    val banner = remember(shopV) { Shop.load(); Shop.bannerLook }
    val sprites by produceState(baseSprites, baseSprites, banner) {
        value = if (banner == 0) baseSprites else withContext(Dispatchers.Default) { withBanner(baseSprites, banner) }
    }
    renderer.sprites = sprites
    renderer.hud = rememberHud()
    val frame = remember { mutableLongStateOf(0L) }
    var uiState by remember { mutableIntStateOf(game.state) }
    var pausedUi by remember { mutableStateOf(false) }
    var victoryUi by remember { mutableStateOf(false) }
    val track = remember { IntArray(1) { Music.NONE } } // music playing now (changed from the frame loop)
    var picked by remember { mutableIntStateOf(game.savedClass) }
    var metaTick by remember { mutableIntStateOf(0) } // bumps after a purchase so the menu re-reads Meta
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
                if (game.paused != pausedUi) { pausedUi = game.paused; if (game.paused) Audio.pause() else Audio.resume() }
                if (game.victoryOpen != victoryUi) victoryUi = game.victoryOpen
                Audio.time = game.time
                val want = game.musicTrack
                if (want != track[0]) { track[0] = want; Audio.music(want) }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(game) {
        PlatformHooks.onBackground = { game.onBackground(); Audio.pause() }
        PlatformHooks.onForeground = { Audio.resume() }
    }
    LaunchedEffect(Unit) { Audio.init() }
    var settingsOpen by remember { mutableStateOf(false) }
    var metaPanel by remember { mutableIntStateOf(-1) } // 0 talents, 1 missions, 2 stats
    LaunchedEffect(Unit) { Missions.ensure() }

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
                    // HUD buttons take the touch instead of the joystick
                    if (game.state == STATE_PLAY && !game.paused) {
                        val p = first.position
                        if (hit(p, renderer.abilityX, renderer.abilityY, renderer.abilityRadius)) { game.useAbility(); first.consume(); return@awaitEachGesture }
                        if (hit(p, renderer.pauseX, renderer.pauseY, renderer.pauseRadius)) { game.paused = true; first.consume(); return@awaitEachGesture }
                    }
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
        when {
            uiState == STATE_MENU -> MainMenu(
                hasSave = game.hasSave, savedLevel = game.savedLevel, savedClass = game.savedClass, best = game.best,
                picked = picked, onPick = { picked = it },
                metaTick = metaTick,
                onContinue = { game.start(); focus.requestFocus() },
                onNew = { game.newGame(picked); focus.requestFocus() },
                onSettings = { settingsOpen = true },
                onMeta = { metaPanel = it },
                onDaily = { Daily.claim(); metaTick++ },
            )
            uiState == STATE_OVER -> DefeatPanel(game, onRevive = { game.revive(); focus.requestFocus() },
                onRetry = { game.retryLevel(); focus.requestFocus() }, onMenu = { game.quitRun(); metaTick++ })
            pausedUi -> PausePanel(game, onResume = { game.paused = false; focus.requestFocus() }, onSettings = { settingsOpen = true }, onMenu = { game.quitRun() })
            victoryUi -> VictoryPanel(game) { game.victoryOpen = false; focus.requestFocus() }
        }
        if (metaPanel >= 0) MetaPanel(metaPanel, metaTick, picked, onChange = { metaTick++ }) { metaPanel = -1 }
        if (settingsOpen) SettingsPanel(onReset = { game.best = 0; metaTick++ }) { settingsOpen = false }
    }
}

private fun hit(p: Offset, x: Float, y: Float, r: Float): Boolean {
    val dx = p.x - x
    val dy = p.y - y
    return dx * dx + dy * dy < r * r
}

