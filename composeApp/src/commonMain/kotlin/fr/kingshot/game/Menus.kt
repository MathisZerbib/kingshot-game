package fr.kingshot.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.kingshot.game.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Menu screens built from the interface kit (art/ui/DA.md, mock-ups in art/ui/mockup.py).

private val CLASS_ROLE = arrayOf("Corps à corps · très solide", "Tir rapide à longue portée", "Constructions -30 % · aimant")
private val CLASS_ABILITY_SHORT = arrayOf("Tourbillon", "Pluie de flèches", "Réparation")
private val CLASS_HP = intArrayOf(3, 1, 2)
val PORTRAITS = arrayOf(Res.drawable.portrait_warrior, Res.drawable.portrait_ranger, Res.drawable.portrait_builder)
val CLASS_ICONS = arrayOf(Res.drawable.ui_icon_sword, Res.drawable.ui_icon_bow, Res.drawable.ui_icon_hammer)
private val TALENT_ICONS = arrayOf(
    Res.drawable.ui_icon_hourglass, Res.drawable.ui_icon_coins, Res.drawable.ui_icon_hammer, Res.drawable.ui_icon_shield,
    Res.drawable.ui_icon_coins, Res.drawable.ui_icon_flag, Res.drawable.ui_icon_chest, Res.drawable.ui_icon_crown, Res.drawable.ui_icon_bow,
)
private val UP_ICONS = arrayOf(Res.drawable.ui_icon_heart, Res.drawable.ui_icon_sword, Res.drawable.ui_icon_hourglass)

private val MENU_BGS = arrayOf(Res.drawable.menu_bg_warrior, Res.drawable.menu_bg_ranger, Res.drawable.menu_bg_builder)
// v2 plate: Vie / Attaque / Vitesse on 3 pips, and the role line (art/ui/DA-v2.md)
private val CLASS_STATS = arrayOf(intArrayOf(3, 2, 1), intArrayOf(1, 3, 3), intArrayOf(2, 1, 2))
private val CLASS_ROLE_V2 = arrayOf("Tient la ligne au corps à corps", "Tir rapide à longue portée", "Bâtit vite, répare les murs")
private val STAT_NAMES = arrayOf("Vie", "Attaque", "Vitesse")
private val STAT_BARS = arrayOf(Res.drawable.ui_bar_red, Res.drawable.ui_bar_gold, Res.drawable.ui_bar_blue)
private var menuIntroPlayed = false // the opening scene runs once per launch

