package com.univishwas.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.univishwas.app.auth.ApiClient;
import com.univishwas.app.auth.SessionManager;
import com.univishwas.app.auth.User;

public class ProfileActivity extends AppCompatActivity {

    private SessionManager session;
    private TextView nameView;
    private TextView emailView;
    private TextView societyView;
    private TextView houseNumberView;
    private MaterialCardView pendingCard;
    private TextView pendingText;
    private MaterialButton requestChangeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_profile);

        MaterialToolbar toolbar = findViewById(R.id.profileToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        nameView = findViewById(R.id.profileName);
        emailView = findViewById(R.id.profileEmail);
        societyView = findViewById(R.id.profileSociety);
        houseNumberView = findViewById(R.id.profileHouseNumber);
        pendingCard = findViewById(R.id.pendingRequestCard);
        pendingText = findViewById(R.id.pendingRequestText);
        requestChangeButton = findViewById(R.id.requestHouseChangeButton);

        requestChangeButton.setOnClickListener(v -> showHouseChangeDialog());
        findViewById(R.id.logoutButton).setOnClickListener(v -> confirmLogout());

        bindUser();
    }

    private void bindUser() {
        User user = session.currentUser();
        nameView.setText(user.name);
        emailView.setText(user.email);
        societyView.setText(user.society);
        houseNumberView.setText(
                user.houseNumber == null || user.houseNumber.isEmpty()
                        ? getString(R.string.profile_house_unassigned)
                        : user.houseNumber);

        if (user.pendingHouseNumber != null && !user.pendingHouseNumber.isEmpty()) {
            pendingCard.setVisibility(View.VISIBLE);
            pendingText.setText(getString(
                    R.string.profile_pending_request, user.pendingHouseNumber));
            requestChangeButton.setEnabled(false);
            requestChangeButton.setText(R.string.request_house_change_pending);
        } else {
            pendingCard.setVisibility(View.GONE);
            requestChangeButton.setEnabled(true);
            requestChangeButton.setText(R.string.request_house_change);
        }
    }

    private void showHouseChangeDialog() {
        Context ctx = this;
        TextInputLayout wrap = new TextInputLayout(ctx);
        wrap.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        wrap.setHint(getString(R.string.request_house_dialog_hint));
        TextInputEditText input = new TextInputEditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        wrap.addView(input, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int padH = (int) (24 * getResources().getDisplayMetrics().density);
        int padV = (int) (8 * getResources().getDisplayMetrics().density);
        wrap.setPadding(padH, padV, padH, 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.request_house_dialog_title)
                .setMessage(R.string.request_house_dialog_message)
                .setView(wrap)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.submit, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    CharSequence c = input.getText();
                    String requested = c == null ? "" : c.toString().trim();
                    submitHouseChange(requested, dialog);
                }));
        dialog.show();
    }

    private void submitHouseChange(String requested, AlertDialog dialog) {
        User current = session.currentUser();
        ApiClient.get().requestHouseNumberChange(
                session.token(),
                current.houseNumber,
                requested,
                new ApiClient.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        User updated = new User(
                                current.name,
                                current.email,
                                current.society,
                                current.houseNumber,
                                requested,
                                current.emailVerified);
                        session.update(updated);
                        bindUser();
                        dialog.dismiss();
                        Snackbar.make(requestChangeButton,
                                getString(R.string.request_house_submitted),
                                Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        Snackbar.make(requestChangeButton, message,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout, (d, w) -> performLogout())
                .show();
    }

    private void performLogout() {
        session.clear();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
