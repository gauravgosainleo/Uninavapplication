package com.univishwas.app;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.univishwas.app.auth.SessionManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    /**
     * Site this WebView wraps.
     * Change this constant if you ever migrate the PHP app to a new URL.
     */
    private static final String START_URL =
            "http://learninganddevelopment.net/Uninav/Application/";

    private static final int PERMISSION_REQUEST_CODE = 4242;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private View offlineView;

    /** Holds the WebView's file-chooser callback while a picker is in flight. */
    private ValueCallback<Uri[]> filePathCallback;

    /** Path of the temporary camera output file, used after a camera capture. */
    private String cameraPhotoPath;

    /** Launches the file/camera chooser and returns selected URIs to WebView. */
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (filePathCallback == null) return;

                        Uri[] results = null;
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data == null || data.getDataString() == null) {
                                // No data returned: assume camera capture
                                if (cameraPhotoPath != null) {
                                    results = new Uri[]{Uri.parse(cameraPhotoPath)};
                                }
                            } else {
                                String dataString = data.getDataString();
                                results = new Uri[]{Uri.parse(dataString)};
                            }
                        }

                        filePathCallback.onReceiveValue(results);
                        filePathCallback = null;
                        cameraPhotoPath = null;
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        offlineView = findViewById(R.id.offline_view);

        findViewById(R.id.retry_button).setOnClickListener(v -> {
            if (isOnline()) {
                showOnline();
                webView.loadUrl(START_URL);
            } else {
                Toast.makeText(this, R.string.still_offline, Toast.LENGTH_SHORT).show();
            }
        });

        FloatingActionButton profileFab = findViewById(R.id.profileFab);
        profileFab.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        configureWebView();

        if (isOnline()) {
            webView.loadUrl(START_URL);
        } else {
            showOffline();
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Make sure cookies persist (required for the PHP session cookie UNINAVSESS).
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalScheme(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !isOnline()) {
                    showOffline();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                if (!hasUploadPermissions()) {
                    requestUploadPermissions();
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return true;
                }

                Intent chooser = buildFileChooserIntent(fileChooserParams);
                try {
                    fileChooserLauncher.launch(chooser);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this,
                            R.string.no_file_picker, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        // Trigger the system DownloadManager when the page links to a downloadable file
        // (the polls module exports PDFs).
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimetype);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("cookie", cookies);
            req.addRequestHeader("User-Agent", userAgent);
            req.setDescription(getString(R.string.downloading));
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            req.setTitle(fileName);
            req.allowScanningByMediaScanner();
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, getString(R.string.downloading_named, fileName),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private Intent buildFileChooserIntent(WebChromeClient.FileChooserParams params) {
        Intent contentIntent = params.createIntent();

        // Add a camera intent so the picker offers "Take photo" alongside Files/Gallery.
        Intent cameraIntent = createCameraIntent();
        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INTENT, contentIntent);
        chooser.putExtra(Intent.EXTRA_TITLE, getString(R.string.choose_file));
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }
        return chooser;
    }

    @Nullable
    private Intent createCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) return null;

        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            return null;
        }

        cameraPhotoPath = "file:" + photoFile.getAbsolutePath();
        Uri photoUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", photoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return intent;
    }

    private File createImageFile() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("UNI_" + stamp + "_", ".jpg", storageDir);
    }

    private boolean handleExternalScheme(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        switch (scheme) {
            case "mailto":
            case "tel":
            case "sms":
            case "whatsapp":
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.no_app_to_handle, Toast.LENGTH_SHORT).show();
                    return true;
                }
            default:
                return false; // let the WebView load http/https itself
        }
    }

    private boolean hasUploadPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean read = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean cam = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        return read && cam;
    }

    private void requestUploadPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            perms = new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showOffline() {
        webView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.GONE);
        offlineView.setVisibility(View.VISIBLE);
    }

    private void showOnline() {
        offlineView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Tell the user to retry the upload now that perms (may) have been granted.
            Toast.makeText(this, R.string.retry_upload, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
