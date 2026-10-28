# Kingshot — le vrai jeu, sans pub

Jeu mobile inspiré des pubs Kingshot : un roi à cheval mène une escouade d'archers, ramasse l'or
des chevaliers rouges, le dépose sur les chantiers (archers, tours, palissades, forge) et défend
son donjon contre des vagues de plus en plus dures, avec un boss toutes les 5 vagues.

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
