package com.example.remind;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends BaseActivity {

    private FirebaseUser user;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        user = FirebaseAuth.getInstance().getCurrentUser();
        db   = FirebaseFirestore.getInstance();

        TextView tvCurrentName  = findViewById(R.id.tvCurrentName);
        TextView tvCurrentEmail = findViewById(R.id.tvCurrentEmail);

        if (user != null) {
            String name = user.getDisplayName();
            tvCurrentName.setText((name != null && !name.isEmpty()) ? name : user.getEmail().split("@")[0]);
            tvCurrentEmail.setText(user.getEmail());
        }

        findViewById(R.id.llEditName).setOnClickListener(v -> showEditNameSheet(tvCurrentName));
        findViewById(R.id.llChangePassword).setOnClickListener(v -> showChangePasswordSheet());
        findViewById(R.id.llSignOut).setOnClickListener(v -> signOut());
        findViewById(R.id.llDeleteAccount).setOnClickListener(v -> showDownloadDataSheet());
    }

    // ── Sign Out ──────────────────────────────────────────────────────────

    private void signOut() {
        ConfirmSheet.show(this,
                "Sign Out",
                "Are you sure you want to sign out?",
                "Sign Out",
                () -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(this, AuthenticationActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                });
    }

    // ── Edit Name ─────────────────────────────────────────────────────────

    private void showEditNameSheet(TextView tvCurrentName) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.view.View v = getLayoutInflater().inflate(R.layout.bottom_sheet_input, null);
        sheet.setContentView(v);
        ((TextView) v.findViewById(R.id.tvSheetTitle)).setText("Display Name");
        TextInputEditText etInput = v.findViewById(R.id.etInput);
        etInput.setHint("Enter display name");
        etInput.setText(tvCurrentName.getText());
        ((MaterialButton) v.findViewById(R.id.btnSheetSave)).setOnClickListener(sv -> {
            String name = etInput.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show(); return; }
            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder().setDisplayName(name).build();
            user.updateProfile(req).addOnSuccessListener(u -> {
                tvCurrentName.setText(name);
                // Also update Firestore
                db.collection("users").document(user.getUid())
                        .update("fullName", name);
                Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                sheet.dismiss();
            }).addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        ((MaterialButton) v.findViewById(R.id.btnSheetCancel)).setOnClickListener(sv -> sheet.dismiss());
        sheet.show();
    }

    // ── Change Password ───────────────────────────────────────────────────

    private void showChangePasswordSheet() {
        if (user == null || user.getEmail() == null) return;
        String email = user.getEmail();

        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.view.View v = getLayoutInflater().inflate(R.layout.bottom_sheet_input, null);
        sheet.setContentView(v);
        ((TextView) v.findViewById(R.id.tvSheetTitle)).setText("Reset Password");

        TextInputEditText etInput = v.findViewById(R.id.etInput);
        etInput.setText(email);
        etInput.setEnabled(false);
        etInput.setHint("Your email");

        MaterialButton btnSave = v.findViewById(R.id.btnSheetSave);
        btnSave.setText("Send Reset Link");
        btnSave.setOnClickListener(sv -> {
            btnSave.setEnabled(false);
            btnSave.setText("Sending...");
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(u -> {
                    Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show();
                    sheet.dismiss();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Send Reset Link");
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        });
        ((MaterialButton) v.findViewById(R.id.btnSheetCancel)).setOnClickListener(sv -> sheet.dismiss());
        sheet.show();
    }

    // ── STEP 1: Download data? ────────────────────────────────────────────

    private void showDownloadDataSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.widget.LinearLayout ll = buildSheetLayout();

        // Icon
        android.widget.ImageView icon = new android.widget.ImageView(this);
        android.widget.LinearLayout.LayoutParams ilp =
                new android.widget.LinearLayout.LayoutParams(dp(48), dp(48));
        ilp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        ilp.bottomMargin = dp(12);
        icon.setLayoutParams(ilp);
        icon.setImageResource(android.R.drawable.ic_menu_save);
        icon.setColorFilter(getColor(R.color.accent), android.graphics.PorterDuff.Mode.SRC_IN);
        ll.addView(icon);

        addTitle(ll, "Download Your Data");
        addBody(ll, "Before deleting your account, would you like to download a PDF of all your tasks and profile info?");

        MaterialButton btnYes = makeBtn("Yes, Download PDF", true);
        MaterialButton btnNo  = makeBtn("No, Skip", false);

        ll.addView(btnYes);
        ll.addView(btnNo);
        sheet.setContentView(ll);

        btnYes.setOnClickListener(v -> {
            sheet.dismiss();
            btnYes.setEnabled(false);
            generateAndSharePdf(() -> showWarningSheet());
        });
        btnNo.setOnClickListener(v -> {
            sheet.dismiss();
            showWarningSheet();
        });
        sheet.show();
    }

    // ── PDF Generation ────────────────────────────────────────────────────

    private void generateAndSharePdf(Runnable onDone) {
        if (user == null) return;
        Toast.makeText(this, "Preparing PDF...", Toast.LENGTH_SHORT).show();

        String uid = user.getUid();
        db.collection("users").document(uid).get().addOnSuccessListener(profileDoc -> {
            String fullName  = profileDoc.getString("fullName");
            String email     = profileDoc.getString("email");
            String bio       = profileDoc.getString("bio");
            String phone     = profileDoc.getString("phone");
            String provider  = profileDoc.getString("provider");
            Object createdAt = profileDoc.get("createdAt");
            if (fullName == null) fullName = user.getDisplayName() != null ? user.getDisplayName() : "User";
            if (email    == null) email    = user.getEmail() != null ? user.getEmail() : "";
            if (bio      == null) bio      = "";
            if (phone    == null) phone    = "";
            if (provider == null) provider = "";

            final String fName = fullName, fEmail = email, fBio = bio,
                         fPhone = phone, fProvider = provider,
                         fCreated = createdAt != null ? createdAt.toString() : "";

            // Fetch tasks → notes → timers
            db.collection("users").document(uid).collection("tasks").get()
                .addOnSuccessListener(taskSnap ->
                    db.collection("users").document(uid).collection("notes").get()
                        .addOnSuccessListener(noteSnap ->
                            db.collection("users").document(uid).collection("timers").get()
                                .addOnSuccessListener(timerSnap -> {
                                    try {
                                        File pdf = buildPdf(fName, fEmail, fBio, fPhone, fProvider,
                                                fCreated, taskSnap, noteSnap, timerSnap);
                                        sharePdf(pdf, onDone);
                                    } catch (Exception e) {
                                        Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        onDone.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // timers optional — proceed without
                                    try {
                                        File pdf = buildPdf(fName, fEmail, fBio, fPhone, fProvider,
                                                fCreated, taskSnap, noteSnap, null);
                                        sharePdf(pdf, onDone);
                                    } catch (Exception ex) {
                                        Toast.makeText(this, "PDF error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                                        onDone.run();
                                    }
                                })
                        )
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Could not fetch notes", Toast.LENGTH_SHORT).show();
                            onDone.run();
                        })
                )
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not fetch tasks", Toast.LENGTH_SHORT).show();
                    onDone.run();
                });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Could not fetch profile", Toast.LENGTH_SHORT).show();
            onDone.run();
        });
    }

    /** Start a new PDF page and reset y position */
    private Canvas newPage(PdfDocument doc, PdfDocument.Page[] pageRef, Canvas[] cRef,
                           int[] pageNum, int[] yRef, int pageW, int pageH) {
        doc.finishPage(pageRef[0]);
        PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum[0]++).create();
        pageRef[0] = doc.startPage(pi);
        cRef[0] = pageRef[0].getCanvas();
        yRef[0] = 48;
        return cRef[0];
    }

    private File buildPdf(String name, String email, String bio, String phone,
                          String provider, String createdAt,
                          com.google.firebase.firestore.QuerySnapshot tasks,
                          com.google.firebase.firestore.QuerySnapshot notes,
                          com.google.firebase.firestore.QuerySnapshot timers) throws Exception {

        PdfDocument doc = new PdfDocument();
        int pageW = 595, pageH = 842;

        // ── Paints ──────────────────────────────────────────────────
        Paint pAccent = new Paint();
        pAccent.setColor(Color.parseColor("#E94560"));
        pAccent.setTextSize(20f);
        pAccent.setFakeBoldText(true);

        Paint pSection = new Paint();
        pSection.setColor(Color.parseColor("#1A1A2E"));
        pSection.setTextSize(13f);
        pSection.setFakeBoldText(true);

        Paint pLabel = new Paint();
        pLabel.setColor(Color.parseColor("#9999AA"));
        pLabel.setTextSize(10f);

        Paint pValue = new Paint();
        pValue.setColor(Color.parseColor("#1A1A2E"));
        pValue.setTextSize(12f);

        Paint pSmall = new Paint();
        pSmall.setColor(Color.parseColor("#555566"));
        pSmall.setTextSize(11f);

        Paint pLine = new Paint();
        pLine.setColor(Color.parseColor("#E0E0E0"));
        pLine.setStrokeWidth(1f);

        Paint pAccentLine = new Paint();
        pAccentLine.setColor(Color.parseColor("#E94560"));
        pAccentLine.setStrokeWidth(2f);

        Paint pBg = new Paint();
        pBg.setColor(Color.parseColor("#FFF5F7"));

        int margin = 48;
        int timerCount = timers != null ? timers.size() : 0;
        int[] yRef = {0};

        PdfDocument.Page[] pageRef = {null};
        Canvas[] cRef = {null};
        int[] pageNum = {1};

        PdfDocument.PageInfo pi0 = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum[0]++).create();
        pageRef[0] = doc.startPage(pi0);
        cRef[0] = pageRef[0].getCanvas();
        yRef[0] = 48;
        Canvas c = cRef[0];

        String exportDate = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());

        // ── Header banner ────────────────────────────────────────────
        Paint pBanner = new Paint();
        pBanner.setColor(Color.parseColor("#E94560"));
        c.drawRect(new android.graphics.RectF(0, 0, pageW, 72), pBanner);

        Paint pWhiteBold = new Paint();
        pWhiteBold.setColor(Color.WHITE);
        pWhiteBold.setTextSize(22f);
        pWhiteBold.setFakeBoldText(true);
        c.drawText("Remind — Account Data Export", margin, 44, pWhiteBold);

        Paint pWhiteSmall = new Paint();
        pWhiteSmall.setColor(Color.parseColor("#FFCCCC"));
        pWhiteSmall.setTextSize(10f);
        c.drawText("Exported on: " + exportDate + "   |   " + email, margin, 64, pWhiteSmall);

        yRef[0] = 96;

        // ── SECTION 1: Account Information ──────────────────────────
        c.drawText("ACCOUNT INFORMATION", margin, yRef[0], pSection);
        yRef[0] += 4;
        c.drawLine(margin, yRef[0], pageW - margin, yRef[0], pAccentLine);
        yRef[0] += 18;

        String[][] profileFields = {
            {"Full Name",       name},
            {"Email Address",   email},
            {"Phone Number",    phone.isEmpty()     ? "Not provided"  : phone},
            {"Sign-in Method",  provider.isEmpty()  ? "Not provided"  : provider},
            {"Account Created", createdAt.isEmpty() ? "Not available" : createdAt},
            {"Bio",             bio.isEmpty()       ? "Not provided"  : bio},
        };
        for (String[] field : profileFields) {
            c.drawText(field[0].toUpperCase(), margin, yRef[0], pLabel);
            yRef[0] += 15;
            c.drawText(field[1], margin + 4, yRef[0], pValue);
            yRef[0] += 22;
        }
        yRef[0] += 10;

        // ── SECTION 2: Statistics ────────────────────────────────────
        if (yRef[0] > pageH - 100) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
        c.drawText("STATISTICS", margin, yRef[0], pSection);
        yRef[0] += 4;
        c.drawLine(margin, yRef[0], pageW - margin, yRef[0], pAccentLine);
        yRef[0] += 18;

        int boxW3 = (pageW - margin * 2 - 24) / 3;
        drawStatBox(c, margin,                    yRef[0], boxW3, 56, "Total Tasks",  String.valueOf(tasks  != null ? tasks.size()  : 0), pBg, pAccent, pSmall);
        drawStatBox(c, margin + boxW3 + 12,       yRef[0], boxW3, 56, "Total Notes",  String.valueOf(notes  != null ? notes.size()  : 0), pBg, pAccent, pSmall);
        drawStatBox(c, margin + (boxW3 + 12) * 2, yRef[0], boxW3, 56, "Total Timers", String.valueOf(timerCount),                         pBg, pAccent, pSmall);
        yRef[0] += 72;

        // ── SECTION 3: Tasks ─────────────────────────────────────────
        if (yRef[0] > pageH - 100) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
        c.drawText("TASKS (" + (tasks != null ? tasks.size() : 0) + ")", margin, yRef[0], pSection);
        yRef[0] += 4;
        c.drawLine(margin, yRef[0], pageW - margin, yRef[0], pAccentLine);
        yRef[0] += 18;

        if (tasks == null || tasks.isEmpty()) {
            c.drawText("No tasks found.", margin, yRef[0], pSmall);
            yRef[0] += 24;
        } else {
            int idx = 1;
            for (QueryDocumentSnapshot t : tasks) {
                if (yRef[0] > pageH - 80) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
                String taskName = t.getString("task");
                String taskDate = t.getString("date");
                String taskTime = t.getString("time");
                String taskCat  = t.getString("category");
                if (taskName == null) taskName = "(no name)";

                Paint rowPaint = new Paint();
                rowPaint.setColor(idx % 2 == 0 ? Color.parseColor("#F9F9F9") : Color.WHITE);
                c.drawRect(new android.graphics.RectF(margin - 4, yRef[0] - 13, pageW - margin + 4, yRef[0] + 32), rowPaint);

                Paint pIdx = new Paint();
                pIdx.setColor(Color.parseColor("#E94560"));
                pIdx.setTextSize(11f); pIdx.setFakeBoldText(true);
                c.drawText(String.valueOf(idx), margin, yRef[0], pIdx);

                Paint pTaskName = new Paint();
                pTaskName.setColor(Color.parseColor("#1A1A2E"));
                pTaskName.setTextSize(12f); pTaskName.setFakeBoldText(true);
                c.drawText(taskName, margin + 22, yRef[0], pTaskName);
                yRef[0] += 17;

                c.drawText("  Date: " + (taskDate != null ? taskDate : "—")
                        + "   Time: " + (taskTime != null ? taskTime : "—")
                        + "   Category: " + (taskCat != null ? taskCat : "—"),
                        margin, yRef[0], pSmall);
                yRef[0] += 22;
                idx++;
            }
        }
        yRef[0] += 10;

        // ── SECTION 4: Notes ─────────────────────────────────────────
        if (yRef[0] > pageH - 120) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
        c.drawText("NOTES (" + (notes != null ? notes.size() : 0) + ")", margin, yRef[0], pSection);
        yRef[0] += 4;
        c.drawLine(margin, yRef[0], pageW - margin, yRef[0], pAccentLine);
        yRef[0] += 18;

        if (notes == null || notes.isEmpty()) {
            c.drawText("No notes found.", margin, yRef[0], pSmall);
            yRef[0] += 24;
        } else {
            SimpleDateFormat noteSdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            int idx = 1;
            for (QueryDocumentSnapshot n : notes) {
                if (yRef[0] > pageH - 100) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
                String nTitle   = n.getString("title");
                String nDesc    = n.getString("description");
                Boolean nPinned = n.getBoolean("pinned");
                Long nTs        = n.getLong("timestamp");
                Long nReminder  = n.getLong("reminderTime");
                String nColor   = n.getString("color");

                if (nTitle == null || nTitle.isEmpty()) nTitle = "(No title)";
                if (nDesc  == null) nDesc = "";
                String tsStr  = nTs != null ? noteSdf.format(new Date(nTs)) : "—";
                String remStr = (nReminder != null && nReminder > 0) ? noteSdf.format(new Date(nReminder)) : "None";
                String pinStr = Boolean.TRUE.equals(nPinned) ? "  [PINNED]" : "";

                Paint noteBgPaint = new Paint();
                noteBgPaint.setColor(idx % 2 == 0 ? Color.parseColor("#F9F9F9") : Color.WHITE);
                c.drawRect(new android.graphics.RectF(margin - 4, yRef[0] - 13, pageW - margin + 4, yRef[0] + 46), noteBgPaint);

                if (nColor != null && !nColor.isEmpty() && !nColor.equals("#FFFFFF")) {
                    try {
                        Paint dotPaint = new Paint();
                        dotPaint.setColor(Color.parseColor(nColor));
                        c.drawCircle(margin + 5, yRef[0] - 3, 5, dotPaint);
                    } catch (Exception ignored) {}
                }

                Paint pNoteTitle = new Paint();
                pNoteTitle.setColor(Color.parseColor("#1A1A2E"));
                pNoteTitle.setTextSize(12f); pNoteTitle.setFakeBoldText(true);
                c.drawText(idx + ". " + nTitle + pinStr, margin + 16, yRef[0], pNoteTitle);
                yRef[0] += 17;

                if (!nDesc.isEmpty()) {
                    String preview = nDesc.length() > 100 ? nDesc.substring(0, 100) + "…" : nDesc;
                    c.drawText("   " + preview, margin, yRef[0], pSmall);
                    yRef[0] += 16;
                }
                c.drawText("   Saved: " + tsStr + "   Reminder: " + remStr, margin, yRef[0], pLabel);
                yRef[0] += 22;
                idx++;
            }
        }
        yRef[0] += 10;

        // ── SECTION 5: Timers ─────────────────────────────────────────
        if (yRef[0] > pageH - 120) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
        c.drawText("TIMERS (" + timerCount + ")", margin, yRef[0], pSection);
        yRef[0] += 4;
        c.drawLine(margin, yRef[0], pageW - margin, yRef[0], pAccentLine);
        yRef[0] += 18;

        if (timers == null || timers.isEmpty()) {
            c.drawText("No timers found.", margin, yRef[0], pSmall);
            yRef[0] += 24;
        } else {
            int idx = 1;
            for (QueryDocumentSnapshot t : timers) {
                if (yRef[0] > pageH - 100) { c = newPage(doc, pageRef, cRef, pageNum, yRef, pageW, pageH); }
                String tTitle    = t.getString("title");
                Long   tDuration = t.getLong("durationMillis");
                String tState    = t.getString("state");
                Object tCreated  = t.get("createdAt");

                if (tTitle == null) tTitle = "(No title)";
                if (tState == null) tState = "idle";

                String durStr = "—";
                if (tDuration != null && tDuration > 0) {
                    long totalSec = tDuration / 1000;
                    long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
                    durStr = String.format("%02d:%02d:%02d", h, m, s);
                }

                int stateColor;
                switch (tState) {
                    case "running":   stateColor = Color.parseColor("#4CAF50"); break;
                    case "paused":    stateColor = Color.parseColor("#FF9800"); break;
                    case "completed": stateColor = Color.parseColor("#E94560"); break;
                    default:          stateColor = Color.parseColor("#9999AA"); break;
                }

                Paint rowPaint = new Paint();
                rowPaint.setColor(idx % 2 == 0 ? Color.parseColor("#F9F9F9") : Color.WHITE);
                c.drawRect(new android.graphics.RectF(margin - 4, yRef[0] - 13, pageW - margin + 4, yRef[0] + 36), rowPaint);

                Paint dotPaint = new Paint();
                dotPaint.setColor(stateColor);
                c.drawCircle(margin + 5, yRef[0] - 3, 5, dotPaint);

                Paint pTTitle = new Paint();
                pTTitle.setColor(Color.parseColor("#1A1A2E"));
                pTTitle.setTextSize(12f); pTTitle.setFakeBoldText(true);
                c.drawText(idx + ". " + tTitle, margin + 16, yRef[0], pTTitle);
                yRef[0] += 17;

                c.drawText("   Duration: " + durStr
                        + "   Status: " + tState.toUpperCase()
                        + "   Created: " + (tCreated != null ? tCreated.toString() : "—"),
                        margin, yRef[0], pSmall);
                yRef[0] += 22;
                idx++;
            }
        }

        // ── Footer ───────────────────────────────────────────────────
        yRef[0] = pageH - 32;
        c.drawLine(margin, yRef[0] - 8, pageW - margin, yRef[0] - 8, pLine);
        Paint pFooter = new Paint();
        pFooter.setColor(Color.parseColor("#9999AA"));
        pFooter.setTextSize(9f);
        c.drawText("Generated by Remind App  •  " + exportDate, margin, yRef[0], pFooter);

        doc.finishPage(pageRef[0]);

        File outFile = new File(getCacheDir(), "remind_data_export.pdf");
        FileOutputStream fos = new FileOutputStream(outFile);
        doc.writeTo(fos);
        fos.close();
        doc.close();
        return outFile;
    }

    private void drawStatBox(Canvas c, int x, int y, int w, int h,
                             String label, String value,
                             Paint bgPaint, Paint valuePaint, Paint labelPaint) {
        android.graphics.RectF rect = new android.graphics.RectF(x, y, x + w, y + h);
        c.drawRoundRect(rect, 8, 8, bgPaint);

        Paint vp = new Paint(valuePaint);
        vp.setTextSize(22f);
        vp.setFakeBoldText(true);
        c.drawText(value, x + 14, y + 30, vp);

        Paint lp = new Paint(labelPaint);
        lp.setTextSize(10f);
        c.drawText(label, x + 14, y + 46, lp);
    }

    private void sharePdf(File pdf, Runnable onDone) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".provider", pdf);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Remind — My Data Export");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Save or Share PDF"));

        // After sharing, show warning sheet
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(onDone::run, 1500);
    }

    // ── STEP 2: Warning sheet ─────────────────────────────────────────────

    private void showWarningSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.widget.LinearLayout ll = buildSheetLayout();

        // Warning icon
        android.widget.ImageView icon = new android.widget.ImageView(this);
        android.widget.LinearLayout.LayoutParams ilp =
                new android.widget.LinearLayout.LayoutParams(dp(48), dp(48));
        ilp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        ilp.bottomMargin = dp(12);
        icon.setLayoutParams(ilp);
        icon.setImageResource(android.R.drawable.ic_dialog_alert);
        icon.setColorFilter(getColor(R.color.accent), android.graphics.PorterDuff.Mode.SRC_IN);
        ll.addView(icon);

        addTitle(ll, "⚠ Delete Account");
        addBody(ll, "Your account and all data will be permanently deleted.\n\nThis email will not be allowed to register again.\n\nAre you sure you want to continue?");

        MaterialButton btnDelete = makeBtn("Yes, Delete My Account", true);
        MaterialButton btnKeep   = makeBtn("No, Keep My Account", false);

        ll.addView(btnDelete);
        ll.addView(btnKeep);
        sheet.setContentView(ll);

        btnKeep.setOnClickListener(v -> sheet.dismiss());
        btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            performAccountDeletion();
        });
        sheet.show();
    }

    // ── Account Deletion ──────────────────────────────────────────────────

    private void performAccountDeletion() {
        if (user == null) return;
        String uid   = user.getUid();
        String email = user.getEmail() != null ? user.getEmail().toLowerCase(Locale.ROOT) : "";

        Toast.makeText(this, "Deleting account...", Toast.LENGTH_SHORT).show();

        // 1. Save to blocklist in Firestore so login can check it
        Map<String, Object> block = new HashMap<>();
        block.put("email",     email);
        block.put("deletedAt", Timestamp.now());
        block.put("uid",       uid);

        db.collection("deletedAccounts").document(uid)
            .set(block)
            .addOnCompleteListener(t -> {
                // 2. Delete all user tasks
                db.collection("users").document(uid).collection("tasks")
                    .get().addOnSuccessListener(snap -> {
                        for (QueryDocumentSnapshot d : snap) d.getReference().delete();
                    });

                // 3. Delete user profile doc
                db.collection("users").document(uid).delete();

                // 4. Delete Firebase Auth account
                user.delete().addOnCompleteListener(authTask -> {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, AuthenticationActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                });
            });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private android.widget.LinearLayout buildSheetLayout() {
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(dp(24), dp(24), dp(24), dp(32));
        ll.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_bottom_sheet));

        // Drag handle
        android.widget.LinearLayout hw = new android.widget.LinearLayout(this);
        hw.setGravity(android.view.Gravity.CENTER);
        hw.setPadding(0, 0, 0, dp(16));
        android.view.View handle = new android.view.View(this);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(dp(40), dp(4));
        handle.setLayoutParams(hlp);
        handle.setBackgroundColor(getColor(R.color.divider));
        hw.addView(handle);
        ll.addView(hw);
        return ll;
    }

    private void addTitle(android.widget.LinearLayout ll, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(getColor(R.color.text_primary));
        tv.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        tv.setLayoutParams(lp);
        ll.addView(tv);
    }

    private void addBody(android.widget.LinearLayout ll, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(getColor(R.color.text_secondary));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setLineSpacing(dp(4), 1f);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(24);
        tv.setLayoutParams(lp);
        ll.addView(tv);
    }

    private MaterialButton makeBtn(String label, boolean filled) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setCornerRadius(dp(14));
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);
        if (filled) {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
            btn.setTextColor(getColor(R.color.white));
        } else {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
            btn.setStrokeWidth(dp(1));
            btn.setTextColor(getColor(R.color.accent));
        }
        return btn;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
