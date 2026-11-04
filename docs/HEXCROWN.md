# HEXCROWN — identité, mécaniques, publication

Décisions prises le 2026-11-04 (commandes complètes confiées à Claude). Ce document est la référence : il
prime sur les vieux noms « Kingshot » qui traînent dans le code et les docs. Objectifs : jouabilité
incroyable, addiction saine (boucle de progression, feel), **mode monde ouvert possible** par
l'architecture, **zéro plagiat** de Kingshot (Century Games), publication Play Store puis App Store.

## 1. Identité
- **Nom** : **Hexcrown** (FR/EN, prononçable, aucune app ni marque trouvée sous ce nom le 04-11-2026).
  Accroche : « Étends tes terres. Tiens la ligne. » / « Grow your lands. Hold the line. »
- **Ids** : Android `applicationId` + `namespace` **`com.hexcrown.game`**, package Kotlin
  `com.hexcrown.game`, `rootProject.name = "Hexcrown"`, desktop `packageName = "Hexcrown"`, iOS bundle
  `com.hexcrown.game`. Libellé d'app « Hexcrown ». Ces ids ne changeront plus après la publication.
- **Logo** : badge hexagonal or (pointe en haut) + couronne blanche (`art/icon/make_icon.py`) ; logotype
  HEXCROWN en Lilita One blanc → or (`art/ui/make_ui_kit_v3.py`). Aucun roi dessiné dans l'icône (Kingshot
  montre son héros ; nous montrons l'hexagone, notre signature).
