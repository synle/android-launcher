# android-launcher — Architecture

## High-Level Overview

`android-launcher` (a.k.a. **Nova Launcher** — package `com.launcher.nova`) is
a minimal native Android home-screen replacement written in Kotlin. It follows
the standard **MVVM** pattern (Activity ↔ ViewModel ↔ Repository) and ships as
a single-activity app whose `LauncherActivity` is registered for the
`android.intent.category.HOME` intent filter, so the system offers it as a
selectable launcher and routes the HOME button to it.

At runtime the launcher:

1. Queries the system `LauncherApps` service for every launchable activity
   visible to the current user.
2. Exposes that list as a `StateFlow<List<AppInfo>>` from `LauncherViewModel`.
3. Renders it as a 4-column `RecyclerView` grid via `AppAdapter` and
   ViewBinding.
4. Listens to `PACKAGE_ADDED/REMOVED/CHANGED` broadcasts to keep the grid in
   sync with install/uninstall events.
5. Reroutes back-press and re-entry to "scroll to top" instead of exiting, so
   the launcher behaves like a real home screen.

Targets: **AGP 8.7.3**, **Kotlin 2.1.0**, **Gradle 8.11.1**, **JDK 17**,
**minSdk 26**, **compileSdk/targetSdk 35**.

## Key Directories

```
android-launcher/
├── app/                                     # Single Gradle module (the launcher app)
│   ├── build.gradle.kts                     # Module build script, deps, SDK levels, viewBinding
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # HOME intent filter lives here
│       │   ├── java/com/launcher/nova/
│       │   │   ├── LauncherActivity.kt      # Single Activity, MVVM "View"
│       │   │   ├── adapter/                 # RecyclerView adapter (AppAdapter)
│       │   │   ├── model/                   # Data classes (AppInfo)
│       │   │   ├── repository/              # AppRepository — LauncherApps wrapper
│       │   │   ├── util/                    # AppListUtils — sort/filter helpers
│       │   │   └── viewmodel/               # LauncherViewModel — StateFlow exposure
│       │   └── res/
│       │       ├── layout/                  # activity_launcher.xml, item_app.xml
│       │       ├── values/                  # themes.xml, strings.xml
│       │       ├── drawable/                # Launcher icon vectors
│       │       └── mipmap-anydpi-v26/       # Adaptive launcher icon
│       └── test/                            # JVM unit tests (JUnit4 + Mockito)
├── gradle/                                  # Gradle wrapper distribution config
├── .github/workflows/                       # build.yml (CI) + release.yml (APK release)
├── build.gradle.kts                         # Root build script (plugin versions only)
├── settings.gradle.kts                      # Project name + module list
├── gradle.properties
└── gradlew / gradlew.bat                    # Gradle wrapper entry points
```

## Important Files

- **`settings.gradle.kts`** — declares `rootProject.name = "Launcher"` and the
  single `:app` module. Pins `pluginManagement` repos
  (google/mavenCentral/gradlePluginPortal) and locks dependency resolution to
  `FAIL_ON_PROJECT_REPOS`.
- **`build.gradle.kts`** (root) — applies AGP `8.7.3` and Kotlin `2.1.0`
  plugins with `apply false`; the `:app` module activates them.
- **`app/build.gradle.kts`** — namespace `com.launcher.nova`, `minSdk 26`,
  `targetSdk/compileSdk 35`, Java 17 source/target, `kotlinOptions.jvmTarget =
  "17"`, `viewBinding = true`. Release build type runs R8 with
  `isMinifyEnabled` and `isShrinkResources`; debug appends
  `applicationIdSuffix = ".debug"` so both can coexist. Pulls AndroidX
  core/appcompat/activity/recyclerview/lifecycle-viewmodel, Material,
  kotlinx-coroutines-android, JUnit4 and Mockito for tests.
