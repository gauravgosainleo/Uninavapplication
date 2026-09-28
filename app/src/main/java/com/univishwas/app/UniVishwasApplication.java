package com.univishwas.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.webkit.WebView;

public class UniVishwasApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable WebView debugging in debug builds so you can inspect with
        // chrome://inspect on a desktop browser when the device is plugged in.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        createNotificationChannel();
        // Background "what's new" check: notices, events, photos, polls, ads...
        UpdatesWorker.schedule(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                UpdatesWorker.CHANNEL_ID,
                getString(R.string.channel_updates_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.channel_updates_desc));
        nm.createNotificationChannel(channel);
    }
}
