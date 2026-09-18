package com.example.remind;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.security.MessageDigest;

public class AppLockActivity extends AppCompatActivity {

    static final String KEY_APP_LOCK = "app_lock_enabled";
    static final String KEY_APP_PIN  = "app_lock_pin";   // SHA-256 hex

    private StringBuilder pinInput = new StringBuilder();
    private TextView tvDots;
    private String savedHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);

        SharedPreferences prefs = getSharedPreferences(AdditionalSettingsActivity.PREFS_NAME, MODE_PRIVATE);
        savedHash = prefs.getString(KEY_APP_PIN, "");

        tvDots = findViewById(R.id.tvPinDots);

        // Digit buttons
        int[] btnIds = {
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };
        for (int i = 0; i < btnIds.length; i++) {
            final String digit = String.valueOf(i);
            findViewById(btnIds[i]).setOnClickListener(v -> appendDigit(digit));
        }

        // Backspace
        ImageButton btnBack = findViewById(R.id.btnPinBack);
        btnBack.setOnClickListener(v -> {
            if (pinInput.length() > 0) {
                pinInput.deleteCharAt(pinInput.length() - 1);
                updateDots();
            }
        });

        // Fingerprint button
        View btnFinger = findViewById(R.id.btnFingerprint);
        BiometricManager bm = BiometricManager.from(this);
        boolean canUseBio = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
        btnFinger.setVisibility(canUseBio ? View.VISIBLE : View.GONE);
        if (canUseBio) {
            btnFinger.setOnClickListener(v -> showBiometric());
            // Auto-trigger biometric on open
            showBiometric();
        }
    }

    private void appendDigit(String d) {
        if (pinInput.length() >= 6) return;
        pinInput.append(d);
        updateDots();
        if (pinInput.length() == 6) verifyPin();
    }

    private void updateDots() {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < 6; i++) dots.append(i < pinInput.length() ? "●" : "○");
        tvDots.setText(dots.toString());
    }

    private void verifyPin() {
        String hash = sha256(pinInput.toString());
        if (hash.equals(savedHash)) {
            unlock();
        } else {
            pinInput.setLength(0);
            updateDots();
            tvDots.setText("Wrong PIN");
            tvDots.postDelayed(this::updateDots, 800);
        }
    }

    private void showBiometric() {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Remind")
                .setSubtitle("Use fingerprint to unlock")
                .setNegativeButtonText("Use PIN")
                .build();

        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult r) {
                        unlock();
                    }
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        // user chose PIN — do nothing, PIN pad is visible
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        Toast.makeText(AppLockActivity.this, "Not recognized", Toast.LENGTH_SHORT).show();
                    }
                });
        prompt.authenticate(info);
    }

    private void unlock() {
        RemindApp.appUnlocked = true;
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent back — user must authenticate
        finishAffinity();
    }

    // ── Static helpers ─────────────────────────────────────────────

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input; // fallback (should never happen)
        }
    }
}
