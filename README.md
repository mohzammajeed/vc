# Soft V Compressor

Android app (WebView) that hosts the video compressor page in `app/src/main/assets/index.html`.

## Get the APK from GitHub
1. Push this folder to a GitHub repository (the `.github` folder must be at the top level).
2. Open the **Actions** tab. The **Build APK** workflow runs on every push to `main`/`master`.
   You can also start it with **Run workflow**.
3. When the run is green, open it and download **SoftVCompressor-debug-apk** from **Artifacts**.
4. Unzip it and install `app-debug.apk` on your phone.

The workflow file is `.github/workflows/build-apk.yml`. It installs its own Gradle,
so no `gradlew` file is needed.

## Build locally
Open the folder in Android Studio and press **Build > Build APK(s)**, or run `gradle assembleDebug`.

## Notes
- This builds a **debug** APK. A Play Store release needs a signed release build.
- The app needs no permissions: videos are chosen with the system picker, and **Save** opens the
  system "save as" dialog (**Share** uses the Android share sheet).
- Requires JDK 17 and Gradle 8.5 (the workflow installs both). The workflow uses Node.js 24-based
  GitHub Actions (checkout v6, setup-java v5, setup-android v4, setup-gradle v6, upload-artifact v6).
