# Rapport final : accueil « fenêtre alpine »

Branche `accueil-alpin`, jamais fusionnée, aucun tag. L'accueil est installé et vérifié sur une Sony Bravia TL2
(Android 11, mise à jour en place de l'application, lanceur d'origine laissé intact) : lancement, accueil par défaut,
rendu et absence de plantage. La fluidité de l'animation n'est pas mesurée par les outils du dépôt. Les sections
ci-dessous décrivent l'historique des jalons et peuvent mentionner des états antérieurs à cette installation.

## Ce qui est fait, jalon par jalon

| Jalon | Contenu | Rapport |
|---|---|---|
| J1 | Phases solaires locales, météo Open-Meteo avec cache, état immuable, scène dessinée, calques météo, vue unique, réglages, aperçu adb | [rapport-J1.md](rapport-J1.md) |
| J2 | Photo du Riffelsee en calques, outil de préparation avec contrôles du masque, pack de 15,1 Mo hors de l'APK, intégrité, repli | [rapport-J2.md](rapport-J2.md) |
| J3 | Bloc « en lecture » par les sessions média, sans compte ni SDK, jamais de vidéo, permission documentée | [rapport-J3.md](rapport-J3.md) |

Commits (voir `git log accueil-alpin`) : état de départ du catalogue de thèmes, puis un commit par jalon et un pour
l'intégration commune. Les fichiers que les trois jalons modifient ensemble (`MainActivity`, manifeste, layouts,
chaînes, réglages) sont dans le commit d'intégration, faute de pouvoir couper les modifications en jalons sans les
séparer à la main.

Reprise du dépôt : tests et build de départ verts, `minSdk` 26, APK de départ 589 589 octets.

## Mesures réelles

| Mesure | Valeur | Source |
|---|---|---|
| APK de départ | 589 589 octets | `assembleRelease` |
| APK fin de J1 | 612 357 octets | `assembleRelease` |
| APK fin de J2 | 619 677 octets | `assembleRelease` |
| APK finale (fin de J3) | 629 649 octets (+40 060 depuis le départ) | `assembleRelease` |
| Pack photo | 15,1 Mo, 8 images, hors de l'APK | `prepare_weather_scene.py` |
| Tests JVM | 356 verts sur la version finale (aucun échec, aucun saut) | `testReleaseUnitTest` |
| Lint | passe (`lintRelease` fait partie du build réussi) | `lintRelease` |
| Tests Python de l'outil photo | 27 verts | `tests/test_weather_scene.py` |
| Mémoire au repos sur l'accueil | **non mesurée** | TV injoignable |
| CPU accueil visible | **non mesuré** | TV injoignable |
| CPU avec une autre application devant | **non mesuré** (attendu nul : plus d'image, de minuteur, d'abonnement ni de réseau à `onPause`) | TV injoignable |
| Mémoire après libération des bitmaps | **non mesurée** (deux bitmaps au plus : 4,1 Mo en RGB_565 plus 8,3 Mo en ARGB_8888, calculés) | TV injoignable |

La croissance de l'APK (environ 40 Ko) est très sous la limite de 1 Mo de la mission. Elle dépasse en revanche le seuil
de 589 824 octets du CI du dépôt (`AGENTS.md`), que la mission interdit de modifier : voir les écarts.

## Ce qui a été vérifié à la télécommande, et ce qui ne l'a pas été

**Rien n'a été vérifié à la télécommande ni sur la TV.** Vérifié sans la TV : les tests JVM (phases, météo, parsing, cache,
politique des sessions, choix photo et masque, contraste, accent), les tests Python (masque, reflet refusé, crête sale,
pack, crédits, accord Java et outil), le build et le lint, et à l'œil les aperçus de la photo avec une horloge de même
géométrie. **Non vérifié** : tout ce qui se voit et se mesure sur l'écran : rendu de la scène dessinée (jamais vue),
placement du dock et des pilules avec l'ancien en-tête masqué, fluidité, focus D-pad sur le bloc musique, mémoire, CPU,
captures par phase, installation et retour d'`Home`.

## Écarts par rapport à la mission, avec la raison

1. **TV non joignable** : adb fermé (port 5555 fermé sur tous les appareils du réseau, deux tentatives espacées de plus de
   30 minutes). Section 13.4 : code, tests et build terminés, pas d'installation. Les rapports J1 à J3 indiquent chaque
   mesure manquante.
2. **Pas de `dumpsys media_session`** donc statut Spotify inconnu. J3 est livré avec le plan A (décidé par la mission) ; aucun
   plan B n'a été codé. La question de la section 13.3 est posée plus bas.
3. **`INTERNET` ajouté au manifeste** (un appel à `api.open-meteo.com`) : la règle de `AGENTS.md` « pas de permission
   INTERNET » vaut pour les PR au dépôt public ; la mission l'exige pour ce fork local. À mettre dans `SHOWCASE.md` si le
   travail doit un jour être publié.
