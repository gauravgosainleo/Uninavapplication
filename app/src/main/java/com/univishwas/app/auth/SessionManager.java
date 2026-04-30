package com.univishwas.app.auth;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS = "univishwas_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_HOUSE = "house_number";
    private static final String KEY_PENDING_HOUSE = "pending_house_number";
    private static final String KEY_EMAIL_VERIFIED = "email_verified";

    public static final String SOCIETY_NAME = "Uninav Heights";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return prefs.getString(KEY_TOKEN, null) != null;
    }

    public void save(String token, User user) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_NAME, user.name)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_HOUSE, user.houseNumber)
                .putString(KEY_PENDING_HOUSE, user.pendingHouseNumber)
                .putBoolean(KEY_EMAIL_VERIFIED, user.emailVerified)
                .apply();
    }

    public void update(User user) {
        prefs.edit()
                .putString(KEY_NAME, user.name)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_HOUSE, user.houseNumber)
                .putString(KEY_PENDING_HOUSE, user.pendingHouseNumber)
                .putBoolean(KEY_EMAIL_VERIFIED, user.emailVerified)
                .apply();
    }

    public User currentUser() {
        return new User(
                prefs.getString(KEY_NAME, ""),
                prefs.getString(KEY_EMAIL, ""),
                SOCIETY_NAME,
                prefs.getString(KEY_HOUSE, ""),
                prefs.getString(KEY_PENDING_HOUSE, null),
                prefs.getBoolean(KEY_EMAIL_VERIFIED, false));
    }

    public String token() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
