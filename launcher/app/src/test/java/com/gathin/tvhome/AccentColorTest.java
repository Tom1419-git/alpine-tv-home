package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccentColorTest {
    private static int[] fill(int argb, int n) {
        int[] px = new int[n];
        java.util.Arrays.fill(px, argb);
        return px;
    }

    @Test
    public void pochetteLumineuseDonneUnAccentAssombri() {
        int accent = AccentColor.derive(fill(0xFFFFE066, 256), 256);
        assertTrue(TextContrast.luma(accent) <= AccentColor.MAX_LUMA + 0.01f);
        // La teinte reste jaune : rouge et vert dominent le bleu.
        assertTrue(((accent >> 16) & 0xFF) > (accent & 0xFF));
        assertEquals(0xFF, accent >>> 24);
    }

    @Test
    public void lesCouleursSatureesPesentPlusQueLesGris() {
        int[] px = new int[256];
        for (int i = 0; i < 256; i++) px[i] = i < 200 ? 0xFF808080 : 0xFFCC2222;
        int accent = AccentColor.derive(px, 256);
        assertTrue("rouge dominant : " + Integer.toHexString(accent), ((accent >> 16) & 0xFF) > ((accent >> 8) & 0xFF) + 20);
    }

    @Test
    public void pochetteNoireDonneUnAccentDiscretLisible() {
        int accent = AccentColor.derive(fill(0xFF000000, 256), 256);
        assertTrue(TextContrast.luma(accent) >= 0.05f);
    }

    @Test
    public void sansPixelsOnPrendLaCouleurDeRepli() {
        assertEquals(AccentColor.FALLBACK, AccentColor.derive(null, 0));
        assertEquals(AccentColor.FALLBACK, AccentColor.derive(new int[0], 0));
        assertEquals(AccentColor.FALLBACK, AccentColor.derive(fill(0x00FFFFFF, 16), 16));   // tout transparent
    }

    @Test
    public void accentEclairciPourLaBarre() {
        int lifted = AccentColor.lift(0xFF203040);
        assertTrue(TextContrast.luma(lifted) > TextContrast.luma(0xFF203040));
        assertEquals(0xFF, lifted >>> 24);
    }
}
