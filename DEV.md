# Developer Guide - Launcher

Custom Android home screen launcher built with Kotlin (native Android, MVVM). Targets Android 8.0+ (minSdk 26, compileSdk 35); built with AGP 8.7.3, Kotlin 2.1.0, JDK 17, Gradle 8.11.1.

## Quick Start

Regenerate the Gradle wrapper jar once (not committed):

```bash
gradle wrapper --gradle-version 8.11.1
chmod +x gradlew
```

Build and install:

```bash
./gradlew assembleDebug          # Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (R8 minified)
./gradlew installDebug           # Build + install on connected device
./gradlew test                   # Local JVM unit tests
./gradlew clean
```

Sideload a CI-built APK:

```bash
gh run download --name android-launcher-debug-apk
adb install -r app-debug.apk
```

Set as default launcher: Settings -> Apps -> Default apps -> Home app -> Launcher (Debug). Revert to Samsung One UI:

```bash
adb shell cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity
```

Debug builds use applicationId `com.launcher.nova.debug`; release uses `com.launcher.nova`. CI (`.github/workflows/build.yml`) runs on push to `main`, PRs, and `workflow_dispatch`.
