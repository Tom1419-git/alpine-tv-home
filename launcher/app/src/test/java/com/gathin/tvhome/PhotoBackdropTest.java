package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoBackdropTest {
    @Test
    public void jeuDeFichiersSelonLaSurface() {
        assertTrue(PhotoBackdrop.useHd(1920));
        assertTrue(PhotoBackdrop.useHd(1280));
        assertFalse(PhotoBackdrop.useHd(3840));
    }

    @Test
    public void lePlafondDeMemoireBorneLeDecodage() {
        // 1080p : 12,4 Mo, rien a reduire. UHD : 49,8 Mo, sous le plafond de 56 Mo : lu entier sur un ecran 4K.
        assertEquals(1, PhotoBackdrop.budgetSample(1920, 1080, 1));
        assertEquals(1, PhotoBackdrop.budgetSample(3840, 2160, 1));
        int s = PhotoBackdrop.budgetSample(6000, 4000, 1);          // image plus grande que prevu : reduite, jamais entiere
        assertTrue(s >= 2);
        assertTrue((long) (6000 / s) * (4000 / s) * 6 <= PhotoBackdrop.BUDGET_BYTES);
        assertEquals(4, PhotoBackdrop.budgetSample(1920, 1080, 4));  // l'echantillonnage demande n'est jamais diminue
        assertTrue(PhotoBackdrop.budgetSample(100000, 100000, 1) <= 16);
    }

    @Test
    public void tailleDEchantillonnageSuitLaSurface() {
        assertEquals(1, PhotoBackdrop.sampleSize(1920, 1920));
        assertEquals(1, PhotoBackdrop.sampleSize(1920, 3840));
        assertEquals(1, PhotoBackdrop.sampleSize(1920, 1280));
        assertEquals(2, PhotoBackdrop.sampleSize(1920, 960));
        assertEquals(4, PhotoBackdrop.sampleSize(1920, 480));
        assertEquals(1, PhotoBackdrop.sampleSize(1920, 0));
    }
}
