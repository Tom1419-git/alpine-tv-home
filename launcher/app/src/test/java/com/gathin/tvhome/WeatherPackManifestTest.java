package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherPackManifestTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static String array(int n, String value) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < n; i++) b.append(i == 0 ? "" : ",").append(value);
        return b.append("]").toString();
    }

    private static String manifest(int version, double waterline, int crestN, int gridN, String... skip) {
        StringBuilder files = new StringBuilder();
        for (String name : WeatherPackManifest.FILES) {
            boolean skipped = false;
            for (String s : skip) skipped |= s.equals(name);
            if (skipped) continue;
            if (files.length() > 0) files.append(',');
            files.append("\"").append(name).append("\":{\"sha256\":\"").append(SHA).append("\",\"size\":1234}");
        }
        return "{\"version\":" + version + ",\"waterline\":" + waterline + ",\"crest\":" + array(crestN, "0.3")
                + ",\"grid\":" + array(gridN, "120") + ",\"files\":{" + files + "}}";
    }

    private static String ok() {
        return manifest(3, 0.56, 241, WeatherPackManifest.GRID_CELLS);
    }

    @Test
    public void manifesteValide() {
        WeatherPackManifest m = WeatherPackManifest.parse(ok());
        assertNotNull(m);
        assertEquals(0.56f, m.waterline, 0.001f);
        assertEquals(241, m.crest.length);
        assertEquals(WeatherPackManifest.GRID_CELLS, m.grid.length);
        assertEquals(120, m.grid[0]);
        assertTrue(m.has("foreground_1080.png"));
        assertEquals(1234, m.size("photo.jpg"));
        assertEquals(SHA, m.sha("photo_1080.jpg"));
        assertEquals(-1, m.size("autre/chose.jpg"));
    }

    @Test
    public void manifesteRefuse() {
        assertNull(WeatherPackManifest.parse(null));
        assertNull(WeatherPackManifest.parse(""));
        assertNull(WeatherPackManifest.parse("pas du json"));
        assertNull(WeatherPackManifest.parse(manifest(4, 0.56, 241, WeatherPackManifest.GRID_CELLS)));
        assertNull(WeatherPackManifest.parse(manifest(2, 0.56, 241, WeatherPackManifest.GRID_CELLS)));   // ancien format : refuse
        assertNull(WeatherPackManifest.parse(manifest(1, 0.56, 241, WeatherPackManifest.GRID_CELLS)));
        assertNull(WeatherPackManifest.parse(manifest(3, 0.05, 241, WeatherPackManifest.GRID_CELLS)));
        assertNull(WeatherPackManifest.parse(manifest(3, 0.95, 241, WeatherPackManifest.GRID_CELLS)));
        assertNull(WeatherPackManifest.parse(manifest(3, 0.56, 3, WeatherPackManifest.GRID_CELLS)));          // crete trop courte
        assertNull(WeatherPackManifest.parse(manifest(3, 0.56, 241, 100)));                                    // grille tronquee
        assertNull(WeatherPackManifest.parse(manifest(3, 0.56, 241, WeatherPackManifest.GRID_CELLS).replace("0.3", "7")));
        assertNull(WeatherPackManifest.parse(manifest(3, 0.56, 241, WeatherPackManifest.GRID_CELLS).replace("120", "300")));
        for (String name : WeatherPackManifest.FILES) {
            assertNull(name + " manquant", WeatherPackManifest.parse(manifest(3, 0.56, 241, WeatherPackManifest.GRID_CELLS, name)));
        }
        assertNull(WeatherPackManifest.parse(ok().replace(SHA, "zz")));
        assertNull(WeatherPackManifest.parse(ok().replace("\"size\":1234", "\"size\":0")));
    }

    @Test
    public void cheminHorsListeRefuse() {
        assertNull(WeatherPackManifest.parse(ok().replace("photo.jpg", "../evil/photo.jpg")));
        assertNull(WeatherPackManifest.parse(ok().replace("\"foreground.png\"", "\"dawn/foreground.png\"")));
    }

    @Test
    public void manifesteTronque() {
        String json = ok();
        assertNull(WeatherPackManifest.parse(json.substring(0, json.length() / 2)));
        assertNull(WeatherPackManifest.parse(json.substring(0, json.length() - 1)));
    }

    @Test
    public void jeuUhdOuJeuDeSecours() {
        assertEquals("photo.jpg", WeatherPackManifest.file(true, false));
        assertEquals("photo_1080.jpg", WeatherPackManifest.file(true, true));
        assertEquals("foreground.png", WeatherPackManifest.file(false, false));
        assertEquals("foreground_1080.png", WeatherPackManifest.file(false, true));
        assertFalse(WeatherPackManifest.parse(ok()).has("dusk/photo.jpg"));
    }

    @Test
    public void laPhotoUniqueNeGardeAucunDossierDePhase() {
        for (String name : WeatherPackManifest.FILES) assertFalse(name.contains("/"));
        assertEquals(4, WeatherPackManifest.FILES.length);
    }
}
