package com.univishwas.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.univishwas.app.auth.ApiClient;

public class RegisterActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout verifyEmailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText nameField;
    private TextInputEditText emailField;
    private TextInputEditText verifyEmailField;
    private TextInputEditText passwordField;
    private TextInputEditText confirmPasswordField;
    private MaterialButton registerButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameLayout = findViewById(R.id.registerNameLayout);
        emailLayout = findViewById(R.id.registerEmailLayout);
        verifyEmailLayout = findViewById(R.id.registerVerifyEmailLayout);
        passwordLayout = findViewById(R.id.registerPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.registerConfirmPasswordLayout);

        nameField = findViewById(R.id.registerName);
        emailField = findViewById(R.id.registerEmail);
        verifyEmailField = findViewById(R.id.registerVerifyEmail);
        passwordField = findViewById(R.id.registerPassword);
        confirmPasswordField = findViewById(R.id.registerConfirmPassword);

        registerButton = findViewById(R.id.registerButton);
        progress = findViewById(R.id.registerProgress);

        registerButton.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.goToLogin).setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        verifyEmailLayout.setError(null);
        passwordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        String name = text(nameField);
        String email = text(emailField);
        String verifyEmail = text(verifyEmailField);
        String password = text(passwordField);
        String confirmPassword = text(confirmPasswordField);

        boolean ok = true;
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_name_required));
            ok = false;
        }
        if (email.isEmpty() || !email.contains("@")) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            ok = false;
        }
        if (!verifyEmail.equalsIgnoreCase(email)) {
            verifyEmailLayout.setError(getString(R.string.error_emails_dont_match));
            ok = false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_too_short));
            ok = false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_passwords_dont_match));
            ok = false;
        }
        if (!ok) return;

        setBusy(true);
        ApiClient.get().register(name, email, password, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setBusy(false);
                showVerificationSentDialog();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Snackbar.make(registerButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showVerificationSentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.verification_sent_title)
                .setMessage(R.string.verification_sent_message)
                .setPositiveButton(R.string.go_to_login, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!busy);
    }

    private static String text(TextInputEditText field) {
        CharSequence c = field.getText();
        return c == null ? "" : c.toString().trim();
    }
}
