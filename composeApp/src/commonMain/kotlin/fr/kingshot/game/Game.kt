package fr.kingshot.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val STATE_MENU = 0
const val STATE_PLAY = 1
const val STATE_OVER = 2

const val PAD_RECRUIT = 0
const val PAD_FORGE = 1
const val PAD_REPAIR = 2
const val PAD_TOWER = 3
const val PAD_TOWER_ARCHER = 4
const val START_SQUAD = 2
const val TRANSITION_TIME = 4.2f
const val PAD_WALL = 5

const val MAX_SQUAD = 30

// Hero classes, chosen on the menu
const val CLASS_WARRIOR = 0 // melee cleave, tough; ability: whirlwind (damage + knockback around the king)
const val CLASS_RANGER = 1 // fast long-range shots, fragile; ability: arrow rain on every enemy in range
const val CLASS_BUILDER = 2 // weak shots, builds 30 % cheaper, bigger magnet; ability: repair all walls and the keep
val CLASS_NAMES = arrayOf("Guerrier", "Rôdeur", "Bâtisseur")
val ABILITY_NAMES = arrayOf("Tourbillon", "Pluie de flèches", "Réparation")
val ABILITY_COOLDOWN = floatArrayOf(10f, 12f, 20f)

// Permanent hero upgrades bought from the treasure (coins kept between games): the meta loop
const val UP_HP = 0 // +10 % health per level
const val UP_POWER = 1 // +8 % king damage per level
const val UP_ABILITY = 2 // -6 % ability cooldown per level
const val UP_MAX = 10
val UP_NAMES = arrayOf("Vie", "Force", "Capacité")

/** Treasure (coins kept from past games) and hero upgrades, kept across games. */
object Meta {
    var treasure: Int
        get() = Storage.loadInt("treasure")
        set(v) = Storage.saveInt("treasure", v)
    fun level(cls: Int, up: Int) = Storage.loadInt("up_${cls}_$up")
    fun cost(cls: Int, up: Int) = 20 + 15 * level(cls, up)
    fun buy(cls: Int, up: Int): Boolean {
        val lv = level(cls, up)
        val c = cost(cls, up)
        if (lv >= UP_MAX || treasure < c) return false
        treasure -= c
        Storage.saveInt("up_${cls}_$up", lv + 1)
        return true
    }
}

/** Coins drawn on the king's head (tall enough to leave the screen); the HUD counter shows the real total. */
const val MAX_STACK = 360 // ~1.3 screen heights of coins; more would not be visible

/** Sprite direction (0 = facing the camera/south, 2 = east, 4 = north, 6 = west) for a vector. */
fun dirIndex(dx: Float, dy: Float): Int {
    val a = atan2(dx, dy)
    return ((round(a / (PI.toFloat() / 4f)).toInt() % 8) + 8) % 8
}
const val TOWER_SLOTS = 3

class Enemy {
    var active = false
    var gen = 0
    var x = 0f
    var y = 0f
    var path = 0
    var s = 0f
    var offX = 0f
    var offY = 0f
    var hp = 0f
    var maxHp = 0f
    var speed = 0f
    var dmg = 0f
    var radius = 16f
    var scale = 1f
    var kind = 0 // see KIND_* (2 = boss, its look/behaviour in bossType)
    var bossType = 0 // 0 giant knight (wood age), 1 ogre (stone: hurls the king), 2 dark lord (royal: summons soldiers)
    var special = 0f // per-kind timer: archer reload, lord summon, wolf leap
    var mdx = 0f // last movement direction (shield bearers block arrows from the front)
    var mdy = 0f
    var atkCd = 0f
    var swing = 0f
    var hitFlash = 0f
    var walk = 0f
    var faceRight = false
    var dir = 0
    var coins = 1
}

const val KIND_SOLDIER = 0
const val KIND_BRUTE = 1
const val KIND_BOSS = 2
const val KIND_WOLF = 3 // fast, weak, leaps at the king
const val KIND_ARCHER = 4 // stops at range and shoots the king, walls and keep
const val KIND_SHIELD = 5 // slow, tough, blocks arrows from the front
const val KIND_RAM = 6 // goes for walls and gates, ignores the king, escorted

/** Enemy archer's arrow, flying to a fixed spot; [target] 1 king, 2 wall [ref], 3 keep. */
class EnemyShot {
    var active = false
    var x = 0f
    var y = 0f
    var tx = 0f
    var ty = 0f
    var target = 0
    var ref = 0
    var dmg = 0f
}

class Arrow {
    var active = false
    var x = 0f
    var y = 0f
    var z = 0f
    var dx = 1f
    var dy = 0f
    var dz = 0f
    var target = -1
    var targetGen = 0
    var tx = 0f
    var ty = 0f
    var tz = 0f
    var dmg = 0f
    var life = 0f
}

class Coin {
    var active = false
    var x = 0f
    var y = 0f
    var z = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var state = 0 // 0 popping, 1 resting, 2 flying to king
    var t = 0f
    var spin = 0f
}

/** Coin flying from the king's stack to a build pad. */
class FlyCoin {
    var active = false
    var sx = 0f
    var sy = 0f
    var sz = 0f
    var pad = 0
    var t = 0f
}

class Particle {
    var active = false
    var x = 0f
    var y = 0f
    var z = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var life = 0f
    var maxLife = 1f
    var size = 4f
    var color = 0L
    var gravity = 0f
}

class FloatText {
    var active = false
    var x = 0f
    var y = 0f
    var z = 0f
    var value = 0
    var life = 0f
    var big = false
    var kind = 0 // 0 normal hit (white), 1 critical (yellow), 2 damage taken by the king (red)
}

/** A fallen enemy: tips over, sinks and fades, then disappears. */
class Corpse {
    var active = false
    var x = 0f
    var y = 0f
    var kind = 0
    var dir = 0
    var scale = 1f
    var t = 0f // 0 .. CORPSE_TIME
}
const val CORPSE_TIME = 0.6f

class Pad(val kind: Int, val x: Float, val y: Float, val ref: Int = -1) {
    var cost = 0
    var paid = 0
    var pending = 0
    var visible = true
    var level = 0
    var bump = 0f
    companion object {
        const val HALF = 55f
    }
}

class Tower(val x: Float, val y: Float) {
    var built = false
    var archers = 0
    val cd = FloatArray(TOWER_SLOTS)
    val faceRight = BooleanArray(TOWER_SLOTS)
    val dir = IntArray(TOWER_SLOTS)
    val shoot = FloatArray(TOWER_SLOTS)
    var bump = 0f
}

class Wall(val path: Int, val s: Float) {
    var x = 0f
    var y = 0f
    var nx = 0f // perpendicular direction
    var ny = 0f
    var built = false
    var hp = 0f
    var maxHp = 300f
    var hitFlash = 0f
    var bump = 0f
    var gate = 0f // 0 closed .. 1 open (ramparts, from the stone age)
    // rampart line snapped to the sprite axes (east-west or north-south), since the stone sprites are axis-aligned
    var rx = 0f
    var ry = 0f
}

/** Half length of a stone rampart across its road, and half width of its gate. */
const val RAMPART_HALF = 135f // gate (90) + one 90-unit stone segment each side
const val PALISADE_HALF = 85f
const val GATE_HALF = 40f
const val RING_MAX = 640 // points of the wall ring linking the palisades/ramparts
const val RING_STEP = 20f

class Input {
    var up = false
    var down = false
    var left = false
    var right = false
    var touching = false
    var ox = 0f
    var oy = 0f
    var cx = 0f
    var cy = 0f
    var radiusPx = 120f
    var fixedBase = false

    /** [fixed]: keep the base where the finger landed (touches in the upper screen, so it never drifts over the king). */
    fun touchStart(x: Float, y: Float, fixed: Boolean = false) {
        touching = true; ox = x; oy = y; cx = x; cy = y; fixedBase = fixed
    }

    fun touchMove(x: Float, y: Float) {
        cx = x; cy = y
        if (fixedBase) return
        // drag the joystick base along when the finger goes too far
        val dx = cx - ox
        val dy = cy - oy
        val d = hypot(dx, dy)
        if (d > radiusPx) {
            ox = cx - dx / d * radiusPx
            oy = cy - dy / d * radiusPx
        }
    }

    fun touchEnd() {
        touching = false
    }
}

class Game {
    val rnd = Random(Random.nextLong())
    val input = Input()

    var state = STATE_MENU
    var time = 0f

    // King
    var kx = World.KING_START_X
    var ky = World.KING_START_Y
    var kvx = 0f
    var kvy = 0f
    var kFaceRight = true
    var kDir = 0
    var kHp = 150f
    var kMaxHp = 150f
    var heroClass = CLASS_WARRIOR
    private var heroPower = 1f // king damage from upgrades
    var abilityTime = 10f // cooldown of the ability after upgrades
    var treasureWon = 0 // coins sent to the treasure by the last game (game-over panel)
    var abilityCd = 0f // seconds until the ability is ready
    var abilityFlash = 0f // > 0 right after use (visual)
    var paused = false

    // Enclosure: arcs around the castle linking each built wall to the next one (by angle), as points RING_STEP apart.
    // ringRun[i] = false where point i starts a new arc (no segment from i-1 to i).
    val ringX = FloatArray(RING_MAX)
    val ringY = FloatArray(RING_MAX)
    val ringRun = BooleanArray(RING_MAX)
    var ringCount = 0
    private var ringDirty = true
    private val ringOrder = IntArray(16)
    private val ringAngle = FloatArray(16)

