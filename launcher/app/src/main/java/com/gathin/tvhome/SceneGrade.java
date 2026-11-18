package com.gathin.tvhome;

/**
 * L'etalonnage continu de la photo unique : une fonction de la hauteur du soleil (et de la meteo), jamais un saut
 * d'une image a l'autre. Entre la nuit, l'aube, le lever, le matin, midi, l'apres-midi, le coucher et le crepuscule la
 * lumiere glisse : chaque reglage est interpole entre les points de la table, avec un lissage qui n'a aucune cassure.
 *
 * L'etalonnage est une matrice de couleur (exposition, contraste, saturation, dominante RVB) plus un voile
 * atmospherique (couleur et opacite). Meme table, memes formules dans l'interface de comparaison
 * (tools/alpine_preview/grade.js) : un test compare les deux tables.
 * Logique pure, sans type Android.
 */
final class SceneGrade {
    // KEYFRAMES-BEGIN
    // hauteur du soleil (deg), exposition, contraste, saturation, gain R, G, B, voile R, G, B, opacite du voile
    static final float[][] KEYS = {
            {-20f, 0.30f, 1.06f, 0.78f, 0.62f, 0.78f, 1.16f, 0.05f, 0.08f, 0.20f, 0.00f},
            {-12f, 0.38f, 1.06f, 0.84f, 0.74f, 0.82f, 1.14f, 0.20f, 0.18f, 0.45f, 0.06f},
            {-6f, 0.60f, 1.06f, 0.96f, 0.96f, 0.88f, 1.08f, 0.50f, 0.34f, 0.62f, 0.10f},
            {-2f, 0.84f, 1.07f, 1.10f, 1.12f, 0.97f, 0.90f, 0.95f, 0.60f, 0.45f, 0.08f},
            {0f, 0.94f, 1.08f, 1.20f, 1.18f, 0.98f, 0.84f, 1.00f, 0.62f, 0.35f, 0.06f},
            {4f, 0.98f, 1.07f, 1.15f, 1.12f, 0.99f, 0.90f, 1.00f, 0.72f, 0.50f, 0.04f},
            {12f, 1.00f, 1.05f, 1.08f, 1.04f, 1.00f, 0.97f, 1.00f, 0.85f, 0.70f, 0.02f},
            {25f, 1.00f, 1.05f, 1.05f, 0.99f, 1.00f, 1.02f, 0.80f, 0.90f, 1.00f, 0.01f},
            {45f, 1.00f, 1.06f, 1.06f, 0.96f, 1.00f, 1.06f, 0.70f, 0.85f, 1.00f, 0.00f},
            {70f, 1.00f, 1.06f, 1.06f, 0.96f, 1.00f, 1.06f, 0.70f, 0.85f, 1.00f, 0.00f}};
    // KEYFRAMES-END

    /** Reglages d'une image : matrice (exposition, contraste, saturation, gain) et voile. Reutilisable (pas d'allocation). */
    static final class Grade {
        float exposure = 1f, contrast = 1f, saturation = 1f, gainR = 1f, gainG = 1f, gainB = 1f;
        float veilR, veilG, veilB, veilAlpha;

        void set(float exposure, float contrast, float saturation, float gainR, float gainG, float gainB,
                 float veilR, float veilG, float veilB, float veilAlpha) {
            this.exposure = exposure;
            this.contrast = contrast;
            this.saturation = saturation;
            this.gainR = gainR;
            this.gainG = gainG;
            this.gainB = gainB;
            this.veilR = veilR;
            this.veilG = veilG;
            this.veilB = veilB;
            this.veilAlpha = veilAlpha;
        }

        /** Vrai si cet etalonnage differe de {@code o} de plus de {@code eps} sur un reglage (evite de recreer un filtre pour rien). */
        boolean differs(Grade o, float eps) {
            return Math.abs(exposure - o.exposure) > eps || Math.abs(contrast - o.contrast) > eps
                    || Math.abs(saturation - o.saturation) > eps || Math.abs(gainR - o.gainR) > eps
                    || Math.abs(gainG - o.gainG) > eps || Math.abs(gainB - o.gainB) > eps
                    || Math.abs(veilAlpha - o.veilAlpha) > eps || Math.abs(veilR - o.veilR) > eps
                    || Math.abs(veilG - o.veilG) > eps || Math.abs(veilB - o.veilB) > eps;
        }

        void copyFrom(Grade o) {
            set(o.exposure, o.contrast, o.saturation, o.gainR, o.gainG, o.gainB, o.veilR, o.veilG, o.veilB, o.veilAlpha);
        }

