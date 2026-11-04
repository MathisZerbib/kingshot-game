package com.hexcrown.game

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hexcrown.game.resources.allDrawableResources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
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
import com.hexcrown.game.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Menu screens built from the interface kit (art/ui/DA.md, mock-ups in art/ui/mockup.py).

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
private val CLASS_ROLE_V2 = LArr("role.0", "role.1", "role.2")
private val STAT_NAMES = LArr("stat.0", "stat.1", "stat.2")
private val STAT_BARS = arrayOf(Res.drawable.ui_bar_red, Res.drawable.ui_bar_gold, Res.drawable.ui_bar_blue)
private var menuIntroPlayed = false // the opening scene runs once per launch

/** Menu v2 « le héros tient l'écran »: the background is the selector, one plate, one gold button, one tab bar. */
@Composable
fun MainMenu(
    hasSave: Boolean, savedLevel: Int, savedClass: Int, best: Int,
    picked: Int, onPick: (Int) -> Unit, metaTick: Int,
    onContinue: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onMeta: (Int) -> Unit, onDaily: () -> Unit,
    onShop: () -> Unit = {}, onRealm: () -> Unit = {},
) {
    metaTick // re-read Meta after purchases
    val reduced = Prefs.reduceMotion
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
                        Audio.play(Sfx.SWIPE)
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
                    if (Daily.available && Daily.nextStreak > 1) GText(L.f("streak", Daily.nextStreak), 13, Color.White)
                }
                Spacer(Modifier.weight(1f))
                val shown by animateIntAsState(Meta.treasure, tween(500, easing = ExpoOut))
                Pill(Res.drawable.ui_icon_coins, "$shown", width = 120.dp)
            }
            Box(Modifier.fillMaxWidth().enter(0, 420, 0f, 1.05f), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.ui_logotype), "Hexcrown", Modifier.fillMaxWidth(0.63f), contentScale = ContentScale.FillWidth)
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
                    Res.drawable.ui_button_gold, L["play.continue"], L.f("play.continue.sub", savedLevel, World.levelName(savedLevel)), Res.drawable.ui_icon_play,
                    width = 253.dp, height = 56.dp, textColor = Pal.ink, titleSize = 24, shine = true, onClick = onContinue,
                ) else KitButton(
                    Res.drawable.ui_button_gold, L["play.new"], L.f("play.new.sub", CLASS_NAMES[picked]), Res.drawable.ui_icon_play,
                    width = 253.dp, height = 56.dp, textColor = Pal.ink, titleSize = 24, shine = true, onClick = onNew,
                )
                RealmEntry(onRealm)
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

/** The line under the selected tab, sliding from one to the next. */
@Composable
fun GlideUnderline(selected: Int, count: Int) {
    val x = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { x.animateTo(selected.toFloat(), tween(220, easing = ExpoOut)) }
    Box(Modifier.fillMaxWidth().height(3.dp)) {
        Box(
            Modifier.fillMaxWidth(1f / count).height(3.dp).graphicsLayer { translationX = x.value * size.width }
                .padding(horizontal = 14.dp).background(Color(0xFFFFD65A), RoundedCornerShape(2.dp)),
        )
    }
}

/** Realm entry: a glass card with the hex icon; padlock until level 5 is done, « New » until first opened. */
@Composable
private fun RealmEntry(onRealm: () -> Unit) {
    val open = Stats.bestLevel >= 6
    Box(Modifier.padding(top = 8.dp).width(253.dp)) {
        KitButton(
            if (open) Res.drawable.ui_button_blue else Res.drawable.ui_button_grey, if (open) L["realm"] else L["realm.locked"], null, Res.drawable.ui_icon_hex,
            width = 253.dp, height = 64.dp, titleSize = 20, enabled = open, iconSize = 24.dp, caps = false,
        ) { Storage.saveInt("realm_seen", 1); onRealm() }
        if (open && Storage.loadInt("realm_seen") == 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 6.dp).background(Pal.red, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
        ) { GText(L["realm.new"], 13, weight = FontWeight.Black, shadow = false) }
    }
}

