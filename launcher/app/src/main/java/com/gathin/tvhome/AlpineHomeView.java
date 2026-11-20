package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * La vue unique de l'accueil alpin : elle dessine, de bas en haut, le fond ({@link Backdrop}), les voiles de lisibilite,
 * l'heure et la date, le premier plan (la rive, qui coupe le bas des chiffres), la meteo de premier plan et un voile sous
 * le dock. Le dock, les pilules et les rangees restent des vues Android au-dessus, pour garder le focus D-pad natif.
 *
 * La lumiere est continue : un {@link SceneMix} lisse la hauteur du soleil et chaque effet meteo vers la cible calculee a
 * partir de l'heure reelle et de la meteo ; un changement de moment ou de meteo glisse donc en deux secondes, sans image
 * noire, sans coupure, et une cible qui change pendant le glissement redirige simplement la valeur. Au retour au
 * premier plan, la scene est posee directement sur l'etat de l'heure courante.
 *
 * La lisibilite est garantie par zone (heure, date, salutation, pilules, titre du dock) : {@link SceneZones} mesure le
 * pire fond derriere chacune et {@link ContrastPlan} choisit la couleur du texte et, si besoin, un voile local borne.
 *
 * Cycle de vie : {@link #start} au retour sur Home, {@link #stop} des que Home passe derriere une autre application.
 * Entre les deux, rien ne tourne : ni image, ni minuteur, ni appel reseau, et les bitmaps du fond sont rendus.
 */
public final class AlpineHomeView extends View {
    /** Plafond de cadence des animations : 30 images par seconde. */
    static final long FRAME_MS = 33;
    /** Cadence maximale du recalcul des plans de lisibilite pendant un glissement. */
    private static final long PLAN_MS = 100;
    private static final int ZONES = SceneZones.Zone.values().length;
    private static final int WHITE = 0xFFFFFFFF, DARK = 0xFF0B1620;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlay = new Paint();
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WeatherLayer weatherLayer = new WeatherLayer();
    private final WeatherRepository repository;
    private AlpineSettings settings;
    private Backdrop backdrop = new DrawnAlpineBackdrop();
    private String clockFormat = "system";
    private float density = 1f;

    private final Backdrop.Scene scene = new Backdrop.Scene();
    private final float[] gradeTmp = new float[10];
    private long lastFrame;
    private long frozenMs = 40_000L;
    private WeatherData weather;
    private boolean weatherStale;
    private boolean started;
    private boolean timeValid = true;
    private boolean dream;

    // Apercu : adb shell am start ... --es phase dusk --es weather rain --es clock 20:52
    private String previewPhase;
    private WeatherCodes.Condition previewCondition;
    private int previewHour = -1;
    private int previewMinute;

    // Lisibilite : un plan par zone, vers lequel la couleur du texte et le voile glissent.
    private final float[] tone = new float[ZONES], toneTarget = new float[ZONES];
    private final float[] veil = new float[ZONES], veilTarget = new float[ZONES];
    private final float[] planOut = new float[2], planTmp = new float[6];
    private float fadeTop, capBottom;
    private final Paint fadePaint = fadePaint();
    private long lastPlan;
    private boolean planDirty = true;
    // Voile de lisibilite : une grande ellipse sans bord perceptible (comme un filtre degrade de photographe), jamais une plaque.
    // Le rectangle du texte tient dans son plateau. Degrade unitaire mis a l'echelle par la matrice : aucune allocation au dessin.
    private static final float[] VEIL_STOPS = {0f, 0.42f, 0.55f, 0.68f, 0.80f, 0.90f, 1f};
    private static final int[] VEIL_ALPHA = {0xFF, 0xF7, 0xD6, 0x94, 0x4D, 0x1A, 0x00};
    private final Shader[] veilShader = {veilShader(0x0A1220), veilShader(0xFFFFFF)};

    private static Shader veilShader(int rgb) {
        int[] colors = new int[VEIL_ALPHA.length];
        for (int i = 0; i < colors.length; i++) colors[i] = (VEIL_ALPHA[i] << 24) | rgb;
        return new RadialGradient(0f, 0f, 1f, colors, VEIL_STOPS, Shader.TileMode.CLAMP);
    }

    private Shader scrim;
    private int scrimH = -1;
    private HomeState textState;
    private int textW = -1;
    private String timeString = "";
    private String dateString = "";
    private String greetingString = "";
    private float textSize = -1f;
    private int sizeW = -1;
    private float clockLeft, clockRight, baseline, digitH, dateY, greetY, dateSize, greetSize;

    public AlpineHomeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        repository = new WeatherRepository(context.getApplicationContext());
        density = context.getResources().getDisplayMetrics().density;
        weatherLayer.setDensity(density);
        if (!isInEditMode()) {
            Typeface face = UiFont.get(context, R.string.font_extralight);
            text.setTypeface(face);
        }
        dot.setStyle(Paint.Style.STROKE);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        settings = AlpineSettings.load(context);
        for (int i = 0; i < ZONES; i++) toneTarget[i] = 0f;
    }

    /** Full-screen et dessinee en une passe : evite le calque hors ecran a chaque image. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Applique les reglages et le fond choisi ; appele a la creation de Home (qui se reconstruit si un reglage change). */
    void configure(AlpineSettings s, String clockFormat, Backdrop backdrop) {
        this.settings = s;
        this.clockFormat = clockFormat;
        this.backdrop.release();
        this.backdrop = backdrop;
        scene.state = null;
        setPreview(s.previewPhase, s.previewCondition, null);
    }

    /**
     * Mode screensaver ({@link AlpineDream}) : ni dock ni pilules, donc ni voile sous le dock ni plan de lisibilite pour
     * leurs zones. L'heure, la date, la photo et la meteo restent identiques a l'accueil.
     */
    void setDream(boolean dream) {
        this.dream = dream;
        planDirty = true;
    }

    /** Change de fond sans reconstruire Home (pack valide a l'instant, ou image illisible : retour a la scene dessinee). */
    void useBackdrop(Backdrop next) {
        backdrop.release();
        backdrop = next;
        sizeW = -1;
        textState = null;
        planDirty = true;
        scheduleFrame();
    }

    /** Apercu : fige la phase, la condition ou l'heure affichees (null et -1 pour les valeurs reelles). */
    void setPreview(@Nullable String phase, @Nullable String condition, @Nullable String clock) {
        previewPhase = phase != null && SolarPhase.previewSky(phase) != null ? phase.trim() : null;
        previewCondition = WeatherCodes.parse(condition);
        previewHour = -1;
        if (clock != null && clock.matches("\\d{1,2}:\\d{2}")) {
            String[] hm = clock.split(":");
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (h < 24 && m < 60) {
                previewHour = h;
                previewMinute = m;
            }
        }
        if (started) refreshState(false);
    }

    // ---- cycle de vie --------------------------------------------------------------------------------------

    void start() {
        started = true;
        lastFrame = 0;
        refreshState(true);   // la scene est posee directement sur l'heure courante : aucune transition perimee
        fetchWeather();
        scheduleMinute();
        scheduleFrame();
    }

    void stop() {
        started = false;
        removeCallbacks(minuteTick);
        removeCallbacks(frame);
        repository.cancel();
        backdrop.release();
        scene.mix.initialized = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private final Runnable minuteTick = this::onMinute;

    private void onMinute() {
        if (!started) return;
        refreshState(false);
        fetchWeather();
        scheduleMinute();
    }

    /** Le depot ne rappelle le reseau que si la derniere meteo a plus de 30 minutes. */
    private void fetchWeather() {
        repository.refreshIfStale(settings.lat, settings.lon, (data, stale) -> {
            weather = data;
            weatherStale = stale;
            if (started) refreshState(false);
        });
    }

    private void scheduleMinute() {
        removeCallbacks(minuteTick);
        long now = System.currentTimeMillis();
        postDelayed(minuteTick, 60_000 - now % 60_000 + 50);
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            advance();
            invalidate();
            if (needsFrames()) postDelayed(this, FRAME_MS);
        }
    };

    private boolean needsFrames() {
        HomeState s = scene.state;
        if (s == null) return false;
        return scene.mix.settling() || backdrop.animated(scene) || (s.motion && WeatherLayer.active(scene.mix))
                || tonesSettling();
    }

    private void scheduleFrame() {
        removeCallbacks(frame);
        if (!started) return;
        frame.run();   // une image tout de suite, puis a la cadence tant que la scene bouge
    }

    // ---- etat ----------------------------------------------------------------------------------------------

    /** Reconstruit l'etat depuis l'heure, les reglages et la meteo ; {@code initial} : pose la scene sans glissement. */
    private void refreshState(boolean initial) {
        ZonedDateTime now = ZonedDateTime.now(settings.zone);
        LocalDate date = now.toLocalDate();
        int minute = now.getHour() * 60 + now.getMinute();
        int offset = now.getOffset().getTotalSeconds() / 60;
        int[] riseSet = SolarPhase.riseAndSet(date, settings.lat, settings.lon, settings.zone);
        WeatherCodes.Condition cond = previewCondition != null ? previewCondition
                : weather != null ? weather.condition : WeatherCodes.Condition.CLEAR;
        double temp = weather != null ? weather.tempC : Double.NaN;
        boolean located = !Double.isNaN(settings.lat) && !Double.isNaN(settings.lon);
        double[] sun, moon;
        float evening = minute >= 12 * 60 ? 1f : 0f;
        SolarPhase.Phase phase;
        float[] sky = SolarPhase.previewSky(previewPhase);
        if (sky != null) {
            sun = new double[]{sky[1], sky[0]};
            moon = new double[]{200, sky[0] < -8 ? 35 : -20};
            evening = sky[2];
            phase = SolarPhase.phaseOf(sky[0], sky[2] > 0.5f);
        } else if (located) {
            sun = SolarPhase.sunPosition(date, minute, settings.lat, settings.lon, offset);
            moon = SolarPhase.moonPosition(date, minute, settings.lat, settings.lon, offset);
            phase = SolarPhase.at(minute, riseSet);
        } else {
            phase = SolarPhase.at(minute, riseSet);   // repli : phases fixes, hauteur de soleil nominale
            float[] nominal = SolarPhase.previewSky(phase == SolarPhase.Phase.DAWN ? "dawn" : phase == SolarPhase.Phase.DAY
                    ? "day" : phase == SolarPhase.Phase.DUSK ? "dusk" : "night");
            sun = new double[]{nominal[1], nominal[0]};
            moon = new double[]{200, nominal[0] < -8 ? 35 : -20};
        }
        timeValid = previewHour >= 0 || now.getYear() >= 2025;   // heure de la TV pas encore reglee : pas d'heure fausse
        int h = previewHour >= 0 ? previewHour : now.getHour();
        int m = previewHour >= 0 ? previewMinute : now.getMinute();
        scene.state = new HomeState(phase, cond, temp, settings.fahrenheit, date, h, m,
                ClockFormat.is24(getContext(), clockFormat), settings.motion, settings.blackNight,
                weather != null && weatherStale && previewCondition == null, sun, moon,
                SolarPhase.moonPhase(date, minute, offset));
        SceneMix mix = scene.mix;
        mix.targetWeather(cond, previewCondition != null ? null : weather);   // les observations ne pilotent pas un apercu force
        mix.targetSky((float) sun[1], (float) sun[0], (float) moon[1], (float) moon[0], evening);
        if (initial || !settings.motion) mix.snap();
        planDirty = true;
        scheduleFrame();
    }

    /** Une image : fait avancer le melange lisse, l'etalonnage, les plans de lisibilite (hors du dessin). */
    private void advance() {
        HomeState s = scene.state;
        if (s == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        scene.timeMs = s.motion ? now : frozenMs;
        if (s.motion) scene.mix.step(dt);
        else scene.mix.snap();
        SceneGrade.compute(scene.mix.elevation, scene.mix.evening, scene.mix, scene.grade, gradeTmp);
        backdrop.prepare(scene);
        if (planDirty || (scene.mix.settling() && now - lastPlan >= PLAN_MS)) {
            planDirty = false;
            lastPlan = now;
            updatePlans();
        }
        float k = 1f - (float) Math.exp(-dt / 0.35f);
        for (int i = 0; i < ZONES; i++) {
            tone[i] += (toneTarget[i] - tone[i]) * k;
            veil[i] += (veilTarget[i] - veil[i]) * k;
        }
        if (dt == 0f) {                       // premiere image : aucun glissement depuis un etat inconnu
            System.arraycopy(toneTarget, 0, tone, 0, ZONES);
            System.arraycopy(veilTarget, 0, veil, 0, ZONES);
        }
    }

    private boolean tonesSettling() {
        for (int i = 0; i < ZONES; i++) {
            if (Math.abs(tone[i] - toneTarget[i]) > 0.01f || Math.abs(veil[i] - veilTarget[i]) > 0.004f) return true;
        }
        return false;
    }

    /** Recalcule, pour chaque zone, la couleur du texte et le voile qui garantissent le contraste. */
    private void updatePlans() {
        int w = getWidth(), h = getHeight();
        int[] grid = backdrop.colorGrid();
        float[] crest = backdrop.crestCurve();
        if (w == 0 || h == 0 || grid == null || crest == null) return;
        for (SceneZones.Zone z : SceneZones.Zone.values()) {
            if (dream && SceneZones.whiteFixed(z)) {   // pilules et titre du dock : absents du screensaver
                toneTarget[z.ordinal()] = 0f;
                veilTarget[z.ordinal()] = 0f;
                continue;
            }
            ContrastPlan.Plan p = SceneZones.plan(z, w, h, grid, crest, scene.grade, scene.mix, planOut, planTmp);
            toneTarget[z.ordinal()] = SceneZones.whiteFixed(z) || !p.darkText ? 0f : 1f;
            veilTarget[z.ordinal()] = p.veilAlpha;
        }
    }

    // ---- dessin --------------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        HomeState s = scene.state;
        int w = getWidth();
        int h = getHeight();
        if (s == null || w == 0 || h == 0) return;
        layoutClock(w, h);
        backdrop.draw(c, w, h, scene);
        backdrop.drawUnderClock(c, w, h, scene);
        backdrop.drawForeground(c, w, h, scene);
        backdrop.drawAtmosphere(c, w, h, scene);       // sous les voiles : ils recouvrent photo et premier plan sans couture
        drawVeils(c, w, h, false);
        if (timeValid) {
            // L'heure vit dans un calque : son bas s'efface dans le fondu de la rive (ce que fait le bord du premier plan),
            // sans redessiner photo ni voiles par-dessus, donc sans couture.
            int layer = c.saveLayer(0f, 0f, w, capBottom, null);
            drawClock(c, w, h, s);
            c.drawRect(0f, fadeTop, w, capBottom, fadePaint);
            c.restoreToCount(layer);
        }
        drawVeils(c, w, h, true);          // titre du dock et pilules : des vues posees au-dessus du premier plan
        weatherLayer.draw(c, w, h, scene);
        if (!dream) drawScrim(c, w, h);
    }

    private static Paint fadePaint() {
        Paint p = new Paint();
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        return p;
    }

    /** Voiles locaux, bornes et fondus sur leurs bords, derriere le texte (et derriere les pilules, qui sont des vues). */
    private void drawVeils(Canvas c, int w, int h, boolean front) {
        for (SceneZones.Zone z : SceneZones.Zone.values()) {
            if (SceneZones.whiteFixed(z) != front) continue;
            float a = veil[z.ordinal()];
            if (a <= 0.004f) continue;
            float[] r = SceneZones.rect(z, w, h);
            int k = !SceneZones.whiteFixed(z) && toneTarget[z.ordinal()] > 0.5f ? 1 : 0;   // voile clair sous un texte sombre
            veilPaint.setShader(veilShader[k]);
            veilPaint.setAlpha((int) (255 * Math.min(ContrastPlan.MAX_VEIL, a)));
            c.save();
            c.translate((r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f);
            c.scale((r[2] - r[0]) / 2f * 2.3f + 70f * density, (r[3] - r[1]) / 2f * 2.3f + 45f * density);
            c.drawCircle(0f, 0f, 1f, veilPaint);
            c.restore();
        }
        veilPaint.setShader(null);
    }

    /** Taille et position de l'heure, de la date et de la salutation : la geometrie de la mise en page B (AlpineMetrics). */
    private void layoutClock(int w, int h) {
        if (sizeW == w && textSize > 0) return;
        sizeW = w;
        text.setTextSize(100f);
        text.setLetterSpacing(0f);
        float measured = text.measureText("00:00");
        textSize = 100f * AlpineMetrics.CLOCK_WIDTH * w / Math.max(1f, measured);
        float[] sizes = AlpineMetrics.clockSizes(w, h);
        digitH = textSize * AlpineMetrics.DIGIT_RATIO;
        baseline = sizes[2];
        clockLeft = w * (0.5f - AlpineMetrics.CLOCK_WIDTH / 2f);
        clockRight = w * (0.5f + AlpineMetrics.CLOCK_WIDTH / 2f);
        float[] lines = AlpineMetrics.dateLines(w, h);
        dateY = lines[0];
        greetY = lines[1];
        dateSize = lines[2];
        greetSize = lines[3];
        float cap = baseline - digitH * AlpineMetrics.OCCLUSION;
        fadeTop = cap - digitH * AlpineMetrics.SOFT;
        capBottom = Math.min(h, baseline + digitH * 0.1f);
        fadePaint.setShader(new LinearGradient(0f, fadeTop, 0f, cap, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        weatherLayer.setNoGo(clockLeft, baseline - digitH - 0.1f * h, clockRight, baseline);
        if (scrimH != h) {
            scrimH = h;
            scrim = new LinearGradient(0, h * 0.62f, 0, h, 0x00000000, 0x80000000, Shader.TileMode.CLAMP);
        }
        planDirty = true;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void drawClock(Canvas c, int w, int h, HomeState s) {
        // Textes prepares une fois par etat : rien n'est alloue pendant les images d'animation.
        if (textState != s || textW != w) {
            textState = s;
            textW = w;
            timeString = s.timeText();
            dateString = dateLine(s);
            // Un apercu force sur une phase (sans heure) garde la salutation de cette phase ; sinon l'heure decide.
            greetingString = greeting(previewPhase != null && previewHour < 0 ? s.phase : HomeState.greetingPhase(s.hour));
        }
        // Ecrans OLED : l'heure se decale de quelques pixels chaque minute, pour ne pas marquer la dalle.
        float dx = ((s.minute % 5) - 2) * 2f * density;
        float dy = (((s.minute / 5) % 3) - 1) * 2f * density;
        float cx = w / 2f + dx;

        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0f);
        text.setTextSize(textSize);
        drawLine(c, SceneZones.Zone.CLOCK, timeString, cx, baseline + dy, textSize, 255, 8f, 2f);

        text.setLetterSpacing(0.3f);
        drawLine(c, SceneZones.Zone.DATE, dateString, cx, dateY + dy, dateSize, 235, 4f, 1f);
        if (s.stale) {
            // Indicateur discret : la meteo affichee vient du cache, sans reseau.
            float half = text.measureText(dateString) / 2f;
            dot.setColor(mix(WHITE, DARK, tone[SceneZones.Zone.DATE.ordinal()]));
            dot.setAlpha(170);
            dot.setStrokeWidth(Math.max(1f, density));
            c.drawCircle(cx + half + dateSize * 0.9f, dateY + dy - dateSize * 0.32f, dateSize * 0.22f, dot);
        }
        drawLine(c, SceneZones.Zone.GREETING, greetingString, cx, greetY + dy, greetSize, 165, 4f, 1f);
        text.setShadowLayer(0, 0, 0, 0);
    }

    /** Une ligne de texte avec la couleur et le halo de sa zone, qui glissent entre blanc et sombre (jamais un saut). */
    private void drawLine(Canvas c, SceneZones.Zone z, String t, float x, float y, float size, int alpha, float blur, float off) {
        float k = tone[z.ordinal()];
        text.setTextSize(size);
        text.setColor(mix(WHITE, DARK, k));
        text.setShadowLayer(0f, 0f, 0f, 0);   // aucun contour ni halo : la lisibilite vient du traitement progressif de l'image
        text.setAlpha(alpha);
        c.drawText(t, x, y, text);
    }

    private String dateLine(HomeState s) {
        String d = s.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                .toUpperCase(Locale.getDefault());
        String t = s.tempText();
        return t.isEmpty() ? d : d + "   ·   " + t;
    }

    private String greeting(SolarPhase.Phase p) {
        int id;
        switch (p) {
            case DAWN: id = R.string.alpine_greeting_dawn; break;
            case DAY: id = R.string.alpine_greeting_day; break;
            case DUSK: id = R.string.alpine_greeting_dusk; break;
            default: id = R.string.alpine_greeting_night; break;
        }
        return getResources().getString(id).toUpperCase(Locale.getDefault());
    }

    /** Un voile sombre sous le dock : il reste lisible sur n'importe quel fond ou calque. */
    private void drawScrim(Canvas c, int w, int h) {
        overlay.setShader(scrim);
        overlay.setAlpha(255);
        c.drawRect(0, h * 0.62f, w, h, overlay);
        overlay.setShader(null);
    }

    /** Hauteur de soleil, azimut et ciel courants (mesures et tests). */
    SceneMix mixForTest() {
        return scene.mix;
    }
}
