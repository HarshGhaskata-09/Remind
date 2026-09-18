package com.example.remind;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.OnBackPressedCallback;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends BaseActivity {

    private ViewPager2 viewPager;
    private MainPagerAdapter pagerAdapter;

    // Nav icons — all ImageView now
    private ImageView ivHomeIcon, ivNotesIcon, ivTimerIcon, ivStopwatchIcon, ivProfileIcon;

    // Nav labels
    private TextView tvHomeLabel, tvNotesLabel, tvTimerLabel, tvStopwatchLabel, tvProfileLabel;

    private FloatingActionButton fab;

    private String lastFont;
    private String lastEffect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        viewPager = findViewById(R.id.viewPager);

        // Nav icon refs
        ivHomeIcon      = findViewById(R.id.ivHomeIcon);
        ivNotesIcon     = findViewById(R.id.ivAlarmIcon);   // reuse same view id
        ivTimerIcon     = findViewById(R.id.ivTimerIcon);
        ivStopwatchIcon = findViewById(R.id.ivStopwatchIcon);
        ivProfileIcon   = findViewById(R.id.ivProfileIcon);

        // Nav label refs
        tvHomeLabel      = findViewById(R.id.tvHomeLabel);
        tvNotesLabel     = findViewById(R.id.tvAlarmLabel);  // reuse same view id
        tvTimerLabel     = findViewById(R.id.tvTimerLabel);
        tvStopwatchLabel = findViewById(R.id.tvStopwatchLabel);
        tvProfileLabel   = findViewById(R.id.tvProfileLabel);

        // Nav tap targets
        LinearLayout llHome      = findViewById(R.id.llHome);
        LinearLayout llAlarm     = findViewById(R.id.llAlarm);
        LinearLayout llTimer     = findViewById(R.id.llTimer);
        LinearLayout llStopwatch = findViewById(R.id.llStopwatch);
        LinearLayout llProfile   = findViewById(R.id.llProfile);
        fab = findViewById(R.id.ivAddData);

        TextView tvTitle = findViewById(R.id.tvToolbarTitle);
        tvTitle.setTypeface(AdditionalSettingsActivity.getSavedTypeface(this));
        android.widget.ImageButton iv3Dot = findViewById(R.id.iv3Dot);

        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        // Page change → sync nav + toolbar
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setActiveTab(position);
                switch (position) {
                    case MainPagerAdapter.PAGE_HOME:      tvTitle.setText(getString(R.string.app_name)); break;
                    case MainPagerAdapter.PAGE_NOTES:     tvTitle.setText("Notes"); break;
                    case MainPagerAdapter.PAGE_TIMER:     tvTitle.setText("Timer"); break;
                    case MainPagerAdapter.PAGE_STOPWATCH: tvTitle.setText("Stopwatch"); break;
                    case MainPagerAdapter.PAGE_PROFILE:   tvTitle.setText("Profile"); break;
                }
                iv3Dot.setVisibility(position == MainPagerAdapter.PAGE_PROFILE ? View.VISIBLE : View.GONE);
                // Show FAB on Home, Notes, and Timer tabs
                if (position == MainPagerAdapter.PAGE_HOME
                        || position == MainPagerAdapter.PAGE_NOTES
                        || position == MainPagerAdapter.PAGE_TIMER) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }

            @Override
            public void onPageScrolled(int pos, float offset, int offsetPx) {
                // fade handled by onPageSelected via fab.show()/hide()
            }
        });

        // 3-dot → AdditionalSettings
        iv3Dot.setOnClickListener(v ->
                startActivity(new Intent(this, AdditionalSettingsActivity.class)));

        // Nav taps
        llHome.setOnClickListener(v      -> viewPager.setCurrentItem(MainPagerAdapter.PAGE_HOME, true));
        llAlarm.setOnClickListener(v     -> viewPager.setCurrentItem(MainPagerAdapter.PAGE_NOTES, true));
        llTimer.setOnClickListener(v     -> viewPager.setCurrentItem(MainPagerAdapter.PAGE_TIMER, true));
        llStopwatch.setOnClickListener(v -> viewPager.setCurrentItem(MainPagerAdapter.PAGE_STOPWATCH, true));
        llProfile.setOnClickListener(v   -> viewPager.setCurrentItem(MainPagerAdapter.PAGE_PROFILE, true));

        // FAB → add task (Home) or add note (Notes) or add timer (Timer)
        fab.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur == MainPagerAdapter.PAGE_NOTES) {
                pagerAdapter.getNotesFragment().openAddEdit(null);
            } else if (cur == MainPagerAdapter.PAGE_TIMER) {
                pagerAdapter.getTimerFragment().openAddTimerSheet();
            } else {
                pagerAdapter.getHomeFragment().openAddTaskDialog();
            }
        });

        setActiveTab(MainPagerAdapter.PAGE_HOME);

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // On Android 12+ prompt user to grant exact alarm permission if not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                    ConfirmSheet.show(this,
                            "Enable Exact Reminders",
                            "To receive task reminders on time, please allow exact alarms in Settings.",
                            "Open Settings",
                            () -> {
                                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            });
                }
        }

        // Ensure notification channel is created early
        ReminderReceiver.createChannel(this);

        // Back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int cur = viewPager.getCurrentItem();
                if (cur != MainPagerAdapter.PAGE_HOME) {
                    viewPager.setCurrentItem(MainPagerAdapter.PAGE_HOME, true);
                } else {
                    showExitSheet();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(AdditionalSettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String currentFont   = prefs.getString(AdditionalSettingsActivity.KEY_FONT,   "default");
        String currentEffect = prefs.getString(AdditionalSettingsActivity.KEY_EFFECT, "none");

        if (lastFont == null) {
            lastFont   = currentFont;
            lastEffect = currentEffect;
        } else if (!currentFont.equals(lastFont) || !currentEffect.equals(lastEffect)) {
            lastFont   = currentFont;
            lastEffect = currentEffect;
            // Re-stamp the whole window instead of recreate() to avoid duplication
            RemindApp.applyAll(getWindow().getDecorView(), this);
        }
    }
    private void showExitSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        View v = getLayoutInflater().inflate(R.layout.bottom_sheet_exit, null);
        sheet.setContentView(v);
        sheet.setCanceledOnTouchOutside(true);
        ((MaterialButton) v.findViewById(R.id.btnExitConfirm)).setOnClickListener(sv -> {
            sheet.dismiss();
            finishAffinity();
        });
        ((MaterialButton) v.findViewById(R.id.btnExitCancel)).setOnClickListener(sv -> sheet.dismiss());
        sheet.show();
    }

    private void setActiveTab(int activeIndex) {
        int accent    = getResources().getColor(R.color.accent,         getTheme());
        int secondary = getResources().getColor(R.color.text_secondary, getTheme());
        int primary   = getResources().getColor(R.color.text_primary,   getTheme());

        // Reset ALL to inactive
        ImageView[] icons  = {ivHomeIcon, ivNotesIcon, ivTimerIcon, ivStopwatchIcon, ivProfileIcon};
        TextView[]  labels = {tvHomeLabel, tvNotesLabel, tvTimerLabel, tvStopwatchLabel, tvProfileLabel};
        for (ImageView iv : icons)  { iv.setColorFilter(primary); iv.setAlpha(0.45f); }
        for (TextView  tv : labels) { tv.setTextColor(secondary); tv.setTypeface(null, android.graphics.Typeface.NORMAL); }

        // Activate selected
        icons[activeIndex].setColorFilter(accent);
        icons[activeIndex].setAlpha(1f);
        labels[activeIndex].setTextColor(accent);
        labels[activeIndex].setTypeface(null, android.graphics.Typeface.BOLD);
    }
}
