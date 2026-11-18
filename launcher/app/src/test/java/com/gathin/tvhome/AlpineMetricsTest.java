package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlpineMetricsTest {
    @Test
    public void positionExacteDeLHorlogeEtDesPilules() {
        float[] c = AlpineMetrics.clockSizes(1920f, 1080f);
        assertEquals(383.7f, c[0], 0.5f);          // taille du texte
        assertEquals(268.6f, c[1], 0.5f);          // hauteur des chiffres
        assertEquals(565.9f, c[2], 0.5f);          // ligne de base : 566 px, remontee de 52 px (avant : 618 px)
        float[] lines = AlpineMetrics.dateLines(1920f, 1080f);
        assertEquals(232.5f, lines[0], 0.6f);      // date
        assertEquals(183.3f, lines[1], 0.6f);      // salutation
        float[] p = AlpineMetrics.pill(0, 1920f, 1080f);
        assertEquals(64f, p[1], 0.01f);            // pilules : haut a 64 px (avant : 118 px)
        assertEquals(82f, p[3], 0.01f);
        assertEquals(618f - 566f, 52f, 1f);
        assertEquals(118f - 64f, 54f, 0.01f);
    }

    @Test
    public void rienNeToucheLesBordsLHorlogeNeToucheJamaisLesPilules() {
        float w = 1920f, h = 1080f, safeX = w * AlpineMetrics.SAFE, safeY = h * AlpineMetrics.SAFE;
        float[] clock = AlpineMetrics.clockVisible(w, h);
        assertTrue(clock[0] >= safeX && clock[2] <= w - safeX);
        float[] lines = AlpineMetrics.dateLines(w, h);
        float greetTop = lines[1] - lines[3] * 1.1f;
        float pillsBottom = 0;
        for (int i = 0; i < 3; i++) {
            float[] p = AlpineMetrics.pill(i, w, h);
            assertTrue("pilule " + i + " dans la marge", p[1] >= safeY && p[0] + p[2] <= w - safeX);
            pillsBottom = Math.max(pillsBottom, p[1] + p[3]);
        }
        assertTrue("la salutation reste sous les pilules : " + greetTop + " > " + pillsBottom, greetTop > pillsBottom + 8f);
        float dateTop = lines[0] - lines[2] * 1.1f, greetBottom = lines[1] + lines[3] * 0.3f;
        assertTrue("date sous la salutation, sans chevauchement", dateTop > greetBottom);
        float clockTop = clock[1];
        assertTrue("date au-dessus des chiffres", lines[0] + lines[2] * 0.3f < clockTop);
    }

    @Test
    public void laRiveCoupeLeBasDesChiffresSansLesRendreIllisibles() {
        float[] clock = AlpineMetrics.clockVisible(1920f, 1080f);
        float[] sizes = AlpineMetrics.clockSizes(1920f, 1080f);
        float hidden = (sizes[2] - clock[3]) / sizes[1];
        assertEquals(AlpineMetrics.OCCLUSION, hidden, 0.001f);
        assertTrue("au plus un quart de la hauteur des chiffres est cache", hidden <= 0.25f);
    }
}
