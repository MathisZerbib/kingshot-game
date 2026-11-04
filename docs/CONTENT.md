# Hexcrown — contenu v1.0 (données prêtes à coder)

Tables de référence pour `Relics.kt`, `Achievements.kt`, `Tutorial.kt` et `L.kt`. Les icônes existent déjà dans
`composeResources/drawable/ui_icon_<icon>.png`, les sons dans `files/sfx/<son>.wav`. Les sprites `banner`,
`banner_burning`, `banner_neutral`, `torch` sont rendus (entrées SpriteData : voir `art/blender/render_sprites.py`,
`--only banner,banner_burning,banner_neutral,torch` ou copier les 4 lignes du commit e3f777c).

## Reliques (choix de 1 parmi 3 après chaque boss ; max 6 par run ; rareté : C commun, R rare)
| id | icône | FR nom / effet | EN name / effect | rareté | effet (valeurs) |
|---|---|---|---|---|---|
| fire | relic_fire | Flèches ardentes — Dégâts +25 %, cadence −10 % | Burning Arrows — Damage +25%, fire rate −10% | C | dmgMul 1.25, rateMul 0.9, flèches teintées orange + fx_glow à l'impact ; éteintes sous la pluie (dmgMul 1.0) |
| curse | relic_curse | Trésor maudit — Pièces ×2, donjon −30 % PV max | Cursed Treasure — Coins ×2, keep −30% max HP | R | coinMul 2.0, keepHpMul 0.7 |
| boots | relic_boots | Marche forcée — Vitesse du roi +20 % | Forced March — King speed +20% | C | kingSpeedMul 1.2 |
| guard | relic_guard | Garde rapprochée — +2 archers, revenu des hex −15 % | Close Guard — +2 archers, hex income −15% | C | archers +2 à la prise, hexIncomeMul 0.85 |
| iron | relic_iron | Bannières de fer — Les hex brûlent deux fois moins vite | Iron Banners — Hexes burn half as fast | C | burnDelay 3 s → 6 s |
| phoenix | relic_phoenix | Second souffle — Une réanimation gratuite | Second Wind — One free revive | R | freeRevive 1 (consommé) |
| rain | relic_rain | Pluie d'or — Butin des boss ×2 | Golden Rain — Boss loot ×2 | C | bossLootMul 2.0 |
| wall | relic_wall | Murs vivants — Les palissades se réparent (2 PV/s) | Living Walls — Palisades regenerate (2 HP/s) | C | wallRegen 2/s |
| grapple | relic_grapple | Grappin — Portée de l'aimant ×2 | Grapple — Magnet range ×2 | C | magnetMul 2.0 |
| veteran | relic_veteran | Vétérans — Archers +1 niveau | Veterans — Archers +1 level | C | archerLevel +1 |
| wolf | relic_wolf | Loups domptés — 20 % des loups tués rejoignent l'escouade | Tamed Wolves — 20% of slain wolves join you | R | tameChance 0.2 (loup allié : 1 PV, mord 1×, suit le roi comme un archer) |
| eye | relic_eye | Clairvoyance — Vague suivante annoncée, prochaine relique rare garantie | Foresight — Next wave revealed, next relic guaranteed rare | R | revealNext true, nextRare true |

Tirage : 3 reliques distinctes non possédées ; 1 rare garantie si `nextRare` ou si aucune rare en 2 drafts ; sinon
P(rare) = 0,25 par carte. Panneau : 3 `ui_card` verre, icône 96 dp, nom Lilita 22, effet Nunito 15, bouton or
« Prendre » / « Take » ; entrée des cartes à 40 ms d'écart, la rare a un liseré or animé. Son `relic`, haptique LIGHT.
Codex (onglet dans Stats) : 12 cases, grisées si jamais prises ; 12/12 → skin « Chroniqueur » (gold recoloré, à rendre).