/** Menu v2 « le héros tient l'écran »: the background is the selector, one plate, one gold button, one tab bar. */
@Composable
fun MainMenu(
    hasSave: Boolean, savedLevel: Int, savedClass: Int, best: Int,
    picked: Int, onPick: (Int) -> Unit, metaTick: Int,
    onContinue: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onMeta: (Int) -> Unit, onDaily: () -> Unit,
    onShop: () -> Unit = {},
) {
    metaTick // re-read Meta after purchases
    val reduced = Prefs.cap60
    var dir by remember { mutableIntStateOf(1) }
    val pick = { c: Int, d: Int ->
        if (c != picked) { dir = d; Audio.play(Sfx.RECRUIT); Audio.haptic(Haptic.LIGHT); onPick(c) }
    }
    // opening scene: one clock, each element reads its own window of it in graphicsLayer (no recomposition)
    val intro = remember { Animatable(if (menuIntroPlayed) 1f else 0f) }
    LaunchedEffect(Unit) { menuIntroPlayed = true; intro.animateTo(1f, tween(800, easing = LinearEasing)) }
    fun Modifier.enter(delayMs: Int, durMs: Int, rise: Float, scaleFrom: Float = 1f) = graphicsLayer {
        val k = ExpoOut.transform(((intro.value * 800f - delayMs) / durMs).coerceIn(0f, 1f))
        alpha = k
        if (!reduced) { translationY = (1f - k) * rise * density; val sc = scaleFrom + (1f - scaleFrom) * k; scaleX = sc; scaleY = sc }
    }
    Box(
        Modifier.fillMaxSize().background(Pal.navyDeep).pointerInput(picked) {
            // horizontal swipe anywhere: 80 dp, or a flick faster than 0.3 dp/ms
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = {
                    val far = kotlin.math.abs(dx) > 80f * density
                    if (far || kotlin.math.abs(dx) > 24f * density) {
                        if (dx < 0f) pick((picked + 1) % 3, 1) else pick((picked + 2) % 3, -1)
                    }
                },
            ) { ch, d -> dx += d; ch.consume() }
        },
    ) {
        MenuBackground(picked, dir, reduced)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column {
                    RoundButton(Res.drawable.ui_icon_gear, onClick = onSettings)
                    if (Daily.available) RoundButton(Res.drawable.ui_icon_gift, red = true, badge = true, size = 46.dp, onClick = onDaily)
                }
                Spacer(Modifier.weight(1f))
                Pill(Res.drawable.ui_icon_coins, "${Meta.treasure}", width = 120.dp)
            }
            Box(Modifier.fillMaxWidth().enter(0, 420, 0f, 1.05f), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.ui_logotype), "Kingshot", Modifier.fillMaxWidth(0.63f), contentScale = ContentScale.FillWidth)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                for (c in 0..2) Box(Modifier.width(100.dp).enter(120 + 60 * c, 420, 20f), contentAlignment = Alignment.Center) {
                    HeroChip(c, c == picked) { pick(c, if (c > picked) 1 else -1) }
                }
            }
            Box(Modifier.enter(200, 420, 16f)) { HeroPlate(picked) }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().enter(280, 420, 16f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasSave) KitButton(
                    Res.drawable.ui_button_gold, "Continuer", "Niveau $savedLevel · ${World.levelName(savedLevel)}", Res.drawable.ui_icon_play,
                    width = 253.dp, height = 56.dp, textColor = Pal.ink, titleSize = 24, shine = true, onClick = onContinue,
                ) else KitButton(
                    Res.drawable.ui_button_gold, "Jouer", "${CLASS_NAMES[picked]} · nouvelle partie", Res.drawable.ui_icon_play,
                    width = 253.dp, height = 56.dp, textColor = Pal.ink, titleSize = 24, shine = true, onClick = onNew,
                )
                if (hasSave) NewGameLink(onNew) else Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.enter(340, 420, 16f)) {
                val ready = (0 until Missions.SLOTS).count { Missions.done(it) }
                TabBar(ready, onShop, onMeta)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Hero art fitted to the width and pinned to the top; fades into night blue from 62 % down. 350 ms cross-fade + 30 dp slide. */
@Composable
private fun MenuBackground(picked: Int, dir: Int, reduced: Boolean) {
    val bgs = MENU_BGS.map { imageResource(it) }
    var from by remember { mutableIntStateOf(picked) }
    var to by remember { mutableIntStateOf(picked) }
    var sdir by remember { mutableIntStateOf(1) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(picked) {
        if (picked == to) return@LaunchedEffect
        from = to; to = picked; sdir = dir
        fade.snapTo(0f); fade.animateTo(1f, tween(350, easing = InOut))
    }
    val drift = rememberInfiniteTransition().animateFloat(1f, 1.03f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse))
    Box(Modifier.fillMaxSize().drawWithCache {
        val vignette = Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent), 0f, size.height * 0.125f)
        val melt = Brush.verticalGradient(listOf(Color.Transparent, Pal.navyDeep), size.height * 0.62f, size.height * 0.80f)
        val shift = 30.dp.toPx()
        onDrawBehind {
            fun layer(i: Int, dx: Float, alpha: Float) {
                val im = bgs[i]
                val w = size.width * drift.value
                val h = w * im.height / im.width
                drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset(((size.width - w) / 2f + dx).toInt(), 0), IntSize(w.toInt(), h.toInt()), alpha = alpha)
            }
            val f = fade.value
            if (f < 1f) layer(from, if (reduced) 0f else -sdir * shift * f, 1f)
            layer(to, if (reduced) 0f else sdir * shift * (1f - f), f)
            drawRect(vignette, size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.125f))
            drawRect(melt, topLeft = Offset(0f, size.height * 0.62f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.38f))
        }
    })
}

