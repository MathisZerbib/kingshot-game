package fr.kingshot.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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

// One palette per level, cycling: spring, autumn, winter (grass, dark patch, light patch, cliff top, cliff top light)
private val SEASONS = arrayOf(
    arrayOf(Color(0xFF8DC24F), Color(0xFF7DB443), Color(0xFF9CCD5C), Color(0xFF86A83F), Color(0xFF94B54A), Color(0xFF5E9A2E)),
    arrayOf(Color(0xFFC2B25A), Color(0xFFB2A04C), Color(0xFFD0BF68), Color(0xFFAE9A45), Color(0xFFBCA851), Color(0xFF9A7F33)),
    arrayOf(Color(0xFFE4ECEF), Color(0xFFD2DDE3), Color(0xFFF1F5F7), Color(0xFFD8E2E7), Color(0xFFE8EEF1), Color(0xFFAFC0CA)),
)
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
    private var scale = 1f
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
        scale = (if (portrait) w / 560f else min(w / 1000f, h / 620f)) * g.camZoom
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
        season = SEASONS[(g.level - 1) % SEASONS.size]
        drawRect(season[0])
        withTransform({
            translate(ox, oy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawGround(vl, vt, vr, vb)
            for (p in g.pads) if (p.visible) drawPad(p, p.kind == PAD_TOWER_ARCHER && g.squad == 0)
            drawSorted(g, vl, vt, vr, vb)
            drawArrows(g)
            drawFlyCoins(g)
            drawParticles(g)
        }

        // world-anchored labels in screen space
        for (p in g.pads) {
            if (!p.visible) continue
            val sx = ox + p.x * scale
            val sy = oy + p.y * scale
            if (sx < -200 || sx > w + 200 || sy < -200 || sy > h + 200) continue
            val pop = 1f + p.bump * 0.25f
            label(text.num(p.cost - p.paid), sx + 18f * scale, sy - 4f * scale, 34f * scale * pop, WHITE)
            label(text.str(padName(p)), sx, sy + (Pad.HALF + 16f) * scale, 17f * scale, WHITE)
        }
        for (t in g.texts) {
            if (!t.active) continue
            val k = t.life / 0.8f
            val pop = if (k > 0.8f) 1f + (k - 0.8f) * 3f else 1f
            val px = (if (t.big) 34f else 24f) * scale * pop
            label(text.neg(t.value), ox + t.x * scale, oy + (t.y - t.z) * scale, px, if (t.big) Color(0xFFFFD84A) else WHITE, min(1f, k * 2.5f))
        }

        drawHud(g, w, h)
    }

    private fun padName(p: Pad): String = when (p.kind) {
        PAD_RECRUIT -> "Archer"
        PAD_FORGE -> "Forge  +dégâts"
        PAD_REPAIR -> "Réparer le donjon"
        PAD_TOWER -> "Tour"
        PAD_TOWER_ARCHER -> "1 archer → tour"
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
        val frac = (p.paid.toFloat() / p.cost).coerceIn(0f, 1f)
        if (frac > 0f) rr(PAD_FILL, x0, y0, size * frac, size, 14f)
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
        } else drawCoinIcon(p.x - 22f, p.y - 4f, 13f)
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
        val ringStride = if (g.age >= 2) 2 else 1
        for (k in 0 until g.ringCount) if (k % ringStride == 0 && vis(g.ringX[k], g.ringY[k])) n = push(n, g.ringY[k], K_RING, k)
        n = push(n, World.HOUSE_Y, K_HOUSE, 0)
        n = push(n, World.CASTLE_Y, K_CASTLE, 0)
        for (k in g.towers.indices) if (g.towers[k].built) n = push(n, g.towers[k].y, K_TOWER, k)
        for (k in g.walls.indices) if (g.walls[k].built) n = push(n, g.walls[k].y, K_WALL, k)
        for (k in g.enemies.indices) { val e = g.enemies[k]; if (e.active && vis(e.x, e.y)) n = push(n, e.y, K_ENEMY, k) }
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
                K_WALL -> drawWall(g.walls[idx], g.age)
                K_ENEMY -> drawEnemy(g.enemies[idx])
                K_ARCHER -> drawArcher(g, idx)
                K_KING -> drawKing(g)
                K_COIN -> drawGroundCoin(g.coinsOnGround[idx])
            }
        }
    }

    // ------------------------------------------------------------ sprites

    /** Draws one atlas cell with its anchor (feet) at world (x, y); [k] scales the model. */
    private fun DrawScope.sprite(img: ImageBitmap, info: SpriteInfo, col: Int, row: Int, x: Float, y: Float, k: Float, filter: ColorFilter? = null) {
        if (img.width < info.w * info.cols || img.height < info.h * info.rows) return
        val s = k / info.ppu
        withTransform({
            translate(x - info.ax * s, y - info.ay * s)
            scale(s, s, Offset.Zero)
        }) {
            drawImage(img, IntOffset(col * info.w, row * info.h), IntSize(info.w, info.h), IntOffset.Zero, IntSize(info.w, info.h), colorFilter = filter)
        }
    }

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
        if (g.age >= 2) {
            // stone block oriented by the local direction of the ring
            val j = if (k + 1 < g.ringCount && g.ringRun[k + 1]) k + 1 else k - 1
            val vertical = j >= 0 && abs(g.ringY[j] - y) > abs(g.ringX[j] - x)
            if (vertical) sprite(sp.wallStoneV, SpriteData.wallStoneV, 0, 0, x, y, 0.72f)
            else sprite(sp.wallStone, SpriteData.wallStone, 0, 0, x, y, 0.72f)
        } else {
            sprite(sp.stake, SpriteData.stake, 0, 0, x, y, 0.95f + ((k * 7) % 3) * 0.06f)
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
            sprite(sp.archer, SpriteData.archer, col, t.dir[a], t.x + (a - 1) * 24f, top + (a % 2) * 8f, UNIT_SCALE)
        }
    }

    private fun DrawScope.drawWall(w: Wall, age: Int) {
        val sp = sprites ?: return
        val filter = if (w.hitFlash > 0f) flashFilter else null
        val b = 1f + w.bump * 0.2f
        if (age >= 2) {
            // stone rampart: segment, gate, segment along the wall line, drawn back to front
            val vertical = w.ry != 0f
            val open = w.gate > 0.5f
            for (i in -1..1) { // left to right / top to bottom = back to front
                val x = w.x + w.rx * i * 90f
                val y = w.y + w.ry * i * 90f
                if (i == 0) {
                    if (vertical) sprite(if (open) sp.gateOpenV else sp.gateClosedV, if (open) SpriteData.gateOpenV else SpriteData.gateClosedV, 0, 0, x, y, b, filter)
                    else sprite(if (open) sp.gateOpen else sp.gateClosed, if (open) SpriteData.gateOpen else SpriteData.gateClosed, 0, 0, x, y, b, filter)
                } else {
                    if (vertical) sprite(sp.wallStoneV, SpriteData.wallStoneV, 0, 0, x, y, b, filter)
                    else sprite(sp.wallStone, SpriteData.wallStone, 0, 0, x, y, b, filter)
                }
            }
        } else {
            val stakes = 9
            val reverse = w.ny < 0f
            val slide = w.gate * 26f
            for (k0 in 0 until stakes) {
                val k = if (reverse) stakes - 1 - k0 else k0
                if (k == 4 && w.gate > 0.5f) continue // the middle stake swings away
                var tt = (k - (stakes - 1) / 2f) * 20f
                if (k == 3) tt -= slide else if (k == 5) tt += slide
                sprite(sp.stake, SpriteData.stake, 0, 0, w.x + w.nx * tt, w.y + w.ny * tt, b * (0.95f + ((k * 7) % 3) * 0.06f), filter)
            }
        }
        if (w.hp < w.maxHp) hpBar(w.x, w.y - 64f, 70f, w.hp / w.maxHp, HP_GREEN)
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
        sprite(sp.king, SpriteData.king, col, g.kDir, x, y, UNIT_SCALE, if (g.kHurtTimer > 2.85f) flashFilter else null)
        // coin stack on the king's head
        val n = min(g.coins, MAX_STACK)
        val bob = if (moving) abs(sin(g.kWalk * 2f)) * 2f else 0f
        val base = y - 70f - bob + g.stackBump * 3f
        for (i in 0 until n) {
            val cx = x + g.sway * min(i * i * 0.016f, 70f) // bend capped so a tall stack never leaves the screen sideways
            sprite(sp.coinFlat, SpriteData.coinFlat, 0, 0, cx, base - i * 4.5f, 1f)
        }
        if (g.kHp < g.kMaxHp) hpBar(x, y + 18f, 50f, g.kHp / g.kMaxHp, HP_GREEN) // under the king: the stack can be taller than the screen
    }

    private fun DrawScope.drawEnemy(e: Enemy) {
        val sp = sprites ?: return
        val boss = e.kind == 2
        val filter = if (e.hitFlash > 0f) flashFilter else null
        if (boss) {
            val col = if (e.swing > 0f) 4 + ((1f - e.swing) * 3f).toInt().coerceIn(0, 2) else walkFrame(e.walk, 4)
            sprite(sp.boss, SpriteData.boss, col, e.dir, e.x, e.y, UNIT_SCALE, filter)
        } else {
            val col = if (e.swing > 0f) 6 + ((1f - e.swing) * 3f).toInt().coerceIn(0, 2) else walkFrame(e.walk, 6)
            sprite(sp.knight, SpriteData.knight, col, e.dir, e.x, e.y, UNIT_SCALE * e.scale, filter)
        }
        if (boss || e.hp < e.maxHp) {
            val bw = if (boss) 110f else 34f * e.scale
            val top = if (boss) 150f else 62f * e.scale
            hpBar(e.x, e.y - top, bw, e.hp / e.maxHp, HP_RED)
        }
    }

    private fun DrawScope.drawGroundCoin(c: Coin) {
        val sp = sprites ?: return
        val frame = ((c.spin / PI.toFloat() * 8f).toInt() % 8 + 8) % 8
        sprite(sp.coinSpin, SpriteData.coinSpin, frame, 0, c.x, c.y - c.z, 1f)
    }

    private fun DrawScope.drawArrows(g: Game) {
        for (a in g.arrows) {
            if (!a.active) continue
            val px = a.x
            val py = a.y - a.z
            var dx = a.dx
            var dy = a.dy - a.dz
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= l; dy /= l
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

    private fun DrawScope.drawParticles(g: Game) {
        for (p in g.particles) {
            if (!p.active) continue
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
            circ(HUD_SHADOW, pcx, pcy + 3f * u, pr + 3f * u)
            circ(GOLD_DARK, pcx, pcy, pr + 3f * u)
            circ(CLASS_COLORS[g.heroClass], pcx, pcy, pr)
            drawClassIcon(g.heroClass, pcx, pcy, pr * 0.62f, u)
            val bcx = pcx + pr * 0.78f
            val bcy = pcy + pr * 0.78f
            circ(GOLD_DARK, bcx, bcy, 12f * u)
            circ(GOLD, bcx, bcy, 10f * u)
            label(text.num(g.level), bcx, bcy, 13f * u, Color(0xFF4A2A00), outlined = false)
            val lx = pcx + pr + 8f * u
            val lw = 112f * u
            rr(HUD_BG, lx, top + 4f * u, lw, 44f * u, 12f * u)
            label(text.str(CLASS_NAMES[g.heroClass]), lx + lw / 2f, top + 15f * u, 13f * u, WHITE, outlined = false)
            label(text.str(World.levelName(g.level)), lx + lw / 2f, top + 28f * u, 10f * u, GOLD, outlined = false)
            rr(HUD_TRACK, lx + 8f * u, top + 37f * u, lw - 16f * u, 6f * u, 3f * u)
            rr(GOLD, lx + 8f * u, top + 37f * u, (lw - 16f * u) * g.levelProgress, 6f * u, 3f * u)
            // --- top-right: coins and pause
            val ph = 34f * u
            val pauseR = 17f * u
            pauseX = w - 12f * u - pauseR; pauseY = top + pauseR; pauseRadius = pauseR + 8f * u
            circ(HUD_SHADOW, pauseX, pauseY + 3f * u, pauseR)
            circ(HUD_BUTTON, pauseX, pauseY, pauseR)
            rr(WHITE, pauseX - 6f * u, pauseY - 7f * u, 4f * u, 14f * u, 1.5f * u)
            rr(WHITE, pauseX + 2f * u, pauseY - 7f * u, 4f * u, 14f * u, 1.5f * u)
            val pw = 96f * u
            val px = pauseX - pauseR - 8f * u - pw
            rr(HUD_BG, px, top + 1f * u, pw, ph, ph / 2f)
            drawCoinIcon(px + ph / 2f, top + 1f * u + ph / 2f, ph * 0.46f)
            label(text.num(g.coins), px + pw * 0.6f, top + 1f * u + ph / 2f, 22f * u * (1f + g.stackBump * 0.18f), WHITE)
            // --- centre: wave pill and keep health
            val by = top + 62f * u
            val wpw = 112f * u
            rr(HUD_SHADOW, w / 2f - wpw / 2f, by + 2f * u, wpw, 24f * u, 12f * u)
            rr(WAVE_RED, w / 2f - wpw / 2f, by, wpw, 24f * u, 12f * u)
            label(text.str(waveLabel(g.wave).uppercase()), w / 2f, by + 12f * u, 14f * u, WHITE)
            val bw = min(w * 0.55f, 220f * u)
            val bx = w / 2f - bw / 2f
            val hy = by + 30f * u
            rr(HUD_BG, bx - 3f * u, hy - 3f * u, bw + 6f * u, 13f * u, 6.5f * u)
            val frac = (g.castleHp / g.castleMaxHp).coerceIn(0f, 1f)
            rr(if (frac > 0.35f) HP_GREEN else HP_RED, bx, hy, bw * frac, 7f * u, 3.5f * u)
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
        if (g.bossBanner > 0f) {
            val k = g.bossBanner / 2.2f
            val pop = 1f + sin(k * 20f) * 0.05f
            label(text.str("BOSS !"), w / 2f, h * 0.38f, 56f * u * pop, Color(0xFFFF4A3A), min(1f, k * 3f))
        }
        if (g.state == STATE_PLAY && g.kDead) {
            label(text.str("Le roi est tombé..."), w / 2f, h * 0.45f, 26f * u, WHITE)
        }
        if (g.state == STATE_PLAY && g.wave <= 1 && g.squad <= START_SQUAD) {
            label(text.str("Tue les chevaliers, ramasse l'or,"), w / 2f, h - 150f * u, 16f * u, WHITE)
            label(text.str("puis dépose-le sur les chantiers"), w / 2f, h - 128f * u, 16f * u, WHITE)
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

    /** Hit areas of the HUD buttons, in screen pixels (read by App's touch handler). */
    var pauseX = -1000f; var pauseY = -1000f; var pauseRadius = 0f
    var abilityX = -1000f; var abilityY = -1000f; var abilityRadius = 0f

    private fun DrawScope.drawAbilityButton(g: Game, w: Float, h: Float, u: Float) {
        val r = 34f * u
        abilityX = w - 18f * u - r; abilityY = h - 46f * u - r; abilityRadius = r + 10f * u
        val ready = g.abilityCd <= 0f
        if (ready) circ(GOLD, abilityX, abilityY, r + (5f + 3f * sin(g.time * 6f)) * u) // ready glow
        circ(HUD_SHADOW, abilityX, abilityY + 4f * u, r)
        circ(GOLD_DARK, abilityX, abilityY, r)
        circ(CLASS_COLORS[g.heroClass], abilityX, abilityY, r - 4f * u)
        drawAbilityIcon(g.heroClass, abilityX, abilityY, r * 0.55f, u)
        if (!ready) {
            val f = (g.abilityCd / ABILITY_COOLDOWN[g.heroClass]).coerceIn(0f, 1f)
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
