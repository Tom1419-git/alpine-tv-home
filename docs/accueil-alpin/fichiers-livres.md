# Fichiers livres : resolution, poids, provenance

Genere par `tools/.venv/bin/python tools/build_alpine_preview.py --docs` a partir des fichiers reellement produits ; rien n'est ecrit a la main. Licence et modifications : `themes/weather/CREDITS.md`.

## Photo unique : master natif (themes/weather/masters-native/, ignore par git, jamais modifie)

| Photo | Resolution native | Poids | Octets par pixel | Licence | Original (SHA-256) |
|---|---|---|---|---|---|
| Matterhorn reflection in the Riffelsee at sunrise.jpg | 6000x4000 | 20.4 Mo | 0.85 | CC BY-SA 4.0 | `9da0b44bb5653723...` |

## Pack livre a l'application (themes/weather/weather_pack.zip, hors APK)

Manifeste version 3 ; total des fichiers 11.5 Mo, zip non compresse (JPEG et PNG le sont deja). Recadrage natif 3900x2194, jamais agrandi. Photos : JPEG qualite 95, chrominance 4:4:4. Premiers plans : PNG sans perte. Les fichiers `_1080` sont le jeu de secours lu par une surface 1080p ; l'UHD est lu par une surface 4K.

| Fichier | Pixels | Poids (Mo) | SHA-256 |
|---|---|---|---|
| `foreground.png` | 3840x2160 | 5.1 | `11b8462864977171...` |
| `foreground_1080.png` | 1920x1080 | 1.2 | `efc12c14c8c6b0e9...` |
| `photo.jpg` | 3840x2160 | 4.3 | `264a43491f94d065...` |
| `photo_1080.jpg` | 1920x1080 | 0.9 | `9d26cb290ba34e10...` |

Archive `themes/weather/weather_pack.zip` : 11562310 octets (11.6 Mo).
APK de release : 643405 octets (limite du CI : 737 280).
