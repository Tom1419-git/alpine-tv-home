package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherDataTest {
    /** Reponse reelle relevee le 17/11/2026. */
    private static final String REAL = "{\"latitude\":46.516,\"longitude\":6.63282,\"timezone\":\"Europe/Zurich\","
            + "\"current\":{\"time\":\"2026-11-17T18:15\",\"interval\":900,\"temperature_2m\":13.0,"
            + "\"weather_code\":3,\"is_day\":0},\"daily\":{\"time\":[\"2026-11-17\"],"
            + "\"sunrise\":[\"2026-11-17T07:38\"],\"sunset\":[\"2026-11-17T16:58\"]}}";

    @Test
    public void reponseValide() {
        WeatherData d = WeatherData.parse(REAL, 1000);
        assertNotNull(d);
        assertEquals(13.0, d.tempC, 0.001);
        assertEquals(3, d.code);
        assertEquals(WeatherCodes.Condition.OVERCAST, d.condition);
        assertFalse(d.isDay);
        assertEquals(7 * 60 + 38, d.sunriseMin);
        assertEquals(16 * 60 + 58, d.sunsetMin);
        assertEquals(1000, d.fetchedAtMs);
    }

    @Test
    public void sansDailyLaMeteoCouranteSuffit() {
        WeatherData d = WeatherData.parse("{\"current\":{\"temperature_2m\":-2.5,\"weather_code\":71}}", 5);
        assertNotNull(d);
        assertEquals(-1, d.sunriseMin);
        assertTrue(d.isDay);
        assertEquals(WeatherCodes.Condition.SNOW, d.condition);
    }

    @Test
    public void entreesInvalidesDonnentNull() {
        assertNull(WeatherData.parse(null, 0));
        assertNull(WeatherData.parse("", 0));
        assertNull(WeatherData.parse("   ", 0));
        assertNull(WeatherData.parse("{}", 0));
        assertNull(WeatherData.parse("pas du json", 0));
        assertNull(WeatherData.parse("{\"current\":{\"weather_code\":3}}", 0));
        assertNull(WeatherData.parse("{\"current\":{\"temperature_2m\":\"chaud\",\"weather_code\":3}}", 0));
        assertNull(WeatherData.parse(REAL.substring(0, 60), 0));
    }

    @Test
    public void heuresIllisiblesDonnentMoinsUn() {
        WeatherData d = WeatherData.parse("{\"current\":{\"temperature_2m\":1,\"weather_code\":0},"
                + "\"daily\":{\"sunrise\":[\"n'importe quoi\"],\"sunset\":[\"2026-11-17T99:99\"]}}", 0);
        assertNotNull(d);
        assertEquals(-1, d.sunriseMin);
        assertEquals(-1, d.sunsetMin);
    }

    @Test
    public void cacheAllerRetour() {
        WeatherData d = WeatherData.fromCache(WeatherData.toCache(REAL, 4242));
        assertNotNull(d);
        assertEquals(4242, d.fetchedAtMs);
        assertEquals(13.0, d.tempC, 0.001);
    }

    @Test
    public void cacheCorrompuOuTronque() {
        String good = WeatherData.toCache(REAL, 4242);
        assertNull(WeatherData.fromCache(null));
        assertNull(WeatherData.fromCache(""));
        assertNull(WeatherData.fromCache(good.substring(0, good.length() / 2)));
        assertNull(WeatherData.fromCache("{\"t\":1,\"body\":\"{\\\"current\\\":\"}"));
        assertNull(WeatherData.fromCache("\u0000\u0000\u0000"));
    }

    @Test
    public void age() {
        WeatherData d = new WeatherData(10, 0, true, -1, -1, 1000);
        assertFalse(d.olderThan(1000 + 29 * 60_000L, 30 * 60_000L));
        assertTrue(d.olderThan(1000 + 31 * 60_000L, 30 * 60_000L));
        assertTrue(d.olderThan(500, 30 * 60_000L));   // horloge reculee : on rafraichit
        assertTrue(new WeatherData(10, 0, true, -1, -1, 0).olderThan(5, 30 * 60_000L));
    }
}
