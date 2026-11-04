# Hexcrown — passe de finition « incroyable » (demande de Mathis, 2026-11-04 18:29)

Référence pour la v1.2 « Polish ». Règles : tout ce qui bouge suit `art/ui/DA-v2.md` (expoOut, 0,97 à la pression,
une scène signée par écran, mouvement réduit = fondus seulement) ; tout ce qui frappe suit `art/fx/FX.md`. Rien de
nouveau ne doit coûter plus de 0,5 ms par image sur le Pixel 8a. Chaque passe est une branche, mergée avec captures.

## Passe A — Feel de jeu (branche polish/feel)
1. **Caméra** : avance de 40 px dans la direction du roi (lissée 300 ms), recul de 8 % à l'entrée d'un boss
   (600 ms) et retour à sa mort, secousse directionnelle (vers l'impact, pas aléatoire), micro-secousse 2 px
   au ramassage de 10 pièces d'affilée. Jamais plus de 2 secousses cumulées.
2. **Impacts** : hit-stop par type (archer 20 ms, roi 40 ms, boss 90 ms), squash des ennemis 1,2/0,85 → 1 en
   140 ms, chiffres de dégâts empilés (un même ennemi touché 3× en 300 ms = un seul chiffre qui grossit),
   critiques en or 1,4× avec `fx_spark`. Mort : les ennemis tombent avec rotation 15° + fondu 400 ms, pièces
   éjectées en arc (vz 260 px/s, gravité) avant l'aimant.
3. **Pièces** : « tic » dont la hauteur monte de 2 % par pièce (reset 400 ms), compteur HUD qui tween (jamais de
   saut), pulse de la pilule +6 % à l'arrivée. Dépense : les pièces partent du haut de la pile sur la tête du roi vers le chantier,
   le chantier « respire » 1,05 et sa jauge se remplit en 250 ms.
4. **Vagues** : bandeau « Vague N » qui glisse de 12 dp + fondu, 1,2 s, puis « Dernier ennemi ! » quand il en
   reste 1 (contour or pulsé sur lui). Fin de vague : bilan 2 s (pièces, kills, combo max, hex tenus) qui n'empêche
   jamais le tap. Vignette rouge pulsée quand le donjon < 30 % (1 Hz, alpha 0,25) + battement sourd.
5. **Roi** : traînée de poussière aux pieds de la monture en mouvement (`fx_puff` 0,5, toutes les 120 ms), inclinaison
   de 4° dans les virages, charge avec ombre étirée, halo de classe sous le roi la nuit (torche portée).
6. **Boss** : entrée = sol qui tremble 400 ms + bandeau nom + jauge qui se remplit en 600 ms ; mort = ralenti 0,3×
   sur 500 ms, flash blanc, pluie de 30 pièces, titre « BOSS VAINCU ! », son BOSS_DOWN ; jauge qui se vide par
   paliers avec clignotement blanc à 50 % et 25 %.
7. **Capacité** : bouton qui « charge » visuellement (arc + pointe lumineuse, déjà), vibration à 100 % prêt,
   lueur sur le bouton 1 s ; pendant la recharge, icône désaturée.

## Passe B — Écrans et transitions (branche polish/screens)
1. **Lancement** : splash 600 ms = badge hexagone sur bleu nuit, lueur, puis fondu vers le menu (le fond du
   héros est déjà visible dessous). Jamais plus de 1 s avant le menu.
2. **Menu → partie** : fondu noir 180 ms + portrait du héros qui glisse ; **Partie → menu** : fondu 180 ms.
3. **Changement de niveau** : la caméra part du donjon, pan 1,2 s (expoOut) vers le nouveau déblocage (chantier,
   route), bandeau « Chapitre II — Les Collines », puis retour au roi ; tap = sauter.
4. **Victoire** : étoiles qui tombent une par une (120 ms d'écart, son STAR), pièces qui comptent (tween 800 ms),
   coffre qui s'ouvre si boss, bouton « Niveau suivant » or ; **Défaite** : fondu gris, « Le donjon est tombé »,
   bilan (vague, kills, pièces gardées 50 %), bouton « Réessayer » or + lien « Menu », une astuce contextuelle
   (« Plante une bannière près du donjon », « Les murs se réparent avec le Bâtisseur »).
5. **Panneaux** (boutique, talents, missions, stats, réglages, reliques) : entrée 0,96 → 1 + alpha 220 ms,
   listes décalées 40 ms, onglets avec soulignement qui glisse, nombres qui tweenent, boutons désactivés à 40 %.
6. **Royaume** : fondu d'entrée depuis la mini-carte (zoom 0,9 → 1), couronne de région = fanfare + confettis
   `fx_confetti` 80 particules + anneau or `fx_ring` sur la carte + titre, mini-carte qui s'anime (hex qui se
   dore en 400 ms).
7. **Toasts et bulles** : un seul style (verre, 56 dp), file d'attente, jamais sur le HUD du haut, jamais pendant
   un boss ; bulles du tutoriel avec flèche animée (2 px, 1 Hz).

## Passe C — Réglages, QA, store (branche polish/qa)
1. **Réglages** : Langue, Sons, Sons complets, Musique, Vibrations, Mouvement réduit, Afficher les fps, Rejouer le
   tutoriel, Taille du texte (normal / grand = ×1,15), Crédits (CC0 : KayKit, Kenney, OpenGameArt, polices OFL),
   Confidentialité (ouvre docs/privacy), Version + versionCode. Tout persistant.
2. **Interruptions** : appel / arrière-plan = pause automatique + sauvegarde ; reprise sur l'écran de pause ;
   bouton retour Android = pause en jeu, retour dans les panneaux, double-retour pour quitter au menu
   (toast « Encore une fois pour quitter »). Rotation bloquée portrait ; safe areas haut/bas sur tous les écrans
   (testé en 20:9 et 16:9, 360 / 411 / 480 dp).
3. **Sauvegarde** : à chaque vague et à chaque changement (achat, talent, relique), atomique (écrire puis
   renommer), version de schéma + migration, backup `game_prev` ; test : tuer le process pendant une vague.
4. **Perf** : 0 allocation par image (update + render), pool de particules, p95 ≤ 12 ms sur le Pixel 8a (mesure
   après « go ») ; option « Économie de batterie » = 60 fps ; mémoire < 450 Mo ; démarrage à froid < 2 s.
5. **Accessibilité** : textes ≥ 13 dp, contraste ≥ 4,5 sur tous les boutons, zones tactiles ≥ 48 dp, mouvement
   réduit respecté partout, pas d'information portée par la couleur seule (icône + texte).
6. **Store** : captures retakées sur le Pixel (6, FR et EN, sans fps), fiche `docs/store/listing.md` relue,
   `docs/privacy.md` servie (GitHub Pages à activer par Mathis : Settings → Pages → branche main, dossier /docs),
   AAB final versionName 1.2.0, test d'installation à froid sur un profil vierge (pas de crash au premier
   lancement, tutoriel visible).

## Nouveaux assets (côté art, déjà prévus)
`fx_confetti` (6 images, rubans colorés), `fx_star` (étoile de victoire), `fx_snow` / `fx_rain_splash`, son
BOSS_DOWN, STAR, CROWN (fanfare courte), TAB, SWIPE, musique `realm` (exploration calme, boucle).
