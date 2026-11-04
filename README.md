# Hexcrown — roi, bannières, sièges (sans pub)

Jeu mobile de défense : un roi à cheval mène une escouade d'archers, plante des bannières pour réclamer des
hexagones, bâtit sur ses terres (archers, tours, palissades, forge) et tient la ligne contre des vagues de plus en
plus rusées, avec un boss et un choix de relique toutes les 5 vagues. Identité, mécaniques et plan de publication :
`docs/HEXCROWN.md`.

Kotlin Multiplatform + Compose Multiplatform (Canvas). Cibles : Android, iOS, desktop (JVM).

## Fluidité (60 / 120 fps)
- Boucle de jeu calée sur la vsync (`withFrameNanos`), delta-time réel, pas de limite de fps.
- Android demande le mode d'affichage le plus rapide (120 Hz sur Pixel 8a), iOS active ProMotion
  (`CADisableMinimumFrameDurationOnPhone`).
- Zéro allocation par frame : pools d'objets, `Path` et textes pré-calculés, tri de profondeur
  sur un `LongArray`, seule la phase de dessin est invalidée (pas de recomposition par frame).
- Compteur de fps affiché en bas à gauche.

## Commandes
- Mobile : glisser le doigt n'importe où (joystick flottant).
- Clavier : ZQSD / WASD / flèches. Espace ou Entrée pour lancer une partie.

## Lancer
- Android : `./gradlew :composeApp:installRelease` (téléphone branché, débogage USB activé)
  ou ouvrir le projet dans Android Studio.
- Desktop : `./gradlew :composeApp:run`
- iOS : `cd iosApp && xcodegen && open iosApp.xcodeproj` (installer XcodeGen avec `brew install xcodegen`).

## Graphismes 3D
Les modèles low-poly (roi à cheval, archers, chevaliers, boss, château, maison, tour, palissade,
arbres, rochers, pièces) sont construits en Three.js dans `art/models.js`, puis rendus en atlas de
sprites isométriques (8 directions, animations) par `art/render.mjs` :

    cd art && npm install && node render.mjs

Le script écrit les PNG dans `composeApp/src/commonMain/composeResources/drawable/` et génère
`SpriteData.kt`. `node preview.mjs out.png` compose une scène d'aperçu.