        /**
         * Matrice 4x5 d'Android (ligne par ligne, decalages en 0-255) : sortie_i = gain_i * (saturation * v_i + (1 -
         * saturation) * luma(v)) avec v = contraste * exposition * entree + 0,5 * (1 - contraste), puis le voile
         * (melange). Ecrit dans `out` (20 flottants) sans allocation.
         */
        void toMatrix(float[] out) {
            float k = contrast * exposure, off = 0.5f * (1f - contrast) * 255f;
            float[] w = {LUMA_R, LUMA_G, LUMA_B};
            float keep = 1f - veilAlpha;       // le voile est un melange : sortie * (1 - a) + couleur * a
            for (int i = 0; i < 3; i++) {
                float g = i == 0 ? gainR : i == 1 ? gainG : gainB;
                float vc = (i == 0 ? veilR : i == 1 ? veilG : veilB) * veilAlpha * 255f;
                for (int j = 0; j < 3; j++) {
                    out[i * 5 + j] = keep * g * ((1f - saturation) * w[j] + (i == j ? saturation : 0f)) * k;
                }
                out[i * 5 + 3] = 0f;
                out[i * 5 + 4] = keep * g * off + vc;
            }
            out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f;
        }

        /** Applique l'etalonnage a une couleur (composantes 0 a 1) : sert au modele de contraste et aux tests. */
        void apply(float[] rgb) {
            float k = contrast * exposure, off = 0.5f * (1f - contrast);
            float vr = k * rgb[0] + off, vg = k * rgb[1] + off, vb = k * rgb[2] + off;
            float l = LUMA_R * vr + LUMA_G * vg + LUMA_B * vb;
            float r = clamp(gainR * (saturation * vr + (1f - saturation) * l));
            float g = clamp(gainG * (saturation * vg + (1f - saturation) * l));
            float b = clamp(gainB * (saturation * vb + (1f - saturation) * l));
            rgb[0] = r + (veilR - r) * veilAlpha;     // le voile atmospherique est dans la matrice de couleur
            rgb[1] = g + (veilG - g) * veilAlpha;
            rgb[2] = b + (veilB - b) * veilAlpha;
        }
    }

    static final float LUMA_R = 0.2126f, LUMA_G = 0.7152f, LUMA_B = 0.0722f;

    // Calques de la scene, communs au dessin (PhotoBackdrop) et au modele de contraste (SceneZones) : le modele est
    // toujours au moins aussi pessimiste que ce qui est dessine.
    /** Nuages sur le ciel de la photo : opacite maximale et couleur. */
    static final float CLOUD_ALPHA = 0.35f;
    static final float[] CLOUD_RGB = {0.62f, 0.65f, 0.70f};
    /** Nappe de brouillard sur toute la photo. */
    static final float FOG_ALPHA = 0.60f;
    static final float[] FOG_RGB = {0.82f, 0.85f, 0.88f};
    /** Halo du soleil sur le ciel (pire cas : sous l'astre). */
    static final float SUN_GLOW_ALPHA = 0.40f;
    static final float[] SUN_RGB = {1.00f, 0.93f, 0.80f};
    /** Assombrissement de l'orage sur toute la scene. */
    static final float STORM_ALPHA = 0.28f;
    static final float[] STORM_RGB = {0.04f, 0.05f, 0.07f};
    /** Verre sombre des pilules (alpine) : opacite et couleur. */
    static final float PILL_GLASS_ALPHA = 0.86f;
    static final float[] PILL_GLASS_RGB = {0.055f, 0.078f, 0.102f};

    private SceneGrade() {
    }

