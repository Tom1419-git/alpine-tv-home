# Rapport : photo unique, lumiere continue, lisibilite garantie, interface de comparaison

Phase entierement locale : **aucune connexion a la TV** (ni ADB, ni scan reseau, ni `dumpsys`, ni installation). Rien de ce qui
suit n'a ete installe ni mesure sur la TV.

## Ce qui a change

- **Une seule photo** : Riffelsee, Cervin au lever du soleil (Deralpinbergsteiger, CC BY-SA 4.0). Les autres candidates
  (Riffelsee au coucher, Stellisee, Tre Cime, Oeschinensee) sont retirees de l'interface, du pack, du manifeste, des credits
  et de l'outil de telechargement.
- **Mise en page B** (validee) : horloge, date, salutation et pilules remontees, dock en deux rangees.
- **Lumiere continue** : la couleur de la photo est calculee a l'affichage d'apres la hauteur du soleil, les nuages, le
  brouillard, la pluie, la neige et l'orage (`SceneGrade`), au lieu de photos differentes par phase.
- **Lisibilite garantie** par un modele de contraste par zone (`ContrastPlan`, `SceneZones`) et un voile local borne.

## Position de l'horloge et des pilules, avant et apres (1920x1080)

| Element | Avant (A) | Apres (B) | Ecart |
|---|---|---|---|
| Ligne de base de l'heure | 618 px | 566 px | 52 px plus haut |
| Haut des chiffres | 385 px | 297 px | plus grands (58 % de la largeur au lieu de 50 %) |
| Date (ligne de base) | 331 px | 233 px | 99 px plus haut |
| Salutation (ligne de base) | 282 px | 183 px | 99 px plus haut |
| Pilules d'etat, bord haut | 118 px | 64 px | 54 px plus haut |
| Premiere rangee du dock (haut des cartes) | 723 px | 612 px | 111 px plus haut |
| Deuxieme rangee du dock | coupee par le bord de l'ecran | 856 a 1026 px | visible, avec la marge basse de 5 % |
| Bord de la rive sur les chiffres | 30 % | 22 % | plus de chiffre visible |

Controles (testes, `tests/test_alpine_preview.py`) : la salutation reste sous le bas des pilules, l'heure reste a plus de
20 px du dock, la derniere rangee finit a 1026 px (marge basse de 5 %), les cartes restent entre 187 et 1733 px (marge de 5 % : 96 px), les deux
rangees ne se chevauchent pas, cartes de 303 x 170 px. La photo n'est ni etiree ni decalee : la geometrie de l'image ne change pas.

## Dock

Premiere rangee : les quatre applications actuelles (yallo TV, CANAL+, sky, YouTube), inchangees. Deuxieme rangee juste
dessous : les autres applications, a 72 % d'opacite (plus discretes), meme alignement en quatre colonnes, grandes cartes
(303 x 170 px). Aucun nouveau systeme d'epinglage : la liste vient des reglages existants. Le focus garde son cadre net ;
le titre de l'application survolee s'affiche au-dessus du dock.

## Lisibilite : methode