- **`app/src/main/AndroidManifest.xml`** — the file that makes this a
  launcher: `LauncherActivity` is `exported="true"` with `launchMode="singleTask"`,
  `clearTaskOnLaunch="true"`, `stateNotNeeded="true"` and an `<intent-filter>`
  containing **`android.intent.action.MAIN`** + **`category.HOME`** +
  `category.DEFAULT` + `category.LAUNCHER`. Also declares
  `QUERY_ALL_PACKAGES` (Android 11+) plus `SET_WALLPAPER` and a
  capped-to-API-32 `READ_EXTERNAL_STORAGE`.
- **`LauncherActivity.kt`** (`com.launcher.nova`) — the MVVM View. Inflates
  `ActivityLauncherBinding`, configures a 4-column `GridLayoutManager`, applies
  edge-to-edge insets, observes `viewModel.apps` and `viewModel.isLoading` via
  `repeatOnLifecycle(STARTED)`, registers a `BroadcastReceiver` for package
  install/remove/change events (`RECEIVER_NOT_EXPORTED`), overrides back-press
  + `onNewIntent` to scroll-to-top instead of exiting, and starts apps through
  the repository-supplied launch `Intent` with `FLAG_ACTIVITY_NEW_TASK`.
- **`viewmodel/LauncherViewModel.kt`** — `AndroidViewModel` that owns an
  `AppRepository`, exposes `apps: StateFlow<List<AppInfo>>` and
  `isLoading: StateFlow<Boolean>`, and loads via `viewModelScope.launch`.
  Also forwards `getLaunchIntent(pkg)` to the repository.
- **`repository/AppRepository.kt`** — wraps `LauncherApps.getActivityList(...)`
  on `Dispatchers.IO`, builds `AppInfo` rows (package, label, badged icon,
  activity class) and sorts case-insensitively by label. Resolves launch
  intents via `PackageManager.getLaunchIntentForPackage`.
- **`model/AppInfo.kt`** — immutable data class (`packageName`, `label`,
  `icon: Drawable`, `activityName`) — the single rendering record.
- **`adapter/AppAdapter.kt`** — `ListAdapter`-based grid adapter binding
  `item_app.xml` rows; receives click + long-click lambdas from the Activity.
- **`util/AppListUtils.kt`** + **`test/.../AppListUtilsTest.kt`** — pure
  sort/filter helpers; the unit test target exercises these on the JVM
  without instrumentation.

## Build & Release Flow

Two GitHub Actions workflows under `.github/workflows/`:

- **`build.yml` — Build APK.** Triggered on `push` / `pull_request` to
  `main`/`master` and manually via `workflow_dispatch`. On `ubuntu-latest`:
  sets up Temurin JDK 17, installs Gradle `8.11.1` via
  `gradle/actions/setup-gradle@v4`, runs `gradle testDebugUnitTest`,
  `gradle assembleDebug`, uploads the debug APK as the
  `android-launcher-debug-apk` artifact (`if-no-files-found: error`), then
  attempts `gradle assembleRelease` (`continue-on-error: true`, since release
  builds are unsigned) and uploads any resulting unsigned release APK.
- **`release.yml` — Release APK.** **Manual only** (`workflow_dispatch`) with
  required `tag` input (e.g. `v0.1.0`) and optional `notes`. Same JDK 17 +
  Gradle 8.11.1 setup; builds the debug APK, then attempts a release APK
  (`continue-on-error: true`). Stages both into `release-apks/` renamed
  `android-launcher-<tag>-debug.apk` / `-release.apk`, then publishes a
  GitHub Release via `softprops/action-gh-release@v2` with
  `tag_name = inputs.tag` (no derivation from `github.ref_name`) and
  `permissions: contents: write`.

Local build path mirrors CI: `./gradlew testDebugUnitTest assembleDebug`
produces `app/build/outputs/apk/debug/app-debug.apk`; release builds land at
`app/build/outputs/apk/release/app-release.apk` and remain unsigned until a
signing config is added.
