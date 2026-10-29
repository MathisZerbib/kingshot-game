package fr.kingshot.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val UNIT_SCALE = 1.5f

private const val K_TREE = 0
private const val K_ROCK = 1
private const val K_HOUSE = 2
private const val K_CASTLE = 3
private const val K_TOWER = 4
private const val K_WALL = 5
private const val K_ENEMY = 6
private const val K_ARCHER = 7
private const val K_KING = 8
private const val K_COIN = 9
private const val K_PROP = 10
private const val K_RING = 11
private const val K_CORPSE = 12
private const val K_BUILDING = 13 // idx 0 farm, 1 barracks, 2 mine
private const val K_LANCER = 14

// One palette per level, cycling: spring, autumn, winter (grass, dark patch, light patch, cliff top, cliff top light)
private val SEASONS = arrayOf(
    arrayOf(Color(0xFF8DC24F), Color(0xFF7DB443), Color(0xFF9CCD5C), Color(0xFF86A83F), Color(0xFF94B54A), Color(0xFF5E9A2E)),
    arrayOf(Color(0xFFC2B25A), Color(0xFFB2A04C), Color(0xFFD0BF68), Color(0xFFAE9A45), Color(0xFFBCA851), Color(0xFF9A7F33)),
    arrayOf(Color(0xFFE4ECEF), Color(0xFFD2DDE3), Color(0xFFF1F5F7), Color(0xFFD8E2E7), Color(0xFFE8EEF1), Color(0xFFAFC0CA)),
    arrayOf(Color(0xFF5F9E3A), Color(0xFF4F8C30), Color(0xFF6DAD45), Color(0xFF5A8A34), Color(0xFF679A3D), Color(0xFF3E7A24)),
)
/** Ground palette per chapter: plain = spring, forest = deep green, snow = winter, kingdom = golden autumn. */
private val CHAPTER_SEASON = intArrayOf(0, 3, 2, 1)
private val FLOWERS = arrayOf(Color(0xFFFFFFFF), Color(0xFFFFD84A), Color(0xFFFF8FB4))
private val FLOWER_HEART = Color(0xFFFFB12B)
private val DIRT_LIGHT = Color(0xFFE6C493)
private val PEBBLE = Color(0xFFB9A88F)
private val RIVER_BANK = Color(0xFF7FA35A)
private val RIVER_WATER = Color(0xFF3E9AD6)
private val RIVER_LIGHT = Color(0xFF8FD3F7)
private val PLANK = Color(0xFFA0703F)
private val PLANK_DARK = Color(0xFF6E4A27)
private val PEBBLE_DARK = Color(0x33000000)
private val DIRT = Color(0xFFDDB57F)
private val DIRT_EDGE = Color(0xFFCDA36C)
private val CLIFF_FACE = Color(0xFFB09A80)
private val CLIFF_FACE_DARK = Color(0x33000000)
private val CLIFF_FOOT = Color(0x40000000)
private val CLIFF_LIP = Color(0x55000000)
private val CLIFF_SHADOW = Color(0x26000000)
private val SHADOW = Color(0x33000000)
private val KING_RING = Color(0x4DFFFFFF)
private val CLASS_COLORS = arrayOf(Color(0xFFD9443A), Color(0xFF3F9E4D), Color(0xFFE08A2B))
private val HUD_SHADOW = Color(0x55000000)
private val HUD_TRACK = Color(0x66000000)
private val HUD_BUTTON = Color(0xFF3B4A6B)
private val WAVE_RED = Color(0xFFC8342B)
private val COOLDOWN = Color(0xAA000000)
private val PAD_DIM = Color(0x66000000)
private val CAPTAIN_AURA = Color(0x55FFC531)
private val CARD_BG = Color(0xF21F2A40)
private val LOCK = Color(0xFFE0C068)
private const val WALL_SCALE = 1f // the v2 wall/gate sprites are already drawn tall and chunky
private val TORCH = Color(0xFFFFD25A)
private val TORCH_GLOW = Color(0x55FF9A2A)
private val NIGHT = Color(0xD0050A1E)
private val FOG = Color(0xE6DCE6E8)
private val SNOW = Color(0xCCFFFFFF)
private val ENEMY_ARROW = Color(0xFF3A2414)
private val ALERT_RED = Color(0xFFFF4A3A)
private const val MAX_EDGE_ARROWS = 12
private val WOOD = Color(0xFFA4683A)
private val WOOD_DARK = Color(0xFF6E4325)
private val WOOD_STAKE = Color(0xFF7A4A2A)
private val STONE = Color(0xFFA3A9B0)
private val STONE_DARK = Color(0xFF7D838B)
private val ROOF = Color(0xFF8B5A36)
private val ROOF_DARK = Color(0xFF6B4228)
private val SKIN = Color(0xFFF4C8A2)
private val HAIR = Color(0xFF2B2B2E)
private val SHIRT = Color(0xFFEFE6D6)
private val LEG = Color(0xFF5A4636)
private val BOW = Color(0xFF3D7BE0)
private val ROYAL = Color(0xFF2F5BD3)
private val ROYAL_DARK = Color(0xFF1F3F9E)
private val SILVER = Color(0xFFD4DAE2)
private val GOLD = Color(0xFFFFC531)
private val GOLD_DARK = Color(0xFFD9901A)
private val HORSE = Color(0xFFD9A066)
private val HORSE_DARK = Color(0xFF8A5A33)
private val RED = Color(0xFFD8262B)
private val RED_LIGHT = Color(0xFFEE3B3B)
private val RED_DARK = Color(0xFF9E1620)
private val BOSS = Color(0xFF8E1B1F)
private val WHITE = Color.White
private val BLACK = Color(0xFF1B1B1F)
private val PAD_BG = Color(0x66404A1E)
private val PAD_FILL = Color(0xFF4CDB3A)
private val PAD_LINE = Color(0xEEFFFFFF)
private val HP_BG = Color(0xCC2A1A1A)
private val HP_RED = Color(0xFFE8333A)
private val HP_GREEN = Color(0xFF52D64A)
private val HUD_BG = Color(0x99202020)

/** Caches text layouts so the hot loop never re-measures text. */
private class TextCache(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val ints = arrayOfNulls<TextLayoutResult>(2048)
    private val negs = arrayOfNulls<TextLayoutResult>(2048)
    private val strs = HashMap<String, TextLayoutResult>()

    fun num(v: Int): TextLayoutResult {
        if (v in 0 until 2048) return ints[v] ?: measurer.measure(v.toString(), style).also { ints[v] = it }
        return str(v.toString())
    }

    fun neg(v: Int): TextLayoutResult {
        if (v in 0 until 2048) return negs[v] ?: measurer.measure("-$v", style).also { negs[v] = it }
        return str("-$v")
    }

    fun str(s: String): TextLayoutResult = strs.getOrPut(s) { measurer.measure(s, style) }
}

class Renderer(measurer: TextMeasurer, private val density: Float) {
    /** Base pixel height of cached text; drawn scaled to the wanted size. */
    private val base = 64f
    private val text = TextCache(measurer, TextStyle(fontSize = (base / density).sp, fontWeight = FontWeight.Black))
    private val outline = Stroke(width = 8f, join = StrokeJoin.Round)

