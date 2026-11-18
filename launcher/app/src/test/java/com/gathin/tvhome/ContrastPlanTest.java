package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContrastPlanTest {
    @Test
    public void luminanceEtRapportWcag() {
        assertEquals(0f, ContrastPlan.luminance(0f, 0f, 0f), 1e-6f);
        assertEquals(1f, ContrastPlan.luminance(1f, 1f, 1f), 1e-5f);
        assertEquals(0.2140f, ContrastPlan.luminance(0.5f, 0.5f, 0.5f), 0.002f);
        assertEquals(21f, ContrastPlan.ratio(1f, 0f), 1e-3f);
        assertEquals(1f, ContrastPlan.ratio(0.3f, 0.3f), 1e-6f);
        assertEquals(ContrastPlan.ratio(1f, 0.1f), ContrastPlan.ratio(0.1f, 1f), 1e-6f);
        assertEquals(ContrastPlan.DARK_L, ContrastPlan.luminance(11 / 255f, 22 / 255f, 32 / 255f), 0.002f);
    }

    @Test
    public void fondSombreTexteBlancSansVoile() {
        ContrastPlan.Plan p = ContrastPlan.decide(0.0f, 0.10f, ContrastPlan.TEXT_RATIO);
        assertFalse(p.darkText);
        assertEquals(0f, p.veilAlpha, 0f);
        assertTrue(p.ok && p.ratio >= 4.5f);
    }

    @Test
    public void fondClairTexteSombreSansVoile() {
        ContrastPlan.Plan p = ContrastPlan.decide(0.55f, 0.95f, ContrastPlan.TEXT_RATIO);
        assertTrue(p.darkText);
        assertEquals(0f, p.veilAlpha, 0f);
        assertTrue(p.ok && p.ratio >= 4.5f);
    }

    @Test
    public void zoneMixteTexteSombreSurVoileBlancLegerEtBorne() {
        // Le texte blanc est la regle : tant que le voile sombre necessaire reste sous PREFER_WHITE, on le garde.
        ContrastPlan.Plan p = ContrastPlan.decide(0.02f, 0.85f, ContrastPlan.CLOCK_RATIO);
        assertFalse(p.darkText);
        assertTrue("voile borne : " + p.veilAlpha, p.veilAlpha > 0f && p.veilAlpha <= ContrastPlan.PREFER_WHITE);
        assertTrue(p.ok && p.ratio >= 3f);
        // Un fond tres clair partout sauf un coin sombre : le texte sombre sur voile clair prend le relais.
        ContrastPlan.Plan t = ContrastPlan.decide(0.0f, 1.0f, ContrastPlan.TEXT_RATIO);
        assertTrue(t.darkText && t.veilAlpha <= ContrastPlan.MAX_VEIL && t.ok);
    }

    @Test
    public void leSeuilEstAtteintDansLePireCas() {
        for (float lo = 0f; lo <= 0.9f; lo += 0.05f) {
            for (float hi = lo; hi <= 1f; hi += 0.05f) {
                for (float thr : new float[]{3f, 4.5f}) {
                    ContrastPlan.Plan p = ContrastPlan.decide(lo, hi, thr);
                    if (p.ok) assertTrue("lo=" + lo + " hi=" + hi + " thr=" + thr + " ratio=" + p.ratio, p.ratio + 1e-3f >= thr);
                    assertTrue(p.veilAlpha >= 0f && p.veilAlpha <= 1f);
                }
            }
        }
    }

    @Test
    public void textePermanentBlancSeuleLeVoileNoirEstLibre() {
        ContrastPlan.Plan clairObscur = ContrastPlan.decideWhite(0.0f, 0.15f, 4.5f);
        assertFalse(clairObscur.darkText);
        assertEquals(0f, clairObscur.veilAlpha, 0f);
        ContrastPlan.Plan clair = ContrastPlan.decideWhite(0.3f, 0.5f, 4.5f);
        assertTrue(clair.veilAlpha > 0f);
        assertTrue(clair.ratio >= 4.5f || !clair.ok);
        ContrastPlan.Plan impossible = ContrastPlan.decideWhite(0.9f, 1.0f, 4.5f);
        assertTrue("un fond blanc demande un voile fort, mais borne : " + impossible.veilAlpha,
                impossible.veilAlpha > 0.5f && impossible.veilAlpha <= ContrastPlan.MAX_VEIL);
    }
}
