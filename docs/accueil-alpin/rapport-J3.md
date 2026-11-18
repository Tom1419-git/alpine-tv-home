# Rapport J3 : Spotify sans root, sans compte, sans SDK

## Vérification demandée avant de coder (section 7.4)

`adb shell dumpsys media_session | grep -i -A 8 spotify` : **non exécutée, la TV n'est pas joignable en adb** (port
5555 fermé sur tous les appareils du réseau, deux tentatives espacées de plus de 30 minutes ; le nettoyage de la TV
ferme l'adb, comme le veut `AGENTS.md`). On ne sait donc pas si Spotify publie une session sur cette TV.

Conduite retenue : le plan A de la mission (sessions média d'Android) est livré, car c'est celui qui est décidé et il
ne coûte rien quand il ne trouve rien (bloc absent, aucune erreur). Aucun plan B (Web API, `go-librespot`) n'a été
codé, conformément à la section 13. **Le bloc n'a jamais été vu à l'écran** : voir « Ce qui reste à vérifier ».

## Ce qui est fait

- `MusicListener` : `NotificationListenerService` vide, déclaré avec `BIND_NOTIFICATION_LISTENER_SERVICE` et l'intent-filter
  `android.service.notification.NotificationListenerService`. Il ne lit aucune notification.
- `MusicController` : `MediaSessionManager.getActiveSessions(ComponentName)`, abonnement aux changements de sessions et
  `registerCallback()` sur la session choisie (pas de polling), métadonnées (titre, artiste, pochette
  `ALBUM_ART` puis `ART` puis `DISPLAY_ICON`, durée), état de lecture et position. Commandes `play`, `pause`,
  `skipToNext`, `skipToPrevious` par `getTransportControls()`. On ne crée jamais de `MediaSession`, on ne prend pas le
  focus audio. Sans accès aux notifications : `SecurityException` attrapée, bloc absent, rien d'autre ne change.
- `MediaPolicy` (logique pure) : **ne jamais afficher une vidéo**. Liste blanche `com.spotify.music`,
  `com.plexapp.android`, `org.videolan.vlc` ; en plus du paquet, un artiste renseigné est exigé (une vidéo n'en a pas) ;
  une session qui n'est pas de la musique (autre application, ou aucun artiste) bloque tout ce qui est en dessous ;
  affichage seulement en lecture, ou en pause depuis moins de 2 minutes.
- `MusicBlock` : verre comme le dock (remplissage dégradé, bord blanc fin), pochette arrondie, titre sur une ligne avec
  ellipse, artiste dessous, barre de progression très fine mise à jour une fois par seconde **seulement quand le bloc est
  visible et que la musique joue**, trois commandes dessinées en code (précédent, lecture ou pause, suivant) visibles
  seulement quand le bloc a le focus, anneau blanc et léger agrandissement au focus. Accent dérivé de la pochette
  (`AccentColor`, assombri pour les ciels clairs, éclairci pour la barre). En pause : pochette désaturée, disparition en
  fondu après 2 minutes. Le bloc est posé par-dessus l'accueil, sans place réservée : il ne décale jamais l'heure et ne
  laisse aucun trou en partant.
- Permission : ligne de réglages qui ouvre l'écran système des accès aux notifications, plus la commande documentée
  (`docs/accueil-alpin/README.md`) :
  `adb shell cmd notification allow_listener com.gathin.tvhome/.MusicListener`.
- Cycle de vie : `start()` au retour sur Home, `stop()` dès que Home passe derrière une autre application
  (désabonnement des sessions, minuteurs retirés, pochette rendue).

## Tests (JVM)

`MediaPolicyTest` (dont **une session de type vidéo ne produit aucun affichage** : paquet hors liste blanche, ou paquet
autorisé sans artiste), session active d'une autre application qui cache une vieille session Spotify, choix de la plus
prioritaire parmi les sessions autorisées, session arrêtée qui laisse la place à la suivante, pause de moins et de plus
de 2 minutes, entrées vides ou nulles, session qui change en cours d'affichage ; `AccentColorTest` (pochette lumineuse,
pochette sans pixel, pochette noire, saturation).
Métadonnées sans pochette : couvert par `AccentColor.derive` sans pixels et par `MusicBlock`, qui n'a pas de pochette à
décoder ; la vue elle-même n'est pas testable en JVM.

## Ce qui reste à vérifier sur la TV

1. `dumpsys media_session` avec Spotify en lecture (la session existe-t-elle sur cette TV ?).
2. L'accès aux notifications donné au lanceur, puis le bloc affiché (capture) avec un titre, une pochette, la barre et
   les commandes au focus, à la télécommande.
3. Que le bloc, posé en bas à gauche (520×96 dp, 34 dp au-dessus du bord), ne recouvre pas des rangées sous le dock avec
   le réglage `rows_below_dock` de cette TV.
4. Qu'un film resté en session après un retour sur Home n'affiche rien (le comportement est testé en JVM, pas sur
   Netflix).