    private val roadStrokeEdge = Stroke(width = 104f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val roadStroke = Stroke(width = 88f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val roadCenterStroke = Stroke(width = 34f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val bowStroke = Stroke(width = 3f, cap = StrokeCap.Round)
    private val roads = ArrayList<Path>()
    private val roads2 = ArrayList<Path>()
    private val roadEdge2 = Stroke(width = 212f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val road2 = Stroke(width = 176f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val roadCenter2 = Stroke(width = 68f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // tiled ground textures, created once the sprites are loaded
    private var tilesOf: Sprites? = null
    private var grassBrush: ShaderBrush? = null
    private var roadBrush: ShaderBrush? = null
    private var cliffBrush: ShaderBrush? = null
    private val autumnTint = ColorFilter.tint(Color(0xFFF2CF86), BlendMode.Modulate)
    private val winterTint = ColorFilter.tint(Color(0xFFDDE6EA), BlendMode.Modulate)

    // new road drawn progressively during the level transition
    private var revealIdx = -1
    private val revealPath = Path()
    private val revealMeasure = PathMeasure()

    // river path and bridge spots (x, y, road tangent), rebuilt with the roads
    private var riverPath: Path? = null
    private val riverBank = Stroke(width = World.RIVER_HALF * 2f + 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val riverWater = Stroke(width = World.RIVER_HALF * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val riverShine = Stroke(width = World.RIVER_HALF * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val bridges = FloatArray(4 * 8)
    private var bridgeCount = 0
    private var time = 0f

    private fun syncTiles() {
        val sp = sprites ?: return
        if (tilesOf === sp) return
        tilesOf = sp
        grassBrush = ShaderBrush(ImageShader(sp.grassTile, TileMode.Repeated, TileMode.Repeated))
        roadBrush = ShaderBrush(ImageShader(sp.roadTile, TileMode.Repeated, TileMode.Repeated))
        cliffBrush = ShaderBrush(ImageShader(sp.cliffTile, TileMode.Repeated, TileMode.Repeated))
    }

    private val keys = LongArray(2048)
    private val flashFilter = ColorFilter.tint(Color(0xD0FFFFFF), BlendMode.SrcAtop)
    private val dimFilter = ColorFilter.tint(Color(0xB0808080), BlendMode.SrcAtop)

    /** Pre-rendered 3D sprite atlases; null until loaded. */
    var sprites: Sprites? = null
    /** Interface kit images for the HUD (art/ui), set by App. */
    var hud: HudImages? = null
    private var scale = 1f
    private val portraitClip = Path()
    private val payRing = Stroke(width = 9f, cap = StrokeCap.Round)
    private val cardBorder = Stroke(width = 6f)
    private val lockStroke = Stroke(width = 6f)
    private var viewTop = 0f // world y of the top of the view, to stop the coin stack where it leaves the screen
    private var viewOx = 0f // world -> screen offset of the current frame, for the HUD
    private var viewOy = 0f
    /** Unit triangle pointing +x, drawn rotated/scaled for off-screen enemy arrows. */
    private val edgeArrow = Path().apply { moveTo(1f, 0f); lineTo(-0.7f, -0.8f); lineTo(-0.35f, 0f); lineTo(-0.7f, 0.8f); close() }

    var topInset = 0f

    private var roadsVersion = -1
    private var season = SEASONS[0]

    /** Road paths are cached per map; rebuilt only when [World.load] changes the layout. */
    private fun syncRoads() {
        if (roadsVersion == World.version) return
        roadsVersion = World.version
        roads.clear()
        for (p in World.paths) roads.add(polyPath(p.xs, p.ys))
        for (r in World.decoRoads) {
            val n = r.size / 2
            roads.add(polyPath(FloatArray(n) { r[it * 2] }, FloatArray(n) { r[it * 2 + 1] }))
        }
        // same roads at 2x: tiles are drawn under scale(0.5) so a 512 px texture covers 256 world units at full detail
        val rv = World.river
        riverPath = if (rv.size < 4) null else Path().apply { moveTo(rv[0], rv[1]); var k = 2; while (k < rv.size) { lineTo(rv[k], rv[k + 1]); k += 2 } }
        bridgeCount = 0
        if (rv.size >= 4) {
            val v = Vec(); val tg = Vec()
            for (p in World.paths) {
                var sd = 0f
                var was = false
                while (sd <= p.length && bridgeCount < 8) {
                    p.sample(sd, v)
                    val wet = World.inRiverCore(v.x, v.y)
                    if (wet && !was) { p.tangent(sd + 30f, tg); bridges[bridgeCount * 4] = v.x + tg.x * 30f; bridges[bridgeCount * 4 + 1] = v.y + tg.y * 30f; bridges[bridgeCount * 4 + 2] = tg.x; bridges[bridgeCount * 4 + 3] = tg.y; bridgeCount++ }
                    was = wet
                    sd += 10f
                }
            }
        }
        roads2.clear()
        for (p in World.paths) roads2.add(polyPath(FloatArray(p.n) { p.xs[it] * 2f }, FloatArray(p.n) { p.ys[it] * 2f }))
        for (r in World.decoRoads) {
            val n = r.size / 2
            roads2.add(polyPath(FloatArray(n) { r[it * 2] * 2f }, FloatArray(n) { r[it * 2 + 1] * 2f }))
        }
    }

    private fun polyPath(xs: FloatArray, ys: FloatArray) = Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
    }


    // ------------------------------------------------------------ helpers

    private fun DrawScope.rr(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))

    private fun DrawScope.rect(c: Color, x: Float, y: Float, w: Float, h: Float) =
        drawRect(c, Offset(x, y), Size(w, h))

    private fun DrawScope.oval(c: Color, x: Float, y: Float, w: Float, h: Float) =
        drawOval(c, Offset(x, y), Size(w, h))

    private fun DrawScope.circ(c: Color, x: Float, y: Float, r: Float) =
        drawCircle(c, r, Offset(x, y))

    private fun DrawScope.line(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) =
        drawLine(c, Offset(x0, y0), Offset(x1, y1), w, StrokeCap.Round)

    /** Draws cached text centered at (cx, cy) with the given pixel height. */
    private fun DrawScope.label(t: TextLayoutResult, cx: Float, cy: Float, px: Float, color: Color, alpha: Float = 1f, outlined: Boolean = true) {
        val s = px / base
        val w = t.size.width.toFloat()
        val h = t.size.height.toFloat()
        withTransform({
            translate(cx - w * s / 2f, cy - h * s / 2f)
            scale(s, s, Offset.Zero)
        }) {
            if (outlined) drawText(t, color = BLACK, alpha = alpha * 0.85f, drawStyle = outline)
            // Fill must be explicit: a null drawStyle keeps the previous (stroke) paint style.
            drawText(t, color = color, alpha = alpha, drawStyle = Fill)
        }
    }

    // ------------------------------------------------------------ frame

    fun draw(ds: DrawScope, g: Game) = with(ds) {
        val w = size.width
        val h = size.height
        val portrait = h >= w
        scale = (if (portrait) w / 560f else min(w / 1000f, h / 620f)) * g.camZoom * g.worldZoom
        val halfW = w / 2f / scale
        val halfH = h / 2f / scale
        val cx = if (halfW * 2 >= World.W) World.W / 2 else g.camX.coerceIn(halfW, World.W - halfW)
        val cy = if (halfH * 2 >= World.H) World.H / 2 else g.camY.coerceIn(halfH - 200f, World.H - halfH)
        val shx = if (g.shake > 0f) sin(g.time * 93f) * g.shake else 0f
        val shy = if (g.shake > 0f) cos(g.time * 71f) * g.shake else 0f
        val ox = w / 2f - cx * scale + shx
        val oy = h / 2f - cy * scale + shy
        viewOx = ox; viewOy = oy
        val vl = cx - halfW - 160f
        val vr = cx + halfW + 160f
        val vt = cy - halfH - 60f
        viewTop = vt
        val vb = cy + halfH + 260f

        time = g.time
        syncRoads()
        syncTiles()
        revealIdx = g.revealRoad
        if (revealIdx in roads2.indices) {
            revealPath.reset()
            revealMeasure.setPath(roads2[revealIdx], false)
            revealMeasure.getSegment(0f, revealMeasure.length * g.revealT, revealPath, true)
        }
        season = SEASONS[CHAPTER_SEASON[World.chapter(g.level)]]
        drawRect(season[0])
        withTransform({
            translate(ox, oy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawGround(vl, vt, vr, vb)
            for (p in g.pads) if (p.visible) drawPad(p, p.kind == PAD_TOWER_ARCHER && g.squad == 0)
            drawSorted(g, vl, vt, vr, vb)
            drawArrows(g, vl, vt, vr, vb)
            for (sh in g.enemyShots) if (sh.active) { // enemy arrows: short dark streak
                val dx = sh.tx - sh.x; val dy = sh.ty - sh.y
                val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                line(ENEMY_ARROW, sh.x, sh.y, sh.x - dx / l * 26f, sh.y - dy / l * 26f, 3.5f)
            }
            drawFlyCoins(g)
            drawParticles(g, vl, vt, vr, vb)
        }

        // world-anchored labels in screen space
        for (p in g.pads) {
            if (!p.visible) continue
            val sx = ox + p.x * scale
            val sy = oy + p.y * scale
            if (sx < -200 || sx > w + 200 || sy < -200 || sy > h + 200) continue
            val pop = 1f + p.bump * 0.5f
            if (p.locked) label(text.str(lockLabel(UNLOCK_LEVEL[p.level.coerceIn(0, UNLOCK_LEVEL.size - 1)])), sx, sy + 34f * scale, 18f * scale, GOLD)
            else label(text.num(p.cost - p.paid), sx + 18f * scale, sy - 4f * scale, 34f * scale * pop, WHITE)
            label(text.str(padName(p, g)), sx, sy + (Pad.HALF + 16f) * scale, 17f * scale, WHITE)
        }
        for (t in g.texts) {
            if (!t.active) continue
            val k = t.life / 0.8f
            val pop = if (k > 0.8f) 1f + (k - 0.8f) * 3f else 1f
            val px = (if (t.big) 34f else 24f) * scale * pop
            val col = when (t.kind) { 1 -> Color(0xFFFFD84A); 2 -> ALERT_RED; else -> if (t.big) Color(0xFFFFD84A) else WHITE }
            label(text.neg(t.value), ox + t.x * scale, oy + (t.y - t.z) * scale, px, col, min(1f, k * 2.5f))
        }

        drawFog(g, w, h)
        drawWeather(g, w, h)
        drawHud(g, w, h)
    }

    // ---- level weather / light (screen space, no per-frame allocation)
    private var lightMask: ImageBitmap? = null

    /** Soft round light (opaque centre fading out), punched out of the night layer. */
    private fun lightBitmap(): ImageBitmap {
        lightMask?.let { return it }
        val n = 128
        val img = ImageBitmap(n, n)
        val c = androidx.compose.ui.graphics.Canvas(img)
        val paint = androidx.compose.ui.graphics.Paint()
        for (r in n / 2 downTo 1) { // concentric discs, more opaque towards the centre
            paint.color = Color.Black.copy(alpha = (1f - r / (n / 2f)).coerceIn(0f, 1f) * 0.12f)
            c.drawCircle(Offset(n / 2f, n / 2f), r.toFloat(), paint)
        }
        lightMask = img
        return img
    }

    /** "NOUVEAU" card at the level-up: the unlocked building, big, with its name and effect. */
    private fun DrawScope.drawUnlockCard(g: Game, w: Float, h: Float, u: Float, a: Float, pop: Float) {
        val sp = sprites ?: return
        val cw = 230f * u
        val chh = 210f * u
        val cx = w / 2f
        val cy = h * 0.66f
        rr(HUD_SHADOW, cx - cw / 2f, cy - chh / 2f + 6f * u, cw, chh, 22f * u)
        rr(CARD_BG, cx - cw / 2f, cy - chh / 2f, cw, chh, 22f * u)
        drawRoundRect(GOLD, Offset(cx - cw / 2f, cy - chh / 2f), Size(cw, chh), CornerRadius(22f * u, 22f * u), style = cardBorder, alpha = a)
        label(text.str("NOUVEAU !"), cx, cy - chh / 2f + 20f * u, 20f * u * pop, GOLD, a)
        // the building itself, scaled to fit the card
        val id = g.newUnlockId
        withTransform({ translate(cx, cy + 30f * u); scale(u * 0.55f, u * 0.55f, Offset.Zero) }) {
            when (id) {
                U_STABLE -> sprite(sp.stable, SpriteData.stable, 0, 0, 0f, 0f, 1f)
                U_ALTAR -> sprite(sp.church, SpriteData.church, 0, 0, 0f, 0f, 1f)
                U_MILL -> sprite(sp.watermill, SpriteData.watermill, 0, 0, 0f, 0f, 1f)
                U_MARKET -> sprite(sp.market, SpriteData.market, 0, 0, 0f, 0f, 1f)
                U_TREASURY -> sprite(sp.well, SpriteData.well, 0, 0, 0f, 0f, 1.4f, eliteTint)
                U_GATEWORKS -> sprite(sp.blacksmith, SpriteData.blacksmith, 0, 0, 0f, 0f, 1f)
                U_STATUE -> sprite(sp.lumbermill, SpriteData.lumbermill, 0, 0, 0f, 0f, 1f, eliteTint)
                U_KEEP3 -> sprite(sp.towerRoyal, SpriteData.towerRoyal, 0, 0, 0f, 0f, 0.8f)
                UNLOCK_MINE_CARD -> sprite(sp.mine, SpriteData.mine, 0, 0, 0f, 0f, 1f)
                else -> sprite(sp.towerStone, SpriteData.towerStone, 0, 0, 0f, 0f, 0.8f)
            }
        }
        val name = if (id == UNLOCK_MINE_CARD) "Mine et Caserne" else UNLOCK_NAMES[id]
        val desc = if (id == UNLOCK_MINE_CARD) "butin x1,5 · 3 lanciers" else UNLOCK_DESC[id]
        label(text.str(name), cx, cy + chh / 2f - 42f * u, 18f * u, WHITE, a)
        label(text.str(desc), cx, cy + chh / 2f - 20f * u, 12f * u, GOLD, a)
    }

    private var fogRing: ImageBitmap? = null

    /** Mist outside a soft-rimmed clear disc (clear up to 80 % of the radius), built once: no layer per frame. */
    private fun fogRingBitmap(): ImageBitmap {
        fogRing?.let { return it }
        val n = 256
        val img = ImageBitmap(n, n)
        val c = androidx.compose.ui.graphics.Canvas(img)
        val paint = androidx.compose.ui.graphics.Paint()
        paint.color = FOG
        c.drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, n.toFloat(), n.toFloat()), paint)
        paint.blendMode = BlendMode.DstOut
        for (r in (n / 2) downTo (n * 2 / 5)) { // soft rim
            paint.color = Color.Black.copy(alpha = 0.09f)
            c.drawCircle(Offset(n / 2f, n / 2f), r.toFloat(), paint)
        }
        paint.color = Color.Black
        c.drawCircle(Offset(n / 2f, n / 2f), n * 0.4f, paint)
        fogRing = img
        return img
    }

    /** Unexplored land: a mist beyond the explored radius, receding as levels go by. */
    private fun DrawScope.drawFog(g: Game, w: Float, h: Float) {
        if (g.fogR <= 0f) return
        val sx = viewOx + World.CASTLE_X * scale
        val sy = viewOy + World.CASTLE_Y * scale
        val rr = g.fogR * scale / 0.8f // the ring is clear up to 80 % of its radius
        if (sx - rr * 0.8f >= 0f && sx + rr * 0.8f <= w && sy - rr * 0.8f >= 0f && sy + rr * 0.8f <= h) return // fog not on screen
        val ring = fogRingBitmap()
        val l = sx - rr; val t = sy - rr; val r = sx + rr; val b = sy + rr
        drawImage(ring, IntOffset.Zero, IntSize(ring.width, ring.height), IntOffset(l.toInt(), t.toInt()), IntSize((rr * 2).toInt(), (rr * 2).toInt()))
        // plain mist beyond the ring's square
        if (t > 0f) drawRect(FOG, Offset(0f, 0f), Size(w, t))
        if (b < h) drawRect(FOG, Offset(0f, b), Size(w, h - b))
        if (l > 0f) drawRect(FOG, Offset(0f, max(0f, t)), Size(l, min(h, b) - max(0f, t)))
        if (r < w) drawRect(FOG, Offset(r, max(0f, t)), Size(w - r, min(h, b) - max(0f, t)))
    }

    private fun DrawScope.drawWeather(g: Game, w: Float, h: Float) {
        if (g.state != STATE_PLAY && g.transition <= 0f) return
        when (World.theme(g.level)) {
            World.TH_NIGHT -> {
                val light = lightBitmap()
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), nightPaint)
                drawRect(NIGHT)
                fun hole(x: Float, y: Float, r: Float) {
                    val sx = viewOx + x * scale; val sy = viewOy + y * scale; val rr = r * scale
                    drawImage(light, IntOffset.Zero, IntSize(light.width, light.height), IntOffset((sx - rr).toInt(), (sy - rr).toInt()), IntSize((rr * 2).toInt(), (rr * 2).toInt()), blendMode = BlendMode.DstOut)
                }
                hole(g.kx, g.ky - 30f, 330f)
                hole(World.CASTLE_X, World.CASTLE_Y - 40f, 420f)
                for (t in g.towers) if (t.built) hole(t.x, t.y - 60f, 260f)
                drawContext.canvas.restore()
            }
            World.TH_STORM, World.TH_FROST -> { // falling snow, stateless: positions from time
                val t = g.time
                for (i in 0 until 90) {
                    val x = ((i * 97.3f + t * (40f + i % 7 * 12f)) % (w + 40f)) - 20f
                    val y = ((i * 211.7f + t * (160f + i % 5 * 40f)) % (h + 40f)) - 20f
                    circ(SNOW, x, y, 2f + (i % 3))
                }
            }
        }
    }
    private val nightPaint = androidx.compose.ui.graphics.Paint()

    private fun padName(p: Pad, g: Game): String = if (p.kind == PAD_WALL && g.walls[p.ref].built) "Réparer le mur" else when (p.kind) {
        PAD_RECRUIT -> "Archer"
        PAD_FORGE -> "Forge  +dégâts"
        PAD_REPAIR -> "Réparer le donjon"
        PAD_TOWER -> "Tour"
        PAD_TOWER_ARCHER -> "1 archer → tour"
        PAD_FARM -> "Ferme  +or"
        PAD_BARRACKS -> "Caserne  3 lanciers"
        PAD_MINE -> "Mine  butin x2"
        PAD_BALLISTA -> "Baliste"
        PAD_ELITE -> "Archers d'élite"
        PAD_SPOT_A, PAD_SPOT_B -> UNLOCK_NAMES[p.level.coerceIn(0, UNLOCK_NAMES.size - 1)]
        else -> "Palissade"
    }

    private fun DrawScope.drawGround(vl: Float, vt: Float, vr: Float, vb: Float) {
        val winter = season === SEASONS[2]
        val gb = grassBrush
        val rb = roadBrush
        val tint = if (season === SEASONS[1]) autumnTint else if (winter) winterTint else null
        withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) {
            if (gb != null) drawRect(gb, Offset(vl * 2f, vt * 2f), Size((vr - vl) * 2f, (vb - vt) * 2f), alpha = if (winter) 0.22f else 1f, colorFilter = tint)
        }
        val gp = World.grassPatches
        var i = 0
        while (i < gp.size) {
            val x = gp[i]; val y = gp[i + 1]; val r = gp[i + 2]
            if (x + r > vl && x - r < vr && y + r > vt && y - r < vb) {
                val c = if (gp[i + 3] > 0.5f) season[1] else season[2]
                drawOval(c, Offset(x - r * 1.15f, y - r * 0.69f), Size(r * 2.3f, r * 1.38f), alpha = if (gb != null) 0.18f else 0.5f) // soft edge
                drawOval(c, Offset(x - r, y - r * 0.6f), Size(r * 2f, r * 1.2f), alpha = if (gb != null) 0.3f else 1f)
            }
            i += 4
        }
        if (riverPath != null) {
            drawPath(riverPath!!, RIVER_BANK, style = riverBank)
            drawPath(riverPath!!, RIVER_WATER, style = riverWater)
            drawPath(riverPath!!, RIVER_LIGHT, alpha = 0.5f + 0.2f * sin(time * 1.5f), style = riverShine)
        }
        if (rb != null) {
            withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) {
                for (k in roads2.indices) drawPath(if (k == revealIdx) revealPath else roads2[k], DIRT_EDGE, alpha = 0.55f, style = roadEdge2) // soft border
                for (k in roads2.indices) drawPath(if (k == revealIdx) revealPath else roads2[k], rb, style = road2, colorFilter = if (winter) winterTint else null)
                for (k in roads2.indices) drawPath(if (k == revealIdx) revealPath else roads2[k], DIRT_LIGHT, alpha = 0.25f, style = roadCenter2) // worn middle of the track
            }
        } else {
            for (r in roads) drawPath(r, DIRT_EDGE, style = roadStrokeEdge)
            for (r in roads) drawPath(r, DIRT, style = roadStroke)
            for (r in roads) drawPath(r, DIRT_LIGHT, style = roadCenterStroke)
        }
        // wooden bridges where roads cross the river
        val bx = bridges
        i = 0
        while (i < bridgeCount) {
            val x = bx[i * 4]; val y = bx[i * 4 + 1]; val tx = bx[i * 4 + 2]; val ty = bx[i * 4 + 3]
            if (x > vl - 100f && x < vr + 100f && y > vt - 100f && y < vb + 100f) {
                for (k in -3..3) { // planks across the road, rails on both sides
                    val px = x + tx * k * 14f; val py = y + ty * k * 14f
                    line(PLANK, px - ty * 50f, py + tx * 50f, px + ty * 50f, py - tx * 50f, 11f)
                }
                line(PLANK_DARK, x - ty * 52f - tx * 56f, y + tx * 52f - ty * 56f, x - ty * 52f + tx * 56f, y + tx * 52f + ty * 56f, 7f)
                line(PLANK_DARK, x + ty * 52f - tx * 56f, y - tx * 52f - ty * 56f, x + ty * 52f + tx * 56f, y - tx * 52f + ty * 56f, 7f)
            }
            i++
        }
        val pb = World.pebbles
        i = 0
        while (i < pb.size) {
            val x = pb[i]; val y = pb[i + 1]; val r = pb[i + 2]
            if (x > vl && x < vr && y > vt && y < vb) {
                oval(PEBBLE_DARK, x - r, y - r * 0.4f, r * 2.2f, r * 1.2f)
                oval(PEBBLE, x - r, y - r * 0.8f, r * 2f, r * 1.3f)
            }
            i += 3
        }
        // flat decor, no depth sort: grass tufts (every 5th spot) and flowers, not in winter
        val sp = sprites
        if (sp != null && !winter) {
            val tf = World.tufts
            i = 0
            while (i < tf.size) {
                val x = tf[i]; val y = tf[i + 1]
                if (x > vl && x < vr && y > vt && y < vb) {
                    if ((i / 3) % 2 == 0) sprite(sp.tuft0, SpriteData.tuft0, 0, 0, x, y, tf[i + 2])
                    else sprite(sp.tuft1, SpriteData.tuft1, 0, 0, x, y, tf[i + 2])
                }
                i += 15
            }
            val fl = World.flowers
            i = 0
            while (i < fl.size) {
                val x = fl[i]; val y = fl[i + 1]
                if (x > vl && x < vr && y > vt && y < vb) {
                    if (fl[i + 2] < 1f) sprite(sp.flower0, SpriteData.flower0, 0, 0, x, y, 1f)
                    else sprite(sp.flower1, SpriteData.flower1, 0, 0, x, y, 1f)
                }
                i += 6
            }
        }
        // three passes so touching cliff blocks merge into one plateau: cast shadows, rock faces, grass tops
        for (c in World.cliffs) {
            val x = c[0]; val y = c[1]; val cw = c[2]; val d = c[3]; val ht = c[4]
            if (x > vr + 120f || x + cw < vl || y - ht > vb || y + d + 60f < vt) continue
            // short soft shadow (~0.4 x height) down-right, matching the sprites' baked shadows
            val sh = ht * 0.4f
            rr(CLIFF_SHADOW, x + 8f, y + d - 14f, cw + sh * 0.5f, sh * 0.5f + 14f, sh * 0.3f + 7f)
            rr(CLIFF_SHADOW, x + cw - 12f, y + d - ht * 0.6f, sh * 0.5f + 12f, ht * 0.6f + sh * 0.3f, sh * 0.3f + 6f)
        }
        for (c in World.cliffs) {
            val x = c[0]; val y = c[1]; val cw = c[2]; val d = c[3]; val ht = c[4]
            if (x > vr || x + cw < vl || y - ht > vb || y + d < vt) continue
            val fy = y + d - ht
            val cb = cliffBrush
            if (cb != null) withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) {
                drawRoundRect(cb, Offset(x * 2f, fy * 2f), Size(cw * 2f, ht * 2f), CornerRadius(36f, 36f))
            } else rr(CLIFF_FACE, x, fy, cw, ht, 18f)
            // rock strata and a darker base give the face some depth
            rect(CLIFF_FACE_DARK, x + 6f, fy + ht * 0.42f, cw - 12f, 6f)
            rect(CLIFF_FACE_DARK, x + 6f, fy + ht * 0.68f, cw - 12f, 5f)
            rr(CLIFF_FOOT, x, y + d - ht * 0.2f, cw, ht * 0.2f, 18f)
        }
        for (c in World.cliffs) {
            val x = c[0]; val y = c[1]; val cw = c[2]; val d = c[3]; val ht = c[4]
            if (x > vr || x + cw < vl || y - ht > vb || y + d < vt) continue
            rr(CLIFF_LIP, x - 4f, y - ht + d - 4f, cw + 8f, 26f, 13f) // shade under the grass overhang
            val gb = grassBrush
            if (gb != null) {
                withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) {
                    drawRoundRect(gb, Offset((x - 6f) * 2f, (y - ht) * 2f), Size((cw + 12f) * 2f, (d + 4f) * 2f), CornerRadius(52f, 52f),
                        colorFilter = if (season === SEASONS[1]) autumnTint else if (season === SEASONS[2]) winterTint else null)
                }
                drawRoundRect(season[4], Offset(x - 6f, y - ht), Size(cw + 12f, d + 4f), CornerRadius(26f, 26f), alpha = 0.3f) // sunlit plateau
            } else {
                rr(season[2], x - 6f, y - ht, cw + 12f, d + 4f, 26f) // sunlit plateau, lighter than the ground below
                rr(season[4], x + 12f, y - ht + 12f, cw * 0.55f, d * 0.45f, 30f)
            }
        }
    }

    private fun DrawScope.drawPad(p: Pad, dim: Boolean) {
        val s = 1f + p.bump * 0.12f
        val hs = Pad.HALF * s
        val x0 = p.x - hs
        val y0 = p.y - hs
        val size = hs * 2f
        rr(PAD_BG, x0, y0, size, size, 14f)
        val frac = ((p.paid + p.pending).toFloat() / p.cost).coerceIn(0f, 1f)
        if (frac > 0f) {
            rr(PAD_FILL, x0, y0, size * (p.paid.toFloat() / p.cost).coerceIn(0f, 1f), size, 14f)
            // fast-filling ring around the site while paying
            drawArc(GOLD, -90f, 360f * frac, false, Offset(p.x - hs - 14f, p.y - hs - 14f), Size(hs * 2f + 28f, hs * 2f + 28f), style = payRing)
        }
        val k = 22f * s
        val lw = 5f
        line(PAD_LINE, x0, y0 + k, x0, y0 + 6f, lw); line(PAD_LINE, x0 + 6f, y0, x0 + k, y0, lw)
        line(PAD_LINE, x0 + size - k, y0, x0 + size - 6f, y0, lw); line(PAD_LINE, x0 + size, y0 + 6f, x0 + size, y0 + k, lw)
        line(PAD_LINE, x0, y0 + size - k, x0, y0 + size - 6f, lw); line(PAD_LINE, x0 + 6f, y0 + size, x0 + k, y0 + size, lw)
        line(PAD_LINE, x0 + size - k, y0 + size, x0 + size - 6f, y0 + size, lw); line(PAD_LINE, x0 + size, y0 + size - 6f, x0 + size, y0 + size - k, lw)
        if (p.kind == PAD_TOWER_ARCHER) {
            val sp = sprites
            if (sp != null) sprite(sp.archer, SpriteData.archer, 0, 0, p.x - 22f, p.y + 16f, 0.75f, if (dim) dimFilter else null)
            if (dim) rr(PAD_DIM, x0, y0, size, size, 14f)
        } else if (!p.locked) drawCoinIcon(p.x - 22f, p.y - 4f, 13f)
        if (p.locked) { // padlock teaser
            rr(PAD_DIM, x0, y0, size, size, 14f)
            rr(LOCK, p.x - 16f, p.y - 4f, 32f, 26f, 5f)
            drawArc(LOCK, 180f, 180f, false, Offset(p.x - 11f, p.y - 22f), Size(22f, 30f), style = lockStroke)
        }
    }

    private fun DrawScope.drawCoinIcon(x: Float, y: Float, r: Float) {
        circ(GOLD_DARK, x, y + r * 0.18f, r)
        circ(GOLD, x, y, r)
        circ(GOLD_DARK, x, y, r * 0.62f)
        circ(GOLD, x, y + r * 0.08f, r * 0.5f)
    }

    // ------------------------------------------------------------ sorted world objects

    private fun push(n: Int, y: Float, kind: Int, idx: Int): Int {
        if (n >= keys.size) return n
        val yk = ((y + 1000f) * 8f).toLong().coerceIn(0L, 65535L)
        keys[n] = (yk shl 24) or (kind.toLong() shl 16) or idx.toLong()
        return n + 1
    }

    private fun DrawScope.drawSorted(g: Game, vl: Float, vt: Float, vr: Float, vb: Float) {
        var n = 0
        fun vis(x: Float, y: Float) = x > vl && x < vr && y > vt && y < vb
        val t = World.trees
        var i = 0
        while (i < t.size) { if (vis(t[i], t[i + 1])) n = push(n, t[i + 1], K_TREE, i); i += 3 }
        val r = World.rocks
        i = 0
        while (i < r.size) { if (vis(r[i], r[i + 1])) n = push(n, r[i + 1], K_ROCK, i); i += 3 }
        val pr = World.props
        i = 0
        while (i < pr.size) { if (vis(pr[i], pr[i + 1])) n = push(n, pr[i + 1], K_PROP, i); i += 3 }
        // enclosure: stakes every point (wood), a stone block every 3rd point (stone/royal)
        val ringStride = 2
        for (k in 0 until g.ringCount) if (k % ringStride == 0 && vis(g.ringX[k], g.ringY[k])) n = push(n, g.ringY[k], K_RING, k)
        n = push(n, World.HOUSE_Y, K_HOUSE, 0)
        if (g.farm) n = push(n, World.FARM_Y, K_BUILDING, 0)
        if (g.barracks) {
            n = push(n, World.BARRACKS_Y, K_BUILDING, 1)
            for (k in 0 until LANCERS) if (vis(g.lx[k], g.ly[k])) n = push(n, g.ly[k], K_LANCER, k)
        }
        if (g.mine) n = push(n, World.MINE_Y, K_BUILDING, 2)
        if (g.chestActive) n = push(n, g.chestY, K_BUILDING, 5)
        if (g.lastUnlock(0) >= 0) n = push(n, World.SPOT_A_Y, K_BUILDING, 3)
        if (g.lastUnlock(1) >= 0) n = push(n, World.SPOT_B_Y, K_BUILDING, 4)
        n = push(n, World.CASTLE_Y, K_CASTLE, 0)
        for (k in g.towers.indices) if (g.towers[k].built) n = push(n, g.towers[k].y, K_TOWER, k)
        for (k in g.walls.indices) if (g.walls[k].built) n = push(n, g.walls[k].y, K_WALL, k)
        for (k in g.enemies.indices) { val e = g.enemies[k]; if (e.active && vis(e.x, e.y)) n = push(n, e.y, K_ENEMY, k) }
        for (k in g.corpses.indices) { val c = g.corpses[k]; if (c.active && vis(c.x, c.y)) n = push(n, c.y - 2f, K_CORPSE, k) }
        for (k in 0 until g.squad) if (vis(g.ax[k], g.ay[k])) n = push(n, g.ay[k], K_ARCHER, k)
        if (!g.kDead && g.state != STATE_MENU) n = push(n, g.ky, K_KING, 0)
        for (k in g.coinsOnGround.indices) { val c = g.coinsOnGround[k]; if (c.active && vis(c.x, c.y)) n = push(n, c.y - 1f, K_COIN, k) }
        keys.sort(0, n)
        for (j in 0 until n) {
            val key = keys[j]
            val kind = ((key shr 16) and 0xFF).toInt()
            val idx = (key and 0xFFFF).toInt()
            when (kind) {
                K_TREE -> drawTree(t[idx], t[idx + 1], t[idx + 2], idx / 3)
                K_ROCK -> drawRock(r[idx], r[idx + 1], r[idx + 2], idx / 3)
                K_PROP -> drawProp(pr[idx], pr[idx + 1], pr[idx + 2])
                K_RING -> drawRingPiece(g, idx)
                K_HOUSE -> drawHouse(World.HOUSE_X, World.HOUSE_Y)
                K_CASTLE -> drawCastle(g)
                K_TOWER -> drawTower(g.towers[idx], g.age)
                K_WALL -> drawWall(g.walls[idx], g.age, g)
                K_ENEMY -> drawEnemy(g.enemies[idx], g.level % 5 == 0)
                K_CORPSE -> drawCorpse(g.corpses[idx])
                K_BUILDING -> drawBuilding(idx, g)
                K_LANCER -> {
                    val sp = sprites
                    if (sp != null) {
                        val col = if (g.lSwing[idx] > 0f) 7 + ((1f - g.lSwing[idx]) * 3f).toInt().coerceIn(0, 2) else if (g.lMoving[idx]) 1 + walkFrame(g.time * 12f, 6) else 0
                        sprite(sp.lancer, SpriteData.lancer, col, g.lDir[idx], g.lx[idx], g.ly[idx], UNIT_SCALE)
                    }
                }
                K_ARCHER -> drawArcher(g, idx)
                K_KING -> drawKing(g)
                K_COIN -> drawGroundCoin(g.coinsOnGround[idx], g.kx, g.ky)
            }
        }
    }

    // ------------------------------------------------------------ sprites

    /** Draws one atlas cell with its anchor (feet) at world (x, y); [k] scales the model. */
    private fun DrawScope.sprite(img: ImageBitmap, info: SpriteInfo, col: Int, row: Int, x: Float, y: Float, k: Float, filter: ColorFilter? = null, alpha: Float = 1f) {
        if (img.width < info.w * info.cols || img.height < info.h * info.rows) return
        val s = k / info.ppu
        withTransform({
            translate(x - info.ax * s, y - info.ay * s)
            scale(s, s, Offset.Zero)
        }) {
            drawImage(img, IntOffset(col * info.w, row * info.h), IntSize(info.w, info.h), IntOffset.Zero, IntSize(info.w, info.h), alpha = alpha, colorFilter = filter)
        }
    }

    /** Fallen enemy: tips over away from the camera, sinks a little and fades out. */
    private fun DrawScope.drawCorpse(c: Corpse) {
        val sp = sprites ?: return
        val f = c.t / CORPSE_TIME
        val tip = min(1f, f * 3f)
        val a = 1f - f * f
        withTransform({ rotate((if (c.dir % 8 < 4) -1f else 1f) * 75f * tip, Offset(c.x, c.y)) }) {
            val k = UNIT_SCALE * (if (c.kind == KIND_SOLDIER || c.kind == KIND_BRUTE) c.scale else 1f)
            enemySprite(sp, c.kind, c.bossType, 0f, 0f, c.dir, c.x, c.y + f * 12f, k, hitTint, a)
        }
    }
    private val hitTint = ColorFilter.tint(Color(0x55FFFFFF), BlendMode.SrcAtop)

    private fun walkFrame(phase: Float, frames: Int): Int {
        val f = ((phase / (2f * PI.toFloat())) * frames).toInt() % frames
        return if (f < 0) f + frames else f
    }

    private fun DrawScope.drawTree(x: Float, y: Float, s: Float, idx: Int) {
        val sp = sprites ?: return
        when (idx % 7) {
            0, 3, 5 -> sprite(sp.pine0, SpriteData.pine0, 0, 0, x, y, s)
            1, 4 -> sprite(sp.pine1, SpriteData.pine1, 0, 0, x, y, s)
            else -> sprite(sp.roundtree, SpriteData.roundtree, 0, 0, x, y, s * 1.2f)
        }
    }

    private fun DrawScope.drawRock(x: Float, y: Float, s: Float, idx: Int) {
        val sp = sprites ?: return
        if (idx % 2 == 0) sprite(sp.rock0, SpriteData.rock0, 0, 0, x, y, s)
        else sprite(sp.rock1, SpriteData.rock1, 0, 0, x, y, s)
    }

    private fun DrawScope.drawRingPiece(g: Game, k: Int) {
        val sp = sprites ?: return
        val x = g.ringX[k]
        val y = g.ringY[k]
        // wall block oriented by the local direction of the ring: wood, then stone from the stone age
        val j = if (k + 1 < g.ringCount && g.ringRun[k + 1]) k + 1 else k - 1
        val vertical = j >= 0 && abs(g.ringY[j] - y) > abs(g.ringX[j] - x)
        val stone = g.age >= 2
        if (vertical) sprite(if (stone) sp.wallStoneV else sp.wallWoodV, if (stone) SpriteData.wallStoneV else SpriteData.wallWoodV, 0, 0, x, y, 0.72f * WALL_SCALE)
        else sprite(if (stone) sp.wallStone else sp.wallWood, if (stone) SpriteData.wallStone else SpriteData.wallWood, 0, 0, x, y, 0.72f * WALL_SCALE)
    }

    // placeholder looks for the new buildings until their own models land
    private val eliteTint = ColorFilter.tint(Color(0x88FFD84A), BlendMode.SrcAtop)


    private fun DrawScope.drawBuilding(idx: Int, g: Game) {
        val sp = sprites ?: return
        when (idx) {
            3, 4 -> { // base spot: the last building bought there
                val u = g.lastUnlock(idx - 3)
                val x = if (idx == 3) World.SPOT_A_X else World.SPOT_B_X
                val y = if (idx == 3) World.SPOT_A_Y else World.SPOT_B_Y
                when (u) {
                    U_STABLE -> sprite(sp.stable, SpriteData.stable, 0, 0, x, y, 1f)
                    U_ALTAR -> sprite(sp.church, SpriteData.church, 0, 0, x, y, 1f)
                    U_MILL -> sprite(sp.watermill, SpriteData.watermill, 0, 0, x, y, 1f)
                    U_MARKET -> sprite(sp.market, SpriteData.market, 0, 0, x, y, 1f)
                    U_TREASURY -> sprite(sp.well, SpriteData.well, 0, 0, x, y, 1.2f, eliteTint)
                    U_GATEWORKS -> sprite(sp.blacksmith, SpriteData.blacksmith, 0, 0, x, y, 1f)
                    U_STATUE -> sprite(sp.lumbermill, SpriteData.lumbermill, 0, 0, x, y, 1f, eliteTint)
                    U_KEEP3 -> sprite(sp.towerRoyal, SpriteData.towerRoyal, 0, 0, x, y, 0.9f)
                    else -> sprite(sp.towerStone, SpriteData.towerStone, 0, 0, x, y, 0.9f) // keep II, banner
                }
            }
            5 -> { // level chest: bobbing golden crate with a glow
                val bob = sin(g.time * 4f) * 6f
                circ(CAPTAIN_AURA, g.chestX, g.chestY, 44f + 6f * sin(g.time * 6f))
                sprite(sp.crate, SpriteData.crate, 0, 0, g.chestX, g.chestY - 8f + bob, 2.2f, eliteTint)
            }
            0 -> sprite(sp.farm, SpriteData.farm, 0, 0, World.FARM_X, World.FARM_Y, 1f)
            1 -> sprite(sp.barracks, SpriteData.barracks, 0, 0, World.BARRACKS_X, World.BARRACKS_Y, 1f)
            else -> sprite(sp.mine, SpriteData.mine, 0, 0, World.MINE_X, World.MINE_Y, 1f)
        }
    }

    private fun DrawScope.drawProp(x: Float, y: Float, kind: Float) {
        val sp = sprites ?: return
        when (kind) {
            PROP_TENT -> sprite(sp.tent, SpriteData.tent, 0, 0, x, y, 1f)
            PROP_BARREL -> sprite(sp.barrel, SpriteData.barrel, 0, 0, x, y, 1f)
            PROP_CRATE -> sprite(sp.crate, SpriteData.crate, 0, 0, x, y, 1f)
            PROP_WHEELBARROW -> sprite(sp.wheelbarrow, SpriteData.wheelbarrow, 0, 0, x, y, 1f)
            PROP_LUMBER -> sprite(sp.lumber, SpriteData.lumber, 0, 0, x, y, 1f)
            PROP_WEAPONRACK -> sprite(sp.weaponrack, SpriteData.weaponrack, 0, 0, x, y, 1f)
            PROP_STUMP -> sprite(sp.stump, SpriteData.stump, 0, 0, x, y, 1f)
            else -> sprite(sp.bush, SpriteData.bush, 0, 0, x, y, 1f)
        }
    }

    private fun DrawScope.drawHouse(x: Float, y: Float) {
        val sp = sprites ?: return
        sprite(sp.house, SpriteData.house, 0, 0, x, y - 30f, 1f)
    }

    private fun DrawScope.drawCastle(g: Game) {
        val sp = sprites ?: return
        val royal = g.age >= 3
        sprite(if (royal) sp.castleRoyal else sp.castle, if (royal) SpriteData.castleRoyal else SpriteData.castle, 0, 0,
            World.CASTLE_X, World.CASTLE_Y - 30f, 0.9f, if (g.castleFlash > 0f) flashFilter else null)
    }

    private fun DrawScope.drawTower(t: Tower, age: Int) {
        val sp = sprites ?: return
        val b = 1f + t.bump * 0.15f
        when {
            age >= 3 -> sprite(sp.towerRoyal, SpriteData.towerRoyal, 0, 0, t.x, t.y, b)
            age == 2 -> sprite(sp.towerStone, SpriteData.towerStone, 0, 0, t.x, t.y, b)
            else -> sprite(sp.tower, SpriteData.tower, 0, 0, t.x, t.y, b)
        }
        val top = t.y - (if (age >= 3) 96f else 80f) * b
        for (a in 0 until t.archers) {
            val col = if (t.shoot[a] > 0f) 7 + ((0.32f - t.shoot[a]) / 0.32f * 3f).toInt().coerceIn(0, 2) else 0
            if (t.elite) sprite(sp.archerElite, SpriteData.archerElite, col, t.dir[a], t.x + (a - 1) * 24f, top + (a % 2) * 8f, UNIT_SCALE)
            else sprite(sp.archer, SpriteData.archer, col, t.dir[a], t.x + (a - 1) * 24f, top + (a % 2) * 8f, UNIT_SCALE)
        }
        if (t.ballista) sprite(sp.ballistaTop, SpriteData.ballistaTop, 0, 0, t.x, top - 6f, 1f)
        if (t.boltT > 0f) line(WHITE, t.x, top - 30f, t.boltX, t.boltY - 30f, 5f * (t.boltT / 0.18f))
    }

    private fun DrawScope.drawWall(w: Wall, age: Int, g: Game) {
        val sp = sprites ?: return
        val filter = if (w.hitFlash > 0f) flashFilter else null
        val b = WALL_SCALE * (1f + w.bump * 0.2f)
        // see-through when the king walks behind it
        val alpha = if (g.ky < w.y && g.ky > w.y - 200f && abs(g.kx - w.x) < RAMPART_HALF + 40f) 0.6f else 1f
        // segment, gate, segment along the wall line, drawn back to front: wooden palisade, then stone from the stone age
        val vertical = w.ry != 0f
        val stone = age >= 2
        oval(SHADOW, w.x - (if (vertical) 50f else RAMPART_HALF + 30f), w.y - (if (vertical) RAMPART_HALF + 20f else 26f),
            if (vertical) 100f else RAMPART_HALF * 2f + 60f, if (vertical) RAMPART_HALF * 2f + 40f else 52f)
        for (i in -1..1) { // left to right / top to bottom = back to front
            val x = w.x + w.rx * i * 90f
            val y = w.y + w.ry * i * 90f
            if (i == 0) {
                // gate: closed and open leaves cross-fade as it opens (0.25 s)
                val closedA = (1f - w.gate) * alpha
                val openA = w.gate * alpha
                if (stone) {
                    if (closedA > 0.01f) sprite(if (vertical) sp.gateClosedV else sp.gateClosed, if (vertical) SpriteData.gateClosedV else SpriteData.gateClosed, 0, 0, x, y, b, filter, closedA)
                    if (openA > 0.01f) sprite(if (vertical) sp.gateOpenV else sp.gateOpen, if (vertical) SpriteData.gateOpenV else SpriteData.gateOpen, 0, 0, x, y, b, filter, openA)
                } else {
                    if (closedA > 0.01f) sprite(if (vertical) sp.gateWoodClosedV else sp.gateWoodClosed, if (vertical) SpriteData.gateWoodClosedV else SpriteData.gateWoodClosed, 0, 0, x, y, b, filter, closedA)
                    if (openA > 0.01f) sprite(if (vertical) sp.gateWoodOpenV else sp.gateWoodOpen, if (vertical) SpriteData.gateWoodOpenV else SpriteData.gateWoodOpen, 0, 0, x, y, b, filter, openA)
                }
                // flickering flames on the gate's lanterns (the banner is part of the sprite)
                val off = if (stone) 51f else 35f // lanterns 1.6 m (stone) / 1.1 m (wood) from the centre
                val px = if (vertical) 0f else off
                val py = if (vertical) off else 0f
                val top = y - 64f * b
                for (sgn in -1..1 step 2) {
                    val tx = x + px * sgn
                    val ty = top + py * sgn
                    val fl = 0.8f + 0.2f * sin(g.time * 17f + sgn * 2f)
                    circ(TORCH_GLOW, tx, ty - 6f, 16f * fl)
                    circ(TORCH, tx, ty - 6f, 6f * fl)
                }
            } else {
                if (vertical) sprite(if (stone) sp.wallStoneV else sp.wallWoodV, if (stone) SpriteData.wallStoneV else SpriteData.wallWoodV, 0, 0, x, y, b, filter, alpha)
                else sprite(if (stone) sp.wallStone else sp.wallWood, if (stone) SpriteData.wallStone else SpriteData.wallWood, 0, 0, x, y, b, filter, alpha)
            }
        }
        if (w.hp < w.maxHp) hpBar(w.x, w.y - 110f, 80f, w.hp / w.maxHp, HP_GREEN)
    }

    private fun DrawScope.hpBar(cx: Float, y: Float, width: Float, frac: Float, col: Color) {
        rr(HP_BG, cx - width / 2f - 2f, y - 2f, width + 4f, 10f, 4f)
        rr(col, cx - width / 2f, y, width * frac.coerceIn(0f, 1f), 6f, 3f)
    }

    private fun DrawScope.drawArcher(g: Game, i: Int) {
        val sp = sprites ?: return
        val col = when {
            g.aShoot[i] > 0f -> 7 + ((0.32f - g.aShoot[i]) / 0.32f * 3f).toInt().coerceIn(0, 2)
            g.aMoving[i] -> 1 + walkFrame(g.aWalk[i], 6)
            else -> 0
        }
        sprite(sp.archer, SpriteData.archer, col, g.aDir[i], g.ax[i], g.ay[i], UNIT_SCALE)
    }

    private fun DrawScope.drawKing(g: Game) {
        val sp = sprites ?: return
        val x = g.kx
        val y = g.ky
        val moving = abs(g.kvx) + abs(g.kvy) > 30f
        val col = if (moving) 1 + walkFrame(g.kWalk, 6) else 0
        oval(KING_RING, x - 44f, y - 20f, 88f, 40f) // keeps the king findable under the thumb
        val kingImg = when (g.heroClass) { CLASS_WARRIOR -> sp.kingWarrior; CLASS_RANGER -> sp.kingRanger; else -> sp.kingBuilder }
        val lean = (g.kvx * 0.02f).coerceIn(-6f, 6f) // degrees, around the hooves
        withTransform({ rotate(lean, Offset(x, y)) }) {
            sprite(kingImg, SpriteData.kingWarrior, col, g.kDir, x, y, UNIT_SCALE, if (g.kHurtTimer > 2.85f) flashFilter else Shop.kingFilter(g.heroClass))
        }
        // coin stack on the king's head
        val n = min(g.coins, MAX_STACK)
        val bob = if (moving) abs(sin(g.kWalk * 2f)) * 2f else 0f
        val base = y - 70f - bob + g.stackBump * 3f
        for (i in 0 until n) {
            val cx = x + g.sway * min(i * i * 0.016f, 70f) // bend capped so a tall stack never leaves the screen sideways
            if (base - i * 4.5f < viewTop) break
            // above 30 coins draw one in two: the flat coins still overlap, so the column stays solid
            if (i >= 30 && i % 2 != 0 && i != n - 1) continue
            sprite(sp.coinFlat, SpriteData.coinFlat, 0, 0, cx, base - i * 4.5f, 1f)
        }
        if (g.kHp < g.kMaxHp) hpBar(x, y + 18f, 50f, g.kHp / g.kMaxHp, HP_GREEN) // under the king: the stack can be taller than the screen
    }


    /** Atlas cell for an enemy: picks the model by kind (and boss type) and the frame from its walk/attack state. */
    private fun DrawScope.enemySprite(sp: Sprites, kind: Int, bossType: Int, walk: Float, swing: Float, dir: Int, x: Float, y: Float, k: Float, filter: ColorFilter?, alpha: Float = 1f) {
        fun atk(first: Int, n: Int) = first + ((1f - swing) * n).toInt().coerceIn(0, n - 1)
        when (kind) {
            KIND_WOLF -> sprite(sp.wolf, SpriteData.wolf, if (swing > 0f) atk(6, 2) else walkFrame(walk, 6), dir, x, y, k, filter, alpha)
            KIND_ARCHER -> sprite(sp.enemyArcher, SpriteData.enemyArcher, if (swing > 0f) atk(5, 3) else 1 + walkFrame(walk, 4), dir, x, y, k, filter, alpha)
            KIND_SHIELD -> sprite(sp.shieldman, SpriteData.shieldman, if (swing > 0f) atk(6, 3) else walkFrame(walk, 6), dir, x, y, k, filter, alpha)
            KIND_RAM -> sprite(sp.ram, SpriteData.ram, if (swing > 0f) 1 else walkFrame(walk * 1.6f, 4), dir, x, y, k, filter, alpha) // log sways while pushed
            KIND_BOSS -> when (bossType) {
                1 -> sprite(sp.ogre, SpriteData.ogre, if (swing > 0f) atk(4, 3) else walkFrame(walk, 4), dir, x, y, k, filter, alpha)
                2 -> sprite(sp.darkLord, SpriteData.darkLord, if (swing > 0f) atk(4, 3) else walkFrame(walk, 4), dir, x, y, k, filter, alpha)
                else -> sprite(sp.boss, SpriteData.boss, if (swing > 0f) atk(4, 3) else walkFrame(walk, 4), dir, x, y, k, filter, alpha)
            }
            else -> sprite(sp.knight, SpriteData.knight, if (swing > 0f) atk(6, 3) else walkFrame(walk, 6), dir, x, y, k, filter, alpha)
        }
    }

    private fun DrawScope.drawEnemy(e: Enemy, bigBoss: Boolean) {
        val sp = sprites ?: return
        val boss = e.kind == 2
        val filter = if (e.hitFlash > 0f) flashFilter else if (e.elite || e.captain) eliteTint else null
        if (boss && e.slam > 0f) { // red warning circle, filling up until the slam lands
            val k = 1f - e.slam / 0.9f
            drawOval(ALERT_RED, Offset(e.x - OGRE_SLAM_R, e.y - OGRE_SLAM_R * 0.6f), Size(OGRE_SLAM_R * 2f, OGRE_SLAM_R * 1.2f), alpha = 0.18f + 0.25f * k)
            drawOval(ALERT_RED, Offset(e.x - OGRE_SLAM_R * k, e.y - OGRE_SLAM_R * 0.6f * k), Size(OGRE_SLAM_R * 2f * k, OGRE_SLAM_R * 1.2f * k), alpha = 0.35f)
        }
        // new models are made at their own size: only knights (soldier/brute) and the wood-age boss use e.scale
        val k = UNIT_SCALE * (if (e.kind == KIND_SOLDIER || e.kind == KIND_BRUTE) e.scale else 1f) * (if (boss && bigBoss) 1.15f else 1f) * (if (e.captain) 1.25f else 1f)
        if (e.captain) circ(CAPTAIN_AURA, e.x, e.y, 46f) // aura ring under the captain
        if (boss && e.stun > 0f) label(text.str("ÉTOURDI !"), e.x, e.y - 175f, 24f, GOLD) // world space: we are inside the camera transform
        enemySprite(sp, e.kind, e.bossType, e.walk, e.swing, e.dir, e.x, e.y, k, filter)
        if (boss || e.hp < e.maxHp) {
            val bw = if (boss) 110f else 34f * e.scale
            val top = if (boss) 150f else 62f * e.scale
            hpBar(e.x, e.y - top, bw, e.hp / e.maxHp, HP_RED)
        }
    }

    private fun DrawScope.drawGroundCoin(c: Coin, kx: Float, ky: Float) {
        val sp = sprites ?: return
        val frame = ((c.spin / PI.toFloat() * 8f).toInt() % 8 + 8) % 8
        if (c.state == 2) { // flying to the king: short trail in the shop colour
            val tc = Shop.trailColor
            val dx = c.x - kx; val dy = c.y - ky
            val inv = 13f / max(1f, sqrt(dx * dx + dy * dy))
            for (k in 1..3) drawCircle(tc, 9f - 2f * k, Offset(c.x + dx * inv * k, c.y - c.z + dy * inv * k + 3f * k), alpha = 0.55f - 0.15f * k)
        }
        sprite(sp.coinSpin, SpriteData.coinSpin, frame, 0, c.x, c.y - c.z, 1f)
    }

    private fun DrawScope.drawArrows(g: Game, vl: Float, vt: Float, vr: Float, vb: Float) {
        for (a in g.arrows) {
            if (!a.active) continue
            val px = a.x
            val py = a.y - a.z
            if (px < vl || px > vr || py < vt || py > vb) continue
            var dx = a.dx
            var dy = a.dy - a.dz
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= l; dy /= l
            if (a.z > 4f) line(SHADOW, a.x - a.dx * 20f, a.y - a.dy * 20f, a.x, a.y, 4f) // shadow on the ground, shows the arc
            line(Color(0x557FC8FF), px - dx * 46f, py - dy * 46f, px, py, 5f)
            line(WOOD_DARK, px - dx * 22f, py - dy * 22f, px, py, 2.5f)
            line(WHITE, px - dx * 4f, py - dy * 4f, px + dx * 3f, py + dy * 3f, 4f)
        }
    }

    private fun DrawScope.drawFlyCoins(g: Game) {
        val sp = sprites ?: return
        for (f in g.flyCoins) {
            if (!f.active || f.pad >= g.pads.size) continue
            val p = g.pads[f.pad]
            val t = f.t
            val x = f.sx + (p.x - f.sx) * t
            val y = f.sy + (p.y - f.sy) * t
            val z = (f.sz - 25f) * (1f - t) + sin(t * PI.toFloat()) * 60f
            sprite(sp.coinFlat, SpriteData.coinFlat, 0, 0, x, y - z, 1f)
        }
    }

    private fun DrawScope.drawParticles(g: Game, vl: Float, vt: Float, vr: Float, vb: Float) {
        for (p in g.particles) {
            if (!p.active || p.x < vl || p.x > vr || p.y - p.z < vt || p.y - p.z > vb) continue
            val k = p.life / p.maxLife
            val s = p.size * (0.4f + 0.6f * k)
            drawRect(Color(p.color.toInt()), Offset(p.x - s / 2f, p.y - p.z - s / 2f), Size(s, s), alpha = min(1f, k * 2f))
        }
    }

    // ------------------------------------------------------------ HUD

    private fun DrawScope.drawHud(g: Game, w: Float, h: Float) {
        val u = min(w, h) / 400f // HUD unit
        val top = topInset + 10f * u
        if (g.state != STATE_MENU && g.transition <= 0f) {
            // --- top-left: hero portrait, level badge, level progress
            val pr = 25f * u
            val pcx = 12f * u + pr
            val pcy = top + pr
            val ui = hud
            if (ui != null) img(ui.circle, pcx, pcy, pr * 1.25f) else {
                circ(HUD_SHADOW, pcx, pcy + 3f * u, pr + 3f * u)
                circ(GOLD_DARK, pcx, pcy, pr + 3f * u)
            }
            circ(CLASS_COLORS[g.heroClass], pcx, pcy, pr * 0.9f)
            val sp = sprites
            if (sp != null) {
                portraitClip.reset()
                portraitClip.addOval(androidx.compose.ui.geometry.Rect(pcx - pr, pcy - pr, pcx + pr, pcy + pr))
                clipPath(portraitClip) {
                    val img = when (g.heroClass) { CLASS_WARRIOR -> sp.portraitWarrior; CLASS_RANGER -> sp.portraitRanger; else -> sp.portraitBuilder }
                    drawImage(img, IntOffset.Zero, IntSize(256, 256), IntOffset((pcx - pr * 1.1f).toInt(), (pcy - pr * 1.1f).toInt()), IntSize((pr * 2.2f).toInt(), (pr * 2.2f).toInt()))
                }
            } else drawClassIcon(g.heroClass, pcx, pcy, pr * 0.62f, u)
            val bcx = pcx + pr * 0.78f
            val bcy = pcy + pr * 0.78f
            circ(GOLD_DARK, bcx, bcy, 12f * u)
            circ(GOLD, bcx, bcy, 10f * u)
            label(text.num(g.level), bcx, bcy, 13f * u, Color(0xFF4A2A00), outlined = false)
            val lx = pcx + pr + 8f * u
            val lw = 112f * u
            if (ui != null) nineSlice(ui.panelDark, IN_PANEL, lx, top + 2f * u, lw, 48f * u, 0.12f * u) else rr(HUD_BG, lx, top + 4f * u, lw, 44f * u, 12f * u)
            label(text.str(CLASS_NAMES[g.heroClass]), lx + lw / 2f, top + 15f * u, 13f * u, WHITE, outlined = false)
            label(text.str(World.levelName(g.level)), lx + lw / 2f, top + 28f * u, 10f * u, GOLD, outlined = false)
            if (ui != null) bar(ui, ui.barGold, lx + 8f * u, top + 35f * u, lw - 16f * u, 8f * u, g.levelProgress) else {
                rr(HUD_TRACK, lx + 8f * u, top + 37f * u, lw - 16f * u, 6f * u, 3f * u)
                rr(GOLD, lx + 8f * u, top + 37f * u, (lw - 16f * u) * g.levelProgress, 6f * u, 3f * u)
            }
            val nu = g.nextUnlock
            if (nu >= 0) label(text.str(nextLabel(nu)), lx + lw / 2f, top + 56f * u, 9.5f * u, WHITE)
            // --- top-right: coins and pause
            val ph = 34f * u
            val pauseR = 17f * u
            pauseX = w - 12f * u - pauseR; pauseY = top + pauseR; pauseRadius = pauseR + 8f * u
            if (ui != null) { img(ui.circle, pauseX, pauseY, pauseR * 1.15f); img(ui.pause, pauseX, pauseY, pauseR * 0.7f) } else {
                circ(HUD_SHADOW, pauseX, pauseY + 3f * u, pauseR)
                circ(HUD_BUTTON, pauseX, pauseY, pauseR)
                rr(WHITE, pauseX - 6f * u, pauseY - 7f * u, 4f * u, 14f * u, 1.5f * u)
                rr(WHITE, pauseX + 2f * u, pauseY - 7f * u, 4f * u, 14f * u, 1.5f * u)
            }
            val pw = 96f * u
            val px = pauseX - pauseR - 8f * u - pw
            if (ui != null) {
                nineSlice(ui.pill, IN_PILL, px, top + 1f * u, pw, ph, ph / 90f)
                img(ui.coins, px + ph / 2f + 2f * u, top + 1f * u + ph / 2f, ph * 0.42f)
            } else {
                rr(HUD_BG, px, top + 1f * u, pw, ph, ph / 2f)
                drawCoinIcon(px + ph / 2f, top + 1f * u + ph / 2f, ph * 0.46f)
            }
            label(text.num(g.coins), px + pw * 0.6f, top + 1f * u + ph / 2f, 22f * u * (1f + g.stackBump * 0.18f), WHITE)
            // --- centre: wave pill and keep health
            val by = top + 62f * u
            val wpw = 112f * u
            if (ui != null) {
                nineSlice(ui.ribbonBlue, IN_RIBBON, w / 2f - wpw * 0.75f, by - 6f * u, wpw * 1.5f, 32f * u, 32f * u / 170f)
                label(text.str(waveLabel(g.wave).uppercase()), w / 2f, by + 8f * u, 13f * u, Color(0xFFFFE896))
            } else {
                rr(HUD_SHADOW, w / 2f - wpw / 2f, by + 2f * u, wpw, 24f * u, 12f * u)
                rr(WAVE_RED, w / 2f - wpw / 2f, by, wpw, 24f * u, 12f * u)
                label(text.str(waveLabel(g.wave).uppercase()), w / 2f, by + 12f * u, 14f * u, WHITE)
            }
            val bw = min(w * 0.55f, 220f * u)
            val bx = w / 2f - bw / 2f
            val hy = by + 30f * u
            val frac = (g.castleHp / g.castleMaxHp).coerceIn(0f, 1f)
            if (ui != null) bar(ui, if (frac > 0.35f) ui.barGreen else ui.barRed, bx, hy - 3f * u, bw, 12f * u, frac) else {
                rr(HUD_BG, bx - 3f * u, hy - 3f * u, bw + 6f * u, 13f * u, 6.5f * u)
                rr(if (frac > 0.35f) HP_GREEN else HP_RED, bx, hy, bw * frac, 7f * u, 3.5f * u)
            }
            label(text.str("Donjon"), w / 2f, hy + 17f * u, 11f * u, WHITE)
            // wave countdown
            if (g.toSpawn == 0 && !g.bossPending && g.waveBreak < 7f && g.waveBreak > 0f && g.enemies.none { it.active }) {
                label(text.str("Prochaine vague dans"), w / 2f, hy + 38f * u, 14f * u, WHITE)
                label(text.num(g.waveBreak.toInt() + 1), w / 2f, hy + 62f * u, 24f * u, GOLD)
            }
            // --- bottom-right: class ability with cooldown
            if (g.state == STATE_PLAY) drawAbilityButton(g, w, h, u)
        }
        if (g.state == STATE_PLAY && g.transition <= 0f) {
            drawEdgeArrows(g, w, h, u, top + 110f * u)
            if (g.castleAlarm > 0f) {
                val pulse = 0.55f + 0.45f * sin(g.time * 12f)
                label(text.str("Le donjon est attaqué !"), w / 2f, top + 128f * u, 20f * u * (1f + pulse * 0.06f), ALERT_RED, min(1f, g.castleAlarm) * pulse)
            }
        }
        // banners
        if (g.levelBanner > 0f) {
            // sunburst behind the title, like the ads
            val ra = min(1f, g.levelBanner * 1.5f) * 0.35f
            withTransform({ translate(w / 2f, h * 0.27f) }) {
                for (k in 0 until 14) {
                    withTransform({ rotate(g.time * 25f + k * (360f / 14f), Offset.Zero); scale(w * 0.9f, 26f * u, Offset.Zero) }) {
                        drawPath(sunRay, GOLD, alpha = ra)
                    }
                }
            }
            val k = g.levelBanner / 3.5f
            val pop = if (k > 0.85f) 1f + (k - 0.85f) * 4f else 1f
            val a = min(1f, g.levelBanner * 2f)
            label(text.str(levelDoneLabel(g.level - 1)), w / 2f, h * 0.27f, 36f * u * pop, GOLD, a)

            val newChapter = World.chapter(g.level) != World.chapter(g.level - 1) || g.level == World.LAST_LEVEL + 1
            val themeTxt = World.THEME_TEXT[World.theme(g.level)]
            if (themeTxt.isNotEmpty()) label(text.str(themeTxt), w / 2f, h * 0.27f + 160f * u, 14f * u, ALERT_RED, a)
            if (g.newUnlocks.isNotEmpty()) {
                rr(HUD_BG, 30f * u, h * 0.27f + 96f * u, w - 60f * u, 30f * u, 15f * u)
                label(text.str(newLabel(g.newUnlocks)), w / 2f, h * 0.27f + 111f * u, 13f * u, GOLD, a)
            }

            if (newChapter) label(text.str(if (g.isEndless) "MODE INFINI !" else "CHAPITRE ${World.chapter(g.level) + 1} : " + World.CHAPTER_NAMES[World.chapter(g.level)].uppercase()),
                w / 2f, h * 0.27f - 40f * u, 20f * u, WHITE, a)
            if (g.ageChanged) {
                label(text.str(if (g.age >= 3) "ÂGE ROYAL !" else "ÂGE DE PIERRE !"), w / 2f, h * 0.27f + 44f * u, 30f * u * pop, WHITE, a)
                label(text.str(if (g.age >= 3) "Tours royales · dégâts ×1,5 · château royal" else "Remparts de pierre avec portes · tours de pierre"), w / 2f, h * 0.27f + 76f * u, 14f * u, WHITE, a)
            } else {
                label(text.str(World.levelName(g.level)), w / 2f, h * 0.27f + 44f * u, 30f * u * pop, WHITE, a)
                label(text.str(if (g.roadAdded) "Nouvelle route à défendre !" else "Les monstres se renforcent !"), w / 2f, h * 0.27f + 76f * u, 15f * u, WHITE, a)
            }
        }
        if (g.waveBanner > 0f && g.levelBanner <= 0f) {
            val k = g.waveBanner / 2.2f
            val pop = if (k > 0.85f) 1f + (k - 0.85f) * 4f else 1f
            label(text.str(waveLabel(g.wave).uppercase()), w / 2f, h * 0.3f, 46f * u * pop, WHITE, min(1f, k * 3f))
        }
        if (g.waveRewardBanner > 0f && g.levelBanner <= 0f) {
            val k = g.waveRewardBanner / 2f
            val pop = if (k > 0.85f) 1f + (k - 0.85f) * 4f else 1f
            label(text.str("VAGUE REPOUSSÉE !"), w / 2f, h * 0.3f, 34f * u * pop, GOLD, min(1f, k * 3f))
            label(text.str("Butin du donjon"), w / 2f, h * 0.3f + 34f * u, 16f * u, WHITE, min(1f, k * 3f))
        }
        if (g.chestBanner > 0f) {
            val k = g.chestBanner / 2.5f
            label(text.str("COFFRE OUVERT !"), w / 2f, h * 0.36f, 28f * u * (1f + 0.1f * k), GOLD, min(1f, k * 3f))
        }
        if (g.caravanBanner > 0f) {
            val k = g.caravanBanner / 2.5f
            label(text.str("CARAVANE DE BUTIN !"), w / 2f, h * 0.36f, 28f * u * (1f + 0.1f * k), GOLD, min(1f, k * 3f))
        }
        if (g.bossBanner > 0f) {
            val k = g.bossBanner / 2.2f
            val pop = 1f + sin(k * 20f) * 0.05f
            label(text.str("BOSS !"), w / 2f, h * 0.38f, 56f * u * pop, Color(0xFFFF4A3A), min(1f, k * 3f))
        }
        if (g.state == STATE_PLAY && g.kDead) {
            label(text.str("Le roi est tombé..."), w / 2f, h * 0.45f, 26f * u, WHITE)
        }
        val obj = g.objective
        if (g.state == STATE_PLAY && obj != null && g.transition <= 0f) {
            val msg = when (obj.kind) { PAD_TOWER -> "Objectif : construis une tour"; PAD_TOWER_ARCHER -> "Objectif : poste un archer sur la tour"; else -> "Objectif : ferme la route avec une palissade" }
            val by = h - 150f * u
            rr(HUD_BG, 24f * u, by - 18f * u, w - 48f * u, 36f * u, 18f * u)
            label(text.str(msg), w / 2f, by, 13.5f * u, GOLD)
            val sx = viewOx + obj.x * scale
            val sy = viewOy + (obj.y - Pad.HALF - 30f) * scale - (8f + 8f * sin(g.time * 7f)) * u
            withTransform({ translate(sx, sy); rotate(90f, Offset.Zero); scale(22f * u, 22f * u, Offset.Zero) }) { drawPath(edgeArrow, GOLD) }
        }
        if (g.state == STATE_PLAY && g.tutorial < 4 && g.transition <= 0f) {
            val msg = when (g.tutorial) {
                0 -> "Glisse le doigt pour bouger le roi"
                1 -> "Tue les chevaliers et ramasse leur or"
                2 -> "Marche sur le chantier Archer pour recruter"
                else -> "Construis une tour : reste sur son chantier"
            }
            val by = h - 150f * u
            rr(HUD_BG, 24f * u, by - 18f * u, w - 48f * u, 36f * u, 18f * u)
            label(text.str(msg), w / 2f, by, 13.5f * u * (1f + 0.04f * sin(g.time * 5f)), WHITE)
            val p = g.tutorialTarget
            if (p != null) {
                val sx = viewOx + p.x * scale
                val sy = viewOy + (p.y - Pad.HALF - 30f) * scale - (8f + 8f * sin(g.time * 7f)) * u
                withTransform({ translate(sx, sy); rotate(90f, Offset.Zero); scale(22f * u, 22f * u, Offset.Zero) }) { drawPath(edgeArrow, GOLD) }
            }
        }
        // fps
        label(text.str(fpsLabel(g.fps)), 34f * u, h - 16f * u, 11f * u, Color(0xCCFFFFFF), outlined = false)
        // joystick
        val inp = g.input
        if (inp.touching && g.state == STATE_PLAY) {
            val r = inp.radiusPx
            circ(Color(0x33FFFFFF), inp.ox, inp.oy, r)
            drawCircle(Color(0x88FFFFFF), r, Offset(inp.ox, inp.oy), style = Stroke(3f * u))
            var dx = inp.cx - inp.ox
            var dy = inp.cy - inp.oy
            val d = sqrt(dx * dx + dy * dy)
            if (d > r) { dx = dx / d * r; dy = dy / d * r }
            circ(Color(0xCCFFFFFF), inp.ox + dx, inp.oy + dy, r * 0.42f)
        }
    }

    /** Red arrows on the screen edge pointing at enemies outside the view (first [MAX_EDGE_ARROWS] found). */
    private fun DrawScope.drawEdgeArrows(g: Game, w: Float, h: Float, u: Float, topEdge: Float) {
        val m = 26f * u
        val cx = w / 2f
        val cy = h / 2f
        var n = 0
        for (e in g.enemies) {
            if (!e.active || n >= MAX_EDGE_ARROWS) continue
            val sx = viewOx + e.x * scale
            val sy = viewOy + (e.y - 40f) * scale
            if (sx in 0f..w && sy in 0f..h) continue
            val dx = sx - cx
            val dy = sy - cy
            val tx = if (dx > 0f) (w - m - cx) / dx else if (dx < 0f) (m - cx) / dx else Float.MAX_VALUE
            val ty = if (dy > 0f) (h - m - cy) / dy else if (dy < 0f) (topEdge - cy) / dy else Float.MAX_VALUE
            val t = min(tx, ty)
            val deg = kotlin.math.atan2(dy, dx) * (180f / PI.toFloat())
            val k = if (e.kind == 2) 22f * u else 15f * u
            withTransform({
                translate(cx + dx * t, cy + dy * t)
                rotate(deg, Offset.Zero)
                scale(k, k, Offset.Zero)
            }) { drawPath(edgeArrow, ALERT_RED) }
            n++
        }
    }

    /** Square image centred on (x, y) with half-size [r]. */
    private fun DrawScope.img(im: ImageBitmap, x: Float, y: Float, r: Float) =
        drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset((x - r).toInt(), (y - r).toInt()), IntSize((r * 2).toInt(), (r * 2).toInt()), filterQuality = FilterQuality.High)

