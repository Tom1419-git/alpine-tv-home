package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Le pack photo de l'accueil alpin, {@code weather_pack.zip}, transfere a cote de l'APK comme theme_catalog.zip et
 * jamais embarque dans l'APK. Ce module localise le fichier, lit son manifeste et verifie son integrite (taille et
 * SHA-256 de chaque image) avant tout usage. Un pack absent ou invalide donne simplement la scene dessinee.
 */
final class WeatherPack {
    static final String FILE_NAME = "weather_pack.zip";
    private static final String OK_KEY = "weather_pack_ok";
    private static final int MAX_MANIFEST = 64 * 1024;

    enum Status { ABSENT, INVALID, OK }

    private WeatherPack() {
    }

    static File file(Context context) {
        File dir = Config.dir(context);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /** Signature du fichier (taille et date) : l'empreinte n'est recalculee que si le fichier a change. */
    private static String signature(File f) {
        return f.length() + ":" + f.lastModified();
    }

    /** Vrai si ce fichier a deja ete verifie avec succes : permet de choisir le fond sans hacher 15 Mo. */
    static boolean knownGood(Context context) {
        File f = file(context);
        if (f == null || !f.isFile()) return false;
        SharedPreferences p = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return signature(f).equals(p.getString(OK_KEY, ""));
    }

    /** Verification complete (lecture et hachage de tout le pack) : a appeler hors du fil principal. */
    static Status validate(Context context) {
        File f = file(context);
        if (f == null || !f.isFile()) return Status.ABSENT;
        SharedPreferences p = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (signature(f).equals(p.getString(OK_KEY, ""))) return Status.OK;
        try (ZipFile zip = new ZipFile(f)) {
            WeatherPackManifest m = manifest(zip);
            if (m == null) return Status.INVALID;
            for (String path : WeatherPackManifest.FILES) {
                ZipEntry e = zip.getEntry(path);
                if (e == null || e.getSize() != m.size(path)) return Status.INVALID;
                if (!m.sha(path).equals(sha256(zip.getInputStream(e)))) return Status.INVALID;
            }
        } catch (Exception e) {
            return Status.INVALID;
        }
        p.edit().putString(OK_KEY, signature(f)).apply();
        return Status.OK;
    }

    /** Le manifeste du fichier, ou null. */
    static WeatherPackManifest manifest(Context context) {
        File f = file(context);
        if (f == null || !f.isFile()) return null;
        try (ZipFile zip = new ZipFile(f)) {
            return manifest(zip);
        } catch (Exception e) {
            return null;
        }
    }

    static WeatherPackManifest manifest(ZipFile zip) throws java.io.IOException {
        ZipEntry e = zip.getEntry("manifest.json");
        if (e == null || e.getSize() <= 0 || e.getSize() > MAX_MANIFEST) return null;
        try (InputStream in = zip.getInputStream(e)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return WeatherPackManifest.parse(out.toString("UTF-8"));
        }
    }

    static String sha256(InputStream in) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream s = in) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = s.read(buf)) > 0) d.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : d.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
