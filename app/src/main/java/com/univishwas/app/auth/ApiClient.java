package com.univishwas.app.auth;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Stub API client. Every method below SIMULATES a server round-trip on a
 * background thread, sleeps briefly, then invokes the callback on the main
 * thread with a hard-coded mock response.
 *
 * TODO(backend): replace each stub body with a real HTTP call to the
 * Uninav backend. Suggested endpoints:
 *   POST {API_BASE}/api/register
 *   POST {API_BASE}/api/verify-email
 *   POST {API_BASE}/api/login
 *   GET  {API_BASE}/api/profile
 *   POST {API_BASE}/api/profile/change-house-request
 */
public class ApiClient {

    /** TODO(backend): point this at the real Uninav API host once endpoints exist. */
    public static final String API_BASE_URL =
            "http://learninganddevelopment.net/Uninav/Application/api";

    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long FAKE_LATENCY_MS = 700L;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class AuthResult {
        public final String token;
        public final User user;
        public AuthResult(String token, User user) {
            this.token = token;
            this.user = user;
        }
    }

    private static final ApiClient INSTANCE = new ApiClient();
    public static ApiClient get() { return INSTANCE; }

    private ApiClient() {}

    public void register(final String name,
                         final String email,
                         final String password,
                         final Callback<Void> cb) {
        IO.execute(() -> {
            sleep();
            // TODO(backend): POST /api/register {name, email, password,
            //   society="Uninav Heights"} -> 200 OK + verification email sent.
            if (email == null || !email.contains("@")) {
                MAIN.post(() -> cb.onError("Invalid email address."));
                return;
            }
            MAIN.post(() -> cb.onSuccess(null));
        });
    }

    public void login(final String email,
                      final String password,
                      final Callback<AuthResult> cb) {
        IO.execute(() -> {
            sleep();
            // TODO(backend): POST /api/login {email, password} -> {token, user}.
            if (email == null || password == null
                    || email.isEmpty() || password.isEmpty()) {
                MAIN.post(() -> cb.onError("Email and password are required."));
                return;
            }
            String mockToken = "mock-token-" + System.currentTimeMillis();
            User mockUser = new User(
                    deriveDisplayName(email),
                    email,
                    SessionManager.SOCIETY_NAME,
                    autoAllocateHouseNumber(email),
                    null,
                    true);
            AuthResult result = new AuthResult(mockToken, mockUser);
            MAIN.post(() -> cb.onSuccess(result));
        });
    }

    public void requestHouseNumberChange(final String token,
                                         final String currentHouse,
                                         final String requestedHouse,
                                         final Callback<Void> cb) {
        IO.execute(() -> {
            sleep();
            // TODO(backend): POST /api/profile/change-house-request
            //   Authorization: Bearer <token>
            //   body: {requestedHouseNumber}
            //   -> 200 OK, status="pending_admin_approval".
            if (requestedHouse == null || requestedHouse.trim().isEmpty()) {
                MAIN.post(() -> cb.onError("Enter a house number."));
                return;
            }
            if (requestedHouse.equalsIgnoreCase(currentHouse)) {
                MAIN.post(() -> cb.onError(
                        "That is already your current house number."));
                return;
            }
            MAIN.post(() -> cb.onSuccess(null));
        });
    }

    /**
     * Stub auto-allocation. The real backend will look the registered name
     * up in the residents roster and return the matching house number, or
     * an "unallocated" marker if no match is found.
     */
    private static String autoAllocateHouseNumber(String email) {
        if (email == null) return "Unassigned";
        int hash = Math.abs(email.hashCode());
        int tower = (hash % 4);     // A, B, C, D
        int floor = ((hash / 4) % 12) + 1;
        int unit = ((hash / 48) % 4) + 1;
        char towerChar = (char) ('A' + tower);
        return towerChar + "-" + floor + (unit < 10 ? "0" + unit : String.valueOf(unit));
    }

    private static String deriveDisplayName(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at).replace('.', ' ').replace('_', ' ');
        if (local.isEmpty()) return email;
        StringBuilder sb = new StringBuilder();
        for (String part : local.split(" ")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private static void sleep() {
        try {
            Thread.sleep(FAKE_LATENCY_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
