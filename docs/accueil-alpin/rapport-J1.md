# Rapport J1 : le socle (scène dessinée, aucune photo, aucun Spotify)

## Point de départ, mesuré

| Mesure | Valeur |
|---|---|
| `minSdk` réel (`launcher/app/build.gradle`) | 26 (Android 8.0) ; `targetSdk` 34 |
| APK de départ, `assembleRelease` | 589 589 octets (le seuil du CI est 589 824 : il restait 235 octets) |
| Tests JVM de départ | verts |
| `dumpsys meminfo com.gathin.tvhome` | **non mesuré : la TV n'est pas joignable en adb** (voir plus bas) |

## Ce qui est fait

- `SolarPhase` : phases aube / jour / crépuscule / nuit calculées sur la TV (équations NOAA, via `Sun`), bornes
  exactes de la mission (aube de 40 min avant le lever à 50 min après, crépuscule de 50 min avant le coucher à 40 min
  après), repli fixe sans coordonnées (06:20, 07:50, 18:10, 19:40). Position du soleil (NOAA) et de la lune (éphéméride
  approchée) pour la scène.
- `WeatherCodes`, `WeatherData`, `WeatherRepository` : table WMO complète vers 8 conditions, code inconnu vers
  « couvert », parsing tolérant (réponse vide, JSON invalide, cache tronqué : `null`, jamais d'exception), cache
  disque écrit de façon atomique, un appel au plus toutes les 30 minutes, délai de 6 s, aucune nouvelle tentative.
- `HomeState` immuable ; interface `Backdrop` ; `DrawnAlpineBackdrop` (ciel dégradé, soleil et lune à leur position
  réelle, quatre plans de montagne dont le Cervin, brume, nuages colorés par la lumière, lac avec reflet et
  frémissement, étoiles, nuit noire OLED) ; `WeatherLayer` (pluie en traînées fines inclinées, neige lente, nappe de
  brouillard, orage sombre avec un éclair toutes les 30 à 60 s au plus et jamais de flash plein écran, rien dans le
  rectangle de l'heure) ; `AlpineHomeView`, vue unique qui dessine fond, heure et date, rive, météo et un voile sous
  le dock ; le dock, les pilules et les rangées restent des vues Android au-dessus.
- Contraste automatique : `TextContrast` mesure la luminance de la zone des chiffres (texte sombre sur ciel clair,
  clair avec halo sinon, et zone mixte ciel clair et roche sombre traitée comme sombre).
- Heure : réutilise `clock_format` ; OLED : décalage de quelques pixels par minute ; aucune allocation dans la boucle
  de rendu (formes, dégradés et textes préparés une fois par changement d'état).
- Changement de phase : fondu de 2 s (une seconde pour s'éteindre, une pour réapparaître).
- Réglages « Accueil alpin » (FR et EN, plus l'hébreu que les tests du dépôt exigent) : accueil météo, ville (nom,
  latitude, longitude, Lausanne par défaut), unité, mouvement, nuit noire OLED, aperçu. Aperçu en adb :
  `--es phase dusk --es weather rain --es clock 20:52`.
- `INTERNET` ajouté au manifeste : le seul appel est `api.open-meteo.com`.

## Tests

`SolarPhaseTest`, `WeatherCodesTest`, `WeatherDataTest`, `HomeStateTest`, `TextContrastTest` : lever et coucher à
Lausanne le 17/11/2026 (07:38 et 16:58, tolérance 4 min), bornes de phase exactes (06:57 nuit, 06:58 aube, 08:28 jour,
16:08 crépuscule, 17:38 nuit), repli sans coordonnées, coordonnées invalides, table WMO complète, code inconnu,
réponse Open-Meteo réelle du 17/11/2026, réponses vide / invalide / tronquée, cache tronqué ou corrompu, âge du cache,
format 12 h et 24 h, unités, contraste. Les 14 thèmes et le catalogue (`ThemeFilesTest`, `test_theme_catalog.py`)
restent verts.

## Taille de l'APK

| | Octets |
|---|---|
| Départ | 589 589 |
| Fin de J1 | 612 357 (+22 768) |

**Écart** : le seuil du CI (589 824 octets, `AGENTS.md`) est dépassé de 22,5 Ko. La mission demande une croissance
sous 1 Mo et interdit de toucher au CI ; l'écart est donc assumé et signalé ici. L'APK a été construite par
`tools/docker-build.sh` (image amd64, voir le rapport final).

## Ce qui n'a pas pu être fait

La TV ne répond pas en adb (port 5555 fermé sur tous les appareils du réseau, deux tentatives espacées de plus de 30
minutes). Conformément à la section 13.4 de la mission, rien n'a été installé. Donc, pour J1 :

- mémoire au repos, CPU au repos, cadence réelle : **non mesurés** ;
- captures par phase de la scène dessinée : **non faites** (pas d'écran) ;
- le plafond de 30 images par seconde est appliqué d'office (`AlpineHomeView.FRAME_MS`), faute de mesure du CPU ;
- test sans réseau et sans coordonnées : couvert par les tests JVM (parsing, repli, cache) mais pas sur la TV.
