package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Lisibilite sur TOUTES les combinaisons : hauteur du soleil (de la nuit profonde au plein midi, matin et soir) x chaque
 * condition meteo x chaque zone de texte, sur la vraie grille de couleur et la vraie crete de la photo. Un rendu illisible
 * est un echec de test : contraste d'au moins 4,5:1 pour le texte courant, 3:1 pour la grande horloge.
 */
public class SceneZonesTest {
    private static int[] grid;
    private static float[] crest;
    private static final float W = 1920f, H = 1080f;
    private static final float[] ELEVATIONS = {-30f, -20f, -12f, -9f, -6f, -4f, -2f, 0f, 1f, 3f, 6f, 10f, 15f, 25f, 40f, 60f, 70f};

    @BeforeClass
    public static void load() throws Exception {
        try (InputStream in = SceneZonesTest.class.getResourceAsStream("/riffelsee_scene.json");
             Scanner sc = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            JSONObject o = new JSONObject(sc.next());
            JSONArray g = o.getJSONArray("grid"), c = o.getJSONArray("crest");
            grid = new int[g.length()];
            for (int i = 0; i < grid.length; i++) grid[i] = g.getInt(i);
            crest = new float[c.length()];
            for (int i = 0; i < crest.length; i++) crest[i] = (float) c.getDouble(i);
        }
    }

    private static SceneMix mixFor(WeatherCodes.Condition c, float elevation, float evening) {
        SceneMix m = new SceneMix();
        m.targetCondition(c);
        m.targetSky(elevation, evening > 0.5f ? 260f : 100f, -20f, 200f, evening);
        m.snap();
        return m;
    }

    @Test
    public void lesDonneesDeLaPhotoSontCompletes() {
        assertNotNull(grid);
        assertEquals(SceneZones.GW * SceneZones.GH * 3, grid.length);
        assertEquals(241, crest.length);
    }

    @Test
    public void toutesLesCombinaisonsSontLisibles() {
        float[] out = new float[2], tmp = new float[6];
        int combos = 0;
        float worst = 99f;
        String worstName = "";
        for (WeatherCodes.Condition c : WeatherCodes.Condition.values()) {
            for (float evening = 0f; evening <= 1f; evening += 1f) {
                for (float e : ELEVATIONS) {
                    SceneMix m = mixFor(c, e, evening);
                    SceneGrade.Grade g = SceneGrade.compute(m.elevation, m.evening, m);
                    for (SceneZones.Zone z : SceneZones.Zone.values()) {
                        ContrastPlan.Plan p = SceneZones.plan(z, W, H, grid, crest, g, m, out, tmp);
                        String name = c + " e=" + e + " soir=" + evening + " zone=" + z + " ratio=" + p.ratio + " voile=" + p.veilAlpha;
                        assertTrue("illisible : " + name, p.ok);
                        assertTrue("contraste sous le seuil : " + name, p.ratio + 1e-3f >= SceneZones.threshold(z));
                        assertTrue("voile non borne : " + name, p.veilAlpha <= ContrastPlan.MAX_VEIL + 1e-6f);
                        if (p.ratio < worst) {
                            worst = p.ratio;
                            worstName = name;
                        }
                        combos++;
                    }
                }
            }
        }
        assertEquals(WeatherCodes.Condition.values().length * 2 * ELEVATIONS.length * 7, combos);
        assertTrue("pire cas : " + worstName, worst >= 3f);
    }

    @Test
    public void lesGrandsTextesSeuilTroisLesTextesCourantsSeuilQuatreEtDemi() {
        assertEquals(3f, SceneZones.threshold(SceneZones.Zone.CLOCK), 0f);
        for (SceneZones.Zone z : SceneZones.Zone.values()) {
            if (z != SceneZones.Zone.CLOCK) assertEquals(4.5f, SceneZones.threshold(z), 0f);
        }
    }

    @Test
    public void lesVoilesRestentBornesPourLaGrandeHorlogeEnPleinSoleilEtEnOrage() {
        float[] out = new float[2], tmp = new float[6];
        for (WeatherCodes.Condition c : new WeatherCodes.Condition[]{WeatherCodes.Condition.CLEAR, WeatherCodes.Condition.STORM}) {
            SceneMix m = mixFor(c, 4f, 0f);
            SceneGrade.Grade g = SceneGrade.compute(m.elevation, m.evening, m);
            ContrastPlan.Plan p = SceneZones.plan(SceneZones.Zone.CLOCK, W, H, grid, crest, g, m, out, tmp);
            assertTrue(c + " : voile de l'horloge borne (" + p.veilAlpha + ")", p.veilAlpha <= ContrastPlan.MAX_VEIL);
        }
    }

    @Test
    public void laNuitClaireDonneUnTexteBlancSansVoile() {
        float[] out = new float[2], tmp = new float[6];
        SceneMix m = mixFor(WeatherCodes.Condition.CLEAR, -30f, 1f);
        SceneGrade.Grade g = SceneGrade.compute(m.elevation, m.evening, m);
        for (SceneZones.Zone z : new SceneZones.Zone[]{SceneZones.Zone.DATE, SceneZones.Zone.GREETING, SceneZones.Zone.CLOCK}) {
            ContrastPlan.Plan p = SceneZones.plan(z, W, H, grid, crest, g, m, out, tmp);
            assertFalse(z + " : texte clair la nuit", p.darkText);
            assertEquals(0f, p.veilAlpha, 0.02f);
        }
    }

    @Test
    public void lesZonesSontDansLEcranEtLaMargeDeCinqPourCent() {
        for (SceneZones.Zone z : SceneZones.Zone.values()) {
            float[] r = SceneZones.rect(z, W, H);
            assertTrue(z + " dans l'ecran", r[0] >= 0 && r[1] >= 0 && r[2] <= W && r[3] <= H && r[0] < r[2] && r[1] < r[3]);
            if (z != SceneZones.Zone.HERO) assertTrue(z + " dans la marge de 5 %", r[1] >= H * 0.05f - 0.5f && r[0] >= W * 0.05f - 0.5f
                    && r[2] <= W * 0.95f + 0.5f);
        }
    }

    @Test
    public void laCreteSepareLeCielDuTerrain() {
        assertTrue(SceneZones.crestY(crest, 0f, W, H) < H * 0.05f);                          // la roche de gauche monte jusqu'en haut
        assertTrue(SceneZones.crestY(crest, W * 0.95f, W, H) > H * 0.25f);
        assertEquals(H * 0.4f, SceneZones.crestY(null, 100f, W, H), 0.01f);
    }
}
