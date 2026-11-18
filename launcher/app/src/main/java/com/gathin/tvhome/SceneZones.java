package com.gathin.tvhome;

/**
 * Le modele de contraste de l'accueil alpin : pour chaque zone de texte (heure, date, salutation, pilules, titre du
 * dock), la luminosite la plus sombre et la plus claire du fond reellement dessine derriere elle, avec l'etalonnage de
 * l'instant, la meteo, les nuages, le brouillard et le halo du soleil ; puis le plan de lisibilite (couleur du texte et
 * voile local) qui garantit le rapport de contraste dans le pire cas de la zone.
 *
 * Le fond vient de la grille de couleur de la photo (manifeste du pack) et de la courbe de crete, qui separe le ciel (nuages
 * et halo) du terrain. Logique pure, sans type Android, testee sur toutes les combinaisons de phase et de meteo.
 */
final class SceneZones {
    enum Zone { CLOCK, DATE, GREETING, PILL_0, PILL_1, PILL_2, HERO }

    static final int GW = 32, GH = 18;

    private SceneZones() {
    }

    /** Seuil de contraste de la zone : 3:1 pour la grande horloge, 4,5:1 pour le reste. */
    static float threshold(Zone z) {
        return z == Zone.CLOCK ? ContrastPlan.CLOCK_RATIO : ContrastPlan.TEXT_RATIO;
    }

    /** Vrai pour les textes dont la couleur n'est pas dessinee par le fond (vues Android blanches). */
    static boolean whiteFixed(Zone z) {
        return z == Zone.PILL_0 || z == Zone.PILL_1 || z == Zone.PILL_2 || z == Zone.HERO;
    }

    /** Rectangle {gauche, haut, droite, bas} de la zone sur un ecran de w x h. */
    static float[] rect(Zone z, float w, float h) {
        float[] clock = AlpineMetrics.clockVisible(w, h);
        float[] lines = AlpineMetrics.dateLines(w, h);
        float half = w * 0.20f;
        switch (z) {
            case CLOCK: {   // sans le fondu de la rive : le premier plan y recouvre deja les chiffres
                float digit = clock[3] - clock[1];
                return new float[]{clock[0], clock[1], clock[2], clock[3] - AlpineMetrics.SOFT * digit};
            }
            case DATE: return new float[]{w / 2f - half, lines[0] - lines[2] * 1.1f, w / 2f + half, lines[0] + lines[2] * 0.3f};
            case GREETING: return new float[]{w / 2f - half * 0.6f, lines[1] - lines[3] * 1.1f, w / 2f + half * 0.6f, lines[1] + lines[3] * 0.3f};
            case HERO: {
                float top = AlpineDock.cardTop((int) h);
                return new float[]{w * 0.05f, top - h * 0.16f, w * 0.27f, top - h * 0.05f};
            }
            default: {
                float[] p = AlpineMetrics.pill(z.ordinal() - Zone.PILL_0.ordinal(), w, h);
                return new float[]{p[0], p[1], p[0] + p[2], p[1] + p[3]};
            }
        }
    }

    /** Hauteur de la crete (en pixels) a l'abscisse x : la courbe du manifeste, interpolee. */
    static float crestY(float[] crest, float x, float w, float h) {
        if (crest == null || crest.length < 2) return h * 0.4f;
        float f = Math.max(0f, Math.min(1f, x / w)) * (crest.length - 1);
        int i = Math.min(crest.length - 2, (int) f);
        return (crest[i] + (crest[i + 1] - crest[i]) * (f - i)) * h;
    }

