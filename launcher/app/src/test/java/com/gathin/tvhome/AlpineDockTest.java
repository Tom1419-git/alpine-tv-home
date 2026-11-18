package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlpineDockTest {
    @Test
    public void lePremierRangDeTuilesSePoseSousLHeureRemontee() {
        assertEquals(612, AlpineDock.cardTop(1080));
        float baseline = AlpineMetrics.BASELINE * 1080;
        assertTrue("l'heure finit au-dessus du dock : " + baseline, baseline < AlpineDock.cardTop(1080) - 30);
        assertEquals(306, AlpineDock.cardTop(540));
    }

    @Test
    public void toutesLesRangeesTiennentAvantLaMargeBasse() {
        for (int rows = 0; rows <= 3; rows++) {
            for (int h : new int[]{720, 1080, 2160}) {
                float density = h / 540f;
                int tile = AlpineDock.tileHeight(h, rows, density);
                assertTrue(tile > 0);
                assertTrue("rangees " + rows + " h=" + h + " bas=" + AlpineDock.lastBottom(h, rows, tile, density),
                        AlpineDock.lastBottom(h, rows, tile, density) <= Math.round(AlpineDock.BOTTOM_FRAC * h) + 1
                                || tile <= Math.round(40 * density));
            }
        }
    }

    @Test
    public void quatreApplicationsPuisLesAutresEnDessous() {
        int h = 1080, rows = 1;
        float density = 2f;
        int tileH = AlpineDock.tileHeight(h, rows, density);
        assertEquals(170, tileH);
        int fit = Math.round(AppAdapter.DESIGN_W_DP * density);
        int tileW = AlpineDock.tileWidth(h, rows, fit, density);
        assertTrue("les tuiles gardent un format 16:9 et restent grandes : " + tileW, tileW >= 290 && tileW <= fit);
        assertEquals(tileH * 180f / 101f, tileW, 1.5f);
        int second = AlpineDock.cardTop(h) + tileH + Math.round(AlpineDock.ROW_GAP_DP * density);
        assertTrue("deuxieme rangee apres la premiere", second > AlpineDock.cardTop(h) + tileH);
        assertTrue("la deuxieme rangee finit dans la marge de 5 %", second + tileH <= h * 0.95f + 1);
    }

    @Test
    public void laLargeurNeDepasseJamaisLaPlaceNiLaTailleDeConception() {
        assertEquals(200, AlpineDock.tileWidth(1080, 0, 200, 2f));
        assertTrue(AlpineDock.tileWidth(1080, 3, 5000, 2f) < 5000);
        assertTrue(AlpineDock.tileWidth(1080, 2, 360, 2f) >= 160);
    }

    @Test
    public void lesRangeesDuDessousSontDiscretesEtLaBandeTranslucide() {
        assertTrue(AlpineDock.BELOW_ALPHA > 0.5f && AlpineDock.BELOW_ALPHA < 0.9f);
        assertTrue(AlpineDock.BAND_OPACITY >= 30 && AlpineDock.BAND_OPACITY <= 50);
    }
}
