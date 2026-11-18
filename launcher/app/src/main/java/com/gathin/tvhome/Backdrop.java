package com.gathin.tvhome;

import android.graphics.Canvas;

/**
 * Un fond de l'accueil alpin. La photo unique ({@link PhotoBackdrop}) et la scene dessinee de secours
 * ({@link DrawnAlpineBackdrop}) l'implementent : la vue ne sait pas laquelle elle montre.
 * Ordre de dessin, de bas en haut : {@link #draw}, {@link #drawForeground} (une premiere fois, sous les voiles et l'heure), les voiles,
 * l'heure, puis le premier plan redessine sur la bande du bas des chiffres (ce qui les coupe), la brume et la meteo.
 * Aucune allocation dans ces methodes : elles tournent a chaque image.
 */
interface Backdrop {
    /** L'etat de la scene a dessiner : etat des horloges, melange lisse du ciel et de la meteo, etalonnage, temps. */
    final class Scene {
        HomeState state;
        final SceneMix mix = new SceneMix();
        final SceneGrade.Grade grade = new SceneGrade.Grade();
        /** Horloge de l'animation (ms), figee quand le mouvement est coupe. */
        long timeMs;
    }

    /** Prepare l'image : recree ce qui doit l'etre (filtre de couleur...). Hors du dessin ; rien par defaut. */
    default void prepare(Scene scene) {
    }

    /** Ciel, photo etalonnee, soleil, nuages, brume et reflet (derriere l'heure). */
    void draw(Canvas canvas, int width, int height, Scene scene);

    /** Dessine entre le fond et l'heure : un voile derriere les chiffres quand la photo n'a pas de premier plan. */
    default void drawUnderClock(Canvas canvas, int width, int height, Scene scene) {
    }

    /** Rive et premier plan, par-dessus l'heure, ce qui coupe le bas des chiffres. Rien par defaut. */
    default void drawForeground(Canvas canvas, int width, int height, Scene scene) {
    }

    /** Atmosphere (brouillard, voile de pluie et de neige) sur photo et premier plan, sous l'heure. Rien par defaut. */
    default void drawAtmosphere(Canvas canvas, int width, int height, Scene scene) {
    }

    /** Ligne d'eau, en fraction de la hauteur (limite basse du premier plan). */
    float waterLine();

    /** Grille de couleur de la photo et courbe de crete pour le modele de contraste ; null pour la scene dessinee. */
    default int[] colorGrid() {
        return null;
    }

    default float[] crestCurve() {
        return null;
    }

    /** Vrai si le fond bouge de lui-meme et demande des images en continu. */
    boolean animated(Scene scene);

    /** Libere ce que le fond retient (bitmaps) : l'accueil passe derriere une autre application. */
    void release();
}
