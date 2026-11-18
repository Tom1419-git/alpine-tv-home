package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Le bloc « en lecture » : verre comme le dock, pochette arrondie, titre sur une ligne avec ellipse, artiste dessous,
 * barre tres fine, trois commandes visibles seulement quand le bloc a le focus. Pose par-dessus l'accueil (il ne
 * decale jamais l'heure) et sans place reservee : quand il part, il ne laisse aucun trou. La progression se met a
 * jour une fois par seconde seulement quand le bloc est visible et que la musique joue.
 */
public final class MusicBlock extends FrameLayout {
    private static final long FADE_MS = 250;
    private static final ColorMatrixColorFilter GRAYED = grayed();

    private final float density;
    private final ImageView cover;
    private final TextView title;
    private final TextView artist;
    private final ProgressLine progress;
    private final LinearLayout controls;
    private final Transport prev;
    private final Transport playPause;
    private final Transport next;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Shader fillShader;
    private int shaderH = -1;

    private MusicController controller;
    private MusicController.Track track;
    private Bitmap shownArt;
    private int accent = AccentColor.FALLBACK;
    private boolean started;

    public MusicBlock(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setVisibility(GONE);
        setPadding(dp(8), dp(7), dp(8), dp(7));
        setFocusable(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0x66FFFFFF);

        cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setClipToOutline(true);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 9 * density);
            }
        });
        addView(cover, new LayoutParams(dp(44), dp(44), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LayoutParams tp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        tp.setMarginStart(dp(44 + 9));
        tp.setMarginEnd(dp(90));
        Typeface light = isInEditMode() ? Typeface.DEFAULT : UiFont.get(context, R.string.font_regular);
        title = text(context, light, 13, 0xFFFFFFFF);
        artist = text(context, light, 10, 0xB3FFFFFF);
        progress = new ProgressLine(context);
        texts.addView(title);
        texts.addView(artist);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(2, dp(3)));
        pp.topMargin = dp(5);
        texts.addView(progress, pp);
        addView(texts, tp);

        controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        prev = new Transport(context, Transport.PREV);
        playPause = new Transport(context, Transport.PLAY);
        next = new Transport(context, Transport.NEXT);
        for (Transport t : new Transport[]{prev, playPause, next}) {
            controls.addView(t, new LinearLayout.LayoutParams(dp(30), dp(30)));
        }
        controls.setAlpha(0f);
        addView(controls, new LayoutParams(dp(90), LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        prev.setOnClickListener(v -> {
            if (controller != null) controller.previous();
        });
        next.setOnClickListener(v -> {
            if (controller != null) controller.next();
        });
        playPause.setOnClickListener(v -> {
            if (controller == null || track == null) return;
            if (track.playing) controller.pause();
            else controller.play();
        });
    }

    private TextView text(Context c, Typeface face, int sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(3f * density, 0, density, 0x66000000);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private static ColorMatrixColorFilter grayed() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0.15f);
        return new ColorMatrixColorFilter(m);
    }

    void setController(MusicController controller) {
        this.controller = controller;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // ---- cycle de vie --------------------------------------------------------------------------------------

    void start() {
        started = true;
        scheduleTick();
    }

    /** Home passe derriere une autre application : plus de minuteur, bloc masque, pochette rendue. */
    void stop() {
        started = false;
        removeCallbacks(tick);
        removeCallbacks(expire);
        animate().cancel();
        track = null;
        shownArt = null;
        cover.setImageDrawable(null);
        setAlpha(1f);
        setVisibility(GONE);
    }

    /** Une nouvelle piste (ou null : rien a afficher). Sur le fil principal. */
    void show(@Nullable MusicController.Track t) {
        if (!started) return;
        removeCallbacks(expire);
        if (t == null) {
            hide();
            return;
        }
        boolean changedArt = t.art != shownArt;
        track = t;
        title.setText(t.title);
        artist.setText(t.artist);
        if (changedArt) {
            shownArt = t.art;
            cover.setImageBitmap(t.art);
            accent = t.art == null ? AccentColor.FALLBACK : accentOf(t.art);
            progress.setColor(accent);
            rim.setColor(blend(0x66FFFFFF, accent));
        }
        cover.setColorFilter(t.playing ? null : GRAYED);   // en pause : pochette desaturee
        playPause.setKind(t.playing ? Transport.PAUSE : Transport.PLAY);
        progress.setFraction(fraction(t));
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(FADE_MS).start();
        } else {
            animate().cancel();
            setAlpha(1f);
        }
        if (!t.playing) {
            long left = MediaPolicy.PAUSE_GRACE_MS - (System.currentTimeMillis() - t.pausedSinceMs);
            postDelayed(expire, Math.max(0, left));   // en pause depuis 2 minutes : on disparait en fondu
        }
        scheduleTick();
        invalidate();
    }

    private void hide() {
        track = null;
        removeCallbacks(tick);
        if (getVisibility() != VISIBLE) return;
        animate().cancel();
        animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            setVisibility(GONE);
            setAlpha(1f);
            cover.setImageDrawable(null);
            shownArt = null;
        }).start();
    }

    private final Runnable expire = this::hide;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!started || track == null || getVisibility() != VISIBLE || !track.playing) return;
            progress.setFraction(fraction(track));
            postDelayed(this, 1000);
        }
    };

    private void scheduleTick() {
        removeCallbacks(tick);
        if (started && track != null && track.playing && getVisibility() == VISIBLE) postDelayed(tick, 1000);
    }

    private static float fraction(MusicController.Track t) {
        return t.durationMs <= 0 ? 0f : Math.max(0f, Math.min(1f, t.positionNow() / (float) t.durationMs));
    }

    // ---- accent --------------------------------------------------------------------------------------------

    private static int accentOf(Bitmap art) {
        Bitmap small = Bitmap.createScaledBitmap(art, 16, 16, true);
        int[] px = new int[256];
        small.getPixels(px, 0, 16, 0, 0, 16, 16);
        if (small != art) small.recycle();
        return AccentColor.derive(px, px.length);
    }

    /** Teinte le bord de verre vers l'accent, sans perdre son aspect de verre. */
    private static int blend(int glass, int accent) {
        int r = (((glass >> 16) & 0xFF) * 2 + ((accent >> 16) & 0xFF)) / 3;
        int g = (((glass >> 8) & 0xFF) * 2 + ((accent >> 8) & 0xFF)) / 3;
        int b = ((glass & 0xFF) * 2 + (accent & 0xFF)) / 3;
        return (glass & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ---- focus : les commandes n'apparaissent que sur le bloc focalise -----------------------------------------

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        controls.animate().alpha(1f).setDuration(150).start();
    }

    @Override
    public void clearChildFocus(View child) {
        super.clearChildFocus(child);
        controls.animate().alpha(0f).setDuration(150).start();
    }

    // ---- dessin du verre -----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (fillShader == null || shaderH != h) {
            shaderH = h;
            fillShader = new LinearGradient(0, 0, 0, h, 0x38FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP);
        }
        float inset = rim.getStrokeWidth() / 2f;
        rect.set(inset, inset, w - inset, h - inset);
        fill.setShader(fillShader);
        c.drawRoundRect(rect, 14 * density, 14 * density, fill);
        c.drawRoundRect(rect, 14 * density, 14 * density, rim);
    }

    // ---- sous-vues -----------------------------------------------------------------------------------------

    /** Barre de progression tres fine. */
    private static final class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction;

        ProgressLine(Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
            bar.setColor(0xFFFFFFFF);
        }

        void setFraction(float f) {
            fraction = f;
            invalidate();
        }

        void setColor(int accent) {
            // L'accent est assombri pour les ciels clairs ; sur le verre sombre on le rapproche du blanc.
            bar.setColor(AccentColor.lift(accent));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, track);
            if (fraction > 0f) canvas.drawRoundRect(0, 0, getWidth() * fraction, getHeight(), r, r, bar);
        }
    }

    /** Une commande de transport : precedent, lecture, pause ou suivant, avec un anneau blanc au focus. */
    private static final class Transport extends View {
        static final int PREV = 0, PLAY = 1, PAUSE = 2, NEXT = 3;
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int kind;

        Transport(Context c, int kind) {
            super(c);
            this.kind = kind;
            icon.setColor(0xFFFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFFFFFFF);
            ring.setStrokeWidth(1.5f * c.getResources().getDisplayMetrics().density);
            setFocusable(true);
            setClickable(true);
        }

        void setKind(int kind) {
            if (this.kind != kind) {
                this.kind = kind;
                invalidate();
            }
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocused) {
            super.onFocusChanged(gainFocus, direction, previouslyFocused);
            animate().scaleX(gainFocus ? 1.12f : 1f).scaleY(gainFocus ? 1.12f : 1f).setDuration(120).start();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, u = Math.min(w, h) / 42f;
            if (isFocused()) c.drawCircle(cx, cy, 19 * u, ring);
            path.reset();
            switch (kind) {
                case PLAY:
                    path.moveTo(cx - 6 * u, cy - 9 * u);
                    path.lineTo(cx + 9 * u, cy);
                    path.lineTo(cx - 6 * u, cy + 9 * u);
                    path.close();
                    break;
                case PAUSE:
                    path.addRect(cx - 8 * u, cy - 9 * u, cx - 2.5f * u, cy + 9 * u, Path.Direction.CW);
                    path.addRect(cx + 2.5f * u, cy - 9 * u, cx + 8 * u, cy + 9 * u, Path.Direction.CW);
                    break;
                case PREV:
                    path.addRect(cx - 9 * u, cy - 8 * u, cx - 6 * u, cy + 8 * u, Path.Direction.CW);
                    path.moveTo(cx + 8 * u, cy - 8 * u);
                    path.lineTo(cx - 5 * u, cy);
                    path.lineTo(cx + 8 * u, cy + 8 * u);
                    path.close();
                    break;
                default:
                    path.addRect(cx + 6 * u, cy - 8 * u, cx + 9 * u, cy + 8 * u, Path.Direction.CW);
                    path.moveTo(cx - 8 * u, cy - 8 * u);
                    path.lineTo(cx + 5 * u, cy);
                    path.lineTo(cx - 8 * u, cy + 8 * u);
                    path.close();
                    break;
            }
            c.drawPath(path, icon);
        }
    }
}
