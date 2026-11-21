# Crédits des images de l'accueil alpin

Toutes les images dérivées de ce dossier, du pack `weather_pack.zip` et de l'interface de comparaison viennent d'**une
seule photographie** publiée sur Wikimedia Commons. **Les images dérivées gardent la licence de la photo** : CC BY-SA 4.0.
Le code du lanceur reste sous licence MIT.

Licence : [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

Cette attribution est atteignable depuis l'application : Réglages du lanceur, Accueil alpin, « Crédits photo ».

## Source

L'original est téléchargé par `tools/fetch_weather_masters.py` (jamais une miniature), à sa résolution native, dans
`themes/weather/masters-native/` (ignoré par git, 20 Mo). Son empreinte SHA-256, son poids et sa résolution sont dans
[`sources.json`](sources.json), qui est versionné ; le script contrôle la licence attendue, les dimensions et l'empreinte
SHA-1 publiée par Commons avant d'écrire quoi que ce soit.

| Photo | Titre sur Commons | Auteur | Licence | Original | Fiche |
|---|---|---|---|---|---|
| Riffelsee, Cervin au lever du soleil | `File:Matterhorn reflection in the Riffelsee at sunrise.jpg` | Deralpinbergsteiger | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | 6000×4000, 20.4 Mo | https://commons.wikimedia.org/wiki/File:Matterhorn_reflection_in_the_Riffelsee_at_sunrise.jpg |

## Modifications

Toutes sont faites par `tools/prepare_weather_scene.py` (pack), à partir du master natif, jamais d'un fichier déjà réduit :

- **Recadrage 16:9** dans le master natif (3900×2194 sur 6000×4000), sans jamais agrandir : le Cervin, à un tiers de la
  largeur dans la photo, est ramené au milieu, avec la ligne d'eau à 56 % de la hauteur.
- **Réduction** (Lanczos) à 3840×2160 et à 1920×1080 ; jamais d'agrandissement. JPEG qualité 95, chrominance 4:4:4.
- **Détourage du premier plan** (`foreground*.png`, PNG sans perte) : le terrain au-dessus de la ligne d'horloge, jamais le
  reflet ; la crête est calculée puis recalée sur l'arête réelle, et retouchée à la main là où la neige se confond avec le
  ciel. Le bas des chiffres de l'horloge passe derrière un fondu de la rive.
- **Étalonnage** : fait **par l'application, à l'affichage**, pas dans les fichiers : luminosité, contraste, saturation,
  dominante de couleur et voile atmosphérique, calculés en continu d'après la hauteur du soleil, les nuages, le brouillard,
  la pluie, la neige et l'orage, sans flou, sans accentuation ni débruitage. Les fichiers du pack sont donc la photo non
  étalonnée. Étoiles, lune, soleil, nuages, brume et météo sont dessinés par l'application ; aucun n'est dans la photo.

## Seconde photo : Lago di Braies

Photographie : Eric Kilby, « Lago di Braies - Morning », Wikimedia Commons, licence **CC BY-SA 2.0**
(https://creativecommons.org/licenses/by-sa/2.0/), https://commons.wikimedia.org/wiki/File:Lago_di_Braies_-_Morning.jpg.

Mêmes modifications que la première : recadrage 16:9 (8000×4500 dans le master 8213×5475, sans agrandissement), réduction à
3840×2160 et 1920×1080, détourage du terrain pour le calque de premier plan, étalonnage fait par l'application. Les images
modifiées restent sous CC BY-SA 2.0. Pack : `weather_pack_braies.zip`, choisi dans les réglages de l'accueil alpin.

## Fichiers du pack livré

| Fichier | Source | Modifications |
|---|---|---|
| `photo.jpg`, `photo_1080.jpg` | Riffelsee, lever du soleil | Recadrage, réduction (3840×2160 et 1920×1080) |
| `foreground.png`, `foreground_1080.png` | Riffelsee, lever du soleil | Détourage du terrain, fondu sous l'horloge |

Résolution et poids exacts de chaque fichier : `docs/accueil-alpin/fichiers-livres.md`.

## Données météo

Weather data by Open-Meteo.com, licence CC BY 4.0, https://open-meteo.com/.

## Remplacer un foreground.png à la main

Éditer `foreground.png` (3840×2160) et `foreground_1080.png` (1920×1080) dans le dossier généré (RGBA, terrain opaque, ciel
et eau transparents), puis refaire le pack :
`tools/.venv/bin/python tools/prepare_weather_scene.py --repack <dossier> --pack themes/weather/weather_pack.zip`.
L'outil refuse un fichier qui contient le reflet ou dont la crête est sale.
