package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneGradeTest {
    private static SceneMix clear() {
        SceneMix m = new SceneMix();
        m.targetCondition(WeatherCodes.Condition.CLEAR);
        m.snap();
        return m;
    }

    private static SceneMix with(WeatherCodes.Condition c) {
        SceneMix m = new SceneMix();
        m.targetCondition(c);
        m.snap();
        return m;
    }

    @Test
    public void laTableCouvreLaNuitProfondeEtLeSoleilHaut() {
        float[][] k = SceneGrade.KEYS;
        assertTrue(k[0][0] <= -18f);
        assertTrue(k[k.length - 1][0] >= 60f);
        for (int i = 0; i < k.length; i++) {
            assertEquals("onze valeurs par ligne", 11, k[i].length);
            if (i > 0) assertTrue("hauteurs croissantes", k[i][0] > k[i - 1][0]);
        }
    }

    @Test
    public void exactAuxPointsDeLaTable() {
        for (float[] row : SceneGrade.KEYS) {
            float[] b = SceneGrade.base(row[0], 0f);
            for (int c = 0; c < 10; c++) assertEquals("point " + row[0] + " colonne " + c, row[c + 1], b[c], 1e-5f);
        }
    }

    @Test
    public void laLumiereGlisseSansJamaisSauter() {
        // Un pas de 0,05 degre (environ 12 secondes de lever du soleil) ne change aucun reglage de plus de 0,01 :
        // aucune cassure, y compris aux points de la table et pour le soir.
        for (float evening = 0f; evening <= 1f; evening += 1f) {
            float[] prev = SceneGrade.base(-25f, evening);
            for (float e = -24.95f; e <= 75f; e += 0.05f) {
                float[] b = SceneGrade.base(e, evening);
                for (int c = 0; c < 10; c++) {
                    assertEquals("e=" + e + " colonne " + c, prev[c], b[c], 0.01f);
                }
                prev = b;
            }
        }
    }

    @Test
    public void laNuitPuisLAubeSEclaircissentSansDescendre() {
        float prev = 0f;
        for (float e = -20f; e <= 12f; e += 0.25f) {
            float x = SceneGrade.base(e, 0f)[0];
            assertTrue("exposition croissante a " + e, x >= prev - 1e-5f);
            prev = x;
        }
        assertTrue(SceneGrade.base(-20f, 0f)[0] < 0.35f);
        assertTrue(SceneGrade.base(12f, 0f)[0] > 0.95f);
    }

    @Test
    public void leLeverEstPlusChaudQueMidiEtLeCoucherUnPeuPlusQueLeLever() {
        float[] sunrise = SceneGrade.base(0f, 0f), noon = SceneGrade.base(45f, 0f), sunset = SceneGrade.base(0f, 1f);
        assertTrue(sunrise[3] > sunrise[5]);      // plus de rouge que de bleu
        assertTrue(noon[5] > noon[3]);            // midi est plus froid
        assertTrue(sunset[3] > sunrise[3]);
        assertTrue(sunset[5] < sunrise[5]);
    }

    @Test
    public void laMeteoModifieLEtalonnageParIntensitesContinues() {
        SceneMix m = clear();
        SceneGrade.Grade clearGrade = SceneGrade.compute(25f, 0f, m);
        float prevExposure = clearGrade.exposure, prevSat = clearGrade.saturation;
        for (float r = 0f; r <= 0.9f; r += 0.05f) {
            m.rain = r;
            SceneGrade.Grade g = SceneGrade.compute(25f, 0f, m);
            assertTrue(g.exposure <= prevExposure + 1e-5f && g.saturation <= prevSat + 1e-5f);
            assertTrue(prevExposure - g.exposure < 0.03f);
            prevExposure = g.exposure;
            prevSat = g.saturation;
        }
        SceneGrade.Grade rain = SceneGrade.compute(25f, 0f, with(WeatherCodes.Condition.RAIN));
        SceneGrade.Grade storm = SceneGrade.compute(25f, 0f, with(WeatherCodes.Condition.STORM));
        SceneGrade.Grade fog = SceneGrade.compute(25f, 0f, with(WeatherCodes.Condition.FOG));
        SceneGrade.Grade snow = SceneGrade.compute(25f, 0f, with(WeatherCodes.Condition.SNOW));
        assertTrue("la pluie baisse la lumiere", rain.exposure < clearGrade.exposure);
        assertTrue("l'orage assombrit nettement", storm.exposure < 0.6f * clearGrade.exposure);
        assertTrue("la pluie refroidit", rain.gainB / rain.gainR > clearGrade.gainB / clearGrade.gainR);
        assertTrue("le brouillard ecrase le contraste", fog.contrast < clearGrade.contrast - 0.05f);
        assertTrue("le brouillard est un voile clair", fog.veilAlpha > 0.25f && fog.veilR > 0.7f);
        assertTrue("la neige est laiteuse", snow.veilAlpha > 0.2f && snow.saturation < clearGrade.saturation);
        assertTrue("le ciel couvert desature", with(WeatherCodes.Condition.OVERCAST).cloud == 1f);
    }

    @Test
    public void laMatriceDAndroidFaitLeMemeCalculQueLeModele() {
        SceneGrade.Grade g = SceneGrade.compute(8f, 1f, with(WeatherCodes.Condition.RAIN));
        float[] m = new float[20];
        g.toMatrix(m);
        float[][] colours = {{0.5f, 0.5f, 0.5f}, {0.7f, 0.4f, 0.3f}, {0.35f, 0.5f, 0.8f}, {0.9f, 0.85f, 0.6f}};
        for (float[] c : colours) {
            float[] viaMatrix = new float[3];
            for (int i = 0; i < 3; i++) {
                viaMatrix[i] = Math.max(0f, Math.min(1f, (m[i * 5] * c[0] + m[i * 5 + 1] * c[1] + m[i * 5 + 2] * c[2]) + m[i * 5 + 4] / 255f));
            }
            float[] viaModel = c.clone();
            g.apply(viaModel);
            for (int i = 0; i < 3; i++) assertEquals(viaModel[i], viaMatrix[i], 0.012f);
        }
        assertEquals(1f, m[18], 0f);
    }

    @Test
    public void lePhaseWeightsSommeUnEtGlisse() {
        float[] prev = SceneGrade.phaseWeights(-25f, 0f);
        for (float e = -24.9f; e <= 60f; e += 0.1f) {
            float[] w = SceneGrade.phaseWeights(e, e % 2 > 1 ? 1f : 0f);
            float sum = 0;
            for (float v : w) {
                assertTrue(v >= -1e-6f);
                sum += v;
            }
            assertEquals(1f, sum, 1e-4f);
        }
        float[] day = SceneGrade.phaseWeights(30f, 0f);
        assertEquals(1f, day[1], 1e-4f);
        assertEquals(1f, SceneGrade.phaseWeights(-20f, 0f)[3], 1e-4f);
        assertTrue(SceneGrade.phaseWeights(-1f, 0f)[0] > 0.3f);
        assertTrue(SceneGrade.phaseWeights(-1f, 1f)[2] > 0.3f);
        assertEquals(prev.length, 4);
    }

    @Test
    public void soleilEtoilesEtCouvert() {
        SceneMix c = clear();
        assertEquals(0f, SceneGrade.sunStrength(-5f, c), 1e-4f);
        assertEquals(1f, SceneGrade.sunStrength(20f, c), 1e-4f);
        assertEquals(0f, SceneGrade.starAlpha(20f, c), 1e-4f);
        assertEquals(1f, SceneGrade.starAlpha(-20f, c), 1e-4f);
        assertTrue(SceneGrade.sunStrength(20f, with(WeatherCodes.Condition.OVERCAST)) < 0.1f);
        assertEquals(0f, SceneGrade.sunStrength(20f, with(WeatherCodes.Condition.STORM)), 1e-4f);
        assertEquals(0f, SceneGrade.starAlpha(-20f, with(WeatherCodes.Condition.OVERCAST)), 1e-4f);
        assertTrue("le soleil est un disque pale dans le brouillard", SceneGrade.sunDiscPale(20f, with(WeatherCodes.Condition.FOG)) > 0.5f);
        assertEquals(0f, SceneGrade.sunDiscPale(20f, c), 1e-4f);
        assertEquals(0f, SceneGrade.skyCover(c), 1e-4f);
        assertEquals(1f, SceneGrade.skyCover(with(WeatherCodes.Condition.OVERCAST)), 1e-4f);
        assertTrue(SceneGrade.skyCover(with(WeatherCodes.Condition.PARTLY)) > 0.2f);
    }
}
