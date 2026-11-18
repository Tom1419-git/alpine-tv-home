package com.gathin.tvhome;

/**
 * La geometrie de la mise en page B de l'accueil alpin, en fractions de l'ecran (ou en pixels d'un ecran de
 * 1920 x 1080). Une seule source pour la vue, pour le dock et pour le modele de contraste ; l'interface de
 * comparaison (tools/alpine_preview/layout.js) a les memes valeurs et un test les compare.
 * Logique pure, sans type Android.
 */
final class AlpineMetrics {
    static final float REF_W = 1920f, REF_H = 1080f;
    /** Largeur de l'heure en fraction de l'ecran (tools/prepare_weather_scene.py : meme valeur). */
    static final float CLOCK_WIDTH = 0.58f;
    /**
     * Ligne de base de l'heure, en fraction de la hauteur : 0,524 (566 px) contre 0,572 (618 px) avant, soit 52 px plus
     * haut. La crete passe toujours devant le bas des chiffres : le fondu du premier plan suit la ligne de base.
     */
    static final float BASELINE = 0.524f;
    static final float ADVANCE_EM = 2.902f, DIGIT_RATIO = 0.70f;
    static final float DATE_GAP = 0.06f, DATE_SIZE = 0.024f, GREETING_SCALE = 0.78f, GREETING_GAP = 1.9f;
    /** Part de la hauteur des chiffres que le terrain peut cacher, et douceur du fondu (comme le pack). */
    static final float OCCLUSION = 0.22f, SOFT = 0.10f;
    /** Pilules d'etat (Sources, Wi-Fi, Parametres) : remontees de 54 px, haut a 64 px au lieu de 118 px. */
    static final float PILLS_TOP = 64f, PILLS_HEIGHT = 82f;
    /** Largeurs mesurees sur la TV a 1080p : x gauche et largeur de chacune. */
    static final float[][] PILLS_X = {{872f, 242f}, {1134f, 360f}, {1514f, 293f}};
    /** Marge de securite : 5 % de l'ecran. */
    static final float SAFE = 0.05f;

    private AlpineMetrics() {
    }

    /** Rectangle {left, top, right, bottom} du bloc de l'heure (chiffres visibles : jusqu'au bord du fondu). */
    static float[] clockVisible(float w, float h) {
        float text = CLOCK_WIDTH * w / ADVANCE_EM, digit = text * DIGIT_RATIO, base = BASELINE * h;
        return new float[]{w * (0.5f - CLOCK_WIDTH / 2f), base - digit, w * (0.5f + CLOCK_WIDTH / 2f),
                base - OCCLUSION * digit};
    }

    /** Hauteur du texte de l'heure, hauteur des chiffres, ligne de base. */
    static float[] clockSizes(float w, float h) {
        float text = CLOCK_WIDTH * w / ADVANCE_EM;
        return new float[]{text, text * DIGIT_RATIO, BASELINE * h};
    }

    /** Ligne de base de la date et de la salutation, taille de la date. */
    static float[] dateLines(float w, float h) {
        float[] c = clockSizes(w, h);
        float top = c[2] - c[1], size = DATE_SIZE * h, date = top - DATE_GAP * h;
        return new float[]{date, date - size * GREETING_GAP, size, size * GREETING_SCALE};
    }

    /** Pilule i : {x, y, largeur, hauteur} pour un ecran de w x h. */
    static float[] pill(int i, float w, float h) {
        float sx = w / REF_W, sy = h / REF_H;
        return new float[]{PILLS_X[i][0] * sx, PILLS_TOP * sy, PILLS_X[i][1] * sx, PILLS_HEIGHT * sy};
    }
}
