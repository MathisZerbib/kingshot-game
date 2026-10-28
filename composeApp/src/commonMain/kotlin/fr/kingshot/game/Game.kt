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
const val PAD_WALL = 5

const val MAX_SQUAD = 30

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
    var kind = 0 // 0 soldier, 1 brute, 2 boss
    var atkCd = 0f
    var swing = 0f
    var hitFlash = 0f
    var walk = 0f
    var faceRight = false
    var dir = 0
    var coins = 1
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
}

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
}

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

    val towers = Array(World.towerSpots.size / 2) { Tower(World.towerSpots[it * 2], World.towerSpots[it * 2 + 1]) }
    val walls = Array(World.paths.size) { Wall(it, World.paths[it].length * World.wallFractions[it]) }
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
        for (w in walls) {
            val p = World.paths[w.path]
            p.sample(w.s, tmp)
            p.tangent(w.s, tmp2)
            w.x = tmp.x; w.y = tmp.y
            w.nx = -tmp2.y; w.ny = tmp2.x
        }
        best = Storage.loadBest()
        reset()
        state = STATE_MENU
    }

    fun reset() {
        kx = World.KING_START_X; ky = World.KING_START_Y
        kvx = 0f; kvy = 0f
        kHp = kMaxHp; kDead = false; coins = 0
        squad = START_SQUAD
        castleHp = castleMaxHp
        castleAlarm = 0f
        dmgLevel = 0
        wave = 0; waveBreak = 6f; toSpawn = 0; bossPending = false
        kills = 0; time = 0f; newRecord = false
        camX = kx; camY = ky
        for (i in 0 until squad) { ax[i] = kx + slotX[i]; ay[i] = ky + slotY[i]; aDir[i] = 0; aShoot[i] = 0f; acd[i] = 0.3f }
        for (e in enemies) e.active = false
        for (a in arrows) a.active = false
        for (c in coinsOnGround) c.active = false
        for (f in flyCoins) f.active = false
        for (p in particles) p.active = false
        for (t in texts) t.active = false
        for (t in towers) { t.built = false; t.archers = 0 }
        for (w in walls) { w.built = false; w.hp = 0f }
        pads.clear()
        recruitPad = Pad(PAD_RECRUIT, 720f, 1600f).also { it.level = squad; it.cost = recruitCost(squad) }
        forgePad = Pad(PAD_FORGE, 560f, 1640f).also { it.cost = forgeCost(0); it.visible = false }
        repairPad = Pad(PAD_REPAIR, 1170f, 1800f).also { it.cost = 15; it.visible = false }
        pads.add(recruitPad); pads.add(forgePad); pads.add(repairPad)
        for (i in towers.indices) {
            val t = towers[i]
            pads.add(Pad(PAD_TOWER, t.x, t.y, i).also { it.cost = 25 })
            pads.add(Pad(PAD_TOWER_ARCHER, t.x, t.y + 125f, i).also { it.cost = 12; it.visible = false })
        }
        for (i in walls.indices) {
            val w = walls[i]
            pads.add(Pad(PAD_WALL, w.x, w.y, i).also { it.cost = 15 })
        }
    }

    fun start() {
        reset()
        state = STATE_PLAY
    }

    private fun recruitCost(n: Int): Int = when (n) {
        0 -> 6; 1 -> 16; 2 -> 30; 3 -> 45; 4 -> 60
        else -> 60 + (n - 4) * 20
    }

    private fun forgeCost(n: Int): Int = 20 + n * 25 + n * n * 5

    fun bestOrCurrent() = max(best, wave)

    // ---------------------------------------------------------------- update

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 1f / 20f)
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
        if (state == STATE_PLAY) {
            updateKing(dt)
            updateSquad(dt)
            updateWaves(dt)
            updateEnemies(dt)
            updateTowers(dt)
            updatePads(dt)
        }
        updateArrows(dt)
        updateCoins(dt)
        updateFlyCoins(dt)
        updateParticles(dt)
        updateTexts(dt)
        if (shake > 0f) shake = max(0f, shake - dt * 30f)
        if (castleFlash > 0f) castleFlash -= dt
        if (castleAlarm > 0f) castleAlarm -= dt
        if (waveBanner > 0f) waveBanner -= dt
        if (bossBanner > 0f) bossBanner -= dt
        if (stackBump > 0f) stackBump = max(0f, stackBump - dt * 6f)
        for (w in walls) {
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
        val speed = 270f
        val tvx = mx * speed
        val tvy = my * speed
        val acc = min(1f, dt * 14f)
        kvx += (tvx - kvx) * acc
        kvy += (tvy - kvy) * acc
        var nx = kx + kvx * dt
        var ny = ky + kvy * dt
        nx = nx.coerceIn(40f, World.W - 40f)
        ny = ny.coerceIn(60f, World.H - 40f)
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
            val t = nearestEnemy(kx, ky, 300f)
            if (t >= 0) {
                fire(kx, ky - 6f, 52f, t, 25f * dmgMult)
                kFaceRight = enemies[t].x > kx
                if (sp <= 40f) kDir = dirIndex(enemies[t].x - kx, enemies[t].y - ky)
                kFireCd = 0.5f
            } else kFireCd = 0.1f
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
                    val target = nearestEnemy(ox, t.y, 380f)
                    if (target >= 0) {
                        fire(ox, t.y, 128f, target, 26f * dmgMult)
                        t.faceRight[a] = enemies[target].x > ox
                        t.dir[a] = dirIndex(enemies[target].x - ox, enemies[target].y - t.y)
                        t.shoot[a] = 0.32f
                        t.cd[a] = 0.75f + rnd.nextFloat() * 0.15f
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
                    hitEnemy(a.target, a.dmg)
                    sparks(a.tx, a.ty, a.tz)
                }
                continue
            }
            a.dx = dx / d; a.dy = dy / d; a.dz = dz / d
            a.x += a.dx * step; a.y += a.dy * step; a.z += a.dz * step
        }
    }

    private fun hitEnemy(i: Int, dmg: Float) {
        val e = enemies[i]
        e.hp -= dmg
        e.hitFlash = 0.09f
        // tiny knockback
        if (e.kind != 2) {
            val dx = e.x - kx
            val dy = e.y - ky
            val d = hypot(dx, dy).coerceAtLeast(1f)
            e.x += dx / d * 3f; e.y += dy / d * 3f
        }
        text(e.x, e.y, 40f * e.scale + 10f, dmg.toInt(), e.kind == 2)
        if (e.kind == 2) shake = max(shake, 3f)
        if (e.hp <= 0f) killEnemy(e)
    }

    private fun killEnemy(e: Enemy) {
        e.active = false
        kills++
        burst(e.x, e.y, 20f * e.scale, if (e.kind == 2) 40 else 12, 0xFFD8262BL, 220f * e.scale)
        burst(e.x, e.y, 20f * e.scale, if (e.kind == 2) 20 else 5, 0xFF8A8F99L, 180f)
        repeat(e.coins) { dropCoin(e.x, e.y) }
        if (e.kind == 2) shake = 14f
    }

    private fun dropCoin(x: Float, y: Float) {
        val c = coinsOnGround.firstOrNull { !it.active }
        if (c == null) { coins++; return }
        c.active = true
        c.x = x; c.y = y; c.z = 20f
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val sp = 40f + rnd.nextFloat() * 110f
        c.vx = cos(a) * sp; c.vy = sin(a) * sp * 0.7f
        c.vz = 260f + rnd.nextFloat() * 180f
        c.state = 0; c.t = 0f
        c.spin = rnd.nextFloat() * 6f
    }

    private fun updateCoins(dt: Float) {
        val magnet = 320f
        for (c in coinsOnGround) {
            if (!c.active) continue
            c.t += dt
            c.spin += dt * 6f
            when (c.state) {
                0 -> {
                    c.vz -= 1100f * dt
                    c.x += c.vx * dt; c.y += c.vy * dt; c.z += c.vz * dt
                    if (c.z <= 0f) {
                        c.z = 0f
                        if (c.vz < -150f) { c.vz = -c.vz * 0.35f; c.vx *= 0.5f; c.vy *= 0.5f } else { c.state = 1; c.t = 0f }
                    }
                }
                1 -> {
                    if (!kDead && state == STATE_PLAY && c.t > 0.15f) {
                        val dx = kx - c.x
                        val dy = ky - c.y
                        if (dx * dx + dy * dy < magnet * magnet) { c.state = 2; c.t = 0f; c.vx = 0f }
                    }
                }
                2 -> {
                    if (kDead) { c.state = 1; c.z = 0f; continue }
                    c.vx += dt * 3200f // used as speed
                    val tz = 95f + min(coins, 60) * 4.5f
                    val dx = kx - c.x
                    val dy = ky - c.y
                    val dz = tz - c.z
                    val d = sqrt(dx * dx + dy * dy + dz * dz)
                    val step = (420f + c.vx) * dt
                    if (d <= step) {
                        c.active = false
                        coins++
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
                f.sx = kx; f.sy = ky; f.sz = 95f + min(coins, 60) * 4.5f
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
                p.cost = 15 + p.level * 10
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
                p.cost = 12 + p.level * 14
                if (t.archers >= TOWER_SLOTS) p.visible = false
                burst(t.x, t.y, 120f, 14, 0xFF6FB7FFL, 180f)
            }
            PAD_WALL -> {
                val w = walls[p.ref]
                w.built = true
                w.maxHp = 300f + wave * 40f
                w.hp = w.maxHp
                w.bump = 1f
                p.visible = false
                burst(w.x, w.y, 20f, 26, 0xFF7A4A2AL, 280f)
            }
        }
    }

    // ---------------------------------------------------------------- waves & enemies

    private fun updateWaves(dt: Float) {
        if (toSpawn > 0 || bossPending) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                if (bossPending && toSpawn <= (3 + wave * 2) / 2) {
                    spawnEnemy(2)
                    bossPending = false
                    bossBanner = 2.2f
                } else if (toSpawn > 0) {
                    val brute = wave >= 3 && rnd.nextFloat() < 0.1f + wave * 0.012f
                    spawnEnemy(if (brute) 1 else 0)
                    toSpawn--
                }
                spawnTimer = max(0.22f, 0.95f - wave * 0.05f) * (0.6f + rnd.nextFloat() * 0.8f)
            }
            return
        }
        if (enemies.any { it.active }) return
        waveBreak -= dt
        if (waveBreak <= 0f) {
            wave++
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

    private fun spawnEnemy(kind: Int) {
        val e = enemies.firstOrNull { !it.active } ?: return
        val paths = when {
            wave <= 1 -> 1
            wave <= 3 -> 2
            else -> 3
        }
        e.active = true
        e.gen++
        e.kind = kind
        e.path = if (paths == 1) 1 else rnd.nextInt(paths)
        e.s = 0f
        e.offX = (rnd.nextFloat() - 0.5f) * 60f
        e.offY = (rnd.nextFloat() - 0.5f) * 40f
        World.paths[e.path].sample(0f, tmp)
        e.x = tmp.x + e.offX; e.y = tmp.y + e.offY
        val hpScale = 1f + (wave - 1) * 0.17f
        when (kind) {
            0 -> { e.maxHp = 50f * hpScale; e.speed = 75f + rnd.nextFloat() * 15f; e.dmg = 8f; e.radius = 16f; e.scale = 1f; e.coins = if (rnd.nextFloat() < 0.4f) 3 else 2 }
            1 -> { e.maxHp = 170f * hpScale; e.speed = 58f; e.dmg = 18f; e.radius = 22f; e.scale = 1.4f; e.coins = 6 }
            else -> { e.maxHp = 1000f * hpScale; e.speed = 45f; e.dmg = 40f; e.radius = 38f; e.scale = 2.4f; e.coins = 40 }
        }
        e.hp = e.maxHp
        e.atkCd = 0.5f
        e.hitFlash = 0f
        e.swing = 0f
        e.walk = rnd.nextFloat() * 6f
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
            val aggro = if (e.kind == 2) 170f else 140f
            if (!kDead && dk < aggro) {
                tx = kx; ty = ky; target = 1
            } else {
                val wallBlocks = wall.built && e.s >= wall.s - 30f - e.radius && e.s < wall.s + 10f
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
            val stopDist = when (target) {
                1 -> reach + 10f
                2 -> 20f
                3 -> World.CASTLE_R + e.radius - 10f
                else -> 0f
            }
            var moving = false
            val distToGoal = if (target == 3) hypot(World.CASTLE_X - e.x, (World.CASTLE_Y - e.y) * 1.6f) else d
            if (distToGoal > stopDist && d > 0.5f) {
                val step = min(e.speed * dt, d)
                e.x += dx / d * step; e.y += dy / d * step
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
        w.hp = 0f
        burst(w.x, w.y, 25f, 34, 0xFF7A4A2AL, 320f)
        shake = max(shake, 6f)
        pads.first { it.kind == PAD_WALL && it.ref == w.path }.apply {
            visible = true; paid = 0; pending = 0; level++; cost = 15 + level * 5
        }
    }

    private fun damageKing(d: Float) {
        kHp -= d
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

    private fun text(x: Float, y: Float, z: Float, v: Int, big: Boolean) {
        val t = texts.firstOrNull { !it.active } ?: return
        t.active = true
        t.x = x + (rnd.nextFloat() - 0.5f) * 20f
        t.y = y
        t.z = z
        t.value = v
        t.life = 0.8f
        t.big = big
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