/** Portrait chip: 50 dp, the picked one 62 dp, raised 5 dp, gold ring and a slow halo. Spring only here. */
@Composable
private fun HeroChip(c: Int, selected: Boolean, onClick: () -> Unit) {
    val sc = remember { Animatable(if (selected) 1f else 50f / 62f) }
    val ring = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        launch { sc.animateTo(if (selected) 1f else 50f / 62f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
        ring.animateTo(if (selected) 1f else 0f, tween(200, easing = ExpoOut))
    }
    val halo = rememberInfiniteTransition().animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(2000), RepeatMode.Reverse))
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, if (pressed) tween(100) else tween(160, easing = ExpoOut))
    Box(
        Modifier.size(72.dp).graphicsLayer {
            scaleX = sc.value * press; scaleY = sc.value * press
            translationY = -5.dp.toPx() * ring.value
        }.clickable(src, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(72.dp).graphicsLayer { alpha = halo.value * ring.value }.clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xCCFFD55A), Color.Transparent))))
        Image(painterResource(Res.drawable.ui_circle), null, Modifier.size(62.dp))
        Image(painterResource(Shop.skinPortrait(c, Shop.skinLook(c)) ?: PORTRAITS[c]), CLASS_NAMES[c], Modifier.size(50.dp).clip(CircleShape))
        Box(Modifier.size(62.dp).drawBehind {
            val r = ring.value
            if (r > 0f) drawCircle(Pal.gold, size.minDimension / 2f - 3.dp.toPx(), alpha = r, style = Stroke(2.5.dp.toPx()))
            if (r < 1f) drawCircle(Pal.navyDeep, size.minDimension / 2f - 4.dp.toPx(), alpha = 0.4f * (1f - r))
        })
    }
}

/** Name between gold rules, role, hero XP, three 3-pip gauges and the ability chip. */
@Composable
private fun HeroPlate(picked: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(picked, transitionSpec = {
            (fadeIn(tween(180)) + slideInVertically(tween(180, easing = ExpoOut)) { it / 6 }) togetherWith fadeOut(tween(120))
        }) { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ornament(left = true)
                    GText(CLASS_NAMES[c], 30, Pal.goldLight, title = true, modifier = Modifier.padding(horizontal = 10.dp))
                    Ornament(left = false)
                }
                GText(CLASS_ROLE_V2[c], 15, Color(0xFFEBF0FF), weight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Bar(Hero.progress(), Res.drawable.ui_bar_blue, Modifier.width(100.dp), 9.dp)
        GText("Héros niv. ${Hero.level}", 13, Color(0xFFAAC8FF), weight = FontWeight.Black, shadow = false)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                for (i in 0..2) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(66.dp)) { GText(STAT_NAMES[i], 14, Color.White, align = TextAlign.Start, shadow = false) }
                    StatBar(CLASS_STATS[picked][i], STAT_BARS[i], Modifier.width(104.dp).height(12.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GText("Capacité", 13, Color(0xFFAAB4D2), shadow = false)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.width(146.dp).height(34.dp).nine(Res.drawable.ui_pill, IN_PILL, 34f / 90f).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(CLASS_ICONS[picked]), null, Modifier.size(18.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GText(ABILITY_NAMES[picked], 13, Pal.goldLight, weight = FontWeight.Black, shadow = false) }
                }
            }
        }
    }
}

/** Gold rule ending in a diamond, drawn in Canvas. */
@Composable
private fun Ornament(left: Boolean) {
    Canvas(Modifier.width(54.dp).height(12.dp)) {
        val y = size.height / 2f
        val d = 4.dp.toPx()
        val tip = if (left) d else size.width - d
        drawLine(Pal.gold, Offset(if (left) d else 0f, y), Offset(if (left) size.width else size.width - d, y), 1.5.dp.toPx())
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(tip - d, y); lineTo(tip, y - d); lineTo(tip + d, y); lineTo(tip, y + d); close() }
        drawPath(path, Pal.goldLight)
    }
}

