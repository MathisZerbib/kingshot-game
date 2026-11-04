# Audio credits

All third-party audio below is **CC0 1.0 (public domain)**: free for any use, no attribution required. We credit the authors anyway.

## Music (OpenGameArt.org)

| Game file | Track | Author | Source |
|---|---|---|---|
| `files/music/menu.m4a` | Medieval: The Old Tower Inn | RandomMind | https://opengameart.org/content/medieval-the-old-tower-inn |
| `files/music/battle.m4a` | Medieval: Battle | RandomMind | https://opengameart.org/content/medieval-battle |
| `files/music/boss.m4a` | War Theme (ver 2) | Spring Spring | https://opengameart.org/content/war-theme |

Converted to AAC 128 kb/s, loudness normalised to -18 LUFS.

## Sound effects (recorded CC0 sources, built by art/audio/make_sfx_natural.py)

Since v1.2 every effect in `files/sfx/` is assembled from real recordings (no oscillators), layered, filtered and loudness-matched by `art/audio/make_sfx_natural.py`. The per-file source list is generated in [`sfx_sources.md`](sfx_sources.md).

| Library | Author | Licence | Used for |
|---|---|---|---|
| CC0 Public Domain Sounds collection (Kenney RPG Audio / Impact Sounds / Interface Sounds / UI Audio, 80 CC0 RPG SFX, 80 CC0 creature SFX 1 & 2, 100 CC0 wood & metal SFX, 75 CC0 breaking-falling-hit SFX, 40 CC0 water-splash-slime SFX, 30 CC0 SFX loops, Micro Pack Organic Wooshes, Micro Pack Record Fuzzies) | Kenney Vleugels (https://kenney.nl), rubberduck, Lavender (https://github.com/lavenderdotpet/CC0-Public-Domain-Sounds) and the pack authors listed there | CC0 1.0 | impacts, wood, cloth, coins, doors, creatures, whooshes, rain |
| Versilian Community Sample Library (VCSL) | Versilian Studios, Samuel Gossner and contributors (https://github.com/sgossner/VCSL) | CC0 1.0 | glockenspiel, tubular bells, hand chimes, mark tree, bell tree, finger cymbals, clash and suspended cymbals, anvil, timpani, bass drum, frame drum, concert and folk harps, bass recorder, didgeridoo |

Converted to mono 22 050 Hz 16-bit WAV, leading silence trimmed, loudest 50 ms window normalised per sound.

## Généré (art/audio/gen_sfx.py)

`files/music/realm.m4a` (exploration du Royaume) est encore synthétisé par le script, sans source tierce. Les anciens effets synthétisés de `gen_sfx.py` sont remplacés par les enregistrements ci-dessus.
