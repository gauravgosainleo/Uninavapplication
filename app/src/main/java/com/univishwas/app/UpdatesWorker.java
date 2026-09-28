package com.univishwas.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Background check for new society-wide content (notices, events, gallery
 * photos, polls, ads, AOA changes, vendors).
 *
 * Runs about every 15 minutes through WorkManager, asks the portal's
 * {@code api/app_updates.php} for anything newer than the last check, and
 * shows one notification per item. Tapping a notification opens the app on
 * the matching page.
 *
 * The very first run only records the server time, so a fresh install is not
 * flooded with everything that was ever posted.
 */
public class UpdatesWorker extends Worker {

    /** Notification channel for society updates (created in the Application). */
    static final String CHANNEL_ID = "society_updates";

    /** Extra carried by the notification tap intent: page to open. */
    static final String EXTRA_OPEN_URL = "open_url";

    private static final String ENDPOINT = MainActivity.START_URL + "api/app_updates.php";

    /** Must match APP_NOTIFY_KEY in the portal's includes/helpers.php. */
    private static final String APP_KEY = "uv-notify-7c3e9a1b4d2f";

    private static final String PREFS = "uv_updates";
    private static final String KEY_LAST_TS = "last_ts";
    private static final String KEY_SEEN = "seen_ids";

    private static final String PERIODIC_WORK = "society-updates";
    private static final String IMMEDIATE_WORK = "society-updates-now";

    public UpdatesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Registers the recurring check (safe to call on every app start). */
    static void schedule(Context context) {
        Constraints online = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                UpdatesWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(online)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic);
    }

    /** Runs one check right now (used when the app is opened). */
    static void checkNow(Context context) {
        OneTimeWorkRequest once = new OneTimeWorkRequest.Builder(UpdatesWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, once);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_TS, 0L);

        JSONObject response;
        try {
            response = new JSONObject(fetch(ENDPOINT + "?since=" + last + "&k=" + APP_KEY));
        } catch (Exception e) {
            return Result.retry();
        }
        if (!response.optBoolean("ok", false)) return Result.retry();

        long serverTime = response.optLong("server_time", 0L);
        JSONArray items = response.optJSONArray("items");

        if (last > 0 && items != null && items.length() > 0 && canNotify(ctx)) {
            Set<String> seen = new LinkedHashSet<>(Arrays.asList(
                    prefs.getString(KEY_SEEN, "").split("\n")));
            List<String> newlySeen = new ArrayList<>();
            // Oldest first so the newest ends up on top of the shade.
            for (int i = items.length() - 1; i >= 0; i--) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty() || seen.contains(id)) continue;
                notify(ctx, id, item.optString("title", ctx.getString(R.string.app_name)),
                        item.optString("body", ""), item.optString("url", MainActivity.START_URL));
                newlySeen.add(id);
            }
            if (!newlySeen.isEmpty()) {
                seen.addAll(newlySeen);
                List<String> keep = new ArrayList<>(seen);
                if (keep.size() > 200) keep = keep.subList(keep.size() - 200, keep.size());
                StringBuilder sb = new StringBuilder();
                for (String s : keep) if (!s.isEmpty()) sb.append(s).append('\n');
                prefs.edit().putString(KEY_SEEN, sb.toString()).apply();
            }
        }

        if (serverTime > 0) {
            // Step back a minute so an item saved in the same second as this
            // check is not missed; the seen-id list prevents duplicates.
            prefs.edit().putLong(KEY_LAST_TS, Math.max(1L, serverTime - 60L)).apply();
        }
        return Result.success();
    }

    private static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    private static void notify(Context ctx, String id, String title, String body, String url) {
        Intent open = new Intent(ctx, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_OPEN_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = id.hashCode();
        PendingIntent tap = PendingIntent.getActivity(ctx, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF0B4A46)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setGroup(CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL);
        try {
            NotificationManagerCompat.from(ctx).notify(requestCode, b.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the post.
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Uni-Vishwas Android/" + BuildConfig.VERSION_NAME);
        conn.setRequestProperty("Accept", "application/json");
        try {
            if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }
}
