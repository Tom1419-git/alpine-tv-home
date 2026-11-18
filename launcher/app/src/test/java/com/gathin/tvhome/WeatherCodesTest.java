package com.gathin.tvhome;

import static com.gathin.tvhome.WeatherCodes.Condition.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherCodesTest {
    private static void range(int from, int to, WeatherCodes.Condition expected) {
        for (int c = from; c <= to; c++) assertEquals("code " + c, expected, WeatherCodes.map(c));
    }

    @Test
    public void tableWmoComplete() {
        range(0, 1, CLEAR);
        assertEquals(PARTLY, WeatherCodes.map(2));
        assertEquals(OVERCAST, WeatherCodes.map(3));
        assertEquals(FOG, WeatherCodes.map(45));
        assertEquals(FOG, WeatherCodes.map(48));
        range(51, 57, DRIZZLE);
        range(61, 67, RAIN);
        range(71, 77, SNOW);
        range(80, 82, SHOWERS);
        range(85, 86, SNOW);
        range(95, 99, STORM);
    }

    @Test
    public void codeInconnuGardeUneConditionNeutre() {
        assertEquals(OVERCAST, WeatherCodes.map(-1));
        assertEquals(OVERCAST, WeatherCodes.map(4));
        assertEquals(OVERCAST, WeatherCodes.map(58));
        assertEquals(OVERCAST, WeatherCodes.map(1234));
    }

    @Test
    public void parseNom() {
        assertEquals(RAIN, WeatherCodes.parse("Rain"));
        assertNull(WeatherCodes.parse("grele"));
    }
}
