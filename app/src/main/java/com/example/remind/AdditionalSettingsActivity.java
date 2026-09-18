package com.example.remind;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AdditionalSettingsActivity extends BaseActivity {

    static final String PREFS_NAME    = "remind_prefs";
    static final String KEY_DARK      = "dark_mode";
    static final String KEY_FONT      = "font_style";   // default / serif / mono / cursive
    static final String KEY_EFFECT    = "app_effect";   // none / rounded / compact
    static final String KEY_EYE_PROT  = "eye_protection"; // boolean
    static final String KEY_APP_LOCK  = "app_lock_enabled"; // boolean (mirrors AppLockActivity)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_additional_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ── Dark Mode ──────────────────────────────────────────────
        SwitchMaterial switchDark = findViewById(R.id.switchDarkMode);
        android.widget.ImageView ivDarkIcon = findViewById(R.id.ivDarkModeIcon);
        boolean isDark = prefs.getBoolean(KEY_DARK, false);
        switchDark.setChecked(isDark);
        ivDarkIcon.setImageResource(isDark ? R.drawable.ic_moon : R.drawable.ic_sun);
        switchDark.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_DARK, checked).apply();
            ivDarkIcon.setImageResource(checked ? R.drawable.ic_moon : R.drawable.ic_sun);
            AppCompatDelegate.setDefaultNightMode(
                    checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // ── Eye Protection ─────────────────────────────────────────
        SwitchMaterial switchEye = findViewById(R.id.switchEyeProtection);
        switchEye.setChecked(prefs.getBoolean(KEY_EYE_PROT, false));
        switchEye.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_EYE_PROT, checked).apply();
            applyEyeProtection(checked);
        });

        // ── App Lock ───────────────────────────────────────────────
        SwitchMaterial switchLock = findViewById(R.id.switchAppLock);
        switchLock.setChecked(prefs.getBoolean(KEY_APP_LOCK, false));
        switchLock.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                // Must set a PIN first
                showPinSetupSheet(prefs, switchLock);
            } else {
                prefs.edit().putBoolean(KEY_APP_LOCK, false)
                        .remove(AppLockActivity.KEY_APP_PIN).apply();
                RemindApp.appUnlocked = false;
            }
        });

        // ── Font Style ─────────────────────────────────────────────
        ChipGroup chipFont = findViewById(R.id.chipGroupFont);
        TextView tvPreview = findViewById(R.id.tvFontPreview);
        String savedFont = prefs.getString(KEY_FONT, "default");
        setFontChip(chipFont, savedFont);
        // Apply immediately after layout is ready
        tvPreview.post(() -> applyFontToPreview(tvPreview, savedFont));

        chipFont.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            String font = fontFromChipId(ids.get(0));
            prefs.edit().putString(KEY_FONT, font).apply();
            applyFontToPreview(tvPreview, font);
        });

        // ── App Effect ─────────────────────────────────────────────
        ChipGroup chipEffect = findViewById(R.id.chipGroupEffect);
        MaterialCardView previewCard = findViewById(R.id.cardEffectPreview);
        String savedEffect = prefs.getString(KEY_EFFECT, "none");
        setEffectChip(chipEffect, savedEffect);
        previewCard.post(() -> applyEffectToPreview(previewCard, savedEffect));

        chipEffect.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            String effect = effectFromChipId(ids.get(0));
            prefs.edit().putString(KEY_EFFECT, effect).apply();
            applyEffectToPreview(previewCard, effect);
        });
    }

    // ── Helpers ────────────────────────────────────────────────────

    private String fontFromChipId(int id) {
        if (id == R.id.chipFontSerif)   return "serif";
        if (id == R.id.chipFontMono)    return "mono";
        if (id == R.id.chipFontCursive) return "cursive";
        return "default";
    }

    private String effectFromChipId(int id) {
        if (id == R.id.chipEffectRounded) return "rounded";
        if (id == R.id.chipEffectCompact) return "compact";
        if (id == R.id.chipEffectGlass)   return "glass";
        return "none";
    }

    private void setFontChip(ChipGroup group, String font) {
        int id = R.id.chipFontDefault;
        if ("serif".equals(font))   id = R.id.chipFontSerif;
        if ("mono".equals(font))    id = R.id.chipFontMono;
        if ("cursive".equals(font)) id = R.id.chipFontCursive;
        group.check(id);
    }

    private void setEffectChip(ChipGroup group, String effect) {
        int id = R.id.chipEffectNone;
        if ("rounded".equals(effect)) id = R.id.chipEffectRounded;
        if ("compact".equals(effect)) id = R.id.chipEffectCompact;
        if ("glass".equals(effect))   id = R.id.chipEffectGlass;
        group.check(id);
    }

    private void applyFontToPreview(TextView tv, String font) {
        switch (font) {
            case "serif":   tv.setTypeface(Typeface.SERIF);   break;
            case "mono":    tv.setTypeface(Typeface.MONOSPACE); break;
            case "cursive": tv.setTypeface(Typeface.create("cursive", Typeface.NORMAL)); break;
            default:        tv.setTypeface(Typeface.DEFAULT); break;
        }
    }

    private void applyEffectToPreview(MaterialCardView card, String effect) {
        float d = getResources().getDisplayMetrics().density;
        switch (effect) {
            case "glass":
                card.setRadius(20 * d);
                card.setCardBackgroundColor(getResources().getColor(R.color.bg_glass, getTheme()));
                card.setStrokeColor(getResources().getColor(R.color.glass_stroke, getTheme()));
                card.setStrokeWidth((int)(1.5f * d));
                card.setCardElevation(12 * d);
                card.setAlpha(1f);
                break;
            case "rounded":
                card.setRadius(28 * d);
                card.setCardBackgroundColor(getResources().getColor(R.color.bg_rounded, getTheme()));
                card.setStrokeColor(getResources().getColor(R.color.accent, getTheme()));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(6 * d);
                card.setAlpha(1f);
                break;
            case "compact":
                card.setRadius(4 * d);
                card.setCardBackgroundColor(getResources().getColor(R.color.bg_surface, getTheme()));
                card.setStrokeColor(getResources().getColor(R.color.divider, getTheme()));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(2 * d);
                card.setAlpha(1f);
                break;
            default:
                card.setRadius(12 * d);
                card.setCardBackgroundColor(getResources().getColor(R.color.bg_surface, getTheme()));
                card.setStrokeColor(getResources().getColor(R.color.accent, getTheme()));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(2 * d);
                card.setAlpha(1f);
                break;
        }
    }

    // ── PIN Setup Sheet ────────────────────────────────────────────

    private void showPinSetupSheet(SharedPreferences prefs, SwitchMaterial switchLock) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.view.View v = getLayoutInflater().inflate(R.layout.bottom_sheet_pin_setup, null);
        sheet.setContentView(v);
        sheet.setCanceledOnTouchOutside(false);

        EditText etPin1 = v.findViewById(R.id.etPin1);
        EditText etPin2 = v.findViewById(R.id.etPin2);
        MaterialButton btnSet = v.findViewById(R.id.btnSetPin);
        MaterialButton btnCancel = v.findViewById(R.id.btnCancelPin);

        btnCancel.setOnClickListener(sv -> {
            switchLock.setChecked(false);
            sheet.dismiss();
        });
        sheet.setOnCancelListener(d -> switchLock.setChecked(false));

        btnSet.setOnClickListener(sv -> {
            String p1 = etPin1.getText().toString().trim();
            String p2 = etPin2.getText().toString().trim();
            if (p1.length() != 6) {
                Toast.makeText(this, "PIN must be 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putBoolean(KEY_APP_LOCK, true)
                    .putString(AppLockActivity.KEY_APP_PIN, AppLockActivity.sha256(p1))
                    .apply();
            RemindApp.appUnlocked = true; // current session stays unlocked
            Toast.makeText(this, "App lock enabled", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });

        sheet.show();
    }

    // ── Static helpers called from SplashActivity ──────────────────

    public static void applyTheme(android.content.Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(
                p.getBoolean(KEY_DARK, false)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }
    private void applyEyeProtection(boolean enabled) {
        android.view.View overlay = getWindow().getDecorView().findViewWithTag("eye_overlay");
        if (enabled) {
            if (overlay == null) {
                android.view.View v = new android.view.View(this);
                v.setTag("eye_overlay");
                v.setBackgroundColor(0x0DFFD700); // ~5% warm yellow tint
                v.setClickable(false);
                v.setFocusable(false);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
                ((android.view.ViewGroup) getWindow().getDecorView()).addView(v, lp);
            }
        } else {
            if (overlay != null) {
                ((android.view.ViewGroup) getWindow().getDecorView()).removeView(overlay);
            }
        }
    }

    /** Called from BaseActivity — applies eye protection overlay to any activity window */
    public static void applyEyeProtectionToWindow(android.app.Activity activity) {
        boolean enabled = activity.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_EYE_PROT, false);
        android.view.View decor = activity.getWindow().getDecorView();
        android.view.View existing = decor.findViewWithTag("eye_overlay");
        if (enabled && existing == null) {
            android.view.View v = new android.view.View(activity);
            v.setTag("eye_overlay");
            v.setBackgroundColor(0x0DFFD700); // ~5% warm yellow tint
            v.setClickable(false);
            v.setFocusable(false);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            ((android.view.ViewGroup) decor).addView(v, lp);
        } else if (!enabled && existing != null) {
            ((android.view.ViewGroup) decor).removeView(existing);
        }
    }

    public static Typeface getSavedTypeface(android.content.Context ctx) {
        String font = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_FONT, "default");
        switch (font) {
            case "serif":   return Typeface.SERIF;
            case "mono":    return Typeface.MONOSPACE;
            case "cursive": return Typeface.create("cursive", Typeface.NORMAL);
            default:        return Typeface.DEFAULT;
        }
    }

    public static float getSavedCornerRadius(android.content.Context ctx) {
        String effect = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_EFFECT, "none");
        float d = ctx.getResources().getDisplayMetrics().density;
        if ("rounded".equals(effect)) return 32 * d;
        if ("compact".equals(effect)) return 4  * d;
        if ("glass".equals(effect))   return 20 * d;
        return 16 * d;
    }

    /** Apply the saved effect to any task card — call from TaskAdapter.onBindViewHolder */
    public static void applyEffectToCard(android.content.Context ctx, MaterialCardView card) {
        String effect = ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_EFFECT, "none");
        float d = ctx.getResources().getDisplayMetrics().density;
        android.content.res.Resources.Theme theme = ctx.getTheme();

        switch (effect) {
            case "glass":
                card.setRadius(20 * d);
                card.setCardBackgroundColor(ctx.getResources().getColor(R.color.bg_glass, theme));
                card.setStrokeColor(ctx.getResources().getColor(R.color.glass_stroke, theme));
                card.setStrokeWidth((int)(1.5f * d));
                card.setCardElevation(12 * d);
                card.setAlpha(0.92f);
                break;

            case "rounded":
                card.setRadius(28 * d);
                card.setCardBackgroundColor(ctx.getResources().getColor(R.color.bg_rounded, theme));
                card.setStrokeColor(ctx.getResources().getColor(R.color.accent, theme));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(6 * d);
                card.setAlpha(1f);
                break;

            case "compact":
                card.setRadius(4 * d);
                card.setCardBackgroundColor(ctx.getResources().getColor(R.color.bg_card, theme));
                card.setStrokeColor(ctx.getResources().getColor(R.color.divider, theme));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(2 * d);
                card.setAlpha(1f);
                break;

            default: // none / default
                card.setRadius(16 * d);
                card.setCardBackgroundColor(ctx.getResources().getColor(R.color.bg_card, theme));
                card.setStrokeColor(ctx.getResources().getColor(R.color.divider, theme));
                card.setStrokeWidth((int)(1 * d));
                card.setCardElevation(2 * d);
                card.setAlpha(1f);
                break;
        }
    }
}
