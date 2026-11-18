package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneMixTest {
    private static SceneMix start(float elevation, WeatherCodes.Condition c) {
        SceneMix m = new SceneMix();
        m.targetSky(elevation, 180f, -20f, 200f, 0f);
        m.targetCondition(c);
        m.snap();
        return m;
    }

    private static void run(SceneMix m, float seconds) {
        for (float t = 0; t < seconds; t += 1f / 30f) m.step(1f / 30f);
    }

    @Test
    public void unChangementDePhaseGlisseEnDeuxSecondesSansPasserParLeNoir() {
        // Plein jour vers nuit profonde : la hauteur du soleil parcourt toutes les valeurs intermediaires (le soleil
        // se couche en accelere), l'exposition baisse sans jamais passer sous celle de la cible, et ne saute jamais.
        SceneMix m = start(45f, WeatherCodes.Condition.CLEAR);
        float nightExposure = SceneGrade.compute(-20f, 1f, m).exposure;
        m.targetSky(-20f, 180f, 35f, 200f, 1f);
        float prev = SceneGrade.compute(m.elevation, m.evening, m).exposure, maxStep = 0f, min = prev;
        for (int i = 0; i < 90; i++) {
            m.step(1f / 30f);
            float e = SceneGrade.compute(m.elevation, m.evening, m).exposure;
            maxStep = Math.max(maxStep, Math.abs(e - prev));
            min = Math.min(min, e);
            prev = e;
        }
        // La zone la plus raide (le lever : l'exposition passe de 0,60 a 0,84 en quatre degres) se traverse en une dizaine
        // d'images : au plus 9 % de l'exposition par image de 33 ms, toujours monotone, jamais un saut ni un noir.
        assertTrue("pas de saut : " + maxStep, maxStep < 0.09f);
        assertTrue("jamais plus sombre que la cible : " + min, min >= SceneGrade.compute(-20f, 1f, m).exposure - 0.01f);
        assertTrue(Math.abs(m.elevation - (-20f)) < 6f);                       // quasi arrive en 3 s
        run(m, 3f);
        assertEquals(-20f, m.elevation, 0.2f);
        assertEquals(nightExposure, SceneGrade.compute(m.elevation, m.evening, m).exposure, 0.02f);
    }

    @Test
    public void laPluieSeChangeEnNeigeParMonteeEtDescente() {
        SceneMix m = start(25f, WeatherCodes.Condition.RAIN);
        m.targetCondition(WeatherCodes.Condition.SNOW);
        float prevRain = m.rain, prevSnow = m.snow;
        boolean crossed = false;
        for (int i = 0; i < 15 * 30; i++) {   // la meteo glisse en une dizaine de secondes (TAU_WEATHER)
            m.step(1f / 30f);
            assertTrue("la pluie ne fait que descendre", m.rain <= prevRain + 1e-6f);
            assertTrue("la neige ne fait que monter", m.snow >= prevSnow - 1e-6f);
            if (m.rain > 0.1f && m.snow > 0.1f) crossed = true;   // un instant ou les deux sont presentes : fondu, pas remplacement
            prevRain = m.rain;
            prevSnow = m.snow;
        }
        assertTrue(crossed);
        assertTrue(m.rain < 0.02f && m.snow > 0.8f);
    }

    @Test
    public void uneTransitionInterrompueConvergeVersLaNouvelleCible() {
        SceneMix m = start(45f, WeatherCodes.Condition.CLEAR);
        m.targetSky(-20f, 180f, 35f, 200f, 1f);
        m.targetCondition(WeatherCodes.Condition.STORM);
        run(m, 0.6f);                                       // a mi-chemin...
        float mid = m.elevation, storm = m.storm;
        assertTrue(mid < 45f && mid > -20f && storm > 0f && storm < 1f);
        m.targetSky(45f, 180f, -20f, 200f, 0f);             // ...le soleil revient et la meteo s'eclaircit
        m.targetCondition(WeatherCodes.Condition.CLEAR);
        float prevE = m.elevation, prevS = m.storm;
        for (int i = 0; i < 20 * 30; i++) {   // 20 s : la meteo est plus lente que le ciel
            m.step(1f / 30f);
            assertTrue("pas de saut de hauteur", Math.abs(m.elevation - prevE) < 8f);
            assertTrue("pas de saut d'orage", Math.abs(m.storm - prevS) < 0.12f);
            prevE = m.elevation;
            prevS = m.storm;
        }
        assertEquals(45f, m.elevation, 0.5f);
        assertTrue(m.storm < 0.02f);
        assertFalse(m.settling() && m.cloud > 0.01f);
    }

    @Test
    public void aucuneValeurNeDepasseSaCible() {
        SceneMix m = start(0f, WeatherCodes.Condition.CLEAR);
        m.targetSky(30f, 180f, -20f, 200f, 0f);
        m.targetCondition(WeatherCodes.Condition.RAIN);
        for (int i = 0; i < 300; i++) {
            m.step(1f / 30f);
            assertTrue(m.elevation <= 30.0001f && m.rain <= m.tRain + 1e-6f && m.cloud <= m.tCloud + 1e-6f);
        }
    }

    @Test
    public void unRetourAuPremierPlanPoseLaSceneSansTransition() {
        SceneMix m = start(45f, WeatherCodes.Condition.CLEAR);
        m.targetSky(-20f, 180f, 35f, 200f, 1f);
        m.targetCondition(WeatherCodes.Condition.SNOW);
        assertTrue(m.settling());
        m.snap();
        assertEquals(-20f, m.elevation, 0f);
        assertEquals(m.tSnow, m.snow, 0f);
        assertFalse(m.settling());
        m.step(1f / 30f);
        assertEquals(-20f, m.elevation, 0f);
    }

    @Test
    public void lAzimutPrendLePlusCourtChemin() {
        SceneMix m = start(10f, WeatherCodes.Condition.CLEAR);
        m.targetSky(10f, 350f, -20f, 200f, 0f);
        m.azimuth = 10f;
        float prev = 10f;
        for (int i = 0; i < 20; i++) {
            m.step(1f / 30f);
            assertTrue("par le nord, jamais par le sud : " + m.azimuth, m.azimuth <= prev + 1e-3f || m.azimuth > 300f);
            prev = m.azimuth;
        }
        assertEquals(20f, SceneMix.angle(350f - 330f), 1e-4f);
        assertEquals(-20f, SceneMix.angle(10f - 30f), 1e-4f);
        assertEquals(-10f, SceneMix.angle(350f - 0f), 1e-4f);
    }

    @Test
    public void uneSceneAuRepoSeStabiliseEtNeDemandePlusDImages() {
        SceneMix m = start(30f, WeatherCodes.Condition.CLEAR);
        assertFalse(m.settling());
        m.targetCondition(WeatherCodes.Condition.OVERCAST);
        assertTrue(m.settling());
        run(m, 30f);   // la meteo met une vingtaine de secondes a se poser a 0,2 %
        assertFalse(m.settling());
        SceneMix fresh = new SceneMix();
        assertTrue("jamais initialise : doit s'initialiser", fresh.settling());
    }

    @Test
    public void lePasDeTempsAbsurdeNeFaitPasSauter() {
        SceneMix m = start(0f, WeatherCodes.Condition.CLEAR);
        m.targetSky(40f, 180f, -20f, 200f, 0f);
        m.step(60f);      // l'application a ete suspendue une minute : le pas est borne
        assertTrue(m.elevation < 40f);
    }

    @Test
    public void lesAversesMontentEtDescendentParVagues() {
        SceneMix m = start(20f, WeatherCodes.Condition.SHOWERS);
        float lo = 9f, hi = -1f;
        for (float t = 0; t < 30f; t += 0.25f) {
            float r = WeatherLayer.rainNow(m, t);
            lo = Math.min(lo, r);
            hi = Math.max(hi, r);
        }
        assertTrue("intensite qui varie : " + lo + " a " + hi, hi - lo > 0.2f);
        assertTrue(hi <= m.rain + 1e-5f);
        SceneMix steady = start(20f, WeatherCodes.Condition.RAIN);
        assertEquals(steady.rain, WeatherLayer.rainNow(steady, 3f), 1e-5f);
        for (float t = 0; t < 60f; t += 0.5f) assertTrue(Math.abs(WeatherLayer.gust(t)) <= 1.0001f);
    }
}