/** The 7 regions around home, as hexes; crowned ones in gold. */
@Composable
private fun RealmMap(m: Realm) {
    GText(L["realm.map"], 14, Color.White, title = true, caps = false)
    val gold = remember { Animatable(0f) }
    LaunchedEffect(Unit) { gold.animateTo(1f, tween(400, easing = ExpoOut)) }
    Canvas(Modifier.size(200.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val k = size.minDimension / 2f / (m.maxX - m.minX).coerceAtLeast(m.maxY - m.minY) * 1.8f
        fun hex(x: Float, y: Float, r: Float, col: Color) {
            val p = androidx.compose.ui.graphics.Path()
            for (i in 0..5) { val a = (60f * i - 90f) * 0.0174533f; val px = x + kotlin.math.cos(a) * r; val py = y + kotlin.math.sin(a) * r; if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
            p.close()
            drawPath(p, col)
        }
        hex(c.x, c.y, 22.dp.toPx(), Color(0xFF4A7BD6))
        for (i in 0 until REGIONS) hex(c.x + (m.centerX[i] - World.CASTLE_X) * k, c.y + (m.centerY[i] - World.CASTLE_Y) * k, 18.dp.toPx(), if (m.crowned[i]) androidx.compose.ui.graphics.lerp(Color(0xFF3A4464), Color(0xFFFFD65A), gold.value) else Color(0xFF3A4464))
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
            if (r > 0f) drawCircle(Color(0xFFFFD65A), size.minDimension / 2f - 3.dp.toPx(), alpha = r, style = Stroke(2.5.dp.toPx()))
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
                    GText(CLASS_NAMES[c], 30, Color.White, title = true, modifier = Modifier.padding(horizontal = 10.dp))
                    Ornament(left = false)
                }
                GText(CLASS_ROLE_V2[c], 15, Color(0xFFEBF0FF), weight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Bar(Hero.progress(), Res.drawable.ui_bar_blue, Modifier.width(100.dp), 9.dp)
        GText(L.f("hero.level", Hero.level), 13, Color(0xFFAAC8FF), weight = FontWeight.Black, shadow = false)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                for (i in 0..2) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(66.dp)) { GText(STAT_NAMES[i], 14, Color.White, align = TextAlign.Start, shadow = false) }
                    StatBar(CLASS_STATS[picked][i], STAT_BARS[i], Modifier.width(104.dp).height(12.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GText(L["ability"], 13, Color(0xFFAAB4D2), shadow = false)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.width(146.dp).height(34.dp).nine(Res.drawable.ui_pill, IN_PILL, 34f / 90f).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(CLASS_ICONS[picked]), null, Modifier.size(18.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GText(ABILITY_NAMES[picked], 13, Color.White, weight = FontWeight.Black, shadow = false) }
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
        // kit v3: two thin white strokes (45 %) ending in a small gold dot
        drawLine(Color.White.copy(alpha = 0.45f), Offset(if (left) d * 2f else 0f, y), Offset(if (left) size.width else size.width - d * 2f, y), 1.5.dp.toPx())
        drawCircle(Color(0xFFFFD65A), d * 0.75f, Offset(tip, y))
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
            if (confirm) L["newgame.confirm"] else L["newgame"],
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
            .background(Color(0xEB101A30), RoundedCornerShape(14.dp)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuTab(L["tab.shop"], Res.drawable.ui_icon_chest, 0, Modifier.weight(1f), onShop)
        MenuTab(L["tab.talents"], Res.drawable.ui_icon_star, 0, Modifier.weight(1f)) { onMeta(0) }
        MenuTab(L["tab.missions"], Res.drawable.ui_icon_scroll, ready, Modifier.weight(1f)) { onMeta(1) }
        MenuTab(L["tab.stats"], Res.drawable.ui_icon_trophy, 0, Modifier.weight(1f)) { onMeta(2) }
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
            GText(text, 11, Color.White, title = true, caps = false)
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
    KitPanel(L["pause"], dark = true) {
        val rm = game.realm
        if (rm != null) RealmMap(rm) else GText(L.f("pause.sub", CLASS_NAMES[game.heroClass], game.level, game.wave), 14)
        if (!game.isRealm) GText(L["pause.saved"], 11, Color(0xFFB8C4E0))
        KitButton(Res.drawable.ui_button_green, L["resume"], icon = Res.drawable.ui_icon_play, onClick = onResume)
        KitButton(Res.drawable.ui_button_blue, L["settings"], icon = Res.drawable.ui_icon_gear, width = 220.dp, height = 52.dp, onClick = onSettings)
        KitButton(Res.drawable.ui_button_wood, L["mainmenu"], icon = Res.drawable.ui_icon_flag, width = 220.dp, height = 52.dp, onClick = onMenu)
        KitToggle(L["sfx"], !Prefs.sfxOff, dark = true) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
        KitToggle(L["sfx.full"], Prefs.sfxFull, dark = true) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
        KitToggle(L["music"], !Prefs.musicOff, dark = true) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
        KitToggle(L["vibration"], !Prefs.vibrationOff, dark = true) { Prefs.vibrationOff = !it; tick++ }
    }
}

@Composable
fun SettingsPanel(onReset: () -> Unit, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    tick
    var credits by remember { mutableStateOf(false) }
    val maxH = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.72f).toDp() }
    KitPanel(L["settings"], onDismiss = onClose) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxH).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GText(L["language"], 15, Pal.ink, title = true, shadow = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (l in 0..1) Box(
                    Modifier.weight(1f).height(48.dp).nine(if (L.lang == l) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.28f)
                        .clickable { Audio.play(Sfx.CLICK); L.setLang(l) },
                    contentAlignment = Alignment.Center,
                ) { GText(L["lang.$l"], 13, if (L.lang == l) Color.White else Pal.ink, title = true, caps = false, shadow = L.lang == l) }
            }
            KitToggle(L["sfx"], !Prefs.sfxOff) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
            KitToggle(L["sfx.full"], Prefs.sfxFull) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
            KitToggle(L["music"], !Prefs.musicOff) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
            KitToggle(L["vibration"], !Prefs.vibrationOff) { Prefs.vibrationOff = !it; tick++ }
            KitToggle(L["reduce.motion"], Prefs.reduceMotion) { Prefs.reduceMotion = it; tick++ }
            KitToggle(L["battery"], Prefs.cap60) { Prefs.cap60 = it; tick++ }
            KitToggle(L["large.text"], Prefs.largeText) { Prefs.largeText = it; tick++ }
            KitToggle(L["show.fps"], Prefs.showFps) { Prefs.showFps = it; tick++ }
            GText(L["difficulty"], 15, Pal.ink, title = true, shadow = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0..2) Box(
                    Modifier.weight(1f).height(48.dp).nine(if (Prefs.difficulty == d) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.28f)
                        .clickable { Audio.play(Sfx.CLICK); Prefs.difficulty = d; tick++ },
                    contentAlignment = Alignment.Center,
                ) { GText(DIFF_NAMES[d], 13, if (Prefs.difficulty == d) Color.White else Pal.ink, title = true, caps = false, shadow = Prefs.difficulty == d) }
            }
            GText(L["difficulty.note"], 13, Pal.ink, shadow = false)
            SettingsLink(if (Storage.loadInt("tuto_done") == 1) L["tuto.replay"] else L["tuto.replay.set"]) { Storage.saveInt("tuto_done", 0); tick++ }
            SettingsLink(L["credits"]) { credits = !credits }
            if (credits) GText(L["credits.body"], 13, Pal.ink, shadow = false)
            PlatformHooks.openUrl?.let { open -> SettingsLink(L["privacy"]) { open(PRIVACY_URL) } }
            KitButton(
                Res.drawable.ui_button_red, if (confirmReset) L["reset.confirm"] else L["reset"],
                if (confirmReset) L["reset.what"] else L["reset.all"], width = 230.dp, height = 52.dp,
            ) { if (confirmReset) { Prefs.resetProgress(); confirmReset = false; onReset() } else confirmReset = true }
            PlatformHooks.version?.let { GText(L.f("version", it), 13, Pal.ink, shadow = false) }
        }
        KitButton(Res.drawable.ui_button_green, L["close"], icon = Res.drawable.ui_icon_close, width = 200.dp, height = 52.dp, onClick = onClose)
    }
}

