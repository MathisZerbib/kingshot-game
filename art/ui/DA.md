# Kingshot — direction artistique des menus et du HUD

Référence visuelle : `/mnt/project-files/kingshot/maquettes-menu.png` (générée par `art/ui/mockup.py`, écrans menu,
pause, défaite, talents, victoire). Style : « jeu mobile premium médiéval » : fond illustré 3D au couchant, cadres bois +
liseré or, parchemin pour l'information, bleu nuit pour les panneaux système, boutons épais brillants, titres Cinzel,
textes Nunito. Tout est en français.

## Palette (tokens)
| Token | Hex | Usage |
|---|---|---|
| navy | #15213B | panneaux système (pause, talents), bandeaux de cartes |
| navyDeep | #0C1326 | fond des pilules / jauges |
| royal | #2457D6 | ruban bleu, bouton bleu, barre d'XP |
| gold | #F2C14E | liserés, titres, chiffres de pièces |
| goldLight | #FFE896 | texte des rubans, reflets |
| goldDark | #A06C14 | ombres de l'or, contour des pilules |
| parchment | #F3E6C8 → #D6BE92 | fond des panneaux d'information |
| wood | #6B4423 (#402612 sombre) | cadres, bouton « bois » |
| ink | #3A240C | texte sur parchemin et sur bouton or |
| red | #D73B3B | ruban défaite, bouton danger, badges |
| green | #48B44C | bouton d'action principale (JOUER, REPRENDRE) |

## Polices (`composeResources/font/`, OFL)
- `cinzel_decorative_black` : uniquement le logotype (déjà rendu en PNG `ui_logotype`).
- `cinzel_bold` : titres de rubans, noms de héros, libellés des boutons, titres de cartes. Toujours en MAJUSCULES,
  `letterSpacing 0.04em`, contour 1-2 px sombre quand le texte est sur image.
- `nunito_extrabold` : textes courants, descriptions, sous-titres de boutons.
- `nunito_black` : chiffres (pièces, vagues, stats), badges.
Tailles (dp) : ruban 26-30, titre de carte 15-17, bouton principal 22, sous-titre de bouton 12, corps 13-14, badge 12.

