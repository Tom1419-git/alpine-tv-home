package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;

public class HomeStateTest {
    private static HomeState at(int h, int m, boolean h24, double temp, boolean f) {
        return new HomeState(SolarPhase.Phase.DAY, WeatherCodes.Condition.CLEAR, temp, f,
                LocalDate.of(2026, 11, 17), h, m, h24, true, false, false,
                new double[]{180, 20}, new double[]{0, -10}, 0.3);
    }

    @Test
    public void formatDeLHeure() {
        assertEquals("20:52", at(20, 52, true, 0, false).timeText());
        assertEquals("08:05", at(8, 5, true, 0, false).timeText());
        assertEquals("8:52", at(20, 52, false, 0, false).timeText());
        assertEquals("12:00", at(0, 0, false, 0, false).timeText());
        assertEquals("12:30", at(12, 30, false, 0, false).timeText());
    }

    @Test
    public void temperature() {
        assertEquals("13°", at(1, 1, true, 13.0, false).tempText());
        assertEquals("55°", at(1, 1, true, 13.0, true).tempText());
        assertEquals("", at(1, 1, true, Double.NaN, false).tempText());
        assertEquals("-3°", at(1, 1, true, -2.6, false).tempText());
    }

    @Test
    public void unEtatCopieResteImmuable() {
        HomeState s = at(10, 0, true, 5, false);
        HomeState t = s.withTime(20, 52);
        assertEquals(10, s.hour);
        assertEquals(20, t.hour);
        assertEquals(WeatherCodes.Condition.RAIN, s.with(SolarPhase.Phase.DUSK, WeatherCodes.Condition.RAIN).condition);
        assertEquals(WeatherCodes.Condition.CLEAR, s.condition);
    }

    @org.junit.Test
    public void laSalutationSuitLHeureDeLaJourneePasLaLumiere() {
        assertEquals(SolarPhase.Phase.NIGHT, HomeState.greetingPhase(3));
        assertEquals(SolarPhase.Phase.DAWN, HomeState.greetingPhase(5));
        assertEquals(SolarPhase.Phase.DAWN, HomeState.greetingPhase(11));
        assertEquals(SolarPhase.Phase.DAY, HomeState.greetingPhase(12));
        assertEquals(SolarPhase.Phase.DAY, HomeState.greetingPhase(16));   // 16 h 22 en novembre : pas "bonsoir"
        assertEquals(SolarPhase.Phase.DUSK, HomeState.greetingPhase(17));
        assertEquals(SolarPhase.Phase.DUSK, HomeState.greetingPhase(21));
        assertEquals(SolarPhase.Phase.NIGHT, HomeState.greetingPhase(22));
        assertEquals(SolarPhase.Phase.NIGHT, HomeState.greetingPhase(0));
    }
}
