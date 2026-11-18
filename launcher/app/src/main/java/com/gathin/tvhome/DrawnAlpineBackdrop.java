package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * La scene dessinee : ciel degrade, soleil et lune a leur position reelle, quatre plans de montagne en
 * parallaxe dont le Cervin, brume, nuages, lac avec reflet. C'est le fond de J1 et le repli quand la photo
 * manque. Les formes sont construites quand l'etat ou la taille change, jamais dans {@link #draw}.
 */
final class DrawnAlpineBackdrop implements Backdrop {
    static final float HORIZON = 0.56f;
    private static final int LAYERS = 4;
    private static final int RIDGE_POINTS = 97;
    private static final int STARS = 90;
    private static final int MAX_CLOUDS = 9;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Path[] ridges = new Path[LAYERS];
    private final int[] layerColor = new int[LAYERS];
    private final float[] ridgeTop = new float[LAYERS];
    private final float[][] ridgeY = new float[LAYERS][RIDGE_POINTS];
    private final RectF oval = new RectF();
    private final float[] starX = new float[STARS];
    private final float[] starY = new float[STARS];
    private final float[] starSize = new float[STARS];
    private final float[] cloudX = new float[MAX_CLOUDS];
    private final float[] cloudY = new float[MAX_CLOUDS];
    private final float[] cloudScale = new float[MAX_CLOUDS];
    private final float[] cloudSpeed = new float[MAX_CLOUDS];

    // Etat construit par rebuild().
    private int builtW = -1, builtH = -1;
    private SolarPhase.Phase builtPhase;
    private WeatherCodes.Condition builtCondition;
    private boolean builtBlack;
    private int skyTop, skyMid, skyHorizon, waterColor, cloudColor, mistColor;
    private int cloudCount;
    private float starAlpha;
    private Shader skyShader, waterShader;
    private final Shader[] mistShader = new Shader[2];
    private final float[] mistTop = new float[2];
    // Halo du soleil : recree seulement quand sa position ou son intensite change (une fois par minute).
    private Shader sunShader;
    private float sunKeyX = -1, sunKeyY = -1, sunKeyR = -1;
    private int sunKeyColor;

    DrawnAlpineBackdrop() {
        Random r = new Random(7);
        for (int i = 0; i < STARS; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat() * HORIZON * 0.9f;
            starSize[i] = 0.6f + r.nextFloat() * 1.4f;
        }
        for (int i = 0; i < MAX_CLOUDS; i++) {
            cloudX[i] = r.nextFloat();
            cloudY[i] = 0.08f + r.nextFloat() * 0.26f;
            cloudScale[i] = 0.6f + r.nextFloat() * 0.9f;
            cloudSpeed[i] = 0.004f + r.nextFloat() * 0.006f;   // fractions de largeur par seconde
        }
        for (int i = 0; i < LAYERS; i++) ridges[i] = new Path();
    }

    @Override
    public float waterLine() {
        return HORIZON;
    }

    @Override
    public boolean animated(Scene scene) {
        return scene.state.motion;   // l'eau frissonne et les nuages derivent
    }

    /** Grille de couleur de la scene dessinee (pour le modele de contraste) : ciel, montagne, eau ; refaite a chaque changement. */
    @Override
    public int[] colorGrid() {
        return grid;
    }

    @Override
    public float[] crestCurve() {
        return crest;
    }

    private final int[] grid = new int[SceneZones.GW * SceneZones.GH * 3];
    private final float[] crest = new float[RIDGE_POINTS];

    /** Refait la grille de couleur et la courbe de crete a partir des couleurs et des plans de montagne courants. */
    private void buildModel(int h) {
        for (int i = 0; i < RIDGE_POINTS; i++) {
            float top = HORIZON * h;
            for (int l = 0; l < LAYERS - 1; l++) top = Math.min(top, ridgeY[l][i]);
            crest[i] = top / h;
        }
        for (int y = 0; y < SceneZones.GH; y++) {
            for (int x = 0; x < SceneZones.GW; x++) {
                float fy = (y + 0.5f) / SceneZones.GH, fx = (x + 0.5f) / SceneZones.GW;
                float cy = crest[Math.min(RIDGE_POINTS - 1, Math.round(fx * (RIDGE_POINTS - 1)))];
                int color = fy < cy ? Sky.lerpColor(skyTop, skyHorizon, Math.min(1f, fy / HORIZON))
                        : fy < HORIZON ? layerColor[2] : waterColor;
                int o = (y * SceneZones.GW + x) * 3;
                grid[o] = (color >> 16) & 0xFF;
                grid[o + 1] = (color >> 8) & 0xFF;
                grid[o + 2] = color & 0xFF;
            }
        }
    }

