# Uni-Vishwas Android Wrapper

A thin Android WebView wrapper around the Uni-Vishwas residential society
PHP application hosted at:

```
http://learninganddevelopment.net/Uninav/Application/
```

It produces a real, installable `.apk` from a GitHub Actions workflow.
You don't need Android Studio, the Android SDK, or even Java installed on
your own machine — GitHub builds it for you.

---

## What the app does

| Feature | Where |
| --- | --- |
| Loads the live PHP site in a WebView | `MainActivity.java` |
| File uploads (profile photos, complaint images, notice images, ad images) | `onShowFileChooser` with gallery + camera intents |
| Camera capture inside the chooser | `FileProvider` + `MediaStore.ACTION_IMAGE_CAPTURE` |
| Poll PDF downloads | Android `DownloadManager` listener |
| Persistent login (session cookie `UNINAVSESS`) | `CookieManager` |
| `mailto:` / `tel:` / `sms:` links | Forwarded to the system app |
| Pull-to-refresh | `SwipeRefreshLayout` |
| Offline screen with retry button | `activity_main.xml` |
| Hardware Back navigates WebView history | `onKeyDown` |
| HTTP allowed only for the host domain | `network_security_config.xml` |
| Light + dark themes | Material 3 `DayNight` |

---

## How to ship an APK in 5 minutes

### 1. Push this folder to a new GitHub repo

```bash
cd android-wrapper
git init
git add .
git commit -m "Initial Uni-Vishwas Android wrapper"
git branch -M main
git remote add origin https://github.com/<your-username>/uni-vishwas-android.git
git push -u origin main
```

### 2. Watch the build

- Go to the repo on github.com → **Actions** tab.
- The **Build APK** workflow starts automatically on the push.
- It takes about 3–5 minutes.

### 3. Download the APK

- Click the green-checkmark workflow run.
- Scroll to **Artifacts** at the bottom.
- Download **Uni-Vishwas-debug-apk** → unzip → `Uni-Vishwas-debug.apk`.

### 4. Install on your phone

- Copy the APK to your phone (email, Telegram, USB, anything).
- Tap it. Android will ask you to enable "Install unknown apps" for whatever
  app you opened it from — allow it once.
- Done. The app opens straight into the login page.

> **Why "debug" not "release"?** The debug APK is signed with the SDK's
> built-in debug keystore, so it installs everywhere with zero extra setup.
> If you want a properly signed release APK, see "Optional: signed release"
> below.

---

## Manually triggering a build

You don't have to push every time:

1. Go to **Actions → Build APK**.
2. Click **Run workflow** → choose your branch → **Run**.
3. New artifact appears when it's done.

---

## Changing the URL

Edit one constant in `app/src/main/java/com/univishwas/app/MainActivity.java`:

```java
private static final String START_URL =
        "http://learninganddevelopment.net/Uninav/Application/";
```

If you move the site to a different domain, also update
`app/src/main/res/xml/network_security_config.xml` so cleartext traffic is
allowed for the new host. Once you switch the site to **HTTPS**, you can
remove the `domain-config` block entirely and delete
`android:usesCleartextTraffic="true"` from `AndroidManifest.xml`.

---

## Local build (optional)

You don't need this — the GitHub workflow is the supported path. But if you
want to build locally:

```bash
# 1. Install JDK 17 and Gradle 8.5 (e.g. via SDKMAN: `sdk install gradle 8.5`)
# 2. Generate the wrapper (the jar isn't committed; the workflow regenerates it too)
gradle wrapper --gradle-version 8.5
# 3. Install the Android SDK, set ANDROID_HOME, accept licences
sdkmanager --licenses
# 4. Build
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

---

## Optional: signed release APK

The workflow automatically builds a signed release APK **if** these four
secrets are present on the repo:

| Secret | Description |
| --- | --- |
| `UNI_KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `UNI_KEYSTORE_PASSWORD` | Store password |
| `UNI_KEY_ALIAS` | Key alias |
| `UNI_KEY_PASSWORD` | Key password |

Generate a keystore once on any machine that has the JDK installed:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias univishwas
```

Then encode it:

```bash
base64 -w0 release.jks   # Linux
base64 -i release.jks    # macOS
```

Paste the output into the `UNI_KEYSTORE_BASE64` secret on GitHub
(**Settings → Secrets and variables → Actions → New repository secret**).
Add the other three secrets too.

The next build will produce a second artifact: **Uni-Vishwas-release-apk**.
Use this one if you ever want to publish to the Play Store, distribute
through MDM, or otherwise need a real signature.

> **Keep `release.jks` safe and back it up.** If you lose it, future
> updates can never replace the installed app — users will have to
> uninstall first.

---

## Project layout

```
android-wrapper/
├── .github/workflows/build-apk.yml      ← the CI build
├── app/
│   ├── build.gradle                     ← module config (AGP 8.2.2, SDK 34, min 21)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml          ← perms, FileProvider, theme
│       ├── java/com/univishwas/app/
│       │   ├── MainActivity.java        ← WebView + uploads + downloads
│       │   └── UniVishwasApplication.java
│       └── res/
│           ├── drawable/                ← adaptive-icon vectors
│           ├── layout/activity_main.xml
│           ├── mipmap-*/                ← PNG launcher fallbacks (API < 26)
│           ├── mipmap-anydpi-v26/       ← adaptive launcher (API ≥ 26)
│           ├── values/                  ← strings, colors, light theme
│           ├── values-night/            ← dark theme
│           └── xml/                     ← file_paths, network_security_config
├── build.gradle                         ← root
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── .gitignore
```

---

## Security notes

- The PHP backend uses HTTP, so the wrapper opts in to cleartext traffic
  **only** for `learninganddevelopment.net`. All other domains are still
  required to use HTTPS via the system's `base-config`.
- WebView debugging (`chrome://inspect`) is enabled in **debug builds only**
  via `BuildConfig.DEBUG`.
- `UNINAVSESS` cookies and CSRF tokens are handled entirely by the WebView's
  built-in `CookieManager`; the app code never reads or writes them itself.
- File access (`setAllowFileAccess(false)`) is disabled to prevent the
  WebView from reading arbitrary files via `file://` URLs.

---

## Troubleshooting

**"App not installed" on the phone.** You probably had a previous version
installed signed with a different key (e.g. you switched from debug to
release, or generated a new keystore). Uninstall the old version first.

**Login works but uploads do nothing.** Re-check the upload runtime
permissions — Android 13+ requires `READ_MEDIA_IMAGES`, older versions
need `READ_EXTERNAL_STORAGE`. The app prompts on first upload; if you
denied, clear app permissions in Settings and try again.

**Pull-to-refresh fights with scroll-to-top inside the page.** Some PHP
modules have their own scrollable areas. If a module's scroll feels
hijacked, scroll up to the very top of the page first and *then* pull
down — the SwipeRefreshLayout only triggers when the WebView is at
scroll position 0.

**Build fails with "SDK location not found" locally.** You need
`local.properties` pointing at your Android SDK install
(`sdk.dir=/path/to/Android/Sdk`). The CI workflow handles this for you.