    private fun DrawScope.bar(ui: HudImages, fill: ImageBitmap, x: Float, y: Float, w: Float, h: Float, frac: Float) {
        nineSlice(ui.barTrack, IN_BAR, x, y, w, h, h / 44f)
        val fw = w * frac.coerceIn(0f, 1f)
        if (fw > 2f) nineSlice(fill, IN_BAR, x, y, fw, h, h / 44f)
    }

    /** Hit areas of the HUD buttons, in screen pixels (read by App's touch handler). */
    var pauseX = -1000f; var pauseY = -1000f; var pauseRadius = 0f
    var abilityX = -1000f; var abilityY = -1000f; var abilityRadius = 0f

    private fun DrawScope.drawAbilityButton(g: Game, w: Float, h: Float, u: Float) {
        val r = 34f * u
        abilityX = w - 18f * u - r; abilityY = h - 46f * u - r; abilityRadius = r + 10f * u
        val ready = g.abilityCd <= 0f
        if (ready) circ(GOLD, abilityX, abilityY, r + (5f + 3f * sin(g.time * 6f)) * u) // ready glow
        val ui = hud
        if (ui != null) {
            img(ui.circleRed, abilityX, abilityY, r * 1.12f)
            img(ui.classIcons[g.heroClass], abilityX, abilityY, r * 0.62f)
        } else {
            circ(HUD_SHADOW, abilityX, abilityY + 4f * u, r)
            circ(GOLD_DARK, abilityX, abilityY, r)
            circ(CLASS_COLORS[g.heroClass], abilityX, abilityY, r - 4f * u)
            drawAbilityIcon(g.heroClass, abilityX, abilityY, r * 0.55f, u)
        }
        if (!ready) {
            val f = (g.abilityCd / g.abilityTime).coerceIn(0f, 1f)
            drawArc(COOLDOWN, -90f, 360f * f, true, Offset(abilityX - r + 4f * u, abilityY - r + 4f * u), Size((r - 4f * u) * 2f, (r - 4f * u) * 2f))
            label(text.num(g.abilityCd.toInt() + 1), abilityX, abilityY, 22f * u, WHITE)
        }
        label(text.str(ABILITY_NAMES[g.heroClass]), abilityX, abilityY + r + 12f * u, 11f * u, WHITE)
    }

