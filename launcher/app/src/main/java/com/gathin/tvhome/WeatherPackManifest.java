package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Le manifeste du pack photo (version 3, {@code manifest.json} de weather_pack.zip) : la photo unique du lever du soleil en
 * UHD et en 1080p, avec la taille et l'empreinte SHA-256 de chaque fichier, la ligne d'eau, la courbe de crete et la
 * grille de couleur de la photo. Logique pure : un manifeste invalide donne {@code null}, jamais d'exception.
 */
final class WeatherPackManifest {
    static final int VERSION = 3;
    static final String[] FILES = {"photo.jpg", "photo_1080.jpg", "foreground.png", "foreground_1080.png"};
    static final int GRID_CELLS = SceneZones.GW * SceneZones.GH * 3;
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

    final float waterline;
    /** Hauteur de la premiere ligne de terrain, en fraction de la hauteur, pour des colonnes regulieres. */
    final float[] crest;
    /** Couleur moyenne de chaque cellule de la photo (32 x 18 x RVB, 0 a 255). */
    final int[] grid;
    private final Map<String, Long> sizes = new HashMap<>();
    private final Map<String, String> hashes = new HashMap<>();

    private WeatherPackManifest(float waterline, float[] crest, int[] grid) {
        this.waterline = waterline;
        this.crest = crest;
        this.grid = grid;
    }

    /** Nom du fichier : la photo ou le premier plan, en 1080p de secours ou en UHD. */
    static String file(boolean photo, boolean hd) {
        return (photo ? "photo" : "foreground") + (hd ? "_1080" : "") + (photo ? ".jpg" : ".png");
    }

    long size(String path) {
        Long v = sizes.get(path);
        return v == null ? -1 : v;
    }

    String sha(String path) {
        return hashes.get(path);
    }

    boolean has(String path) {
        return hashes.containsKey(path);
    }

    static WeatherPackManifest parse(String json) {
        if (json == null || json.length() > 128 * 1024) return null;
        try {
            JSONObject o = new JSONObject(json);
            if (o.optInt("version", -1) != VERSION) return null;
            double water = o.optDouble("waterline", Double.NaN);
            if (Double.isNaN(water) || water < 0.3 || water > 0.8) return null;
            JSONArray c = o.optJSONArray("crest");
            if (c == null || c.length() < 8 || c.length() > 2000) return null;
            float[] crest = new float[c.length()];
            for (int i = 0; i < crest.length; i++) {
                double v = c.optDouble(i, Double.NaN);
                if (Double.isNaN(v) || v < 0 || v > 1.0) return null;
                crest[i] = (float) v;
            }
            JSONArray g = o.optJSONArray("grid");
            if (g == null || g.length() != GRID_CELLS) return null;
            int[] grid = new int[GRID_CELLS];
            for (int i = 0; i < grid.length; i++) {
                int v = g.optInt(i, -1);
                if (v < 0 || v > 255) return null;
                grid[i] = v;
            }
            JSONObject files = o.optJSONObject("files");
            if (files == null) return null;
            WeatherPackManifest m = new WeatherPackManifest((float) water, crest, grid);
            java.util.Iterator<String> keys = files.keys();
            while (keys.hasNext()) {
                String path = keys.next();
                if (!path.matches("photo(_1080)?\\.jpg|foreground(_1080)?\\.png")) return null;
                JSONObject f = files.optJSONObject(path);
                if (f == null) return null;
                String sha = f.optString("sha256", "");
                long size = f.optLong("size", -1);
                if (!SHA.matcher(sha).matches() || size <= 0 || size > 48L * 1024 * 1024) return null;
                m.hashes.put(path, sha);
                m.sizes.put(path, size);
            }
            for (String name : FILES) {
                if (!m.has(name)) return null;
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
