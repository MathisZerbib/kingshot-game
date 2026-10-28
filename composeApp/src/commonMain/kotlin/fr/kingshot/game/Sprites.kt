package fr.kingshot.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import fr.kingshot.game.resources.Res
import fr.kingshot.game.resources.archer
import fr.kingshot.game.resources.boss
import fr.kingshot.game.resources.castle
import fr.kingshot.game.resources.coin_flat
import fr.kingshot.game.resources.coin_spin
import fr.kingshot.game.resources.house
import fr.kingshot.game.resources.king
import fr.kingshot.game.resources.knight
import fr.kingshot.game.resources.pine0
import fr.kingshot.game.resources.pine1
import fr.kingshot.game.resources.rock0
import fr.kingshot.game.resources.rock1
import fr.kingshot.game.resources.roundtree
import fr.kingshot.game.resources.stake
import fr.kingshot.game.resources.tower
import fr.kingshot.game.resources.wall_stone
import fr.kingshot.game.resources.wall_stone_v
import fr.kingshot.game.resources.gate_closed
import fr.kingshot.game.resources.gate_open
import fr.kingshot.game.resources.gate_closed_v
import fr.kingshot.game.resources.gate_open_v
import fr.kingshot.game.resources.tower_stone
import fr.kingshot.game.resources.tower_royal
import fr.kingshot.game.resources.castle_royal
import fr.kingshot.game.resources.grass_tile
import fr.kingshot.game.resources.road_tile
import fr.kingshot.game.resources.cliff_tile
import fr.kingshot.game.resources.tuft0
import fr.kingshot.game.resources.tuft1
import fr.kingshot.game.resources.flower0
import fr.kingshot.game.resources.flower1
import fr.kingshot.game.resources.bush
import fr.kingshot.game.resources.stump
import fr.kingshot.game.resources.barrel
import fr.kingshot.game.resources.crate
import fr.kingshot.game.resources.tent
import fr.kingshot.game.resources.wheelbarrow
import fr.kingshot.game.resources.lumber
import fr.kingshot.game.resources.weaponrack
import org.jetbrains.compose.resources.imageResource

/** Sprite atlases pre-rendered in Blender from the KayKit models (see art/blender/render_sprites.py). */
class Sprites(
    val archer: ImageBitmap,
    val knight: ImageBitmap,
    val boss: ImageBitmap,
    val king: ImageBitmap,
    val castle: ImageBitmap,
    val house: ImageBitmap,
    val tower: ImageBitmap,
    val stake: ImageBitmap,
    val pine0: ImageBitmap,
    val pine1: ImageBitmap,
    val roundtree: ImageBitmap,
    val rock0: ImageBitmap,
    val rock1: ImageBitmap,
    val coinSpin: ImageBitmap,
    val coinFlat: ImageBitmap,
    val wallStone: ImageBitmap,
    val wallStoneV: ImageBitmap,
    val gateClosed: ImageBitmap,
    val gateOpen: ImageBitmap,
    val gateClosedV: ImageBitmap,
    val gateOpenV: ImageBitmap,
    val towerStone: ImageBitmap,
    val towerRoyal: ImageBitmap,
    val castleRoyal: ImageBitmap,
    val grassTile: ImageBitmap,
    val roadTile: ImageBitmap,
    val cliffTile: ImageBitmap,
    val tuft0: ImageBitmap,
    val tuft1: ImageBitmap,
    val flower0: ImageBitmap,
    val flower1: ImageBitmap,
    val bush: ImageBitmap,
    val stump: ImageBitmap,
    val barrel: ImageBitmap,
    val crate: ImageBitmap,
    val tent: ImageBitmap,
    val wheelbarrow: ImageBitmap,
    val lumber: ImageBitmap,
    val weaponrack: ImageBitmap,
)

@Composable
fun rememberSprites(): Sprites {
    val archer = imageResource(Res.drawable.archer)
    val knight = imageResource(Res.drawable.knight)
    val boss = imageResource(Res.drawable.boss)
    val king = imageResource(Res.drawable.king)
    val castle = imageResource(Res.drawable.castle)
    val house = imageResource(Res.drawable.house)
    val tower = imageResource(Res.drawable.tower)
    val stake = imageResource(Res.drawable.stake)
    val pine0 = imageResource(Res.drawable.pine0)
    val pine1 = imageResource(Res.drawable.pine1)
    val roundtree = imageResource(Res.drawable.roundtree)
    val rock0 = imageResource(Res.drawable.rock0)
    val rock1 = imageResource(Res.drawable.rock1)
    val coinSpin = imageResource(Res.drawable.coin_spin)
    val coinFlat = imageResource(Res.drawable.coin_flat)
    val wallStone = imageResource(Res.drawable.wall_stone)
    val wallStoneV = imageResource(Res.drawable.wall_stone_v)
    val gateClosed = imageResource(Res.drawable.gate_closed)
    val gateOpen = imageResource(Res.drawable.gate_open)
    val gateClosedV = imageResource(Res.drawable.gate_closed_v)
    val gateOpenV = imageResource(Res.drawable.gate_open_v)
    val towerStone = imageResource(Res.drawable.tower_stone)
    val towerRoyal = imageResource(Res.drawable.tower_royal)
    val castleRoyal = imageResource(Res.drawable.castle_royal)
    val grassTile = imageResource(Res.drawable.grass_tile)
    val roadTile = imageResource(Res.drawable.road_tile)
    val cliffTile = imageResource(Res.drawable.cliff_tile)
    val tuft0 = imageResource(Res.drawable.tuft0)
    val tuft1 = imageResource(Res.drawable.tuft1)
    val flower0 = imageResource(Res.drawable.flower0)
    val flower1 = imageResource(Res.drawable.flower1)
    val bush = imageResource(Res.drawable.bush)
    val stump = imageResource(Res.drawable.stump)
    val barrel = imageResource(Res.drawable.barrel)
    val crate = imageResource(Res.drawable.crate)
    val tent = imageResource(Res.drawable.tent)
    val wheelbarrow = imageResource(Res.drawable.wheelbarrow)
    val lumber = imageResource(Res.drawable.lumber)
    val weaponrack = imageResource(Res.drawable.weaponrack)
    return remember(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat, wallStone, wallStoneV, gateClosed, gateOpen, gateClosedV, gateOpenV, towerStone, towerRoyal, castleRoyal, grassTile, roadTile, cliffTile, tuft0, tuft1, flower0, flower1, bush, stump, barrel, crate, tent, wheelbarrow, lumber, weaponrack) {
        Sprites(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat, wallStone, wallStoneV, gateClosed, gateOpen, gateClosedV, gateOpenV, towerStone, towerRoyal, castleRoyal, grassTile, roadTile, cliffTile, tuft0, tuft1, flower0, flower1, bush, stump, barrel, crate, tent, wheelbarrow, lumber, weaponrack)
    }
}
