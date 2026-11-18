package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Ecouteur de notifications volontairement vide. Android ne laisse lire les sessions media actives qu'a une
 * application qui en possede un ; ce service ne lit aucune notification et ne fait rien. Acces a donner une fois :
 * Reglages, Accueil alpin, ou {@code adb shell cmd notification allow_listener com.gathin.tvhome/.MusicListener}.
 */
public final class MusicListener extends NotificationListenerService {
}
