package com.hexcrown.game

// Run relics (docs/CONTENT.md): one chosen out of 3 after each boss, 6 at most per run.

const val R_FIRE = 0
const val R_CURSE = 1
const val R_BOOTS = 2
const val R_GUARD = 3
const val R_IRON = 4
const val R_PHOENIX = 5
const val R_RAIN = 6
const val R_WALL = 7
const val R_GRAPPLE = 8
const val R_VETERAN = 9
const val R_WOLF = 10
const val R_EYE = 11
const val RELIC_COUNT = 12
const val RELIC_MAX = 6

object Relics {
    val ids = arrayOf("fire", "curse", "boots", "guard", "iron", "phoenix", "rain", "wall", "grapple", "veteran", "wolf", "eye")
    private val rare = booleanArrayOf(false, true, false, false, false, true, false, false, false, false, true, true)
    fun isRare(i: Int) = rare[i]
    fun name(i: Int) = L["relic.${ids[i]}"]
    fun desc(i: Int) = L["relic.${ids[i]}.desc"]
    fun iconName(i: Int) = "ui_icon_relic_${ids[i]}"
    fun seen(i: Int) = Storage.loadInt("codex_${ids[i]}") == 1
    fun markSeen(i: Int) = Storage.saveInt("codex_${ids[i]}", 1)
    val codexCount: Int get() { var n = 0; for (i in 0 until RELIC_COUNT) if (seen(i)) n++; return n }

    /**
     * Three distinct relics not owned yet. One rare is guaranteed by [forceRare]; otherwise each card is rare with
     * probability 1/4 (when a rare is left). Returns how many were drawn in [out].
     */
    fun draw(owned: BooleanArray, forceRare: Boolean, rnd: kotlin.random.Random, out: IntArray): Int {
        var n = 0
        fun pick(wantRare: Boolean): Int {
            var count = 0
            for (i in 0 until RELIC_COUNT) if (!owned[i] && rare[i] == wantRare && (0 until n).none { out[it] == i }) count++
            if (count == 0) return -1
            var k = rnd.nextInt(count)
            for (i in 0 until RELIC_COUNT) if (!owned[i] && rare[i] == wantRare && (0 until n).none { out[it] == i } && k-- == 0) return i
            return -1
        }
        for (slot in 0..2) {
            val wantRare = (slot == 0 && forceRare) || rnd.nextFloat() < 0.25f
            var p = pick(wantRare)
            if (p < 0) p = pick(!wantRare)
            if (p < 0) break
            out[n++] = p
        }
        return n
    }
}