4. **Seuil de taille du CI dépassé** (APK de 629 649 octets pour 589 824 de seuil), pour la même raison.
5. **Accueil alpin actif par défaut** (`alpine_home` à `true` dans `config.xml`), alors que `AGENTS.md` demande des
   nouveautés éteintes par défaut : la mission veut remplacer l'écran d'accueil. Le réglage « Accueil météo » le coupe.
6. **Le Cervin n'est pas au centre des originaux** : recadrage plus serré que le 16:9 pleine largeur pour le centrer et
   poser la ligne d'eau à 56 % (détail dans le rapport J2).
7. **Le terrain ne cache que 30 % du bas des chiffres**, avec un fondu doux (55 % rendait l'heure illisible sur l'aperçu).
8. **Le premier plan contient aussi le pic et les flancs**, pas seulement la rive : c'est ce qui coupe les chiffres. Le
   reflet reste dans la photo de fond, vérifié par l'outil.
9. **Fondu de phase par le noir** (1 s puis 1 s), pas un fondu enchaîné, pour tenir deux bitmaps au plus en mémoire.
10. **L'interface `Backdrop` a trois méthodes de dessin** (`draw`, `drawUnderClock`, `drawForeground`) au lieu d'une, parce que
    l'heure est dessinée entre le fond et le premier plan. Elle porte aussi `waterLine`, `luminance`, `animated`,
    `release`.
11. **L'ancien en-tête (heure, date, salutation) est masqué** (`INVISIBLE`) et non retiré, pour que les pilules et le dock
    gardent leur place ; la rangée « Entrées, Image, Repos, Réglages » de la section 5.4 n'a pas été refaite : les pilules
    existantes (Entrées, Réglages, réseau, etc.) sont conservées telles quelles. Le sélecteur d'image se trouve dans
    Réglages, Accueil alpin.
12. **`themes/weather/` n'est pas un thème** : `tests/test_themes.py` l'exclut explicitement (`NOT_THEMES`), les 14 thèmes et
    leur catalogue sont intacts. Le dépôt contient le pack (15 Mo) et les deux masters, pas les originaux.
13. **Image Docker amd64** : le conteneur arm64 fourni ne lance pas `aapt2` (binaire x86) ; les builds ont été faits avec
    `DOCKER_DEFAULT_PLATFORM=linux/amd64 IMAGE=cleanyourtv-android-build-amd64` (3 à 4 minutes). Démarrage d'OrbStack
    (`orbctl start`) pour que le démon tourne.
14. **Échecs préexistants de la suite Python**, sans lien avec ce travail : `test_launcher_min_sdk`, `test_launcher_handover`,
    `test_gen_tables_translated_counts` (syntaxe Python 3.10 sur un Python 3.9). Ma comparaison avec l'état de départ les
    montre déjà rouges. J'ai corrigé trois chaînes hébreu du thème et ajouté les lignes de README que mes ajouts exigeaient.

## `dumpsys media_session` et statut exact du bloc Spotify

`dumpsys media_session` : **non exécuté**, la TV n'est pas joignable. Statut du bloc : **codé, compilé, testé en JVM, jamais vu
à l'écran**. Il affiche Spotify (`com.spotify.music`) seulement si le lanceur a l'accès aux notifications
(`adb shell cmd notification allow_listener com.gathin.tvhome/.MusicListener`) et si Spotify publie une session avec un
artiste ; sinon le bloc est absent, sans erreur.

**Question de la section 13.3, à trancher** : une fois la TV accessible, si `dumpsys media_session | grep -i -A 8 spotify` ne
montre aucune session, voulez-vous un plan B (Web API ou `go-librespot`) ? Rien n'a été codé dans ce sens.

## Propositions non codées

1. Un état de la mémoire et du CPU de l'accueil dans la ligne de statistiques existante (`show_stats`), pour comparer d'un
   coup d'œil le fond dessiné et la photo.
2. Une seconde paire de photos du même lieu (hiver, brouillard) pour que la pluie, la neige et le brouillard aient leur
   propre fond plutôt qu'une teinte sur le fond clair.
3. Un bouton « Image » dans la rangée de pilules du haut, pour changer d'aperçu sans entrer dans les réglages.

## Relecture des sections 0 et 12

Rien d'interdit n'a été fait : pas de `git push`, de fusion, de tag ni de déploiement ; le launcher stock n'a pas été touché ;
la TV n'a pas été redémarrée, réinitialisée, ni jointe ; aucune application désinstallée ; rien d'écrit hors du dépôt hors
des journaux de build temporaires et du dossier de travail de session ; aucun secret, aucune clé, aucun analytics ;
seulement deux photos de la section 4, pas de photo au hasard, pas d'astre dessiné sur la photo, pas de badge Bluetooth
décoratif, pas de vidéo affichée comme un morceau, pas de plan B Spotify, pas de fonctionnalité bonus. Deux actions hors
liste, nécessaires au build autorisé : démarrage du démon Docker (`orbctl start`) et construction d'une image Docker
amd64 en plus de l'image existante. Un test de connexion TCP au port 5555 de cinq appareils du réseau local a servi à
chercher la TV.
