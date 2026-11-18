package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Le fond en calques, sur UNE seule photo (le Riffelsee au lever du soleil) : la photo etalonnee en continu selon la
 * hauteur du soleil et la meteo ({@link SceneGrade}), puis, decoupes par la crete (donc derriere les montagnes) le soleil,
 * la lune, les etoiles, les nuages et la brume ; l'heure vient ensuite, puis le premier plan (la rive et les montagnes)
 * avec le meme etalonnage. Deux bitmaps au plus en memoire, decodes hors du fil de l'interface, sur la largeur reelle de la
 * surface (jeu 1080p sur un ecran 1080p, UHD sur un ecran 4K), rendus quand l'accueil passe derriere une autre application.
 * Pack ou image illisible : {@link Listener#onUnavailable} et la vue repasse sur la scene dessinee.
 *
 * Aucune allocation dans {@link #draw}, {@link #drawForeground} et leurs aides : formes, tableaux de particules et
 * degrades sont prepares une fois. Les seuls objets recrees le sont dans {@link #prepare}, hors du dessin : le filtre de
 * couleur quand l'etalonnage change (rarement en regime etabli, a chaque image pendant un glissement de 2 s).
 */
final class PhotoBackdrop implements Backdrop {
    interface Listener {
        void onUnavailable();
    }

    private static final long FADE_IN_MS = 400;
    /** Budget de memoire du couple de bitmaps : photo RGB_565 (2 octets) et premier plan ARGB_8888 (4 octets). */
    static final long BUDGET_BYTES = 56L * 1024 * 1024;
    private static final int SURFACE_HD_MAX = 2200;
    private static final int STARS = 110, CLOUDS = 14, MIST = 9, SPARKS = 40;
    /** Ligne de ciel de la photo, en fraction de la hauteur, a partir de laquelle on place le soleil (sa position reelle). */
    private static final float SKY_HORIZON = 0.46f;

    private final Context context;
    private final WeatherPackManifest manifest;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Bitmap photo;
    private Bitmap foreground;
    private LoadState loadState = LoadState.NONE;
    private int generation;
    private long readyAt;

    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private final float[] matrix = new float[20];
    private final SceneGrade.Grade applied = new SceneGrade.Grade();
    private boolean filterSet;
    private final Path skyPath = new Path();
    private int pathW = -1, pathH = -1;

    // Degrades unitaires (centre a l'origine, rayon 1), places et agrandis par la matrice du canvas : aucune allocation.
    private final Shader softWhite = unit(1f, 1f, 1f);
    private final Shader softWarm = unit(1f, 0.74f, 0.52f);
    private final Shader softGray = unit(0.74f, 0.77f, 0.82f);
    private final Shader softNight = unit(0.14f, 0.17f, 0.28f);
    private final Shader softStorm = unit(0.24f, 0.26f, 0.31f);
    private final Paint cloudPaint = gradientPaint(new int[]{0xFF9EA6B3, 0x8C9EA6B3, 0x009EA6B3}, new float[]{0f, 0.55f, 1f});
    private final Paint hazePaint = gradientPaint(new int[]{0x00D1D9E0, 0x38D1D9E0, 0x4DD1D9E0, 0x00D1D9E0},
            new float[]{0f, 0.4f, 0.6f, 1f});
    private final Paint valleyPaint = valleyPaint();

    private static Paint valleyPaint() {
        Paint p = new Paint();
        p.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFD1D9E0, 0xD1D1D9E0, 0x6BD1D9E0, 0x1FD1D9E0, 0x00D1D9E0},
                new float[]{0f, 0.35f, 0.65f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        return p;
    }

    /** Un degrade vertical unitaire (0 a 1), mis a l'echelle par la matrice du canevas : sans allocation au dessin. */
    private static Paint gradientPaint(int[] colors, float[] pos) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0f, 0f, 0f, 1f, colors, pos, Shader.TileMode.CLAMP));
        return p;
    }

    private final float[] starX = new float[STARS], starY = new float[STARS], starS = new float[STARS], starP = new float[STARS];
    private final float[] cloudX = new float[CLOUDS], cloudY = new float[CLOUDS], cloudS = new float[CLOUDS],
            cloudV = new float[CLOUDS];
    private final float[] mistX = new float[MIST], mistY = new float[MIST], mistS = new float[MIST], mistV = new float[MIST];
    private final float[] sparkX = new float[SPARKS], sparkY = new float[SPARKS], sparkP = new float[SPARKS];
    private final android.graphics.Matrix tmpMatrix = new android.graphics.Matrix();

    /** Etat de chargement (sans rapport avec une phase : il n'y a plus qu'une photo). */
    private enum LoadState { NONE, LOADING, READY }

    PhotoBackdrop(Context context, WeatherPackManifest manifest, Listener listener) {
        this.context = context.getApplicationContext();
        this.manifest = manifest;
        this.listener = listener;
        Random r = new Random(5);
        for (int i = 0; i < STARS; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat() * 0.40f;
            starS[i] = 0.5f + r.nextFloat() * 1.5f;
            starP[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < CLOUDS; i++) {
            cloudX[i] = r.nextFloat() * 1.4f - 0.2f;
            cloudY[i] = 0.03f + r.nextFloat() * 0.34f;
            cloudS[i] = 0.6f + r.nextFloat() * 0.9f;
            cloudV[i] = 0.003f + r.nextFloat() * 0.006f;
        }
        for (int i = 0; i < MIST; i++) {
            mistX[i] = r.nextFloat() * 1.3f - 0.15f;
            mistY[i] = 0.40f + r.nextFloat() * 0.24f;
            mistS[i] = 0.7f + r.nextFloat() * 0.9f;
            mistV[i] = 0.004f + r.nextFloat() * 0.005f;
        }
        for (int i = 0; i < SPARKS; i++) {
            sparkX[i] = r.nextFloat();
            sparkY[i] = 0.64f + r.nextFloat() * 0.33f;
            sparkP[i] = r.nextFloat() * 6.28f;
        }
    }

    private static Shader unit(float r, float g, float b) {
        int rgb = (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        return new RadialGradient(0f, 0f, 1f, new int[]{0xFF000000 | rgb, 0x00000000 | rgb}, null, Shader.TileMode.CLAMP);
    }

    @Override
    public float waterLine() {
        return manifest.waterline;
    }

    @Override
    public int[] colorGrid() {
        return manifest.grid;
    }

    @Override
    public float[] crestCurve() {
        return manifest.crest;
    }

    @Override
    public boolean animated(Scene s) {
        return loadState != LoadState.READY || SystemClock.uptimeMillis() - readyAt < FADE_IN_MS || s.state.motion;
    }

    @Override
    public void release() {
        generation++;           // un decodage en cours sera ecarte a son arrivee
        loadState = LoadState.NONE;
        photo = null;
        foreground = null;
        filterSet = false;
        pathW = -1;
    }

    // ---- chargement ----------------------------------------------------------------------------------------

    /** Vrai si la surface est du 1080p (ou moins) : on lit alors le jeu 1920x1080 plutot que l'UHD. */
    static boolean useHd(int surfaceWidth) {
        return surfaceWidth <= SURFACE_HD_MAX;
    }

    /** Agrandit {@code sample} (puissance de deux) tant que le couple de bitmaps depasse le budget de memoire. */
    static int budgetSample(int width, int height, int sample) {
        int s = Math.max(1, sample);
        while (s < 16 && (long) (width / s) * (height / s) * 6 > BUDGET_BYTES) s *= 2;
        return s;
    }

    /** Plus grande puissance de deux qui garde l'image au moins aussi large que la surface. */
    static int sampleSize(int imageWidth, int surfaceWidth) {
        int s = 1;
        if (surfaceWidth <= 0) return s;
        while (imageWidth / (s * 2) >= surfaceWidth) s *= 2;
        return s;
    }

    private void ensureLoaded(int surfaceW) {
        if (loadState != LoadState.NONE) return;
        loadState = LoadState.LOADING;
        final int token = ++generation;
        Thread t = new Thread(() -> decode(surfaceW, token), "alpine-photo");
        t.setDaemon(true);
        t.start();
    }

    private void decode(int surfaceW, int token) {
        Bitmap bg = null;
        Bitmap fg = null;
        boolean hd = useHd(surfaceW);
        File file = WeatherPack.file(context);
        try (ZipFile zip = new ZipFile(file)) {
            String photoPath = WeatherPackManifest.file(true, hd);
            String fgPath = WeatherPackManifest.file(false, hd);
            int[] dims = bounds(zip, photoPath);
            int base = budgetSample(dims[0], dims[1], sampleSize(dims[0], surfaceW));
            // Manque de memoire : une seule nouvelle tentative a moitie de definition, puis on abandonne (scene dessinee).
            for (int attempt = 0; attempt < 2 && bg == null; attempt++) {
                int sample = base << attempt;
                try {
                    bg = read(zip, photoPath, sample, Bitmap.Config.RGB_565);
                    if (bg != null) fg = read(zip, fgPath, sample, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError oom) {
                    bg = null;
                    fg = null;
                }
            }
        } catch (Exception e) {
            bg = null;
            fg = null;
        }
        final Bitmap fb = bg;
        final Bitmap ff = fg;
        main.post(() -> {
            if (token != generation) return;   // accueil passe derriere : on jette le resultat
            if (fb == null) {
                loadState = LoadState.NONE;
                listener.onUnavailable();
                return;
            }
            photo = fb;
            foreground = ff;
            loadState = LoadState.READY;
            readyAt = SystemClock.uptimeMillis();
        });
    }

    private int[] bounds(ZipFile zip, String path) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new java.io.FileNotFoundException(path);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = zip.getInputStream(e)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new java.io.IOException(path);
        return new int[]{o.outWidth, o.outHeight};
    }

    private Bitmap read(ZipFile zip, String path, int sample, Bitmap.Config config) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = config;
        try (InputStream in = zip.getInputStream(e)) {
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    // ---- preparation (hors du dessin) ------------------------------------------------------------------------

    /**
     * Met a jour le filtre de couleur quand l'etalonnage a change. Appelee avant chaque image, jamais depuis le dessin :
     * c'est ici que les rares objets sont recrees (un filtre de couleur par changement sensible).
     */
    @Override
    public void prepare(Scene s) {
        if (filterSet && !s.grade.differs(applied, 0.0025f)) return;
        applied.copyFrom(s.grade);
        applied.toMatrix(matrix);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(matrix);
        photoPaint.setColorFilter(f);
        fgPaint.setColorFilter(f);
        filterSet = true;
    }

    /** Le ciel au-dessus de la crete : le soleil, les nuages et les etoiles y sont decoupes, donc derriere les montagnes. */
    private void buildSky(int w, int h) {
        if (pathW == w && pathH == h) return;
        pathW = w;
        pathH = h;
        float[] c = manifest.crest;
        skyPath.reset();
        skyPath.moveTo(0f, 0f);
        skyPath.lineTo(w, 0f);
        for (int i = c.length - 1; i >= 0; i--) {
            skyPath.lineTo(w * i / (float) (c.length - 1), c[i] * h);
        }
        skyPath.close();
    }

    // ---- dessin ----------------------------------------------------------------------------------------------

    private int fadeAlpha() {
        long k = SystemClock.uptimeMillis() - readyAt;
        return k >= FADE_IN_MS ? 255 : (int) (255 * Math.max(0, k) / FADE_IN_MS);
    }

    @Override
    public void draw(Canvas c, int w, int h, Scene s) {
        ensureLoaded(w);
        if (loadState != LoadState.READY || photo == null) {
            c.drawColor(0xFF000000);   // chargement (au retour sur Home) : on s'efface depuis le noir, 0,4 s
            return;
        }
        buildSky(w, h);
        photoPaint.setAlpha(fadeAlpha());
        dst.set(0, 0, w, h);
        c.drawBitmap(photo, null, dst, photoPaint);
        SceneMix m = s.mix;
        float t = s.timeMs / 1000f;
        c.save();
        c.clipPath(skyPath);
        drawStars(c, w, h, m, t);
        drawMoon(c, w, h, s, t);
        drawSun(c, w, h, s);
        drawClouds(c, w, h, m, t);
        c.restore();
    }

    private void drawStars(Canvas c, int w, int h, SceneMix m, float t) {
        float a = SceneGrade.starAlpha(m.elevation, m);
        if (a <= 0.01f) return;
        fill.setColor(0xFFFFFFFF);
        for (int i = 0; i < STARS; i++) {
            float tw = 0.65f + 0.35f * (float) Math.sin(t * 0.8f + starP[i]);
            fill.setAlpha((int) (a * tw * 215f));
            c.drawCircle(starX[i] * w, starY[i] * h, starS[i] * h / 1080f, fill);
        }
    }

    private void drawMoon(Canvas c, int w, int h, Scene s, float t) {
        SceneMix m = s.mix;
        float vis = SceneGrade.smoothstep(-1f, 5f, m.moonElevation) * (1f - SceneGrade.smoothstep(-2f, 8f, m.elevation))
                * (1f - m.cloud) * (1f - m.rain) * (1f - m.snow) * (1f - m.storm) * (1f - 0.8f * m.fog);
        if (vis <= 0.02f) return;
        double lit = (1.0 - Math.cos(2 * Math.PI * s.state.moonPhase)) / 2.0;
        float a = vis * (0.30f + 0.70f * (float) lit);   // plus la lune est pleine, plus elle brille
        float x = bodyX(m.moonAzimuth, w), y = Math.min(0.09f * h, bodyY(m.moonElevation, h));   // la lune reste au-dessus de la salutation, jamais derriere un texte
        unitCircle(c, softWhite, x, y, h * 0.09f, 0.35f * a);
        fill.setColor(0xFFF3F1E4);
        fill.setAlpha((int) (235 * a));
        c.drawCircle(x, y, h * 0.024f, fill);
    }

    private void drawSun(Canvas c, int w, int h, Scene s) {
        SceneMix m = s.mix;
        float strength = SceneGrade.sunStrength(m.elevation, m);
        float pale = SceneGrade.sunDiscPale(m.elevation, m);
        if (strength <= 0.01f && pale <= 0.01f) return;
        float x = bodyX(m.azimuth, w), y = bodyY(m.elevation, h);
        float hi = SceneGrade.smoothstep(4f, 25f, m.elevation);   // chaud au ras de l'horizon, blanc en hauteur
        float glow = SceneGrade.SUN_GLOW_ALPHA * strength;
        unitCircle(c, softWarm, x, y, h * 0.62f, glow * (1f - hi));
        unitCircle(c, softWhite, x, y, h * 0.50f, glow * hi);
        if (strength > 0.05f && m.elevation < 14f) {   // haut dans le ciel, le disque ne se voit pas : seul reste le halo
            fill.setColor(hi > 0.5f ? 0xFFFFFFF0 : 0xFFFFE2B8);
            fill.setAlpha((int) (255 * Math.min(1f, strength * 1.2f)));
            c.drawCircle(x, y, h * 0.020f, fill);
        }
        if (pale > 0.01f) unitCircle(c, softWhite, x, y, h * 0.045f, 0.55f * pale);   // disque pale dans le brouillard
    }

    private static float bodyX(float az, int w) {
        float d = SceneMix.angle(az - 180f);
        return w * (0.5f + Math.max(-0.7f, Math.min(0.7f, d / 130f)));
    }

    private static float bodyY(float el, int h) {
        float hz = SKY_HORIZON * h;
        return hz - Math.max(-10f, el) / 55f * hz * 0.85f;
    }

    /** Un degrade unitaire place en (x, y) avec le rayon r et l'opacite a : sans allocation. */
    private void unitCircle(Canvas c, Shader sh, float x, float y, float r, float a) {
        if (a <= 0.004f) return;
        soft.setShader(sh);
        soft.setAlpha((int) (255 * Math.min(1f, a)));
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        c.drawCircle(0f, 0f, 1f, soft);
        c.restore();
        soft.setShader(null);
    }

    /**
     * Couvert : pas de formes de nuages (elles ressemblent a des taches posees sur la photo), seulement un voile gris froid
     * progressif, plus dense en haut du ciel, qui s'efface avant la crete. La luminosite vient surtout de l'etalonnage.
     */
    private void drawClouds(Canvas c, int w, int h, SceneMix m, float t) {
        float cover = SceneGrade.skyCover(m);
        if (cover <= 0.01f) return;
        float a = SceneGrade.CLOUD_ALPHA * cover * SceneGrade.dayFactor(m.elevation)   // sombre la nuit, comme dans le modele
                * (1f + 0.06f * (float) Math.sin(t * 0.11f) + 0.04f * (float) Math.sin(t * 0.043f + 2f));   // respire tres lentement
        cloudPaint.setAlpha((int) (255 * Math.min(1f, a)));
        c.save();
        c.scale(w, h * 0.5f);
        c.drawRect(0f, 0f, 1f, 1f, cloudPaint);
        c.restore();
    }

    private void unitSquash(Canvas c, Shader sh, float x, float y, float r, float a) {
        if (a <= 0.004f) return;
        soft.setShader(sh);
        soft.setAlpha((int) (255 * Math.min(1f, a)));
        c.save();
        c.translate(x, y);
        c.scale(r * 1.7f, r * 0.62f);
        c.drawCircle(0f, 0f, 1f, soft);
        c.restore();
        soft.setShader(null);
    }

    /**
     * Atmosphere : brouillard, voile de la pluie et de la neige ({@link SceneGrade#haze}). Elle suit le relief et la
     * profondeur de la photo : epaisse dans la vallee du fond (autour du Cervin et des cretes lointaines), mince sur le
     * rocher proche a gauche, sur le haut du ciel et au bas du lac ; elle respire tres lentement, sans forme ni bord.
     */
    private void drawHaze(Canvas c, int w, int h, SceneMix m, float t) {
        float dawn = 0.18f * (1f - SceneGrade.smoothstep(-2f, 14f, m.elevation)) * SceneGrade.smoothstep(-14f, -4f, m.elevation);
        float amount = Math.max(SceneGrade.haze(m), dawn) * SceneGrade.dayFactor(m.elevation);
        if (amount <= 0.01f) return;
        float a = Math.min(1f, amount * (1f + 0.07f * (float) Math.sin(t * 0.157f) + 0.05f * (float) Math.sin(t * 0.048f + 1.7f)));
        valleyPaint.setAlpha((int) (255 * a * 0.60f));
        c.save();
        c.translate(w * 0.60f, h * 0.50f);
        c.scale(w * 0.78f, h * 0.40f);
        c.drawCircle(0f, 0f, 1f, valleyPaint);
        c.restore();
        hazePaint.setAlpha((int) (255 * a));
        c.save();
        c.translate(0f, h * 0.30f);
        c.scale(w, h * 0.48f);
        c.drawRect(0f, 0f, 1f, 1f, hazePaint);
        c.restore();
    }


    @Override
    public void drawUnderClock(Canvas c, int w, int h, Scene s) {
        // Rien : le premier plan et le plan de lisibilite (voile local) s'en chargent.
    }

    @Override
    public void drawForeground(Canvas c, int w, int h, Scene s) {
        if (loadState != LoadState.READY || foreground == null) return;
        fgPaint.setAlpha(fadeAlpha());
        dst.set(0, 0, w, h);
        c.drawBitmap(foreground, null, dst, fgPaint);
    }

    @Override
    public void drawAtmosphere(Canvas c, int w, int h, Scene s) {
        if (loadState != LoadState.READY || photo == null) return;
        drawHaze(c, w, h, s.mix, s.timeMs / 1000f);
    }
}