    private fun DrawScope.drawClassIcon(cls: Int, x: Float, y: Float, s: Float, u: Float) = classIcon(cls, x, y, s, 4.5f * u)
    private fun DrawScope.drawAbilityIcon(cls: Int, x: Float, y: Float, s: Float, u: Float) = classIcon(cls, x, y, s, 4.5f * u)

    private val ageLabels = arrayOf("Âge du Bois", "Âge de Pierre", "Âge Royal")
    private fun ageLabel(age: Int) = ageLabels[(age - 1).coerceIn(0, 2)]
    /** Unit ray (tip at the origin, widening to x = 1) for the level-up sunburst. */
    private val sunRay = Path().apply { moveTo(0f, 0f); lineTo(1f, -1f); lineTo(1f, 1f); close() }

    private val waveLabels = HashMap<Int, String>()
    private fun waveLabel(n: Int) = waveLabels.getOrPut(n) { "Vague ${max(n, 1)}" }
    private val STAR_LABELS = arrayOf("☆☆☆", "★☆☆", "★★☆", "★★★")
    private val lockLabels = HashMap<Int, String>()
    private fun lockLabel(lv: Int) = lockLabels.getOrPut(lv) { "Niveau $lv" }
    private val nextLabels = HashMap<Int, String>()
    private fun nextLabel(u: Int) = nextLabels.getOrPut(u) { "Prochain : " + UNLOCK_NAMES[u] + " (niv. " + UNLOCK_LEVEL[u] + ")" }
    private val newLabels = HashMap<String, String>()
    private fun newLabel(s: String) = newLabels.getOrPut(s) { "NOUVEAU : $s" }
    private val levelLabels = HashMap<Int, String>()
    private fun levelLabel(n: Int) = levelLabels.getOrPut(n) { "Niveau $n · " + (if (n >= 5) "Royal" else if (n >= 3) "Pierre" else "Bois") }
    private val levelDoneLabels = HashMap<Int, String>()
    private fun levelDoneLabel(n: Int) = levelDoneLabels.getOrPut(n) { "NIVEAU $n TERMINÉ !" }
    private val fpsLabels = HashMap<Int, String>()
    private fun fpsLabel(n: Int) = fpsLabels.getOrPut(n) { "$n fps" }
}

