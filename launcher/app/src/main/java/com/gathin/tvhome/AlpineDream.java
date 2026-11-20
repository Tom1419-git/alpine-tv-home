package com.gathin.tvhome;

import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;

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

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // n'importe quelle touche reveille la TV
        setFullscreen(true);
        AlpineSettings as = AlpineSettings.load(this);
        FrameLayout root = new FrameLayout(this);
        alpine = new AlpineHomeView(this, null);
        alpine.setDream(true);
        root.addView(alpine, new FrameLayout.LayoutParams(-1, -1));
        float d = getResources().getDisplayMetrics().density;
        musicBlock = new MusicBlock(this, null);
        // Le screensaver n'est pas interactif : ni focus ni boutons de transport sur le bloc, juste le titre et la progression.
        musicBlock.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (320 * d), (int) (60 * d),
                Gravity.BOTTOM | Gravity.START);
        lp.setMarginStart((int) (48 * d));
        lp.bottomMargin = (int) (48 * d);
        root.addView(musicBlock, lp);
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

    private void stopAll() {
        if (alpine != null) alpine.stop();
        if (musicOn && music != null) {
            music.stop();
            musicBlock.stop();
        }
    }
}
