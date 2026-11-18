package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

public class SolarPhaseTest {
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);

    private static int hm(int h, int m) {
        return h * 60 + m;
    }

    @Test
    public void leverEtCoucherALausanne() {
        int[] rs = SolarPhase.riseAndSet(DAY, 46.516, 6.63282, ZURICH);
        assertNotNull(rs);
        assertEquals(hm(7, 38), rs[0], 4);
        assertEquals(hm(16, 58), rs[1], 4);
    }

    @Test
    public void bornesDePhaseCeJourLa() {
        int[] rs = {hm(7, 38), hm(16, 58)};
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(hm(6, 57), rs));
        assertEquals(SolarPhase.Phase.DAWN, SolarPhase.at(hm(6, 58), rs));
        assertEquals(SolarPhase.Phase.DAWN, SolarPhase.at(hm(8, 27), rs));
        assertEquals(SolarPhase.Phase.DAY, SolarPhase.at(hm(8, 28), rs));
        assertEquals(SolarPhase.Phase.DAY, SolarPhase.at(hm(16, 7), rs));
        assertEquals(SolarPhase.Phase.DUSK, SolarPhase.at(hm(16, 8), rs));
        assertEquals(SolarPhase.Phase.DUSK, SolarPhase.at(hm(17, 37), rs));
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(hm(17, 38), rs));
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(0, rs));
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(hm(23, 59), rs));
    }

    @Test
    public void repliSansCoordonnees() {
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(hm(6, 19), null));
        assertEquals(SolarPhase.Phase.DAWN, SolarPhase.at(hm(6, 20), null));
        assertEquals(SolarPhase.Phase.DAY, SolarPhase.at(hm(7, 50), null));
        assertEquals(SolarPhase.Phase.DUSK, SolarPhase.at(hm(18, 10), null));
        assertEquals(SolarPhase.Phase.NIGHT, SolarPhase.at(hm(19, 40), null));
    }

    @Test
    public void coordonneesInvalidesDonnentNull() {
        assertNull(SolarPhase.riseAndSet(DAY, Double.NaN, 6, ZURICH));
        assertNull(SolarPhase.riseAndSet(DAY, 95, 6, ZURICH));
        assertNull(SolarPhase.riseAndSet(DAY, 46, 200, ZURICH));
    }

    @Test
    public void parseNomDePhase() {
        assertEquals(SolarPhase.Phase.DUSK, SolarPhase.parse("dusk"));
        assertNull(SolarPhase.parse("midi"));
        assertNull(SolarPhase.parse(null));
    }

    @Test
    public void positionDuSoleil() {
        // Midi solaire a Lausanne en novembre : plein sud, hauteur 90 - 46,5 - 18,9, soit 24,6 degres.
        int offset = 60;
        double[] noon = SolarPhase.sunPosition(DAY, hm(12, 20), 46.516, 6.63282, offset);
        assertEquals(180, noon[0], 10);
        assertEquals(24.6, noon[1], 2);
        double[] night = SolarPhase.sunPosition(DAY, hm(23, 0), 46.516, 6.63282, offset);
        assertTrue(night[1] < -20);
        double[] morning = SolarPhase.sunPosition(DAY, hm(7, 38), 46.516, 6.63282, offset);
        assertEquals(0, morning[1], 2);
        assertTrue(morning[0] > 100 && morning[0] < 140);
    }

    @Test
    public void phaseLunaireEtPosition() {
        // Pleine lune le 24 novembre 2026 environ : la lune se leve quand le soleil se couche.
        double full = SolarPhase.moonPhase(LocalDate.of(2026, 11, 24), 720, 60);
        assertEquals(0.5, full, 0.06);
        double[] m = SolarPhase.moonPosition(LocalDate.of(2026, 11, 24), 0, 46.516, 6.63282, 60);
        assertTrue("lune pleine haute a minuit : " + m[1], m[1] > 20);
    }
}
