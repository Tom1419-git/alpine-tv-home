# Rapport J2 : la photo en calques

> Mise a jour : le pack decrit ici (1920x1080, 15,1 Mo) a ete remplace par un pack UHD 3840x2160 avec jeu de secours 1080p, voir [rapport-comparaison.md](rapport-comparaison.md) et [fichiers-livres.md](fichiers-livres.md). Les masters 2560x1440 ont ete remplaces par les masters natifs.

## Ce qui est fait

- **Photos** : les deux originaux de Deralpinbergsteiger (CC BY-SA 4.0) ont été téléchargés depuis Wikimedia Commons
  (6000×4000, 20,4 Mo et 21,6 Mo, métadonnées vérifiées : auteur, licence, dimensions). `themes/weather/CREDITS.md`
  liste titre, auteur, licence, liens et chaque modification de chaque fichier.
- `tools/prepare_weather_scene.py` :
  - recadre en 16:9 autour du Cervin et pose la ligne d'eau à 56 % de la hauteur ;
  - détoure la rive et les montagnes au-dessus de la ligne d'eau (propagation depuis le ciel, crête recalée sur
    l'arête réelle, filtre médian, bord doux de 3 px) avec une retouche manuelle à la main de la neige du glacier de
    l'aube qui se confond avec le ciel ;
  - fabrique `dawn`, `dusk` (telles quelles), `day` (étalonnage neutre de l'aube) et `night` (étalonnage du
    crépuscule, étoiles discrètes dans le ciel seulement, aucun astre dessiné) ;
  - **échoue bruyamment** si le masque contient le reflet (« le masque contient de l'eau ou le reflet »), si la crête
    est sale, en dents de scie ou frangée de ciel, ou si le masque est vide ; `--verify` et `--repack` permettent de
    remplacer un `foreground.png` à la main ;
  - écrit `manifest.json` (version, ligne d'eau, taille et SHA-256 de chaque image, crédits) et `weather_pack.zip`.
- **Pack** : `themes/weather/weather_pack.zip`, 8 images (4 phases × `photo.jpg` + `foreground.png`), **15,1 Mo**
  (cible : environ 20 Mo), 1920×1080, JPEG 85. Masters 2560×1440 de l'aube et du crépuscule dans
  `themes/weather/masters/`. Jamais dans l'APK : transféré par `launcher/build-and-install.sh` (et `.ps1`) comme
  `theme_catalog.zip`, par `ThemeProvider` (`weather_pack.zip` ajouté à la liste des fichiers autorisés).
- `WeatherPackManifest`, `WeatherPack` : manifeste validé (version, ligne d'eau plausible, chemins stricts, quatre phases
  complètes), intégrité vérifiée avant tout usage (taille et SHA-256 de chaque image, résultat gardé tant que le
  fichier ne change pas). Pack absent, tronqué ou invalide : scène dessinée, sans plantage.
- `PhotoBackdrop` : `photo.jpg` sous l'heure, `foreground.png` par-dessus (la rive et le Cervin passent devant le bas
  des chiffres), teinte selon la météo, calques météo par-dessus, **deux bitmaps au plus en mémoire**, décodés hors du
  fil de l'interface avec `inSampleSize` calculé sur la largeur réelle de la surface, rendus dès que l'accueil passe
  derrière une autre application. Sans `foreground.png` : l'heure passe au-dessus avec un léger voile sombre. Image
  illisible : retour automatique sur la scène dessinée.
- Réglages : scène photo (retour possible à la scène dessinée), état du pack (vérification, installé, absent, invalide),
  crédits photo.

## Choix et écarts par rapport à la mission

1. **Le Cervin n'est pas au centre des originaux** (à environ 33 % de la largeur à l'aube, 67 % au crépuscule) et la
   ligne d'eau est à 52 % puis 68 % de la hauteur, contrairement à ce que dit la section 4.2. Le recadrage 16:9 pleine
   largeur aurait laissé l'horloge centrée sur une roche. Le recadrage est donc plus serré (3900 px sur 6000) pour
   ramener le pic au milieu et la ligne d'eau à 56 %.
2. **Le détourage prend aussi le pic et les flancs** : dans ce cadrage, ils passent devant les chiffres, ce qui est
   l'effet voulu. Mais la roche couvre toute la zone de l'horloge : à 55 % de chiffres cachés l'heure devenait
   illisible sur un aperçu. Le terrain ne cache donc que le bas des chiffres (30 % de leur hauteur, avec un fondu doux
   plutôt qu'une coupe nette) ; au-dessus, les chiffres passent devant la roche. C'est un compromis de lisibilité,
   assumé.
3. **Largeur de l'horloge 50 %** de l'écran (mission : « très large »). Un test vérifie que Java et l'outil
   utilisent la même valeur.
4. **Le fondu de phase est un fondu par le noir** (1 s pour s'éteindre, 1 s pour réapparaître), pas un fondu enchaîné :
   un fondu enchaîné demanderait quatre bitmaps en mémoire pendant 2 s, et la mission en limite le nombre à deux.
5. Pas de lune dessinée sur la photo de nuit (section 12) : le calque de nuit ne contient que des étoiles.
6. Le contrôle de frange du masque est une heuristique (voir le commentaire dans l'outil) : sur ces photos 8 à 15 % des
   colonnes ressemblent à du ciel parce que la neige est très claire, et le seuil est à 25 %. Les arêtes ont été
   relues à l'œil sur des agrandissements ; quelques fines taches de brume claire restent sur les crêtes.

## Tests

`tests/test_weather_scene.py` (27 tests, sautés sans Pillow et numpy) : recadrage, accord Java et outil sur la largeur
de l'horloge et la ligne d'eau, masque sur scène synthétique, **refus d'un masque contenant le reflet**, crête sale,
crête en dents de scie, masque décalé dans le ciel, retouche manuelle, filtre médian, lisibilité sous l'horloge,
étalonnage, étoiles dans le ciel seulement, manifeste, zip, crédits. JVM : `WeatherPackManifestTest`, `PhotoPlanTest`
(choix photo et masque pour chacun des 4 × 8 couples phase et condition, repli sans pack, sans premier plan, pack
désactivé), manifeste tronqué ou hors liste, taille d'échantillonnage.

## Taille et mémoire

| | Octets |
|---|---|
| APK, fin de J1 | 612 357 |
| APK, fin de J2 | 619 677 (+7 320 pour J2, +30 088 depuis le départ) |

La croissance de l'APK est bien sous 1 Mo. La mémoire à l'exécution (deux bitmaps : photo en RGB_565 à 1920×1080
soit 4,1 Mo, premier plan en ARGB_8888 soit 8,3 Mo) est calculée, **pas mesurée** : la TV n'est pas joignable.
Captures des quatre phases : seul l'aperçu fabriqué par l'outil (photo, premier plan et une horloge de même géométrie)
a été relu ; les captures de la TV ne sont pas faites.
