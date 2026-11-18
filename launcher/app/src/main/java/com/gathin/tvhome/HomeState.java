package com.gathin.tvhome;

import java.time.LocalDate;

/**
 * Tout ce dont le decor a besoin pour se dessiner, en un objet immuable : le decor ne lit ni les
 * preferences ni le reseau, il dessine un etat.
 */
final class HomeState {
    final SolarPhase.Phase phase;
    final WeatherCodes.Condition condition;
    /** Temperature en degres Celsius, NaN si inconnue. */
    final double tempC;
    final boolean fahrenheit;
    final LocalDate date;
    final int hour;
    final int minute;
    final boolean use24h;
    /** Faux : scene figee, aucune animation. */
    final boolean motion;
    /** Nuit tres sombre pour les ecrans OLED. */
    final boolean blackNight;
    /** Vrai quand la meteo affichee vient du cache, sans reseau. */
    final boolean stale;
    /** Soleil et lune : azimut puis hauteur en degres. */
    final double sunAz, sunEl, moonAz, moonEl;
    /** Phase lunaire, 0 (nouvelle) a 1. */
    final double moonPhase;

    HomeState(SolarPhase.Phase phase, WeatherCodes.Condition condition, double tempC, boolean fahrenheit,
              LocalDate date, int hour, int minute, boolean use24h, boolean motion, boolean blackNight,
              boolean stale, double[] sun, double[] moon, double moonPhase) {
        this.phase = phase;
        this.condition = condition;
        this.tempC = tempC;
        this.fahrenheit = fahrenheit;
        this.date = date;
        this.hour = hour;
        this.minute = minute;
        this.use24h = use24h;
        this.motion = motion;
        this.blackNight = blackNight;
        this.stale = stale;
        this.sunAz = sun[0];
        this.sunEl = sun[1];
        this.moonAz = moon[0];
        this.moonEl = moon[1];
        this.moonPhase = moonPhase;
    }

    /** Copie avec une autre heure d'affichage (apercu {@code --es clock 20:52}). */
    HomeState withTime(int h, int m) {
        return new HomeState(phase, condition, tempC, fahrenheit, date, h, m, use24h, motion, blackNight, stale,
                new double[]{sunAz, sunEl}, new double[]{moonAz, moonEl}, moonPhase);
    }

    HomeState with(SolarPhase.Phase p, WeatherCodes.Condition c) {
        return new HomeState(p, c, tempC, fahrenheit, date, hour, minute, use24h, motion, blackNight, stale,
                new double[]{sunAz, sunEl}, new double[]{moonAz, moonEl}, moonPhase);
    }

    /** Texte de l'heure : 24 h "20:52", 12 h "8:52". */
    String timeText() {
        int h = use24h ? hour : (hour % 12 == 0 ? 12 : hour % 12);
        return (use24h && h < 10 ? "0" : "") + h + ":" + (minute < 10 ? "0" : "") + minute;
    }

    /** Temperature arrondie dans l'unite choisie, ex. "13°" ; vide si inconnue. */
    String tempText() {
        if (Double.isNaN(tempC)) return "";
        double v = fahrenheit ? tempC * 9 / 5 + 32 : tempC;
        return Math.round(v) + "°";
    }
}
