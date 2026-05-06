# Developer Guide — Nova Launcher

A custom Android home screen launcher (Kotlin, native Android, MVVM).

## Toolchain

| Tool | Version |
|------|---------|
| JDK | 17 (Temurin recommended) |
| Gradle | 8.11.1 (CI auto-installs; locally use Gradle wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| Compile / Target SDK | 35 (Android 15) |
| Min SDK | 26 (Android 8.0) |

## Local build

The repo ships `gradlew` / `gradlew.bat` but **the wrapper jar is missing**. Regenerate it once before building locally:

```bash
gradle wrapper --gradle-version 8.11.1
chmod +x gradlew
```

Then:

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (R8 minified) → app/build/outputs/apk/release/
./gradlew installDebug           # Build + install on connected device
./gradlew clean
```

Debug builds use applicationId `com.launcher.nova.debug`; release uses `com.launcher.nova`.

## Install on phone (sideload)

1. Go to the [Actions tab](../../actions) and pick the latest successful **Build APK** run.
2. Scroll to the **Artifacts** section and download `nova-launcher-debug-apk` (or `nova-launcher-release-apk`).
3. Unzip — you'll get `app-debug.apk` (or `app-release.apk`).
4. On the phone:
   - **Via ADB**: `adb install -r app-debug.apk`
   - **Via file transfer**: copy the APK to the phone, open it from Files, and accept "Install from unknown source" when prompted.
5. After install, set Nova as your default launcher:
   - Settings → Apps → Default apps → Home app → **Nova Launcher (Debug)**
   - Or press the Home button — Android will offer a chooser.
6. To revert to the Samsung One UI launcher:
   ```bash
   adb shell cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity
   ```

## CI

`.github/workflows/build.yml` runs on every push to `main`/`master`, on PRs, and on manual `workflow_dispatch`. It produces a debug APK artifact (and best-effort release APK).

## Target device notes

- Designed for Samsung Galaxy S24 Ultra on Android 15 (One UI 7).
- `QUERY_ALL_PACKAGES` permission is declared — required for launchers on Android 11+.
- Edge-to-edge layout handles the camera cutout and status/nav bar insets.
- Debug builds can run side-by-side with the release variant.
