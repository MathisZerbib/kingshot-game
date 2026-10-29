# Effets des capacités de héros (v1 → « vrais effets »)

Feuilles : `composeApp/.../drawable/fx_*.png`, générées par `art/fx/make_fx.py`, décrites dans `art/fx/fx.json`
(`frame` = côté d'une image, `frames` = nombre d'images en bande horizontale, `blend` = `plus` (additif, pour la lumière)
ou `over`). Toutes sont blanches ou neutres : on les teinte avec `ColorFilter.tint(couleur, BlendMode.Modulate)`.
Couleurs de classe : Guerrier or `#FFC531`, Rôdeur vert `#9EE37D`, Bâtisseur or clair `#FFE08A` + vert `#7CFF8A`.
Rendu : tout est dessiné dans le monde (sous le zoom), trié avec les unités par `y`, sauf le flash d'écran et le titre.

| Feuille | Taille | Images | Usage |
|---|---|---|---|
| fx_slash | 256 | 8 | arc de lame (une image = 45° de rotation) |
| fx_ring | 256 | 8 | onde de choc qui s'étend |
| fx_spark | 128 | 6 | impact (étoile + flash) |
| fx_puff | 128 | 8 | poussière |
| fx_sparkle | 64 | 6 | scintillement |
| fx_glow | 256 | 1 | halo doux (sol, flash, particules lumineuses) |
| fx_shadow | 128 | 1 | ombre portée au sol (ellipse) |
| fx_trail | 256 | 1 | traînée (tête à droite : tourner selon la vitesse) |
| fx_reticle | 256 | 1 | marqueur de cible |
| fx_rune | 512 | 1 | sceau de réparation |
| fx_arrow_stuck | 64 | 1 | flèche plantée |
| fx_dot | 32 | 1 | particule ronde (remplace les carrés) |

## Règles communes
- **Hit-stop** : au déclenchement, `dt` ×0,15 pendant 90 ms (Guerrier, Bâtisseur) ou 50 ms (Rôdeur), puis retour normal.
- **Flash du héros** : sprite du roi en blanc (`ColorFilter.tint(white, SrcAtop)` à 80 %) pendant 60 ms.
- **Sol** : `fx_glow` sous le roi, teinte de classe, échelle 0 → 2,2 (en unités de rayon 60 px) sur 220 ms, alpha 0,9 → 0.
- **Titre** : « TOURBILLON ! » / « PLUIE DE FLÈCHES ! » / « RÉPARATION ! » en Lilita One 26 dp, couleur de classe, ombre,
  centré à 22 % de la hauteur : entrée 180 ms (échelle 1,3 → 1, alpha 0 → 1, expoOut), tenue 400 ms, sortie 200 ms
  (montée 10 dp + fondu). Jamais deux titres superposés.
- **Bouton** : à l'appui, échelle 0,9 (80 ms) puis 1,12 (160 ms) puis 1 ; anneau `fx_ring` autour du bouton (HUD, 4
  images en 200 ms) ; pendant la recharge, l'arc se remplit avec une pointe lumineuse qui tourne (`fx_dot` 6 dp).
- **Particules** : plus de `drawRect` : `fx_dot` (poussière, débris) en `over`, `fx_glow` 16-24 px en `plus` pour tout ce
  qui est lumineux (étincelles, pièces) ; taille 0,4 → 1 → 0,6 sur la vie, alpha 1 → 0 sur le dernier tiers.
- **Vignette** : flash de bord d'écran (dégradé radial, couleur de classe, alpha 0,35 → 0 sur 220 ms) au déclenchement.
- **Son** : ABILITY (discret) + vibration MEDIUM ; impacts : au plus 3 sons par demi-seconde (politique `Audio.discreet`).
- **Mouvement réduit** : pas de hit-stop ni de secousse, les effets restent.