## Succès (30, locaux, 25 pièces chacun, 100 pour les 3 marqués ★)
| id | FR | EN | condition |
|---|---|---|---|
| first_banner | Première terre | First Land | prendre 1 hex |
| seven_hex | Sept terres | Seven Lands | tenir 7 hex à la fois |
| forty_hex | Seigneur des terres | Land Lord | tenir 40 hex à la fois ★ |
| no_burn | Pas une flamme | Not a Flame | finir un niveau sans hex brûlé |
| charge_10 | Cavalier | Rider | 10 charges |
| charge_trample | Piétinement | Trample | renverser 5 ennemis en une charge |
| combo_20 | Enchaînement | Chain | combo 20 |
| combo_50 | Imparable | Unstoppable | combo 50 ★ |
| boss_1 | Chasseur | Hunter | 1 boss |
| boss_10 | Tueur de géants | Giant Slayer | 10 boss |
| relic_first | Collectionneur | Collector | 1 relique |
| relic_codex | Chroniqueur | Chronicler | Codex 12/12 ★ |
| night_wave | Veilleur | Night Watch | finir une vague de nuit sans perdre de mur |
| rain_win | Sous la pluie | In the Rain | gagner un niveau pluvieux |
| level_5 / level_10 / level_20 | Chapitre I / II / IV | Chapter I / II / IV | atteindre le niveau |
| endless_20 | Sans fin | Endless | vague 20 en infini |
| archers_12 | Escouade | Squad | 12 archers à la fois |
| towers_6 | Bastion | Bastion | 6 tours à la fois |
| coins_1000 | Trésorier | Treasurer | 1 000 pièces en un run |
| talents_max | Maître | Master | un talent au niveau 5 |
| all_classes | Trois couronnes | Three Crowns | gagner un niveau avec chaque classe |
| skin_1 | Garde-robe | Wardrobe | acheter un skin |
| streak_7 | Fidèle | Loyal | série de 7 jours |
| missions_30 | Besogneux | Diligent | 30 missions |
| wolves_tamed | Meute | Pack | 5 loups domptés en un run |
| repair_walls | Maçon | Mason | réparer 100 PV de murs en une Réparation |
| whirl_8 | Tourbillon | Whirlwind | toucher 8 ennemis en un Tourbillon |
| arrows_rain | Déluge | Deluge | toucher 12 cibles en une Pluie de flèches |
Toast de succès : bandeau verre 56 dp en haut, icône `trophy`, 2,4 s, son `achievement`, jamais pendant un boss
(file d'attente).

## Série (jours joués d'affilée)
Compteur `streak` (jour local, tolérance 1 jour manqué = conservé une fois par semaine). Cadeau du jour
= 60 + 20 × min(série, 7) pièces ; palier 7 = +1 relique commune gratuite au prochain run. Affiché sur le cadeau
(« Série 4 jours »).

## Combo
`combo` +1 par ennemi tué, remis à 0 quand le donjon ou un mur prend des dégâts ; multiplicateur de pièces
1 + 0,1 × (combo / 10) plafonné à 2. Affichage : « ×1.3 · 27 » en Lilita 18 en haut à droite, pulse +6 % à chaque
palier de 10 (son `combo_up`) ; rupture : secousse 4, teinte rouge 150 ms, son `combo_break`.

## Jour / nuit / pluie
Cycle par niveau : vagues 1-3 jour, 4 crépuscule, 5-6 nuit, puis boucle. Nuit : voile `#0A1030` à 45 % (multiply)
sur le monde, torches `torch` sur chaque bannière à nous (portée 200 px, lueur `fx_glow` ambre), archers hors
torche portée −15 %, ennemis vitesse +10 %, son `night` à l'entrée. Pluie (1 vague sur 6, jamais la 1re) :
120 gouttes `fx_dot` 2 dp (pool), teinte gris-bleu 20 %, `rain_start`, flèches ardentes inactives, brûlure 2× plus
lente. Mouvement réduit : pas de gouttes, voile seulement.

## Tutoriel (niveau 1, première partie seulement, re-jouable dans Réglages)
Bulles verre (max 2 lignes, 16 dp, flèche vers la cible, bouton « OK » seulement pour la dernière) :
1. « Glisse pour déplacer le roi. » (jusqu'au 1er mouvement)
2. « Ramasse l'or des ennemis. » (jusqu'à 5 pièces)
3. « Tes terres grandissent là où tu passes. » (jusqu'au 1er hex pris)
4. « Construis sur tes terres. » (chantier le plus proche en surbrillance)
5. « Double-tape : charge ! » (jusqu'à la 1re charge), puis « Tiens la ligne. Le boss arrive à la vague 5. » (OK)
Jamais de pause forcée ; les ennemis de la vague 1 sont ralentis à 70 % tant que le tutoriel est actif.