/** 3-pip gauge; the fill slides from the previous hero's value (300 ms expoOut), drawn without recomposing. */
@Composable
private fun StatBar(value: Int, fill: DrawableResource, modifier: Modifier) {
    val track = imageResource(Res.drawable.ui_bar_track)
    val img = imageResource(fill)
    val a = remember { Animatable(value / 3f) }
    LaunchedEffect(value) { a.animateTo(value / 3f, tween(300, easing = ExpoOut)) }
    Box(modifier.drawBehind {
        val s = size.height / 44f
        nineSlice(track, IN_BAR, 0f, 0f, size.width, size.height, s)
        val fw = maxOf(size.height * 1.4f, size.width * a.value)
        nineSlice(img, IN_BAR, 0f, 0f, fw, size.height, s)
        for (k in 1..2) {
            val x = size.width * k / 3f
            drawLine(Color(0xC80C1326), Offset(x, size.height * 0.18f), Offset(x, size.height * 0.82f), 1.3.dp.toPx())
        }
    })
}

/** « Nouvelle partie » as an underlined link; the second tap within 3 s erases the run. */
@Composable
private fun NewGameLink(onNew: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    Box(
        Modifier.height(36.dp).padding(horizontal = 24.dp).clickable { Audio.play(Sfx.CLICK); if (confirm) onNew() else confirm = true },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            if (confirm) "Touche encore pour effacer ta partie" else "Nouvelle partie",
            style = TextStyle(
                color = if (confirm) Color(0xFFFF8A80) else Color(0xFFC8BEA0), fontSize = 13.sp, fontFamily = nunito(),
                fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline,
            ),
        )
    }
}

/** Bottom tab bar: night-blue band with a gold edge, four tabs, red badge on Missions. */
@Composable
private fun TabBar(ready: Int, onShop: () -> Unit, onMeta: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp).height(56.dp)
            .background(Color(0xE60C1326), RoundedCornerShape(10.dp)).border(1.dp, Pal.goldDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuTab("Boutique", Res.drawable.ui_icon_chest, 0, Modifier.weight(1f), onShop)
        MenuTab("Talents", Res.drawable.ui_icon_star, 0, Modifier.weight(1f)) { onMeta(0) }
        MenuTab("Missions", Res.drawable.ui_icon_scroll, ready, Modifier.weight(1f)) { onMeta(1) }
        MenuTab("Stats", Res.drawable.ui_icon_trophy, 0, Modifier.weight(1f)) { onMeta(2) }
    }
}

@Composable
private fun MenuTab(text: String, icon: DrawableResource, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, if (pressed) tween(100) else tween(160, easing = ExpoOut))
    Box(modifier.graphicsLayer { scaleX = press; scaleY = press }) {
        Column(
            Modifier.fillMaxWidth().height(48.dp).nine(Res.drawable.ui_tab_off, IN_TAB, 0.26f)
                .clickable(src, indication = null) { Audio.play(Sfx.CLICK); onClick() }.padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(icon), null, Modifier.size(20.dp))
            GText(text, 11, Color.White, title = true)
        }
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-8).dp).size(18.dp).clip(CircleShape).background(Pal.red),
            contentAlignment = Alignment.Center,
        ) { GText("$badge", 11, weight = FontWeight.Black, shadow = false) }
    }
}

