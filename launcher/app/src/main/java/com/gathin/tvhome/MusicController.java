package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lit la session media de la musique en cours, sans compte, sans SDK et sans creer de session : seulement
 * {@link MediaSessionManager#getActiveSessions} (grace a {@link MusicListener}), un abonnement aux changements et les
 * commandes de transport de la session de l'autre application. Branche entre {@link #start} et {@link #stop} :
 * Home derriere une autre application ne garde ni abonnement ni reference.
 */
final class MusicController {
    /** Ce que le bloc affiche. Immuable. */
    static final class Track {
        final String title;
        final String artist;
        @Nullable final Bitmap art;
        final long durationMs;
        final long positionMs;
        /** Instant (elapsedRealtime) auquel {@code positionMs} etait vrai. */
        final long positionAtMs;
        final float speed;
        final boolean playing;
        final long pausedSinceMs;
        final String pkg;

        Track(String title, String artist, @Nullable Bitmap art, long durationMs, long positionMs, long positionAtMs,
              float speed, boolean playing, long pausedSinceMs, String pkg) {
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.durationMs = durationMs;
            this.positionMs = positionMs;
            this.positionAtMs = positionAtMs;
            this.speed = speed;
            this.playing = playing;
            this.pausedSinceMs = pausedSinceMs;
            this.pkg = pkg;
        }

        /** Position courante : position de base plus le temps ecoule, bornee a la duree. */
        long positionNow() {
            if (!playing) return positionMs;
            long p = positionMs + (long) ((SystemClock.elapsedRealtime() - positionAtMs) * speed);
            return durationMs > 0 ? Math.min(p, durationMs) : p;
        }
    }

    interface Listener {
        /** Sur le fil principal : la musique a afficher, ou null (rien, ou pas de permission). */
        void onTrack(@Nullable Track track);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ComponentName listenerComponent;
    private MediaSessionManager manager;
    private MediaController watched;
    private boolean started;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            controllers -> refresh();

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            refresh();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            refresh();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    MusicController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.listenerComponent = new ComponentName(this.context, MusicListener.class);
    }

    /** Vrai si Android a donne l'acces aux notifications a l'ecouteur. */
    boolean hasAccess() {
        String enabled = android.provider.Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(listenerComponent.flattenToString());
    }

    void start() {
        if (started) return;
        started = true;
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null || !hasAccess()) {
            listener.onTrack(null);   // sans permission : bloc absent, aucune insistance
            return;
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, main);
        } catch (SecurityException e) {
            listener.onTrack(null);
            return;
        }
        refresh();
    }

    void stop() {
        started = false;
        unwatch();
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (RuntimeException ignored) {
                // deja desabonne
            }
        }
        manager = null;
    }

    private void unwatch() {
        if (watched != null) {
            try {
                watched.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
                // session deja detruite
            }
            watched = null;
        }
    }

    /** Relit les sessions et publie ce que la politique autorise. */
    private void refresh() {
        if (!started || manager == null) return;
        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(listenerComponent);
        } catch (SecurityException e) {
            unwatch();
            listener.onTrack(null);
            return;
        }
        long wallNow = System.currentTimeMillis();
        long elapsedNow = SystemClock.elapsedRealtime();
        List<MediaPolicy.Candidate> candidates = new ArrayList<>();
        for (MediaController c : controllers) candidates.add(candidate(c, wallNow, elapsedNow));
        MediaPolicy.Candidate chosen = MediaPolicy.pick(candidates, MediaPolicy.DEFAULT_WHITELIST, wallNow);
        MediaController controller = null;
        if (chosen != null) controller = controllers.get(candidates.indexOf(chosen));
        if (controller != watched) {
            unwatch();
            if (controller != null) {
                controller.registerCallback(callback, main);
                watched = controller;
            }
        }
        listener.onTrack(controller == null ? null : track(controller, chosen, elapsedNow));
    }

    private static MediaPolicy.Candidate candidate(MediaController c, long wallNow, long elapsedNow) {
        MediaMetadata md = c.getMetadata();
        PlaybackState ps = c.getPlaybackState();
        String artist = md == null ? null : firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        MediaPolicy.Play play = MediaPolicy.Play.OTHER;
        long pausedSince = 0;
        if (ps != null) {
            int s = ps.getState();
            if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING) {
                play = MediaPolicy.Play.PLAYING;
            } else if (s == PlaybackState.STATE_PAUSED) {
                play = MediaPolicy.Play.PAUSED;
                long ago = Math.max(0, elapsedNow - ps.getLastPositionUpdateTime());
                pausedSince = wallNow - ago;
            }
        }
        return new MediaPolicy.Candidate(c.getPackageName(), title, artist, play, pausedSince);
    }

    private static Track track(MediaController c, MediaPolicy.Candidate chosen, long elapsedNow) {
        MediaMetadata md = c.getMetadata();
        PlaybackState ps = c.getPlaybackState();
        Bitmap art = null;
        long duration = 0;
        if (md != null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }
        long pos = ps == null ? 0 : Math.max(0, ps.getPosition());
        long at = ps == null ? elapsedNow : ps.getLastPositionUpdateTime();
        float speed = ps == null || ps.getPlaybackSpeed() <= 0 ? 1f : ps.getPlaybackSpeed();
        String title = chosen.title == null || chosen.title.trim().isEmpty() ? chosen.artist : chosen.title;
        return new Track(title, chosen.artist, art, duration, pos, at, speed,
                chosen.state == MediaPolicy.Play.PLAYING, chosen.pausedSinceMs, chosen.pkg);
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.trim().isEmpty() ? a : b;
    }

    // ---- commandes -----------------------------------------------------------------------------------------

    void play() {
        if (watched != null) watched.getTransportControls().play();
    }

    void pause() {
        if (watched != null) watched.getTransportControls().pause();
    }

    void next() {
        if (watched != null) watched.getTransportControls().skipToNext();
    }

    void previous() {
        if (watched != null) watched.getTransportControls().skipToPrevious();
    }
}
