# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A custom Android home screen launcher (HOME app replacement) built in Kotlin. Registers as a system launcher via `CATEGORY_HOME` intent filter. MVP stage — displays installed apps in a grid, launches them on tap.

**Target device:** Samsung Galaxy S24 Ultra, Android 15 (One UI 7)
**Dev machine:** Windows 11

## Build Commands

Windows (Command Prompt or PowerShell):
```cmd
gradlew.bat assembleDebug          # Build debug APK
gradlew.bat installDebug           # Build and install on connected device
gradlew.bat assembleRelease        # Build release APK (R8 minified)
gradlew.bat clean                  # Clean build outputs
```

Launch after install:
```cmd
adb shell am start -n com.launcher.nova.debug/com.launcher.nova.LauncherActivity
```

Reset S24 Ultra to One UI Home if stuck:
```cmd
adb shell cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity
```

Note: debug builds use applicationId `com.launcher.nova.debug` (suffix in build.gradle.kts). Release builds use `com.launcher.nova`.

No tests exist yet. No linter is configured.

## Architecture

MVVM with Repository pattern. Single-module Gradle project (`app/`).

**Data flow:** `LauncherApps API` → `AppRepository` (IO thread) → `LauncherViewModel` (StateFlow) → `LauncherActivity` (collects via repeatOnLifecycle) → `AppAdapter` (ListAdapter + DiffUtil)

Key architectural decisions:
- Uses `LauncherApps` API (not deprecated `PackageManager.queryIntentActivities`) — correctly handles work profiles, Samsung Secure Folder, and Android 11+ package visibility.
- `BroadcastReceiver` in `LauncherActivity` registered via `ContextCompat.registerReceiver()` for backward compatibility across all API levels.
- Activity uses `singleTask` launch mode with `clearTaskOnLaunch` — standard launcher lifecycle.
- Back handling uses `OnBackPressedCallback` (not deprecated `onBackPressed`) — required for Android 15 predictive back gesture.
- `enableEdgeToEdge()` + `ViewCompat.setOnApplyWindowInsetsListener` handles Android 15 mandatory edge-to-edge with proper insets for status bar, nav bar, and Samsung camera cutout.
- Theme is transparent (`windowShowWallpaper=true`) so the system wallpaper renders behind the grid. No manual `WallpaperManager.getDrawable()` needed.
- `itemAnimator = null` on RecyclerView for zero-latency scrolling.
- `QUERY_ALL_PACKAGES` permission is required for launchers on Android 11+.

## Key Files

| File | Role |
|---|---|
| `app/src/main/AndroidManifest.xml` | HOME intent filter, permissions, activity flags |
| `app/src/main/java/.../LauncherActivity.kt` | Main activity — edge-to-edge, grid setup, broadcast receiver, lifecycle |
| `app/src/main/java/.../repository/AppRepository.kt` | Loads apps via LauncherApps on Dispatchers.IO |
| `app/src/main/java/.../viewmodel/LauncherViewModel.kt` | Holds `StateFlow<List<AppInfo>>`, scoped coroutines |
| `app/src/main/java/.../adapter/AppAdapter.kt` | ListAdapter with DiffUtil, ViewBinding |
| `app/src/main/java/.../model/AppInfo.kt` | Data class: packageName, label, icon, activityName |
| `app/src/main/res/values/themes.xml` | Transparent theme for wallpaper passthrough |

## Build Config

- **Kotlin**, no Java. JVM target 17.
- **minSdk 26**, targetSdk/compileSdk 35.
- **ViewBinding** enabled (no DataBinding, no Compose).
- **R8 + resource shrinking** on release builds. ProGuard keeps model classes.
- Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0.

## Windows Notes

- Use `gradlew.bat` not `gradlew` from command line.
- Samsung USB drivers must be installed for ADB to detect the S24 Ultra.
- Add project folder + `%USERPROFILE%\.gradle` to Windows Defender exclusions for faster builds.
- If paths exceed 260 chars, enable long paths or move project closer to drive root.

## CI

`.github/workflows/build.yml` runs on push to `main`/`master`, on PRs, and on manual `workflow_dispatch`. It uses JDK 17 + Gradle 8.11.1 (the gradle-actions setup-gradle action installs Gradle directly — the wrapper jar is **not** checked in). Produces:

- `android-launcher-debug-apk` — every run.
- `android-launcher-release-apk` — best-effort (continues on error since release signing is debug-keystore-fallback).

Local builds: regenerate the wrapper once with `gradle wrapper --gradle-version 8.11.1 && chmod +x gradlew`.

See `dev.md` for the full sideload + install flow (download artifact → `adb install`).

## Branding Note

User-visible strings show **"Launcher"** (was renamed from "Nova Launcher" — that name is trademarked). The internal applicationId / package (`com.launcher.nova`) still uses the codename — that's not user-visible. Do not reintroduce "Nova Launcher" as a display name, theme name, project name, or artifact name.