@Composable
fun PausePanel(game: Game, onResume: () -> Unit, onSettings: () -> Unit, onMenu: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    tick
    KitPanel("Pause", dark = true) {
        GText("${CLASS_NAMES[game.heroClass]} · Niveau ${game.level} · Vague ${game.wave}", 14)
        GText("Partie sauvegardée à chaque vague", 11, Color(0xFFB8C4E0))
        KitButton(Res.drawable.ui_button_green, "Reprendre", icon = Res.drawable.ui_icon_play, onClick = onResume)
        KitButton(Res.drawable.ui_button_blue, "Réglages", icon = Res.drawable.ui_icon_gear, width = 220.dp, height = 52.dp, onClick = onSettings)
        KitButton(Res.drawable.ui_button_wood, "Menu principal", icon = Res.drawable.ui_icon_flag, width = 220.dp, height = 52.dp, onClick = onMenu)
        KitToggle("Sons", !Prefs.sfxOff, dark = true) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Sons complets", Prefs.sfxFull, dark = true) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
        KitToggle("Musique", !Prefs.musicOff, dark = true) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Vibrations", !Prefs.vibrationOff, dark = true) { Prefs.vibrationOff = !it; tick++ }
    }
}

@Composable
fun SettingsPanel(onReset: () -> Unit, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    tick
    KitPanel("Réglages", onDismiss = onClose) {
        KitToggle("Sons", !Prefs.sfxOff) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Sons complets", Prefs.sfxFull) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
        KitToggle("Musique", !Prefs.musicOff) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Vibrations", !Prefs.vibrationOff) { Prefs.vibrationOff = !it; tick++ }
        KitToggle("Fluidité max (sinon 60 Hz)", !Prefs.cap60) { Prefs.cap60 = !it; tick++ }
        GText("Difficulté", 15, Pal.ink, title = true, shadow = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 0..2) Box(
                Modifier.weight(1f).height(40.dp).nine(if (Prefs.difficulty == d) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.28f)
                    .clickable { Audio.play(Sfx.CLICK); Prefs.difficulty = d; tick++ },
                contentAlignment = Alignment.Center,
            ) { GText(DIFF_NAMES[d], 11, Color.White, title = true) }
        }
        GText("S'applique à la prochaine partie", 10, Pal.ink, shadow = false)
        KitButton(
            Res.drawable.ui_button_red, if (confirmReset) "Confirmer ?" else "Remettre à zéro",
            if (confirmReset) "trésor, talents, record, partie" else "toute la progression", width = 230.dp, height = 52.dp,
        ) { if (confirmReset) { Prefs.resetProgress(); confirmReset = false; onReset() } else confirmReset = true }
        KitButton(Res.drawable.ui_button_green, "Fermer", icon = Res.drawable.ui_icon_close, width = 200.dp, height = 52.dp, onClick = onClose)
    }
}

@Composable
private fun StatPill(value: String, name: String, modifier: Modifier) {
    Column(
        modifier.height(54.dp).nine(Res.drawable.ui_pill, IN_PILL, 54f / 90f / 1.3f).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        GText(value, 16, Color.White, weight = FontWeight.Black)
        GText(name, 8, Color(0xFFCCD6F0))
    }
}

@Composable
fun DefeatPanel(game: Game, onRevive: () -> Unit, onRetry: () -> Unit, onMenu: () -> Unit) {
    KitPanel(if (game.kDead) "Le roi est tombé" else "Le donjon est tombé", red = true, widthFrac = 0.9f) {
        KitIcon(Res.drawable.ui_icon_skull, 52.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatPill("${game.level}", "Niveau", Modifier.weight(1f))
            StatPill("${game.wave}", "Vague", Modifier.weight(1f))
            StatPill("${game.kills}", "Ennemis", Modifier.weight(1f))
            StatPill("${game.coinsCollected}", "Or ramassé", Modifier.weight(1f))
        }
        if (game.newRecord) GText("Nouveau record !", 15, Color(0xFF2E8B34), title = true, shadow = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitIcon(Res.drawable.ui_icon_coins, 20.dp)
            Spacer(Modifier.width(4.dp))
            GText("+${game.treasureWon} pièces au trésor", 12, Pal.ink, shadow = false)
        }
        if (game.canRevive) KitButton(
            Res.drawable.ui_button_gold, "Revivre", "Coûte ${game.reviveCost} pièces · une seule fois", Res.drawable.ui_icon_heart,
            textColor = Pal.ink, onClick = onRevive,
        )
        KitButton(Res.drawable.ui_button_green, "Réessayer", icon = Res.drawable.ui_icon_arrow, width = 230.dp, height = 52.dp, onClick = onRetry)
        KitButton(Res.drawable.ui_button_wood, "Menu", icon = Res.drawable.ui_icon_flag, width = 200.dp, height = 48.dp, onClick = onMenu)
    }
}

