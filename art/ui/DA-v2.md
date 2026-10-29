# Kingshot — menu v2 : « le héros tient l'écran »

Maquettes : `/mnt/project-files/kingshot/maquettes-v2-menu.png` (`art/ui/mockup_v2.py`). Captures de l'état v1 :
`docs/captures/menu-v1/`. Cette v2 remplace la section « Menu principal » de `DA.md` ; le kit, la palette et les
polices restent. Les règles « Motion » et « Échelle » ci-dessous s'appliquent à tous les écrans (pause, réglages,
talents, missions, stats, victoire, défaite, boutique).

## Pourquoi la v1 ne suffisait pas (critique)
- Trois cartes héros répètent le héros déjà énorme dans le fond, avec du texte de 9-10 dp illisible.
- Deux boutons de même poids (JOUER vert + CONTINUER or) : pas de hiérarchie. Un ruban « Choisis ton héros », une
  couronne, une devise, un record : du décor qui concurrence l'action.
- Le fond est rogné (crop) : le roi passe sous les cartes.
- Le logo flotte en permanence, les cartes pulsent : du mouvement partout, aucun moment signé.

## Principes v2
1. **Le fond est le sélecteur.** L'image du héros occupe le haut (fit largeur, calée en haut, 1080×1920) et fond
   dans le bleu nuit `#0C1326` à partir de 62 % de la hauteur : l'UI vit dans la zone sombre, jamais sur le roi.
2. **Une seule action principale** : un bouton or de 760×160 px (CONTINUER ou JOUER). « Nouvelle partie » est un
   lien texte souligné sous le bouton (deux étapes avant d'effacer). Pas de bouton vert au menu.
3. **Une plaque, pas des cartes** : nom du héros en Cinzel 68 px avec filet + losange or de chaque côté, rôle en
   Nunito 32, jauge d'XP du héros (300×26) et « Héros niv. N », trois jauges Vie / Attaque / Vitesse (pips 3),
   chip « Capacité » à droite. Tout le texte ≥ 22 px (≥ 13 dp).
4. **Trois pastilles portrait** (150 px, sélectionnée 186 px, surélevée de 14 px, anneau or 7 px + halo) posées sur la
   ligne de fondu. Tap ou balayage horizontal sur le fond change de héros.
5. **Haut dépouillé** : rond réglages à gauche (cadeau du jour dessous, rouge, seulement s'il est disponible),
   pilule trésor à droite, logotype à 63 % de large, sans couronne ni devise. Le record vit dans Stats.
6. **Barre d'onglets** unique en bas (bandeau bleu nuit 90 % d'opacité, liseré or) : BOUTIQUE / TALENTS / MISSIONS /
   STATS, badge rouge sur Missions quand une récompense attend.

## Échelle (dp) et marges
Logotype 63 % de la largeur ; nom du héros 30 ; rôle 15 ; libellés 13 ; nombres 16 ; bouton 24 + sous-titre 13 ;
onglets 11 avec icône 20. Marge latérale 24 ; zones tactiles ≥ 48 ; safe areas haut/bas respectées (la barre
d'onglets ne passe jamais sous la zone de gestes). Jamais de texte sous 13 dp.

## Motion (une seule scène signée, tout le reste discret)
Courbes : `expoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)` pour toute entrée, `inOut = CubicBezierEasing(0.77f,
0f, 0.175f, 1f)` pour ce qui se déplace, `linear` pour la dérive. Jamais d'ease-in, jamais de rebond élastique
sur l'UI (le seul rebond autorisé : la pastille sélectionnée, `spring(dampingRatio 0.7, stiffness Medium)`).
- **Ouverture du menu (une fois par lancement, < 800 ms, n'empêche jamais le tap)** : le fond est visible dès la
  première image ; logotype alpha 0→1 et échelle 1,05→1 en 420 ms ; pastilles montent de 20 dp avec 60 ms d'écart
  à partir de 120 ms ; plaque à 200 ms (alpha + 16 dp) ; bouton à 280 ms ; barre d'onglets à 340 ms.
- **Changement de héros (≤ 350 ms)** : le fond fait un fondu de 350 ms avec un glissement latéral de 30 dp dans le
  sens du balayage (l'ancien sort, le nouveau entre), flou 6→0 dp sur le nouveau si c'est gratuit (sinon pas de
  flou) ; la pastille grandit 150→186 en spring, l'anneau apparaît en 200 ms ; nom + rôle : fondu croisé 180 ms avec
  montée de 8 dp ; les jauges animent leur largeur depuis la valeur précédente (300 ms expoOut) ; le sous-titre du
  bouton change en fondu. Son RECRUIT (discret) + vibration légère. Balayage : seuil 80 dp ou vitesse > 0,3 dp/ms.
- **Pression** : tout ce qui est tapable passe à 0,97 en 100 ms au doigt posé, revient en 160 ms expoOut. Le bouton
  or s'éclaircit légèrement en plus.
- **Repos** : seule la dérive du fond (1,0→1,03 en 12 s aller-retour) et un reflet qui balaie le bouton or toutes
  les 6 s. Le logotype ne flotte plus. Rien ne pulse en continu sauf le halo de la pastille (très lent, 2 s).
- **Panneaux** (pause, réglages, boutique, talents…) : voile 150 ms, panneau 0,96→1 + alpha en 220 ms expoOut, sortie
  150 ms ; le ruban glisse de 12 dp. Les éléments d'une liste arrivent avec 40 ms d'écart (jamais plus de 6 décalés).
- **Mouvement réduit** (réglage système ou « Fluidité max » off) : fondus seulement, pas de glissement ni d'échelle.

## Boutique (nouvel écran plein, panneau bleu nuit)
En-tête : rond fermer à gauche, titre BOUTIQUE (Cinzel 62), pilule trésor à droite. Quatre onglets : ROIS /
ÉTENDARDS / MONTURES / TRAÎNÉES. Vitrine `ui_panel_dark` 980×760 : à gauche l'aperçu de l'article sur le fond du
héros (460×460, coins 28), à droite nom (Cinzel 46), « Guerrier · skin », trois lignes de description, bouton or
« ● prix » (ou vert ÉQUIPÉ, ou gris + condition si verrouillé), note « s'applique dès la prochaine vague ».
Grille 3×2 de `ui_card` 300×400 : titre, aperçu 200 px, bouton or prix / vert ÉQUIPÉ / cadenas + condition en gris
(aperçu désaturé). Tap sur une carte = elle passe en vitrine (fondu 180 ms). Achat : le bouton se presse, pluie de
pièces courte depuis la pilule trésor vers la carte (12 pièces, 500 ms), compteur qui décrémente, carte qui passe
ÉQUIPÉ avec halo or ; son PURCHASE. Catalogue et prix : voir `Shop.kt` (rois 1 500 / 3 000 / 6 000, étendards 800,
montures 1 200, traînées 600 ; déblocages par exploit affichés verrouillés avec la condition). Tout en pièces du
trésor, aucun bonus de jeu, pas d'argent réel. Pied : « Les skins ne changent pas la difficulté. »
Aperçus des rois : atlas recolorés rendus par `art/blender/render_sprites.py` (à venir : `king_<classe>_<skin>`),
en attendant un tint HSV du portrait.

## Copie (ton)
Phrases courtes, à l'impératif ou à l'indicatif, pas de majuscules partout hors titres Cinzel. Le bouton dit ce qui
arrive : « Continuer », « Jouer », « Équiper », « Acheter 1 500 ». Les états vides et verrouillés disent quoi faire :
« Atteins le niveau 10 », « Bats le premier boss ».
