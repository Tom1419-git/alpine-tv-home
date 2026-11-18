package com.gathin.tvhome;

/**
 * L'etat visuel lisse de la scene : hauteur et azimut du soleil, de la lune, et l'intensite de chaque effet
 * meteo (nuages, brouillard, pluie, neige, orage). Chaque valeur glisse vers sa cible avec un amortissement
 * exponentiel : un changement de phase ou de meteo est donc un fondu continu (une pluie qui devient neige monte et
 * descend en intensite, le soleil parcourt les hauteurs intermediaires), jamais un remplacement brutal. Une cible qui
 * change pendant le glissement redirige simplement la valeur, sans empiler de calque ni sauter.
 * Logique pure, sans type Android ; les champs sont mis a jour dans {@link #step}, sans allocation.
 */
final class SceneMix {
    /** Constante de temps du lissage : environ 95 % du chemin en 2 secondes. */
    static final float TAU = 0.65f;
    /** La meteo glisse plus lentement que le ciel : 95 % du chemin en une dizaine de secondes. */
    static final float TAU_WEATHER = 3.0f;

    float elevation, azimuth, moonElevation, moonAzimuth, evening;
    float cloud, fog, rain, snow, storm, showers, wind;
    /** Cibles. */
    float tElevation, tAzimuth, tMoonElevation, tMoonAzimuth, tEvening;
    float tCloud, tFog, tRain, tSnow, tStorm, tShowers, tWind = 0.18f;
    boolean initialized;

    /** Vrai tant qu'une valeur n'a pas atteint sa cible (le rendu doit continuer a produire des images). */
    boolean settling() {
        return !initialized || far(elevation, tElevation, 0.05f) || far(azimuth, tAzimuth, 0.05f)
                || far(moonElevation, tMoonElevation, 0.05f) || far(moonAzimuth, tMoonAzimuth, 0.05f)
                || far(evening, tEvening, 0.002f) || far(cloud, tCloud, 0.002f) || far(fog, tFog, 0.002f)
                || far(rain, tRain, 0.002f) || far(snow, tSnow, 0.002f) || far(storm, tStorm, 0.002f)
                || far(showers, tShowers, 0.002f) || far(wind, tWind, 0.002f);
    }

    private static boolean far(float a, float b, float eps) {
        return Math.abs(a - b) > eps;
    }

    /** Fixe la meteo cible pour une condition. */
    void targetCondition(WeatherCodes.Condition c) {
        float cl = 0, fo = 0, ra = 0, sn = 0, st = 0, sh = 0;
        switch (c) {
            case PARTLY: cl = 0.35f; break;
            case OVERCAST: cl = 1f; break;
            case FOG: fo = 0.85f; cl = 0.45f; break;
            case DRIZZLE: ra = 0.30f; cl = 0.85f; break;
            case RAIN: ra = 0.75f; cl = 0.95f; break;
            case SHOWERS: ra = 0.90f; cl = 0.90f; sh = 1f; break;
            case SNOW: sn = 0.85f; cl = 0.85f; break;
            case STORM: st = 1f; ra = 0.90f; cl = 1f; break;
            default: break;
        }
        tCloud = cl;
        tFog = fo;
        tRain = ra;
        tSnow = sn;
        tStorm = st;
        tShowers = sh;
        tWind = 0.18f;
    }