/** Talents (hero upgrades + 3x3 grid), missions and statistics. */
@Composable
fun MetaPanel(which: Int, metaTick: Int, hero: Int, onChange: () -> Unit, onClose: () -> Unit) {
    metaTick
    KitPanel(arrayOf("Talents", "Missions", "Statistiques")[which], dark = true, widthFrac = 0.94f, onDismiss = onClose) {
        Pill(Res.drawable.ui_icon_coins, "${Meta.treasure}", width = 140.dp)
        when (which) {
            0 -> {
                GText("${CLASS_NAMES[hero]} : améliorations", 12, Pal.goldLight, title = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (up in 0..2) TalentCard(UP_NAMES[up], UP_ICONS[up], Meta.level(hero, up), UP_MAX, Meta.cost(hero, up), Modifier.weight(1f).stagger(up)) {
                        if (Meta.buy(hero, up)) { Audio.play(Sfx.FORGE); onChange() }
                    }
                }
                GText("Talents du royaume", 12, Pal.goldLight, title = true)
                for (row in 0 until 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 3) {
                        val t = row * 3 + col
                        TalentCard(TALENT_NAMES[t], TALENT_ICONS[t], Talents.level(t), TALENT_MAX, Talents.cost(t), Modifier.weight(1f).stagger(3 + t)) {
                            if (Talents.buy(t)) { Audio.play(Sfx.FORGE); onChange() }
                        }
                    }
                }
            }
            1 -> for (sl in 0 until Missions.SLOTS) {
                val done = Missions.done(sl)
                Column(
                    Modifier.fillMaxWidth().stagger(sl).nine(Res.drawable.ui_panel, IN_PANEL, 0.22f).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    GText(Missions.label(sl), 13, Pal.ink, shadow = false, align = TextAlign.Start)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Bar(Missions.progress(sl).toFloat() / Missions.target(sl), Res.drawable.ui_bar_gold, Modifier.weight(1f), 14.dp)
                        Spacer(Modifier.width(8.dp))
                        GText("${minOf(Missions.progress(sl), Missions.target(sl))}/${Missions.target(sl)}", 11, Pal.ink, shadow = false)
                    }
                    if (done) KitButton(Res.drawable.ui_button_gold, "Réclamer", "+${Missions.reward(sl)} pièces", width = 180.dp, height = 46.dp, textColor = Pal.ink) {
                        Missions.claim(sl); Audio.play(Sfx.COIN_STACK); onChange()
                    } else GText("Récompense : ${Missions.reward(sl)} pièces", 10, Pal.ink, shadow = false)
                }
            }
            else -> {
                val secs = Stats.playSeconds
                listOf(
                    "Niveau de héros" to "${Hero.level}", "Meilleur niveau" to "${Stats.bestLevel}", "Meilleure vague" to "${Storage.loadBest()}",
                    "Étoiles" to "${Stats.stars} / 60", "Ennemis vaincus" to "${Stats.kills}", "Parties" to "${Stats.games}",
                    "Temps de jeu" to "${secs / 3600} h ${secs / 60 % 60} min",
                ).forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().stagger(i).padding(vertical = 2.dp)) {
                        Box(Modifier.weight(1f)) { GText(k, 14, Color(0xFFCCD6F0), align = TextAlign.Start) }
                        GText(v, 15, Pal.goldLight, weight = FontWeight.Black)
                    }
                }
            }
        }
        KitButton(Res.drawable.ui_button_green, "Fermer", icon = Res.drawable.ui_icon_close, width = 200.dp, height = 50.dp, onClick = onClose)
    }
}

