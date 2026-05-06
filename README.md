# Nova Launcher

Custom Android home screen replacement built with Kotlin. Targets Android 15 on Samsung Galaxy S24 Ultra. Developed on Windows 11.

**Stack:** Kotlin, ViewBinding, MVVM, Coroutines, LauncherApps API
**Build:** Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0
**SDK:** minSdk 26 / targetSdk 35 / compileSdk 35

---

## Table of Contents

1. [How It Works](#how-it-works)
2. [Architecture](#architecture)
3. [Code Map](#code-map)
4. [Entrypoint and Startup Flow](#entrypoint-and-startup-flow)
5. [Data Flow](#data-flow)
6. [App Launch Flow](#app-launch-flow)
7. [Package Change Flow](#package-change-flow)
8. [File-by-File Reference](#file-by-file-reference)
9. [Build Configuration](#build-configuration)
10. [Where to Start Reading](#where-to-start-reading)
11. [Where to Add New Features](#where-to-add-new-features)
12. [Samsung Galaxy S24 Ultra Notes](#samsung-galaxy-s24-ultra-notes)
13. [Windows 11 Setup Guide](#windows-11-setup-guide)
14. [Run and Debug on Your Phone](#run-and-debug-on-your-phone)
15. [Emergency Recovery](#emergency-recovery)
16. [Troubleshooting](#troubleshooting)
17. [Roadmap](#roadmap)

---

## How It Works

The app registers itself as an Android home screen via intent filters in the manifest. When the user presses the HOME button, Android routes to this activity instead of the stock launcher. The activity queries all installed apps using the `LauncherApps` system service, displays them in a scrollable 4-column grid, and launches whichever app the user taps.

The device wallpaper shows through a transparent window background. A broadcast receiver auto-refreshes the grid when apps are installed, removed, or updated. The back button and HOME button both scroll to the top of the grid rather than exiting — standard launcher behavior.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AndroidManifest.xml                      │
│          CATEGORY_HOME + CATEGORY_DEFAULT intent filter         │
│          QUERY_ALL_PACKAGES permission (Android 11+)            │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Android OS routes HOME press here
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       LauncherActivity                          │
│                                                                 │
│  - enableEdgeToEdge() for Android 15                            │
│  - Sets up RecyclerView (4-col grid)                            │
│  - Observes ViewModel StateFlows                                │
│  - Registers BroadcastReceiver for package changes              │
│  - OnBackPressedCallback (predictive back gesture)              │
│  - Window inset handling (status bar, nav bar, camera cutout)   │
└──────────┬──────────────────────────────────┬───────────────────┘
           │ delegates to                     │ observes
           ▼                                  ▼
┌─────────────────────┐          ┌──────────────────────────────┐
│     AppAdapter       │◄─────── │      LauncherViewModel       │
│                      │ submits │                              │
│  - ListAdapter       │ list    │  - apps: StateFlow<List>     │
│  - DiffUtil          │         │  - isLoading: StateFlow      │
│  - ViewBinding       │         │  - loadApps()                │
│  - onClick callback  │         │  - getLaunchIntent()         │
│  - onLongClick       │         └──────────────┬───────────────┘
└─────────────────────┘                         │ calls
                                                ▼
                                 ┌──────────────────────────────┐
                                 │       AppRepository          │
                                 │                              │
                                 │  - LauncherApps API          │
                                 │  - Dispatchers.IO            │
                                 │  - getInstalledApps()        │
                                 │  - getLaunchIntent()         │
                                 └──────────────┬───────────────┘
                                                │ queries
                                                ▼
                                 ┌──────────────────────────────┐
                                 │     Android System           │
                                 │                              │
                                 │  - LauncherApps service      │
                                 │  - PackageManager            │
                                 │  - WallpaperManager          │
                                 └──────────────────────────────┘
```

**Pattern:** MVVM with Repository. One activity, one ViewModel, one repository, one adapter, one data model.

---

## Code Map

```
android-launcher/
│
├── build.gradle.kts ·················· Root build: plugin versions (AGP 8.7.3, Kotlin 2.1.0)
├── settings.gradle.kts ··············· Project name "NovaLauncher", includes :app module
├── gradle.properties ················· JVM args (-Xmx2048m), AndroidX, Kotlin style
├── gradlew ··························· Gradle wrapper script (macOS/Linux)
├── gradlew.bat ······················· Gradle wrapper script (Windows) ← USE THIS
├── .gitignore ························ Ignores /build, .idea, local.properties, .apk
│
├── gradle/wrapper/
│   └── gradle-wrapper.properties ····· Gradle 8.11.1 distribution URL
│
└── app/
    ├── build.gradle.kts ·············· App config: SDK versions, dependencies, ViewBinding
    ├── proguard-rules.pro ············ R8 keep rules for model classes
    │
    └── src/main/
        ├── AndroidManifest.xml ······· HOME intent filter, permissions, activity config
        │
        ├── java/com/launcher/nova/
        │   │
        │   ├── LauncherActivity.kt ··· ENTRYPOINT — the home screen activity
        │   │
        │   ├── model/
        │   │   └── AppInfo.kt ········ Data class for one installed app
        │   │
        │   ├── repository/
        │   │   └── AppRepository.kt ·· Loads apps from system, builds launch intents
        │   │
        │   ├── viewmodel/
        │   │   └── LauncherViewModel.kt  Holds app list state, survives rotation
        │   │
        │   └── adapter/
        │       └── AppAdapter.kt ····· RecyclerView adapter with DiffUtil
        │
        └── res/
            ├── layout/
            │   ├── activity_launcher.xml  Root layout: RecyclerView + ProgressBar
            │   └── item_app.xml ·········· Single grid cell: icon + label
            │
            ├── values/
            │   ├── themes.xml ············ Transparent theme (wallpaper shows through)
            │   └── strings.xml ··········· App name, accessibility strings
            │
            ├── drawable/
            │   ├── ic_launcher_foreground.xml  Adaptive icon foreground
            │   └── ic_launcher_background.xml  Adaptive icon background
            │
            └── mipmap-anydpi-v26/
                └── ic_launcher.xml ······· Adaptive icon definition
```

---

## Entrypoint and Startup Flow

The entrypoint is `LauncherActivity.kt`. Android launches it when the user presses HOME.

```
User presses HOME button
        │
        ▼
Android checks AndroidManifest.xml
        │
        │  Finds: <category android:name="android.intent.category.HOME" />
        │         <category android:name="android.intent.category.DEFAULT" />
        │
        ▼
LauncherActivity.onCreate()
        │
        ├── 1. enableEdgeToEdge()          → Makes app draw behind status/nav bars
        ├── 2. Inflate ActivityLauncherBinding → ViewBinding for activity_launcher.xml
        ├── 3. setupWindowInsets()          → Pads grid away from system bars + camera cutout
        ├── 4. setupRecyclerView()          → 4-column GridLayoutManager, attach AppAdapter
        ├── 5. setupBackHandler()           → OnBackPressedCallback: scroll to top
        ├── 6. observeViewModel()           → Collect apps StateFlow, collect isLoading StateFlow
        ├── 7. registerPackageReceiver()    → Listen for app install/uninstall/update
        └── 8. viewModel.loadApps()         → Trigger first app list load
```

**If user presses HOME while already on home screen:**

```
HOME pressed again
        │
        ▼
LauncherActivity.onNewIntent()
        │
        └── Scrolls RecyclerView to position 0 (top)
```

**If user presses BACK:**

```
BACK pressed
        │
        ▼
OnBackPressedCallback.handleOnBackPressed()
        │
        └── Scrolls RecyclerView to position 0 (does NOT exit)
```

---

## Data Flow

How the app list gets from the Android system to the screen:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. LauncherActivity calls viewModel.loadApps()              │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. LauncherViewModel launches coroutine in viewModelScope   │
│    Sets _isLoading = true                                   │
│    Calls repository.getInstalledApps()                      │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. AppRepository.getInstalledApps()                         │
│    Switches to Dispatchers.IO (background thread)           │
│    Calls launcherApps.getActivityList(null, userHandle)     │
│    Maps each LauncherActivityInfo → AppInfo data class      │
│    Sorts alphabetically by label                            │
│    Returns List<AppInfo>                                    │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. LauncherViewModel stores result in _apps StateFlow       │
│    Sets _isLoading = false                                  │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. LauncherActivity collects apps StateFlow                 │
│    (via repeatOnLifecycle — auto stops/starts with          │
│     activity lifecycle, prevents leaks)                     │
│    Calls appAdapter.submitList(apps)                        │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. AppAdapter (ListAdapter) receives new list               │
│    DiffUtil compares old vs new by packageName              │
│    Only redraws changed items                               │
│    Each ViewHolder binds: icon → ImageView, label → TextView│
└─────────────────────────────────────────────────────────────┘
```

---

## App Launch Flow

What happens when the user taps an app icon:

```
User taps app icon in grid
        │
        ▼
AppAdapter.onClick callback fires
        │
        ▼
LauncherActivity.onAppClicked(app)
        │
        ├── viewModel.getLaunchIntent(app.packageName)
        │           │
        │           ▼
        │   AppRepository.getLaunchIntent()
        │           │
        │           ├── PackageManager.getLaunchIntentForPackage()
        │           └── Adds flags: FLAG_ACTIVITY_NEW_TASK
        │                           FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        │
        ├── Intent found → startActivity(intent)
        │                       │
        │                       ▼
        │               Target app opens in foreground
        │               Launcher moves to background
        │
        └── Intent null → Toast "Cannot open <app name>"
```

---

## Package Change Flow

What happens when an app is installed, uninstalled, or updated:

```
User installs/removes/updates an app
        │
        ▼
Android broadcasts:
  ACTION_PACKAGE_ADDED    or
  ACTION_PACKAGE_REMOVED  or
  ACTION_PACKAGE_CHANGED
        │
        ▼
LauncherActivity.packageReceiver.onReceive()
        │
        ▼
viewModel.loadApps()
        │
        ▼
(Same data flow as above — re-queries all apps, updates grid)
```

---

## File-by-File Reference

### `AndroidManifest.xml`

Registers the app as a home screen launcher. Key pieces:

- **`QUERY_ALL_PACKAGES`** permission — required on Android 11+ for an app to see all installed packages. Without this, `LauncherApps.getActivityList()` returns an empty list.
- **`READ_EXTERNAL_STORAGE`** (maxSdkVersion 32) — for wallpaper access on older Android versions. Not needed on Android 13+.
- **`SET_WALLPAPER`** — allows future wallpaper-changing features.
- **`CATEGORY_HOME`** + **`CATEGORY_DEFAULT`** — tells Android this activity can be a home screen.
- **`launchMode="singleTask"`** — only one instance ever exists. HOME always returns to it.
- **`clearTaskOnLaunch="true"`** — when returning via HOME, any stacked activities are cleared.
- **`stateNotNeeded="true"`** — Android can kill/recreate this without saving instance state.
- **`resumeWhilePausing="true"`** — speeds up HOME transitions by overlapping pause/resume.
- **`configChanges="..."`** — handles rotation, keyboard, screen size internally (no activity recreation).

### `LauncherActivity.kt` — The Entrypoint

The single activity. This is the home screen. Key methods:

| Method | What it does |
|---|---|
| `onCreate` | Enables edge-to-edge, inflates layout, sets up everything, triggers first load |
| `setupWindowInsets` | Applies padding around system bars and camera cutout via `WindowInsetsCompat` |
| `setupRecyclerView` | Creates 4-column `GridLayoutManager`, attaches adapter, disables item animator |
| `setupBackHandler` | Registers `OnBackPressedCallback` — scrolls to top instead of exiting |
| `observeViewModel` | Collects `apps` and `isLoading` StateFlows inside `repeatOnLifecycle` |
| `registerPackageReceiver` | Registers `BroadcastReceiver` for install/uninstall/update events via `ContextCompat` |
| `onAppClicked` | Gets launch intent from ViewModel, starts the target activity |
| `onAppLongClicked` | Placeholder — shows Toast. Future: drag/drop, app info, uninstall |
| `onNewIntent` | Fires when HOME is pressed while already home — scrolls to top |
| `onDestroy` | Unregisters the broadcast receiver to prevent leaks |

### `AppInfo.kt` — Data Model

```kotlin
data class AppInfo(
    val packageName: String,    // "com.spotify.music"
    val label: String,          // "Spotify"
    val icon: Drawable,         // The app's launcher icon
    val activityName: String,   // "com.spotify.music.MainActivity"
)
```

Immutable. One instance per installed app. Held in a list inside the ViewModel's StateFlow.

### `AppRepository.kt` — Data Layer

Two functions:

| Function | Thread | What it does |
|---|---|---|
| `getInstalledApps()` | IO (background) | Queries `LauncherApps.getActivityList()`, maps to `AppInfo`, sorts A-Z |
| `getLaunchIntent(pkg)` | Main | Calls `PackageManager.getLaunchIntentForPackage()`, adds launch flags |

Uses `LauncherApps` API (not the deprecated `queryIntentActivities`). This correctly handles work profiles (Samsung Secure Folder) and Android 11+ package visibility.

### `LauncherViewModel.kt` — State Holder

| Member | Type | Purpose |
|---|---|---|
| `apps` | `StateFlow<List<AppInfo>>` | Current list of installed apps |
| `isLoading` | `StateFlow<Boolean>` | True while loading apps |
| `loadApps()` | Function | Triggers reload via coroutine in `viewModelScope` |
| `getLaunchIntent()` | Function | Delegates to repository |

Extends `AndroidViewModel` — has access to `Application` context. Survives configuration changes (rotation). Coroutines auto-cancel when ViewModel is cleared.

### `AppAdapter.kt` — RecyclerView Adapter

Extends `ListAdapter<AppInfo, AppViewHolder>` with a `DiffUtil.ItemCallback`. This means:
- You call `submitList(newList)` — DiffUtil figures out what changed
- Only changed items are rebound (no full refresh)
- The `ItemCallback` compares by `packageName` (identity) and `packageName + label` (content)

Each `AppViewHolder` uses `ItemAppBinding` (ViewBinding for `item_app.xml`) to set the icon and label.

### `activity_launcher.xml` — Main Layout

`FrameLayout` containing:
- `RecyclerView` (`app_grid`) — full screen, `clipToPadding=false` so items scroll behind padding
- `ProgressBar` (`progress_bar`) — centered, shown during loading

### `item_app.xml` — Grid Cell Layout

Vertical `LinearLayout`:
- `ImageView` (48dp x 48dp) — app icon
- `TextView` — app name, white text, 12sp, single line with ellipsis, text shadow for readability over wallpaper

### `themes.xml` — Transparent Theme

```xml
Theme.NovaLauncher (parent: Theme.Material3.DayNight.NoActionBar)
  ├── windowShowWallpaper = true    ← System wallpaper composites behind this activity
  └── windowBackground = transparent ← Activity window is see-through
```

`enableEdgeToEdge()` in the activity handles status/nav bar transparency. The theme only needs to set the wallpaper passthrough and transparent background.

### Build Files

| File | Purpose |
|---|---|
| `build.gradle.kts` (root) | Declares plugin versions: AGP 8.7.3, Kotlin 2.1.0 |
| `settings.gradle.kts` | Project name "NovaLauncher", includes `:app`, configures Maven repos |
| `gradle.properties` | JVM heap 2048m, AndroidX enabled, non-transitive R classes |
| `app/build.gradle.kts` | minSdk 26, targetSdk 35, ViewBinding on, R8 on release, debug suffix `.debug` |
| `app/proguard-rules.pro` | Keeps `com.launcher.nova.model.**` from being obfuscated by R8 |

### Dependencies

| Library | Version | Why |
|---|---|---|
| `core-ktx` | 1.15.0 | Kotlin extensions for Android framework APIs |
| `activity-ktx` | 1.9.3 | `enableEdgeToEdge()`, `OnBackPressedCallback`, `viewModels()` delegate |
| `appcompat` | 1.7.0 | `AppCompatActivity` base class, backward-compat themes |
| `material` | 1.12.0 | `Theme.Material3.DayNight.NoActionBar` parent theme |
| `recyclerview` | 1.3.2 | `RecyclerView`, `ListAdapter`, `DiffUtil`, `GridLayoutManager` |
| `lifecycle-viewmodel-ktx` | 2.8.7 | `AndroidViewModel`, `viewModelScope` |
| `lifecycle-runtime-ktx` | 2.8.7 | `repeatOnLifecycle` for safe StateFlow collection |
| `kotlinx-coroutines-android` | 1.9.0 | `Dispatchers.IO`, `Dispatchers.Main`, `withContext` |

---

## Where to Start Reading

If you're new to this codebase, read in this order:

1. **`AppInfo.kt`** (10 lines) — understand the data model first
2. **`AppRepository.kt`** (42 lines) — how apps are queried from the system
3. **`LauncherViewModel.kt`** (31 lines) — how state is managed
4. **`AppAdapter.kt`** (47 lines) — how apps are rendered in the grid
5. **`LauncherActivity.kt`** (152 lines) — how everything connects together
6. **`AndroidManifest.xml`** (41 lines) — how the app registers as a launcher

Total: ~320 lines of Kotlin. The whole app reads in under 15 minutes.

---

## Where to Add New Features

| Feature | Where to add it |
|---|---|
| **App drawer** | New `AppDrawerActivity` or bottom-sheet fragment in `LauncherActivity` |
| **Drag-and-drop** | `ItemTouchHelper` on `AppAdapter`, persist order in Room/SQLite |
| **Dock / favorites bar** | New layout section below the grid in `activity_launcher.xml`, separate adapter |
| **Home screen pages** | Replace `RecyclerView` with `ViewPager2`, one page per screen of apps |
| **Widget hosting** | `AppWidgetHost` + `AppWidgetManager` in `LauncherActivity`, new layout for widget slots |
| **Search bar** | `EditText` at top of `activity_launcher.xml`, filter `apps` StateFlow in ViewModel |
| **Gesture handling** | `GestureDetector` or `MotionEvent` handling on the root layout |
| **Icon packs** | New `IconPackRepository` to load from third-party icon pack APKs, inject into `AppAdapter` |
| **Persistent layout** | Add Room database, store grid positions, load from DB in `AppRepository` |
| **App shortcuts** | `ShortcutManager` API in `AppRepository`, show on long-press popup |

---

## Samsung Galaxy S24 Ultra Notes

### Setting as Default Launcher

On One UI, the standard "Choose launcher" dialog may not appear. Use this instead:

**Settings > Apps > Default apps > Home app > select "Nova Launcher"**

### One UI Interference

- **Home Up module** (Good Lock) — can break third-party launcher animations. Disable while testing.
- **Secure Folder** — apps inside appear as duplicates via `LauncherApps` when Secure Folder is unlocked. This is expected.
- **System updates** — One UI may reset your default launcher after a system update.

### Camera Cutout

The S24 Ultra's centered punch-hole camera cutout is handled automatically by `enableEdgeToEdge()` + `WindowInsetsCompat.Type.displayCutout()`. No special code needed.

### Edge Panels and Gestures

Samsung's edge panels and gesture navigation work independently of the launcher. Swipe-up-for-home returns to your custom launcher as long as it's set as default.

---

## Windows 11 Setup Guide

From a fresh Windows 11 machine to running on your S24 Ultra.

### 1. Install Android Studio

1. Download from https://developer.android.com/studio (the `.exe` installer)
2. Run the installer. Accept all defaults. Click Install.
3. Launch Android Studio when finished.

### 2. First Launch Setup Wizard

1. Select **"Do not import settings"**
2. Install type: **Standard**
3. Pick a UI theme (Darcula = dark)
4. Accept all licenses, click Finish
5. Wait for SDK download to complete (5-20 min, ~2-4 GB)

The Android SDK installs to:
```
C:\Users\<YourName>\AppData\Local\Android\Sdk
```

### 3. Verify SDK Components

**File > Settings > Languages & Frameworks > Android SDK:**

- **SDK Platforms tab**: Android 15 (VanillaIceCream) API 35 must be installed
- **SDK Tools tab**: Verify these are installed:
  - Android SDK Build-Tools 35
  - Android SDK Platform-Tools
  - Android SDK Command-line Tools (latest)

Check anything missing and click Apply.

### 4. Set Environment Variables

This makes `adb` available from any terminal.

1. Press **Win key**, type **"environment variables"**, open **"Edit the system environment variables"**
2. Click **"Environment Variables..."**
3. Under **User variables**, click **New**:
   - Name: `ANDROID_HOME`
   - Value: `C:\Users\<YourName>\AppData\Local\Android\Sdk`
4. Edit the **Path** variable, add two new entries:
   - `%ANDROID_HOME%\platform-tools`
   - `%ANDROID_HOME%\tools`
5. Click OK on everything

**Verify** — open a **new** Command Prompt:
```cmd
adb version
```
Should print `Android Debug Bridge version 1.0.41` or similar.

### 5. Install Samsung USB Drivers

Windows needs Samsung-specific drivers to talk to the S24 Ultra over USB.

1. Download from https://developer.samsung.com/android-usb-driver
2. Extract the zip, run the installer inside
3. **Restart your PC** after installation

Without this, `adb devices` will not see your phone.

### 6. Enable Developer Options on Your Phone

1. **Settings > About phone > Software information**
2. Tap **Build number** 7 times (Samsung may ask for your PIN)
3. Go back to **Settings > Developer options**
4. Enable **USB debugging** (tap OK on the dialog)
5. Optional: Enable **Stay awake** (screen stays on while charging)

### 7. Connect Phone via USB

1. Plug S24 Ultra into PC with a **data-capable USB-C cable** (the one that came with the phone)
2. Pull down notification shade on phone, tap the USB notification, select **"File transfer / Android Auto"**
3. Accept the **"Allow USB debugging?"** popup (check "Always allow from this computer")
4. On PC, open Command Prompt:
   ```cmd
   adb devices
   ```
   Expected:
   ```
   List of devices attached
   RFXXXXXXXX      device
   ```

**If nothing shows:** try a different cable, different USB port, or run `adb kill-server` then `adb devices` again.

### 8. Open the Project

1. In Android Studio, click **Open**
2. Navigate to the `android-launcher` folder (the one with `build.gradle.kts` and `gradlew.bat` at the root)
3. Click OK
4. Wait for Gradle sync to finish (2-5 min on first run)

If sync fails with "SDK location not found", create `local.properties` in the project root:
```
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

### 9. Speed Up Builds (Important)

Windows Defender scans every file Gradle reads/writes. This can double your build time.

1. Open **Windows Security > Virus & threat protection > Manage settings > Exclusions > Add**
2. Add these folders:
   - Your project folder (e.g., `C:\dev\android-launcher`)
   - `C:\Users\<YourName>\.gradle`
   - `C:\Users\<YourName>\AppData\Local\Android\Sdk`

---

## Run and Debug on Your Phone

### Run (No Debugger)

1. Select your phone in the device dropdown (top toolbar) — shows as "Samsung SM-S928B" or similar
2. Click the **green triangle** or press **Shift+F10**
3. First build: 1-3 min. Subsequent builds: 10-30 sec.

### Run with Debugger

1. Click the **bug icon** or press **Shift+F9**
2. Same as above, but with debugger attached

### Using Breakpoints

1. Open any `.kt` file (e.g., `LauncherActivity.kt`)
2. Click the **left gutter** next to a line number — a red dot (breakpoint) appears
3. Run with Debug (Shift+F9)
4. When execution hits the breakpoint, the app pauses:
   - **Variables panel** — inspect current values
   - **Frames panel** — see the call stack
   - **F8** — step over
   - **F7** — step into
   - **Shift+F8** — step out
   - **F9** — resume
   - **Ctrl+F2** — stop

### Logcat (Runtime Logs)

1. **View > Tool Windows > Logcat** (or the Logcat tab at the bottom)
2. Filter: `package:com.launcher.nova.debug`
3. Crashes show in red — always check here first

### Command-Line Build

```cmd
gradlew.bat assembleDebug
gradlew.bat installDebug
adb shell am start -n com.launcher.nova.debug/com.launcher.nova.LauncherActivity
```

### Set as Default Launcher

After installing: **Settings > Apps > Default apps > Home app > "Nova Launcher"**

### Wireless Debugging

1. Phone and PC on same Wi-Fi
2. Phone: **Settings > Developer options > Wireless debugging > ON**
3. Tap **"Pair device with pairing code"**
4. On PC:
   ```cmd
   adb pair <IP>:<PAIRING_PORT>
   adb connect <IP>:<PORT>
   ```
5. Unplug cable — `adb devices` still shows your phone

---

## Emergency Recovery

If you're stuck in a launcher loop and can't reach Settings:

**From your PC's Command Prompt:**

```cmd
:: Reset to Samsung's default launcher
adb shell cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity

:: Or clear the app's defaults
adb shell pm clear com.launcher.nova.debug

:: Or uninstall the app entirely
adb uninstall com.launcher.nova.debug
```

---

## Troubleshooting

### Build / Gradle

| Problem | Fix |
|---|---|
| `'gradlew' is not recognized` | Use `gradlew.bat` on Windows |
| "Could not resolve" dependencies | Check internet. File > Invalidate Caches > Restart |
| "SDK location not found" | Create `local.properties`: `sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk` |
| "Unsupported class file major version" | File > Settings > Build > Gradle > Gradle JDK > select bundled **jbr-17** |
| Build extremely slow | Add project + `.gradle` folder to Windows Defender exclusions |
| Long path errors | Move project to `C:\dev\android-launcher`. Or enable long paths: `reg add HKLM\SYSTEM\CurrentControlSet\Control\FileSystem /v LongPathsEnabled /t REG_DWORD /d 1` (admin cmd, then reboot) |

### USB / ADB

| Problem | Fix |
|---|---|
| `adb devices` shows nothing | Samsung USB drivers missing. Install them and restart PC |
| Shows `unauthorized` | Check phone for USB debugging popup. Tap Allow |
| Shows `offline` | `adb kill-server` then `adb devices` |
| `'adb' is not recognized` | Environment variable not set (Step 4). Open a **new** terminal |
| Phone stuck on "Charging only" | Tap USB notification, change to "File transfer". Try a different cable |

### App Runtime

| Problem | Fix |
|---|---|
| Crashes on launch | Check Logcat for red stack trace |
| Black screen, no apps | `QUERY_ALL_PACKAGES` permission issue. Rebuild and reinstall |
| Not in Home app list | Force stop: Settings > Apps > Nova Launcher > Force stop. Retry |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.launcher.nova.debug` then reinstall |
| Wallpaper not showing | Set app as default launcher first. Verify `themes.xml` has `windowShowWallpaper=true` |
| Breakpoints not hitting | Use Debug (Shift+F9), not Run (Shift+F10) |

---

## Roadmap

- [ ] App drawer (separate from home screen workspace)
- [ ] Drag-and-drop icon rearrangement
- [ ] Home screen pages with swipe navigation
- [ ] Dock / favorites bar
- [ ] Widget hosting via `AppWidgetHost`
- [ ] Folder grouping
- [ ] Gesture support (swipe up for drawer, pinch for settings)
- [ ] Icon pack support
- [ ] SQLite/Room persistence for layout
- [ ] Search bar