## Tourbillon (Guerrier) — 0,75 s
| t (ms) | Effet |
|---|---|
| 0 | hit-stop 90 ms, flash héros, halo sol, vignette or, secousse 9, titre |
| 0 → 350 | `fx_ring` or, rayon 40 → 190 px (le rayon réel de la capacité), alpha 1 → 0 |
| 0 → 600 | 3 `fx_slash` or autour du roi (décalés de 120°), rayon 150 → 190 px, 2,5 tours (image = angle/45° mod 8, plus rotation fine du dessin), alpha 1 jusqu'à 400 ms puis → 0 ; échelle Y 0,55 (vue 3/4) |
| 0 → 600 | le roi tourne sur lui-même : direction d'atlas qui avance d'un cran toutes les 75 ms (8 directions = 1 tour), cheval compris |
| 80, 160, … 560 | `fx_puff` à un point aléatoire du cercle (rayon 150-190), 7 bouffées, teinte sable |
| à chaque ennemi touché | `fx_spark` or (rotation aléatoire, échelle 1,2) ; l'ennemi : recul de 90 px sur sa route en 180 ms (expoOut, pas instantané), squash 1,25/0,8 → 1 en 150 ms, chiffre de dégâts en or 1,3× |
| 350 → 750 | 12 `fx_glow` 14 px or qui s'éloignent en spirale (vitesse 380 px/s, courbure 2 rad/s), vie 0,4 s |

## Pluie de flèches (Rôdeur) — 1,6 s
| t (ms) | Effet |
|---|---|
| 0 | hit-stop 50 ms, flash héros, halo sol vert, vignette verte, titre ; le roi lève l'arc (image « tir » tenue 250 ms) |
| 0 → 300 | sur chaque cible (max 12 marqueurs, les autres sans marqueur) : `fx_reticle` vert, échelle 1,6 → 1 (expoOut), alpha 0 → 0,9, puis pulsation ±8 % à 4 Hz jusqu'à l'impact ; `fx_shadow` sous la cible, échelle 0,3 → 1 jusqu'à l'impact |
| 250, 430, 610 | trois salves ; par salve, les flèches de 1/3 des cibles : chaque flèche part du roi vers le haut (vz 900 px/s, légère dispersion), passe hors champ, redescend sur la cible (trajectoire balistique calculée pour arriver à t+450 ms ± 60) ; dessin : sprite flèche + `fx_trail` vert tourné selon la vitesse, longueur 60 px, alpha 0,8, en `plus` ; micro-secousse 3 à chaque salve |
| impact (700 → 1 300) | `fx_spark` petit (0,7) blanc-vert + `fx_puff` sable (0,8) ; dégâts ; `fx_arrow_stuck` planté au point d'impact (rotation ±15°), reste 2,5 s puis fondu 300 ms ; une flèche sur trois rate la cible si elle a bougé de > 40 px (réalisme, pas de perte de dégâts : la cible prend quand même) |
| 1 300 → 1 600 | les marqueurs restants se dissipent (échelle 1 → 1,3, alpha → 0) |

## Réparation (Bâtisseur) — 1,2 s
| t (ms) | Effet |
|---|---|
| 0 | hit-stop 90 ms, le roi abat le marteau (image d'attaque tenue 200 ms), flash héros, halo sol or, vignette or clair, titre, secousse 5 |
| 0 → 600 | `fx_ring` or clair centré sur le roi, rayon 0 → 700 px (toute la zone visible), alpha 0,9 → 0, épaisseur fine : c'est l'onde qui « touche » les murs |
| quand l'onde atteint un mur ou le donjon (distance au roi ≤ rayon courant) | flash blanc 80 ms, bump échelle 1,1 → 1 (220 ms, expoOut), la jauge de PV monte en 400 ms (pas instantanée), `+ 60 %` flottant vert au-dessus, 4 `fx_sparkle` or qui montent de 20 px sur 600 ms décalés de 90 ms, `fx_puff` léger à la base |
| 0 → 1 200 | `fx_rune` or sous le donjon : échelle 0,6 → 1,2, rotation 0 → 60°, alpha 0 → 0,8 (300 ms) → 0 (dernier 400 ms), écrasé en Y 0,55 |
| 200 → 1 000 | 24 `fx_glow` 10 px or qui montent du sol autour du donjon (vz 80 px/s, dérive sinus), vie 0,8 s |
| 1 000 | son REPAIR final (un seul), haptique LIGHT |

## Budget
≤ 80 sprites d'effet à l'écran, batching par feuille (une `drawImage` par instance, aucune allocation par image :
listes préallouées de 96 effets réutilisés). Mesurer sur le Pixel pendant une capacité : objectif inchangé, 120 fps, p95 ≤ 12 ms.
