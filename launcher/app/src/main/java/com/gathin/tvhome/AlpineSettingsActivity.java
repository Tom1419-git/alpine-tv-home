package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglages de l'accueil alpin : accueil meteo, ville, unite, mouvement, nuit noire OLED, apercu, credits.
 * Les lignes sont construites en code, avec le style des autres reglages du lanceur. Chaque changement incremente
 * le compteur de revision : Home se reconstruit a son retour, comme pour les autres reglages.
 */
public final class AlpineSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private final List<Runnable> refreshers = new ArrayList<>();
    private LinearLayout column;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101419);
        scroll.setFillViewport(true);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = dp(72);
        column.setPadding(side, dp(38), side, dp(38));
        scroll.addView(column);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(R.string.alpine_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(34);
        column.addView(title);

        toggle(R.string.alpine_enabled_row, AlpineSettings.ENABLED, getResources().getBoolean(R.bool.alpine_home));
        place();
        unit();
        toggle(R.string.alpine_motion_row, AlpineSettings.MOTION, true);
        toggle(R.string.alpine_black_night_row, AlpineSettings.BLACK_NIGHT, false);
        preview();
        extraRows();

        TextView credit = new TextView(this);
        credit.setText(R.string.alpine_weather_credit);
        credit.setTextColor(0x99FFFFFF);
        credit.setTextSize(14);
        credit.setPadding(dp(18), dp(24), 0, 0);
        column.addView(credit);
        refresh();
        if (column.getChildCount() > 1) column.getChildAt(1).requestFocus();
    }

    /** Lignes ajoutees par les jalons suivants (pack photo, musique, credits). */
    private void extraRows() {
        toggle(R.string.alpine_photo_row, AlpineSettings.PHOTO, true);
        packRow();
        toggle(R.string.alpine_music_row, AlpineSettings.MUSIC, true);
        TextView access = row();
        refreshers.add(() -> access.setText(getString(R.string.alpine_notif_row, getString(
                new MusicController(this, t -> { }).hasAccess() ? R.string.alpine_notif_granted
                        : R.string.alpine_notif_missing))));
        access.setOnClickListener(x -> {
            Toast.makeText(this, R.string.alpine_notif_help, Toast.LENGTH_LONG).show();
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        });
        TextView credits = row();
        credits.setText(R.string.alpine_credits_row);
        credits.setOnClickListener(x -> new AlertDialog.Builder(this)
                .setTitle(R.string.alpine_credits_row)
                .setMessage(R.string.alpine_credits_text)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    /** Etat du pack photo : verifie en arriere-plan (hachage des images), puis affiche sur la ligne. */
    private void packRow() {
        TextView v = row();
        v.setText(getString(R.string.alpine_pack_row, getString(R.string.alpine_pack_checking)));
        Thread t = new Thread(() -> {
            WeatherPack.Status status = WeatherPack.validate(getApplicationContext());
            int label = R.string.alpine_pack_absent;
            if (status == WeatherPack.Status.INVALID) label = R.string.alpine_pack_invalid;
            else if (status == WeatherPack.Status.OK) label = R.string.alpine_pack_ok;
            final int shown = label;
            runOnUiThread(() -> {
                if (!isDestroyed()) v.setText(getString(R.string.alpine_pack_row, getString(shown)));
            });
        }, "alpine-pack-status");
        t.setDaemon(true);
        t.start();
    }

    // ---- lignes --------------------------------------------------------------------------------------------

    private TextView row() {
        TextView v = new TextView(new ContextThemeWrapper(this, R.style.SettingsRow), null, 0, R.style.SettingsRow);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(28);
        column.addView(v, lp);
        return v;
    }

    private void toggle(int label, String key, boolean fallback) {
        TextView v = row();
        refreshers.add(() -> v.setText(getString(label,
                getString(prefs.getBoolean(key, fallback) ? R.string.on : R.string.off))));
        v.setOnClickListener(x -> {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, fallback)).apply();
            changed();
            refresh();
        });
    }

    private void unit() {
        TextView v = row();
        refreshers.add(() -> v.setText(getString(R.string.alpine_unit_row,
                prefs.getBoolean(AlpineSettings.FAHRENHEIT, false) ? "°F" : "°C")));
        v.setOnClickListener(x -> {
            prefs.edit().putBoolean(AlpineSettings.FAHRENHEIT,
                    !prefs.getBoolean(AlpineSettings.FAHRENHEIT, false)).apply();
            changed();
            refresh();
        });
    }

    private void place() {
        TextView v = row();
        refreshers.add(() -> v.setText(getString(R.string.alpine_city_row,
                prefs.getString(AlpineSettings.CITY, AlpineSettings.DEFAULT_CITY))));
        v.setOnClickListener(x -> showPlaceDialog());
    }

    private void showPlaceDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = field(box, R.string.alpine_city_name, InputType.TYPE_CLASS_TEXT,
                prefs.getString(AlpineSettings.CITY, AlpineSettings.DEFAULT_CITY));
        EditText lat = field(box, R.string.alpine_latitude,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                prefs.getString(AlpineSettings.LAT, AlpineSettings.DEFAULT_LAT));
        EditText lon = field(box, R.string.alpine_longitude,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                prefs.getString(AlpineSettings.LON, AlpineSettings.DEFAULT_LON));
        new AlertDialog.Builder(this)
                .setTitle(R.string.alpine_city_row_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String la = lat.getText().toString().trim();
                    String lo = lon.getText().toString().trim();
                    prefs.edit().putString(AlpineSettings.CITY, name.getText().toString().trim())
                            .putString(AlpineSettings.LAT, la).putString(AlpineSettings.LON, lo).apply();
                    if (Double.isNaN(AlpineSettings.parse(la, -90, 90))
                            || Double.isNaN(AlpineSettings.parse(lo, -180, 180))) {
                        Toast.makeText(this, R.string.alpine_invalid_place, Toast.LENGTH_LONG).show();
                    }
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private EditText field(LinearLayout box, int hint, int type, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setText(value);
        box.addView(e);
        return e;
    }

    private void preview() {
        TextView v = row();
        refreshers.add(() -> v.setText(getString(R.string.alpine_preview_row, previewLabel())));
        v.setOnClickListener(x -> pickPreviewPhase());
    }

    private String previewLabel() {
        String phase = prefs.getString(AlpineSettings.PREVIEW_PHASE, "");
        String cond = prefs.getString(AlpineSettings.PREVIEW_COND, "");
        if (phase.isEmpty() && cond.isEmpty()) return getString(R.string.alpine_real);
        return (phase.isEmpty() ? getString(R.string.alpine_real) : phaseLabel(phase))
                + " / " + (cond.isEmpty() ? getString(R.string.alpine_real) : conditionName(WeatherCodes.parse(cond)));
    }

    private void pickPreviewPhase() {
        final String[] all = {"avant-lever", "lever", "matin", "midi", "apres-midi", "coucher", "crepuscule", "nuit"};
        final int[] names = {R.string.alpine_phase_predawn, R.string.alpine_phase_sunrise, R.string.alpine_phase_morning,
                R.string.alpine_phase_midday, R.string.alpine_phase_afternoon, R.string.alpine_phase_sunset,
                R.string.alpine_phase_dusk, R.string.alpine_phase_night};
        String[] labels = new String[all.length + 1];
        labels[0] = getString(R.string.alpine_real);
        int checked = 0;
        String current = prefs.getString(AlpineSettings.PREVIEW_PHASE, "");
        for (int i = 0; i < all.length; i++) {
            labels[i + 1] = getString(names[i]);
            if (all[i].equalsIgnoreCase(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle(R.string.alpine_preview_phase)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    prefs.edit().putString(AlpineSettings.PREVIEW_PHASE, which == 0 ? "" : all[which - 1]).apply();
                    pickPreviewCondition();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pickPreviewCondition() {
        WeatherCodes.Condition[] all = WeatherCodes.Condition.values();
        String[] labels = new String[all.length + 1];
        labels[0] = getString(R.string.alpine_real);
        int checked = 0;
        String current = prefs.getString(AlpineSettings.PREVIEW_COND, "");
        for (int i = 0; i < all.length; i++) {
            labels[i + 1] = conditionName(all[i]);
            if (all[i].name().equalsIgnoreCase(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle(R.string.alpine_preview_weather)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    prefs.edit().putString(AlpineSettings.PREVIEW_COND,
                            which == 0 ? "" : all[which - 1].name().toLowerCase()).apply();
                    changed();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Libelle d'un moment de la journee (les huit apercus, plus les quatre phases d'origine). */
    private String phaseLabel(String name) {
        switch (name) {
            case "avant-lever": return getString(R.string.alpine_phase_predawn);
            case "lever": case "dawn": return getString(R.string.alpine_phase_sunrise);
            case "matin": return getString(R.string.alpine_phase_morning);
            case "midi": case "day": return getString(R.string.alpine_phase_midday);
            case "apres-midi": return getString(R.string.alpine_phase_afternoon);
            case "coucher": return getString(R.string.alpine_phase_sunset);
            case "crepuscule": case "dusk": return getString(R.string.alpine_phase_dusk);
            default: return getString(R.string.alpine_phase_night);
        }
    }

    private String phaseName(SolarPhase.Phase p) {
        switch (p) {
            case DAWN: return getString(R.string.alpine_phase_dawn);
            case DAY: return getString(R.string.alpine_phase_day);
            case DUSK: return getString(R.string.alpine_phase_dusk);
            default: return getString(R.string.alpine_phase_night);
        }
    }

    private String conditionName(WeatherCodes.Condition c) {
        switch (c) {
            case CLEAR: return getString(R.string.alpine_cond_clear);
            case PARTLY: return getString(R.string.alpine_cond_partly);
            case FOG: return getString(R.string.alpine_cond_fog);
            case DRIZZLE: return getString(R.string.alpine_cond_drizzle);
            case RAIN: return getString(R.string.alpine_cond_rain);
            case SHOWERS: return getString(R.string.alpine_cond_showers);
            case SNOW: return getString(R.string.alpine_cond_snow);
            case STORM: return getString(R.string.alpine_cond_storm);
            default: return getString(R.string.alpine_cond_overcast);
        }
    }

    // ---- etat ----------------------------------------------------------------------------------------------

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();   // l'acces aux notifications a pu etre accorde dans l'ecran systeme
    }

    /** Un reglage a change : Home se reconstruit a son retour (compteur de revision). */
    private void changed() {
        setResult(RESULT_OK);
        prefs.edit().putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