/** Simple vector class icons (crossed swords, bow, hammer), shared by the HUD and the menu cards. */
fun DrawScope.classIcon(cls: Int, x: Float, y: Float, s: Float, lw: Float) {
    val c = Color.White
    when (cls) {
        CLASS_WARRIOR -> {
            drawLine(c, Offset(x - s, y + s), Offset(x + s * 0.8f, y - s * 0.8f), lw, StrokeCap.Round)
            drawLine(c, Offset(x + s, y + s), Offset(x - s * 0.8f, y - s * 0.8f), lw, StrokeCap.Round)
            drawLine(c, Offset(x - s * 0.95f, y + s * 0.45f), Offset(x - s * 0.45f, y + s * 0.95f), lw, StrokeCap.Round)
            drawLine(c, Offset(x + s * 0.95f, y + s * 0.45f), Offset(x + s * 0.45f, y + s * 0.95f), lw, StrokeCap.Round)
        }
        CLASS_RANGER -> {
            drawArc(c, -70f, 140f, false, Offset(x - s * 1.4f, y - s), Size(s * 2f, s * 2f), style = Stroke(lw))
            drawLine(c, Offset(x - s * 0.06f, y - s * 0.94f), Offset(x - s * 0.06f, y + s * 0.94f), lw * 0.45f)
            drawLine(c, Offset(x - s * 0.9f, y), Offset(x + s * 0.9f, y), lw * 0.7f, StrokeCap.Round)
            drawLine(c, Offset(x + s * 0.9f, y), Offset(x + s * 0.55f, y - s * 0.3f), lw * 0.7f, StrokeCap.Round)
            drawLine(c, Offset(x + s * 0.9f, y), Offset(x + s * 0.55f, y + s * 0.3f), lw * 0.7f, StrokeCap.Round)
        }
        else -> {
            drawLine(c, Offset(x - s * 0.7f, y + s), Offset(x + s * 0.3f, y - s * 0.1f), lw, StrokeCap.Round)
            drawRoundRect(c, Offset(x - s * 0.1f, y - s * 0.9f), Size(s * 1.1f, s * 0.6f), CornerRadius(lw * 0.4f, lw * 0.4f))
        }
    }
}
