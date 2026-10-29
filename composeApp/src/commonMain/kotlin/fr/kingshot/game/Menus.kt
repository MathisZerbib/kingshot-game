package fr.kingshot.game

import androidx.compose.animation.core.RepeatMode
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
import fr.kingshot.game.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Menu screens built from the interface kit (art/ui/DA.md, mock-ups in art/ui/mockup.py).

private val CLASS_ROLE = arrayOf("Corps à corps · très solide", "Tir rapide à longue portée", "Constructions -30 % · aimant")
private val CLASS_ABILITY_SHORT = arrayOf("Tourbillon", "Pluie de flèches", "Réparation")
private val CLASS_HP = intArrayOf(3, 1, 2)
private val PORTRAITS = arrayOf(Res.drawable.portrait_warrior, Res.drawable.portrait_ranger, Res.drawable.portrait_builder)
val CLASS_ICONS = arrayOf(Res.drawable.ui_icon_sword, Res.drawable.ui_icon_bow, Res.drawable.ui_icon_hammer)
private val TALENT_ICONS = arrayOf(
    Res.drawable.ui_icon_hourglass, Res.drawable.ui_icon_coins, Res.drawable.ui_icon_hammer, Res.drawable.ui_icon_shield,
    Res.drawable.ui_icon_coins, Res.drawable.ui_icon_flag, Res.drawable.ui_icon_chest, Res.drawable.ui_icon_crown, Res.drawable.ui_icon_bow,
)
private val UP_ICONS = arrayOf(Res.drawable.ui_icon_heart, Res.drawable.ui_icon_sword, Res.drawable.ui_icon_hourglass)

@Composable
fun MainMenu(
    hasSave: Boolean, savedLevel: Int, savedClass: Int, best: Int,
    picked: Int, onPick: (Int) -> Unit, metaTick: Int,
    onContinue: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onMeta: (Int) -> Unit, onDaily: () -> Unit,
) {
    metaTick // re-read Meta after purchases
    val float = rememberInfiniteTransition()
    val bob by float.animateFloat(-4f, 4f, infiniteRepeatable(tween(1500), RepeatMode.Reverse))
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(Res.drawable.menu_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.22f to Color(0x00000000), 0.5f to Color(0x00000000), 1f to Color(0xCC000000))))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.dp, vertical = 8.dp)) {
            // top bar: settings + gift on the left, treasure + hero XP on the right
            Row(Modifier.fillMaxWidth()) {
                Column {
                    RoundButton(Res.drawable.ui_icon_gear, onClick = onSettings)
                    if (Daily.available) RoundButton(Res.drawable.ui_icon_gift, red = true, badge = true, onClick = onDaily)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Pill(Res.drawable.ui_icon_coins, "${Meta.treasure}", width = 130.dp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(130.dp), contentAlignment = Alignment.Center) {
                        Bar(Hero.progress(), Res.drawable.ui_bar_blue, Modifier.fillMaxWidth(), 16.dp)
                        GText("Héros niv. ${Hero.level}", 10, weight = FontWeight.Black)
                    }
                }
            }
            // logo
            Column(Modifier.fillMaxWidth().offset(y = bob.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(Res.drawable.ui_icon_crown), null, Modifier.size(40.dp))
                Image(painterResource(Res.drawable.ui_logotype), "Kingshot", Modifier.fillMaxWidth(0.82f), contentScale = ContentScale.FillWidth)
                GText("Défends ton royaume", 15, Pal.goldLight, title = true)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Ribbon("Choisis ton héros", width = 290.dp) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) HeroCard(c, c == picked, Modifier.weight(1f)) { onPick(c) }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KitButton(Res.drawable.ui_button_green, "Jouer", "${CLASS_NAMES[picked]} · nouvelle partie", Res.drawable.ui_icon_play, onClick = onNew)
                if (hasSave) KitButton(
                    Res.drawable.ui_button_gold, "Continuer", "Niveau $savedLevel · ${World.levelName(savedLevel)}", null,
                    width = 220.dp, height = 52.dp, textColor = Pal.ink, onClick = onContinue,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val ready = (0 until Missions.SLOTS).count { Missions.done(it) }
                MenuTab("Talents", Res.drawable.ui_icon_star, false, 0, Modifier.weight(1f)) { onMeta(0) }
                MenuTab("Missions", Res.drawable.ui_icon_scroll, false, ready, Modifier.weight(1f)) { onMeta(1) }
                MenuTab("Stats", Res.drawable.ui_icon_trophy, false, 0, Modifier.weight(1f)) { onMeta(2) }
            }
            if (best > 0) Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                GText("Record : vague $best · niveau ${maxOf(Stats.bestLevel, if (hasSave) savedLevel else 1)}", 12, Color(0xFFBFE8A8))
            }
        }
    }
}

