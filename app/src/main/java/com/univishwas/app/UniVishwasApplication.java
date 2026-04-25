package com.univishwas.app;

import android.app.Application;
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
    }
}
