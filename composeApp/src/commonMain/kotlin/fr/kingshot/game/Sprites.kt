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
import org.jetbrains.compose.resources.imageResource

/** Sprite atlases pre-rendered from the low-poly 3D models in art/ (see art/render.mjs). */
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
    return remember(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat) {
        Sprites(archer, knight, boss, king, castle, house, tower, stake, pine0, pine1, roundtree, rock0, rock1, coinSpin, coinFlat)
    }
}