    static float clamp(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    static float smoothstep(float a, float b, float x) {
        float t = clamp((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }

    /**
     * Reglages de base a la hauteur {@code elevation} (deg) : interpolation lisse entre les points de la table (la
     * courbe d'interpolation est un smoothstep, donc sans cassure aux points), hors meteo. {@code evening} (0 le matin,
     * 1 le soir) rechauffe un peu le coucher par rapport au lever.
     */
    static float[] base(float elevation, float evening) {
        return base(elevation, evening, new float[10]);
    }

    /** Meme calcul, sans allocation : ecrit les 10 reglages dans {@code out}. */
    static float[] base(float elevation, float evening, float[] out) {
        float[][] k = KEYS;
        float e = Math.max(k[0][0], Math.min(k[k.length - 1][0], elevation));
        int i = 0;
        while (i < k.length - 2 && e > k[i + 1][0]) i++;
        float t = smoothstep(k[i][0], k[i + 1][0], e);
        for (int c = 0; c < 10; c++) out[c] = k[i][c + 1] + (k[i + 1][c + 1] - k[i][c + 1]) * t;
        float warm = evening * smoothstep(-10f, 0f, e) * (1f - smoothstep(0f, 14f, e));
        out[3] *= 1f + 0.06f * warm;      // gain R : le coucher est un peu plus chaud que le lever
        out[5] *= 1f - 0.07f * warm;      // gain B
        return out;
    }

    /** Etalonnage complet (alloue un objet : pour les tests et le modele) ; voir {@link #compute(float, float, SceneMix, Grade, float[])}. */
    static Grade compute(float elevation, float evening, SceneMix m) {
        Grade g = new Grade();
        compute(elevation, evening, m, g, new float[10]);
        return g;
    }

    /** Etalonnage complet dans {@code out}, sans allocation : la base a la hauteur du soleil, modifiee par la meteo. */
    static void compute(float elevation, float evening, SceneMix m, Grade out, float[] tmp) {
        float[] b = base(elevation, evening, tmp);
        float c = m.cloud, f = m.fog, r = m.rain, s = m.snow, t = m.storm;
        float exposure = b[0] * (1f - 0.16f * c - 0.18f * r - 0.06f * s - 0.52f * t - 0.05f * f);
        float contrast = b[1] * (1f - 0.20f * c - 0.25f * f - 0.18f * r - 0.22f * s - 0.10f * t);
        float sat = b[2] * Math.max(0.25f, 1f - 0.35f * c - 0.45f * f - 0.38f * r - 0.40f * s - 0.50f * t);
        float cool = 0.05f * r + 0.04f * s + 0.04f * c + 0.06f * t;   // la lumiere baisse et se refroidit
        float gr = b[3] * (1f - cool), gg = b[4], gb = b[5] * (1f + 0.8f * cool);
        // Voile : celui de l'heure, plus un voile gris pour le couvert, blanc pour le brouillard, laiteux pour la neige.
        float a = b[9], vr = b[6], vg = b[7], vb = b[8];
        for (int l = 0; l < 4; l++) {
            float lr, lg, lb, la;
            if (l == 0) { lr = 0.74f; lg = 0.77f; lb = 0.82f; la = 0.12f * c; }
            else if (l == 1) { lr = 0.80f; lg = 0.84f; lb = 0.88f; la = 0.34f * f; }
            else if (l == 2) { lr = 0.92f; lg = 0.95f; lb = 1.00f; la = 0.30f * s; }
            else { lr = 0.20f; lg = 0.24f; lb = 0.32f; la = 0.18f * t; }
            float na = 1f - (1f - a) * (1f - la);
            if (na > 0f) {
                vr = (vr * a * (1f - la) + lr * la) / na;
                vg = (vg * a * (1f - la) + lg * la) / na;
                vb = (vb * a * (1f - la) + lb * la) / na;
            }
            a = na;
        }
        out.set(exposure, contrast, sat, gr, gg, gb, vr, vg, vb, a);
    }

    // ---- ce que la lumiere montre : soleil, etoiles, lune ---------------------------------------------------------

    /** Intensite du soleil (0 a 1) : il se leve en trois degres, les nuages, la pluie et le brouillard l'eteignent. */
    static float sunStrength(float elevation, SceneMix m) {
        float s = smoothstep(-3f, 3f, elevation);
        return s * (1f - 0.92f * m.cloud) * (1f - 0.7f * m.rain) * (1f - 0.35f * m.fog) * (1f - 0.85f * m.snow)
                * (1f - m.storm);
    }

    /** Disque pale dans le brouillard : le soleil reste visible mais lave. */
    static float sunDiscPale(float elevation, SceneMix m) {
        return smoothstep(-2f, 4f, elevation) * m.fog * (1f - m.rain) * (1f - m.snow) * (1f - m.storm);
    }

    /** Etoiles : elles apparaissent quand le soleil est a plus de cinq degres sous l'horizon, ciel degage. */
    static float starAlpha(float elevation, SceneMix m) {
        float s = 1f - smoothstep(-14f, -5f, elevation);
        return s * (1f - m.cloud) * (1f - m.rain) * (1f - m.snow) * (1f - m.fog) * (1f - m.storm);
    }

    /** Fraction du ciel de la photo cachee par les nuages (0 a 1) : le couvert l'occulte progressivement. */
    /** Brume ressentie : le brouillard, mais aussi le voile de la pluie et de la neige (la visibilite baisse). */
    static float haze(SceneMix m) {
        return Math.max(m.fog, Math.max(0.40f * m.rain, Math.max(0.35f * m.snow, 0.12f * m.cloud)));
    }

    /** Luminosite des nuages et de la brume : sombres la nuit, pleins le jour (le modele de contraste s'en sert aussi). */
    static float dayFactor(float elevation) {
        return 0.18f + 0.82f * smoothstep(-13f, 6f, elevation);
    }

    static float skyCover(SceneMix m) {
        return clamp(Math.max(m.cloud, 0.9f * m.storm));
    }

    /** Phases de la journee (aube ou crepuscule selon le soir, jour, nuit) en poids qui somment 1, pour la scene dessinee. */
    static float[] phaseWeights(float elevation, float evening) {
        float s1 = smoothstep(-14f, -4f, elevation), s2 = smoothstep(0f, 10f, elevation);
        float night = 1f - s1, twilight = Math.max(0f, s1 - s2), day = s2;
        return new float[]{(1f - evening) * twilight, day, evening * twilight, night};   // dawn, day, dusk, night
    }
}
