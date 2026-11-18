package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Les quatre phases de la journee de l'accueil alpin, deduites du lever et du coucher du soleil.
 * Calcul local (equations NOAA via {@link Sun}), donc valable sans reseau. Logique pure, sans type Android,
 * testee en JVM.
 */
final class SolarPhase {
    enum Phase { DAWN, DAY, DUSK, NIGHT }

    /** Aube : de 40 min avant le lever a 50 min apres. */
    static final int DAWN_BEFORE = 40;
    static final int DAWN_AFTER = 50;
    /** Crepuscule : de 50 min avant le coucher a 40 min apres. */
    static final int DUSK_BEFORE = 50;
    static final int DUSK_AFTER = 40;

    /** Repli sans coordonnees : aube 06:20, jour 07:50, crepuscule 18:10, nuit 19:40. */
    private static final int FB_DAWN = 6 * 60 + 20;
    private static final int FB_DAY = 7 * 60 + 50;
    private static final int FB_DUSK = 18 * 60 + 10;
    private static final int FB_NIGHT = 19 * 60 + 40;

    private SolarPhase() {
    }

    /** Lever et coucher du jour (minutes locales), ou null sans coordonnees valides ou en jour/nuit polaire. */
    static int[] riseAndSet(LocalDate date, double lat, double lon, ZoneId zone) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        return Sun.riseAndSet(date, lat, lon, zone);
    }

    /** Phase a {@code minute} (0 a 1439) ; {@code riseSet} null applique le repli fixe. */
    static Phase at(int minute, int[] riseSet) {
        int dawn, day, dusk, night;
        if (riseSet == null) {
            dawn = FB_DAWN; day = FB_DAY; dusk = FB_DUSK; night = FB_NIGHT;
        } else {
            dawn = riseSet[0] - DAWN_BEFORE;
            day = riseSet[0] + DAWN_AFTER;
            dusk = riseSet[1] - DUSK_BEFORE;
            night = riseSet[1] + DUSK_AFTER;
        }
        if (minute >= dawn && minute < day) return Phase.DAWN;
        if (minute >= day && minute < dusk) return Phase.DAY;
        if (minute >= dusk && minute < night) return Phase.DUSK;
        return Phase.NIGHT;
    }

    /**
     * Ciel d'un apercu nomme : {hauteur du soleil en degres, azimut, 0 le matin ou 1 le soir}. Les huit moments de la
     * comparaison (avant-lever, lever, matin, midi, apres-midi, coucher, crepuscule, nuit) et les quatre phases d'origine
     * (dawn, day, dusk, night). null si le nom est inconnu.
     */
    static float[] previewSky(String name) {
        if (name == null) return null;
        switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "avant-lever": return new float[]{-7f, 95f, 0f};
            case "lever": return new float[]{0.5f, 100f, 0f};
            case "matin": return new float[]{18f, 140f, 0f};
            case "midi": return new float[]{40f, 180f, 0f};
            case "apres-midi": return new float[]{20f, 230f, 1f};
            case "coucher": return new float[]{0.5f, 260f, 1f};
            case "crepuscule": return new float[]{-6f, 265f, 1f};
            case "nuit": case "night": return new float[]{-20f, 180f, 1f};
            case "dawn": return new float[]{-2f, 100f, 0f};
            case "day": return new float[]{25f, 180f, 0f};
            case "dusk": return new float[]{-2f, 260f, 1f};
            default: return null;
        }
    }

    /** Phase (pour la salutation) correspondant a une hauteur de soleil et au matin ou au soir. */
    static Phase phaseOf(float elevation, boolean evening) {
        if (elevation < -8f) return Phase.NIGHT;
        if (elevation < 6f) return evening ? Phase.DUSK : Phase.DAWN;
        return Phase.DAY;
    }

    static Phase parse(String name) {
        if (name != null) {
            for (Phase p : Phase.values()) {
                if (p.name().equalsIgnoreCase(name.trim())) return p;
            }
        }
        return null;
    }

    /**
     * Position du soleil, equations NOAA : {@code {azimut en degres depuis le nord, hauteur en degres}}.
     * {@code minute} est l'heure locale en minutes, {@code offsetMin} le decalage UTC de la zone.
     */
    static double[] sunPosition(LocalDate date, int minute, double lat, double lon, int offsetMin) {
        double g = 2 * Math.PI / 365 * (date.getDayOfYear() - 1 + (minute - 720) / 1440.0);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double tst = minute + eqTime + 4 * lon - offsetMin;
        return horizontal(Math.toRadians(tst / 4 - 180), decl, Math.toRadians(lat));
    }

    /**
     * Position approchee de la lune (ecliptique confondue avec l'orbite lunaire, precision de quelques degres,
     * suffisante pour un decor) : meme format que {@link #sunPosition}.
     */
    static double[] moonPosition(LocalDate date, int minute, double lat, double lon, int offsetMin) {
        double day = date.getDayOfYear() - 1 + (minute - 720) / 1440.0;
        double sunLon = Math.toRadians((day - 79.5) * 360 / 365.25);
        double elong = 2 * Math.PI * moonPhase(date, minute, offsetMin);
        double moonLon = sunLon + elong;
        double obl = Math.toRadians(23.44);
        double decl = Math.asin(Math.sin(obl) * Math.sin(moonLon));
        double raMoon = Math.atan2(Math.cos(obl) * Math.sin(moonLon), Math.cos(moonLon));
        double raSun = Math.atan2(Math.cos(obl) * Math.sin(sunLon), Math.cos(sunLon));
        double sunHa = Math.toRadians((minute + 4 * lon - offsetMin) / 4 - 180);
        return horizontal(sunHa - (raMoon - raSun), decl, Math.toRadians(lat));
    }

    /** Phase lunaire de 0 (nouvelle lune) a 1, par rapport a la nouvelle lune du 6 janvier 2000. */
    static double moonPhase(LocalDate date, int minute, int offsetMin) {
        double days = date.toEpochDay() - LocalDate.of(2000, 1, 6).toEpochDay()
                + (minute - offsetMin) / 1440.0 - 18.2 / 24;
        double p = (days / 29.530588853) % 1.0;
        return p < 0 ? p + 1 : p;
    }

    private static double[] horizontal(double ha, double decl, double lat) {
        double sinEl = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(ha);
        double el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
        double az = Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat));
        return new double[]{(Math.toDegrees(az) + 180 + 360) % 360, Math.toDegrees(el)};
    }
}
