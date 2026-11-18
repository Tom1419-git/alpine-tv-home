# Accueil « fenêtre alpine »

Un écran d'accueil de plus pour le lanceur : une fenêtre ouverte sur le Riffelsee et le Cervin, qui suit la lumière
du soleil et la météo de Lausanne, avec le morceau en cours de lecture. Le dock, les apps et la disposition ne
changent pas. Réglage « Accueil météo » désactivé : l'écran d'accueil d'origine est rendu tel quel.

## Ce que ça fait

| Élément | Source | Réseau |
|---|---|---|
| Phase du jour : huit moments, lumière continue | Lever et coucher calculés sur la TV (équations NOAA), position du soleil et de la lune réelles | Aucun |
| Condition météo : dégagé, nuageux, brouillard, bruine, pluie, neige, orage | Open-Meteo, sans clé, un appel au plus toutes les 30 minutes, cache disque | Un seul appel, `api.open-meteo.com` |
| Photo : Riffelsee, Cervin, une seule image étalonnée en continu, avec rive et montagnes devant l'heure | `weather_pack.zip`, transféré à côté de l'APK | Aucun |
| Musique : titre, artiste, pochette, commandes | Sessions média d'Android (`MediaSessionManager`), sans compte ni SDK | Aucun |

Sans le pack photo, c'est la scène dessinée qui s'affiche (même disposition, même heure, mêmes calques météo).
Sans accès aux notifications, le bloc musique est absent et tout le reste fonctionne.

## Réglages

Réglages du lanceur, ligne « Accueil alpin » : accueil météo, ville (nom, latitude, longitude, Lausanne par
défaut), unité °C ou °F, mouvement, nuit noire OLED, aperçu (phase et météo forcées), scène photo, état du pack,
bloc musique, accès aux notifications, crédits photo. Le format de l'heure suit le réglage existant `clock_format`.

## Aperçu depuis adb

```bash
adb shell am start -n com.gathin.tvhome/.MainActivity --es phase coucher --es weather rain
adb shell am start -n com.gathin.tvhome/.MainActivity --es clock 20:52     # fige l'heure pour juger la typographie
adb exec-out screencap -p > /tmp/dusk.png
```

Phases : `avant-lever`, `lever`, `matin`, `midi`, `apres-midi`, `coucher`, `crepuscule`, `nuit` (et les anciens `dawn`, `day`, `dusk`, `night`). Météo : `clear`, `partly`, `overcast`, `fog`, `drizzle`, `rain`, `snow`,
`storm`. Sans extra, c'est la valeur réelle (ou l'aperçu choisi dans les réglages) qui s'affiche.

## Le pack photo

```bash
python3 -m venv tools/.venv && tools/.venv/bin/pip install -r tools/requirements-weather.txt
tools/.venv/bin/python tools/fetch_weather_masters.py            # les originaux Wikimedia, a leur resolution native
tools/.venv/bin/python tools/prepare_weather_scene.py \
    --master themes/weather/masters-native/riffelsee-lever.jpg \
    --out build/weather --pack themes/weather/weather_pack.zip
```

Les originaux ne sont **jamais** des miniatures : `fetch_weather_masters.py` lit l'URL du fichier original, controle la
licence attendue, les dimensions et l'empreinte SHA-1 de Commons, et note tout dans `themes/weather/sources.json`. Les
masters natifs (6000x4000 pour le Riffelsee) restent dans `themes/weather/masters-native/`, ignore par git, et ne sont
jamais modifies. L'outil recadre dans le master (jamais dans un fichier reduit), reduit une seule fois vers 3840x2160 puis
1920x1080, **n'agrandit jamais** (un recadrage plus petit que l'UHD est refuse ou marque plafonne), detoure, etalonne et
**echoue** si la crete est sale ou si le masque contient le reflet.

