package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextContrastTest {
    @Test
    public void cielClairChiffresSombres() {
        assertTrue(TextContrast.darkText(TextContrast.luma(0xFFBFE2F5)));
        assertEquals(0xFF0B1620, TextContrast.textColor(0.8f));
    }

    @Test
    public void nuitChiffresClairsAvecHalo() {
        assertFalse(TextContrast.darkText(TextContrast.luma(0xFF0B1530)));
        assertEquals(0xFFFFFFFF, TextContrast.textColor(0.05f));
        assertEquals(0x99000000, TextContrast.haloColor(0.05f));
        assertEquals(0x66FFFFFF, TextContrast.haloColor(0.9f));
    }

    @Test
    public void lumaDesExtremes() {
        assertEquals(0f, TextContrast.luma(0xFF000000), 0.001f);
        assertEquals(1f, TextContrast.luma(0xFFFFFFFF), 0.001f);
        assertTrue(TextContrast.luma(0xFF00FF00) > TextContrast.luma(0xFFFF0000));
    }

    @Test
    public void zoneMixteTraiteeCommeSombre() {
        // Ciel tres clair (0,8) sur lequel un tiers de roche sombre : le texte doit rester clair.
        float mean = 0.8f * 0.65f + 0.1f * 0.35f;
        assertTrue(TextContrast.darkText(mean));
        assertFalse(TextContrast.darkText(TextContrast.conservative(mean, 0.35f)));
        // Un peu de roche (10 %) ne change rien.
        assertTrue(TextContrast.darkText(TextContrast.conservative(0.75f, 0.10f)));
    }

    @Test
    public void moyenneDeCouleurs() {
        assertEquals(0.5f, TextContrast.meanLuma(new int[]{0xFF000000, 0xFFFFFFFF}, 2), 0.001f);
        assertEquals(0f, TextContrast.meanLuma(new int[]{}, 0), 0f);
    }
}
