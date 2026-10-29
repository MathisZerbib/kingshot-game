package fr.kingshot.game

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import fr.kingshot.game.resources.*
import fr.kingshot.game.resources.cinzel_bold
import fr.kingshot.game.resources.nunito_black
import fr.kingshot.game.resources.nunito_bold
import fr.kingshot.game.resources.nunito_extrabold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource

// Interface kit of the menus (art/ui/DA.md): palette, fonts, 9-slice frames, buttons, ribbons, pills.

object Pal {
    val navy = Color(0xFF15213B)
    val navyDeep = Color(0xFF0C1326)
    val royal = Color(0xFF2457D6)
    val gold = Color(0xFFF2C14E)
    val goldLight = Color(0xFFFFE896)
    val goldDark = Color(0xFFA06C14)
    val ink = Color(0xFF3A240C)
    val red = Color(0xFFD73B3B)
    val green = Color(0xFF48B44C)
    val veil = Color(0xB9080C1A)
}

/** Menu v2 curves (art/ui/DA-v2.md): expoOut for every entrance, inOut for what moves. */
val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val InOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** 9-slice insets of a kit image, in source pixels (art/ui/ui_kit.json). */
class Inset(val l: Float, val t: Float, val r: Float, val b: Float)

val IN_BUTTON = Inset(40f, 40f, 40f, 52f)
val IN_PANEL = Inset(58f, 58f, 58f, 58f)
val IN_CARD = Inset(46f, 104f, 46f, 46f)
val IN_RIBBON = Inset(142f, 40f, 142f, 40f)
val IN_BAR = Inset(24f, 20f, 24f, 20f)
val IN_TAB = Inset(34f, 34f, 34f, 10f)
val IN_PILL = Inset(47f, 43f, 47f, 43f)

/** Draws [img] stretched over (x, y, w, h) keeping its corners; [s] = screen px per source px for the borders. */
fun DrawScope.nineSlice(img: ImageBitmap, ins: Inset, x: Float, y: Float, w: Float, h: Float, s: Float, alpha: Float = 1f, filter: ColorFilter? = null) {
    val sw = img.width.toFloat()
    val sh = img.height.toFloat()
    // borders shrink if the box is smaller than both corners
    val k = minOf(s, w / (ins.l + ins.r), h / (ins.t + ins.b))
    val xs = floatArrayOf(0f, ins.l, sw - ins.r, sw)
    val ys = floatArrayOf(0f, ins.t, sh - ins.b, sh)
    val xd = floatArrayOf(x, x + ins.l * k, x + w - ins.r * k, x + w)
    val yd = floatArrayOf(y, y + ins.t * k, y + h - ins.b * k, y + h)
    for (j in 0..2) for (i in 0..2) {
        val so = IntOffset(xs[i].toInt(), ys[j].toInt())
        val ss = IntSize((xs[i + 1] - xs[i]).toInt(), (ys[j + 1] - ys[j]).toInt())
        // round both edges so neighbouring slices share a pixel boundary (no seams)
        val x0 = kotlin.math.round(xd[i]).toInt(); val y0 = kotlin.math.round(yd[j]).toInt()
        val dOff = IntOffset(x0, y0)
        val ds = IntSize((kotlin.math.round(xd[i + 1]).toInt() - x0).coerceAtLeast(0), (kotlin.math.round(yd[j + 1]).toInt() - y0).coerceAtLeast(0))
        if (ss.width <= 0 || ss.height <= 0 || ds.width <= 0 || ds.height <= 0) continue
        drawImage(img, so, ss, dOff, ds, alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.High)
    }
}

/** Background modifier: the kit image as a 9-slice frame; [k] = dp per source pixel for the borders. */
@Composable
fun Modifier.nine(res: DrawableResource, ins: Inset, k: Float = 0.36f): Modifier {
    val img = imageResource(res)
    return this.drawBehind { nineSlice(img, ins, 0f, 0f, size.width, size.height, k * density) }
}

