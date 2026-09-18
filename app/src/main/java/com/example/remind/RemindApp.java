package com.example.remind;

import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class RemindApp extends Application {

    /** True once the user has passed the lock screen in this process session */
    public static boolean appUnlocked = false;
    /** Timestamp when app last went to background (ms). Lock after 30s in background. */
    private static long backgroundedAt = 0;
    private static final long LOCK_TIMEOUT_MS = 30_000; // 30 seconds

    @Override
    public void onCreate() {
        super.onCreate();
        // Enable Firestore offline cache (offline support)
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .setFirestoreSettings(new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build());
        registerActivityLifecycleCallbacks(new SimpleLifecycleCallbacks() {
            private int resumed = 0;
            @Override public void onActivityResumed(android.app.Activity a) {
                resumed++;
                if (resumed == 1 && backgroundedAt > 0) {
                    long elapsed = System.currentTimeMillis() - backgroundedAt;
                    if (elapsed > LOCK_TIMEOUT_MS) appUnlocked = false;
                    backgroundedAt = 0;
                }
            }
            @Override public void onActivityStopped(android.app.Activity a) {
                resumed--;
                if (resumed <= 0) {
                    resumed = 0;
                    backgroundedAt = System.currentTimeMillis();
                }
            }
        });
    }

    // Minimal adapter so we only override what we need
    private static abstract class SimpleLifecycleCallbacks implements ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
        @Override public void onActivityStarted(android.app.Activity a) {}
        @Override public void onActivityPaused(android.app.Activity a) {}
        @Override public void onActivityDestroyed(android.app.Activity a) {}
        @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
    }
    // ── Font ──────────────────────────────────────────────────────────────────

    public static void applyFont(View root, Context ctx) {
        if (root == null) return;
        Typeface tf = AdditionalSettingsActivity.getSavedTypeface(ctx);
        stampTypeface(root, tf);
    }

    private static void stampTypeface(View view, Typeface tf) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                stampTypeface(vg.getChildAt(i), tf);
            }
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int style = (tv.getTypeface() != null) ? tv.getTypeface().getStyle() : Typeface.NORMAL;
            tv.setTypeface(tf, style);
        }
    }

    // ── Effect ────────────────────────────────────────────────────────────────

    /** Walk full view tree — used by BaseActivity stampRoot */
    public static void applyEffect(View root, Context ctx) {
        if (root == null) return;
        String effect = getEffect(ctx);
        float d = ctx.getResources().getDisplayMetrics().density;
        stampEffect(root, effect, d, ctx);
    }

    /** Single card overload — used by TaskAdapter directly on cardForeground */
    public static void applyEffect(MaterialCardView card, Context ctx) {
        if (card == null) return;
        String effect = getEffect(ctx);
        float d = ctx.getResources().getDisplayMetrics().density;
        applyEffectToCard(card, effect, d, ctx);
    }

    private static String getEffect(Context ctx) {
        return ctx.getSharedPreferences(AdditionalSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AdditionalSettingsActivity.KEY_EFFECT, "none");
    }

    private static void stampEffect(View view, String effect, float d, Context ctx) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                stampEffect(vg.getChildAt(i), effect, d, ctx);
            }
        }
        if (view instanceof MaterialCardView) {
            applyEffectToCard((MaterialCardView) view, effect, d, ctx);
        } else if (view instanceof MaterialButton) {
            applyEffectToButton((MaterialButton) view, effect, d);
        }
    }

    private static void applyEffectToCard(MaterialCardView card, String effect, float d, Context ctx) {
        android.content.res.Resources.Theme theme = ctx.getTheme();
        android.content.res.Resources res = ctx.getResources();
        // ALWAYS alpha=1f — any transparency makes the swipe-edit bg bleed through
        card.setAlpha(1f);
        switch (effect) {
            case "glass":
                card.setRadius(20 * d);
                card.setCardBackgroundColor(res.getColor(R.color.bg_glass, theme));
                card.setStrokeColor(res.getColor(R.color.glass_stroke, theme));
                card.setStrokeWidth((int) (1.5f * d));
                card.setCardElevation(10 * d);
                break;
            case "rounded":
                card.setRadius(28 * d);
                card.setCardBackgroundColor(res.getColor(R.color.bg_rounded, theme));
                card.setStrokeColor(res.getColor(R.color.accent, theme));
                card.setStrokeWidth((int) (1 * d));
                card.setCardElevation(6 * d);
                break;
            case "compact":
                card.setRadius(4 * d);
                card.setCardBackgroundColor(res.getColor(R.color.bg_card, theme));
                card.setStrokeColor(res.getColor(R.color.divider, theme));
                card.setStrokeWidth((int) (1 * d));
                card.setCardElevation(1 * d);
                break;
            default:
                card.setRadius(16 * d);
                card.setCardBackgroundColor(res.getColor(R.color.bg_card, theme));
                card.setStrokeColor(res.getColor(R.color.divider, theme));
                card.setStrokeWidth((int) (1 * d));
                card.setCardElevation(2 * d);
                break;
        }
    }

    private static void applyEffectToButton(MaterialButton btn, String effect, float d) {
        switch (effect) {
            case "glass":
            case "rounded":
                btn.setCornerRadius((int) (24 * d));
                break;
            case "compact":
                btn.setCornerRadius((int) (4 * d));
                break;
            default:
                btn.setCornerRadius((int) (12 * d));
                break;
        }
    }

    // ── Combined ──────────────────────────────────────────────────────────────

    public static void applyAll(View root, Context ctx) {
        applyFont(root, ctx);
        applyEffect(root, ctx);
    }
}
