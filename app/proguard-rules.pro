# Keep WebView JavaScript interfaces, in case any are added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep file-chooser callbacks intact.
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**
