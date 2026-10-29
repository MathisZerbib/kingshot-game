package fr.kingshot.game

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.abs

const val SHOP_SKIN = 0
const val SHOP_BANNER = 1
const val SHOP_MOUNT = 2
const val SHOP_TRAIL = 3

// achievements that gate an item (it stays visible, locked, with its condition)
const val UNLOCK_NONE = 0
const val UNLOCK_LEVEL10 = 1
const val UNLOCK_BOSS1 = 2
const val UNLOCK_WAVE50 = 3
const val UNLOCK_CHAPTER3STARS = 4

/** [cls] is the hero class for a skin, -1 otherwise; [look] indexes the kind's look table (skin tint, banner colour...). */
class ShopItem(val id: String, val kind: Int, val cls: Int, val name: String, val desc: String, val price: Int, val unlock: Int, val look: Int)

/** Cosmetic shop: bought with the treasure only (no real money, no gems). Equipped looks are cached in memory for the renderer. */
object Shop {
    private const val KEY = "shop1_" // versioned save keys

    val items: List<ShopItem> = buildList {
        val skins = arrayOf(
            Triple("gold", "Or", "Armure d'apparat dorée"),
            Triple("frost", "Givre", "Acier bleu glacé"),
            Triple("shadow", "Ombre", "Plates noires, reflets violets"),
        )
        val skinPrice = intArrayOf(1500, 3000, 6000)
        val skinUnlock = intArrayOf(UNLOCK_NONE, UNLOCK_LEVEL10, UNLOCK_CHAPTER3STARS)
        val prefix = arrayOf("warrior", "ranger", "builder")
        for (c in 0..2) for (k in 0..2) {
            add(ShopItem("${prefix[c]}_${skins[k].first}", SHOP_SKIN, c, "${CLASS_NAMES[c]} ${skins[k].second}", skins[k].third, skinPrice[k], skinUnlock[k], k + 1))
        }
        val banners = arrayOf("Bleu royal", "Pourpre", "Émeraude", "Noir et or", "Blanc neige")
        val bannerUnlock = intArrayOf(UNLOCK_NONE, UNLOCK_NONE, UNLOCK_NONE, UNLOCK_BOSS1, UNLOCK_NONE)
        val bannerId = arrayOf("royal", "purple", "emerald", "blackgold", "snow")
        for (k in banners.indices) add(ShopItem("banner_${bannerId[k]}", SHOP_BANNER, -1, banners[k], "Étendards, toits et bannières", if (k == 0) 0 else 800, bannerUnlock[k], k))
        val mounts = arrayOf("Blanc", "Bai", "Noir", "Gris pommelé", "Palomino")
        val mountId = arrayOf("white", "bay", "black", "dapple", "palomino")
        for (k in mounts.indices) add(ShopItem("mount_${mountId[k]}", SHOP_MOUNT, -1, mounts[k], "Robe du cheval royal", if (k == 0) 0 else 1200, if (k == 4) UNLOCK_LEVEL10 else UNLOCK_NONE, k))
        val trails = arrayOf("Or", "Étincelles bleues", "Feuilles", "Flammes")
        val trailId = arrayOf("gold", "sparks", "leaves", "flames")
        for (k in trails.indices) add(ShopItem("trail_${trailId[k]}", SHOP_TRAIL, -1, trails[k], "Traînée des pièces", if (k == 0) 0 else 600, if (k == 3) UNLOCK_WAVE50 else UNLOCK_NONE, k))
    }

    /** Bumped on every purchase or equip, so Compose can re-read the shop. */
    val version = mutableIntStateOf(0)

    fun items(kind: Int, cls: Int = -1) = items.filter { it.kind == kind && (cls < 0 || it.cls == cls) }
    fun item(id: String) = items.firstOrNull { it.id == id }

    fun owned(id: String): Boolean { val it = item(id) ?: return false; return it.price == 0 || Storage.loadInt("${KEY}own_$id") == 1 }
    fun unlocked(id: String): Boolean = item(id)?.let { met(it.unlock) } ?: false
    fun canBuy(id: String): Boolean { val it = item(id) ?: return false; return !owned(id) && met(it.unlock) && Meta.treasure >= it.price }

    fun unlockText(cond: Int) = when (cond) {
        UNLOCK_LEVEL10 -> "Atteindre le niveau 10"
        UNLOCK_BOSS1 -> "Vaincre un boss"
        UNLOCK_WAVE50 -> "Atteindre la vague 50"
        UNLOCK_CHAPTER3STARS -> "3 étoiles sur un chapitre entier"
        else -> ""
    }

    private fun met(cond: Int) = when (cond) {
        UNLOCK_LEVEL10 -> Stats.bestLevel >= 10
        UNLOCK_BOSS1 -> Stats.bosses >= 1
        UNLOCK_WAVE50 -> Storage.loadBest() >= 50
        UNLOCK_CHAPTER3STARS -> (0..3).any { ch -> (1..5).all { Storage.loadInt("stars_${ch * 5 + it}") >= 3 } }
        else -> true
    }

    /** Pays from the treasure, owns and equips in one go; false (nothing changed) if it cannot. */
    fun buy(id: String): Boolean {
        val it = item(id) ?: return false
        if (!canBuy(id)) return false
        Meta.treasure -= it.price
        Storage.saveInt("${KEY}own_$id", 1)
        equip(id)
        Audio.play(Sfx.FORGE)
        return true
    }