    @Override
    public void release() {
        skyShader = null;
        waterShader = null;
        mistShader[0] = null;
        mistShader[1] = null;
        sunShader = null;
        builtW = -1;
    }

    // ---- construction --------------------------------------------------------------------------------------

    private void rebuildIfNeeded(HomeState s, int w, int h) {
        if (w == builtW && h == builtH && s.phase == builtPhase && s.condition == builtCondition
                && s.blackNight == builtBlack && skyShader != null) {
            return;
        }
        builtW = w;
        builtH = h;
        builtPhase = s.phase;
        builtCondition = s.condition;
        builtBlack = s.blackNight;
        pickColors(s);
        float hz = HORIZON * h;
        skyShader = new LinearGradient(0, 0, 0, hz, new int[]{skyTop, skyMid, skyHorizon},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        int deep = Sky.lerpColor(waterColor, 0xFF000000, 0.35f);
        waterShader = new LinearGradient(0, hz, 0, h,
                new int[]{(waterColor & 0x00FFFFFF) | 0x59000000, (deep & 0x00FFFFFF) | 0xE6000000},
                null, Shader.TileMode.CLAMP);
        buildRidges(w, h);
        buildMist(s, h);
        cloudCount = cloudsFor(s.condition);
        starAlpha = starsFor(s);
        buildModel(h);
    }

    /** Deux nappes de brume, une devant chacun des deux plans les plus lointains. */
    private void buildMist(HomeState s, int h) {
        float strength = s.phase == SolarPhase.Phase.DAWN || s.phase == SolarPhase.Phase.DUSK ? 0.5f
                : s.condition == WeatherCodes.Condition.FOG ? 0.7f : 0.16f;
        float hz = HORIZON * h;
        for (int layer = 0; layer < 2; layer++) {
            float y0 = hz - h * (0.10f + 0.04f * layer);
            mistTop[layer] = y0;
            mistShader[layer] = new LinearGradient(0, y0, 0, hz,
                    (mistColor & 0x00FFFFFF) | ((int) (255 * strength) << 24), mistColor & 0x00FFFFFF,
                    Shader.TileMode.CLAMP);
        }
    }

    private static int cloudsFor(WeatherCodes.Condition c) {
        switch (c) {
            case CLEAR: return 1;
            case PARTLY: return 4;
            case FOG: return 3;
            case SNOW: case STORM: case OVERCAST: return MAX_CLOUDS;
            default: return 7;
        }
    }

    private static float starsFor(HomeState s) {
        float a = s.phase == SolarPhase.Phase.NIGHT ? 1f : s.phase == SolarPhase.Phase.DAY ? 0f : 0.25f;
        switch (s.condition) {
            case CLEAR: return a;
            case PARTLY: return a * 0.55f;
            default: return 0f;
        }
    }

    /** Palette de la phase, puis grisee et assombrie selon la meteo. */
    private void pickColors(HomeState s) {
        int top, mid, hor, base, water;
        switch (s.phase) {
            case DAWN:
                top = 0xFF27385F; mid = 0xFF9A6C86; hor = 0xFFF2A878; base = 0xFF4A4A6A; water = 0xFF5C5F82; break;
            case DAY:
                top = 0xFF2F7FD0; mid = 0xFF6FB2EA; hor = 0xFFBFE2F5; base = 0xFF5A7690; water = 0xFF3D78A8; break;
            case DUSK:
                top = 0xFF2A2F5E; mid = 0xFFB2506A; hor = 0xFFF58F5C; base = 0xFF3C3050; water = 0xFF5A4468; break;
            default:
                top = 0xFF050914; mid = 0xFF0B1530; hor = 0xFF172447; base = 0xFF0C1424; water = 0xFF0A1428; break;
        }
        if (s.phase == SolarPhase.Phase.NIGHT && s.blackNight) {
            top = 0xFF000000; mid = 0xFF02040A; hor = 0xFF070C18; base = 0xFF030509; water = 0xFF02040A;
        }
        float gray = 0f;
        float dark = 1f;
        switch (s.condition) {
            case PARTLY: gray = 0.15f; break;
            case OVERCAST: gray = 0.55f; dark = 0.9f; break;
            case FOG: gray = 0.65f; dark = 1.05f; break;
            case DRIZZLE: gray = 0.55f; dark = 0.85f; break;
            case RAIN: gray = 0.7f; dark = 0.75f; break;
            case SNOW: gray = 0.5f; dark = 1.0f; break;
            case STORM: gray = 0.8f; dark = 0.6f; break;
            default: break;
        }
        skyTop = weather(top, gray, dark);
        skyMid = weather(mid, gray, dark);
        skyHorizon = weather(hor, gray * 0.8f, dark);
        waterColor = weather(water, gray * 0.6f, dark);
        int mountain = weather(base, gray * 0.5f, dark);
        layerColor[0] = Sky.lerpColor(skyHorizon, mountain, 0.30f);
        layerColor[1] = Sky.lerpColor(skyHorizon, mountain, 0.50f);
        layerColor[2] = Sky.lerpColor(skyHorizon, mountain, 0.75f);
        layerColor[3] = Sky.lerpColor(mountain, 0xFF000000, 0.35f);
        mistColor = skyHorizon;
        // Nuages : blancs de jour, couleur chaude de la lumiere a l'aube et au crepuscule, sombres la nuit.
        switch (s.phase) {
            case DAWN: cloudColor = 0xFFF7C3A2; break;
            case DUSK: cloudColor = 0xFFF6A27C; break;
            case DAY: cloudColor = 0xFFFFFFFF; break;
            default: cloudColor = s.blackNight ? 0xFF0A0F1A : 0xFF2A3350; break;
        }
        cloudColor = weather(cloudColor, gray * 0.7f, dark);
    }

    private static int weather(int color, float gray, float dark) {
        if (gray > 0f) {
            int g = Math.round(255 * TextContrast.luma(color));
            color = Sky.lerpColor(color, 0xFF000000 | (g << 16) | (g << 8) | g, gray);
        }
        if (dark < 1f) return Sky.lerpColor(color, 0xFF000000, 1f - dark);
        if (dark > 1f) return Sky.lerpColor(color, 0xFFFFFFFF, dark - 1f);
        return color;
    }

    /** Quatre lignes de crete, la troisieme portant le Cervin au centre. Courbes figees, graine fixe. */
    private void buildRidges(int w, int h) {
        float hz = HORIZON * h;
        float[] heights = {0.17f, 0.20f, 0.14f, 0.06f};
        float[] roughness = {0.5f, 0.7f, 0.9f, 0.5f};
        for (int l = 0; l < LAYERS; l++) {
            Random r = new Random(100 + l);
            float p1 = r.nextFloat() * 6.28f, p2 = r.nextFloat() * 6.28f, p3 = r.nextFloat() * 6.28f;
            float f1 = 2.2f + l * 0.7f, f2 = 5.1f + l, f3 = 11f + l * 2;
            Path path = ridges[l];
            path.reset();
            path.moveTo(0, hz);
            float top = hz;
            for (int i = 0; i < RIDGE_POINTS; i++) {
                float x = i / (float) (RIDGE_POINTS - 1);
                float e = 0.5f + 0.3f * (float) Math.sin(x * f1 + p1) + 0.2f * (float) Math.sin(x * f2 + p2)
                        + 0.1f * roughness[l] * (float) Math.sin(x * f3 + p3);
                float height = heights[l] * h * e;
                if (l == 1) height += peak(x) * 0.34f * h;   // le Cervin, plan 2, au centre
                float y = hz - height;
                top = Math.min(top, y);
                ridgeY[l][i] = y;
                path.lineTo(x * w, y);
            }
            path.lineTo(w, hz);
            path.close();
            ridgeTop[l] = top;
        }
    }

    /** Pyramide un peu concave, de 0 a 1, centree en 0,5. */
    private static float peak(float x) {
        float d = Math.abs(x - 0.5f) / 0.16f;
        return d >= 1f ? 0f : (float) Math.pow(1f - d, 1.25);
    }

    // ---- dessin --------------------------------------------------------------------------------------------

    @Override
    public void draw(Canvas c, int w, int h, Scene scene) {
        HomeState s = scene.state;
        rebuildIfNeeded(s, w, h);
        float t = s.motion ? scene.timeMs / 1000f : 0f;
        float hz = HORIZON * h;

        paint.setShader(skyShader);
        c.drawRect(0, 0, w, hz, paint);
        paint.setShader(null);

        drawStars(c, w, h);
        drawSun(c, w, h, s);
        drawMoon(c, w, h, s);
        drawClouds(c, w, h, t, 0, cloudCount / 2);

        for (int l = 0; l < LAYERS - 1; l++) {
            float shift = s.motion ? (float) Math.sin(t * 0.05f + l) * (2f + 3f * l) : 0f;
            fill.setColor(layerColor[l]);
            c.save();
            c.translate(shift, 0);
            c.drawPath(ridges[l], fill);
            c.restore();
            if (l < 2) drawMist(c, w, h, l);
            if (l == 1) drawClouds(c, w, h, t, cloudCount / 2, cloudCount);
        }
        drawWater(c, w, h, t, s);
    }

    private void drawStars(Canvas c, int w, int h) {
        if (starAlpha <= 0f) return;
        fill.setColor(0xFFFFFFFF);
        for (int i = 0; i < STARS; i++) {
            fill.setAlpha((int) (starAlpha * 200 * (0.4f + 0.6f * starSize[i] / 2f)));
            c.drawCircle(starX[i] * w, starY[i] * h, starSize[i] * h / 1080f, fill);
        }
    }

    private void drawSun(Canvas c, int w, int h, HomeState s) {
        if (s.sunEl < -6) return;
        float x = bodyX(s.sunAz, w);
        float y = bodyY(s.sunEl, h);
        float veil = veil(s.condition);
        boolean low = s.sunEl < 12;
        int glow = low ? 0xFFFFB070 : 0xFFFFF2C8;
        float r = h * (low ? 0.55f : 0.38f);
        int a = (int) (200 * veil * Math.max(0.15f, Math.min(1f, (s.sunEl + 6) / 12f)));
        int color = (glow & 0x00FFFFFF) | (a << 24);
        if (sunShader == null || x != sunKeyX || y != sunKeyY || r != sunKeyR || color != sunKeyColor) {
            sunShader = new RadialGradient(x, y, r, new int[]{color, glow & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            sunKeyX = x;
            sunKeyY = y;
            sunKeyR = r;
            sunKeyColor = color;
        }
        paint.setShader(sunShader);
        c.drawCircle(x, y, r, paint);
        paint.setShader(null);
        if (veil > 0.5f && s.sunEl > -1) {
            fill.setColor(low ? 0xFFFFE2B8 : 0xFFFFFFF0);
            fill.setAlpha((int) (255 * veil));
            c.drawCircle(x, y, h * 0.022f, fill);
        }
    }

    private void drawMoon(Canvas c, int w, int h, HomeState s) {
        if (s.moonEl < -2 || s.phase == SolarPhase.Phase.DAY) return;
        float veil = veil(s.condition) * (s.phase == SolarPhase.Phase.NIGHT ? 1f : 0.45f);
        if (veil <= 0.1f) return;
        float x = bodyX(s.moonAz, w);
        float y = bodyY(s.moonEl, h);
        float r = h * 0.026f;
        fill.setColor(0xFFF3F1E4);
        fill.setAlpha((int) (235 * veil));
        c.drawCircle(x, y, r, fill);
        // Phase : un disque couleur de ciel decale cache la partie dans l'ombre.
        double lit = (1 - Math.cos(2 * Math.PI * s.moonPhase)) / 2;
        float shift = (float) (2 * r * lit) * (s.moonPhase < 0.5 ? -1 : 1);
        fill.setColor(skyMid);
        fill.setAlpha((int) (235 * veil));
        c.save();
        oval.set(x - r, y - r, x + r, y + r);
        c.clipRect(oval);
        c.drawCircle(x + shift, y, r, fill);
        c.restore();
    }

    /** Transparence laissee au soleil et a la lune par la meteo. */
    private static float veil(WeatherCodes.Condition c) {
        switch (c) {
            case CLEAR: return 1f;
            case PARTLY: return 0.8f;
            case OVERCAST: case SNOW: return 0.3f;
            case DRIZZLE: return 0.25f;
            default: return 0f;
        }
    }

    /** Azimut 90 a 270 (est a ouest par le sud) sur la largeur ; au-dela, hors cadre mais borne. */
    private static float bodyX(double az, int w) {
        double d = az - 180;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return (float) (w * (0.5 + Math.max(-0.7, Math.min(0.7, d / 130))));
    }

    private static float bodyY(double el, int h) {
        float hz = HORIZON * h;
        return (float) (hz - Math.max(-10, el) / 55.0 * hz * 0.82);
    }

    private void drawClouds(Canvas c, int w, int h, float t, int from, int to) {
        if (cloudCount == 0) return;
        boolean heavy = builtCondition != WeatherCodes.Condition.CLEAR && builtCondition != WeatherCodes.Condition.PARTLY;
        fill.setColor(cloudColor);
        for (int i = from; i < to; i++) {
            float x = ((cloudX[i] + cloudSpeed[i] * t) % 1.3f) * w - 0.15f * w;
            float y = cloudY[i] * h;
            float s = cloudScale[i] * h * 0.085f;
            fill.setAlpha(heavy ? 150 : 95);
            for (int k = 0; k < 5; k++) {
                float ox = (k - 2) * s * 0.9f;
                float oy = (k % 2 == 0 ? 0f : -0.22f) * s;
                float rx = s * (1.25f - Math.abs(k - 2) * 0.2f);
                oval.set(x + ox - rx, y + oy - s * 0.38f, x + ox + rx, y + oy + s * 0.38f);
                c.drawOval(oval, fill);
            }
        }
    }

    /** Brume entre les plans, surtout a l'aube et au crepuscule (formes preparees par {@link #buildMist}). */
    private void drawMist(Canvas c, int w, int h, int layer) {
        paint.setShader(mistShader[layer]);
        c.drawRect(0, mistTop[layer], w, HORIZON * h, paint);
        paint.setShader(null);
    }

    private void drawWater(Canvas c, int w, int h, float t, HomeState s) {
        float hz = HORIZON * h;
        c.save();
        c.clipRect(0, hz, w, h);
        c.scale(1f, -1f, 0f, hz);
        for (int l = 0; l < LAYERS - 1; l++) {
            fill.setColor(layerColor[l]);
            c.drawPath(ridges[l], fill);
        }
        c.restore();
        paint.setShader(waterShader);
        c.drawRect(0, hz, w, h, paint);
        paint.setShader(null);
        // Frissonnement tres lent : fines lignes claires qui glissent.
        fill.setColor(mistColor);
        for (int i = 0; i < 14; i++) {
            float k = i / 14f;
            float y = hz + (h - hz) * (0.04f + k * k * 0.9f);
            float x = ((i * 0.37f + t * 0.006f * (1 + k)) % 1.2f) * w - 0.1f * w;
            fill.setAlpha(s.motion ? (int) (22 + 14 * Math.sin(t * 0.4f + i)) : 24);
            c.drawRect(x, y, x + w * (0.08f + 0.12f * k), y + Math.max(1f, h / 540f), fill);
        }
    }

    // ---- premier plan --------------------------------------------------------------------------------------

    @Override
    public void drawForeground(Canvas c, int w, int h, Scene scene) {
        rebuildIfNeeded(scene.state, w, h);
        float hz = HORIZON * h;
        fill.setColor(layerColor[3]);
        // La rive : bande sombre irreguliere qui coupe le bas des chiffres sur toute la largeur.
        c.drawRect(0, hz - h * 0.008f, w, hz + h * 0.022f, fill);
        float step = w / 64f;
        for (int i = 0; i < 64; i++) {
            float bump = h * (0.012f + 0.03f * (float) Math.abs(Math.sin(i * 1.7f) * Math.cos(i * 0.6f)));
            c.drawRect(i * step, hz - h * 0.008f - bump, (i + 1) * step + 1, hz, fill);
        }
    }
}
