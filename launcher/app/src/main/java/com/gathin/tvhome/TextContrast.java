package com.gathin.tvhome;

/**
 * Choix de la couleur du texte de l'accueil selon la luminance de la zone qu'il recouvre : ciel clair, chiffres
 * tres sombres ; nuit, chiffres clairs avec un halo. Aucune couleur en dur dans la scene. Logique pure.
 */
final class TextContrast {
    /** Au-dessus de ce seuil de luminance (0 a 1), le fond est clair et le texte devient sombre. */
    static final float LIGHT_BACKGROUND = 0.52f;

    private TextContrast() {
    }

    /** Luminance relative approchee (Rec. 709 sur les composantes sRGB) d'une couleur ARGB, de 0 a 1. */
    static float luma(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    static boolean darkText(float backgroundLuma) {
        return backgroundLuma > LIGHT_BACKGROUND;
    }

    /** Couleur du texte principal. */
    static int textColor(float backgroundLuma) {
        return darkText(backgroundLuma) ? 0xFF0B1620 : 0xFFFFFFFF;
    }

    /** Couleur du halo : clair derriere un texte sombre, sombre derriere un texte clair. */
    static int haloColor(float backgroundLuma) {
        return darkText(backgroundLuma) ? 0x66FFFFFF : 0x99000000;
    }

    /**
     * Luminance a retenir pour une zone melant ciel clair et roche sombre : si au moins un quart de la zone est
     * sombre, on la traite comme sombre (texte clair avec halo), car un texte sombre disparaitrait sur la roche.
     */
    static float conservative(float mean, float darkFraction) {
        return darkFraction >= 0.25f ? Math.min(mean, 0.30f) : mean;
    }

    /** Luminance moyenne d'une liste de couleurs ARGB (zone echantillonnee). */
    static float meanLuma(int[] colors, int count) {
        if (count <= 0) return 0f;
        float sum = 0f;
        for (int i = 0; i < count; i++) sum += luma(colors[i]);
        return sum / count;
    }
}
