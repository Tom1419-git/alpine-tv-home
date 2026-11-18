package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quelle session media l'accueil affiche, et surtout laquelle il n'affiche jamais : le bloc ne montre que de la
 * musique, sinon un film reste en session apres le retour sur Home apparaitrait comme un morceau. Logique pure,
 * testee en JVM.
 */
final class MediaPolicy {
    /** Lecteurs audio autorises par defaut (com.spotify.tv.android : le paquet releve par dumpsys sur la Bravia TL2). */
    static final Set<String> DEFAULT_WHITELIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.spotify.music", "com.spotify.tv.android", "com.plexapp.android", "org.videolan.vlc")));
    /** En pause depuis plus longtemps, le bloc disparait. */
    static final long PAUSE_GRACE_MS = 2 * 60_000L;

    enum Play { PLAYING, PAUSED, OTHER }

    /** Une session vue par le gestionnaire de sessions, sans type Android. */
    static final class Candidate {
        final String pkg;
        final String title;
        final String artist;
        final Play state;
        /** Heure (ms) a laquelle la lecture s'est arretee, pour une session en pause. */
        final long pausedSinceMs;

        Candidate(String pkg, String title, String artist, Play state, long pausedSinceMs) {
            this.pkg = pkg;
            this.title = title;
            this.artist = artist;
            this.state = state;
            this.pausedSinceMs = pausedSinceMs;
        }

        boolean hasArtist() {
            return artist != null && !artist.trim().isEmpty();
        }
    }

    private MediaPolicy() {
    }

    /**
     * La session a afficher parmi {@code sessions} (de la plus prioritaire a la moins prioritaire), ou null.
     * Une session qui n'est pas de la musique (autre application, ou aucun artiste) bloque tout ce qui est en dessous :
     * l'application au premier plan ne laisse pas une vieille session de musique reapparaitre.
     */
    static Candidate pick(List<Candidate> sessions, Set<String> whitelist, long nowMs) {
        if (sessions == null) return null;
        for (Candidate c : sessions) {
            if (c == null) continue;
            if (!whitelist.contains(c.pkg) || !c.hasArtist()) return null;
            if (c.state == Play.PLAYING) return c;
            if (c.state == Play.PAUSED && c.pausedSinceMs > 0 && nowMs - c.pausedSinceMs <= PAUSE_GRACE_MS
                    && nowMs >= c.pausedSinceMs) {
                return c;
            }
        }
        return null;
    }

    /** Le bloc doit-il encore etre affiche pour cette session, a cette heure ? */
    static boolean stillVisible(Candidate c, long nowMs) {
        return c != null && (c.state == Play.PLAYING
                || (c.state == Play.PAUSED && nowMs - c.pausedSinceMs <= PAUSE_GRACE_MS));
    }
}