@Composable
private fun TalentCard(name: String, icon: DrawableResource, lv: Int, max: Int, cost: Int, modifier: Modifier, onBuy: () -> Unit) {
    val can = lv < max && Meta.treasure >= cost
    Column(
        modifier.nine(Res.drawable.ui_card, IN_CARD, 0.22f).clickable(enabled = can) { onBuy() }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { GText(name, 9, Pal.goldLight, title = true) }
        Spacer(Modifier.height(6.dp))
        KitIcon(icon, 34.dp, grey = lv == 0)
        GText(if (max > 5) "$lv / $max" else "★".repeat(lv) + "☆".repeat(max - lv), 10, Pal.gold, weight = FontWeight.Black)
        Box(
            Modifier.fillMaxWidth().height(24.dp)
                .nine(if (lv >= max) Res.drawable.ui_button_grey else if (can) Res.drawable.ui_button_gold else Res.drawable.ui_button_grey, IN_BUTTON, 24f / 150f),
            contentAlignment = Alignment.Center,
        ) { GText(if (lv >= max) "MAX" else "● $cost", 10, if (can) Pal.ink else Color.White, weight = FontWeight.Black, shadow = false) }
    }
}

/** End of a level: stars, chest to tap, the new building, then on to the next level. */
@Composable
fun VictoryPanel(game: Game, onNext: () -> Unit) {
    var chestOpen by remember { mutableStateOf(false) }
    val shake = rememberInfiniteTransition()
    val wob by shake.animateFloat(-6f, 6f, infiniteRepeatable(tween(120), RepeatMode.Reverse))
    KitPanel("Niveau ${game.level - 1} terminé", widthFrac = 0.9f) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (k in 0 until 3) KitIcon(Res.drawable.ui_icon_star, if (k == 1) 64.dp else 48.dp, grey = k >= game.lastStars)
        }
        GText("Donjon > 80 % · aucun mur perdu · rapide", 11, Color(0xFF2E7D32), shadow = false)
        Box(
            Modifier.size(110.dp).rotate(if (chestOpen) 0f else wob).clickable(enabled = !chestOpen) { chestOpen = true; game.openChest() },
            contentAlignment = Alignment.Center,
        ) { KitIcon(Res.drawable.ui_icon_chest, 100.dp) }
        GText(if (chestOpen) "+${game.chestCoins} pièces · +${game.chestTreasure} au trésor" else "Touche le coffre !", 13, Pal.ink, title = !chestOpen, shadow = false)
        val id = game.newUnlockId
        if (id >= 0) {
            Column(
                Modifier.fillMaxWidth().nine(Res.drawable.ui_card, IN_CARD, 0.25f).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GText("Nouveau : " + (if (id == UNLOCK_MINE_CARD) "Mine et Caserne" else UNLOCK_NAMES[id]), 12, Pal.goldLight, title = true)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(unlockArt(id)), null, Modifier.size(80.dp))
                    Spacer(Modifier.width(8.dp))
                    GText(if (id == UNLOCK_MINE_CARD) "Mine : butin x1,5 · Caserne : 3 lanciers" else UNLOCK_DESC[id], 12, Pal.ink, shadow = false)
                }
            }
        }
        KitButton(Res.drawable.ui_button_green, "Niveau suivant", icon = Res.drawable.ui_icon_arrow) { if (!chestOpen) game.openChest(); onNext() }
    }
}

private fun unlockArt(id: Int): DrawableResource = when (id) {
    U_STABLE -> Res.drawable.stable
    U_ALTAR -> Res.drawable.church
    U_MILL -> Res.drawable.watermill
    U_MARKET -> Res.drawable.market
    U_TREASURY -> Res.drawable.well
    U_GATEWORKS -> Res.drawable.blacksmith
    U_STATUE -> Res.drawable.lumbermill
    U_KEEP3 -> Res.drawable.tower_royal
    UNLOCK_MINE_CARD -> Res.drawable.mine
    else -> Res.drawable.tower_stone
}
