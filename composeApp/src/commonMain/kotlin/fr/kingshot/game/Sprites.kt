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
    return remember(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat, wallStone, wallStoneV, gateClosed, gateOpen, gateClosedV, gateOpenV, towerStone, towerRoyal, castleRoyal) {
        Sprites(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat, wallStone, wallStoneV, gateClosed, gateOpen, gateClosedV, gateOpenV, towerStone, towerRoyal, castleRoyal)
    }
}