Le pack est **une seule photo** (la lumière des huit moments est calculée en continu par l'application) : il contient `photo.jpg` (3840x2160) et `photo_1080.jpg` (JPEG 95, chrominance 4:4:4), `foreground.png`
et `foreground_1080.png` (PNG sans perte). Resolution et poids de chaque fichier : [fichiers-livres.md](fichiers-livres.md).
L'application lit le jeu 1080p sur une surface 1080p et l'UHD sur une surface 4K, avec deux bitmaps au plus et un plafond
de memoire de 56 Mo pour le couple photo et premier plan.

`launcher/build-and-install.sh` transfere `themes/weather/weather_pack.zip` s'il existe ; le pack n'est jamais dans l'APK.
Pour remplacer un `foreground.png` a la main, voir la fin de `themes/weather/CREDITS.md`.

Au demarrage de Home, le pack est verifie en arriere-plan (taille et SHA-256 de chaque image, resultat garde tant que le
fichier ne change pas). Pack absent, invalide ou d'un ancien format : scene dessinee, sans erreur.

## Interface de comparaison

Une page web locale, sans reseau, pour comparer toutes les variantes avant de choisir : six photos, huit phases, huit meteos,
trois mises en page (A, B, C) et trois etats (repos, application selectionnee, bloc musique).

```bash
tools/.venv/bin/python tools/fetch_weather_masters.py            # une fois
tools/.venv/bin/python tools/build_alpine_preview.py --docs       # fabrique build/accueil-alpin-preview/ (environ 25 s)
open build/accueil-alpin-preview/index.html                       # double-clic : aucun serveur n'est necessaire
```

Avec un petit serveur local : `tools/.venv/bin/python tools/build_alpine_preview.py --serve` puis `http://127.0.0.1:8765/`.
Dossier de sortie : `build/accueil-alpin-preview/` (198 Mo, non versionne). Lien direct vers un plein ecran :
`index.html?tv=photo|phase|meteo|disposition|etat&res=uhd`, par exemple
`index.html?tv=riffelsee-lever|nuit|clair|A|focus&res=uhd`.

Vues : grille filtrable (photo, phase, meteo, mise en page, etat) ; comparaison de 2 a 4 versions avec zoom commun,
defilement synchronise et superposition en opacite ; plein ecran TV en 1080p ou en UHD 3840x2160 a un pixel d'image pour un
pixel d'ecran ; favoris et un resume texte a coller. Chaque apercu dit s'il est calcule avec la geometrie de l'application ou
approche, sa taille de rendu et la resolution du fichier source ; les points a noter d'une mise en page (bloc musique qui
recouvre le dock, chiffres tres masques, dock serre) sont affiches au lieu d'etre caches.

La geometrie vient de l'application et de la capture de la TV (`tools/alpine_preview/layout.js`) ; les tests
`tests/test_alpine_preview.py` la comparent au code Java et aux mesures de la TV. Auto-test dans un navigateur (a servir en
http) : `index.html?selftest`.

## Musique

Le bloc n'affiche que de la musique : liste blanche `com.spotify.music`, `com.spotify.tv.android` (le paquet de la Bravia TL2), `com.plexapp.android`, `org.videolan.vlc`,
et une session avec un artiste renseigné (une vidéo n'en a pas). Si la session au premier plan appartient à une autre
application, il reste caché. Accès aux notifications, une fois, depuis les réglages ou en adb :

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/.MusicListener
adb shell dumpsys media_session | grep -i -A 8 spotify     # vérifier que Spotify publie une session
```

## Quand une autre application est devant

À `onPause`, l'accueil arrête le rendu, les animations, les minuteurs et tout appel réseau, se désabonne des
sessions média et rend les bitmaps de la photo. Au retour, la phase est recalculée pour l'heure courante et la météo
n'est rappelée que si le cache a plus de 30 minutes.

## Rapports et fichiers

[rapport-J1](rapport-J1.md), [rapport-J2](rapport-J2.md), [rapport-J3](rapport-J3.md), [rapport-final](rapport-final.md), [rapport-comparaison](rapport-comparaison.md) (qualite d'image UHD et interface de comparaison), [fichiers-livres](fichiers-livres.md) (resolution, poids et provenance de chaque fichier).

## Licences

Le code reste sous licence MIT. Les images dérivées des photos de Deralpinbergsteiger restent sous
**CC BY-SA 4.0** (`themes/weather/CREDITS.md`). L'attribution est atteignable dans l'application : Réglages du
lanceur, Accueil alpin, « Crédits photo ». Météo : Open-Meteo.com, CC BY 4.0.
