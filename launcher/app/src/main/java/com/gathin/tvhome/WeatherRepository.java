package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * La meteo de l'accueil : un appel a Open-Meteo (sans cle) au plus toutes les 30 minutes, et un cache disque
 * qui sert hors ligne. Seul acces reseau de l'accueil alpin. Delai de 6 s, pas de nouvelle tentative, aucune
 * exception ne remonte : en cas d'echec on garde la derniere meteo connue.
 */
final class WeatherRepository {
    interface Listener {
        /** Appele sur le fil principal. {@code stale} : donnee issue du cache, le reseau n'a pas repondu. */
        void onWeather(WeatherData data, boolean stale);
    }

    static final long MAX_AGE_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 6000;
    private static final int MAX_BYTES = 64 * 1024;
    private static final String CACHE_NAME = "weather_cache.json";

    private final File cacheFile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean fetching;
    private volatile boolean cancelled;
    private WeatherData latest;

    WeatherRepository(Context context) {
        cacheFile = new File(context.getFilesDir(), CACHE_NAME);
    }

    /** URL de la requete pour une position. */
    static String url(double lat, double lon) {
        return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                + "&current=temperature_2m,weather_code,is_day,cloud_cover,precipitation,wind_speed_10m,visibility&daily=sunrise,sunset&timezone=auto&forecast_days=1",
                trim(lat), trim(lon));
    }

    private static String trim(double v) {
        return String.format(Locale.US, "%.5f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Derniere meteo connue (memoire, sinon cache disque), ou null. Lecture disque : appeler hors du rendu. */
    WeatherData cached() {
        if (latest == null) latest = WeatherData.fromCache(read());
        return latest;
    }

    /**
     * Rafraichit si la derniere meteo a plus de 30 minutes (ou n'existe pas). Sans coordonnees valides, ne fait rien.
     * Le listener recoit d'abord le cache, puis la meteo fraiche si le reseau a repondu.
     */
    void refreshIfStale(double lat, double lon, Listener listener) {
        cancelled = false;
        WeatherData cache = cached();
        long now = System.currentTimeMillis();
        boolean old = cache == null || cache.olderThan(now, MAX_AGE_MS);
        if (cache != null) listener.onWeather(cache, old);   // vieux de plus de 30 min : indicateur hors ligne
        if (!old || Double.isNaN(lat) || Double.isNaN(lon) || fetching) return;   // cache frais : aucun appel
        fetching = true;
        String address = url(lat, lon);
        Thread t = new Thread(() -> {
            String body = download(address);
            long at = System.currentTimeMillis();
            WeatherData fresh = WeatherData.parse(body, at);
            if (fresh != null) write(WeatherData.toCache(body, at));
            fetching = false;
            if (cancelled) return;
            main.post(() -> {
                if (cancelled) return;
                if (fresh != null) {
                    latest = fresh;
                    listener.onWeather(fresh, false);
                }
            });
        }, "weather");
        t.setDaemon(true);
        t.start();
    }

    /** L'accueil passe derriere une autre application : plus aucun rappel ne doit arriver. */
    void cancel() {
        cancelled = true;
        main.removeCallbacksAndMessages(null);
    }

    private static String download(String address) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String read() {
        if (!cacheFile.isFile() || cacheFile.length() > MAX_BYTES) return null;
        try (FileInputStream in = new FileInputStream(cacheFile)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private void write(String text) {
        if (text.isEmpty()) return;
        File tmp = new File(cacheFile.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(cacheFile)) tmp.delete();   // ecriture atomique : jamais de cache a moitie ecrit
    }
}