@Composable
private fun HeroCard(c: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1.04f else 1f, tween(160))
    Column(
        modifier.scale(sc).nine(Res.drawable.ui_card, IN_CARD, 0.3f)
            .clickable { if (!selected) Audio.play(Sfx.RECRUIT); onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) { GText(CLASS_NAMES[c], 12, Pal.goldLight, title = true) }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x66FFD54A)))
            Image(painterResource(Res.drawable.ui_circle), null, Modifier.size(60.dp))
            Image(painterResource(PORTRAITS[c]), null, Modifier.size(50.dp).clip(CircleShape))
        }
        Row { for (k in 0 until 3) KitIcon(Res.drawable.ui_icon_heart, 14.dp, grey = k >= CLASS_HP[c]) }
        GText(CLASS_ROLE[c], 9, Pal.ink, shadow = false)
        GText(CLASS_ABILITY_SHORT[c], 10, Color(0xFF8A4A12), weight = FontWeight.Black, shadow = false)
    }
}

@Composable
private fun MenuTab(text: String, icon: DrawableResource, on: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).nine(if (on) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.3f)
                .clickable { Audio.play(Sfx.CLICK); onClick() }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(icon), null, Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            GText(text, 11, Color.White, title = true)
        }
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-6).dp).size(18.dp).clip(CircleShape).background(Pal.red),
            contentAlignment = Alignment.Center,
        ) { GText("$badge", 10, weight = FontWeight.Black, shadow = false) }
    }
}

@Composable
fun PausePanel(game: Game, onResume: () -> Unit, onSettings: () -> Unit, onMenu: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    tick
    KitPanel("Pause", dark = true) {
        GText("${CLASS_NAMES[game.heroClass]} · Niveau ${game.level} · Vague ${game.wave}", 14)
        GText("Partie sauvegardée à chaque vague", 11, Color(0xFFB8C4E0))
        KitButton(Res.drawable.ui_button_green, "Reprendre", icon = Res.drawable.ui_icon_play, onClick = onResume)
        KitButton(Res.drawable.ui_button_blue, "Réglages", icon = Res.drawable.ui_icon_gear, width = 220.dp, height = 52.dp, onClick = onSettings)
        KitButton(Res.drawable.ui_button_wood, "Menu principal", icon = Res.drawable.ui_icon_flag, width = 220.dp, height = 52.dp, onClick = onMenu)
        KitToggle("Sons", !Prefs.sfxOff, dark = true) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Sons complets", Prefs.sfxFull, dark = true) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
        KitToggle("Musique", !Prefs.musicOff, dark = true) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Vibrations", !Prefs.vibrationOff, dark = true) { Prefs.vibrationOff = !it; tick++ }
    }
}