@Composable
// kit v3: Lilita One for titles and buttons (the name stays: it is the « title face »)
fun cinzel() = FontFamily(Font(Res.font.lilita_one_regular, FontWeight.Bold))

@Composable
fun nunito() = FontFamily(
    Font(Res.font.nunito_bold, FontWeight.Bold),
    Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(Res.font.nunito_black, FontWeight.Black),
)

/** Game text: Cinzel titles (upper case) or Nunito body, with a dark outline-like shadow on images. */
@Composable
fun GText(
    text: String, size: Int, color: Color = Color.White, title: Boolean = false, weight: FontWeight = FontWeight.ExtraBold,
    shadow: Boolean = true, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, caps: Boolean = title,
) {
    val px = LocalDensity.current.density
    // menu v2 scale: never under 13 dp for body text, 11 for Cinzel labels (tabs, card titles)
    val size = size.coerceAtLeast(if (title) 11 else 13)
    BasicText(
        if (caps) text.uppercase() else text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontFamily = if (title) cinzel() else nunito(),
            fontWeight = if (title) FontWeight.Bold else weight, textAlign = align,
            letterSpacing = if (title) (size * 0.01f).sp else 0.sp, lineHeight = (size * 1.25f).sp,
            // kit v3: a soft drop shadow (0 / 2 dp, 35 %) instead of the dark engraved look
            shadow = if (shadow) Shadow(Color(0x59000000), Offset(0f, 2f * px), 4f * px) else null,
        ),
    )
}

/** Thick shiny kit button with an optional icon and subtitle; presses down to 95 %. */
@Composable
fun KitButton(
    res: DrawableResource, text: String, sub: String? = null, icon: DrawableResource? = null,
    width: Dp = 260.dp, height: Dp = 64.dp, textColor: Color = Color.White, enabled: Boolean = true,
    titleSize: Int = if (height >= 64.dp) 22 else 17, shine: Boolean = false, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, if (pressed) tween(100) else tween(160, easing = ExpoOut))
    // a light sweep across the button every 6 s (read in draw only)
    val sweep = if (shine) rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing))) else null
    Box(
        Modifier.width(width).height(height).scale(sc)
            .nine(res, IN_BUTTON, height.value / 150f)
            .drawWithContent {
                drawContent()
                if (pressed) drawRoundRect(Color.White, alpha = 0.10f, cornerRadius = CornerRadius(12.dp.toPx()))
                val t = (sweep?.value ?: 1f) / 0.12f // the sweep takes the first 12 % (~720 ms) of each 6 s cycle
                if (t < 1f) {
                    val x = -size.width * 0.3f + t * size.width * 1.6f
                    clipRect(4.dp.toPx(), 3.dp.toPx(), size.width - 4.dp.toPx(), size.height * 0.86f) {
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x55FFFFFF), Color.Transparent), Offset(x - 40.dp.toPx(), 0f), Offset(x + 40.dp.toPx(), size.height)))
                    }
                }
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled) { Audio.play(Sfx.CLICK); onClick() }
            .padding(bottom = height * 0.12f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(painterResource(icon), null, Modifier.size(height * 0.55f))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GText(text, titleSize, textColor, title = true)
                if (sub != null) AnimatedContent(sub, transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                }) { GText(it, 13, textColor.copy(alpha = 0.92f), shadow = textColor == Color.White) }
            }
        }
    }
}

/** Title ribbon (red or blue) with Cinzel text, meant to sit across a panel's top edge. */
@Composable
fun Ribbon(text: String, red: Boolean = false, width: Dp = 280.dp) {
    Box(
        Modifier.width(width).height(54.dp).nine(if (red) Res.drawable.ui_ribbon_red else Res.drawable.ui_ribbon_blue, IN_RIBBON, 54f / 170f)
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) { GText(text, 19, Color.White, title = true) }
}

