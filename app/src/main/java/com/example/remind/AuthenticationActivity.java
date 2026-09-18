package com.example.remind;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Random;

public class AuthenticationActivity extends BaseActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextInputEditText etPassword, etemail, etCaptchaAnswer;
    private TextView tvCaptchaQuestion;
    private GoogleSignInClient googleSignInClient;
    private ProgressBar progress;

    private final ActivityResultLauncher<Intent> googleLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                        .getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken(), account.getEmail());
            } catch (ApiException e) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        etemail           = findViewById(R.id.etemail);
        etPassword        = findViewById(R.id.etPassword);
        etCaptchaAnswer   = findViewById(R.id.etCaptchaAnswer);
        tvCaptchaQuestion = findViewById(R.id.tvCaptchaQuestion);
        progress          = findViewById(R.id.progress);
        MaterialButton btnLogin  = findViewById(R.id.btnLogin);
        MaterialButton btnGoogle = findViewById(R.id.btnGoogleSignIn);
        TextView tvRegister      = findViewById(R.id.tvRegister);
        TextView tvForgot        = findViewById(R.id.tvForgotPassword);
        ImageView ivRefresh      = findViewById(R.id.ivRefreshCaptcha);

        generateCaptcha();
        ivRefresh.setOnClickListener(v -> generateCaptcha());

        // Google Sign-In setup
        String webClientId = getGoogleWebClientId();
        if (webClientId != null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } else {
            btnGoogle.setEnabled(false);
            btnGoogle.setAlpha(0.4f);
            btnGoogle.setText("Google Sign-In (not configured)");
        }

        // Forgot password
        tvForgot.setOnClickListener(v -> {
            String email = etemail.getText().toString().trim();
            if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etemail.setError("Enter your email first");
                etemail.requestFocus();
                return;
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(u -> Toast.makeText(this,
                        "Reset link sent to " + email, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        // Email/Password login
        btnLogin.setOnClickListener(v -> {
            String email    = etemail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String captcha  = etCaptchaAnswer.getText().toString().trim();

            if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etemail.setError("Enter a valid email"); etemail.requestFocus(); return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Enter password"); etPassword.requestFocus(); return;
            }
            if (TextUtils.isEmpty(captcha)) {
                etCaptchaAnswer.setError("Enter the captcha"); etCaptchaAnswer.requestFocus(); return;
            }
            String expected = (String) tvCaptchaQuestion.getTag();
            if (!captcha.equals(expected)) {
                etCaptchaAnswer.setError("Wrong captcha");
                generateCaptcha();
                etCaptchaAnswer.setText("");
                return;
            }

            progress.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);

            // Check Firestore first: if this email is registered via Google, block password login
            db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener((QuerySnapshot snap) -> {
                    if (!snap.isEmpty()) {
                        String provider = snap.getDocuments().get(0).getString("provider");
                        if ("google".equals(provider)) {
                            // Registered via Google — cannot login with password
                            progress.setVisibility(View.GONE);
                            btnLogin.setEnabled(true);
                            Toast.makeText(this,
                                "This email is registered via Google Sign-In. Please use the Google Sign-In button.",
                                Toast.LENGTH_LONG).show();
                            generateCaptcha();
                            etCaptchaAnswer.setText("");
                            return;
                        }
                    }
                    // Either email/password account or not found — let Firebase decide
                    doEmailLogin(email, password, btnLogin);
                })
                .addOnFailureListener(e -> {
                    // Firestore check failed — attempt login directly
                    doEmailLogin(email, password, btnLogin);
                });
        });

        // Google Sign-In button
        btnGoogle.setOnClickListener(v -> {
            if (googleSignInClient == null) {
                Toast.makeText(this, "Google Sign-In not configured in Firebase Console", Toast.LENGTH_LONG).show();
                return;
            }
            progress.setVisibility(View.VISIBLE);
            googleSignInClient.signOut().addOnCompleteListener(t ->
                googleLauncher.launch(googleSignInClient.getSignInIntent()));
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegistarActivity.class));
            finish();
        });

        findViewById(R.id.tvTermsLink).setOnClickListener(v -> TermsHelper.show(this));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { showExitSheet(); }
        });
    }

    /**
     * Performs the actual Firebase email/password sign-in.
     * Called after Firestore provider check passes.
     */
    private void doEmailLogin(String email, String password, MaterialButton btnLogin) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                if (task.isSuccessful()) {
                    checkBlocklistAndGoHome();
                } else {
                    Exception ex = task.getException();
                    String errorCode = (ex instanceof FirebaseAuthException)
                            ? ((FirebaseAuthException) ex).getErrorCode() : "";
                    String msg;
                    if ("ERROR_USER_NOT_FOUND".equals(errorCode)
                            || (ex != null && ex.getMessage() != null
                                && ex.getMessage().contains("no user record"))) {
                        msg = "No account found with this email. Please register first.";
                    } else if ("ERROR_WRONG_PASSWORD".equals(errorCode)
                            || (ex != null && ex.getMessage() != null
                                && ex.getMessage().contains("password is invalid"))) {
                        msg = "Incorrect password. Please try again.";
                    } else if ("ERROR_INVALID_CREDENTIAL".equals(errorCode)
                            || (ex != null && ex.getMessage() != null
                                && ex.getMessage().contains("INVALID_LOGIN_CREDENTIALS"))) {
                        msg = "Invalid email or password. Please check and try again.";
                    } else {
                        msg = ex != null ? ex.getMessage() : "Login failed. Please try again.";
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    generateCaptcha();
                    etCaptchaAnswer.setText("");
                }
            });
    }

    /**
     * Google Sign-In flow.
     * googleEmail: the email from the Google account — used to check if it's already
     * registered via email/password before proceeding.
     */
    private void firebaseAuthWithGoogle(String idToken, String googleEmail) {
        if (googleEmail == null || googleEmail.isEmpty()) {
            doGoogleFirebaseAuth(idToken);
            return;
        }
        // Step 1: Check Firebase Auth — does this email already have a password provider?
        auth.fetchSignInMethodsForEmail(googleEmail)
            .addOnSuccessListener(result -> {
                java.util.List<String> methods = result.getSignInMethods();
                if (methods != null && methods.contains("password")) {
                    // Email/password account exists — block Google sign-in
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this,
                        "This email is already registered with a password. Please login using your email and password.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                // Step 2: Also check Firestore provider field as backup
                db.collection("users")
                    .whereEqualTo("email", googleEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener((QuerySnapshot snap) -> {
                        if (!snap.isEmpty()) {
                            String provider = snap.getDocuments().get(0).getString("provider");
                            if ("email".equals(provider)) {
                                progress.setVisibility(View.GONE);
                                Toast.makeText(this,
                                    "This email is already registered with a password. Please login using your email and password.",
                                    Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        doGoogleFirebaseAuth(idToken);
                    })
                    .addOnFailureListener(e -> doGoogleFirebaseAuth(idToken));
            })
            .addOnFailureListener(e -> {
                // fetchSignInMethods failed — fallback to Firestore check only
                db.collection("users")
                    .whereEqualTo("email", googleEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener((QuerySnapshot snap) -> {
                        if (!snap.isEmpty() && "email".equals(
                                snap.getDocuments().get(0).getString("provider"))) {
                            progress.setVisibility(View.GONE);
                            Toast.makeText(this,
                                "This email is already registered with a password. Please login using your email and password.",
                                Toast.LENGTH_LONG).show();
                            return;
                        }
                        doGoogleFirebaseAuth(idToken);
                    })
                    .addOnFailureListener(err -> doGoogleFirebaseAuth(idToken));
            });
    }

    private void doGoogleFirebaseAuth(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
            .addOnCompleteListener(task -> {
                progress.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        String uid = user.getUid();
                        db.collection("deletedAccounts").document(uid)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    auth.signOut();
                                    Toast.makeText(this,
                                        "This account has been deleted and cannot be used.",
                                        Toast.LENGTH_LONG).show();
                                } else {
                                    // Save/update user profile in Firestore
                                    java.util.Map<String, Object> userData = new java.util.HashMap<>();
                                    userData.put("fullName", user.getDisplayName() != null ? user.getDisplayName() : "");
                                    userData.put("email", user.getEmail() != null ? user.getEmail() : "");
                                    userData.put("provider", "google");
                                    boolean isNew = task.getResult().getAdditionalUserInfo() != null
                                            && task.getResult().getAdditionalUserInfo().isNewUser();
                                    if (isNew) userData.put("createdAt", com.google.firebase.Timestamp.now());
                                    db.collection("users").document(uid)
                                        .set(userData, com.google.firebase.firestore.SetOptions.merge());
                                    goHome();
                                }
                            })
                            .addOnFailureListener(e -> goHome());
                    }
                } else {
                    Exception ex = task.getException();
                    String msg = ex != null ? ex.getMessage() : "Google auth failed";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    private void checkBlocklistAndGoHome() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        db.collection("deletedAccounts").document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    auth.signOut();
                    Toast.makeText(this,
                        "This account has been deleted and cannot be used.",
                        Toast.LENGTH_LONG).show();
                    generateCaptcha();
                    etCaptchaAnswer.setText("");
                } else {
                    goHome();
                }
            })
            .addOnFailureListener(e -> goHome());
    }

    private void generateCaptcha() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%&!";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        tvCaptchaQuestion.setText(sb.toString());
        tvCaptchaQuestion.setTag(sb.toString());
    }

    private void goHome() {
        Intent i = new Intent(this, HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void showExitSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetStyle);
        android.view.View v = getLayoutInflater().inflate(R.layout.bottom_sheet_exit, null);
        sheet.setContentView(v);
        sheet.setCanceledOnTouchOutside(true);
        v.findViewById(R.id.btnExitConfirm).setOnClickListener(sv -> { sheet.dismiss(); finishAffinity(); });
        v.findViewById(R.id.btnExitCancel).setOnClickListener(sv -> sheet.dismiss());
        sheet.show();
    }

    private String getGoogleWebClientId() {
        try {
            int resId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (resId != 0) return getString(resId);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
