package com.gathin.tvhome;

/**
 * Le dock de l'accueil alpin : les quatre applications du dock sur la premiere rangee, les autres juste en dessous
 * (le reglage existant rows_below_dock, mis a 1 par defaut dans l'accueil alpin), tout dans l'ecran, sans rien de coupe.
 *
 * La premiere rangee se pose a 56,7 % de la hauteur, sous l'heure remontee ; la taille des tuiles est celle qui
 * fait tenir toutes les rangees avant la marge basse de 5 %. Pas de nouveau systeme d'epinglage : l'ordre, la
 * visibilite et le nombre de colonnes restent ceux des reglages du lanceur.
 * Logique pure, sans type Android.
 */
final class AlpineDock {
    /** Haut de la premiere rangee de tuiles, en fraction de la hauteur de l'ecran (612 px sur 1080). */
    static final float TOP_FRAC = 0.567f;
    /** Bas des dernieres tuiles : marge de securite de 5 %. */
    static final float BOTTOM_FRAC = 0.95f;
    /** Espace entre deux rangees de tuiles (dp) : 7 dp de nom, 22 dp d'etiquette et 8 dp de marge d'item. */
    static final float ROW_GAP_DP = 37f;
    /** Opacite des rangees sous le dock : plus discretes que le dock. */
    static final float BELOW_ALPHA = 0.72f;
    /** Opacite par defaut de la bande du dock dans l'accueil alpin (0 a 100) : translucide, le lac reste visible. */
    static final int BAND_OPACITY = 35;

    private AlpineDock() {
    }

    static int cardTop(int screenH) {
        return Math.round(TOP_FRAC * screenH);
    }

    /** Hauteur d'une tuile pour {@code rowsBelow} rangees sous le dock (0 : le dock seul). */
    static int tileHeight(int screenH, int rowsBelow, float density) {
        float room = BOTTOM_FRAC * screenH - cardTop(screenH) - rowsBelow * ROW_GAP_DP * density;
        return Math.max(Math.round(40 * density), (int) (room / (rowsBelow + 1)));
    }

    /** Largeur de tuile : celle de la hauteur, jamais plus que la taille de conception ni que la place en largeur. */
    static int tileWidth(int screenH, int rowsBelow, int fitWidth, float density) {
        int byHeight = Math.round(tileHeight(screenH, rowsBelow, density) * AppAdapter.DESIGN_W_DP / AppAdapter.DESIGN_H_DP);
        return Math.max(Math.round(80 * density), Math.min(byHeight, fitWidth));
    }

    /** Bas de la derniere tuile pour ces reglages : doit rester sous la marge de 5 %. */
    static int lastBottom(int screenH, int rowsBelow, int tileH, float density) {
        return cardTop(screenH) + (rowsBelow + 1) * tileH + Math.round(rowsBelow * ROW_GAP_DP * density);
    }
}