## Kit (`composeResources/drawable/ui_*.png`, 9-slice, insets en px de l'image source)
| Asset | Taille | Insets (g, h, d, b) | Usage |
|---|---|---|---|
| ui_button_gold / green / blue / red / grey / wood | 480×150 | 40, 40, 40, 52 | boutons (le bas de 52 px = épaisseur) |
| ui_panel | 600×600 | 58, 58, 58, 58 | panneau parchemin (défaite, victoire, réglages) |
| ui_panel_dark | 600×600 | 58, 58, 58, 58 | panneau bleu nuit (pause, talents, missions, stats) |
| ui_card | 360×520 | 46, 104, 46, 46 | cartes (héros, talents, déblocage) ; bandeau titre de 58 px en haut |
| ui_ribbon_red / blue | 800×170 | 142, 40, 142, 40 | titres d'écran, posés à cheval sur le bord haut du panneau |
| ui_bar_track, ui_bar_gold / blue / red / green | 400×44 | 24, 20, 24, 20 | jauges (XP, PV donjon, progression) : piste + remplissage tronqué |
| ui_tab_on / off | 320×110 | 34, 34, 34, 10 | onglets du bas de menu |
| ui_pill | 300×90 | 47, 43, 47, 43 | compteurs (pièces, stats) |
| ui_circle / ui_circle_red | 200×200 | — (étirer uniformément) | boutons ronds à icône (réglages, cadeau, pause, capacité) |
| ui_logotype | 1200×360 | — | titre KINGSHOT (largeur ~82 % de l'écran) |
| menu_bg | 1080×1920 | — | fond du menu, `ContentScale.Crop` centré, + dégradé sombre haut (22 %) et bas (50 %) |
| ui_icon_* (25) | 160×160 | — | gear star crown shield sword bow hammer lock chest trophy book scroll heart speaker vibrate play pause close arrow flag hourglass skull gift medal coins |

Compose : un composable `NineSlice(res, insets, modifier)` qui dessine 9 `drawImage(srcOffset/srcSize → dstOffset/dstSize)`
(pas de .9.png en KMP) ; `FilterQuality.High`. Les icônes se teintent en gris (`ColorFilter.tint(0x80FFFFFF, Modulate)` ou
matrice de saturation 0) pour l'état verrouillé/vide.

## Écrans
**Menu principal** (fond `menu_bg`) : en haut à gauche bouton rond réglages (icône gear) et dessous bouton rond rouge
cadeau avec badge « ! » quand la récompense du jour est disponible ; en haut à droite pilule pièces du trésor (icône coins)
et dessous jauge d'XP bleue « Héros niv. N ». Au centre haut : couronne (icône) + logotype + « DÉFENDS TON ROYAUME »
(cinzel_bold, goldLight). Milieu bas : ruban bleu « CHOISIS TON HÉROS », trois `ui_card` : bandeau nom, portrait dans un
`ui_circle` (150 dp), 3 cœurs (pleins/éteints), rôle (nunito_extrabold, ink), capacité (nunito_black, brun), carte
sélectionnée : échelle 1,04 + halo or flou. Les améliorations permanentes du héros passent dans l'onglet Talents.
Boutons : vert « JOUER » (icône play, sous-titre « Guerrier · nouvelle partie »), or « CONTINUER » (sous-titre « Niveau 8 ·
La Clairière ») — si aucune sauvegarde, un seul bouton vert. Bas : 3 onglets Talents / Missions (badge rouge = missions
à réclamer) / Stats, l'onglet ouvert en `ui_tab_on`. Record discret sous les onglets si > 0.

**Pause** : voile 0xB9080C1A + `ui_panel_dark` 86 % de large, ruban bleu « PAUSE » à cheval, ligne « Classe · Niveau N ·
Vague N », note « Partie sauvegardée à chaque vague », boutons vert REPRENDRE (play), bleu RÉGLAGES (gear), bois MENU
PRINCIPAL (flag), puis les 3 interrupteurs rapides Sons / Musique / Vibrations (piste `ui_bar_track`, bouton `ui_circle`
48 dp, remplissage vert quand actif).

**Réglages** : `ui_panel` parchemin, ruban bleu « RÉGLAGES », interrupteurs (Sons, Musique, Vibrations, Fluidité max),
sélecteur de difficulté en 3 onglets (`ui_tab_on/off`) Normal / Difficile / Cauchemar, bouton rouge « Remettre à zéro »
(confirmation), bouton vert FERMER (close).

**Défaite** : `ui_panel` parchemin, ruban rouge « LE DONJON EST TOMBÉ » (ou « LE ROI EST TOMBÉ »), icône skull, 4 pilules
(Niveau, Vague, Ennemis, Or ramassé), « Nouveau record ! » en vert, ligne pièces + icône coins « +N pièces au trésor »,
boutons or REVIVRE (heart, sous-titre coût), vert RÉESSAYER (arrow), bois MENU (flag).

**Victoire / transition de niveau** : ruban bleu « NIVEAU N TERMINÉ », 3 étoiles (celle du milieu plus grande, grises
si non obtenues) qui apparaissent une à une avec LEVEL_UP, ligne des objectifs réussis, coffre (icône chest, 220 dp) qui
tremble : « Touche le coffre ! » → pluie de pièces + rarement une carte de talent ; carte `ui_card` « NOUVEAU : ÉCURIE »
avec le sprite du bâtiment à gauche et 2-3 effets à droite ; bouton vert NIVEAU SUIVANT (arrow) ; « +N pièces au trésor ».

**Talents / Missions / Stats** : `ui_panel_dark` 90 %, ruban bleu titre, pilule pièces, grille 3×3 de `ui_card` (titre,
icône 110 dp, 3 étoiles = niveau, bouton or « ● coût » ou gris MAX ; verrouillé : icône grisée, cadenas, « Niveau héros N »).
Missions : lignes parchemin claires avec jauge `ui_bar_gold` et bouton or RÉCLAMER. Stats : deux colonnes nunito.

**HUD en jeu** (Renderer.kt, dessin direct) : en haut à gauche portrait de classe dans `ui_circle` 64 dp + jauge PV rouge
+ niveau ; en haut au centre petit ruban bleu « VAGUE 12 / 20 » avec sous-ligne « prochaines : [icônes ennemis] » ; en haut
à droite pilule pièces (bourse / plafond) et bouton rond pause (icône pause). Jauge PV du donjon en `ui_bar_red` sous le
ruban. Bouton de capacité : `ui_circle_red` 84 dp avec icône de classe (sword / bow / hammer), anneau de recharge or.
Pancartes d'objectif et « NOUVEAU » en `ui_pill`/`ui_card`. Chiffres flottants en nunito_black avec contour.

## Animations
Pression de bouton : échelle 0,95 pendant 80 ms + son CLICK. Panneaux : apparition échelle 0,9 → 1 avec léger dépassement
(180 ms, EaseOutBack), voile en fondu 150 ms ; le ruban glisse de 20 dp vers le bas. Cartes de héros : la sélection fait
rebondir la carte (1,04) et jouer RECRUIT. Logotype : léger flottement (±4 dp, 3 s) et reflet qui balaie toutes les 6 s.
Étoiles de victoire : pop successifs à 250 ms d'intervalle. Coffre : tremblement avant ouverture, explosion de pièces.