    // Level transition (ad-style): celebrate, zoom out over the grown map revealing the new road, zoom back
    var transition = 0f // seconds left, TRANSITION_TIME .. 0
    var camZoom = 1f
    var revealRoad = -1 // index of the road being revealed during the transition, -1 none
    var kDead = false
    var kRespawn = 0f
    var kHurtTimer = 0f
    var kFireCd = 0f
    var kWalk = 0f
    var coins = 0
    var stackBump = 0f
    var sway = 0f

    // Squad
    var squad = 0
    val ax = FloatArray(MAX_SQUAD)
    val ay = FloatArray(MAX_SQUAD)
    val acd = FloatArray(MAX_SQUAD)
    val aFaceRight = BooleanArray(MAX_SQUAD)
    val aWalk = FloatArray(MAX_SQUAD)
    val aDir = IntArray(MAX_SQUAD)
    val aShoot = FloatArray(MAX_SQUAD)
    var aMoving = BooleanArray(MAX_SQUAD)
    private val slotX = FloatArray(MAX_SQUAD)
    private val slotY = FloatArray(MAX_SQUAD)

    // Base
    var castleHp = 600f
    var castleMaxHp = 600f
    var castleFlash = 0f
    var castleAlarm = 0f // seconds left on the HUD "castle under attack" warning
    var dmgLevel = 0
    val dmgMult get() = 1f + dmgLevel * 0.3f

    // Pools
    val enemies = Array(160) { Enemy() }
    val arrows = Array(260) { Arrow() }
    val coinsOnGround = Array(400) { Coin() }
    val flyCoins = Array(80) { FlyCoin() }
    val particles = Array(700) { Particle() }
    val texts = Array(90) { FloatText() }
    val corpses = Array(60) { Corpse() }
    val enemyShots = Array(40) { EnemyShot() }
    private var slowMo = 0f // real seconds of 0.4x time left

    var towers = emptyArray<Tower>(); private set
    var walls = emptyArray<Wall>(); private set
    val pads = ArrayList<Pad>()
    lateinit var recruitPad: Pad
    lateinit var forgePad: Pad
    lateinit var repairPad: Pad

    // Waves
    var wave = 0
    var waveBreak = 0f
    var toSpawn = 0
    var spawnTimer = 0f
    var bossPending = false
    var waveBanner = 0f
    var bossBanner = 0f
    var kills = 0
    var best = 0

    // Levels: finishing every tower, tower archer and palisade loads a bigger map
    var level = 1
    var levelBanner = 0f // seconds left on the "level complete" banner
    private var levelUpPending = false
    private var waveCleared = true
    var kBoost = 0f // seconds of king speed boost left (after clearing a wave)
    var waveRewardBanner = 0f // "Vague terminée" banner
    private var rainLeft = 0 // boss coins still to drop
    private var rainX = 0f
    private var rainY = 0f
    private var rainRate = 0f
    var newRecord = false

    // Camera
    var camX = kx
    var camY = ky
    var shake = 0f

    // Paying
    private var payTimer = 0f
    private var payStreak = 0

    // fps
    var fps = 0
    private var fpsFrames = 0
    private var fpsTime = 0f

    private val tmp = Vec()
    private val tmp2 = Vec()

    init {
        var i = 0
        var ring = 0
        while (i < MAX_SQUAD) {
            val count = 6 + ring * 6
            val r = 46f + ring * 38f
            var k = 0
            while (k < count && i < MAX_SQUAD) {
                val a = (k.toFloat() / count) * 2f * PI.toFloat() + ring * 0.4f
                slotX[i] = cos(a) * r
                slotY[i] = sin(a) * r * 0.8f
                i++; k++
            }
            ring++
        }
        buildMap()
        best = Storage.loadBest()
        reset()
        state = STATE_MENU
    }

    /** Towers and palisades for the current [World] layout; existing ones (same index) are kept with their state. */
    private fun buildMap() {
        val oldT = towers
        val oldW = walls
        towers = Array(World.towerSpots.size / 2) { if (it < oldT.size) oldT[it] else Tower(World.towerSpots[it * 2], World.towerSpots[it * 2 + 1]) }
        walls = Array(World.paths.size) { if (it < oldW.size) oldW[it] else Wall(it, World.paths[it].length * World.wallFractions[it]) }
        for (w in walls) {
            val p = World.paths[w.path]
            p.sample(w.s, tmp)
            p.tangent(w.s, tmp2)
            w.x = tmp.x; w.y = tmp.y
            w.nx = -tmp2.y; w.ny = tmp2.x
            if (abs(w.nx) >= abs(w.ny)) { w.rx = 1f; w.ry = 0f } else { w.rx = 0f; w.ry = 1f }
        }
    }

    /** Adds the tower and palisade pads for towers/walls from the given indices (pads of older ones are kept, with what was paid). */
    private fun addMapPads(fromTower: Int, fromWall: Int) {
        for (i in fromTower until towers.size) {
            val t = towers[i]
            pads.add(Pad(PAD_TOWER, t.x, t.y, i).also { it.cost = price(25); it.visible = !t.built })
            pads.add(Pad(PAD_TOWER_ARCHER, t.x, t.y + 125f, i).also { it.cost = 1; it.visible = t.built && t.archers < TOWER_SLOTS })
        }
        for (i in fromWall until walls.size) {
            val w = walls[i]
            pads.add(Pad(PAD_WALL, w.x, w.y, i).also { it.cost = price(15); it.visible = !w.built })
        }
    }

    /** 1 = wood (levels 1-2), 2 = stone (3-4), 3 = royal (5+). */
    val age get() = ageOf(level)
    private fun ageOf(lv: Int) = if (lv >= 5) 3 else if (lv >= 3) 2 else 1
    var ageChanged = false // the last level-up started a new age (banner)
    var roadAdded = false // the last level-up added a road (banner)

    private fun wallMaxHp() = (300f + wave * 40f) * (if (age >= 2) 2.5f else 1f)

    fun reset() {
        level = 1
        towers = emptyArray(); walls = emptyArray()
        World.load(1)
        buildMap()
        rainLeft = 0
        levelBanner = 0f; levelUpPending = false; waveCleared = true; kBoost = 0f
        castleMaxHp = 600f
        kx = World.KING_START_X; ky = World.KING_START_Y
        kvx = 0f; kvy = 0f
        kMaxHp = when (heroClass) { CLASS_WARRIOR -> 300f; CLASS_RANGER -> 110f; else -> 160f } * (1f + 0.1f * Meta.level(heroClass, UP_HP))
        heroPower = 1f + 0.08f * Meta.level(heroClass, UP_POWER)
        abilityTime = ABILITY_COOLDOWN[heroClass] * (1f - 0.06f * Meta.level(heroClass, UP_ABILITY))
        abilityCd = 0f; abilityFlash = 0f; paused = false; transition = 0f; camZoom = 1f; revealRoad = -1
        kHp = kMaxHp; kDead = false; coins = 0
        squad = START_SQUAD
        castleHp = castleMaxHp
        castleAlarm = 0f
        dmgLevel = 0
        wave = 0; waveBreak = 6f; toSpawn = 0; bossPending = false
        kills = 0; time = 0f; newRecord = false
        revived = false; coinsCollected = 0; slowMo = 0f
        tutorial = if (Storage.loadInt("tuto_done") == 1) 4 else 0
        for (c in corpses) c.active = false
        for (sh in enemyShots) sh.active = false
        camX = kx; camY = ky
        for (i in 0 until squad) { ax[i] = kx + slotX[i]; ay[i] = ky + slotY[i]; aDir[i] = 0; aShoot[i] = 0f; acd[i] = 0.3f }
        for (e in enemies) e.active = false
        for (a in arrows) a.active = false
        for (c in coinsOnGround) c.active = false
        for (f in flyCoins) f.active = false
        for (p in particles) p.active = false
        for (t in texts) t.active = false
        for (t in towers) { t.built = false; t.archers = 0 }
        for (w in walls) { w.built = false; w.hp = 0f; w.gate = 0f }
        ringDirty = true
        recruitPad = Pad(PAD_RECRUIT, 720f, 1600f).also { it.level = squad; it.cost = recruitCost(squad) }
        forgePad = Pad(PAD_FORGE, 560f, 1640f).also { it.cost = forgeCost(0); it.visible = false }
        repairPad = Pad(PAD_REPAIR, 1170f, 1800f).also { it.cost = price(15); it.visible = false }
        pads.clear()
        pads.add(recruitPad); pads.add(forgePad); pads.add(repairPad)
        addMapPads(0, 0)
    }

