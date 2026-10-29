package fr.kingshot.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import fr.kingshot.game.resources.Res
import fr.kingshot.game.resources.logo
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
    var pausedUi by remember { mutableStateOf(false) }
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
                if (game.paused != pausedUi) pausedUi = game.paused
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(game) { PlatformHooks.onBackground = { game.onBackground() } }
    var settingsOpen by remember { mutableStateOf(false) }

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
                metaTick = metaTick, onBuy = { up -> if (Meta.buy(picked, up)) metaTick++ },
                onContinue = { game.start(); focus.requestFocus() },
                onNew = { game.newGame(picked); focus.requestFocus() },
                onSettings = { settingsOpen = true },
            )
            uiState == STATE_OVER -> Panel("LE DONJON EST TOMBÉ", "Niveau ${game.level} · vague ${game.wave} · ${game.kills} ennemis vaincus",
                (if (game.newRecord) "Nouveau record !  " else "") + "+${game.treasureWon} pièces au trésor · trésor ${Meta.treasure}",
                "REJOUER", { game.newGame(game.heroClass); focus.requestFocus() }, "Menu", { game.toMenu() })
            pausedUi -> Panel("PAUSE", "${CLASS_NAMES[game.heroClass]} · niveau ${game.level} · vague ${game.wave}", "Partie sauvegardée à chaque vague",
                "REPRENDRE", { game.paused = false; focus.requestFocus() }, "Menu", { game.toMenu() }, third = "Réglages", onThird = { settingsOpen = true })
        }
        if (settingsOpen) SettingsPanel(onReset = { game.best = 0; metaTick++ }) { settingsOpen = false }
    }
}

private fun hit(p: Offset, x: Float, y: Float, r: Float): Boolean {
    val dx = p.x - x
    val dy = p.y - y
    return dx * dx + dy * dy < r * r
}

private val CLASS_TINT = arrayOf(Color(0xFFD9443A), Color(0xFF3F9E4D), Color(0xFFE08A2B))
private val CLASS_ROLE = arrayOf("Corps à corps · très solide", "Tir rapide à longue portée · fragile", "Constructions -30 % · grand aimant")
private val CLASS_ABILITY = arrayOf(
    "Tourbillon : frappe et repousse tout autour",
    "Pluie de flèches sur tous les ennemis proches",
    "Répare murs et donjon, soigne le roi",
)
private val CLASS_HP = intArrayOf(3, 1, 2)

@Composable
private fun MainMenu(
    hasSave: Boolean, savedLevel: Int, savedClass: Int, best: Int,
    picked: Int, onPick: (Int) -> Unit, metaTick: Int, onBuy: (Int) -> Unit, onContinue: () -> Unit, onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    metaTick // read so a purchase recomposes the menu
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0xCC000000)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).clickable(onClick = onSettings)
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Label("⚙ Réglages", Color.White, 16, FontWeight.Black)
                }
                Box(Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Label("Trésor ● ${Meta.treasure}", Color(0xFFFFC531), 16, FontWeight.Black)
                }
            }
            Image(painterResource(Res.drawable.logo), null, Modifier.size(110.dp))
            Label("KINGSHOT", Color(0xFFFFC531), 48, FontWeight.Black)
            Label("Défends ton royaume", Color.White, 16, FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            Label("Choisis ton héros", Color.White, 18, FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) ClassCard(c, c == picked, Modifier.weight(1f)) { onPick(c) }
            }
            Spacer(Modifier.height(12.dp))
            // permanent upgrades of the picked hero
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (up in 0..2) {
                    val lv = Meta.level(picked, up)
                    val cost = Meta.cost(picked, up)
                    val can = lv < UP_MAX && Meta.treasure >= cost
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (can) Color(0xEE2E6B34) else Color(0xAA1A2233))
                            .clickable(enabled = can) { onBuy(up) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Label("${UP_NAMES[up]} $lv/$UP_MAX", Color.White, 12, FontWeight.Black)
                        Label(if (lv >= UP_MAX) "MAX" else "+ ● $cost", if (can) Color(0xFFFFC531) else Color(0xFF999999), 12, FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (hasSave) {
                BigButton("CONTINUER", "${CLASS_NAMES[savedClass]} · niveau $savedLevel", Color(0xFF45C34A), Color(0xFF2A8A2F), onContinue)
                Spacer(Modifier.height(12.dp))
                BigButton("NOUVELLE PARTIE", CLASS_NAMES[picked], Color(0xFFFFB627), Color(0xFFC77E00), onNew, small = true)
            } else {
                BigButton("JOUER", CLASS_NAMES[picked], Color(0xFF45C34A), Color(0xFF2A8A2F), onNew)
            }
            if (best > 0) {
                Spacer(Modifier.height(14.dp))
                Label("Record : vague $best", Color(0xFF9EE37D), 14, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassCard(c: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .scale(if (selected) 1.04f else 1f)
            .clip(shape)
            .background(if (selected) Color(0xEE2B3A55) else Color(0xAA1A2233))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFFFC531) else Color(0x55FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFD9901A), r)
            drawCircle(CLASS_TINT[c], r * 0.88f)
            classIcon(c, center.x, center.y, r * 0.5f, r * 0.14f)
        }
        Spacer(Modifier.height(6.dp))
        Label(CLASS_NAMES[c], Color.White, 15, FontWeight.Black)
        Label("PV " + "♥".repeat(CLASS_HP[c]), Color(0xFFFF7A7A), 11, FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Label(CLASS_ROLE[c], Color(0xFFDDDDDD), 10)
        Spacer(Modifier.height(4.dp))
        Label(CLASS_ABILITY[c], Color(0xFFFFD86A), 10, FontWeight.Bold)
    }
}

@Composable
private fun BigButton(text: String, sub: String?, color: Color, shade: Color, onClick: () -> Unit, small: Boolean = false) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .width(if (small) 220.dp else 260.dp)
            .clip(shape)
            .background(shade) // darker bottom edge gives the chunky mobile-button look
            .clickable(onClick = onClick)
            .padding(bottom = 5.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(color).padding(vertical = if (small) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(text, Color.White, if (small) 18 else 26, FontWeight.Black)
            if (sub != null) Label(sub, Color(0xEEFFFFFF), 12, FontWeight.Bold)
        }
    }
}