    fun equip(id: String): Boolean {
        val it = item(id) ?: return false
        if (!owned(id)) return false
        Storage.saveInt(slotKey(it.kind, it.cls), items.indexOf(it) + 1)
        load()
        version.intValue++
        return true
    }

    fun equipped(kind: Int, cls: Int = -1): ShopItem? = items.getOrNull(Storage.loadInt(slotKey(kind, cls)) - 1)

    private fun slotKey(kind: Int, cls: Int) = if (kind == SHOP_SKIN) "${KEY}eq_${kind}_$cls" else "${KEY}eq_$kind"

    // ---------------------------------------------------------------- looks, read by the renderer every frame (no Storage there)

    private val skinLook = IntArray(3) // per class: 0 default, 1 gold, 2 frost, 3 shadow
    var bannerLook = 0; private set
    var mountLook = 0; private set
    var trailColor = TRAIL_COLORS[0]; private set
    private var loaded = false

    fun load() {
        for (c in 0..2) skinLook[c] = equipped(SHOP_SKIN, c)?.look ?: 0
        bannerLook = equipped(SHOP_BANNER)?.look ?: 0
        mountLook = equipped(SHOP_MOUNT)?.look ?: 0
        trailColor = TRAIL_COLORS[equipped(SHOP_TRAIL)?.look ?: 0]
        loaded = true
    }

    /** Provisional skin: a colour grade of the class sprite until the recoloured atlases exist. */
    fun kingFilter(cls: Int): ColorFilter? { if (!loaded) load(); return SKIN_FILTERS[skinLook[cls]] }
}

private val TRAIL_COLORS = arrayOf(Color(0xFFFFD84A), Color(0xFF6FD3FF), Color(0xFF7BD05A), Color(0xFFFF7A2A))

private fun grade(r: Float, g: Float, b: Float, add: Float = 0f) = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    r, 0f, 0f, 0f, add, 0f, g, 0f, 0f, add, 0f, 0f, b, 0f, add, 0f, 0f, 0f, 1f, 0f,
)))
private val SKIN_FILTERS = arrayOf(null, grade(1.25f, 1.05f, 0.55f, 12f), grade(0.78f, 0.98f, 1.3f, 10f), grade(0.5f, 0.42f, 0.62f))

/** Banner colour: blue pixels (roofs, banners) of the buildings take the bought colour; everything else is untouched. */
fun recolorBanner(src: ImageBitmap, look: Int): ImageBitmap {
    if (look == 0) return src
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.readPixels(px)
    val hsv = FloatArray(3)
    for (i in px.indices) {
        val c = px[i]
        val a = c ushr 24
        if (a == 0) continue
        rgbToHsv(c, hsv)
        if (hsv[0] < 190f || hsv[0] > 255f || hsv[1] < 0.28f) continue
        when (look) {
            1 -> hsv[0] = 285f + (hsv[0] - 222f) * 0.5f // purple
            2 -> hsv[0] = 145f + (hsv[0] - 222f) * 0.5f // emerald
            3 -> { hsv[1] *= 0.25f; hsv[2] *= 0.32f } // black (gold trims are not blue, they stay)
            4 -> { hsv[1] *= 0.08f; hsv[2] = 0.55f + hsv[2] * 0.45f } // snow white
        }
        px[i] = (a shl 24) or hsvToRgb(hsv)
    }
    return imageFromPixels(w, h, px)
}

private fun rgbToHsv(c: Int, out: FloatArray) {
    val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
    out[0] = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    out[1] = if (mx == 0f) 0f else d / mx
    out[2] = mx
}

private fun hsvToRgb(hsv: FloatArray): Int {
    val h = (hsv[0] % 360f + 360f) % 360f; val s = hsv[1]; val v = hsv[2]
    val c = v * s; val x = c * (1f - abs((h / 60f) % 2f - 1f)); val m = v - c
    val (r, g, b) = when ((h / 60f).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
    return (((r + m) * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or (((g + m) * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or ((b + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/** Platform bitmap from ARGB pixels. */
expect fun imageFromPixels(w: Int, h: Int, argb: IntArray): ImageBitmap

/** The buildings' atlases in the equipped banner colour (call off the main thread; small images, ~10 ms each). */
fun withBanner(sp: Sprites, look: Int): Sprites = if (look == 0) sp else sp.copy(
    castle = recolorBanner(sp.castle, look), castleRoyal = recolorBanner(sp.castleRoyal, look),
    tower = recolorBanner(sp.tower, look), towerStone = recolorBanner(sp.towerStone, look), towerRoyal = recolorBanner(sp.towerRoyal, look),
    wallStone = recolorBanner(sp.wallStone, look), wallStoneV = recolorBanner(sp.wallStoneV, look),
    gateClosed = recolorBanner(sp.gateClosed, look), gateOpen = recolorBanner(sp.gateOpen, look),
    gateClosedV = recolorBanner(sp.gateClosedV, look), gateOpenV = recolorBanner(sp.gateOpenV, look),
    gateWoodClosed = recolorBanner(sp.gateWoodClosed, look), gateWoodOpen = recolorBanner(sp.gateWoodOpen, look),
    gateWoodClosedV = recolorBanner(sp.gateWoodClosedV, look), gateWoodOpenV = recolorBanner(sp.gateWoodOpenV, look),
)