private const val PRIVACY_URL = "https://mathiszerbib.github.io/kingshot-game/privacy"

@Composable
private fun SettingsLink(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).clickable { Audio.play(Sfx.CLICK); onClick() }, contentAlignment = Alignment.Center) {
        GText(text, 14, Pal.ink, shadow = false)
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
    // the scene goes grey behind the panel
    val grey = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grey.animateTo(1f, tween(400)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = grey.value }.background(Color(0x996E7078)))
    KitPanel(if (game.kDead) L["over.king"] else L["over.keep"], red = true, widthFrac = 0.9f) {
        KitIcon(Res.drawable.ui_icon_skull, 52.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatPill("${game.wave}", L["word.wave"], Modifier.weight(1f))
            StatPill("${game.kills}", L["word.enemies"], Modifier.weight(1f))
            StatPill("${game.coinsCollected}", L["word.gold"], Modifier.weight(1f))
        }
        if (game.newRecord) GText(L["record"], 15, Color(0xFF2E8B34), title = true, shadow = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitIcon(Res.drawable.ui_icon_coins, 20.dp)
            Spacer(Modifier.width(4.dp))
            GText(L.f("over.treasure", game.treasureWon), 14, Pal.ink, shadow = false)
        }
        GText(L[game.defeatTip], 14, Color(0xFF3A4464), shadow = false)
        if (game.canRevive) KitButton(
            Res.drawable.ui_button_blue, L["revive"], L.f("revive.sub", game.reviveCost), Res.drawable.ui_icon_heart,
            width = 250.dp, height = 56.dp, onClick = onRevive,
        )
        KitButton(Res.drawable.ui_button_gold, L["retry"], icon = Res.drawable.ui_icon_arrow, width = 250.dp, height = 60.dp, textColor = Pal.ink, onClick = onRetry)
        Box(Modifier.height(48.dp).padding(horizontal = 24.dp).clickable { Audio.play(Sfx.CLICK); onMenu() }, contentAlignment = Alignment.Center) {
            BasicText(
                L["menu"],
                style = TextStyle(color = Color(0xFF3A4464), fontSize = 15.sp, fontFamily = nunito(), fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline),
            )
        }
    }
}

