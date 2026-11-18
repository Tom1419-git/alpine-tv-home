package com.gathin.tvhome;

/**
 * Couleur d'accent tiree d'une pochette : moyenne des pixels ponderee par leur saturation, puis assombrie pour rester
 * lisible sur un ciel clair. Logique pure sur des pixels ARGB, testee en JVM.
 */
final class AccentColor {
    /** Luminance maximale de l'accent : au-dessus, il se perd sur un ciel clair. */
    static final float MAX_LUMA = 0.42f;
    static final int FALLBACK = 0xFF3B4A63;

    private AccentColor() {
    }

    static int derive(int[] pixels, int count) {
        if (pixels == null || count <= 0) return FALLBACK;
        double r = 0, g = 0, b = 0, total = 0;
        for (int i = 0; i < count; i++) {
            int p = pixels[i];
            if ((p >>> 24) < 128) continue;
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            int max = Math.max(pr, Math.max(pg, pb));
            int min = Math.min(pr, Math.min(pg, pb));
            double sat = max == 0 ? 0 : (max - min) / (double) max;
            double w = sat * sat + 0.02;
            r += pr * w;
            g += pg * w;
            b += pb * w;
            total += w;
        }
        if (total <= 0) return FALLBACK;
        return darken((int) Math.round(r / total), (int) Math.round(g / total), (int) Math.round(b / total));
    }

    /** Ramene la luminance sous {@link #MAX_LUMA} (et au-dessus d'un plancher) en gardant la teinte. */
    static int darken(int r, int g, int b) {
        float luma = TextContrast.luma(0xFF000000 | (r << 16) | (g << 8) | b);
        float k = luma > MAX_LUMA ? MAX_LUMA / luma : 1f;
        r = Math.round(r * k);
        g = Math.round(g * k);
        b = Math.round(b * k);
        if (TextContrast.luma(0xFF000000 | (r << 16) | (g << 8) | b) < 0.10f) {   // pochette noire : un gris-bleu discret
            r = Math.round(r * 0.5f + 0x3B * 0.5f);
            g = Math.round(g * 0.5f + 0x4A * 0.5f);
            b = Math.round(b * 0.5f + 0x63 * 0.5f);
        }
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** L'accent rapproche du blanc, pour la barre de progression sur le verre sombre du bloc. */
    static int lift(int accent) {
        int r = ((accent >> 16) & 0xFF), g = ((accent >> 8) & 0xFF), b = (accent & 0xFF);
        r = Math.round(r + (255 - r) * 0.45f);
        g = Math.round(g + (255 - g) * 0.45f);
        b = Math.round(b + (255 - b) * 0.45f);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