    /**
     * Links the built walls into one enclosure: walls sorted by angle around the castle, and from each wall's end to the
     * next wall's start an arc whose radius blends between the two (so it stays around the base, outside the pads).
     */
    private fun rebuildRing() {
        ringDirty = false
        ringCount = 0
        var n = 0
        for (i in walls.indices) {
            val w = walls[i]
            if (!w.built || n >= ringOrder.size) continue
            ringOrder[n] = i
            ringAngle[n] = atan2(w.y - World.CASTLE_Y, w.x - World.CASTLE_X)
            n++
        }
        if (n < 2) return
        // insertion sort by angle (n <= 6)
        for (i in 1 until n) {
            var j = i
            while (j > 0 && ringAngle[j - 1] > ringAngle[j]) {
                val a = ringAngle[j]; ringAngle[j] = ringAngle[j - 1]; ringAngle[j - 1] = a
                val o = ringOrder[j]; ringOrder[j] = ringOrder[j - 1]; ringOrder[j - 1] = o
                j--
            }
        }
        val stone = age >= 2
        val half = if (stone) RAMPART_HALF else PALISADE_HALF
        for (k in 0 until n) {
            val a = walls[ringOrder[k]]
            val b = walls[ringOrder[(k + 1) % n]]
            // end of a with the larger angle, end of b with the smaller angle
            val ax = if (stone) a.rx else a.nx; val ay = if (stone) a.ry else a.ny
            val bx = if (stone) b.rx else b.nx; val by = if (stone) b.ry else b.ny
            var p0x = a.x + ax * half; var p0y = a.y + ay * half
            if (angleFrom(a, a.x - ax * half, a.y - ay * half) > angleFrom(a, p0x, p0y)) { p0x = a.x - ax * half; p0y = a.y - ay * half }
            var p1x = b.x - bx * half; var p1y = b.y - by * half
            if (angleFrom(b, b.x + bx * half, b.y + by * half) < angleFrom(b, p1x, p1y)) { p1x = b.x + bx * half; p1y = b.y + by * half }
            val t0 = atan2(p0y - World.CASTLE_Y, p0x - World.CASTLE_X)
            var dt = atan2(p1y - World.CASTLE_Y, p1x - World.CASTLE_X) - t0
            while (dt <= 0f) dt += 2f * PI.toFloat()
            if (dt > 4.7f) continue // ends overlap (walls very close): the wrap-around would circle the whole map
            val r0 = hypot(p0x - World.CASTLE_X, p0y - World.CASTLE_Y)
            val r1 = hypot(p1x - World.CASTLE_X, p1y - World.CASTLE_Y)
            val steps = max(1, (dt * (r0 + r1) * 0.5f / RING_STEP).toInt())
            for (s in 0..steps) {
                if (ringCount >= RING_MAX) return
                val f = s.toFloat() / steps
                val ang = t0 + dt * f
                val r = r0 + (r1 - r0) * f
                ringX[ringCount] = World.CASTLE_X + cos(ang) * r
                ringY[ringCount] = World.CASTLE_Y + sin(ang) * r
                ringRun[ringCount] = s > 0
                ringCount++
            }
        }
    }

    /** Angle of (x, y) around the castle, unwrapped to stay within pi of the wall's own angle. */
    private fun angleFrom(w: Wall, x: Float, y: Float): Float {
        val base = atan2(w.y - World.CASTLE_Y, w.x - World.CASTLE_X)
        var a = atan2(y - World.CASTLE_Y, x - World.CASTLE_X)
        while (a - base > PI.toFloat()) a -= 2f * PI.toFloat()
        while (a - base < -PI.toFloat()) a += 2f * PI.toFloat()
        return a
    }

    /** Share of this level's construction done (towers, their archers, walls): the HUD progress bar. */
    val levelProgress: Float get() {
        var done = 0
        var total = 0
        for (t in towers) { total += 1 + TOWER_SLOTS; if (t.built) done += 1 + t.archers }
        for (w in walls) { total++; if (w.built) done++ }
        return if (total == 0) 0f else done.toFloat() / total
    }

    private fun levelDone(): Boolean =
        towers.all { it.built && it.archers >= TOWER_SLOTS } && walls.all { it.built && it.hp > 0f }

    /** Next level: bank loose coins, clear the field, grow the map (everything built stays), maybe enter a new age. */
    private fun levelUp() {
        levelUpPending = false
        val oldAge = age
        level++
        levelBanner = 3.5f
        ageChanged = age != oldAge
        coins += rainLeft; rainLeft = 0
        for (c in coinsOnGround) if (c.active) { c.active = false; coins++ }
        // finishing a level banks all the coins in hand into the treasure (and the game goes on with them)
        Meta.treasure += coins
        for (f in flyCoins) if (f.active) { f.active = false; coins++ }
        for (e in enemies) e.active = false
        for (a in arrows) a.active = false
        toSpawn = 0; bossPending = false; waveBreak = 7f; waveCleared = true
        castleMaxHp *= if (ageChanged && age == 3) 1.25f * 1.5f else 1.25f // royal castle: +50 % on top
        castleHp = castleMaxHp
        val nt = towers.size
        val nw = walls.size
        World.load(level)
        buildMap()
        roadAdded = walls.size > nw
        addMapPads(nt, nw)
        // everything already built upgrades by itself
        for (w in walls) if (w.built) { w.maxHp = wallMaxHp(); w.hp = w.maxHp; w.bump = 1f }
        ringDirty = true
        for (t in towers) if (t.built) t.bump = 1f
        recruitPad.cost = recruitCost(recruitPad.level)
        forgePad.cost = forgeCost(forgePad.level)
        checkpoint = saveString()
        Storage.saveGame(checkpoint)
        kx = World.KING_START_X; ky = World.KING_START_Y; kvx = 0f; kvy = 0f
        camX = kx; camY = ky
        for (i in 0 until squad) { ax[i] = kx + slotX[i]; ay[i] = ky + slotY[i] }
        burst(kx, ky, 40f, 70, 0xFFFFD84AL, 420f)
        shake = 8f
        // ad-style sequence, see updateTransition
        transition = TRANSITION_TIME
        revealRoad = if (roadAdded) walls.size - 1 else -1
        for (k in CONFETTI.indices) burst(kx, ky, 90f, 22, CONFETTI[k], 520f)
    }

    private val CONFETTI = longArrayOf(0xFFFFD84AL, 0xFF4AD3FFL, 0xFFFF5A7AL, 0xFF7CFF8AL, 0xFFFFFFFFL)

    /** 0..1 progress of the new road drawing during the transition. */
    val revealT: Float get() = ((TRANSITION_TIME - transition - 1.9f) / 1.1f).coerceIn(0f, 1f)

