package com.example.remind;

import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AdditionalSettingsActivity.applyTheme(this);

        // Full screen for splash only
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);
        FirebaseApp.initializeApp(this);

        LinearLayout centerGroup = findViewById(R.id.centerGroup);
        ImageView    ivLogo      = findViewById(R.id.ivLogo);
        ProgressBar  progress    = findViewById(R.id.progressBar);

        Handler handler = new Handler(Looper.getMainLooper());

        // Step 1 — whole center group scales + fades in
        Animation groupAnim = AnimationUtils.loadAnimation(this, R.anim.splash_logo_enter);
        groupAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {
                centerGroup.setVisibility(View.VISIBLE);
            }
            @Override public void onAnimationEnd(Animation a) {
                // Step 2 — bell rings after group appears
                Drawable d = ivLogo.getDrawable();
                if (d instanceof AnimatedVectorDrawable) {
                    ((AnimatedVectorDrawable) d).start();
                }
            }
            @Override public void onAnimationRepeat(Animation a) {}
        });
        centerGroup.startAnimation(groupAnim);

        // Step 3 — show progress spinner at bottom
        handler.postDelayed(() -> progress.setVisibility(View.VISIBLE), 900);

        // Step 4 — navigate
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            Class<?> dest = mAuth.getCurrentUser() != null
                    ? HomeActivity.class
                    : IntroActivity.class;
            Intent next = new Intent(this, dest);
            // Clear entire back stack so no activity can be re-entered
            next.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(next);
            finish();
        }, SPLASH_DURATION);
    }
}
