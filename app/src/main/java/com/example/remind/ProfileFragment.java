package com.example.remind;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends androidx.fragment.app.Fragment {

    private static final String TAG = "ProfileFragment";

    private ImageView ivProfile_image;
    private FirebaseUser user;
    private DocumentReference userDoc;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        userDoc = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid());

        ivProfile_image        = view.findViewById(R.id.ivProfile_image);
        TextView profileName   = view.findViewById(R.id.profile_name);
        TextView tvUserEmail   = view.findViewById(R.id.tvUserEmail);
        TextView tvInfoEmail   = view.findViewById(R.id.tvInfoEmail);
        TextView tvTaskCount   = view.findViewById(R.id.tvTaskCount);
        TextView tvNoteCount   = view.findViewById(R.id.tvNoteCount);
        TextView tvBio         = view.findViewById(R.id.tvBio);
        TextView tvTimerCount  = view.findViewById(R.id.tvTimerCount);
        LinearLayout llChangeImage = view.findViewById(R.id.llChangeImage);
        LinearLayout llBioRow      = view.findViewById(R.id.llBioRow);

        String email = user.getEmail();
        String name  = user.getDisplayName();
        if (name == null || name.isEmpty())
            name = email != null ? email.split("@")[0] : "User";
        profileName.setText(name);
        tvUserEmail.setText(email);
        tvInfoEmail.setText(email);

        userDoc.collection("tasks").get()
                .addOnSuccessListener(snap -> tvTaskCount.setText(snap.size() + " tasks"));

        userDoc.collection("notes").get()
                .addOnSuccessListener(snap -> tvNoteCount.setText(snap.size() + " notes"));

        userDoc.collection("timers").get()
                .addOnSuccessListener(snap -> tvTimerCount.setText(snap.size() + " timers"));

        loadFromFirestore(tvBio);

        // Long-press on avatar = show options sheet
        llChangeImage.setOnClickListener(v -> showImageOptionsSheet());
        ivProfile_image.setOnClickListener(v -> showImageOptionsSheet());

        llBioRow.setOnClickListener(v -> showBioSheet(tvBio));
        view.findViewById(R.id.llTerms).setOnClickListener(v -> TermsHelper.show(requireContext()));
        view.findViewById(R.id.llSettings).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(), SettingsActivity.class)));
    }

    // ── Load from Firestore ───────────────────────────────────────────────

    private void loadFromFirestore(TextView tvBio) {
        userDoc.get()
            .addOnSuccessListener(doc -> {
                if (!isAdded()) return;
                if (!doc.exists()) return;
                String bio = doc.getString("bio");
                if (bio != null && !bio.isEmpty()) {
                    tvBio.setText(bio);
                    tvBio.setTextColor(requireContext().getColor(R.color.text_primary));
                }
                String b64 = doc.getString("profileImage");
                if (b64 != null && !b64.isEmpty()) showBase64Image(b64);
            })
            .addOnFailureListener(e -> Log.e(TAG, "Load failed: " + e.getMessage()));
    }

    // ── Image options bottom sheet ────────────────────────────────────────

    private void showImageOptionsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);

        android.widget.LinearLayout ll = new android.widget.LinearLayout(requireContext());
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(0, 24, 0, 48);
        ll.setBackground(androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet));

        // Drag handle
        android.widget.LinearLayout handleWrap = new android.widget.LinearLayout(requireContext());
        handleWrap.setGravity(android.view.Gravity.CENTER);
        handleWrap.setPadding(0, 0, 0, 24);
        android.view.View handle = new android.view.View(requireContext());
        android.widget.LinearLayout.LayoutParams hlp = new android.widget.LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(4));
        handle.setLayoutParams(hlp);
        handle.setBackgroundColor(requireContext().getColor(R.color.divider));
        handleWrap.addView(handle);
        ll.addView(handleWrap);

        // Title
        android.widget.TextView title = new android.widget.TextView(requireContext());
        title.setText("Profile Photo");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(requireContext().getColor(R.color.text_primary));
        title.setPadding(dpToPx(24), 0, dpToPx(24), dpToPx(16));
        ll.addView(title);

        // Choose from Gallery
        ll.addView(makeSheetRow(android.R.drawable.ic_menu_gallery, "Choose from Gallery", () -> {
            sheet.dismiss();
            pickImageLauncher.launch("image/*");
        }));

        // Delete Photo
        ll.addView(makeSheetRow(android.R.drawable.ic_menu_delete, "Delete Photo", () -> {
            sheet.dismiss();
            deletePhoto();
        }));

        sheet.setContentView(ll);
        sheet.show();
    }

    private android.widget.LinearLayout makeSheetRow(int iconRes, String label, Runnable action) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(24), dpToPx(14), dpToPx(24), dpToPx(14));
        row.setBackground(getAttrDrawable(android.R.attr.selectableItemBackground));

        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(dpToPx(22), dpToPx(22));
        ilp.setMarginEnd(dpToPx(16));
        icon.setLayoutParams(ilp);
        icon.setImageResource(iconRes);
        icon.setColorFilter(requireContext().getColor(R.color.text_secondary),
                android.graphics.PorterDuff.Mode.SRC_IN);

        android.widget.TextView tv = new android.widget.TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(requireContext().getColor(
                label.equals("Delete Photo") ? R.color.accent : R.color.text_primary));

        row.addView(icon);
        row.addView(tv);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private android.graphics.drawable.Drawable getAttrDrawable(int attr) {
        int[] attrs = { attr };
        android.content.res.TypedArray ta = requireContext().obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ── Gallery pick → uCrop ──────────────────────────────────────────────

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) startCrop(uri); });

    private void startCrop(Uri sourceUri) {
        Uri destUri = Uri.fromFile(new File(requireContext().getCacheDir(), "cropped_profile.jpg"));

        UCrop.of(sourceUri, destUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(400, 400)
                .withOptions(buildCropOptions())
                .start(requireContext(), this);
    }

    private UCrop.Options buildCropOptions() {
        UCrop.Options opts = new UCrop.Options();
        opts.setCircleDimmedLayer(true);
        opts.setShowCropGrid(false);
        opts.setShowCropFrame(true);
        opts.setToolbarTitle("Crop Photo");
        opts.setToolbarColor(requireContext().getColor(R.color.bg_card));
        opts.setStatusBarColor(requireContext().getColor(R.color.bg_dark));
        opts.setToolbarWidgetColor(requireContext().getColor(R.color.text_primary));
        opts.setActiveControlsWidgetColor(requireContext().getColor(R.color.accent));
        opts.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        opts.setCompressionQuality(85);
        return opts;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK && data != null) {
            Uri croppedUri = UCrop.getOutput(data);
            if (croppedUri != null) saveToFirestore(croppedUri);
        } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
            Throwable err = UCrop.getError(data);
            Toast.makeText(getContext(), "Crop error: " + (err != null ? err.getMessage() : "unknown"), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Save cropped image to Firestore as Base64 ─────────────────────────

    private void saveToFirestore(Uri uri) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(getContext(), "Saving...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bitmap == null) throw new Exception("Could not decode image");

                // Scale to max 300px
                bitmap = scaleBitmap(bitmap, 300);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                // Show immediately in UI
                final Bitmap finalBmp = bitmap;
                requireActivity().runOnUiThread(() -> {
                    ivProfile_image.clearColorFilter();
                    ivProfile_image.setImageBitmap(finalBmp);
                });

                Map<String, Object> data = new HashMap<>();
                data.put("profileImage", b64);
                userDoc.set(data, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Photo saved", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Save failed: " + e.getMessage());
                        requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    });

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── Delete photo ──────────────────────────────────────────────────────

    private void deletePhoto() {
        Map<String, Object> data = new HashMap<>();
        data.put("profileImage", "");
        userDoc.set(data, SetOptions.merge())
            .addOnSuccessListener(v -> {
                if (!isAdded()) return;
                ivProfile_image.setImageResource(R.drawable.ic_profile);
                ivProfile_image.setColorFilter(
                        requireContext().getColor(R.color.text_primary),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                Toast.makeText(getContext(), "Photo removed", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Bitmap scaleBitmap(Bitmap src, int maxPx) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = maxPx / (float) Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    private void showBase64Image(String b64) {
        if (!isAdded()) return;
        byte[] bytes = Base64.decode(b64, Base64.NO_WRAP);
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bmp != null) {
            ivProfile_image.clearColorFilter();
            ivProfile_image.setImageBitmap(bmp);
        }
    }

    // ── Bio sheet ─────────────────────────────────────────────────────────

    private void showBioSheet(TextView tvBio) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.BottomSheetStyle);

        android.widget.LinearLayout ll = new android.widget.LinearLayout(requireContext());
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        ll.setBackground(androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet));

        android.widget.TextView title = new android.widget.TextView(requireContext());
        title.setText("Bio");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(requireContext().getColor(R.color.text_primary));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dpToPx(16));

        android.widget.EditText etBio = new android.widget.EditText(requireContext());
        etBio.setHint("Write something about yourself...");
        etBio.setTextColor(requireContext().getColor(R.color.text_primary));
        etBio.setHintTextColor(requireContext().getColor(R.color.text_hint));
        String current = tvBio.getText().toString();
        etBio.setText(current.equals("Tap to add bio") ? "" : current);
        etBio.setBackground(null);
        etBio.setMinLines(3);

        com.google.android.material.button.MaterialButton btnSave =
                new com.google.android.material.button.MaterialButton(requireContext());
        btnSave.setText("Save");
        btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.accent)));
        btnSave.setTextColor(requireContext().getColor(R.color.white));
        btnSave.setCornerRadius(dpToPx(16));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(16);
        btnSave.setLayoutParams(lp);

        ll.addView(title);
        ll.addView(etBio);
        ll.addView(btnSave);
        sheet.setContentView(ll);

        btnSave.setOnClickListener(v -> {
            String bio = etBio.getText().toString().trim();
            tvBio.setText(bio.isEmpty() ? "Tap to add bio" : bio);
            tvBio.setTextColor(requireContext().getColor(
                    bio.isEmpty() ? R.color.text_hint : R.color.text_primary));
            Map<String, Object> data = new HashMap<>();
            data.put("bio", bio);
            userDoc.set(data, SetOptions.merge())
                    .addOnFailureListener(e -> Log.e(TAG, "Bio save failed: " + e.getMessage()));
            sheet.dismiss();
        });
        sheet.show();
    }

}
