package com.example.remind;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        stampRoot();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        stampRoot();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        stampRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // App lock check — only when user is logged in
        if (!isLockScreen()) {
            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                SharedPreferences prefs = getSharedPreferences(AdditionalSettingsActivity.PREFS_NAME, MODE_PRIVATE);
                boolean lockEnabled = prefs.getBoolean(AppLockActivity.KEY_APP_LOCK, false);
                if (lockEnabled && !RemindApp.appUnlocked) {
                    startActivity(new Intent(this, AppLockActivity.class));
                    return;
                }
            }
        }
        // Re-stamp on every resume so font/effect changes from AdditionalSettings apply
        stampRoot();
        // Apply eye protection overlay
        AdditionalSettingsActivity.applyEyeProtectionToWindow(this);
    }

    /** Override in AppLockActivity to prevent lock loop */
    protected boolean isLockScreen() { return false; }

    public void applyAllToView(View v) {
        RemindApp.applyAll(v, this);
    }

    private void stampRoot() {
        View root = getWindow().getDecorView();
        // post() ensures the full layout tree is attached before we walk it
        root.post(() -> RemindApp.applyAll(root, this));
    }
}
