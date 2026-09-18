package com.example.remind;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddEditNoteActivity extends BaseActivity {

    // Palette of note colors (Google Keep style)
    private static final String[] COLORS = {
        "#FFFFFF", "#F28B82", "#FBBC04", "#FFF475",
        "#CCFF90", "#A8DAB5", "#CBF0F8", "#AECBFA",
        "#D7AEFB", "#FDCFE8", "#E6C9A8", "#E8EAED"
    };

    private EditText etTitle, etDesc;
    private ImageButton btnPin, btnDelete, btnReminder;
    private Chip chipReminder;
    private LinearLayout llColors;
    private android.view.View rootView; // for live color preview

    private CollectionReference notesRef;
    private String noteId;          // null = add mode
    private boolean isPinned = false;
    private String selectedColor = "#FFFFFF";
    private long reminderTime = 0;

    // Auto-save debounce
    private final android.os.Handler autoSaveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoSaveRunnable;
    private static final long AUTO_SAVE_DELAY_MS = 1500;

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("MMM d, yyyy  hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_note);

        rootView = findViewById(android.R.id.content);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle      = findViewById(R.id.etNoteTitle);
        etDesc       = findViewById(R.id.etNoteDesc);
        btnPin       = findViewById(R.id.btnPin);
        btnDelete    = findViewById(R.id.btnDelete);
        btnReminder  = findViewById(R.id.btnReminder);
        chipReminder = findViewById(R.id.chipReminder);
        llColors     = findViewById(R.id.llColors);
        MaterialButton btnSave = findViewById(R.id.btnSaveNote);
        ImageButton btnDownload = findViewById(R.id.btnDownload);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        notesRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("notes");

        noteId = getIntent().getStringExtra("noteId");

        buildColorPicker();

        if (noteId != null) {
            // Edit mode — load existing note
            loadNote();
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            btnDelete.setVisibility(View.GONE);
        }

        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            btnPin.setAlpha(isPinned ? 1f : 0.4f);
        });

        btnDelete.setOnClickListener(v -> confirmDelete());

        btnReminder.setOnClickListener(v -> pickReminderDateTime());

        chipReminder.setOnCloseIconClickListener(v -> {
            reminderTime = 0;
            chipReminder.setVisibility(View.GONE);
        });

        btnSave.setOnClickListener(v -> saveNote());
        btnDownload.setOnClickListener(v -> downloadNote());

        // Auto-save on text change
        android.text.TextWatcher autoSaveWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                scheduleAutoSave();
            }
        };
        etTitle.addTextChangedListener(autoSaveWatcher);
        etDesc.addTextChangedListener(autoSaveWatcher);
    }

    // ── Download / Share as TXT ────────────────────────────────────

    private void downloadNote() {
        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();

        if (title.isEmpty() && desc.isEmpty()) {
            Toast.makeText(this, "Nothing to download — note is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build file content
        String timestamp = new SimpleDateFormat("MMM d, yyyy  hh:mm a", Locale.getDefault())
                .format(new Date());
        String content = (title.isEmpty() ? "(No title)" : title)
                + "\n" + "─".repeat(40)
                + "\n\n" + desc
                + "\n\n─────────────────────────────────────────"
                + "\nSaved: " + timestamp
                + "\nApp: Remind";

        // Write to app cache dir (no permission needed)
        String safeTitle = title.isEmpty() ? "note" : title.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
        if (safeTitle.isEmpty()) safeTitle = "note";
        String fileName = safeTitle + "_" + System.currentTimeMillis() + ".txt";

        File file = new File(getCacheDir(), fileName);
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show();
            return;
        }

        // Share via FileProvider
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, title.isEmpty() ? "Note" : title);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Download / Share note as…"));
    }

    // ── Color picker ───────────────────────────────────────────────

    private void buildColorPicker() {
        float d = getResources().getDisplayMetrics().density;
        int size = (int)(36 * d);
        int margin = (int)(6 * d);

        for (String hex : COLORS) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(hex));
            gd.setStroke((int)(2 * d), Color.parseColor("#CCCCCC"));
            dot.setBackground(gd);

            dot.setOnClickListener(v -> {
                selectedColor = hex;
                // Apply background color immediately to the note screen
                applyNoteColor(hex);
                // Highlight selected dot
                for (int i = 0; i < llColors.getChildCount(); i++) {
                    View child = llColors.getChildAt(i);
                    GradientDrawable bg = (GradientDrawable) child.getBackground();
                    bg.setStroke((int)(2 * d), Color.parseColor("#CCCCCC"));
                }
                gd.setStroke((int)(3 * d), Color.parseColor("#E94560"));
                // Auto-save with new color
                scheduleAutoSave();
            });

            llColors.addView(dot);
        }
    }

    /** Apply note background color to the activity window */
    private void applyNoteColor(String hex) {
        try {
            int color = Color.parseColor(hex);
            // Apply to the root content view
            View content = findViewById(android.R.id.content);
            if (content != null) content.setBackgroundColor(color);
            // Also tint the toolbar area
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) toolbar.setBackgroundColor(color);
        } catch (Exception ignored) {}
    }

    // ── Auto-save ──────────────────────────────────────────────────

    private void scheduleAutoSave() {
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveRunnable = this::autoSaveNote;
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS);
    }

    private void autoSaveNote() {
        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();
        if (title.isEmpty() && desc.isEmpty()) return; // nothing to save yet

        long now = System.currentTimeMillis();
        String id = (noteId != null) ? noteId : notesRef.document().getId();
        if (noteId == null) noteId = id; // assign id on first auto-save

        Note note = new Note(id, title, desc, isPinned, selectedColor, now, reminderTime);
        notesRef.document(id).set(note); // silent save — no toast
    }

    // ── Load note (edit mode) ──────────────────────────────────────

    private void loadNote() {
        notesRef.document(noteId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            Note n = doc.toObject(Note.class);
            if (n == null) return;

            etTitle.setText(n.getTitle());
            etDesc.setText(n.getDescription());
            isPinned = n.isPinned();
            btnPin.setAlpha(isPinned ? 1f : 0.4f);
            reminderTime = n.getReminderTime();

            if (n.getColor() != null && !n.getColor().isEmpty()) {
                selectedColor = n.getColor();
                // Apply color immediately
                applyNoteColor(selectedColor);
                // Highlight the matching dot in the color picker
                highlightColorDot(selectedColor);
            }
            if (reminderTime > 0) {
                chipReminder.setText("⏰ " + SDF.format(new Date(reminderTime)));
                chipReminder.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Highlight the color dot matching the given hex */
    private void highlightColorDot(String hex) {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COLORS.length && i < llColors.getChildCount(); i++) {
            View child = llColors.getChildAt(i);
            GradientDrawable bg = (GradientDrawable) child.getBackground();
            if (COLORS[i].equalsIgnoreCase(hex)) {
                bg.setStroke((int)(3 * d), Color.parseColor("#E94560"));
            } else {
                bg.setStroke((int)(2 * d), Color.parseColor("#CCCCCC"));
            }
        }
    }

    // ── Save ───────────────────────────────────────────────────────

    private void saveNote() {
        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();

        if (title.isEmpty() && desc.isEmpty()) {
            Toast.makeText(this, "Note is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        String id = (noteId != null) ? noteId : notesRef.document().getId();

        Note note = new Note(id, title, desc, isPinned, selectedColor, now, reminderTime);

        notesRef.document(id).set(note)
                .addOnSuccessListener(v -> {
                    if (reminderTime > 0) scheduleReminder(id, title, reminderTime);
                    Toast.makeText(this, noteId == null ? "Note saved" : "Note updated",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Delete ─────────────────────────────────────────────────────

    private void confirmDelete() {
        ConfirmSheet.show(this,
                "Delete note?",
                "This note will be permanently deleted.",
                "Delete",
                () -> notesRef.document(noteId).delete()
                        .addOnSuccessListener(v -> finish()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
    }

    // ── Reminder ───────────────────────────────────────────────────

    private void pickReminderDateTime() {
        Calendar cal = Calendar.getInstance();
        android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(this, (dp, y, m, d) -> {
            cal.set(y, m, d);
            // Determine if selected date is today
            Calendar sel = Calendar.getInstance();
            sel.set(y, m, d, 0, 0, 0);
            sel.set(Calendar.MILLISECOND, 0);
            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);
            boolean isToday = sel.getTimeInMillis() == todayCal.getTimeInMillis();

            android.app.TimePickerDialog tpd = new android.app.TimePickerDialog(this, (tp, h, min) -> {
                // Validate: if today, time must be in the future
                if (isToday) {
                    Calendar now = Calendar.getInstance();
                    if (h < now.get(Calendar.HOUR_OF_DAY) ||
                            (h == now.get(Calendar.HOUR_OF_DAY) && min <= now.get(Calendar.MINUTE))) {
                        Toast.makeText(this, "Please select a future time for today", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, 0);
                reminderTime = cal.getTimeInMillis();
                chipReminder.setText("⏰ " + SDF.format(new Date(reminderTime)));
                chipReminder.setVisibility(View.VISIBLE);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
            tpd.show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        // Restrict: today to today+3 years
        dpd.getDatePicker().setMinDate(cal.getTimeInMillis());
        Calendar maxCal = Calendar.getInstance();
        maxCal.add(Calendar.YEAR, 3);
        dpd.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
        dpd.show();
    }

    private void scheduleReminder(String noteId, String title, long triggerAt) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, NoteReminderReceiver.class);
        i.putExtra("noteId", noteId);
        i.putExtra("title", title);
        PendingIntent pi = PendingIntent.getBroadcast(this, noteId.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
    }
}
