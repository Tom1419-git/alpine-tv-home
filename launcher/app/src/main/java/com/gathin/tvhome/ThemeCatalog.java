package com.gathin.tvhome;

import android.content.Context;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Theme previews/applications from the archive installed beside (not inside) the launcher APK. */
final class ThemeCatalog {
    static final String FILE_NAME = "theme_catalog.zip";
    static final String CHOICE_KEY = "theme_catalog_choice";
    private static final int MAX_CONFIG_BYTES = 64 * 1024;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_THEME_BYTES = 24L * 1024 * 1024;
    static final class Theme {
        final String id;
        final String label;
        Theme(String id) {
            this.id = id;
            this.label = label(id);
        }
    }

    private ThemeCatalog() {}

    static String label(String id) {
        String[] words = id.replace('-', ' ').split(" ");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (title.length() > 0) title.append(' ');
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return title.toString();
    }

    static List<Theme> list(Context context) throws IOException {
        File archive = archive(context);
        if (archive == null || !archive.isFile()) throw new IOException("Theme catalog is not installed");
        Set<String> ids = new HashSet<>();
        Map<String, Integer> themeFiles = new HashMap<>();
        Map<String, Long> themeBytes = new HashMap<>();
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}/(?:config\\.json|wallpaper\\.jpg|wallpapers/(?:[A-Za-z0-9_.-]+\\.(?:jpg|jpeg|png|webp)|captions\\.txt))")) continue;
                String id = name.substring(0, name.indexOf('/'));
                themeFiles.put(id, themeFiles.containsKey(id) ? themeFiles.get(id) + 1 : 1);
                long bytes = Math.max(0, entry.getSize());
                themeBytes.put(id, themeBytes.containsKey(id) ? themeBytes.get(id) + bytes : bytes);
                if (name.endsWith("/config.json")) ids.add(id);
            }
        }
        for (String id : new ArrayList<>(ids)) {
            if (themeFiles.get(id) > 102 || themeBytes.get(id) > MAX_THEME_BYTES) ids.remove(id);
        }
        List<Theme> themes = new ArrayList<>();
        for (String id : ids) themes.add(new Theme(id));
        Collections.sort(themes, Comparator.comparing(theme -> theme.label.toLowerCase(Locale.ROOT)));
        if (themes.isEmpty()) throw new IOException("Theme catalog is empty");
        return themes;
    }

    static void reset(Context context) throws IOException {
        File dir = Config.dir(context);
        if (dir == null || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            throw new IOException("External storage is not available");
        File config = new File(dir, Config.FILE_NAME);
        if (config.exists() && !config.delete()) throw new IOException("Could not remove selected theme");
        File single = new File(dir, Config.WALLPAPER_NAME);
        if (single.exists() && !single.delete()) throw new IOException("Could not remove old wallpaper");
        File wallpapers = new File(dir, ThemeFiles.WALLPAPERS);
        File[] oldImages = wallpapers.listFiles((folder, name) -> ThemeFiles.collectionImage(name));
        if (oldImages != null) for (File old : oldImages) if (!old.delete()) throw new IOException("Could not remove old wallpapers");
        File captions = new File(wallpapers, WallpaperCaptions.FILE_NAME);
        if (captions.exists() && !captions.delete()) throw new IOException("Could not remove wallpaper captions");
        File revision = new File(dir, "theme_revision");
        try (FileOutputStream out = new FileOutputStream(revision)) {
            out.write(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.US_ASCII));
        }
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .remove(Config.WALLPAPER_CHOICE_KEY).apply();
        Config.rememberThemeSignature(context);
    }

    static void apply(Context context, String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}") || id.contains(".."))
            throw new IOException("Invalid theme name");
        File dir = Config.dir(context);
        if (dir == null || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            throw new IOException("External storage is not available");
        File archive = archive(context);
        if (archive == null || !archive.isFile()) throw new IOException("Theme catalog is not installed");

        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(id + "/")) continue;
                String relative = entry.getName().substring(id.length() + 1);
                if (!allowed(relative)) continue;
                int limit = "config.json".equals(relative) ? MAX_CONFIG_BYTES : MAX_IMAGE_BYTES;
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = readBounded(in, limit);
                }
                total += bytes.length;
                if (total > MAX_THEME_BYTES) throw new IOException("Theme is too large");
                files.put(relative, bytes);
                if (files.size() > 102) throw new IOException("Theme has too many files");
            }
        }
        if (!files.containsKey("config.json")) throw new IOException("Theme config is missing");

        // Stage and validate the complete selection privately before replacing the active theme files.
        File staging = new File(context.getCacheDir(), "theme-" + UUID.randomUUID());
        if (!staging.mkdirs()) throw new IOException("Could not stage theme");
        try {
            for (Map.Entry<String, byte[]> item : files.entrySet()) {
                File out = new File(staging, item.getKey());
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not stage theme files");
                try (FileOutputStream stream = new FileOutputStream(out)) { stream.write(item.getValue()); }
            }
            installFiles(dir, staging, files.keySet());
            context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(Config.WALLPAPER_CHOICE_KEY).apply();
            Config.rememberThemeSignature(context);
        } finally {
            deleteTree(staging);
        }
    }

    private static File archive(Context context) {
        File dir = Config.dir(context);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static boolean allowed(String path) {
        if ("config.json".equals(path) || Config.WALLPAPER_NAME.equals(path)) return true;
        if (!path.startsWith(ThemeFiles.WALLPAPERS + "/")) return false;
        String name = path.substring(ThemeFiles.WALLPAPERS.length() + 1);
        return !name.contains("/") && (ThemeFiles.collectionImage(name)
                || WallpaperCaptions.FILE_NAME.equals(name));
    }

    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > limit) throw new IOException("Theme file is too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void installFiles(File dir, File staging, Set<String> names) throws IOException {
        File wallpapers = new File(dir, ThemeFiles.WALLPAPERS);
        File[] oldImages = wallpapers.listFiles((folder, name) -> ThemeFiles.collectionImage(name));
        if (oldImages != null) for (File old : oldImages) if (!old.delete()) throw new IOException("Could not replace old wallpapers");
        File oldCaptions = new File(wallpapers, WallpaperCaptions.FILE_NAME);
        if (oldCaptions.exists() && !oldCaptions.delete()) throw new IOException("Could not replace wallpaper captions");
        File oldSingle = new File(dir, Config.WALLPAPER_NAME);
        if (oldSingle.exists() && !oldSingle.delete()) throw new IOException("Could not replace wallpaper");

        for (String name : names) {
            if ("config.json".equals(name)) continue;
            copy(new File(staging, name), new File(dir, name));
        }
        File config = new File(dir, Config.FILE_NAME);
        File pending = new File(dir, Config.FILE_NAME + ".new");
        copy(new File(staging, "config.json"), pending);
        if (config.exists() && !config.delete()) throw new IOException("Could not replace theme config");
        if (!pending.renameTo(config)) throw new IOException("Could not activate theme config");
        File revision = new File(dir, "theme_revision");
        try (FileOutputStream out = new FileOutputStream(revision)) {
            out.write(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void copy(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create theme folder");
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
