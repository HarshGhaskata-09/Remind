package com.example.remind;

import androidx.activity.OnBackPressedCallback;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class RegistarActivity extends BaseActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private void goHome() {
        Intent i = new Intent(this, HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            goHome();
            return;
        }

        setContentView(R.layout.activity_registar);

        TextInputEditText edFullName    = findViewById(R.id.edFullName);
        TextInputEditText edPhone       = findViewById(R.id.edPhone);
        TextInputEditText edEmail       = findViewById(R.id.edEmail);
        TextInputEditText edPassword    = findViewById(R.id.edPassword);
        TextInputEditText edConPassword = findViewById(R.id.edConPassword);
        CheckBox cbTerms                = findViewById(R.id.cbTerms);
        TextView tvTermsLink            = findViewById(R.id.tvTermsLink);
        MaterialButton btnRegister      = findViewById(R.id.btnRegister);
        TextView txtLogin               = findViewById(R.id.txtLogin);
        ProgressBar progress            = findViewById(R.id.progress);

        tvTermsLink.setOnClickListener(v -> TermsHelper.show(this));

        txtLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, AuthenticationActivity.class));
            finish();
        });

        btnRegister.setOnClickListener(v -> {
            String fullName = edFullName.getText().toString().trim();
            String phone    = edPhone.getText().toString().trim();
            String email    = edEmail.getText().toString().trim();
            String password = edPassword.getText().toString().trim();
            String confirm  = edConPassword.getText().toString().trim();

            if (TextUtils.isEmpty(fullName)) {
                edFullName.setError("Full name is required"); edFullName.requestFocus(); return;
            }
            if (TextUtils.isEmpty(phone) || phone.length() != 10 || !phone.matches("\\d{10}")) {
                edPhone.setError("Enter a valid 10-digit phone number"); edPhone.requestFocus(); return;
            }
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edEmail.setError("Enter a valid email address"); edEmail.requestFocus(); return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                edPassword.setError("Min 6 characters"); edPassword.requestFocus(); return;
            }
            if (!password.equals(confirm)) {
                edConPassword.setError("Passwords do not match"); edConPassword.requestFocus(); return;
            }
            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please accept Terms & Conditions", Toast.LENGTH_SHORT).show();
                return;
            }

            progress.setVisibility(View.VISIBLE);
            btnRegister.setEnabled(false);

            // Step 1: Check Firebase Auth directly for existing providers
            mAuth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(result -> {
                    java.util.List<String> methods = result.getSignInMethods();
                    if (methods != null && !methods.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        btnRegister.setEnabled(true);
                        if (methods.contains("google.com")) {
                            edEmail.setError("This email is already registered via Google Sign-In. Please use the Google Sign-In button.");
                        } else {
                            edEmail.setError("This email is already registered. Please login with your password.");
                        }
                        edEmail.requestFocus();
                        return;
                    }
                    // Step 2: Also check Firestore as backup
                    checkFirestoreAndRegister(email, password, fullName, phone,
                            progress, btnRegister, edEmail);
                })
                .addOnFailureListener(e -> {
                    // fetchSignInMethods failed — fallback to Firestore check
                    checkFirestoreAndRegister(email, password, fullName, phone,
                            progress, btnRegister, edEmail);
                });
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(RegistarActivity.this, AuthenticationActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
        });
    }

    private void checkFirestoreAndRegister(String email, String password, String fullName,
                                            String phone, ProgressBar progress,
                                            MaterialButton btnRegister,
                                            TextInputEditText edEmail) {
        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener((QuerySnapshot snap) -> {
                if (!snap.isEmpty()) {
                    String existingProvider = snap.getDocuments().get(0).getString("provider");
                    progress.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    if ("google".equals(existingProvider)) {
                        edEmail.setError("This email is already registered via Google Sign-In. Please use the Google Sign-In button.");
                    } else {
                        edEmail.setError("This email is already registered. Please login with your password.");
                    }
                    edEmail.requestFocus();
                } else {
                    doRegister(email, password, fullName, phone, progress, btnRegister);
                }
            })
            .addOnFailureListener(e -> doRegister(email, password, fullName, phone, progress, btnRegister));
    }

    private void doRegister(String email, String password, String fullName, String phone,
                            ProgressBar progress, MaterialButton btnRegister) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.updateProfile(new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName).build());

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("fullName", fullName);
                        userData.put("phone", phone);
                        userData.put("email", email);
                        userData.put("provider", "email");
                        userData.put("createdAt", com.google.firebase.Timestamp.now());

                        db.collection("users").document(user.getUid())
                            .set(userData)
                            .addOnCompleteListener(t -> {
                                progress.setVisibility(View.GONE);
                                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                                goHome();
                            });
                    } else {
                        progress.setVisibility(View.GONE);
                        goHome();
                    }
                } else {
                    progress.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    Exception ex = task.getException();
                    String msg;
                    if (ex != null && ex.getMessage() != null
                            && ex.getMessage().contains("email address is already in use")) {
                        msg = "This email is already registered. Please login instead.";
                    } else {
                        msg = ex != null ? ex.getMessage() : "Registration failed";
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
    }
}
