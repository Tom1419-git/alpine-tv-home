package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * La meteo de premier plan, par intensites continues et lissees (voir {@link SceneMix}) : pluie en trainees inclinees par
 * un vent lent (trois plans de profondeur, rafales, vagues pour les averses), neige en flocons de tailles et de
 * profondeurs differentes (les plus pres sont flous), orage avec un eclair rare et son halo LOCAL, jamais un flash plein
 * ecran. Dessine derriere le dock et les pilules (qui sont des vues au-dessus), jamais devant le texte de l'heure : aucune
 * particule dans son rectangle. Le brouillard et la pluie sur l'eau sont dessines par {@link PhotoBackdrop}, dans les
 * calques (le brouillard derriere la rive, pour la profondeur).
 *
 * Aucune allocation dans {@link #draw} : les particules sont des tableaux tires une fois, leur position est une fonction du
 * temps (elles se recyclent en bas de l'ecran), et le nombre de particules visibles monte et descend avec l'intensite, avec
 * une apparition progressive de chaque particule.
 */
final class WeatherLayer {
    private static final int RAIN = 230, SNOW = 360;
    private static final long FLASH_MS = 220;
    private static final long GAP_MIN_MS = 32_000, GAP_MAX_MS = 60_000;   // un a deux eclairs par minute au plus

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path boltPath = new Path();
    private final float[] rx = new float[RAIN], ry = new float[RAIN], rj = new float[RAIN];
    private final float[] sx = new float[SNOW], sy = new float[SNOW], sj = new float[SNOW], sd = new float[SNOW];
    private final Random random = new Random(11);
    private final Shader haloShader = new RadialGradient(0f, 0f, 1f, new int[]{0xFFDCE6FF, 0x00DCE6FF}, null, Shader.TileMode.CLAMP);
    private final Shader flakeShader = new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private long nextFlash;
    private long flashStart = -1;
    private float boltX;
    private float density = 1f;
    private float noL, noT, noR, noB;

    WeatherLayer() {
        Random r = new Random(3);
        for (int i = 0; i < RAIN; i++) {
            rx[i] = r.nextFloat();
            ry[i] = r.nextFloat();
            rj[i] = r.nextFloat();
        }
        for (int i = 0; i < SNOW; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            sj[i] = r.nextFloat() * 6.2832f;
            sd[i] = (float) Math.pow(r.nextFloat(), 1.7);   // peu de flocons proches
        }
        line.setStrokeCap(Paint.Cap.ROUND);
        bolt.setStyle(Paint.Style.STROKE);
        bolt.setStrokeJoin(Paint.Join.ROUND);
    }

    void setDensity(float density) {
        this.density = density;
        bolt.setStrokeWidth(2.2f * density);
    }

    /** Zone de l'heure, en pixels : aucune particule n'y est dessinee. */
    void setNoGo(float l, float t, float r, float b) {
        noL = l;
        noT = t;
        noR = r;
        noB = b;
    }

    /** Vrai si la meteo de premier plan a quelque chose a dessiner ou a animer. */
    static boolean active(SceneMix m) {
        return m.rain > 0.01f || m.snow > 0.01f || m.storm > 0.01f;
    }

    /** Intensite de pluie effective : les averses montent et descendent par vagues d'une dizaine de secondes. */
    static float rainNow(SceneMix m, float t) {
        float wave = 0.5f + 0.5f * (float) Math.sin(6.2832 * t / 9.0 + 0.8 * Math.sin(6.2832 * t / 23.0));
        return m.rain * (1f - m.showers * 0.45f * (1f - wave));
    }

    /** Vent : -1 a 1, lent et irregulier, plus fort pendant les averses et l'orage. */
    static float gust(float t) {
        return (float) (Math.sin(t * 0.37) * 0.7 + Math.sin(t * 0.91 + 1.0) * 0.3);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    boolean hidden(float x, float y) {
        return x > noL && x < noR && y > noT && y < noB;
    }

    void draw(Canvas c, int w, int h, Backdrop.Scene s) {
        SceneMix m = s.mix;
        float t = s.timeMs / 1000f;
        if (m.storm > 0.01f) {
            line.setColor(0xFF05080F);
            line.setAlpha((int) (255 * SceneGrade.STORM_ALPHA * 0.6f * m.storm));
            c.drawRect(0f, 0f, w, h, line);
        }
        float rain = rainNow(m, t);
        if (rain > 0.01f) rain(c, w, h, t, rain, m);
        if (m.snow > 0.01f) snow(c, w, h, t, m.snow, m);
        if (m.storm > 0.6f) lightning(c, w, h, s);
    }

    /** Vent (0 a 1 des observations, plus les rafales) en inclinaison des traits et derive des flocons. */
    private static float windTilt(SceneMix m, float t) {
        float gust = gust(t);
        return (0.06f + 0.34f * m.wind) * (1f + 0.35f * gust) + 0.10f * (m.showers + m.storm) * gust;
    }

    /**
     * Pluie : trois plans de profondeur, au loin fin, court, lent et pale ; au premier plan plus long, plus rapide, plus net.
     * La bruine n'a que des traits lointains. Le vent incline les traits.
     */
    private void rain(Canvas c, int w, int h, float t, float intensity, SceneMix m) {
        float visible = intensity * RAIN;
        float wind = windTilt(m, t);
        float sizeK = h / 1080f;
        float nearK = SceneGrade.smoothstep(0.28f, 0.7f, intensity);
        for (int g = 0; g < 3; g++) {
            float d = (g + 0.5f) / 3f;
            float layerK = g == 0 ? 1f : g == 1 ? 0.35f + 0.65f * nearK : nearK;
            if (layerK <= 0.01f) continue;
            line.setColor(g == 2 ? 0xFFE1EAF6 : 0xFFC9D8EA);
            line.setStrokeWidth(Math.max(0.8f, (0.6f + 1.0f * d) * sizeK));
            float len = h * (0.010f + 0.040f * d) * (0.6f + 0.6f * Math.min(1f, intensity));
            float speed = 0.7f + 1.5f * d;
            for (int i = g; i < RAIN; i += 3) {
                float k = Math.min(1f, visible - i);             // apparition progressive de chaque trainee
                if (k <= 0f) continue;
                float y = frac(ry[i] + t * speed * (0.85f + 0.3f * rj[i]));
                float x = frac(rx[i] + wind * y * 0.14f) * w;
                float yy = y * h;
                if (hidden(x, yy)) continue;
                float tilt = wind * (0.85f + 0.3f * rj[i]);
                line.setAlpha((int) (255 * k * layerK * (0.08f + 0.22f * d) * Math.min(1f, 0.4f + intensity)));
                c.drawLine(x, yy, x - len * tilt, yy + len, line);
            }
        }
    }

    /**
     * Neige : trois profondeurs. Loin : points d'un demi-pixel a un pixel, lents, pales, nombreux. Pres : flocons ronds et doux
     * de 2 a 3 px, qui tombent vite, derivent avec le vent et vacillent. Aucun flocon devant l'heure.
     */
    private void snow(Canvas c, int w, int h, float t, float intensity, SceneMix m) {
        float visible = intensity * SNOW;
        float sizeK = h / 1080f;
        float wind = windTilt(m, t);
        for (int i = 0; i < SNOW; i++) {
            float k = Math.min(1f, visible - i);
            if (k <= 0f) continue;
            float d = sd[i];
            float sp = 0.020f + 0.085f * d;
            float y = frac(sy[i] + t * sp * (0.85f + 0.3f * (float) Math.sin(sj[i])));
            float x = frac(sx[i] + wind * 0.06f * t * (0.4f + d)) * w + (float) Math.sin(t * (0.6f + d) + sj[i]) * w * (0.002f + 0.008f * d);
            float yy = y * h;
            if (hidden(x, yy)) continue;
            float rad = (0.55f + 2.5f * d) * sizeK;
            float al = k * (0.22f + 0.62f * d);
            if (d > 0.4f) {                                    // proches : ronds et doux, hors de la mise au point
                dot.setShader(flakeShader);
                dot.setAlpha((int) (255 * Math.min(1f, al * 1.05f)));
                c.save();
                c.translate(x, yy);
                c.scale(rad * 1.5f, rad * 1.5f);
                c.drawCircle(0f, 0f, 1f, dot);
                c.restore();
                dot.setShader(null);
            } else {
                dot.setColor(0xFFFFFFFF);
                dot.setAlpha((int) (255 * al));
                c.drawCircle(x, yy, rad, dot);
            }
        }
    }

    /** Un eclair rare (32 a 60 s d'ecart, donc un ou deux par minute), dans le ciel, avec un halo local borne. */
    private void lightning(Canvas c, int w, int h, Backdrop.Scene s) {
        if (!s.state.motion) return;
        long now = s.timeMs;
        if (flashStart < 0 && now >= nextFlash) {
            flashStart = now;
            nextFlash = now + GAP_MIN_MS + (long) (random.nextFloat() * (GAP_MAX_MS - GAP_MIN_MS));
            makeBolt(w, h);
        } else if (flashStart < 0 && nextFlash == 0) {
            nextFlash = now + 8000;
        }
        if (flashStart < 0) return;
        float k = (now - flashStart) / (float) FLASH_MS;
        if (k >= 1f) {
            flashStart = -1;
            return;
        }
        float a = k < 0.3f ? k / 0.3f : (1f - k) / 0.7f;
        float r = h * 0.30f;
        halo.setShader(haloShader);
        halo.setAlpha((int) (255 * 0.22f * a * s.mix.storm));
        c.save();
        c.translate(boltX, h * 0.12f);
        c.scale(r * 1.5f, r);
        c.drawCircle(0f, 0f, 1f, halo);
        c.restore();
        halo.setShader(null);
        bolt.setColor(0xFFEAF0FF);
        bolt.setAlpha((int) (210 * a));
        c.drawPath(boltPath, bolt);
    }

    private void makeBolt(int w, int h) {
        boltPath.reset();
        float px = w * (0.2f + random.nextFloat() * 0.6f);
        boltX = px;
        float py = 0f;
        boltPath.moveTo(px, py);
        while (py < h * 0.34f) {
            px += (random.nextFloat() - 0.5f) * w * 0.03f;
            py += h * (0.03f + random.nextFloat() * 0.03f);
            boltPath.lineTo(px, py);
        }
    }
}