@Composable
fun SettingsPanel(onReset: () -> Unit, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    tick
    KitPanel("Réglages", onDismiss = onClose) {
        KitToggle("Sons", !Prefs.sfxOff) { Prefs.sfxOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Sons complets", Prefs.sfxFull) { Prefs.sfxFull = it; Audio.applySettings(); tick++ }
        KitToggle("Musique", !Prefs.musicOff) { Prefs.musicOff = !it; Audio.applySettings(); tick++ }
        KitToggle("Vibrations", !Prefs.vibrationOff) { Prefs.vibrationOff = !it; tick++ }
        KitToggle("Fluidité max (sinon 60 Hz)", !Prefs.cap60) { Prefs.cap60 = !it; tick++ }
        GText("Difficulté", 15, Pal.ink, title = true, shadow = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 0..2) Box(
                Modifier.weight(1f).height(40.dp).nine(if (Prefs.difficulty == d) Res.drawable.ui_tab_on else Res.drawable.ui_tab_off, IN_TAB, 0.28f)
                    .clickable { Audio.play(Sfx.CLICK); Prefs.difficulty = d; tick++ },
                contentAlignment = Alignment.Center,
            ) { GText(DIFF_NAMES[d], 11, Color.White, title = true) }
        }
        GText("S'applique à la prochaine partie", 10, Pal.ink, shadow = false)
        KitButton(
            Res.drawable.ui_button_red, if (confirmReset) "Confirmer ?" else "Remettre à zéro",
            if (confirmReset) "trésor, talents, record, partie" else "toute la progression", width = 230.dp, height = 52.dp,
        ) { if (confirmReset) { Prefs.resetProgress(); confirmReset = false; onReset() } else confirmReset = true }
        KitButton(Res.drawable.ui_button_green, "Fermer", icon = Res.drawable.ui_icon_close, width = 200.dp, height = 52.dp, onClick = onClose)
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
    KitPanel(if (game.kDead) "Le roi est tombé" else "Le donjon est tombé", red = true, widthFrac = 0.9f) {
        KitIcon(Res.drawable.ui_icon_skull, 52.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatPill("${game.level}", "Niveau", Modifier.weight(1f))
            StatPill("${game.wave}", "Vague", Modifier.weight(1f))
            StatPill("${game.kills}", "Ennemis", Modifier.weight(1f))
            StatPill("${game.coinsCollected}", "Or ramassé", Modifier.weight(1f))
        }
        if (game.newRecord) GText("Nouveau record !", 15, Color(0xFF2E8B34), title = true, shadow = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitIcon(Res.drawable.ui_icon_coins, 20.dp)
            Spacer(Modifier.width(4.dp))
            GText("+${game.treasureWon} pièces au trésor", 12, Pal.ink, shadow = false)
        }
        if (game.canRevive) KitButton(
            Res.drawable.ui_button_gold, "Revivre", "Coûte ${game.reviveCost} pièces · une seule fois", Res.drawable.ui_icon_heart,
            textColor = Pal.ink, onClick = onRevive,
        )
        KitButton(Res.drawable.ui_button_green, "Réessayer", icon = Res.drawable.ui_icon_arrow, width = 230.dp, height = 52.dp, onClick = onRetry)
        KitButton(Res.drawable.ui_button_wood, "Menu", icon = Res.drawable.ui_icon_flag, width = 200.dp, height = 48.dp, onClick = onMenu)
    }
}

/** Talents (hero upgrades + 3x3 grid), missions and statistics. */
@Composable
fun MetaPanel(which: Int, metaTick: Int, hero: Int, onChange: () -> Unit, onClose: () -> Unit) {
    metaTick
    KitPanel(arrayOf("Talents", "Missions", "Statistiques")[which], dark = true, widthFrac = 0.94f, onDismiss = onClose) {
        Pill(Res.drawable.ui_icon_coins, "${Meta.treasure}", width = 140.dp)
        when (which) {
            0 -> {
                GText("${CLASS_NAMES[hero]} : améliorations", 12, Pal.goldLight, title = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (up in 0..2) TalentCard(UP_NAMES[up], UP_ICONS[up], Meta.level(hero, up), UP_MAX, Meta.cost(hero, up), Modifier.weight(1f)) {
                        if (Meta.buy(hero, up)) { Audio.play(Sfx.FORGE); onChange() }
                    }
                }
                GText("Talents du royaume", 12, Pal.goldLight, title = true)
                for (row in 0 until 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 3) {
                        val t = row * 3 + col
                        TalentCard(TALENT_NAMES[t], TALENT_ICONS[t], Talents.level(t), TALENT_MAX, Talents.cost(t), Modifier.weight(1f)) {
                            if (Talents.buy(t)) { Audio.play(Sfx.FORGE); onChange() }
                        }
                    }
                }
            }
            1 -> for (sl in 0 until Missions.SLOTS) {
                val done = Missions.done(sl)
                Column(
                    Modifier.fillMaxWidth().nine(Res.drawable.ui_panel, IN_PANEL, 0.22f).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    GText(Missions.label(sl), 13, Pal.ink, shadow = false, align = TextAlign.Start)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Bar(Missions.progress(sl).toFloat() / Missions.target(sl), Res.drawable.ui_bar_gold, Modifier.weight(1f), 14.dp)
                        Spacer(Modifier.width(8.dp))
                        GText("${minOf(Missions.progress(sl), Missions.target(sl))}/${Missions.target(sl)}", 11, Pal.ink, shadow = false)
                    }
                    if (done) KitButton(Res.drawable.ui_button_gold, "Réclamer", "+${Missions.reward(sl)} pièces", width = 180.dp, height = 46.dp, textColor = Pal.ink) {
                        Missions.claim(sl); Audio.play(Sfx.COIN_STACK); onChange()
                    } else GText("Récompense : ${Missions.reward(sl)} pièces", 10, Pal.ink, shadow = false)
                }
            }
            else -> {
                val secs = Stats.playSeconds
                for ((k, v) in listOf(
                    "Niveau de héros" to "${Hero.level}", "Meilleur niveau" to "${Stats.bestLevel}", "Meilleure vague" to "${Storage.loadBest()}",
                    "Étoiles" to "${Stats.stars} / 60", "Ennemis vaincus" to "${Stats.kills}", "Parties" to "${Stats.games}",
                    "Temps de jeu" to "${secs / 3600} h ${secs / 60 % 60} min",
                )) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Box(Modifier.weight(1f)) { GText(k, 14, Color(0xFFCCD6F0), align = TextAlign.Start) }
                    GText(v, 15, Pal.goldLight, weight = FontWeight.Black)
                }
            }
        }
        KitButton(Res.drawable.ui_button_green, "Fermer", icon = Res.drawable.ui_icon_close, width = 200.dp, height = 50.dp, onClick = onClose)
    }
}

