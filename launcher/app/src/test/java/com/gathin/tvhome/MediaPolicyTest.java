package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MediaPolicyTest {
    private static final long NOW = 10_000_000L;

    private static MediaPolicy.Candidate music(String pkg, String artist, MediaPolicy.Play play, long pausedSince) {
        return new MediaPolicy.Candidate(pkg, "Titre", artist, play, pausedSince);
    }

    private static MediaPolicy.Candidate pick(MediaPolicy.Candidate... sessions) {
        return MediaPolicy.pick(Arrays.asList(sessions), MediaPolicy.DEFAULT_WHITELIST, NOW);
    }

    @Test
    public void spotifyQuiJoueEstAffiche() {
        MediaPolicy.Candidate c = music("com.spotify.music", "Artiste", MediaPolicy.Play.PLAYING, 0);
        assertSame(c, pick(c));
    }

    @Test
    public void uneVideoNeProduitAucunAffichage() {
        // Netflix ou un film : paquet hors liste blanche, jamais affiche meme en lecture.
        assertNull(pick(music("com.netflix.ninja", "Quelqu'un", MediaPolicy.Play.PLAYING, 0)));
        assertNull(pick(music("com.google.android.youtube.tv", "Chaine", MediaPolicy.Play.PLAYING, 0)));
    }

    @Test
    public void uneSessionAutoriseeSansArtisteEstUneVideo() {
        // Plex ou VLC qui lisent un film : le paquet est autorise, pas l'absence d'artiste.
        assertNull(pick(music("com.plexapp.android", null, MediaPolicy.Play.PLAYING, 0)));
        assertNull(pick(music("org.videolan.vlc", "", MediaPolicy.Play.PLAYING, 0)));
        assertNull(pick(music("org.videolan.vlc", "   ", MediaPolicy.Play.PLAYING, 0)));
    }

    @Test
    public void laSessionActiveDUneAutreApplicationCacheLeBloc() {
        MediaPolicy.Candidate film = music("com.netflix.ninja", "X", MediaPolicy.Play.PLAYING, 0);
        MediaPolicy.Candidate oldSpotify = music("com.spotify.music", "A", MediaPolicy.Play.PAUSED, NOW - 30_000);
        assertNull(pick(film, oldSpotify));
        assertNull(pick(music("org.videolan.vlc", null, MediaPolicy.Play.PLAYING, 0), oldSpotify));
    }

    @Test
    public void parmiLesSessionsAutoriseesOnPrendLaPlusPrioritaire() {
        MediaPolicy.Candidate first = music("com.spotify.music", "A", MediaPolicy.Play.PLAYING, 0);
        MediaPolicy.Candidate second = music("org.videolan.vlc", "B", MediaPolicy.Play.PLAYING, 0);
        assertSame(first, pick(first, second));
        // Une session autorisee arretee laisse la place a la suivante.
        MediaPolicy.Candidate stopped = music("com.spotify.music", "A", MediaPolicy.Play.OTHER, 0);
        assertSame(second, pick(stopped, second));
    }

    @Test
    public void enPauseOnResteAfficheDeuxMinutes() {
        assertNotNull(pick(music("com.spotify.music", "A", MediaPolicy.Play.PAUSED, NOW - 119_000)));
        assertNull(pick(music("com.spotify.music", "A", MediaPolicy.Play.PAUSED, NOW - 121_000)));
        assertNull(pick(music("com.spotify.music", "A", MediaPolicy.Play.PAUSED, 0)));
    }

    @Test
    public void entreesVidesOuNulles() {
        assertNull(MediaPolicy.pick(null, MediaPolicy.DEFAULT_WHITELIST, NOW));
        assertNull(MediaPolicy.pick(Collections.<MediaPolicy.Candidate>emptyList(), MediaPolicy.DEFAULT_WHITELIST, NOW));
        List<MediaPolicy.Candidate> withNull = new ArrayList<>();
        withNull.add(null);
        assertNull(MediaPolicy.pick(withNull, MediaPolicy.DEFAULT_WHITELIST, NOW));
    }

    @Test
    public void listeBlancheParDefaut() {
        assertTrue(MediaPolicy.DEFAULT_WHITELIST.contains("com.spotify.music"));
        assertTrue(MediaPolicy.DEFAULT_WHITELIST.contains("com.plexapp.android"));
        assertTrue(MediaPolicy.DEFAULT_WHITELIST.contains("org.videolan.vlc"));
        assertTrue(MediaPolicy.DEFAULT_WHITELIST.contains("com.spotify.tv.android"));
        assertEquals(4, MediaPolicy.DEFAULT_WHITELIST.size());
    }

    @Test
    public void laSessionQuiChangeEnCoursDAffichage() {
        MediaPolicy.Candidate playing = music("com.spotify.music", "A", MediaPolicy.Play.PLAYING, 0);
        assertTrue(MediaPolicy.stillVisible(playing, NOW));
        MediaPolicy.Candidate paused = music("com.spotify.music", "A", MediaPolicy.Play.PAUSED, NOW - 60_000);
        assertTrue(MediaPolicy.stillVisible(paused, NOW));
        assertFalse(MediaPolicy.stillVisible(paused, NOW + 61_000 + MediaPolicy.PAUSE_GRACE_MS));
        assertFalse(MediaPolicy.stillVisible(null, NOW));
    }
}
