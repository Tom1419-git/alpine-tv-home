# Accueil alpin pour LG webOS

La même scène que sur Android TV, en appli pour les TV LG : la photo du Riffelsee et du Cervin dont la lumière suit le vrai
soleil de ta ville, la météo réelle (Open-Meteo), la date et une horloge toujours lisible. Elle se lance depuis la liste des
applications de la TV.

## Ce que c'est, et ce que ce n'est pas

Une TV LG tourne sous **webOS**, pas sous Android TV : le lanceur du reste du dépôt ne s'y installe pas. Cette version est donc
une **appli web plein écran**.

- Elle s'ouvre comme n'importe quelle appli ; elle **ne remplace pas** l'écran d'accueil de LG, ni son écran de veille
  (webOS ne le permet pas).
- La scène est **recalculée chaque minute** (fondu doux), la météo est relue toutes les 30 minutes. La pluie et la neige sont
  une image fixe, pas animée comme sur Android.
- Pas de bloc musique, pas de dock.
- La salutation, la date et la météo sont en français.
- **Non testé sur une vraie LG** à la publication : le rendu est vérifié dans un navigateur, l'installation par les outils LG
  n'a pas été exécutée. Visé : webOS 4 et plus (TV de 2018 et après). Dis-moi (issue) si quelque chose ne va pas.

## Installer, en une commande

Il faut **Node 18 ou plus** sur ton ordinateur (le même réseau que la TV) et le **mode développeur** de la TV.

1. Mode développeur : sur la TV, installe l'appli **Developer Mode** (LG Content Store), connecte-la à un compte LG
   développeur gratuit, active *Dev Mode Status* et *Key Server*. `node webos/install.js --action help` rappelle les étapes.
2. Lance :

```bash
node webos/install.js
```

Un menu te demande quoi faire : installer ou mettre à jour, changer la ville, lancer, désinstaller. Pour l'installation il
demande l'IP de la TV et ta ville, te fait saisir le code affiché par Developer Mode, puis empaquette, installe et lance l'appli.

Les outils LG (`@webos-tools/cli`) sont utilisés s'ils sont installés ; sinon le script propose de les télécharger avec `npx`
(rien n'est installé sur ton système).

### Avec un assistant IA

Colle [PROMPT.md](PROMPT.md) (ou le texte du README principal) dans ton assistant : il clone le dépôt si besoin, te demande le
modèle de TV, l'IP et la ville, vérifie la compatibilité, puis exécute l'installation à ta place.

### Sans question (scripts)

```bash
node webos/install.js --action install --ip 192.168.1.50 --city "Lausanne" --yes
node webos/install.js --action city --city "Genève"
node webos/install.js --action remove
```

`--dry-run` affiche les commandes sans rien lancer.

## La durée du mode développeur

LG limite la durée de la session développeur : quand elle expire, il faut la prolonger depuis l'appli Developer Mode. Je n'ai
pas pu vérifier ce qu'il advient de l'appli installée à l'expiration.

## Pour modifier

`webos/app/` est généré : `tools/.venv/bin/python tools/build_alpine_preview.py` puis `python3 webos/build.py` le reconstruisent
à partir du moteur de l'aperçu (`tools/alpine_preview/`). Les images sont intégrées dans `images.js` en data: URI, pour que
le canvas puisse lire les pixels même servi en `file://`.

## Crédits

Photo : Deralpinbergsteiger, CC BY-SA 4.0 (voir `themes/weather/CREDITS.md`). Météo : [Open-Meteo](https://open-meteo.com), CC BY 4.0.
Police Outfit : SIL Open Font License.