/** Talents (hero upgrades + 3x3 grid), missions and statistics. */
@Composable
fun MetaPanel(which: Int, metaTick: Int, hero: Int, onChange: () -> Unit, onClose: () -> Unit) {
    metaTick
    KitPanel(arrayOf(L["tab.talents"], L["tab.missions"], L["stats.title"])[which], dark = true, widthFrac = 0.94f, onDismiss = onClose) {
        Pill(Res.drawable.ui_icon_coins, "${Meta.treasure}", width = 140.dp)
        when (which) {
            0 -> {
                GText(L.f("meta.upgrades", CLASS_NAMES[hero]), 12, Color.White, title = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (up in 0..2) TalentCard(UP_NAMES[up], UP_ICONS[up], Meta.level(hero, up), UP_MAX, Meta.cost(hero, up), Modifier.weight(1f).stagger(up)) {
                        if (Meta.buy(hero, up)) { Audio.play(Sfx.FORGE); onChange() }
                    }
                }
                GText(L["meta.talents"], 12, Color.White, title = true)
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
                    if (done) KitButton(Res.drawable.ui_button_gold, L["claim"], L.f("claim.sub", Missions.reward(sl)), width = 180.dp, height = 46.dp, textColor = Pal.ink) {
                        Missions.claim(sl); Audio.play(Sfx.COIN_STACK); onChange()
                    } else GText(L.f("reward", Missions.reward(sl)), 10, Pal.ink, shadow = false)
                }
            }
            else -> {
                // Stats, Codex and achievements share the tab
                var sub by remember { mutableIntStateOf(0) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in 0..2) Box(
                        Modifier.weight(1f).height(38.dp).nine(if (sub == k) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.26f)
                            .clickable { Audio.play(Sfx.TAB); sub = k },
                        contentAlignment = Alignment.Center,
                    ) { GText(L["statsub.$k"], 13, Color.White, title = true, caps = false) }
                }
                GlideUnderline(sub, 3)
                when (sub) {
                    0 -> {
                val secs = Stats.playSeconds
                listOf(
                    L["st.hero"] to "${Hero.level}", L["st.level"] to "${Stats.bestLevel}", L["st.wave"] to "${Storage.loadBest()}",
                    L["st.stars"] to "${Stats.stars} / 60", L["st.kills"] to "${Stats.kills}", L["st.games"] to "${Stats.games}",
                    L["st.time"] to "${secs / 3600} h ${secs / 60 % 60} min",
                ).forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().stagger(i).padding(vertical = 2.dp)) {
                        Box(Modifier.weight(1f)) { GText(k, 14, Color(0xFFCCD6F0), align = TextAlign.Start) }
                        GText(v, 15, Color.White, weight = FontWeight.Black)
                    }
                }
                    }
                    1 -> CodexGrid()
                    else -> AchievementList()
                }
            }
        }
        KitButton(Res.drawable.ui_button_green, L["close"], icon = Res.drawable.ui_icon_close, width = 200.dp, height = 50.dp, onClick = onClose)
    }
}

