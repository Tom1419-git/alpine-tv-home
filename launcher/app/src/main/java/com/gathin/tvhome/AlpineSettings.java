package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.ZoneId;

/**
 * Reglages de l'accueil alpin, lus dans les memes preferences que les autres reglages du lanceur
 * ({@link Config#SETTINGS_NAME}). Valeurs par defaut : Lausanne, degres Celsius, mouvement actif.
 */
final class AlpineSettings {
    static final String ENABLED = "alpine_home";
    static final String CITY = "alpine_city";
    static final String LAT = "alpine_lat";
    static final String LON = "alpine_lon";
    static final String ZONE = "alpine_zone";
    static final String FAHRENHEIT = "alpine_fahrenheit";
    static final String MOTION = "alpine_motion";
    static final String BLACK_NIGHT = "alpine_black_night";
    static final String PHOTO = "alpine_photo";
    static final String MUSIC = "alpine_music";
    static final String PHOTO_ID = "alpine_photo_id";
    static final String DREAM_DIM = "alpine_dream_dim";
    static final String PREVIEW_PHASE = "alpine_preview_phase";
    static final String PREVIEW_COND = "alpine_preview_cond";

    static final String DEFAULT_CITY = "Lausanne";
    static final String DEFAULT_LAT = "46.516";
    static final String DEFAULT_LON = "6.63282";
    static final String DEFAULT_ZONE = "Europe/Zurich";

    final boolean enabled;
    final String city;
    final double lat;
    final double lon;
    final ZoneId zone;
    final boolean fahrenheit;
    final boolean motion;
    final boolean blackNight;
    final boolean photo;
    final boolean music;
    /** Screensaver : image assombrie et luminosite baissee selon l'heure (actif par defaut). */
    final boolean dreamDim;
    /** Apercu choisi dans les reglages ("" : valeur reelle). */
    final String previewPhase;
    final String previewCondition;

    private AlpineSettings(SharedPreferences p, boolean enabledDefault) {
        enabled = p.getBoolean(ENABLED, enabledDefault);
        city = p.getString(CITY, DEFAULT_CITY);
        lat = parse(p.getString(LAT, DEFAULT_LAT), -90, 90);
        lon = parse(p.getString(LON, DEFAULT_LON), -180, 180);
        zone = zone(p.getString(ZONE, DEFAULT_ZONE));
        fahrenheit = p.getBoolean(FAHRENHEIT, false);
        motion = p.getBoolean(MOTION, true);
        blackNight = p.getBoolean(BLACK_NIGHT, false);
        photo = p.getBoolean(PHOTO, true);
        music = p.getBoolean(MUSIC, true);
        dreamDim = p.getBoolean(DREAM_DIM, true);
        previewPhase = p.getString(PREVIEW_PHASE, "");
        previewCondition = p.getString(PREVIEW_COND, "");
    }

    static AlpineSettings load(Context context) {
        SharedPreferences p = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return new AlpineSettings(p, context.getResources().getBoolean(R.bool.alpine_home));
    }

    /** Nombre entre {@code min} et {@code max}, ou NaN (pas de coordonnees : phases de repli). */
    static double parse(String text, double min, double max) {
        if (text == null) return Double.NaN;
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.'));
            return v >= min && v <= max ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