/** Counter pill: icon + number. */
@Composable
fun Pill(icon: DrawableResource, text: String, modifier: Modifier = Modifier, width: Dp = 120.dp) {
    Row(
        modifier.width(width).height(38.dp).nine(Res.drawable.ui_pill, IN_PILL, 38f / 90f).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(26.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GText(text, 15, Color.White, weight = FontWeight.Black) }
    }
}

/** Round kit button (ui_circle or ui_circle_red) holding an icon, with an optional red "!" badge. */
@Composable
fun RoundButton(icon: DrawableResource, red: Boolean = false, size: Dp = 50.dp, badge: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(size + 6.dp)) {
        Box(
            Modifier.size(size).clickable { Audio.play(Sfx.CLICK); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(if (red) Res.drawable.ui_circle_red else Res.drawable.ui_circle), null, Modifier.fillMaxWidth().fillMaxHeight())
            Image(painterResource(icon), null, Modifier.size(size * 0.62f))
        }
        if (badge) Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Pal.red),
            contentAlignment = Alignment.Center,
        ) { GText("!", 12, weight = FontWeight.Black, shadow = false) }
    }
}

/** Gauge: kit track with a fill truncated to [frac]. */
@Composable
fun Bar(frac: Float, fill: DrawableResource, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val track = imageResource(Res.drawable.ui_bar_track)
    val img = imageResource(fill)
    Box(modifier.height(height).drawBehind {
        val s = size.height / 44f
        nineSlice(track, IN_BAR, 0f, 0f, size.width, size.height, s)
        val fw = size.width * frac.coerceIn(0f, 1f)
        if (fw > 2f) nineSlice(img, IN_BAR, 0f, 0f, fw, size.height, s)
    })
}

/** Modal frame: dim veil, kit panel (parchment or night blue) with its ribbon across the top edge. */
@Composable
fun KitPanel(
    title: String, dark: Boolean = false, red: Boolean = false, widthFrac: Float = 0.86f,
    onDismiss: (() -> Unit)? = null, content: @Composable () -> Unit,
) {
    // entrance (DA-v2 « Panneaux »): veil 150 ms, panel 0.96 → 1 + alpha in 220 ms expoOut, ribbon slides 12 dp
    val veil = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { veil.animateTo(1f, tween(150)) }
        pop.animateTo(1f, tween(220, easing = ExpoOut))
    }
    val reduced = Prefs.cap60
    Box(
        Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { alpha = veil.value }.background(Pal.veil)
            .then(if (onDismiss != null) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth(widthFrac).wrapContentSize().graphicsLayer {
                alpha = pop.value
                if (!reduced) { val k = 0.96f + 0.04f * pop.value; scaleX = k; scaleY = k }
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(top = 24.dp).fillMaxWidth()
                    .nine(if (dark) Res.drawable.ui_panel_dark else Res.drawable.ui_panel, IN_PANEL, 0.4f)
                    .clickable(enabled = false) {}
                    .padding(start = 22.dp, end = 22.dp, top = 44.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
            Box(Modifier.graphicsLayer { if (!reduced) translationY = -(1f - pop.value) * 12.dp.toPx() }) { Ribbon(title, red) }
        }
    }
}

/** List item entrance: 40 ms apart (at most 6 offsets), alpha + 12 dp rise in 220 ms expoOut. */
@Composable
fun Modifier.stagger(i: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(40L * minOf(i, 6)); a.animateTo(1f, tween(220, easing = ExpoOut)) }
    val reduced = Prefs.cap60
    return graphicsLayer { alpha = a.value; if (!reduced) translationY = (1f - a.value) * 12.dp.toPx() }
}