@Composable
private fun TalentCard(name: String, icon: DrawableResource, lv: Int, max: Int, cost: Int, modifier: Modifier, onBuy: () -> Unit) {
    val can = lv < max && Meta.treasure >= cost
    Column(
        modifier.nine(Res.drawable.ui_card, IN_CARD, 0.22f).clickable(enabled = can) { onBuy() }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { GText(name, 9, Pal.goldLight, title = true) }
        Spacer(Modifier.height(6.dp))
        KitIcon(icon, 34.dp, grey = lv == 0)
        GText(if (max > 5) "$lv / $max" else "★".repeat(lv) + "☆".repeat(max - lv), 10, Pal.gold, weight = FontWeight.Black)
        Box(
            Modifier.fillMaxWidth().height(24.dp)
                .nine(if (lv >= max) Res.drawable.ui_button_grey else if (can) Res.drawable.ui_button_gold else Res.drawable.ui_button_grey, IN_BUTTON, 24f / 150f),
            contentAlignment = Alignment.Center,
        ) { GText(if (lv >= max) "MAX" else "● $cost", 10, if (can) Pal.ink else Color.White, weight = FontWeight.Black, shadow = false) }
    }
}

/** End of a level: stars, chest to tap, the new building, then on to the next level. */
@Composable
fun VictoryPanel(game: Game, onNext: () -> Unit) {
    var chestOpen by remember { mutableStateOf(false) }
    val shake = rememberInfiniteTransition()
    val wob by shake.animateFloat(-6f, 6f, infiniteRepeatable(tween(120), RepeatMode.Reverse))
    KitPanel("Niveau ${game.level - 1} terminé", widthFrac = 0.9f) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (k in 0 until 3) KitIcon(Res.drawable.ui_icon_star, if (k == 1) 64.dp else 48.dp, grey = k >= game.lastStars)
        }
        GText("Donjon > 80 % · aucun mur perdu · rapide", 11, Color(0xFF2E7D32), shadow = false)
        Box(
            Modifier.size(110.dp).rotate(if (chestOpen) 0f else wob).clickable(enabled = !chestOpen) { chestOpen = true; game.openChest() },
            contentAlignment = Alignment.Center,
        ) { KitIcon(Res.drawable.ui_icon_chest, 100.dp) }
        GText(if (chestOpen) "+${game.chestCoins} pièces · +${game.chestTreasure} au trésor" else "Touche le coffre !", 13, Pal.ink, title = !chestOpen, shadow = false)
        val id = game.newUnlockId
        if (id >= 0) {
            Column(
                Modifier.fillMaxWidth().nine(Res.drawable.ui_card, IN_CARD, 0.25f).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GText("Nouveau : " + (if (id == UNLOCK_MINE_CARD) "Mine et Caserne" else UNLOCK_NAMES[id]), 12, Pal.goldLight, title = true)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(unlockArt(id)), null, Modifier.size(80.dp))
                    Spacer(Modifier.width(8.dp))
                    GText(if (id == UNLOCK_MINE_CARD) "Mine : butin x1,5 · Caserne : 3 lanciers" else UNLOCK_DESC[id], 12, Pal.ink, shadow = false)
                }
            }
        }
        KitButton(Res.drawable.ui_button_green, "Niveau suivant", icon = Res.drawable.ui_icon_arrow) { if (!chestOpen) game.openChest(); onNext() }
    }
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
