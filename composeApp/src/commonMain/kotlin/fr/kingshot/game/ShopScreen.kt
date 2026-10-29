package fr.kingshot.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.kingshot.game.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.sin

// Shop screen (art/ui/DA-v2.md « Boutique »): header, four categories, showcase, 3-column grid, coin rain on purchase.

private val CAT_NAMES = arrayOf("Rois", "Étendards", "Montures", "Traînées")
private val CAT_ICONS = arrayOf(Res.drawable.ui_icon_crown, Res.drawable.ui_icon_flag, Res.drawable.ui_icon_heart, Res.drawable.ui_icon_coins)
private val CAT_KIND = arrayOf("skin", "étendard", "monture", "traînée")
private val BANNER_COLORS = arrayOf(Color(0xFF2F5BD6), Color(0xFF8A3FC8), Color(0xFF2FA65A), Color(0xFF1C1C22), Color(0xFFF2F4F8))
private val MOUNT_COLORS = arrayOf(Color(0xFFF2EFE8), Color(0xFF8A4B26), Color(0xFF2A221F), Color(0xFF9AA0A6), Color(0xFFE0B66A))
private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun ShopScreen(hero: Int, onClose: () -> Unit) {
    Shop.version.intValue // re-read after purchases
    val scope = rememberCoroutineScope()
    var cat by remember { mutableIntStateOf(SHOP_SKIN) }
    val items = Shop.items(cat, if (cat == SHOP_SKIN) hero else -1)
    var sel by remember(cat) { mutableStateOf((Shop.equipped(cat, if (cat == SHOP_SKIN) hero else -1) ?: items.first()).id) }
    val shown = remember { Animatable(Meta.treasure.toFloat()) }
    val spots = remember { mutableStateMapOf<String, Offset>() }
    var pill by remember { mutableStateOf(Offset.Zero) }
    val rain = remember { Animatable(1f) }
    var rainTo by remember { mutableStateOf(Offset.Zero) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = ExpoOut)) }
    val coin = imageResource(Res.drawable.ui_icon_coins)

    fun buy(id: String) {
        val before = Meta.treasure
        if (!Shop.buy(id)) return
        Audio.haptic(Haptic.MEDIUM)
        rainTo = spots[id] ?: Offset.Zero
        scope.launch { rain.snapTo(0f); rain.animateTo(1f, tween(500)) }
        scope.launch { shown.snapTo(before.toFloat()); shown.animateTo(Meta.treasure.toFloat(), tween(500, easing = ExpoOut)) }
    }

    Box(Modifier.fillMaxSize().background(Pal.navyDeep).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Image(painterResource(MENU_BG_RES[hero]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Color(0xB0080C1A)))
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 6.dp)
                .graphicsLayer { alpha = enter.value; val k = 0.96f + 0.04f * enter.value; scaleX = k; scaleY = k },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Res.drawable.ui_icon_close, size = 46.dp, onClick = onClose)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { GText("Boutique", 26, Pal.goldLight, title = true) }
                Box(Modifier.onGloballyPositioned { pill = it.boundsInRoot().center }) {
                    TreasurePill(shown)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0..3) CatTab(k, k == cat, Modifier.weight(1f)) { if (k != cat) { Audio.play(Sfx.CLICK); cat = k } }
            }
            Spacer(Modifier.height(10.dp))
            val item = Shop.item(sel) ?: items.first()
            AnimatedContent(item.id, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { id ->
                Showcase(Shop.item(id) ?: item, hero) { buy(it) }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (row in items.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, it) in row.withIndex()) {
                        ShopCard(
                            it, it.id == sel, Modifier.weight(1f).onGloballyPositioned { c -> spots[it.id] = c.boundsInRoot().center },
                            stagger = items.indexOf(it),
                        ) { Audio.play(Sfx.CLICK); sel = it.id }
                        if (i == row.lastIndex) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GText("Les skins ne changent pas la difficulté. Tout s'achète avec le trésor gagné en jeu.", 13, Color(0xFFAAB4D2), weight = FontWeight.Bold, shadow = false)
            Spacer(Modifier.height(6.dp))
        }
        // purchase: 12 coins fly from the treasure pill to the card (500 ms), drawn in one Canvas
        Canvas(Modifier.fillMaxSize()) {
            val p = rain.value
            if (p >= 1f) return@Canvas
            val r = 11.dp.toPx()
            for (i in 0 until 12) {
                val t = ((p * 500f - i * 12f) / 350f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) continue
                val x = pill.x + (rainTo.x - pill.x) * t + sin(i * 1.7f) * 24.dp.toPx() * sin(t * 3.14f)
                val y = pill.y + (rainTo.y - pill.y) * t - sin(t * 3.14f) * 60.dp.toPx()
                drawImage(coin, IntOffset.Zero, IntSize(coin.width, coin.height), IntOffset((x - r).toInt(), (y - r).toInt()), IntSize((2 * r).toInt(), (2 * r).toInt()))
            }
        }
    }
}

