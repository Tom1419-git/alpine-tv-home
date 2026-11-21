package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.time.ZonedDateTime;

/**
 * Screensaver de l'accueil alpin : la meme photo, la meme lumiere continue, la meme meteo et la meme heure que l'accueil,
 * sans dock, sans pilules et sans reglages. Le bloc "en lecture" s'affiche quand une musique joue (Spotify compris).
 *
 * Le rendu est celui de {@link AlpineHomeView} en mode screensaver : aucune logique de scene n'est dupliquee. Rien ne tourne
 * hors de {@link #onDreamingStarted} et {@link #onDreamingStopped}. Une touche de la telecommande rend la TV.
 *
 * Choix : Reglages, ou {@code settings put secure screensaver_components com.gathin.tvhome/.AlpineDream} en adb.
 */
public class AlpineDream extends DreamService {
    private AlpineHomeView alpine;
    private MusicBlock musicBlock;
    private MusicController music;
    private boolean musicOn;
    private View dimmer;
    private AlpineSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dimTick = new Runnable() {
        @Override
        public void run() {
            applyDim();
            handler.postDelayed(this, 60_000L);
        }
    };

    /** Part de noir posee sur l'image selon l'heure : la nuit, un screensaver n'a pas a eclairer la piece. */
    static float dimFor(int hour) {
        switch (HomeState.greetingPhase(hour)) {
            case DAY: return 0.10f;
            case DAWN: return 0.20f;
            case DUSK: return 0.30f;
            default: return 0.55f;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // n'importe quelle touche reveille la TV
        setFullscreen(true);
        AlpineSettings as = AlpineSettings.load(this);
        settings = as;
        FrameLayout root = new FrameLayout(this);
        alpine = new AlpineHomeView(this, null);
        alpine.setDream(true);
        root.addView(alpine, new FrameLayout.LayoutParams(-1, -1));
        float d = getResources().getDisplayMetrics().density;
        musicBlock = new MusicBlock(this, null);
        // Lu de loin sur une TV : le bloc est agrandi (pochette comprise), ancre en bas a gauche.
        musicBlock.setPivotX(0f);
        musicBlock.setPivotY(60 * d);
        musicBlock.setScaleX(1.7f);
        musicBlock.setScaleY(1.7f);
        // Le screensaver n'est pas interactif : ni focus ni boutons de transport sur le bloc, juste le titre et la progression.
        musicBlock.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (320 * d), (int) (60 * d),
                Gravity.BOTTOM | Gravity.START);
        lp.setMarginStart((int) (48 * d));
        lp.bottomMargin = (int) (48 * d);
        root.addView(musicBlock, lp);
        dimmer = new View(this);
        dimmer.setBackgroundColor(0xFF000000);
        dimmer.setAlpha(0f);
        root.addView(dimmer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        musicOn = as.music;
        if (musicOn) {
            music = new MusicController(this, musicBlock::show);
            musicBlock.setController(music);
        }
        alpine.configure(as, Config.load(this).clockFormat, new DrawnAlpineBackdrop());
        if (as.photo && WeatherPack.knownGood(this)) {
            WeatherPackManifest manifest = WeatherPack.manifest(this);
            if (manifest != null) {
                alpine.useBackdrop(new PhotoBackdrop(this, manifest, () -> alpine.useBackdrop(new DrawnAlpineBackdrop())));
            }
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        alpine.start();
        handler.post(dimTick);
        if (musicOn) {
            musicBlock.start();
            music.start();
        }
    }

    @Override
    public void onDreamingStopped() {
        stopAll();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        stopAll();
        super.onDetachedFromWindow();
    }

    /** Part de noir et luminosite de la fenetre (certaines TV l'ignorent, le voile noir fait alors le travail). */
    private void applyDim() {
        if (settings == null || !settings.dreamDim) return;
        int h = ZonedDateTime.now(settings.zone).getHour();
        float dim = dimFor(h);
        dimmer.setAlpha(dim);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.1f, 1f - dim);
        getWindow().setAttributes(lp);
    }

    private void stopAll() {
        handler.removeCallbacks(dimTick);
        if (alpine != null) alpine.stop();
        if (musicOn && music != null) {
            music.stop();
            musicBlock.stop();
        }
    }
}