/** 12 relics, grey until taken once; 12/12 unlocks the Chronicler skin. */
@Composable
internal fun CodexGrid() {
    GText(L.f("codex.count", Relics.codexCount, RELIC_COUNT), 14, Color.White)
    for (row in 0 until 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (col in 0 until 4) {
            val i = row * 4 + col
            Column(Modifier.weight(1f).stagger(i), horizontalAlignment = Alignment.CenterHorizontally) {
                val res = Res.allDrawableResources[Relics.iconName(i)]
                if (res != null) KitIcon(res, 52.dp, grey = !Relics.seen(i))
                GText(if (Relics.seen(i)) Relics.name(i) else "?", 11, if (Relics.seen(i)) Color.White else Color(0xFF8A94B0), title = true, caps = false)
            }
        }
    }
}

@Composable
internal fun AchievementList() {
    GText(L.f("ach.count", Ach.count, Ach.ids.size), 14, Color.White)
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (id in Ach.ids) Row(verticalAlignment = Alignment.CenterVertically) {
            KitIcon(Res.drawable.ui_icon_trophy, 28.dp, grey = !Ach.done(id))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                GText(L["ach.$id"], 15, if (Ach.done(id)) Color.White else Color(0xFF8A94B0), title = true, caps = false, align = TextAlign.Start)
                GText(L["ach.$id.how"], 13, Color(0xFFAAB4D2), shadow = false, align = TextAlign.Start)
            }
        }
    }
}

