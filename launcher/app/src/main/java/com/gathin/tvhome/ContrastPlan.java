package com.gathin.tvhome;

/**
 * Lisibilite garantie : pour une zone de texte dont on connait la luminosite la plus sombre et la plus claire du fond
 * (apres etalonnage, meteo et voiles), on choisit la couleur du texte et, si besoin, un voile local borne derriere lui,
 * de facon que le rapport de contraste (WCAG) soit d'au moins 4,5:1 pour un texte courant et 3:1 pour la grande
 * horloge, dans le pire cas de la zone et pas seulement en moyenne. On ne fonce jamais toute la photo.
 *
 * Un fond clair donne un texte sombre ; un fond sombre un texte blanc ; une zone mixte (ciel clair contre roche sombre)
 * un texte sombre sur un voile blanc leger : eclaircir le sombre coute bien moins qu'assombrir le clair.
 * Logique pure, sans type Android.
 */
final class ContrastPlan {
    static final float TEXT_RATIO = 4.5f, CLOCK_RATIO = 3.0f;
    /** Luminance relative du texte sombre (#0B1620) et blanc du texte clair. */
    static final float DARK_L = 0.0074f, WHITE_L = 1f;
    /** Opacite maximale d'un voile local : au-dela, le plan echoue (le test le voit). */
    static final float MAX_VEIL = 0.60f;

    /** Le plan d'une zone : couleur du texte, voile (blanc si le texte est sombre, noir sinon) et rapport obtenu. */
    static final class Plan {
        final boolean darkText;
        final float veilAlpha;
        final float ratio;
        final boolean ok;

        Plan(boolean darkText, float veilAlpha, float ratio, boolean ok) {
            this.darkText = darkText;
            this.veilAlpha = veilAlpha;
            this.ratio = ratio;
            this.ok = ok;
        }
    }

    private ContrastPlan() {
    }

    static float ratio(float l1, float l2) {
        float hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05f) / (lo + 0.05f);
    }

    /** Luminance relative WCAG d'une couleur sRGB (composantes 0 a 1). */
    static float luminance(float r, float g, float b) {
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b);
    }

    private static float lin(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    /**
     * Choix libre de la couleur du texte. {@code lMin} et {@code lMax} : luminances relatives du fond le plus sombre
     * et le plus clair de la zone.
     */
    /** Voile noir au-dela duquel on prefere le texte sombre sur voile clair. */
    static final float PREFER_WHITE = 0.50f;

    static Plan decide(float lMin, float lMax, float threshold) {
        return decide(lMin, lMax, threshold, PREFER_WHITE);
    }

    /** Comme {@link #decide(float, float, float)} ; {@code preferWhite} : voile noir au-dela duquel le texte sombre est choisi. */
    static Plan decide(float lMin, float lMax, float threshold, float preferWhite) {
        // Les voiles se melangent dans l'espace sRGB du canevas (pas en luminance lineaire) : tout est calcule en sRGB.
        // Texte blanc : tout le fond doit etre assez sombre ; voile noir d'opacite a.
        float needMax = WHITE_L + 0.05f;
        float capWhite = needMax / threshold - 0.05f;
        float sMax = srgb(lMax), sMin = srgb(lMin);
        float aWhite = lMax <= capWhite ? 0f : 1f - srgb(capWhite) / Math.max(sMax, 1e-4f);
        // Texte sombre : tout le fond doit etre assez clair ; voile blanc d'opacite a.
        float needMin = threshold * (DARK_L + 0.05f) - 0.05f;
        float aDark = lMin >= needMin ? 0f : (srgb(needMin) - sMin) / Math.max(1f - sMin, 1e-4f);
        // Le texte blanc est la regle : le texte sombre ne sert que si le voile noir necessaire depasse PREFER_WHITE.
        boolean dark = aWhite > preferWhite && aDark < aWhite - 1e-4f;
        float a = Math.max(0f, dark ? aDark : aWhite);
        // Marge : le voile est plus faible sur ses bords fondus, et un pixel isole peut etre plus extreme que la grille.
        a = a == 0f ? 0f : Math.min(1f, a + (dark ? 0.09f : 0.08f));
        float lo = dark ? lin(sMin + a * (1f - sMin)) : lin(sMin * (1f - a));
        float hi = dark ? lin(sMax + a * (1f - sMax)) : lin(sMax * (1f - a));
        float r = dark ? ratio(lo, DARK_L) : ratio(hi, WHITE_L);
        return new Plan(dark, a, r, a <= MAX_VEIL && r + 1e-3f >= threshold);
    }

    /** Texte blanc impose (pilules, titre du dock : ce sont des vues Android) : seul le voile noir est libre. */
    static Plan decideWhite(float lMin, float lMax, float threshold) {
        float cap = (WHITE_L + 0.05f) / threshold - 0.05f;
        float a = lMax <= cap ? 0f : Math.min(1f, 1f - srgb(cap) / Math.max(srgb(lMax), 1e-4f) + 0.04f);
        float r = ratio(lin(srgb(lMax) * (1f - a)), WHITE_L);
        return new Plan(false, a, r, a <= MAX_VEIL && r + 1e-3f >= threshold);
    }

    /** Valeur sRGB (0 a 1) d'un gris de luminance relative l. */
    static float srgb(float l) {
        l = Math.max(0f, Math.min(1f, l));
        return l <= 0.0031308f ? l * 12.92f : (float) (1.055 * Math.pow(l, 1 / 2.4) - 0.055);
    }
}