    private fun smooth(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    private fun updateTransition(dt: Float) {
        val before = TRANSITION_TIME - transition
        transition = max(0f, transition - dt)
        val t = TRANSITION_TIME - transition
        // 0-1.1 s celebrate on the king, 1.1-1.9 zoom out over the base, 1.9-3.0 new road drawn, 3.2-4.2 back to the king
        val out = smooth((t - 1.1f) / 0.8f) * (1f - smooth((t - 3.2f) / 1f))
        camZoom = 1f - 0.5f * out
        val tx = kx + (World.CASTLE_X - kx) * out
        val ty = ky - 40f + (World.CASTLE_Y - 420f - (ky - 40f)) * out
        camX += (tx - camX) * min(1f, dt * 8f)
        camY += (ty - camY) * min(1f, dt * 8f)
        // the new road's pads pop in as the road reaches them
        if (before < 3.0f && t >= 3.0f) for (p in pads) if (p.visible && (p.kind == PAD_TOWER || p.kind == PAD_WALL) && p.ref == walls.size - 1) {
            p.bump = 1f; burst(p.x, p.y, 10f, 20, 0xFFFFD84AL, 260f)
        }
        if (t >= 0.25f && before < 0.25f) for (k in CONFETTI.indices) burst(kx, ky - 80f, 140f, 16, CONFETTI[k], 600f)
        if (transition <= 0f) { camZoom = 1f; revealRoad = -1 }
    }

    /** Class ability (button); returns false while on cooldown. */
    fun useAbility(): Boolean {
        if (state != STATE_PLAY || paused || transition > 0f || kDead || abilityCd > 0f) return false
        abilityCd = abilityTime
        abilityFlash = 0.6f
        when (heroClass) {
            CLASS_WARRIOR -> {
                for (i in enemies.indices) {
                    val e = enemies[i]
                    if (!e.active || hypot(e.x - kx, e.y - ky) > 190f + e.radius) continue
                    if (e.kind != 2) e.s = max(0f, e.s - 90f) // knocked back along its road
                    hitEnemy(i, 110f * dmgMult * heroPower)
                }
                shake = max(shake, 9f)
                burst(kx, ky, 20f, 50, 0xFFFFFFFFL, 520f)
                burst(kx, ky, 20f, 30, 0xFFFFC531L, 380f)
            }
            CLASS_RANGER -> {
                var n = 0
                for (i in enemies.indices) {
                    val e = enemies[i]
                    if (!e.active || hypot(e.x - kx, e.y - ky) > 520f || n >= 60) continue
                    fire(kx + (rnd.nextFloat() - 0.5f) * 80f, ky, 260f, i, 70f * dmgMult * heroPower)
                    n++
                }
                burst(kx, ky, 60f, 30, 0xFF9EE37DL, 300f)
            }
            else -> {
                castleHp = min(castleMaxHp, castleHp + castleMaxHp * 0.4f)
                for (w in walls) if (w.built) { w.hp = min(w.maxHp, w.hp + w.maxHp * 0.6f); w.bump = 1f; burst(w.x, w.y, 30f, 18, 0xFF7CFF8AL, 220f) }
                burst(World.CASTLE_X, World.CASTLE_Y, 60f, 40, 0xFF7CFF8AL, 300f)
                kHp = kMaxHp
            }
        }
        return true
    }

    /** Continue the saved game if any, else a new game with [cls]. */
    fun start(cls: Int = heroClass) {
        val save = Storage.loadGame()
        if (save == null || !restore(save)) { heroClass = cls; reset() }
        checkpoint = save ?: saveString()
        state = STATE_PLAY
    }

    /** Always a fresh game (drops the save). */
    fun newGame(cls: Int) {
        Storage.saveGame(null)
        heroClass = cls
        reset()
        checkpoint = saveString()
        state = STATE_PLAY
    }

    // ---------------------------------------------------------------- defeat: revive, retry, leave

    var revived = false // the one revive of this run is used
    var coinsCollected = 0
    private var checkpoint: String? = null // state at the start of the current level, for "retry"
    val reviveCost get() = max(10, coins / 2)
    val canRevive get() = !revived && coins >= reviveCost

    /** Pay half the coins: the keep stands again at 50 % and the enemies at its gates are swept away. */
    fun revive() {
        if (state != STATE_OVER || !canRevive) return
        coins -= reviveCost
        revived = true
        castleHp = castleMaxHp * 0.5f
        for (e in enemies) if (e.active && hypot(e.x - World.CASTLE_X, e.y - World.CASTLE_Y) < 520f) { e.active = false; burst(e.x, e.y, 20f, 8, 0xFFFFD84AL, 220f) }
        burst(World.CASTLE_X, World.CASTLE_Y, 60f, 60, 0xFFFFD84AL, 480f)
        shake = 10f
        state = STATE_PLAY
    }

    /** Ends the run for good: half the coins in hand go to the treasure. */
    private fun finishRun() {
        treasureWon = (coins + rainLeft) / 2
        Meta.treasure += treasureWon
        coins = 0; rainLeft = 0
    }

    /** Back to the start of the current level (same hero, the treasure gets its share first). */
    fun retryLevel() {
        finishRun()
        val cp = checkpoint
        if (cp == null || !restore(cp)) reset()
        Storage.saveGame(cp)
        state = STATE_PLAY
    }

    fun quitRun() {
        if (state == STATE_OVER) finishRun()
        toMenu()
    }

    // ---------------------------------------------------------------- first-game tutorial

    /** 0 move, 1 pick up gold, 2 recruit, 3 build a tower, 4 done (persisted). */
    var tutorial = 4
    private fun updateTutorial() {
        if (tutorial >= 4 || state != STATE_PLAY) return
        val next = when (tutorial) {
            0 -> hypot(kx - World.KING_START_X, ky - World.KING_START_Y) > 140f
            1 -> coinsCollected > 0
            2 -> squad > START_SQUAD
            else -> towers.any { it.built }
        }
        if (next) {
            tutorial++
            if (tutorial >= 4) Storage.saveInt("tuto_done", 1)
        }
    }

    /** Pad the tutorial points at, or null. */
    val tutorialTarget: Pad? get() = when (tutorial) {
        2 -> recruitPad
        3 -> pads.firstOrNull { it.visible && it.kind == PAD_TOWER }
        else -> null
    }

    val savedClass: Int get() = Storage.loadGame()?.split(";")?.getOrNull(14)?.toIntOrNull() ?: CLASS_WARRIOR
    val savedLevel: Int get() = Storage.loadGame()?.split(";")?.getOrNull(1)?.toIntOrNull() ?: 1

    /** App left the screen: pause the run and save it. */
    fun onBackground() {
        if (state == STATE_PLAY && transition <= 0f) { paused = true; saveGame() }
    }

    fun toMenu() {
        paused = false
        state = STATE_MENU
    }

    val hasSave get() = Storage.loadGame() != null

    // ---------------------------------------------------------------- save
    // shortcut: plain "k=v" text, written at each wave start and level-up; a format change just drops old saves.

    private fun saveGame() = Storage.saveGame(saveString())

    private fun saveString(): String {
        val sb = StringBuilder("v1")
        sb.append(";").append(level).append(";").append(wave).append(";").append(coins + rainLeft).append(";").append(squad)
        sb.append(";").append(dmgLevel).append(";").append(castleMaxHp).append(";").append(castleHp)
        sb.append(";").append(recruitPad.level).append(";").append(forgePad.level).append(";").append(if (forgePad.visible) 1 else 0)
        sb.append(";").append(kills).append(";")
        for (t in towers) sb.append(if (t.built) t.archers else -1).append(",")
        sb.append(";")
        for (w in walls) sb.append(if (w.built) w.hp else -1f).append(",")
        sb.append(";").append(heroClass)
        return sb.toString()
    }

    private fun restore(s: String): Boolean {
        val f = s.split(";")
        if (f.size < 14 || f[0] != "v1") return false
        return try {
            heroClass = f.getOrNull(14)?.toIntOrNull()?.coerceIn(0, 2) ?: CLASS_WARRIOR
            reset()
            level = f[1].toInt()
            World.load(level)
            buildMap()
            addMapPads(3, 3)
            wave = max(0, f[2].toInt() - 1) // the wave in progress is replayed
            coins = f[3].toInt()
            squad = f[4].toInt().coerceIn(0, MAX_SQUAD)
            dmgLevel = f[5].toInt()
            castleMaxHp = f[6].toFloat(); castleHp = f[7].toFloat()
            recruitPad.level = f[8].toInt(); recruitPad.cost = recruitCost(recruitPad.level)
            forgePad.level = f[9].toInt(); forgePad.cost = forgeCost(forgePad.level); forgePad.visible = f[10] == "1"
            kills = f[11].toInt()
            val ts = f[12].split(",")
            for (i in towers.indices) {
                val a = ts.getOrNull(i)?.toIntOrNull() ?: -1
                towers[i].built = a >= 0; towers[i].archers = a.coerceIn(0, TOWER_SLOTS)
            }
            val ws = f[13].split(",")
            for (i in walls.indices) {
                val hp = ws.getOrNull(i)?.toFloatOrNull() ?: -1f
                walls[i].built = hp > 0f; walls[i].maxHp = wallMaxHp(); walls[i].hp = hp.coerceAtMost(walls[i].maxHp)
            }
            for (p in pads) when (p.kind) {
                PAD_TOWER -> p.visible = !towers[p.ref].built
                PAD_TOWER_ARCHER -> p.visible = towers[p.ref].built && towers[p.ref].archers < TOWER_SLOTS
                PAD_WALL -> p.visible = !walls[p.ref].built
            }
            for (i in 0 until squad) { ax[i] = kx + slotX[i]; ay[i] = ky + slotY[i] }
            waveBreak = 5f
            ringDirty = true
            true
        } catch (e: NumberFormatException) {
            reset(); false
        }
    }

    // Archers are spent on towers now, so recruiting stays affordable; cheaper from level 2.
    private fun recruitCost(n: Int): Int = price((min(4 + n * 2, 20) * (if (level >= 2) 0.75f else 1f)).toInt())

    /** Builder pays 30 % less for everything. */
    private fun price(c: Int) = if (heroClass == CLASS_BUILDER) max(1, (c * 0.7f + 0.5f).toInt()) else c

    private fun forgeCost(n: Int): Int = price(((20 + n * 25 + n * n * 5) * (if (age >= 2) 0.7f else 1f)).toInt())

    fun bestOrCurrent() = max(best, wave)

    // ---------------------------------------------------------------- update

    fun update(dtIn: Float) {
        val dtReal = dtIn.coerceIn(0f, 1f / 20f)
        if (slowMo > 0f) slowMo -= dtReal
        val dt = if (slowMo > 0f) dtReal * 0.4f else dtReal
        fpsFrames++
        fpsTime += dtIn
        if (fpsTime >= 0.5f) {
            fps = (fpsFrames / fpsTime + 0.5f).toInt()
            fpsFrames = 0; fpsTime = 0f
        }
        time += dt
        if (state == STATE_MENU) {
            // gentle attract camera drift
            camX += (World.CASTLE_X + cos(time * 0.2f) * 180f - camX) * min(1f, dt * 2f)
            camY += (World.CASTLE_Y - 120f + sin(time * 0.15f) * 140f - camY) * min(1f, dt * 2f)
            updateParticles(dt)
            return
        }
        if (paused) return
        if (transition > 0f) {
            updateTransition(dt)
            updateParticles(dt)
            updateTexts(dt)
            if (levelBanner > 0f) levelBanner -= dt
            if (shake > 0f) shake = max(0f, shake - dt * 30f)
            return
        }
        if (state == STATE_PLAY) {
            updateKing(dt)
            updateSquad(dt)
            updateWaves(dt)
            updateEnemies(dt)
            updateTowers(dt)
            updatePads(dt)
            if (abilityCd > 0f) abilityCd -= dt
            if (abilityFlash > 0f) abilityFlash -= dt
        }
        updateArrows(dt)
        updateEnemyShots(dt)
        updateRain(dt)
        updateCoins(dt)
        updateFlyCoins(dt)
        updateParticles(dt)
        updateTexts(dt)
        for (c in corpses) if (c.active) { c.t += dt; if (c.t >= CORPSE_TIME) c.active = false }
        updateTutorial()
        if (shake > 0f) shake = max(0f, shake - dt * 30f)
        if (castleFlash > 0f) castleFlash -= dt
        if (castleAlarm > 0f) castleAlarm -= dt
        if (levelBanner > 0f) levelBanner -= dt
        if (kBoost > 0f) kBoost -= dt
        if (waveRewardBanner > 0f) waveRewardBanner -= dt
        if (levelUpPending) levelUp()
        if (ringDirty) rebuildRing()
        if (waveBanner > 0f) waveBanner -= dt
        if (bossBanner > 0f) bossBanner -= dt
        if (stackBump > 0f) stackBump = max(0f, stackBump - dt * 6f)
        for (w in walls) {
            val open = w.built && !kDead && hypot(kx - w.x, ky - w.y) < 110f
            w.gate = if (open) min(1f, w.gate + dt / 0.25f) else max(0f, w.gate - dt / 0.25f)
            if (w.hitFlash > 0f) w.hitFlash -= dt
            if (w.bump > 0f) w.bump = max(0f, w.bump - dt * 3f)
        }
        for (t in towers) if (t.bump > 0f) t.bump = max(0f, t.bump - dt * 3f)

        // camera follow
        val lead = 0.25f
        val tx = kx + kvx * lead
        val ty = ky + kvy * lead - 40f
        val f = min(1f, dt * 5f)
        camX += (tx - camX) * f
        camY += (ty - camY) * f
    }

    private fun updateKing(dt: Float) {
        if (kDead) {
            kRespawn -= dt
            if (kRespawn <= 0f) {
                kDead = false
                kHp = kMaxHp
                kx = World.CASTLE_X - 150f; ky = World.CASTLE_Y + 70f
                for (i in 0 until squad) { ax[i] = kx + slotX[i]; ay[i] = ky + slotY[i] }
                burst(kx, ky, 20f, 30, 0xFFFFE27AL, 260f)
            }
            kvx = 0f; kvy = 0f
            return
        }
        var mx = 0f
        var my = 0f
        if (input.touching) {
            val dx = input.cx - input.ox
            val dy = input.cy - input.oy
            val d = hypot(dx, dy)
            if (d > input.radiusPx * 0.08f) {
                val m = min(1f, d / input.radiusPx)
                mx = dx / d * m; my = dy / d * m
            }
        } else {
            if (input.left) mx -= 1f
            if (input.right) mx += 1f
            if (input.up) my -= 1f
            if (input.down) my += 1f
            val l = hypot(mx, my)
            if (l > 1f) { mx /= l; my /= l }
        }
        val speed = if (kBoost > 0f) 351f else 270f
        val tvx = mx * speed
        val tvy = my * speed
        val acc = min(1f, dt * 14f)
        kvx += (tvx - kvx) * acc
        kvy += (tvy - kvy) * acc
        var nx = kx + kvx * dt
        var ny = ky + kvy * dt
        nx = nx.coerceIn(40f, World.W - 40f)
        ny = ny.coerceIn(60f, World.H - 40f)
        if (World.river.isNotEmpty() && World.inRiver(nx, ny)) {
            if (!World.inRiver(nx, ky)) ny = ky
            else if (!World.inRiver(kx, ny)) nx = kx
            else { nx = kx; ny = ky }
        }
        if (World.insideCliff(nx, ny, 18f)) {
            if (!World.insideCliff(nx, ky, 18f)) ny = ky
            else if (!World.insideCliff(kx, ny, 18f)) nx = kx
            else { nx = kx; ny = ky }
        }
        // castle collision
        val cdx = nx - World.CASTLE_X
        val cdy = (ny - World.CASTLE_Y) * 1.6f
        val cd = hypot(cdx, cdy)
        val minD = World.CASTLE_R + 20f
        if (cd < minD && cd > 0.01f) {
            nx = World.CASTLE_X + cdx / cd * minD
            ny = World.CASTLE_Y + cdy / cd * minD / 1.6f
        }
        val stone = age >= 2
        for (w in walls) {
            if (!w.built) continue
            val lx = if (stone) w.rx else w.nx // wall line direction
            val ly = if (stone) w.ry else w.ny
            val rx = nx - w.x
            val ry = ny - w.y
            val along = rx * lx + ry * ly
            if (abs(along) > (if (stone) RAMPART_HALF else PALISADE_HALF)) continue
            val across = rx * ly - ry * lx // through it
            if (abs(across) >= 22f) continue
            if (abs(along) < GATE_HALF && w.gate > 0.6f) continue
            val side = if ((kx - w.x) * ly - (ky - w.y) * lx >= 0f) 1f else -1f
            nx = w.x + lx * along + ly * 22f * side
            ny = w.y + ly * along - lx * 22f * side
        }
        // the enclosure between walls: push out of any ring segment
        for (i in 1 until ringCount) {
            if (!ringRun[i]) continue
            val x0 = ringX[i - 1]; val y0 = ringY[i - 1]
            val sx = ringX[i] - x0; val sy = ringY[i] - y0
            val l2 = sx * sx + sy * sy
            if (l2 < 1f) continue
            val t = (((nx - x0) * sx + (ny - y0) * sy) / l2).coerceIn(0f, 1f)
            val cx = x0 + sx * t; val cy = y0 + sy * t
            val dx = nx - cx; val dy = ny - cy
            val d2 = dx * dx + dy * dy
            if (d2 >= 18f * 18f) continue
            // push to the side the king came from
            val pside = (kx - x0) * sy - (ky - y0) * sx
            val l = sqrt(l2)
            val px = sy / l * (if (pside >= 0f) 1f else -1f)
            val py = -sx / l * (if (pside >= 0f) 1f else -1f)
            nx = cx + px * 18f; ny = cy + py * 18f
        }
        kx = nx; ky = ny
        val sp = hypot(kvx, kvy)
        if (sp > 10f) {
            kWalk += dt * sp * 0.05f
            if (abs(kvx) > 15f) kFaceRight = kvx > 0f
            if (sp > 40f) kDir = dirIndex(kvx, kvy)
        }
        sway += (-kvx * 0.0016f - sway) * min(1f, dt * 6f)

        if (kHurtTimer > 0f) kHurtTimer -= dt
        else if (kHp < kMaxHp) kHp = min(kMaxHp, kHp + dt * 12f)

        kFireCd -= dt
        if (kFireCd <= 0f) {
            if (heroClass == CLASS_WARRIOR) {
                // sword cleave: every enemy within reach
                var hit = -1
                for (i in enemies.indices) {
                    val e = enemies[i]
                    if (!e.active || hypot(e.x - kx, e.y - ky) > 78f + e.radius) continue
                    hit = i
                    kDir = dirIndex(e.x - kx, e.y - ky)
                    burst((kx + e.x) * 0.5f, (ky + e.y) * 0.5f, 30f, 4, 0xFFFFFFFFL, 160f)
                    hitEnemy(i, 40f * dmgMult * heroPower)
                }
                if (hit >= 0) {
                    kFireCd = 0.6f
                } else kFireCd = 0.1f
            } else {
                val range = if (heroClass == CLASS_RANGER) 430f else 300f
                val t = nearestEnemy(kx, ky, range)
                if (t >= 0) {
                    fire(kx, ky - 6f, 52f, t, (if (heroClass == CLASS_RANGER) 24f else 18f) * dmgMult * heroPower)
                    kFaceRight = enemies[t].x > kx
                    if (sp <= 40f) kDir = dirIndex(enemies[t].x - kx, enemies[t].y - ky)
                    kFireCd = if (heroClass == CLASS_RANGER) 0.26f else 0.5f
                } else kFireCd = 0.1f
            }
        }
    }

    private fun updateSquad(dt: Float) {
        val f = min(1f, dt * 7f)
        for (i in 0 until squad) {
            val tx = kx + slotX[i]
            val ty = ky + slotY[i] + 10f
            val ox = ax[i]
            val oy = ay[i]
            ax[i] += (tx - ax[i]) * f
            ay[i] += (ty - ay[i]) * f
            val moved = hypot(ax[i] - ox, ay[i] - oy)
            aMoving[i] = moved > 0.25f
            if (aShoot[i] > 0f) aShoot[i] -= dt
            if (moved > 0.2f) {
                aWalk[i] += moved * 0.09f
                if (abs(ax[i] - ox) > 0.3f) aFaceRight[i] = ax[i] > ox
                if (aShoot[i] <= 0f && moved > 0.6f) aDir[i] = dirIndex(ax[i] - ox, ay[i] - oy)
            }
            if (kDead) continue
            acd[i] -= dt
            if (acd[i] <= 0f) {
                val t = nearestEnemy(ax[i], ay[i], 280f)
                if (t >= 0) {
                    fire(ax[i], ay[i], 30f, t, 20f * dmgMult)
                    aFaceRight[i] = enemies[t].x > ax[i]
                    aDir[i] = dirIndex(enemies[t].x - ax[i], enemies[t].y - ay[i])
                    aShoot[i] = 0.32f
                    acd[i] = 0.85f + rnd.nextFloat() * 0.25f
                } else acd[i] = 0.08f + rnd.nextFloat() * 0.1f
            }
        }
    }

    private fun updateTowers(dt: Float) {
        for (t in towers) {
            if (!t.built) continue
            for (a in 0 until t.archers) {
                if (t.shoot[a] > 0f) t.shoot[a] -= dt
                t.cd[a] -= dt
                if (t.cd[a] <= 0f) {
                    val ox = t.x + (a - 1) * 20f
                    val target = nearestEnemy(ox, t.y, if (age >= 2) 456f else 380f)
                    if (target >= 0) {
                        fire(ox, t.y, 128f, target, 26f * dmgMult * (if (age >= 3) 1.5f else 1f))
                        t.faceRight[a] = enemies[target].x > ox
                        t.dir[a] = dirIndex(enemies[target].x - ox, enemies[target].y - t.y)
                        t.shoot[a] = 0.32f
                        t.cd[a] = (0.75f + rnd.nextFloat() * 0.15f) / (if (age >= 2) 1.25f else 1f)
                    } else t.cd[a] = 0.1f
                }
            }
        }
    }

    private fun nearestEnemy(x: Float, y: Float, range: Float): Int {
        var best = -1
        var bd = range * range
        for (i in enemies.indices) {
            val e = enemies[i]
            if (!e.active) continue
            val dx = e.x - x
            val dy = e.y - y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    private fun fire(x: Float, y: Float, z: Float, target: Int, dmg: Float) {
        val a = arrows.firstOrNull { !it.active } ?: return
        val e = enemies[target]
        a.active = true
        a.x = x; a.y = y; a.z = z
        a.target = target; a.targetGen = e.gen
        a.tx = e.x; a.ty = e.y; a.tz = 22f * e.scale
        a.dmg = dmg
        a.life = 1.5f
        val dx = a.tx - x
        val dy = a.ty - y
        val d = hypot(dx, dy).coerceAtLeast(1f)
        a.dx = dx / d; a.dy = dy / d; a.dz = 0f
    }

    private fun updateEnemyShots(dt: Float) {
        for (sh in enemyShots) {
            if (!sh.active) continue
            val dx = sh.tx - sh.x
            val dy = sh.ty - sh.y
            val d = hypot(dx, dy)
            val step = 650f * dt
            if (d > step) { sh.x += dx / d * step; sh.y += dy / d * step; continue }
            sh.active = false
            when (sh.target) {
                1 -> if (!kDead && hypot(kx - sh.tx, ky - sh.ty) < 60f) damageKing(sh.dmg)
                2 -> { val w = walls[sh.ref]; if (w.built) { w.hp -= sh.dmg; w.hitFlash = 0.1f; if (w.hp <= 0f) breakWall(w) } }
                3 -> { castleHp -= sh.dmg; castleFlash = 0.12f; castleAlarm = 2.5f; if (castleHp <= 0f && state == STATE_PLAY) gameOver() }
            }
        }
    }

    private fun updateArrows(dt: Float) {
        val speed = 1000f
        for (a in arrows) {
            if (!a.active) continue
            a.life -= dt
            if (a.life <= 0f) { a.active = false; continue }
            val e = enemies[a.target]
            val alive = e.active && e.gen == a.targetGen
            if (alive) { a.tx = e.x; a.ty = e.y; a.tz = 22f * e.scale }
            val dx = a.tx - a.x
            val dy = a.ty - a.y
            val dz = a.tz - a.z
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            val step = speed * dt
            if (d <= step + 4f) {
                a.active = false
                if (alive) {
                    val blocked = e.kind == KIND_SHIELD && a.dx * e.mdx + a.dy * e.mdy < -0.3f
                    hitEnemy(a.target, if (blocked) a.dmg * 0.25f else a.dmg)
                    sparks(a.tx, a.ty, a.tz)
                    if (blocked) burst(a.tx, a.ty, a.tz, 4, 0xFFFFFFFFL, 160f)
                }
                continue
            }
            a.dx = dx / d; a.dy = dy / d; a.dz = dz / d
            a.x += a.dx * step; a.y += a.dy * step; a.z += a.dz * step
        }
    }

    private fun hitEnemy(i: Int, dmgIn: Float) {
        val e = enemies[i]
        val crit = rnd.nextFloat() < 0.12f
        val dmg = if (crit) dmgIn * 2f else dmgIn
        e.hp -= dmg
        e.hitFlash = 0.09f
        // tiny knockback
        if (e.kind != 2) {
            val dx = e.x - kx
            val dy = e.y - ky
            val d = hypot(dx, dy).coerceAtLeast(1f)
            e.x += dx / d * 3f; e.y += dy / d * 3f
        }
        text(e.x, e.y, 40f * e.scale + 10f, dmg.toInt(), e.kind == 2 || crit, if (crit) 1 else 0)
        if (e.kind == 2) shake = max(shake, 3f)
        if (e.hp <= 0f) killEnemy(e)
    }

    private fun killEnemy(e: Enemy) {
        e.active = false
        kills++
        corpses.firstOrNull { !it.active }?.let { c ->
            c.active = true; c.x = e.x; c.y = e.y; c.kind = e.kind; c.dir = e.dir; c.scale = e.scale; c.t = 0f
        }
        if (e.kind == 2) slowMo = 0.8f // boss down: brief slow motion
        burst(e.x, e.y, 20f * e.scale, if (e.kind == 2) 40 else 12, 0xFFD8262BL, 220f * e.scale)
        burst(e.x, e.y, 20f * e.scale, if (e.kind == 2) 20 else 5, 0xFF8A8F99L, 180f)
        if (e.kind == 2) { rainLeft += e.coins; rainX = e.x; rainY = e.y; rainRate = e.coins / 0.8f } // jackpot rain over 0.8 s
        else repeat(e.coins) { dropCoin(e.x, e.y) }
        if (e.kind == 2) shake = 14f
    }

    private fun updateRain(dt: Float) {
        if (rainLeft <= 0) return
        var n = min(rainLeft, (rainRate * dt).toInt() + 1)
        rainLeft -= n
        while (n-- > 0) dropCoin(rainX, rainY, 1.7f)
    }

    private fun dropCoin(x: Float, y: Float, spread: Float = 1f) {
        val c = coinsOnGround.firstOrNull { !it.active }
        if (c == null) { coins++; return }
        c.active = true
        c.x = x; c.y = y; c.z = 20f
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val sp = (40f + rnd.nextFloat() * 110f) * spread
        c.vx = cos(a) * sp; c.vy = sin(a) * sp * 0.7f
        c.vz = 260f + rnd.nextFloat() * 180f
        c.state = 0; c.t = 0f
        c.spin = rnd.nextFloat() * 6f
    }

    private fun updateCoins(dt: Float) {
        val magnet = if (heroClass == CLASS_BUILDER) 780f else 520f
        for (c in coinsOnGround) {
            if (!c.active) continue
            c.t += dt
            c.spin += dt * 6f
            when (c.state) {
                0 -> {
                    c.vz -= 1100f * dt
                    c.x += c.vx * dt; c.y += c.vy * dt; c.z += c.vz * dt
                    if (!kDead && state == STATE_PLAY && c.t > 0.1f) {
                        val dx = kx - c.x
                        val dy = ky - c.y
                        if (dx * dx + dy * dy < 200f * 200f) { c.state = 2; c.t = 0f; c.vx = 0f; continue }
                    }
                    if (c.z <= 0f) {
                        c.z = 0f
                        if (c.vz < -150f) { c.vz = -c.vz * 0.35f; c.vx *= 0.5f; c.vy *= 0.5f } else { c.state = 1; c.t = 0f }
                    }
                }
                1 -> {
                    if (!kDead && state == STATE_PLAY) {
                        val dx = kx - c.x
                        val dy = ky - c.y
                        if (dx * dx + dy * dy < magnet * magnet) { c.state = 2; c.t = 0f; c.vx = 0f }
                    }
                }
                2 -> {
                    if (kDead) { c.state = 1; c.z = 0f; continue }
                    c.vx += dt * 4500f // used as speed
                    val tz = 95f + min(coins, MAX_STACK) * 4.5f
                    val dx = kx - c.x
                    val dy = ky - c.y
                    val dz = tz - c.z
                    val d = sqrt(dx * dx + dy * dy + dz * dz)
                    val step = (500f + c.vx) * dt
                    if (d <= step) {
                        c.active = false
                        coins++
                        coinsCollected++
                        stackBump = 1f
                    } else {
                        c.x += dx / d * step; c.y += dy / d * step; c.z += dz / d * step
                    }
                }
            }
        }
    }

    private fun updatePads(dt: Float) {
        repairPad.visible = castleHp < castleMaxHp * 0.92f
        if (repairPad.bump > 0f) repairPad.bump -= dt * 3f
        var onPad: Pad? = null
        if (!kDead) {
            for (p in pads) {
                if (p.bump > 0f) p.bump = max(0f, p.bump - dt * 3f)
                if (!p.visible) continue
                if (abs(kx - p.x) < Pad.HALF && abs(ky - p.y) < Pad.HALF) { onPad = p; break }
            }
        }
        if (onPad != null && onPad.kind == PAD_TOWER_ARCHER) {
            // paid with squad archers, one every 0.45 s
            payTimer -= dt
            if (payTimer <= 0f && squad > 0) {
                payTimer = 0.45f
                squad--
                burst(ax[squad], ay[squad], 30f, 14, 0xFF6FB7FFL, 220f)
                complete(onPad)
            }
            return
        }
        if (onPad == null || coins <= 0 || onPad.paid + onPad.pending >= onPad.cost) {
            payTimer = 0.12f
            payStreak = 0
            return
        }
        payTimer -= dt
        while (payTimer <= 0f && coins > 0 && onPad.paid + onPad.pending < onPad.cost) {
            val f = flyCoins.firstOrNull { !it.active }
            if (f == null) {
                onPad.paid++
                coins--
                if (onPad.paid >= onPad.cost) complete(onPad)
            } else {
                f.active = true
                f.sx = kx; f.sy = ky; f.sz = 95f + min(coins, MAX_STACK) * 4.5f
                f.pad = pads.indexOf(onPad)
                f.t = 0f
                onPad.pending++
                coins--
            }
            payStreak++
            payTimer += max(0.022f, 0.09f - payStreak * 0.006f)
        }
    }

    private fun updateFlyCoins(dt: Float) {
        for (f in flyCoins) {
            if (!f.active) continue
            f.t += dt / 0.28f
            if (f.t >= 1f) {
                f.active = false
                if (f.pad >= pads.size) continue
                val p = pads[f.pad]
                p.pending--
                p.paid++
                p.bump = 0.35f
                if (p.paid >= p.cost) complete(p)
            }
        }
    }

    private fun complete(p: Pad) {
        p.paid = 0
        p.pending = 0
        p.bump = 1f
        burst(p.x, p.y, 10f, 26, 0xFFFFD84AL, 300f)
        when (p.kind) {
            PAD_RECRUIT -> {
                if (squad < MAX_SQUAD) {
                    ax[squad] = p.x; ay[squad] = p.y
                    aDir[squad] = 0; aShoot[squad] = 0f
                    acd[squad] = 0.3f
                    squad++
                    burst(p.x, p.y, 30f, 16, 0xFF6FB7FFL, 200f)
                }
                p.level++
                p.cost = recruitCost(p.level)
                if (squad >= MAX_SQUAD) p.visible = false
                forgePad.visible = true
            }
            PAD_FORGE -> {
                dmgLevel++
                p.level++
                p.cost = forgeCost(p.level)
            }
            PAD_REPAIR -> {
                castleHp = min(castleMaxHp, castleHp + castleMaxHp * 0.45f)
                p.level++
                p.cost = price(15 + p.level * 10)
                burst(World.CASTLE_X, World.CASTLE_Y, 60f, 30, 0xFF7CFF8AL, 260f)
            }
            PAD_TOWER -> {
                val t = towers[p.ref]
                t.built = true
                t.bump = 1f
                p.visible = false
                pads.first { it.kind == PAD_TOWER_ARCHER && it.ref == p.ref }.visible = true
                burst(t.x, t.y, 40f, 30, 0xFFA4683AL, 280f)
            }
            PAD_TOWER_ARCHER -> {
                val t = towers[p.ref]
                if (t.archers < TOWER_SLOTS) t.archers++
                t.bump = 0.6f
                p.level++
                if (t.archers >= TOWER_SLOTS) p.visible = false
                burst(t.x, t.y, 120f, 14, 0xFF6FB7FFL, 180f)
                if (levelDone()) levelUpPending = true
            }
            PAD_WALL -> {
                val w = walls[p.ref]
                w.built = true
                ringDirty = true
                w.maxHp = wallMaxHp()
                w.hp = w.maxHp
                w.bump = 1f
                p.visible = false
                burst(w.x, w.y, 20f, 26, 0xFF7A4A2AL, 280f)
                if (levelDone()) levelUpPending = true
            }
        }
    }

    // ---------------------------------------------------------------- waves & enemies

    private fun updateWaves(dt: Float) {
        if (toSpawn > 0 || bossPending) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                if (bossPending && toSpawn <= (3 + wave * 2) / 2) {
                    spawnEnemy(KIND_BOSS)
                    bossPending = false
                    bossBanner = 2.2f
                } else if (toSpawn > 0) {
                    spawnEnemy(pickKind())
                    toSpawn--
                }
                spawnTimer = max(0.22f, 0.95f - wave * 0.05f) * (0.6f + rnd.nextFloat() * 0.8f)
            }
            return
        }
        if (enemies.any { it.active }) return
        if (!waveCleared) {
            waveCleared = true
            if (wave > 0) {
                kBoost = 2f
                // wave reward: a shower of coins from the keep, growing with the wave
                rainLeft += 4 + wave * 2; rainX = World.CASTLE_X; rainY = World.CASTLE_Y + 60f; rainRate = (4 + wave * 2) / 0.8f
                waveRewardBanner = 2f
            }
        }
        waveBreak -= dt
        if (waveBreak <= 0f) {
            wave++
            saveGame()
            waveCleared = false
            toSpawn = 3 + wave * 2
            bossPending = wave % 5 == 0
            spawnTimer = 0.5f
            waveBanner = 2.2f
            waveBreak = 7f
            if (wave > best) {
                best = wave
                newRecord = true
                Storage.saveBest(best)
            }
        }
    }

    /** Wave mix: new enemy types join as the waves climb. */
    private fun pickKind(): Int {
        val r = rnd.nextFloat()
        return when {
            wave >= 8 && r < 0.05f && walls.any { it.built } -> KIND_RAM
            wave >= 6 && r < 0.15f -> KIND_SHIELD
            wave >= 4 && r < 0.27f -> KIND_ARCHER
            wave >= 3 && r < 0.42f -> KIND_WOLF
            wave >= 3 && r < 0.42f + 0.1f + wave * 0.012f -> KIND_BRUTE
            else -> KIND_SOLDIER
        }
    }

    private fun spawnEnemy(kind: Int, path: Int = -1, s: Float = 0f, ox: Float = Float.NaN, oy: Float = 0f): Enemy? {
        val e = enemies.firstOrNull { !it.active } ?: return null
        val paths = when {
            wave <= 1 -> 1
            wave <= 3 -> 2
            else -> World.paths.size
        }
        e.active = true
        e.gen++
        e.kind = kind
        e.path = if (path >= 0) path else if (paths == 1) 1 else rnd.nextInt(paths)
        e.s = s
        e.offX = (rnd.nextFloat() - 0.5f) * 60f
        e.offY = (rnd.nextFloat() - 0.5f) * 40f
        World.paths[e.path].sample(s, tmp)
        e.x = tmp.x + e.offX; e.y = tmp.y + e.offY
        if (!ox.isNaN()) { e.x = ox; e.y = oy }
        e.special = 0f; e.mdx = 0f; e.mdy = 0f
        val hpScale = 1f + (wave - 1) * 0.17f
        when (kind) {
            0 -> { e.maxHp = 50f * hpScale; e.speed = 75f + rnd.nextFloat() * 15f; e.dmg = 8f; e.radius = 16f; e.scale = 1f; e.coins = if (rnd.nextFloat() < 0.4f) 3 else 2 }
            KIND_BRUTE -> { e.maxHp = 170f * hpScale; e.speed = 58f; e.dmg = 18f; e.radius = 22f; e.scale = 1.4f; e.coins = 6 }
            KIND_WOLF -> { e.maxHp = 30f * hpScale; e.speed = 150f; e.dmg = 6f; e.radius = 14f; e.scale = 0.8f; e.coins = 1 + rnd.nextInt(2) }
            KIND_ARCHER -> { e.maxHp = 40f * hpScale; e.speed = 70f; e.dmg = 7f; e.radius = 15f; e.scale = 0.95f; e.coins = 3 }
            KIND_SHIELD -> { e.maxHp = 200f * hpScale; e.speed = 45f; e.dmg = 14f; e.radius = 20f; e.scale = 1.25f; e.coins = 7 }
            KIND_RAM -> { e.maxHp = 500f * hpScale; e.speed = 38f; e.dmg = 60f; e.radius = 30f; e.scale = 1.8f; e.coins = 15 }
            else -> {
                e.maxHp = 1000f * hpScale; e.speed = 45f; e.dmg = 40f; e.radius = 38f; e.scale = 2.4f; e.coins = 120 + 40 * (level - 1) + 10 * wave / 5
                e.bossType = age - 1
            }
        }
        if (kind == KIND_RAM) repeat(2) { k -> // escort
            spawnEnemy(KIND_SOLDIER, e.path, max(0f, s - 30f), e.x + (k * 2 - 1) * 40f, e.y + 20f)
        }
        e.hp = e.maxHp
        e.atkCd = 0.5f
        e.hitFlash = 0f
        e.swing = 0f
        e.walk = rnd.nextFloat() * 6f
        return e
    }

    private fun updateEnemies(dt: Float) {
        for (i in enemies.indices) {
            val e = enemies[i]
            if (!e.active) continue
            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.swing > 0f) e.swing = max(0f, e.swing - dt * 4f)
            e.atkCd -= dt
            val path = World.paths[e.path]
            val wall = walls[e.path]
            val reach = e.radius + 18f

            var tx: Float
            var ty: Float
            var target = 0 // 0 path, 1 king, 2 wall, 3 castle
            val dkx = kx - e.x
            val dky = ky - e.y
            val dk = hypot(dkx, dky)
            val aggro = when (e.kind) { KIND_BOSS -> 170f; KIND_WOLF -> 230f; KIND_RAM -> 0f; KIND_ARCHER -> 260f; else -> 140f }
            if (e.kind == KIND_BOSS && e.bossType == 2) { // dark lord: summons a ring of soldiers every 6 s
                e.special -= dt
                if (e.special <= 0f) {
                    e.special = 6f
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3f
                        spawnEnemy(KIND_SOLDIER, e.path, e.s, e.x + cos(a) * 70f, e.y + sin(a) * 50f)
                    }
                    burst(e.x, e.y, 40f, 30, 0xFF8E4AE0L, 300f)
                }
            }
            if (!kDead && dk < aggro) {
                tx = kx; ty = ky; target = 1
            } else {
                val slipsThrough = wall.gate > 0.5f && hypot(e.x - wall.x, e.y - wall.y) < 60f + e.radius
                val wallBlocks = wall.built && !slipsThrough && e.s >= wall.s - 30f - e.radius && e.s < wall.s + 10f
                path.sample(e.s, tmp)
                tx = tmp.x + e.offX * 0.6f; ty = tmp.y + e.offY * 0.6f
                if (wallBlocks) {
                    target = 2
                } else if (e.s >= path.length) {
                    target = 3
                    tx = World.CASTLE_X + e.offX * 1.5f
                    ty = World.CASTLE_Y + 40f + e.offY
                } else if (hypot(tx - e.x, ty - e.y) < 30f) {
                    e.s += e.speed * dt
                }
            }
            val dx = tx - e.x
            val dy = ty - e.y
            val d = hypot(dx, dy)
            if (e.kind == KIND_ARCHER) { // archers keep their distance and shoot
                val goal = when (target) { 1 -> dk; 2 -> hypot(wall.x - e.x, wall.y - e.y); 3 -> hypot(World.CASTLE_X - e.x, World.CASTLE_Y - e.y); else -> 9999f }
                if (goal < 250f) {
                    e.special -= dt
                    if (e.special <= 0f) {
                        e.special = 1.6f
                        e.swing = 1f
                        val sx = if (target == 1) kx else if (target == 2) wall.x else World.CASTLE_X
                        val sy = if (target == 1) ky else if (target == 2) wall.y else World.CASTLE_Y
                        e.dir = dirIndex(sx - e.x, sy - e.y)
                        enemyShots.firstOrNull { !it.active }?.let { sh ->
                            sh.active = true; sh.x = e.x; sh.y = e.y - 30f; sh.tx = sx; sh.ty = sy; sh.target = target; sh.ref = e.path; sh.dmg = e.dmg
                        }
                    }
                    continue
                }
            }
            val stopDist = when (target) {
                1 -> reach + 10f
                2 -> 20f
                3 -> World.CASTLE_R + e.radius - 10f
                else -> 0f
            }
            var moving = false
            val distToGoal = if (target == 3) hypot(World.CASTLE_X - e.x, (World.CASTLE_Y - e.y) * 1.6f) else d
            if (distToGoal > stopDist && d > 0.5f) {
                val leap = e.kind == KIND_WOLF && target == 1 && dk < 140f
                val step = min(e.speed * (if (leap) 1.8f else 1f) * dt, d)
                e.x += dx / d * step; e.y += dy / d * step
                e.mdx = dx / d; e.mdy = dy / d
                moving = true
                if (abs(dx) > 2f) e.faceRight = dx > 0f
                if (step > 0.2f) e.dir = dirIndex(dx, dy)
            }
            if (moving) e.walk += dt * e.speed * 0.09f
            // attacks
            if (e.atkCd <= 0f) {
                when {
                    target == 1 && dk < reach + 16f -> {
                        damageKing(e.dmg)
                        if (e.kind == KIND_BOSS) shake = max(shake, 9f)
                        if (e.kind == KIND_BOSS && e.bossType == 1 && dk > 1f) { // ogre: hurls the king and scatters the squad
                            kx = (kx + dkx / dk * 150f).coerceIn(40f, World.W - 40f); ky = (ky + dky / dk * 150f).coerceIn(60f, World.H - 40f)
                            for (q in 0 until squad) { ax[q] += (rnd.nextFloat() - 0.5f) * 160f; ay[q] += (rnd.nextFloat() - 0.5f) * 120f }
                            burst(kx, ky, 20f, 20, 0xFF9DA3AAL, 260f)
                        }
                        e.faceRight = dkx > 0f
                        e.dir = dirIndex(dkx, dky)
                        e.atkCd = 1.1f; e.swing = 1f
                    }
                    target == 2 && hypot(wall.x - e.x, wall.y - e.y) < 70f + e.radius -> {
                        wall.hp -= e.dmg
                        wall.hitFlash = 0.1f
                        e.atkCd = 1.1f; e.swing = 1f
                        burst(wall.x, wall.y, 20f, 3, 0xFF7A4A2AL, 140f)
                        if (wall.hp <= 0f) breakWall(wall)
                    }
                    target == 3 && distToGoal < stopDist + 20f -> {
                        castleHp -= e.dmg
                        castleFlash = 0.12f
                        castleAlarm = 2.5f
                        shake = max(shake, 4f)
                        e.atkCd = 1.1f; e.swing = 1f
                        if (castleHp <= 0f) gameOver()
                    }
                }
            }
        }
        // separation
        val n = enemies.size
        for (i in 0 until n) {
            val a = enemies[i]
            if (!a.active) continue
            for (j in i + 1 until n) {
                val b = enemies[j]
                if (!b.active) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val r = (a.radius + b.radius) * 0.9f
                val d2 = dx * dx + dy * dy
                if (d2 < r * r && d2 > 0.0001f) {
                    val d = sqrt(d2)
                    val push = (r - d) * 0.5f
                    val px = dx / d * push
                    val py = dy / d * push
                    val wa = if (a.kind == 2) 0.15f else 1f
                    val wb = if (b.kind == 2) 0.15f else 1f
                    a.x -= px * wa; a.y -= py * wa
                    b.x += px * wb; b.y += py * wb
                }
            }
            if (!kDead) {
                val dx = a.x - kx
                val dy = a.y - ky
                val r = a.radius + 22f
                val d2 = dx * dx + dy * dy
                if (d2 < r * r && d2 > 0.0001f) {
                    val d = sqrt(d2)
                    val w = if (a.kind == 2) 0.2f else 1f
                    a.x += dx / d * (r - d) * w; a.y += dy / d * (r - d) * w
                }
            }
        }
    }

    private fun breakWall(w: Wall) {
        w.built = false
        ringDirty = true
        w.hp = 0f
        burst(w.x, w.y, 25f, 34, 0xFF7A4A2AL, 320f)
        shake = max(shake, 6f)
        pads.first { it.kind == PAD_WALL && it.ref == w.path }.apply {
            visible = true; paid = 0; pending = 0; level++; cost = price(15 + level * 5)
        }
    }

    private fun damageKing(d: Float) {
        kHp -= d
        text(kx, ky, 90f, d.toInt(), false, 2)
        kHurtTimer = 3f
        shake = max(shake, 3f)
        if (kHp <= 0f) {
            kDead = true
            kRespawn = 3f
            burst(kx, ky, 30f, 30, 0xFF2F5BD3L, 260f)
            // drop half of the coins, they can be picked up again
            val lost = coins / 2
            coins -= lost
            repeat(min(lost, 120)) { dropCoin(kx, ky) }
        }
    }

    private fun gameOver() {
        Storage.saveGame(null)
        treasureWon = (coins + rainLeft) / 2 // shown on the panel; banked by finishRun()
        castleHp = 0f
        state = STATE_OVER
        shake = 20f
        burst(World.CASTLE_X, World.CASTLE_Y - 60f, 60f, 80, 0xFF9DA3AAL, 420f)
        burst(World.CASTLE_X, World.CASTLE_Y - 60f, 60f, 40, 0xFFFF8A2AL, 360f)
    }

    // ---------------------------------------------------------------- fx

    private fun burst(x: Float, y: Float, z: Float, count: Int, color: Long, speed: Float) {
        var n = 0
        for (p in particles) {
            if (n >= count) break
            if (p.active) continue
            n++
            p.active = true
            p.x = x; p.y = y; p.z = z
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.3f + rnd.nextFloat() * 0.7f)
            p.vx = cos(a) * s; p.vy = sin(a) * s * 0.6f
            p.vz = 150f + rnd.nextFloat() * speed
            p.gravity = 900f
            p.maxLife = 0.5f + rnd.nextFloat() * 0.5f
            p.life = p.maxLife
            p.size = 3f + rnd.nextFloat() * 5f
            p.color = color
        }
    }

    private fun sparks(x: Float, y: Float, z: Float) {
        var n = 0
        for (p in particles) {
            if (n >= 4) break
            if (p.active) continue
            n++
            p.active = true
            p.x = x; p.y = y; p.z = z
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val s = 120f + rnd.nextFloat() * 160f
            p.vx = cos(a) * s; p.vy = sin(a) * s * 0.6f; p.vz = sin(a) * s
            p.gravity = 0f
            p.maxLife = 0.18f + rnd.nextFloat() * 0.12f
            p.life = p.maxLife
            p.size = 2.5f + rnd.nextFloat() * 2f
            p.color = if (n % 2 == 0) 0xFFFFFFFFL else 0xFF8FD3FFL
        }
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.vz -= p.gravity * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            if (p.z < 0f) { p.z = 0f; p.vz = -p.vz * 0.3f; p.vx *= 0.6f; p.vy *= 0.6f }
        }
    }

    private fun text(x: Float, y: Float, z: Float, v: Int, big: Boolean, kind: Int = 0) {
        val t = texts.firstOrNull { !it.active } ?: return
        t.active = true
        t.x = x + (rnd.nextFloat() - 0.5f) * 20f
        t.y = y
        t.z = z
        t.value = v
        t.life = 0.8f
        t.big = big
        t.kind = kind
    }

    private fun updateTexts(dt: Float) {
        for (t in texts) {
            if (!t.active) continue
            t.life -= dt
            t.z += dt * 70f
            if (t.life <= 0f) t.active = false
        }
    }
}
