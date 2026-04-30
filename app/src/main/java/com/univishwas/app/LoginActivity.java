package com.univishwas.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.univishwas.app.auth.ApiClient;
import com.univishwas.app.auth.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailField;
    private TextInputEditText passwordField;
    private MaterialButton loginButton;
    private ProgressBar progress;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (session.isLoggedIn()) {
            goToMain();
            return;
        }
        setContentView(R.layout.activity_login);

        emailLayout = findViewById(R.id.loginEmailLayout);
        passwordLayout = findViewById(R.id.loginPasswordLayout);
        emailField = findViewById(R.id.loginEmail);
        passwordField = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginButton);
        progress = findViewById(R.id.loginProgress);

        loginButton.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.goToRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        emailLayout.setError(null);
        passwordLayout.setError(null);

        String email = text(emailField);
        String password = text(passwordField);

        boolean ok = true;
        if (email.isEmpty() || !email.contains("@")) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            ok = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_password_required));
            ok = false;
        }
        if (!ok) return;

        setBusy(true);
        ApiClient.get().login(email, password, new ApiClient.Callback<ApiClient.AuthResult>() {
            @Override
            public void onSuccess(ApiClient.AuthResult result) {
                session.save(result.token, result.user);
                setBusy(false);
                goToMain();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Snackbar.make(loginButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!busy);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String text(TextInputEditText field) {
        CharSequence c = field.getText();
        return c == null ? "" : c.toString().trim();
    }
}
