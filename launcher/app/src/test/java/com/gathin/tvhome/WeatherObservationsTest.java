package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Les observations d'Open-Meteo (nuages, precipitations, visibilite, vent) pilotent l'intensite des effets. */
public class WeatherObservationsTest {
    private static String body(String extra) {
        return "{\"current\":{\"temperature_2m\":7.5,\"weather_code\":63,\"is_day\":1" + extra + "}}";
    }

    @Test
    public void lesObservationsFacultativesSontLues() {
        WeatherData d = WeatherData.parse(body(",\"cloud_cover\":90,\"precipitation\":2.0,\"wind_speed_10m\":30.0,\"visibility\":4000"), 1000);
        assertEquals(90f, d.cloudPct, 0f);
        assertEquals(2f, d.precipMm, 0f);
        assertEquals(30f, d.windKmh, 0f);
        assertEquals(4000f, d.visibilityM, 0f);
    }

    @Test
    public void uneObservationAbsenteOuInvalideVautMoinsUn() {
        WeatherData d = WeatherData.parse(body(",\"cloud_cover\":-5,\"precipitation\":\"x\""), 1000);
        assertEquals(-1f, d.cloudPct, 0f);
        assertEquals(-1f, d.precipMm, 0f);
        assertEquals(-1f, d.windKmh, 0f);
        assertEquals(-1f, d.visibilityM, 0f);
        assertEquals(63, d.code);
    }

    @Test
    public void laRequeteDemandeLesObservations() {
        String url = WeatherRepository.url(46.5, 6.6);
        for (String v : new String[]{"cloud_cover", "precipitation", "wind_speed_10m", "visibility"}) assertTrue(v, url.contains(v));
    }

    @Test
    public void lesObservationsDeLaPluieDonnentLesCiblesDeLInterface() {
        // memes valeurs que tools/alpine_preview/grade.js (deriveTargets) pour pluie, 90 % de nuages, 2 mm/h, 4 km, 30 km/h
        WeatherData d = new WeatherData(7.5, 63, true, -1, -1, 1000, 90f, 2f, 30f, 4000f);
        SceneMix m = new SceneMix();
        m.targetWeather(WeatherCodes.Condition.RAIN, d);
        assertEquals(0.9f, m.tCloud, 1e-3f);
        assertEquals(0.5482f, m.tRain, 2e-3f);
        assertEquals(0.1236f, m.tFog, 2e-3f);
        assertEquals(0.6667f, m.tWind, 1e-3f);
    }

    @Test
    public void sansObservationLesValeursParDefautDeLaConditionRestent() {
        SceneMix a = new SceneMix(), b = new SceneMix();
        a.targetCondition(WeatherCodes.Condition.SNOW);
        b.targetWeather(WeatherCodes.Condition.SNOW, null);
        assertEquals(a.tSnow, b.tSnow, 0f);
        assertEquals(a.tCloud, b.tCloud, 0f);
        WeatherData d = new WeatherData(0, 71, true, -1, -1, 1000);
        SceneMix c = new SceneMix();
        c.targetWeather(WeatherCodes.Condition.SNOW, d);
        assertEquals(a.tSnow, c.tSnow, 0f);
    }

    @Test
    public void laBruineResteLegereMemeAvecBeaucoupDeprecipitation() {
        SceneMix m = new SceneMix();
        m.targetWeather(WeatherCodes.Condition.DRIZZLE, new WeatherData(5, 53, true, -1, -1, 1, 95f, 8f, 5f, 9000f));
        assertTrue(m.tRain <= 0.35f + 1e-6f);
    }

    @Test
    public void unBrouillardDeVisibiliteFaibleEpaississLaBrume() {
        SceneMix m = new SceneMix();
        m.targetWeather(WeatherCodes.Condition.FOG, new WeatherData(2, 45, true, -1, -1, 1, 70f, 0f, 3f, 300f));
        assertTrue(m.tFog > 0.8f);
        SceneMix clair = new SceneMix();
        clair.targetWeather(WeatherCodes.Condition.CLEAR, new WeatherData(2, 0, true, -1, -1, 1, 5f, 0f, 3f, 30000f));
        assertEquals(0f, clair.tFog, 1e-6f);
    }

    @Test
    public void uneMiseAJourGlisseVersLaNouvelleCibleSansSaut() {
        SceneMix m = new SceneMix();
        m.targetWeather(WeatherCodes.Condition.CLEAR, null);
        m.snap();
        m.targetWeather(WeatherCodes.Condition.RAIN, new WeatherData(5, 63, true, -1, -1, 1, 100f, 4f, 40f, 5000f));
        float prev = m.rain, worst = 0f;
        for (int i = 0; i < 450; i++) {   // 15 s : la meteo glisse en une dizaine de secondes
            m.step(1f / 30f);
            worst = Math.max(worst, m.rain - prev);
            assertTrue("monotone", m.rain >= prev - 1e-6f);
            prev = m.rain;
        }
        assertTrue("pas de saut : " + worst, worst < 0.02f);
        assertEquals(m.tRain, m.rain, 0.05f);
        assertTrue(m.wind > 0.5f);
    }

    @Test
    public void leCycleDuSoleilSuitLaPositionReelleDeLausanneEtLaDate() {
        // memes valeurs que tools/alpine_preview/grade.js (sunPosition) : Lausanne, 17 novembre 2026, UTC+1
        java.time.LocalDate day = java.time.LocalDate.of(2026, 11, 17);
        double[][] expected = {{60, -60.9}, {458, -0.7}, {750, 24.6}, {1020, -0.9}, {1380, -58.0}};
        for (double[] e : expected) {
            double el = SolarPhase.sunPosition(day, (int) e[0], 46.516, 6.63282, 60)[1];
            assertEquals("minute " + (int) e[0], e[1], el, 0.3);
        }
        int[] riseSet = SolarPhase.riseAndSet(day, 46.516, 6.63282, java.time.ZoneId.of("Europe/Zurich"));
        assertEquals(458, riseSet[0], 3);   // 07:38
        assertEquals(1020, riseSet[1], 3);  // 17:00
    }
}