    /**
     * Luminances relatives {min, max} du fond de la zone. {@code grid} : 32 x 18 x 3 octets de la photo ; {@code crest} :
     * courbe de crete en fractions de la hauteur ; {@code tmp} : 6 flottants de travail (aucune allocation ici).
     */
    static void range(Zone z, float w, float h, int[] grid, float[] crest, SceneGrade.Grade g, SceneMix m,
                      float[] out, float[] tmp) {
        float[] r = rect(z, w, h);
        float cw = w / GW, ch = h / GH;
        int x0 = Math.max(0, (int) (r[0] / cw)), x1 = Math.min(GW - 1, (int) (r[2] / cw));
        int y0 = Math.max(0, (int) (r[1] / ch)), y1 = Math.min(GH - 1, (int) (r[3] / ch));
        float cover = SceneGrade.skyCover(m) * SceneGrade.CLOUD_ALPHA;
        float glow = SceneGrade.SUN_GLOW_ALPHA * SceneGrade.sunStrength(m.elevation, m);
        float fog = SceneGrade.FOG_ALPHA * SceneGrade.haze(m), storm = SceneGrade.STORM_ALPHA * m.storm;
        float day = SceneGrade.dayFactor(m.elevation);
        float lo = 2f, hi = -1f;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float cy = crestY(crest, (x + 0.5f) * cw, w, h);
                boolean maybeSky = y * ch < cy, maybeGround = (y + 1) * ch > cy;
                for (int pass = 0; pass < 2; pass++) {
                    boolean sky = pass == 0;
                    if (sky ? !maybeSky : !maybeGround) continue;
                    int o = (y * GW + x) * 3;
                    tmp[0] = grid[o] / 255f;
                    tmp[1] = grid[o + 1] / 255f;
                    tmp[2] = grid[o + 2] / 255f;
                    g.apply(tmp);                      // etalonnage et voile atmospherique (dans la matrice de couleur)
                    // Nuages, halo du soleil et nappes de brouillard peuvent etre presents ou non derriere un texte :
                    // on retient le pire cas dans les deux sens (8 combinaisons), jamais la moyenne.
                    for (int mask = 0; mask < 8; mask++) {
                        tmp[3] = tmp[0];
                        tmp[4] = tmp[1];
                        tmp[5] = tmp[2];
                        if ((mask & 1) != 0 && sky) blendDim(tmp, SceneGrade.CLOUD_RGB, day, cover);
                        if ((mask & 2) != 0 && sky) blend(tmp, 3, SceneGrade.SUN_RGB, glow);
                        if ((mask & 4) != 0) blendDim(tmp, SceneGrade.FOG_RGB, day, fog);
                        blend(tmp, 3, SceneGrade.STORM_RGB, storm);          // l'orage assombrit toute la scene
                        if (z == Zone.PILL_0 || z == Zone.PILL_1 || z == Zone.PILL_2) {
                            blend(tmp, 3, SceneGrade.PILL_GLASS_RGB, SceneGrade.PILL_GLASS_ALPHA);   // verre sombre de la pilule
                        }
                        float l = ContrastPlan.luminance(tmp[3], tmp[4], tmp[5]);
                        if (l < lo) lo = l;
                        if (l > hi) hi = l;
                    }
                }
            }
        }
        // La grille est une moyenne par cellule : les ombres et les reflets plus fins que 60 px n'y apparaissent pas.
        // On elargit donc l'intervalle (mesure sur les pixels rendus par l'interface de comparaison).
        boolean fixed = whiteFixed(z);   // texte blanc impose : les details clairs du premier plan comptent, la marge haute est large
        out[0] = lo > hi ? 0f : lo * (fixed ? FIXED_LOW : z == Zone.CLOCK ? RANGE_LOW : TEXT_LOW);
        out[1] = hi < 0f ? 0f : Math.min(1f, hi * (fixed ? FIXED_HIGH : RANGE_HIGH));
    }

    static final float RANGE_LOW = 0f, TEXT_LOW = 0.5f, PREFER_WHITE_TEXT = 0.38f, RANGE_HIGH = 1.25f, FIXED_LOW = 0.40f, FIXED_HIGH = 4f;

    private static void blendDim(float[] c, float[] rgb, float k, float a) {
        if (a <= 0f) return;
        c[3] += (rgb[0] * k - c[3]) * a;
        c[4] += (rgb[1] * k - c[4]) * a;
        c[5] += (rgb[2] * k - c[5]) * a;
    }

    private static void blend(float[] c, int at, float[] rgb, float a) {
        if (a <= 0f) return;
        c[at] += (rgb[0] - c[at]) * a;
        c[at + 1] += (rgb[1] - c[at + 1]) * a;
        c[at + 2] += (rgb[2] - c[at + 2]) * a;
    }

    /** Plan de lisibilite d'une zone (voir {@link ContrastPlan}). */
    static ContrastPlan.Plan plan(Zone z, float w, float h, int[] grid, float[] crest, SceneGrade.Grade g, SceneMix m,
                                  float[] out, float[] tmp) {
        range(z, w, h, grid, crest, g, m, out, tmp);
        return whiteFixed(z) ? ContrastPlan.decideWhite(out[0], out[1], threshold(z))
                : ContrastPlan.decide(out[0], out[1], threshold(z), z == Zone.CLOCK ? ContrastPlan.PREFER_WHITE : PREFER_WHITE_TEXT);
    }
}