/** Kit switch: dark track, round knob, green fill when on. */
@Composable
fun KitToggle(text: String, on: Boolean, dark: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { Audio.play(Sfx.CLICK); onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { GText(text, 14, if (dark) Color.White else Pal.ink, shadow = dark, align = TextAlign.Start) }
        Box(Modifier.width(56.dp).height(28.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Bar(if (on) 1f else 0f, Res.drawable.ui_bar_green, Modifier.fillMaxWidth(), 22.dp)
            Image(painterResource(Res.drawable.ui_circle), null, Modifier.size(28.dp))
        }
    }
}

private val LOCK_GREY = ColorFilter.tint(Color(0x80FFFFFF), androidx.compose.ui.graphics.BlendMode.Modulate)

/** Icon greyed out (locked / empty states). */
@Composable
fun KitIcon(res: DrawableResource, size: Dp, grey: Boolean = false) {
    Image(painterResource(res), null, Modifier.size(size), colorFilter = if (grey) LOCK_GREY else null)
}

/** Kit images used by the in-game HUD (drawn directly by the Renderer). */
class HudImages(
    val circle: ImageBitmap, val circleRed: ImageBitmap, val ribbonBlue: ImageBitmap, val pill: ImageBitmap,
    val panelDark: ImageBitmap, val barTrack: ImageBitmap, val barRed: ImageBitmap, val barGreen: ImageBitmap, val barGold: ImageBitmap,
    val coins: ImageBitmap, val pause: ImageBitmap, val classIcons: Array<ImageBitmap>,
)

@Composable
fun rememberHud(): HudImages {
    val circle = imageResource(Res.drawable.ui_circle)
    val circleRed = imageResource(Res.drawable.ui_circle_red)
    val ribbon = imageResource(Res.drawable.ui_ribbon_blue)
    val pill = imageResource(Res.drawable.ui_pill)
    val panel = imageResource(Res.drawable.ui_panel_dark)
    val track = imageResource(Res.drawable.ui_bar_track)
    val red = imageResource(Res.drawable.ui_bar_red)
    val green = imageResource(Res.drawable.ui_bar_green)
    val gold = imageResource(Res.drawable.ui_bar_gold)
    val coins = imageResource(Res.drawable.ui_icon_coins)
    val pause = imageResource(Res.drawable.ui_icon_pause)
    val sword = imageResource(Res.drawable.ui_icon_sword)
    val bow = imageResource(Res.drawable.ui_icon_bow)
    val hammer = imageResource(Res.drawable.ui_icon_hammer)
    return remember(circle, circleRed, ribbon, pill, panel, track, red, green, gold, coins, pause, sword, bow, hammer) {
        HudImages(circle, circleRed, ribbon, pill, panel, track, red, green, gold, coins, pause, arrayOf(sword, bow, hammer))
    }
}

/** Ability effect sheets (art/fx/fx.json): white or neutral, tinted at draw time. */
class FxImages(
    val slash: ImageBitmap, val ring: ImageBitmap, val spark: ImageBitmap, val puff: ImageBitmap, val sparkle: ImageBitmap,
    val glow: ImageBitmap, val shadow: ImageBitmap, val trail: ImageBitmap, val reticle: ImageBitmap, val rune: ImageBitmap,
    val stuck: ImageBitmap, val dot: ImageBitmap,
)

@Composable
fun rememberFx(): FxImages {
    val a = imageResource(Res.drawable.fx_slash); val b = imageResource(Res.drawable.fx_ring); val c = imageResource(Res.drawable.fx_spark)
    val d = imageResource(Res.drawable.fx_puff); val e = imageResource(Res.drawable.fx_sparkle); val f = imageResource(Res.drawable.fx_glow)
    val g = imageResource(Res.drawable.fx_shadow); val h = imageResource(Res.drawable.fx_trail); val i = imageResource(Res.drawable.fx_reticle)
    val j = imageResource(Res.drawable.fx_rune); val k = imageResource(Res.drawable.fx_arrow_stuck); val l = imageResource(Res.drawable.fx_dot)
    return remember(a, b, c, d, e, f, g, h, i, j, k, l) { FxImages(a, b, c, d, e, f, g, h, i, j, k, l) }
}