    /**
     * Cible meteo : la condition du code WMO, precisee par les observations quand la reponse en contient (nuages,
     * precipitations, visibilite, vent). Sans observation, les valeurs par defaut de la condition. Les cibles seules
     * changent : la scene glisse ensuite vers elles (voir {@link #step}).
     */
    void targetWeather(WeatherCodes.Condition c, WeatherData d) {
        targetCondition(c);
        if (d == null) return;
        boolean snowy = c == WeatherCodes.Condition.SNOW;
        boolean rainy = c == WeatherCodes.Condition.DRIZZLE || c == WeatherCodes.Condition.RAIN
                || c == WeatherCodes.Condition.SHOWERS || c == WeatherCodes.Condition.STORM;
        if (d.cloudPct >= 0) {
            float cl = clamp01(d.cloudPct / 100f);
            tCloud = Math.max(cl, Math.max(snowy || rainy ? 0.7f : 0f, c == WeatherCodes.Condition.OVERCAST ? 0.85f : 0f));
        }
        if (d.precipMm >= 0) {
            if (snowy) {
                tSnow = clamp01(0.22f + 0.78f * (1f - (float) Math.exp(-d.precipMm / 1.2f)));
            } else if (rainy) {
                float r = clamp01(0.12f + 0.88f * (1f - (float) Math.exp(-d.precipMm / 3f)));
                tRain = c == WeatherCodes.Condition.DRIZZLE ? Math.min(0.35f, r) : Math.max(0.4f, r);
            }
        }
        if (d.visibilityM >= 0) {
            float f = 0.9f * (1f - SceneGrade.smoothstep(200f, 6000f, d.visibilityM));
            tFog = Math.max(tFog > 0f ? 0.45f : 0f, f * (snowy || rainy ? 0.5f : 1f));
        }
        if (d.windKmh >= 0) tWind = clamp01(d.windKmh / 45f);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    /** Fixe le soleil et la lune cibles. */
    void targetSky(float elevation, float azimuth, float moonElevation, float moonAzimuth, float evening) {
        tElevation = elevation;
        tAzimuth = azimuth;
        tMoonElevation = moonElevation;
        tMoonAzimuth = moonAzimuth;
        tEvening = evening;
    }

    /** Pose directement les valeurs sur les cibles : au retour au premier plan, aucune transition perimee n'est rejouee. */
    void snap() {
        elevation = tElevation;
        azimuth = tAzimuth;
        moonElevation = tMoonElevation;
        moonAzimuth = tMoonAzimuth;
        evening = tEvening;
        cloud = tCloud;
        fog = tFog;
        rain = tRain;
        snow = tSnow;
        storm = tStorm;
        showers = tShowers;
        wind = tWind;
        initialized = true;
    }

    /** Fait avancer toutes les valeurs de {@code dt} secondes vers leurs cibles. */
    void step(float dt) {
        if (!initialized) {
            snap();
            return;
        }
        float d = Math.max(0f, Math.min(dt, 0.25f));
        float k = 1f - (float) Math.exp(-d / TAU);
        float kw = 1f - (float) Math.exp(-d / TAU_WEATHER);
        elevation += (tElevation - elevation) * k;
        azimuth += angle(tAzimuth - azimuth) * k;
        moonElevation += (tMoonElevation - moonElevation) * k;
        moonAzimuth += angle(tMoonAzimuth - moonAzimuth) * k;
        evening += (tEvening - evening) * k;
        cloud += (tCloud - cloud) * kw;
        fog += (tFog - fog) * kw;
        rain += (tRain - rain) * kw;
        snow += (tSnow - snow) * kw;
        storm += (tStorm - storm) * kw;
        showers += (tShowers - showers) * kw;
        wind += (tWind - wind) * kw;
    }

    /** Ecart d'angle ramene entre -180 et 180 : l'azimut prend le plus court chemin. */
    static float angle(float d) {
        while (d > 180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    /** Copie les valeurs courantes (un test compare deux etats). */
    SceneMix copy() {
        SceneMix m = new SceneMix();
        m.elevation = elevation; m.azimuth = azimuth; m.moonElevation = moonElevation; m.moonAzimuth = moonAzimuth;
        m.evening = evening; m.cloud = cloud; m.fog = fog; m.rain = rain; m.snow = snow; m.storm = storm; m.showers = showers; m.wind = wind;
        m.tElevation = tElevation; m.tAzimuth = tAzimuth; m.tMoonElevation = tMoonElevation;
        m.tMoonAzimuth = tMoonAzimuth; m.tEvening = tEvening; m.tCloud = tCloud; m.tFog = tFog; m.tRain = tRain;
        m.tSnow = tSnow; m.tStorm = tStorm; m.tShowers = tShowers; m.tWind = tWind; m.initialized = initialized;
        return m;
    }
}