- **Ce qu'on ne copie pas** : le nom, le logo, les écrans de pub (marteau/enclume), les noms de bâtiments.
  La pile de pièces sur la tête est gardée (demande de Mathis, 04-11 : « c'était mieux ») avec notre version :
  hauteur plafonnée à 30, ressort dans les virages, pop à l'empilement, dépense depuis le haut de la pile. L'inspiration (roi monté + escouade + vagues + chantiers) est un genre, pas un jeu.

## 2. Pourquoi on gagne : quatre mécaniques signature
### 2.1 Territoire : la grille d'hexagones et les bannières
- Le sol est une **grille d'hexagones** (rayon 96 px monde, pointe en haut). Chaque hex a un état :
  `neutre`, `à nous` (bannière), `brûlé` (ennemi).
- **Territoire automatique** (retour de Mathis le 04-11 : le bouton « planter » était pénible) : un hex devient
  à nous quand le roi y reste 0,6 s (fine barre sous le roi), un chantier construit prend sa fleur de 7 hex, le
  donjon part avec la sienne. Moment signé : le hex se déplie en 120 ms (expoOut), lueur de classe, son BANNER,
  haptique. Les bannières se posent seules au centre de chaque groupe de 7 hex (visuel, torche la nuit).
- **Nos hex** : +1 pièce / 4 s par hex (revenu passif affiché par petites pièces qui montent),
  les **chantiers ne se posent que sur nos hex**, les archers y tirent 10 % plus vite (lueur douce).
- **Ennemis sur nos hex** pendant 3 s → l'hex **brûle** (plus de revenu, bannière en feu). Le roi qui
  revient dessus l'éteint en 1,5 s. Un hex brûlé 20 s redevient neutre (la bannière tombe).
- (Remplacé par le territoire automatique, voir « Retours de Mathis ».)
- Boucle : s'étendre = plus de revenu et plus de chantiers, mais plus de front à tenir. C'est la
  décision stratégique à chaque vague, et c'est ce qui rend le monde ouvert naturel.

### 2.2 Reliques (draft roguelite, par run)
- Après chaque boss (vagues 5, 10, 15…) : **choisir 1 relique parmi 3** (panneau plein, cartes verre).
  12 reliques au lancement, chacune avec un contre-poids pour créer des builds :
  Flèches ardentes (dégâts +25 %, cadence −10 %), Trésor maudit (pièces ×2, donjon −30 % PV), Marche
  forcée (vitesse roi +20 %), Garde rapprochée (+2 archers, −15 % revenu), Bannières de fer (hex brûlent
  2× moins vite), Second souffle (1 réanimation gratuite), Pluie d'or (boss ×2 butin), Murs vivants
  (palissades se réparent), Grappin (portée aimant ×2), Vétérans (archers +1 niveau), Loups domptés
  (les loups tués ont 20 % de rejoindre l'escouade), Clairvoyance (vague suivante annoncée + 1 relique
  rare ensuite).
- Les reliques vues/possédées remplissent un **Codex** (collection permanente, 100 % = skin « Chroniqueur »).

### 2.3 Charge du roi
- Double-tap (ou bouton Charge sous Capacité) : la monture **charge** 320 px en 350 ms, renverse les
  ennemis traversés (dégâts 1×, recul 120 px), aspire les pièces sur 90 px, recharge 4 s. Avec traînée
  `fx_trail`, poussière, hit-stop 40 ms sur impact. C'est la sensation « je suis le roi » du joueur.

### 2.4 Jour / nuit et météo
- Cycle de 3 vagues jour → 1 vague crépuscule → 2 vagues nuit (teinte de scène bleue, torches sur les
  bannières, portée des archers −15 % hors torches, ennemis +10 % vitesse). Pluie (1 vague sur 6) : les
  flèches ardentes s'éteignent, les hex brûlent 2× moins vite. Affiché sur l'annonce de vague.

## 3. Feel et addiction (règles non négociables)
- Chaque impact : hit-stop ≤ 40 ms, squash, chiffre de dégâts, son discret (≤ 3 / 0,5 s), haptique LIGHT.
- Chaque pièce : aspirée en courbe expoOut, « tic » à hauteur montante (pitch +2 % par pièce, reset 400 ms).
- **Combo** : ennemis tués sans dommage au donjon → compteur ×1,1 par palier de 10 (max ×2) sur les
  pièces ; le combo se brise avec une secousse et un son grave. Affiché en haut à droite, petit.
- **Boss** : entrée avec bandeau nom + jauge, mort en ralenti 0,3× pendant 500 ms, pluie de pièces.
- **Fin de vague** : bilan 2 s (pièces, kills, combo max, hex tenus) sans bloquer le tap.
- **Progression** : niveau de héros (XP), talents, Codex de reliques, 20 niveaux + infini, missions
  quotidiennes (3), cadeau du jour, **succès** (30, locaux), **série** (jours joués d'affilée → bonus
  cadeau), classement local des meilleures vagues par classe. Jamais de pub, jamais d'argent réel,
  jamais de minuterie qui bloque le jeu.
- Session de 3 min qui donne envie d'une 4e : une « prochaine chose » toujours visible au menu
  (relique à découvrir, mission à 80 %, niveau à 1 étoile).

## 4. Monde ouvert : « Royaume » (architecture dès maintenant, mode livré après les niveaux)
- `World` devient **un ensemble de chunks hex** (rayon 12 hex = 1 chunk, ~1,5 écran). Le mode niveaux
  = 1 à 4 chunks figés avec routes scriptées. Le mode Royaume = chunks générés par assemblage de
  « tuiles » faites main (forêt, rivière, ruines, village, camp, col) avec coutures compatibles.
- **Caméra libre** qui suit le roi, culling par chunk (update et dessin seulement pour les chunks
  à ≤ 1,5 écran), ennemis en « flux » : chaque camp émet vers la bannière la plus proche en suivant
  un champ de flot calculé par chunk (plus de `GamePath` fixe en Royaume ; les niveaux gardent leurs
  routes scriptées via le même champ).
- **Sauvegarde du monde** : état des hex (bit-pack par chunk), bâtiments, camps vidés, villages
  rebâtis ; une seule clé `realm` sérialisée (varint) + version.
- Contenu Royaume v1 : 7 régions, 12 camps, 4 boss errants, 5 villages à rebâtir (quêtes courtes),
  brouillard de guerre levé par les bannières. Objectif : « couronner » les 7 régions.
- Interfaces à poser maintenant (RC) : `interface Terrain { fun hexAt(x,y): Hex; fun walkable(x,y) }`,
  `class HexGrid(chunks)`, `object Flow { fun dir(x,y, target): Vec }`, `class Spawner(camp)` ; `Game.update`
  ne doit plus itérer sur des tableaux globaux mais sur les chunks actifs.

### État du Royaume en v1.1
Livré : carte par graine (maison au centre, 7 régions en anneau), 12 camps, 4 boss, 5 villages, flux vers la bannière la
plus proche, brouillard, couronnes, sauvegarde `realm`. Pas encore : `Game.update` ne parcourt pas tout par chunk (seuls les
ennemis et le décor dorment hors de portée), la maison reste la carte du niveau 1, les ennemis sur le terrain rentrent
au camp à la reprise.

## 5. Publication (Play Store d'abord)
- **Signature** : keystore `~/.hexcrown/release.jks` créé sur le Mac (hors git), `keystore.properties`
  ignoré par git, lu par `composeApp/build.gradle.kts` ; sinon fallback debug pour les installs locales.
- **Versions** : `versionCode` = numéro de build auto (date `yyMMddHH`), `versionName` « 1.0.0 ».
- **SDK** : `targetSdk 35`, `minSdk 26`, portrait, `android:allowBackup="false"` (sauvegardes gérées
  par nous), pas de permission autre que VIBRATE.
- **Langues** : FR + EN via `object L` (clés → tableaux FR/EN, langue système, bascule dans Réglages).
  Tous les textes joueurs passent par `L["key"]` ; les noms propres (Hexcrown, classes) restent.
- **Confidentialité** : aucune donnée collectée, pas de réseau, pas de pub, pas d'achat. Page
  `docs/privacy.md` (publiée via GitHub Pages) + section « Données » dans Réglages. Data safety : rien.
- **Visuels store** : icône 512 (`art/icon/store_icon_512.png`), feature graphic 1024×500
  (`art/icon/feature_graphic.png`), 6 captures 1080×1920 (`docs/store/`), texte FR/EN
  (`docs/store/listing.md`). Classification : PEGI 7 / Everyone 10+ (violence cartoon).
- **Qualité** : 0 jank sur Pixel 8a (p95 ≤ 12 ms), test rotation/arrière-plan/reprise, 3 tailles d'écran
  (émulateurs Mac), `proguard` conservant les classes Compose Resources, AAB `bundleRelease`.
- **Onboarding** : 1er lancement = tutoriel de 60 s dans le niveau 1 (bouger, ramasser, planter une
  bannière, construire, charge) avec bulles, jamais de mur de texte.

## Retours de Mathis sur la v1.1 (priment sur ce qui précède)
Pile de pièces sur la tête du roi remise (notre version : plafonnée à 30, ressort sur 2 segments, lueur au sommet ; la
dépense part du haut de la pile). Territoire automatique, sans bouton ni coût : 0,6 s sur un hex neutre le prend, un
chantier construit prend sa fleur, une bannière apparaît au centre de chaque groupe complet de 7. Royaume : un camp vidé
se prend en restant 2 s dessus. Tutoriel en 5 bulles.

## Équilibrage (v1.0, réglé au bot sur l'ordinateur)
Départ : 1 fleur autour du donjon. Revenu de territoire plafonné à 2 pièces/s (+0,5 par rang du talent Bourse).
Bannière 30 + 10 par bannière. Niveau 1 : PV ennemis ×0,7 (0,85 au niveau 2, 1,2 ensuite), boss 520 PV de base
(1000 ensuite). Résultat bot, Difficile : niveau 1 fini en 2,5 à 3,8 min dans 7 parties sur 10, avec 6-7 bannières
(les chantiers sont trop éloignés pour moins). À revalider par une vraie partie : le bot ne passe pas le niveau 2.

## 6. Ordre de livraison
1. Rebrand (ids, nom, logo, logotype, README, docs, textes) + signature release + L FR/EN. → **v0.9**
2. Hex + bannières (grille dessinée, revenu, brûlure, chantiers sur hex) + Charge du roi. → **v0.95**
3. Reliques + Codex + combo + jour/nuit + succès + série + tutoriel + visuels store. → **v1.0 (Play)**
4. Royaume (chunks, flux, sauvegarde du monde, 7 régions) + iOS. → **v1.1**
Chaque étape : build release sur le Mac, install sur le Pixel (lancement seulement sur « go » de Mathis),
mesure fps, capture dans `docs/captures/<étape>/`, merge dans `main`.