private val MENU_BG_RES = arrayOf(Res.drawable.menu_bg_warrior, Res.drawable.menu_bg_ranger, Res.drawable.menu_bg_builder)

@Composable
private fun TreasurePill(shown: Animatable<Float, *>) {
    Pill(Res.drawable.ui_icon_coins, "${shown.value.toInt()}", width = 118.dp)
}

@Composable
private fun CatTab(k: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(50.dp).nine(if (on) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.26f)
            .clickable(onClick = onClick).padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(CAT_ICONS[k]), null, Modifier.size(20.dp))
        GText(CAT_NAMES[k], 11, if (on) Pal.ink else Color.White, title = true, shadow = !on)
    }
}

/** Big preview on the hero's own background, name, kind, description and the one action. */
@Composable
private fun Showcase(item: ShopItem, hero: Int, onBuy: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().nine(Res.drawable.ui_panel_dark, IN_PANEL, 0.3f).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Image(painterResource(MENU_BG_RES[hero]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Preview(item, 116.dp, locked = !Shop.unlocked(item.id))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            GText(item.name, 19, Pal.goldLight, title = true)
            GText(if (item.kind == SHOP_SKIN) "${CLASS_NAMES[item.cls]} · skin" else CAT_KIND[item.kind].replaceFirstChar { it.uppercase() }, 13, Color(0xFFAAB4D2), shadow = false)
            Spacer(Modifier.height(4.dp))
            GText(item.desc, 13, Color.White, weight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ShopAction(item, big = true) { onBuy(item.id) }
        }
    }
}

/** The action of an item: buy (gold, price), equip (blue), equipped (green), or locked (grey + what to do). */
@Composable
private fun ShopAction(item: ShopItem, big: Boolean, onBuy: () -> Unit) {
    Shop.version.intValue // owned / equipped live in Storage: re-read on every purchase or equip
    val w = if (big) 170.dp else 96.dp
    val h = if (big) 50.dp else 38.dp
    val ts = if (big) 17 else 13
    val eq = Shop.equipped(item.kind, item.cls)?.id == item.id || (Shop.equipped(item.kind, item.cls) == null && item.price == 0)
    when {
        eq -> KitButton(Res.drawable.ui_button_green, "Équipé", width = w, height = h, titleSize = ts, enabled = false) {}
        Shop.owned(item.id) -> KitButton(Res.drawable.ui_button_blue, "Équiper", width = w, height = h, titleSize = ts) { Shop.equip(item.id) }
        !Shop.unlocked(item.id) -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(h)) {
            Image(painterResource(Res.drawable.ui_icon_lock), null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            GText(Shop.unlockText(item.unlock), 13, Color(0xFFC8C8DC), shadow = false)
        }
        else -> {
            val afford = Meta.treasure >= item.price
            KitButton(
                if (afford) Res.drawable.ui_button_gold else Res.drawable.ui_button_grey, price(item.price),
                if (big && !afford) "Pas assez de pièces" else null, Res.drawable.ui_icon_coins,
                width = w, height = h, titleSize = ts, textColor = if (afford) Pal.ink else Color.White, enabled = afford, onClick = onBuy,
            )
        }
    }
}

private fun price(p: Int) = if (p >= 1000) "${p / 1000} ${(p % 1000).toString().padStart(3, '0')}" else "$p"

@Composable
private fun ShopCard(item: ShopItem, selected: Boolean, modifier: Modifier, stagger: Int, onClick: () -> Unit) {
    Shop.version.intValue
    val eq = Shop.equipped(item.kind, item.cls)?.id == item.id || (Shop.equipped(item.kind, item.cls) == null && item.price == 0)
    val locked = !Shop.unlocked(item.id)
    val inA = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { kotlinx.coroutines.delay(40L * minOf(stagger, 6)); inA.animateTo(1f, tween(220, easing = ExpoOut)) }
    Column(
        modifier.graphicsLayer { alpha = inA.value; translationY = (1f - inA.value) * 12.dp.toPx() }
            .drawBehind {
                if (eq) for (k in 3 downTo 1) drawRoundRect(Pal.gold, Offset(-k * 3.dp.toPx(), -k * 3.dp.toPx()), Size(size.width + k * 6.dp.toPx(), size.height + k * 6.dp.toPx()), CornerRadius(14.dp.toPx()), alpha = 0.12f)
                if (selected) drawRoundRect(Pal.goldLight, cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(2.5.dp.toPx()))
            }
            .nine(Res.drawable.ui_card, IN_CARD, 0.32f).clickable(onClick = onClick).padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(28.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { GText(item.short, 11, Pal.goldLight, title = true) }
        Spacer(Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center) {
            Preview(item, 84.dp, locked)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            if (locked) GText(Shop.unlockText(item.unlock), 13, Color(0xFF6B6250), shadow = false, align = TextAlign.Center)
            else ShopAction(item, big = false) { onClick() }
        }
    }
}

/** What the item looks like: skin portrait, banner pennant, coat swatch or coin trail. Locked = grey + padlock. */
@Composable
private fun Preview(item: ShopItem, dim: androidx.compose.ui.unit.Dp, locked: Boolean) {
    Box(Modifier.size(dim), contentAlignment = Alignment.Center) {
        val filter = if (locked) GREY else null
        when (item.kind) {
            SHOP_SKIN -> {
                val res = Shop.skinPortrait(item.cls, item.look)
                Image(painterResource(res ?: PORTRAITS[item.cls]), item.name, Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                    colorFilter = filter ?: if (res == null) Shop.skinFilter(item.look) else null)
            }
            SHOP_BANNER -> {
                val c = BANNER_COLORS[item.look]
                Canvas(Modifier.fillMaxSize(0.8f)) {
                    val w = size.width; val h = size.height
                    drawRect(Color(0xFF6B4A2A), Offset(w * 0.18f, h * 0.05f), Size(w * 0.06f, h * 0.9f))
                    val flag = Path().apply { moveTo(w * 0.24f, h * 0.1f); lineTo(w * 0.86f, h * 0.1f); lineTo(w * 0.72f, h * 0.32f); lineTo(w * 0.86f, h * 0.55f); lineTo(w * 0.24f, h * 0.55f); close() }
                    drawPath(flag, if (locked) Color(0xFF8A8A8A) else c)
                    drawPath(flag, Pal.gold, style = Stroke(2.dp.toPx()))
                }
            }
            SHOP_MOUNT -> Canvas(Modifier.fillMaxSize(0.78f)) {
                drawCircle(if (locked) Color(0xFF8A8A8A) else MOUNT_COLORS[item.look])
                drawCircle(Pal.gold, style = Stroke(3.dp.toPx()))
                drawCircle(Color(0x33FFFFFF), radius = size.minDimension * 0.28f, center = Offset(size.width * 0.38f, size.height * 0.36f))
            }
            else -> {
                val c = TRAIL_COLORS[item.look]
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 1..4) drawCircle(if (locked) Color(0xFF8A8A8A) else c, size.width * (0.11f - k * 0.018f), Offset(size.width * (0.5f - k * 0.13f), size.height * (0.5f + k * 0.08f)), alpha = 0.9f - k * 0.18f)
                }
                Image(painterResource(Res.drawable.ui_icon_coins), null, Modifier.fillMaxSize(0.5f), colorFilter = filter)
            }
        }
        if (locked) Image(painterResource(Res.drawable.ui_icon_lock), null, Modifier.fillMaxSize(0.38f))
    }
}
