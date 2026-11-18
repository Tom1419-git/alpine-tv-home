package com.gathin.tvhome;

import org.json.JSONObject;

/**
 * Une meteo lue chez Open-Meteo : immuable, avec le parsing et le format du cache disque.
 * Toute entree invalide donne {@code null}, jamais d'exception.
 */
final class WeatherData {
    final double tempC;
    final int code;
    final WeatherCodes.Condition condition;
    final boolean isDay;
    /** Lever et coucher en minutes locales, ou -1 si la reponse ne les donnait pas. */
    final int sunriseMin;
    final int sunsetMin;
    /** Heure (ms) de la recuperation, 0 si inconnue. */
    final long fetchedAtMs;
    /** Observations facultatives (-1 si la reponse ne les donnait pas) : nuages en %, precipitations en mm/h, vent en km/h, visibilite en m. */
    final float cloudPct, precipMm, windKmh, visibilityM;

    WeatherData(double tempC, int code, boolean isDay, int sunriseMin, int sunsetMin, long fetchedAtMs) {
        this(tempC, code, isDay, sunriseMin, sunsetMin, fetchedAtMs, -1f, -1f, -1f, -1f);
    }

    WeatherData(double tempC, int code, boolean isDay, int sunriseMin, int sunsetMin, long fetchedAtMs,
                float cloudPct, float precipMm, float windKmh, float visibilityM) {
        this.tempC = tempC;
        this.code = code;
        this.condition = WeatherCodes.map(code);
        this.isDay = isDay;
        this.sunriseMin = sunriseMin;
        this.sunsetMin = sunsetMin;
        this.fetchedAtMs = fetchedAtMs;
        this.cloudPct = cloudPct;
        this.precipMm = precipMm;
        this.windKmh = windKmh;
        this.visibilityM = visibilityM;
    }

    /** Reponse d'Open-Meteo, ou null si elle est vide, invalide ou sans meteo courante. */
    static WeatherData parse(String body, long fetchedAtMs) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JSONObject current = new JSONObject(body).optJSONObject("current");
            if (current == null || !current.has("temperature_2m") || !current.has("weather_code")) return null;
            double temp = current.getDouble("temperature_2m");
            int code = current.getInt("weather_code");
            if (Double.isNaN(temp) || Double.isInfinite(temp)) return null;
            boolean day = current.optInt("is_day", 1) != 0;
            JSONObject daily = new JSONObject(body).optJSONObject("daily");
            int rise = -1;
            int set = -1;
            if (daily != null) {
                rise = firstMinute(daily.optJSONArray("sunrise"));
                set = firstMinute(daily.optJSONArray("sunset"));
            }
            return new WeatherData(temp, code, day, rise, set, fetchedAtMs, optional(current, "cloud_cover"),
                    optional(current, "precipitation"), optional(current, "wind_speed_10m"), optional(current, "visibility"));
        } catch (Exception e) {   // JSONException ou valeur de mauvais type
            return null;
        }
    }

    /** Une observation facultative : sa valeur, ou -1 si elle manque, n'est pas un nombre ou est negative. */
    private static float optional(JSONObject o, String key) {
        double v = o.optDouble(key, -1);
        return Double.isNaN(v) || Double.isInfinite(v) || v < 0 ? -1f : (float) v;
    }

    /** "2026-11-17T07:38" en 458, ou -1. */
    private static int firstMinute(org.json.JSONArray array) {
        if (array == null || array.length() == 0) return -1;
        String s = array.optString(0, "");
        int t = s.indexOf('T');
        if (t < 0 || s.length() < t + 6) return -1;
        try {
            int h = Integer.parseInt(s.substring(t + 1, t + 3));
            int m = Integer.parseInt(s.substring(t + 4, t + 6));
            return h < 24 && m < 60 ? h * 60 + m : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Contenu du fichier de cache : l'heure de recuperation et la reponse brute. */
    static String toCache(String body, long fetchedAtMs) {
        try {
            return new JSONObject().put("t", fetchedAtMs).put("body", body).toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Relit un cache ; null s'il est absent, tronque ou corrompu. */
    static WeatherData fromCache(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(text);
            return parse(o.optString("body", ""), o.optLong("t", 0));
        } catch (Exception e) {
            return null;
        }
    }

    /** Vrai si le cache a plus de {@code maxAgeMs} (ou une date inconnue). */
    boolean olderThan(long nowMs, long maxAgeMs) {
        return fetchedAtMs <= 0 || nowMs - fetchedAtMs > maxAgeMs || nowMs < fetchedAtMs;
    }
}