Pour chaque zone de texte (heure, date, salutation, trois pilules, titre du dock), l'application calcule la plage de
luminance du fond (grille de couleur 32 x 18 de la photo, courbe de crete, etalonnage courant, et le pire cas des nuages,
du halo du soleil, du brouillard et de l'orage, qui peuvent se trouver ou non derriere le texte), puis choisit la couleur
du texte et un traitement progressif de l'image (au plus 60 %) pour atteindre **4,5:1 pour le texte et 3:1 pour la grande
heure** (WCAG). Le texte blanc est la regle pour l'heure (le texte sombre ne sert que si le voile sombre depasserait 50 %) ; pour la date et la
salutation il sert des 38 %, ce qui evite un assombrissement visible des ciels clairs. Les pilules sont des vues en verre sombre (86 %). Les voiles se melangent dans l'espace sRGB du canevas, ce que le modele
reproduit. Ce traitement est une tres grande ellipse a degrade long, sans bord perceptible (un filtre degrade de photographe), pas une plaque.

### Mesure

Deux niveaux, relances par `index.html?selftest` dans un vrai navigateur (Brave sans fenetre) :

1. **Modele** (celui de l'application, porte en JavaScript, 72 combinaisons x 7 zones = 504 cas) : aucun cas sous le seuil.
   Minimum par zone : heure 3.29:1 (seuil 3), date 4.52, salutation 4.59, pilules 5.9, titre du dock 4.67 (seuil 4,5).
2. **Pixels rendus** (la scene est dessinee sans texte, puis le 2e et le 98e centile de luminance de chaque zone donnent le
   pire contraste reel avec la couleur de texte choisie) : 72 combinaisons x 7 zones, **504 sur 504 au-dessus du seuil**.
   Minimum par zone : heure 3.06:1, date 5.36:1, salutation 6.12:1, pilules 12.7:1, titre du dock 6.82:1. Voile maximal : 57 %.

Ce que la premiere version du modele ratait, et qui a ete corrige apres la mesure sur pixels (278 zones sous le seuil au
depart) : voile melange en luminance lineaire au lieu de sRGB, voile qui ne couvrait pas les coins du rectangle de texte,
grille de couleur trop lissee pour les reflets fins (marges elargies), lune pouvant passer derriere la date (elle reste
maintenant au-dessus de la salutation), nuages et brouillard supposes clairs la nuit (ils sont maintenant sombres la nuit,
dans le modele comme dans le dessin), couture visible entre le voile et le premier plan (le voile recouvre maintenant photo et
premier plan, et le bas des chiffres s'efface dans un calque).

Contraste mesure sur les pixels rendus, par combinaison (rapport de contraste ; seuil 3 pour l'heure, 4,5 pour le reste ;
tout est au-dessus) :

| Phase | Meteo | Heure | Date | Salutation | Pilules (min) | Titre du dock | Voile max |
|---|---|---|---|---|---|---|---|
| Avant le lever | Clair | 4.1 | 6.6 | 7.5 | 15.9 | 7.3 | 21 % |
| Avant le lever | Partiel | 4.4 | 6.8 | 7.6 | 16.3 | 7.6 | 18 % |
| Avant le lever | Couvert | 4.7 | 7.4 | 8.1 | 16.5 | 8.3 | 24 % |
| Avant le lever | Brouillard | 3.5 | 8.2 | 9.4 | 15.7 | 8.7 | 49 % |
| Avant le lever | Bruine | 4.9 | 7.0 | 7.7 | 16.5 | 8.5 | 22 % |
| Avant le lever | Pluie | 4.9 | 6.8 | 7.3 | 16.4 | 8.2 | 26 % |
| Avant le lever | Averses | 4.8 | 5.8 | 6.1 | 16.4 | 8.1 | 26 % |
| Avant le lever | Neige | 4.0 | 9.1 | 10.3 | 16.0 | 9.7 | 52 % |
| Avant le lever | Orage | 8.7 | 10.1 | 10.5 | 17.0 | 9.1 | 0 % |
| Lever | Clair | 3.6 | 6.0 | 8.0 | 13.1 | 10.1 | 49 % |
| Lever | Partiel | 3.5 | 6.1 | 7.6 | 13.7 | 10.2 | 45 % |
| Lever | Couvert | 3.3 | 7.3 | 8.5 | 14.3 | 10.2 | 47 % |
| Lever | Brouillard | 3.5 | 6.0 | 7.1 | 14.2 | 9.6 | 57 % |
| Lever | Bruine | 3.3 | 7.5 | 8.7 | 14.3 | 10.1 | 46 % |
| Lever | Pluie | 3.1 | 8.0 | 9.2 | 14.3 | 9.9 | 50 % |
| Lever | Averses | 3.1 | 8.1 | 9.4 | 14.2 | 9.9 | 50 % |
| Lever | Neige | 3.5 | 6.6 | 7.8 | 14.4 | 10.1 | 57 % |
| Lever | Orage | 6.1 | 7.0 | 7.1 | 16.4 | 9.0 | 23 % |
| Matin | Clair | 3.7 | 5.4 | 7.7 | 12.7 | 10.2 | 50 % |
| Matin | Partiel | 3.7 | 5.9 | 7.6 | 13.4 | 10.6 | 48 % |
| Matin | Couvert | 3.4 | 6.4 | 7.4 | 14.3 | 10.5 | 49 % |
| Matin | Brouillard | 3.8 | 5.6 | 6.9 | 14.1 | 9.5 | 57 % |
| Matin | Bruine | 3.3 | 7.2 | 8.3 | 14.3 | 10.5 | 48 % |
| Matin | Pluie | 3.1 | 7.8 | 9.1 | 14.3 | 10.5 | 52 % |
| Matin | Averses | 3.2 | 7.9 | 9.2 | 14.3 | 10.6 | 54 % |
| Matin | Neige | 3.7 | 5.6 | 6.8 | 14.3 | 10.1 | 57 % |
| Matin | Orage | 5.7 | 6.4 | 6.5 | 16.2 | 9.2 | 28 % |
| Midi | Clair | 3.4 | 6.4 | 8.8 | 12.7 | 10.1 | 50 % |
| Midi | Partiel | 3.5 | 6.2 | 8.1 | 13.4 | 10.5 | 48 % |
| Midi | Couvert | 3.3 | 6.6 | 7.6 | 14.3 | 10.4 | 48 % |
| Midi | Brouillard | 3.6 | 5.8 | 7.2 | 13.9 | 9.5 | 57 % |
| Midi | Bruine | 3.2 | 7.3 | 8.5 | 14.1 | 10.4 | 47 % |
| Midi | Pluie | 3.1 | 7.9 | 9.2 | 14.3 | 10.4 | 52 % |
| Midi | Averses | 3.1 | 8.0 | 9.3 | 14.1 | 10.5 | 53 % |
| Midi | Neige | 3.7 | 5.7 | 6.8 | 14.3 | 10.2 | 57 % |
| Midi | Orage | 5.8 | 6.5 | 6.6 | 16.3 | 9.2 | 27 % |
| Apres-midi | Clair | 3.6 | 5.4 | 7.7 | 12.7 | 10.2 | 50 % |
| Apres-midi | Partiel | 3.6 | 5.9 | 7.6 | 13.3 | 10.6 | 48 % |
| Apres-midi | Couvert | 3.4 | 6.4 | 7.4 | 14.3 | 10.5 | 49 % |
| Apres-midi | Brouillard | 3.8 | 5.5 | 6.9 | 13.9 | 9.5 | 57 % |
| Apres-midi | Bruine | 3.3 | 7.2 | 8.3 | 14.3 | 10.4 | 47 % |
| Apres-midi | Pluie | 3.2 | 7.8 | 9.1 | 14.3 | 10.4 | 52 % |
| Apres-midi | Averses | 3.2 | 7.9 | 9.2 | 14.3 | 10.6 | 54 % |
| Apres-midi | Neige | 3.7 | 5.6 | 6.8 | 14.3 | 10.1 | 57 % |
| Apres-midi | Orage | 5.7 | 6.4 | 6.5 | 16.2 | 9.2 | 27 % |
| Coucher | Clair | 3.7 | 5.9 | 7.9 | 13.2 | 10.1 | 49 % |
| Coucher | Partiel | 3.5 | 6.1 | 7.6 | 13.7 | 10.3 | 46 % |
| Coucher | Couvert | 3.4 | 7.2 | 8.4 | 14.3 | 10.3 | 48 % |
| Coucher | Brouillard | 3.5 | 5.9 | 7.1 | 14.2 | 9.6 | 57 % |
| Coucher | Bruine | 3.3 | 7.4 | 8.6 | 14.3 | 10.2 | 46 % |
| Coucher | Pluie | 3.1 | 8.0 | 9.2 | 14.3 | 9.9 | 50 % |
| Coucher | Averses | 3.1 | 8.1 | 9.3 | 14.3 | 9.9 | 51 % |
| Coucher | Neige | 3.5 | 6.6 | 7.8 | 14.4 | 10.1 | 57 % |
| Coucher | Orage | 6.1 | 6.9 | 7.1 | 16.4 | 9.0 | 23 % |
| Crepuscule | Clair | 3.8 | 6.7 | 7.8 | 15.8 | 6.8 | 24 % |
| Crepuscule | Partiel | 4.1 | 6.9 | 7.9 | 16.1 | 7.5 | 21 % |
| Crepuscule | Couvert | 4.4 | 7.3 | 8.1 | 16.5 | 8.2 | 26 % |
| Crepuscule | Brouillard | 3.4 | 8.2 | 9.5 | 15.6 | 8.7 | 50 % |
| Crepuscule | Bruine | 4.6 | 7.0 | 7.7 | 16.5 | 8.3 | 24 % |
| Crepuscule | Pluie | 4.6 | 6.7 | 7.3 | 16.4 | 8.3 | 29 % |
| Crepuscule | Averses | 4.6 | 6.5 | 7.1 | 16.4 | 8.2 | 29 % |
| Crepuscule | Neige | 3.9 | 9.1 | 10.3 | 15.8 | 9.7 | 53 % |
| Crepuscule | Orage | 8.4 | 9.7 | 10.0 | 16.9 | 8.9 | 0 % |
| Nuit | Clair | 12.2 | 12.2 | 12.4 | 13.1 | 16.2 | 0 % |
| Nuit | Partiel | 11.4 | 11.5 | 11.7 | 14.9 | 14.4 | 0 % |
| Nuit | Couvert | 9.3 | 10.4 | 10.9 | 17.7 | 10.8 | 0 % |
| Nuit | Brouillard | 4.4 | 6.7 | 7.3 | 16.1 | 8.6 | 39 % |
| Nuit | Bruine | 9.6 | 10.6 | 10.9 | 17.3 | 11.1 | 0 % |
| Nuit | Pluie | 8.6 | 10.1 | 10.5 | 17.2 | 9.5 | 0 % |
| Nuit | Averses | 8.4 | 10.1 | 10.5 | 17.2 | 9.4 | 0 % |
| Nuit | Neige | 4.4 | 7.8 | 8.7 | 16.6 | 9.2 | 45 % |
| Nuit | Orage | 11.0 | 12.8 | 13.0 | 17.3 | 11.1 | 0 % |

Limite honnete : la mesure porte sur l'image calculee par l'interface (moteur et geometrie de l'application, meteo en image
fixe, rendu du texte du navigateur). Elle n'est pas faite sur la TV, ni avec le rendu du texte d'Android, ni avec les positions
reelles des gouttes et des flocons, ni avec l'eclair.

Cout visible : pour garantir ces rapports, le ciel clair est legerement assombri ou eclairci autour du texte (jusqu'a 58 %
au pire). Le degrade est large et sans bord, mais il est perceptible sur un ciel tres clair (voir la section suivante).

## Reprise du rendu (retour de l'utilisateur : « artificiel et mal integre »)

Le premier rendu passait les tests de contraste mais etait laid. Causes trouvees dans le code et sur les captures :
- **neige** : flocons de 1 a 10 px de rayon, dont des disques flous radiaux : de gros points lumineux decoratifs ;
- **nuages et brume** : 14 bouffees radiales aplaties, une couche grise pleine a 70 % et neuf nappes de brume : des formes
  superposees, avec un bord net en bas de la brume ;
- **eclats sur l'eau** sous la pluie : des traits clairs colles ;
- **textes** : ombre et halo autour de chaque ligne, et voiles en ellipses ou en rectangles a bord franc, visibles comme
  des plaques, plus une couture entre le voile et le premier plan ;
- **disque du soleil** a midi, visible comme un point pale derriere la salutation.

Corrections (JavaScript de l'interface et Java de l'application, les memes) :
- neige : 170 petits flocons nets de 0,9 a 2,7 px de rayon, plus petits et plus pales au loin, aucun disque flou ;
- pluie : traits plus fins et plus discrets (opacite divisee par deux), 150 au lieu de 210, plus d'eclats sur l'eau ;
- nuages : plus de formes ; un voile gris froid progressif en haut du ciel (le couvert vient surtout de l'etalonnage) ;
- brume : un seul voile qui se fond en haut et en bas, sans bord ; la brume de l'aube est reduite ;
- textes : aucune ombre, aucun contour ni halo ; voile de lisibilite en tres grande ellipse a degrade long ;
- soleil : le disque disparait au-dessus de 14 degres, seul le halo reste ; la lune reste au-dessus de la salutation.

Scenes effectivement regardees dans le navigateur (captures de 1280 x 720 relues, puis recadrages UHD du bloc heure et textes) :
nuit avec neige (avec et sans bloc musique), midi clair, lever couvert, coucher pluvieux avec focus sur le dock, avant le lever en
brouillard, apres-midi clair avec focus sur la deuxieme rangee, crepuscule orageux. Recadrages UHD a 100 % : nuit neigeuse et midi clair.
Resultat : plus de plaques, de couture ni de contour ; la photo reste lisible comme photo. Defauts restants, vus et non corriges :
sur les ciels clairs (aube, midi) et en brouillard, le traitement de lisibilite se voit comme un leger assombrissement progressif du ciel
au-dessus de l'heure, et la neige de nuit est discrete au point de ressembler a des etoiles. Les couvert, brouillard et neige sont
plus sobres que reels : aucune forme de nuage n'est dessinee.

## Transitions

Methode : **aucune image n'est echangee**. Il n'y a qu'une photo ; un changement de phase ou de meteo ne fait que deplacer
l'etat de la scene (`SceneMix` : hauteur du soleil, azimut, lune, nuages, brouillard, pluie, neige, orage, averses) vers sa
nouvelle cible, par lissage exponentiel (constante de temps 0,65 s pour le ciel, soit environ 2 s pour 95 % du chemin ; 3 s pour la meteo, soit une dizaine de secondes). L'etalonnage est
recalcule a chaque image pendant le glissement (matrice de couleur de la photo ET du premier plan, donc identique), et les
intensites de pluie, neige et brouillard montent et descendent par rampes, sans apparition brusque. Une transition
interrompue redirige simplement la cible : l'etat converge sans sauter ni passer par le noir. Au retour au premier plan,
l'etat saute a l'heure et a la meteo courantes (pas de glissement depuis un etat perime). Les plans de lisibilite sont
recalcules a 10 Hz au plus pendant un glissement et leurs voiles lissent leur opacite.

Seul cas ou l'ecran part du noir : si le systeme a libere les images de la scene pendant que l'accueil etait derriere une
autre application, elles sont relues au retour et la photo apparait en 0,4 s depuis le noir (les images ne sont pas
gardees en memoire en arriere-plan, pour la memoire de la TV). Ce n'est pas un fondu enchaine de deux photos.

Tests JVM : le glissement de phase est monotone et reste sous 9 % d'exposition par image de 33 ms (la zone la plus raide
est le lever) ; une interruption converge ; le retour au premier plan recalcule l'etat ; aucun saut d'etalonnage sur tout
le parcours du soleil (test JS : moins de 0,06 entre deux quarts de degre).

## Meteo

Clair, partiellement nuageux, couvert, brouillard, bruine, pluie, averses (vagues et rafales), neige (profondeur par taille et
opacite des flocons), orage (halo local rare, au plus deux eclairs par minute, jamais un flash plein ecran). Pluie, neige et
brouillard sont derriere le dock et les pilules (le dock est une vue posee au-dessus). Aucune allocation dans la boucle de dessin :
un test lit le code source des methodes de dessin et refuse tout `new`. Exceptions documentees : la scene dessinee de
secours alloue quand elle se reconstruit, et le filtre de couleur de la photo est recree dans `prepare()` (hors du dessin)
quand l'etalonnage change. La boucle d'animation est deja plafonnee a environ 30 images par seconde (une image toutes les 33 ms) ; rien n'a ete mesure sur la TV (Android 9).

## Interface de comparaison

`tools/.venv/bin/python tools/build_alpine_preview.py --serve` puis `http://127.0.0.1:8765/` (12 Mo, aucune dependance externe).
Photo unique, 8 phases, 9 meteos, 4 etats (repos, focus sur le dock, focus sur la deuxieme rangee, bloc musique), huit
combinaisons parlantes (nuit claire, nuit neigeuse, matin brumeux, midi degage, coucher degage, coucher pluvieux, nuit
pluvieuse, orage). Chaque vignette porte son contraste minimal mesure par le modele et un badge rouge sous le seuil (il n'y en a
aucun). Modes 1920x1080 et UHD 3840x2160 (fichiers du pack sans redimensionnement du navigateur : un pixel pour un pixel),
detail a 100 % (trois recadrages), comparaison de 2 a 4 versions ou superposee, plein ecran TV, favoris, resume, onglets
Contraste, Fichiers livres et Legende.

**Ouverte et verifiee dans un vrai navigateur** (auto-test `index.html?selftest`, 22 controles sur 22) : grille et 48
vignettes peintes sur 48, filtres phase x meteo x etat (48 et 30 combinaisons), filtre vide, fichiers references presents,
comparaison en UHD (3840x2160) et en 1080p, zoom 100 %, mode superpose, detail a 100 %, plein ecran UHD, favoris et resume, les 9
meteos dessinees, contraste modele puis pixels sur 72 combinaisons. Captures relues a l'oeil : voir la section « Reprise du rendu ».

## Deuxieme reprise : atmosphere, donnees meteo reelles, cycle et transitions visibles

**Cycle du soleil.** Le soleil et la lune sont calcules sur la TV avec les equations NOAA (`SolarPhase.sunPosition`, `moonPosition`)
pour les coordonnees des reglages (Lausanne par defaut), la date et l'heure reelles, et le fuseau de la TV ; le lever et le coucher
viennent de la meme source (7 h 38 et 17 h 00 le 17 novembre 2026). L'etalonnage de la photo est une fonction continue de la hauteur du
soleil (table de dix points interpolee, plus une teinte un peu plus chaude le soir) : aucune bascule entre scenes jour et nuit. L'aperçu
reprend les memes equations en JavaScript, et des tests JVM et Python comparent les hauteurs de soleil aux memes valeurs.
**Limite** : la photo est fixe. On ne deplace ni les ombres ni la direction de la lumiere, on ne change que l'exposition, la couleur,
le voile atmospherique, les etoiles, la lune et le halo du soleil. L'azimut du soleil ne change que la position de son halo.

**Donnees meteo.** La requete Open-Meteo demande desormais, en plus du code WMO, `cloud_cover`, `precipitation`, `wind_speed_10m` et
`visibility` (`WeatherRepository`, `WeatherData`). Le code donne la condition ; les observations en reglent l'intensite
(`SceneMix.targetWeather`) : pluie et neige selon les mm/h (courbe saturante), bruine plafonnee a 0,35 quelle que soit la quantite,
nuages selon le pourcentage (au moins 0,7 sous la pluie ou la neige), brume selon la visibilite (moitie moins sous precipitations), vent
selon les km/h (inclinaison des traits, derive des flocons). Sans observation, valeurs par defaut de la condition. Les donnees
perimees ou indisponibles gardent la derniere scene (le cache sert, aucun effet ne disparait). Une mise a jour ne fait que changer les
cibles : la meteo glisse vers elles avec une constante de 3 s (environ dix secondes pour 95 %), le ciel avec 0,65 s. Les apercus forces
(`--es weather`) ignorent les observations.

**Rendu.** Plus de nappe uniforme : l'atmosphere (brouillard, voile de la pluie a 40 %, de la neige a 35 %) est un voile epais dans la
vallee du fond (autour du Cervin et des cretes lointaines), mince sur le rocher proche, le haut du ciel et le bas du lac, qui respire
tres lentement. Pluie : trois profondeurs (fin, court et pale au loin ; long, rapide et net devant), inclinee par le vent, la bruine n'a
que des traits lointains. Neige : trois profondeurs, des points d'un demi a un pixel au loin, des flocons ronds et doux de 2 a 3 px devant,
chute irreguliere, vacillement, derive au vent. Couvert : un voile gris froid en haut du ciel qui varie tres lentement. Rien sur le lac.

**Apercu : onglet « Transitions »** (meme interface, `tools/alpine_preview/anim.js`) : journee acceleree (24 h en 30, 60 ou 120 s,
avec soleil, lever et coucher affiches), transition meteo (clair, couvert, bruine, pluie, neige, brouillard, clair, avec les donnees
d'entree de chaque mise a jour, les cibles et les valeurs lissees affichees), lecture, pause, reprise, curseur d'instant precis, pas a
pas, rendu 960x540 pour la lecture, 1080p ou UHD a l'arret, planche d'instants.

**Regardé dans le navigateur** (Brave sans fenetre, captures relues) : journee du 17 novembre a 02:00, 06:40, 07:38, 09:00, 12:30, 16:00,
17:00, 17:45, 19:40 en 1080p ; scenario meteo a 10, 13, 16, 27, 40, 50, 56, 70 et 88 s, puis a 11, 14, 18, 25, 32, 37, 44, 53, 60 s
(autour des mises a jour), pluie a 40 et 50 s, neige a 54 et 57 s, neige dans le brouillard a 63 s, brouillard a 72 et 76 s, bruine a 26
et 30 s, en 1080p ; rendu UHD verifie par le test (3840x2160, non relu a l'oeil). La lecture reelle (animation) a ete lancee par l'auto-test :
la journee avance, la pause fige, la reprise repart ; je n'ai pas regarde une video, seulement des images fixes tirees a differents instants.

## Fichiers supprimes

`themes/weather/masters-native/` : `riffelsee-coucher.jpg`, `stellisee-avant-lever.jpg`, `stellisee-jour.jpg`,
`misurina-tre-cime.jpg`, `oeschinensee.jpg` (63 Mo, ignores par git, retelechargeables depuis Commons ; leurs entrees de
`sources.json` et leurs credits sont retires, aucun fichier du depot n'y fait plus reference : un test le verifie). Sources :
`PhotoPlan.java` et son test (remplaces par `SceneGrade` et `SceneMix`), le pack a seize fichiers et les huit images de phase
(remplaces par quatre fichiers). Gardes : le dock, la navigation, les reglages, les credits photo, la scene dessinee de secours,
l'accueil actuel, le lanceur d'origine intact.

## Fichiers livres (resolution et poids)

| Fichier | Pixels | Poids |
|---|---|---|
| `photo.jpg` (JPEG 95, 4:4:4) | 3840 x 2160 | 4,3 Mo |
| `photo_1080.jpg` (JPEG 95, 4:4:4) | 1920 x 1080 | 0,9 Mo |
| `foreground.png` (PNG sans perte) | 3840 x 2160 | 5,1 Mo |
| `foreground_1080.png` (PNG sans perte) | 1920 x 1080 | 1,2 Mo |
| `weather_pack.zip` (les quatre plus le manifeste) | | 11,6 Mo |
| Master natif Riffelsee (hors depot) | 6000 x 4000 | 20,4 Mo |
| APK de release | | 643 405 octets (limite du CI relevee a 737 280) |

Recadrage natif 3900 x 2194 puis un seul reechantillonnage Lanczos vers le bas : aucun agrandissement, aucun debruitage,
aucune accentuation. Detail et empreintes : [fichiers-livres.md](fichiers-livres.md).

## Verifie moi-meme, en local

| Verification | Resultat |
|---|---|
| Tests JVM (`testReleaseUnitTest`) | 408, aucun echec |
| `lintRelease`, `assembleRelease` | passes ; APK 643 405 octets |
| Tests Python de l'outil photo et de l'interface | 52 verts (`tests/test_weather_scene.py`, `tests/test_alpine_preview.py`) |
| Auto-test du navigateur | 22 controles sur 22, contraste 72 x 7 au-dessus du seuil |

## Reste incertain ou non fait (voulu : rien sur la TV)

1. Rendu Android reel : texte, ombres, voiles, pilules, bloc musique et dock n'ont pas ete compares a la TV.
2. Fluidite, chauffe et memoire sur la Bravia (Android 9) : le calque de l'heure et les voiles ajoutent un peu de dessin par
   image ; non mesure.
3. Les voiles de lisibilite sont perceptibles (voir plus haut) : a juger sur la TV, a assouplir si besoin au prix de marge.
4. Le pack (11,6 Mo) doit etre retransfere a la TV : un ancien pack est refuse proprement, l'accueil retombe sur la scene dessinee.
5. Aucune TV 4K : le chemin UHD n'est verifie que par calcul et tests.
6. Spotify et le bloc musique compact : non reverifies sur la TV.