@Composable
private fun Panel(
    title: String, subtitle: String, info: String, button: String, onClick: () -> Unit, second: String, onSecond: () -> Unit,
    third: String? = null, onThird: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xEE1F2A40))
                .border(3.dp, Color(0xFFFFC531), RoundedCornerShape(22.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(title, Color(0xFFFFC531), 32, FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Label(subtitle, Color.White, 15, FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Label(info, Color(0xFF9EE37D), 13, FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            BigButton(button, null, Color(0xFF45C34A), Color(0xFF2A8A2F), onClick)
            Spacer(Modifier.height(10.dp))
            BigButton(second, null, Color(0xFF4A5A7A), Color(0xFF2E3A52), onSecond, small = true)
            if (third != null) {
                Spacer(Modifier.height(10.dp))
                BigButton(third, null, Color(0xFF4A5A7A), Color(0xFF2E3A52), onThird, small = true)
            }
        }
    }
}

@Composable
private fun SettingsPanel(onReset: () -> Unit, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    tick // re-read Prefs after each change
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF21F2A40))
                .border(3.dp, Color(0xFFFFC531), RoundedCornerShape(22.dp)).clickable(enabled = false) {}.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("RÉGLAGES", Color(0xFFFFC531), 28, FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Toggle("Sons", !Prefs.sfxOff) { Prefs.sfxOff = !it; tick++ }
            Toggle("Musique", !Prefs.musicOff) { Prefs.musicOff = !it; tick++ }
            Toggle("Vibrations", !Prefs.vibrationOff) { Prefs.vibrationOff = !it; tick++ }
            Toggle("Fluidité max (sinon 60 Hz, économise la batterie)", !Prefs.cap60) { Prefs.cap60 = !it; tick++ }
            Spacer(Modifier.height(14.dp))
            BigButton(
                if (confirmReset) "CONFIRMER ?" else "Remettre à zéro", if (confirmReset) "Trésor, améliorations, record et partie" else "la progression",
                if (confirmReset) Color(0xFFD9443A) else Color(0xFF4A5A7A), if (confirmReset) Color(0xFF8E2620) else Color(0xFF2E3A52),
                { if (confirmReset) { Prefs.resetProgress(); confirmReset = false; onReset() } else confirmReset = true }, small = true,
            )
            Spacer(Modifier.height(10.dp))
            BigButton("FERMER", null, Color(0xFF45C34A), Color(0xFF2A8A2F), onClose, small = true)
        }
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.width(280.dp).padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF))
            .clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { BasicText(text, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
        Box(
            Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF45C34A) else Color(0xFF59627A)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(Color.White))
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