/** After a boss: choose 1 relic out of 3. Cards arrive 40 ms apart; the rare one has an animated gold edge. */
@Composable
fun RelicPanel(game: Game, onTake: (Int) -> Unit) {
    val glow = rememberInfiniteTransition().animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    Box(Modifier.fillMaxSize().background(Pal.veil).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ribbon(L["relic.title"])
            for (k in 0 until game.relicChoiceCount) {
                val r = game.relicChoices[k]
                Row(
                    Modifier.fillMaxWidth().stagger(k)
                        .drawBehind {
                            if (Relics.isRare(r)) drawRoundRect(Color(0xFFFFD65A), Offset(-3.dp.toPx(), -3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()), alpha = glow.value, style = Stroke(3.dp.toPx()))
                        }
                        .nine(Res.drawable.ui_panel_dark, IN_PANEL, 0.3f).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val res = Res.allDrawableResources[Relics.iconName(r)]
                    if (res != null) KitIcon(res, 96.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        GText(Relics.name(r), 22, Color.White, title = true, caps = false)
                        GText(Relics.desc(r), 15, Color(0xFFE9E2CF), weight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        KitButton(Res.drawable.ui_button_gold, L["relic.take"], width = 150.dp, height = 44.dp, titleSize = 16, textColor = Pal.ink) { onTake(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TalentCard(name: String, icon: DrawableResource, lv: Int, max: Int, cost: Int, modifier: Modifier, onBuy: () -> Unit) {
    val can = lv < max && Meta.treasure >= cost
    Column(
        modifier.nine(Res.drawable.ui_card, IN_CARD, 0.22f).clickable(enabled = can) { onBuy() }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { GText(name, 9, Color.White, title = true) }
        Spacer(Modifier.height(6.dp))
        KitIcon(icon, 34.dp, grey = lv == 0)
        GText(if (max > 5) "$lv / $max" else "★".repeat(lv) + "☆".repeat(max - lv), 10, Pal.gold, weight = FontWeight.Black)
        Box(
            Modifier.fillMaxWidth().height(24.dp)
                .nine(if (lv >= max) Res.drawable.ui_button_grey else if (can) Res.drawable.ui_button_gold else Res.drawable.ui_button_grey, IN_BUTTON, 24f / 150f),
            contentAlignment = Alignment.Center,
        ) { GText(if (lv >= max) L["max"] else "● $cost", 10, if (can) Pal.ink else Color.White, weight = FontWeight.Black, shadow = false) }
    }
}

/** End of a level: stars, chest to tap, the new building, then on to the next level. */
@Composable
fun VictoryPanel(game: Game, onNext: () -> Unit) {
    var chestOpen by remember { mutableStateOf(false) }
    val shake = rememberInfiniteTransition()
    val wob by shake.animateFloat(-6f, 6f, infiniteRepeatable(tween(120), RepeatMode.Reverse))
    KitPanel(L.f("victory.title", game.level - 1), widthFrac = 0.9f) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (k in 0 until 3) FallingStar(k, k < game.lastStars)
        }
        GText(L["victory.stars"], 11, Color(0xFF2E7D32), shadow = false)
        Box(
            Modifier.size(110.dp).rotate(if (chestOpen) 0f else wob).clickable(enabled = !chestOpen) { chestOpen = true; game.openChest() },
            contentAlignment = Alignment.Center,
        ) { KitIcon(Res.drawable.ui_icon_chest, 100.dp) }
        // the coins count up instead of appearing
        val count = remember { Animatable(0f) }
        LaunchedEffect(chestOpen) { if (chestOpen) count.animateTo(1f, tween(800, easing = ExpoOut)) }
        GText(if (chestOpen) L.f("chest.got", (game.chestCoins * count.value).toInt(), (game.chestTreasure * count.value).toInt()) else L["chest.tap"], 13, Pal.ink, title = !chestOpen, shadow = false)
        val id = game.newUnlockId
        if (id >= 0) {
            Column(
                Modifier.fillMaxWidth().nine(Res.drawable.ui_card, IN_CARD, 0.25f).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GText(L.f("new.named", if (id == UNLOCK_MINE_CARD) L["mine.barracks"] else UNLOCK_NAMES[id]), 12, Color.White, title = true)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(unlockArt(id)), null, Modifier.size(80.dp))
                    Spacer(Modifier.width(8.dp))
                    GText(if (id == UNLOCK_MINE_CARD) L["mine.desc"] else UNLOCK_DESC[id], 12, Pal.ink, shadow = false)
                }
            }
        }
        KitButton(Res.drawable.ui_button_gold, L["next"], icon = Res.drawable.ui_icon_arrow, textColor = Pal.ink) { if (!chestOpen) game.openChest(); onNext() }
    }
}

/** Falls into place, 120 ms after the previous one, with its own ding. */
@Composable
private fun FallingStar(k: Int, earned: Boolean) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var rang = false
        a.animateTo(1f, tween(320, delayMillis = 200 + 120 * k, easing = ExpoOut)) {
            if (!rang && value > 0f) { rang = true; if (earned) Audio.play(Sfx.STAR, 1f, 1f + 0.08f * k) }
        }
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value
        if (!Prefs.reduceMotion) { translationY = -(1f - a.value) * 70.dp.toPx(); val s = 1.5f - 0.5f * a.value; scaleX = s; scaleY = s }
    }) { KitIcon(Res.drawable.ui_icon_star, if (k == 1) 64.dp else 48.dp, grey = !earned) }
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
