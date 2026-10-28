package fr.kingshot.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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

private val GRASS = Color(0xFF8DC24F)
private val GRASS_DARK = Color(0xFF7DB443)
private val GRASS_LIGHT = Color(0xFF9CCD5C)
private val DIRT = Color(0xFFDDB57F)
private val DIRT_EDGE = Color(0xFFCDA36C)
private val CLIFF_TOP = Color(0xFF86A83F)
private val CLIFF_TOP_LIGHT = Color(0xFF94B54A)
private val CLIFF_FACE = Color(0xFF5E5B57)
private val CLIFF_FACE_DARK = Color(0xFF4D4A47)
private val SHADOW = Color(0x33000000)
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
    private val outline = Stroke(width = 12f, join = StrokeJoin.Round)

    private val roadStrokeEdge = Stroke(width = 104f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val roadStroke = Stroke(width = 88f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val bowStroke = Stroke(width = 3f, cap = StrokeCap.Round)
    private val roads = ArrayList<Path>()
    private val pineDark = Path()
    private val pineLight = Path()
    private val rockDark = Path()
    private val rockLight = Path()
    private val roof = Path()
    private val roofSide = Path()
    private val crown = Path()
    private val coneRoof = Path()
    private val flag = Path()

    private val keys = LongArray(2048)
    private var scale = 1f

    var topInset = 0f

    init {
        for (p in World.paths) roads.add(polyPath(p.xs, p.ys))
        for (r in World.decoRoads) {
            val n = r.size / 2
            roads.add(polyPath(FloatArray(n) { r[it * 2] }, FloatArray(n) { r[it * 2 + 1] }))
        }
        tri(pineDark, -36f, -18f, 36f, -18f, 0f, -78f)
        tri(pineDark, -30f, -52f, 30f, -52f, 0f, -108f)
        tri(pineDark, -22f, -84f, 22f, -84f, 0f, -134f)
        tri(pineLight, -36f, -18f, 0f, -18f, 0f, -78f)
        tri(pineLight, -30f, -52f, 0f, -52f, 0f, -108f)
        tri(pineLight, -22f, -84f, 0f, -84f, 0f, -134f)
        rockDark.apply { moveTo(-22f, 0f); lineTo(-18f, -16f); lineTo(-4f, -26f); lineTo(16f, -20f); lineTo(24f, 0f); close() }
        rockLight.apply { moveTo(-18f, -16f); lineTo(-4f, -26f); lineTo(16f, -20f); lineTo(2f, -12f); close() }
        roof.apply { moveTo(-90f, -58f); lineTo(-62f, -132f); lineTo(62f, -132f); lineTo(90f, -58f); close() }
        roofSide.apply { moveTo(-90f, -58f); lineTo(-62f, -132f); lineTo(-40f, -132f); lineTo(-62f, -58f); close() }
        crown.apply {
            moveTo(-10f, 0f); lineTo(-11f, -13f); lineTo(-5f, -6f); lineTo(0f, -15f)
            lineTo(5f, -6f); lineTo(11f, -13f); lineTo(10f, 0f); close()
        }
        coneRoof.apply { moveTo(-26f, 0f); lineTo(0f, -46f); lineTo(26f, 0f); close() }
        flag.apply { moveTo(0f, 0f); lineTo(50f, 6f); lineTo(42f, 18f); lineTo(50f, 30f); lineTo(0f, 28f); close() }
    }

    private fun polyPath(xs: FloatArray, ys: FloatArray) = Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
    }

    private fun tri(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        p.moveTo(x0, y0); p.lineTo(x1, y1); p.lineTo(x2, y2); p.close()
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
            drawText(t, color = color, alpha = alpha)
        }
    }

    // ------------------------------------------------------------ frame

    fun draw(ds: DrawScope, g: Game) = with(ds) {
        val w = size.width
        val h = size.height
        val portrait = h >= w
        scale = if (portrait) w / 720f else min(w / 1100f, h / 700f)
        val halfW = w / 2f / scale
        val halfH = h / 2f / scale
        val cx = if (halfW * 2 >= World.W) World.W / 2 else g.camX.coerceIn(halfW, World.W - halfW)
        val cy = if (halfH * 2 >= World.H) World.H / 2 else g.camY.coerceIn(halfH - 200f, World.H - halfH)
        val shx = if (g.shake > 0f) sin(g.time * 93f) * g.shake else 0f
        val shy = if (g.shake > 0f) cos(g.time * 71f) * g.shake else 0f
        val ox = w / 2f - cx * scale + shx
        val oy = h / 2f - cy * scale + shy
        val vl = cx - halfW - 160f
        val vr = cx + halfW + 160f
        val vt = cy - halfH - 60f
        val vb = cy + halfH + 260f

        drawRect(GRASS)
        withTransform({
            translate(ox, oy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawGround(vl, vt, vr, vb)
            for (p in g.pads) if (p.visible) drawPad(p)
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
        PAD_TOWER_ARCHER -> "Archer sur la tour"
        else -> "Palissade"
    }

    private fun DrawScope.drawGround(vl: Float, vt: Float, vr: Float, vb: Float) {
        val gp = World.grassPatches
        var i = 0
        while (i < gp.size) {
            val x = gp[i]; val y = gp[i + 1]; val r = gp[i + 2]
            if (x + r > vl && x - r < vr && y + r > vt && y - r < vb) {
                oval(if (gp[i + 3] > 0.5f) GRASS_DARK else GRASS_LIGHT, x - r, y - r * 0.6f, r * 2f, r * 1.2f)
            }
            i += 4
        }
        for (r in roads) drawPath(r, DIRT_EDGE, style = roadStrokeEdge)
        for (r in roads) drawPath(r, DIRT, style = roadStroke)
        for (c in World.cliffs) {
            val x = c[0]; val y = c[1]; val cw = c[2]; val d = c[3]; val ht = c[4]
            if (x > vr || x + cw < vl || y - ht > vb || y + d < vt) continue
            oval(SHADOW, x - 10f, y + d - 30f, cw + 40f, 70f)
            rect(CLIFF_FACE, x, y + d - ht, cw, ht)
            rect(CLIFF_FACE_DARK, x + cw * 0.62f, y + d - ht, cw * 0.38f, ht)
            rr(CLIFF_TOP, x - 6f, y - ht, cw + 12f, d + 6f, 18f)
            rr(CLIFF_TOP_LIGHT, x + 10f, y - ht + 10f, cw * 0.5f, d * 0.5f, 30f)
        }
    }

    private fun DrawScope.drawPad(p: Pad) {
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
        drawCoinIcon(p.x - 22f, p.y - 4f, 13f)
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
                K_TREE -> drawTree(t[idx], t[idx + 1], t[idx + 2])
                K_ROCK -> drawRock(r[idx], r[idx + 1], r[idx + 2])
                K_HOUSE -> drawHouse(World.HOUSE_X, World.HOUSE_Y)
                K_CASTLE -> drawCastle(g)
                K_TOWER -> drawTower(g.towers[idx])
                K_WALL -> drawWall(g.walls[idx])
                K_ENEMY -> drawEnemy(g.enemies[idx])
                K_ARCHER -> drawArcher(g.ax[idx], g.ay[idx], g.aFaceRight[idx], g.aWalk[idx], true)
                K_KING -> drawKing(g)
                K_COIN -> drawGroundCoin(g.coinsOnGround[idx])
            }
        }
    }

    private fun DrawScope.drawTree(x: Float, y: Float, s: Float) {
        oval(SHADOW, x - 34f * s, y - 9f * s, 68f * s, 22f * s)
        rect(WOOD_DARK, x - 5f * s, y - 26f * s, 10f * s, 26f * s)
        translate(x, y) {
            scale(s, s, Offset.Zero) {
                drawPath(pineDark, Color(0xFF5E9E2E))
                drawPath(pineLight, Color(0xFF7DBE3C))
            }
        }
    }

    private fun DrawScope.drawRock(x: Float, y: Float, s: Float) {
        translate(x, y) {
            scale(s, s, Offset.Zero) {
                oval(SHADOW, -26f, -6f, 54f, 14f)
                drawPath(rockDark, Color(0xFF9A8E8C))
                drawPath(rockLight, Color(0xFFBDB2AF))
            }
        }
    }

    private fun DrawScope.drawHouse(x: Float, y: Float) {
        oval(SHADOW, x - 100f, y - 26f, 200f, 50f)
        translate(x, y) {
            rect(STONE, -72f, -70f, 144f, 70f)
            rect(STONE_DARK, -72f, -18f, 144f, 18f)
            rr(WOOD_DARK, -16f, -50f, 32f, 50f, 8f)
            rr(Color(0xFF3A3F4A), -56f, -48f, 22f, 20f, 4f)
            rr(Color(0xFF3A3F4A), 34f, -48f, 22f, 20f, 4f)
            drawPath(roof, ROOF)
            drawPath(roofSide, ROOF_DARK)
            rect(ROOF_DARK, -92f, -64f, 184f, 8f)
            line(Color(0xFF5C3A22), -62f, -132f, 62f, -132f, 6f)
            // bow emblem
            drawArc(BOW, 100f, 160f, false, Offset(-28f, -122f), Size(40f, 52f), style = Stroke(6f, cap = StrokeCap.Round))
            line(SILVER, -24f, -78f, 30f, -120f, 4f)
            line(WHITE, 30f, -120f, 20f, -118f, 4f)
            line(WHITE, 30f, -120f, 28f, -110f, 4f)
        }
    }

    private fun DrawScope.drawCastle(g: Game) {
        val x = World.CASTLE_X
        val y = World.CASTLE_Y
        val flash = g.castleFlash > 0f
        val st = if (flash) Color(0xFFFFE0E0) else STONE
        val sd = if (flash) Color(0xFFFFB0B0) else STONE_DARK
        oval(SHADOW, x - 130f, y - 30f, 260f, 64f)
        // side towers
        rect(sd, x - 128f, y - 150f, 46f, 150f)
        rect(sd, x + 82f, y - 150f, 46f, 150f)
        translate(x - 105f, y - 150f) { drawPath(coneRoof, ROYAL) }
        translate(x + 105f, y - 150f) { drawPath(coneRoof, ROYAL_DARK) }
        // keep
        rect(st, x - 92f, y - 180f, 184f, 180f)
        rect(sd, x - 92f, y - 34f, 184f, 34f)
        rect(sd, x + 40f, y - 180f, 52f, 180f)
        for (k in 0 until 5) rect(st, x - 92f + k * 39.5f, y - 200f, 26f, 22f)
        line(sd, x - 92f, y - 120f, x + 40f, y - 120f, 3f)
        line(sd, x - 92f, y - 80f, x + 40f, y - 80f, 3f)
        rr(WOOD_DARK, x - 28f, y - 78f, 56f, 78f, 26f)
        line(Color(0xFF4A2C17), x, y - 74f, x, y, 3f)
        // flag
        line(Color(0xFF5C3A22), x, y - 200f, x, y - 290f, 5f)
        val wave = sin(g.time * 5f) * 4f
        translate(x + 2f, y - 288f + wave * 0.3f) {
            scale(1f + wave * 0.02f, 1f, Offset.Zero) { drawPath(flag, ROYAL) }
            translate(10f, 8f) { drawPath(crown, GOLD) }
        }
    }

    private fun DrawScope.drawTower(t: Tower) {
        val x = t.x
        val y = t.y
        val b = 1f + t.bump * 0.15f
        oval(SHADOW, x - 56f, y - 16f, 112f, 34f)
        scale(b, 1f / b, Offset(x, y)) {
            rect(WOOD_DARK, x - 34f, y - 112f, 9f, 104f)
            rect(WOOD_DARK, x + 25f, y - 112f, 9f, 104f)
            line(WOOD_DARK, x - 36f, y - 30f, x + 36f, y - 90f, 6f)
            line(WOOD_DARK, x + 36f, y - 30f, x - 36f, y - 90f, 6f)
            rect(WOOD, x - 42f, y - 100f, 10f, 100f)
            rect(WOOD, x + 32f, y - 100f, 10f, 100f)
            rr(WOOD, x - 50f, y - 132f, 100f, 34f, 5f)
            rect(WOOD_DARK, x - 50f, y - 100f, 100f, 12f)
            line(WOOD_DARK, x - 50f, y - 121f, x + 50f, y - 121f, 2f)
            line(WOOD_DARK, x - 50f, y - 110f, x + 50f, y - 110f, 2f)
            for (a in 0 until t.archers) {
                drawArcher(x + (a - 1) * 26f, y - 108f - (a % 2) * 6f, t.faceRight[a], 0f, false)
            }
        }
    }

    private fun DrawScope.drawWall(w: Wall) {
        val flash = w.hitFlash > 0f
        val c = if (flash) Color(0xFFFFD0C0) else WOOD_STAKE
        val cd = if (flash) Color(0xFFE0A090) else WOOD_DARK
        val b = 1f + w.bump * 0.2f
        val stakes = 9
        val reverse = w.ny < 0f
        for (k0 in 0 until stakes) {
            val k = if (reverse) stakes - 1 - k0 else k0
            val tt = (k - (stakes - 1) / 2f) * 21f
            val sx = w.x + w.nx * tt
            val sy = w.y + w.ny * tt
            val hgt = (44f + ((k * 7) % 3) * 6f) * b
            oval(SHADOW, sx - 10f, sy - 4f, 20f, 8f)
            rr(c, sx - 7f, sy - hgt, 14f, hgt, 6f)
            rect(cd, sx + 2f, sy - hgt + 6f, 5f, hgt - 6f)
        }
        if (w.hp < w.maxHp) hpBar(w.x, w.y - 74f, 70f, w.hp / w.maxHp, HP_GREEN)
    }

    private fun DrawScope.hpBar(cx: Float, y: Float, width: Float, frac: Float, col: Color) {
        rr(HP_BG, cx - width / 2f - 2f, y - 2f, width + 4f, 10f, 4f)
        rr(col, cx - width / 2f, y, width * frac.coerceIn(0f, 1f), 6f, 3f)
    }

    private fun DrawScope.drawArcher(x: Float, y: Float, faceRight: Boolean, walk: Float, shadow: Boolean) {
        val f = if (faceRight) 1f else -1f
        val la = sin(walk) * 3f
        if (shadow) oval(SHADOW, x - 13f, y - 4f, 26f, 9f)
        rr(LEG, x - 7f, y - 11f - max(0f, la), 5f, 11f, 2f)
        rr(LEG, x + 2f, y - 11f - max(0f, -la), 5f, 11f, 2f)
        rr(ROYAL, x - f * 11f - 4f, y - 32f, 8f, 20f, 3f)
        rr(SHIRT, x - 10f, y - 31f, 20f, 22f, 6f)
        rect(WOOD_DARK, x - 10f, y - 17f, 20f, 3f)
        circ(SKIN, x, y - 40f, 11f)
        drawArc(HAIR, 180f, 180f, true, Offset(x - 11.5f, y - 52f), Size(23f, 23f))
        rect(HAIR, x + f * 3f - 1.5f, y - 40f, 3f, 4f)
        val bx = x + f * 12f
        drawArc(BOW, if (faceRight) -90f else 90f, 180f, false, Offset(bx - 6f, y - 41f), Size(12f, 30f), style = bowStroke)
        line(WHITE, bx, y - 40f, bx, y - 12f, 1f)
    }

    private fun DrawScope.drawKing(g: Game) {
        val x = g.kx
        val y = g.ky
        val f = if (g.kFaceRight) 1f else -1f
        val moving = abs(g.kvx) + abs(g.kvy) > 20f
        val gait = if (moving) sin(g.kWalk) * 4f else 0f
        val bob = if (moving) abs(sin(g.kWalk)) * 2f else 0f
        oval(SHADOW, x - 32f, y - 8f, 64f, 18f)
        // horse legs
        rr(HORSE_DARK, x - 20f, y - 18f - max(0f, gait), 6f, 18f, 2f)
        rr(HORSE_DARK, x - 10f, y - 18f - max(0f, -gait), 6f, 18f, 2f)
        rr(HORSE_DARK, x + 6f, y - 18f - max(0f, gait), 6f, 18f, 2f)
        rr(HORSE_DARK, x + 15f, y - 18f - max(0f, -gait), 6f, 18f, 2f)
        val b = y - bob
        rr(HORSE_DARK, x - f * 30f - 3f, b - 36f, 7f, 18f, 3f)
        rr(HORSE, x - 27f, b - 38f, 54f, 22f, 10f)
        rr(WHITE, x - 10f, b - 26f, 22f, 9f, 4f)
        rr(HORSE, x + f * 20f - 7f, b - 56f, 15f, 26f, 6f)
        rr(HORSE_DARK, x + f * 26f - 5f, b - 52f, 11f, 9f, 3f)
        rr(HORSE_DARK, x + f * 14f - 3f, b - 56f, 6f, 16f, 3f)
        // rider
        val rb = b - 30f
        rr(ROYAL_DARK, x - f * 10f - 5f, rb - 32f, 10f, 34f, 4f)
        rr(ROYAL, x - 10f, rb - 32f, 20f, 26f, 7f)
        rr(SILVER, x - 7f, rb - 29f, 14f, 12f, 4f)
        circ(SKIN, x, rb - 41f, 10f)
        rect(HAIR, x + f * 3f - 1.5f, rb - 42f, 3f, 4f)
        translate(x, rb - 47f) { drawPath(crown, GOLD) }
        rect(GOLD_DARK, x - 10f, rb - 50f, 20f, 3f)
        // coin stack
        val n = min(g.coins, 70)
        val base = y - 86f - bob + g.stackBump * 3f
        for (i in 0 until n) {
            val cy = base - i * 4.5f
            val cx = x + g.sway * i * i * 0.016f
            oval(GOLD_DARK, cx - 11f, cy - 3f, 22f, 9f)
            oval(GOLD, cx - 11f, cy - 5f, 22f, 8f)
        }
        if (g.kHp < g.kMaxHp) hpBar(x, y - 100f - n * 4.5f, 50f, g.kHp / g.kMaxHp, HP_GREEN)
    }

    private fun DrawScope.drawEnemy(e: Enemy) {
        val s = e.scale
        val x = e.x
        val y = e.y
        val f = if (e.faceRight) 1f else -1f
        val flash = e.hitFlash > 0f
        val boss = e.kind == 2
        val body = if (flash) WHITE else if (boss) BOSS else RED
        val light = if (flash) WHITE else RED_LIGHT
        val dark = if (flash) Color(0xFFFFC0C0) else RED_DARK
        val la = sin(e.walk) * 3f * s
        oval(SHADOW, x - 15f * s, y - 5f * s, 30f * s, 10f * s)
        rr(Color(0xFF4A4A52), x - 8f * s, y - 11f * s - max(0f, la), 6f * s, 11f * s, 2f * s)
        rr(Color(0xFF4A4A52), x + 2f * s, y - 11f * s - max(0f, -la), 6f * s, 11f * s, 2f * s)
        rr(body, x - 12f * s, y - 33f * s, 24f * s, 24f * s, 6f * s)
        rr(dark, x + (if (f > 0) 4f else -12f) * s, y - 33f * s, 8f * s, 24f * s, 4f * s)
        rr(light, x - 12f * s, y - 54f * s, 24f * s, 23f * s, 8f * s)
        rect(BLACK, x - 7f * s + f * 3f * s, y - 46f * s, 12f * s, 2.5f * s)
        rect(BLACK, x - 5f * s + f * 3f * s, y - 41f * s, 9f * s, 2f * s)
        // plume
        rr(body, x - 3f * s, y - 64f * s, 6f * s, 14f * s, 3f * s)
        rr(body, x - 3f * s - f * 9f * s - (if (f > 0) 0f else -3f * s), y - 64f * s, 12f * s, 5f * s, 3f * s)
        if (boss) {
            rect(GOLD, x - 12f * s, y - 35f * s, 24f * s, 3f * s)
            rr(dark, x - 17f * s, y - 33f * s, 8f * s, 9f * s, 3f * s)
            rr(dark, x + 9f * s, y - 33f * s, 8f * s, 9f * s, 3f * s)
        }
        // sword
        val hx = x + f * 12f * s
        val hy = y - 22f * s
        val ang = if (f > 0) 0.35f - e.swing * 2.2f else PI.toFloat() - 0.35f + e.swing * 2.2f
        val len = 24f * s
        line(SILVER, hx, hy, hx + cos(ang) * len, hy + sin(ang) * len, 3.5f * s)
        if (boss || e.hp < e.maxHp) {
            val bw = if (boss) 110f else 36f * s
            hpBar(x, y - 74f * s - (if (boss) 6f else 0f), bw, e.hp / e.maxHp, HP_RED)
        }
    }

    private fun DrawScope.drawGroundCoin(c: Coin) {
        val wdt = 6f + 16f * abs(cos(c.spin))
        if (c.z < 60f) oval(SHADOW, c.x - 10f, c.y - 3f, 20f, 7f)
        val y = c.y - c.z - 10f
        oval(GOLD_DARK, c.x - wdt / 2f, y - 11f, wdt, 22f)
        oval(GOLD, c.x - wdt / 2f + 1.5f, y - 11f, wdt - 3f, 20f)
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
        for (f in g.flyCoins) {
            if (!f.active || f.pad >= g.pads.size) continue
            val p = g.pads[f.pad]
            val t = f.t
            val x = f.sx + (p.x - f.sx) * t
            val y = f.sy + (p.y - f.sy) * t
            val z = f.sz * (1f - t) + sin(t * PI.toFloat()) * 60f
            oval(GOLD_DARK, x - 11f, y - z - 3f, 22f, 9f)
            oval(GOLD, x - 11f, y - z - 5f, 22f, 8f)
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
        if (g.state != STATE_MENU) {
            // coins
            val pw = 110f * u
            val ph = 36f * u
            val px = w - pw - 10f * u
            rr(HUD_BG, px, top, pw, ph, ph / 2f)
            drawCoinIcon(px + ph / 2f, top + ph / 2f, ph * 0.46f)
            label(text.num(g.coins), px + pw * 0.62f, top + ph / 2f, 26f * u, WHITE)
            // wave
            rr(HUD_BG, 10f * u, top, 120f * u, ph, ph / 2f)
            label(text.str(waveLabel(g.wave)), 70f * u, top + ph / 2f, 18f * u, WHITE, outlined = false)
            // castle hp
            val bw = min(w * 0.5f, 260f * u)
            val bx = w / 2f - bw / 2f
            val by = top + ph + 8f * u
            rr(HUD_BG, bx - 3f * u, by - 3f * u, bw + 6f * u, 14f * u, 7f * u)
            val frac = (g.castleHp / g.castleMaxHp).coerceIn(0f, 1f)
            rr(if (frac > 0.35f) HP_GREEN else HP_RED, bx, by, bw * frac, 8f * u, 4f * u)
            label(text.str("Donjon"), w / 2f, by + 22f * u, 12f * u, WHITE)
            // wave countdown
            if (g.toSpawn == 0 && !g.bossPending && g.waveBreak < 7f && g.waveBreak > 0f && g.enemies.none { it.active }) {
                label(text.str("Prochaine vague dans"), w / 2f, by + 46f * u, 14f * u, WHITE)
                label(text.num(g.waveBreak.toInt() + 1), w / 2f, by + 70f * u, 24f * u, GOLD)
            }
        }
        // banners
        if (g.waveBanner > 0f) {
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
        if (g.state == STATE_PLAY && g.wave <= 1 && g.squad == 0) {
            label(text.str("Tue les chevaliers, ramasse l'or,"), w / 2f, h - 86f * u, 16f * u, WHITE)
            label(text.str("puis dépose-le sur les chantiers"), w / 2f, h - 64f * u, 16f * u, WHITE)
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

    private val waveLabels = HashMap<Int, String>()
    private fun waveLabel(n: Int) = waveLabels.getOrPut(n) { "Vague ${max(n, 1)}" }
    private val fpsLabels = HashMap<Int, String>()
    private fun fpsLabel(n: Int) = fpsLabels.getOrPut(n) { "$n fps" }
}
