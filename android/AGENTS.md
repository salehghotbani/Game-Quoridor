# Android edition guidance

These instructions apply to `android/`. See [ANDROID.md](../ANDROID.md) for platform/build details and [ONLINE.md](../ONLINE.md) for server behavior.

## Platform and source boundaries

- Keep the app native Java under `app/src/main/java/com/quoridor/mobile`; package ID is `com.quoridor.mobile`. No Swing, embedded browser, media runtime or room server belongs in the APK.
- Reuse the explicit shared-source allowlist in `app/build.gradle` for rules, bots, history, save codecs and online client/transport. Edit shared behavior in `../src`, then check affected desktop/web contracts; do not fork the Java engine into Android.
- Preserve the pinned JDK 17, Gradle wrapper, Android Gradle Plugin and desugaring/checksum setup. Current compile/target SDK is 35 and minimum SDK is 26. Guard newer APIs or use compatible implementations; lint errors must not be suppressed merely to make a build pass.
- Use official dependency repositories and the helper's verified local-cache hook when required. Do not weaken dependency hashes or commit machine-specific SDK/cache paths. Gradle derives version name/code from the root `VERSION`.

## UI, saves and lifecycle

- Put translations in `MobileText` and appearance in `MobileStyle`. Support English/Persian and dark/light independently, including dialogs and system-bar contrast. Preserve user names and match/setup state when changing preferences; use text/bidi-safe rendering.
- RTL must not mirror physical board coordinates, goal edges, wall anchors or keyboard directions. Preserve square geometry, complete landscape visibility, TalkBack virtual nodes and keyboard focus in `QuoridorBoardView`.
- A selected wall is a preview; explicit confirmation commits it. `LocalMatch` owns the session/history on the UI thread. Robot undo/redo restores a whole human/robot turn; stale robot results must fail owner/revision/generation checks.
- Run search, network and document work off the main thread; deliver UI callbacks on the main looper. Retain live local history and the online owner across Activity recreation, detach old listeners and rebind/replay state to the new Activity. Do not retain an obsolete Activity in workers.
- Backgrounding pauses polling and cancels stale analysis without leaving membership. Finishing/leaving closes only the owned online session. Process death does not restore credentials. Never turn a disconnected/closed online game into an editable local match.
- Keep online calls serialized and revision-checked, with strict endpoint validation, normal TLS checks and no redirect or uncertain POST replay. Credentials remain in memory; do not persist/export/log them. Closing a pending entry must release its eventual reserved seat.
- `SaveStorage` uses app-private `AtomicFile` recovery, including backup-only recovery. Read content URIs through document streams, not filesystem paths; no broad storage permission is needed. Preserve bounded strict UTF-8/shared-codec validation and generation fences so invalid or late imports cannot replace a newer match or recovery file.
- Portable saves contain position/configuration, not online credentials or undo history. Import/resume starts fresh history; configuration recreation preserves live history. Preserve desktop `.qsave` v1/v2 and supported legacy text import compatibility.

## Build and release constraints

- **The user explicitly chose an UNSIGNED release APK.** From the repository root use `powershell -ExecutionPolicy Bypass -File .\build-android.ps1 -Unsigned` (add `-Offline` only with cached dependencies). Do not use the default signed-release path, `-InitializeSigning`, generate a release key or sign an artifact without a new explicit user instruction.
- Debug builds/tests may use the existing development-keystore workflow. Debug signing is not release signing. An unsigned APK cannot be installed; verify package/version/SDKs, notices, alignment and absence of signatures instead of claiming an installation test.
- Keep generated Gradle files under `../out/android`; distributables go to `../out/android/releases/v<version>`. Preserve existing versions and `../out/android-signing`; never include keystores/password files in source, APKs or release assets. Generated notice/version assets come from the root files via Gradle.

## Verification and owned devices

- Run `powershell -ExecutionPolicy Bypass -File .\test-android.ps1 -UnitOnly` for host unit tests plus lint. Run `test-android.ps1` for actual device behavior; build success alone is not a device test. Report `-SkipBuild` and skipped scopes accurately.
- Host tests live in `app/src/test`; native tests in `app/src/androidTest` cover board input/accessibility, local/robot flows, Activity lifecycle, atomic storage and real Java-server interoperability. Shared model/save/transport changes also need the relevant root Java regressions.
- Use `../scripts/AndroidEmulator.ps1` and the standard test helper for the owned `QuoridorQA` AVD under `../out/android/emulator`. Defaults are private ADB port 5038 and emulator port 5580; occupied ports must not be commandeered.
- Target only the helper's verified serial/process/session. Never install into a personal AVD/physical device, call a global `adb kill-server` or terminate unrelated emulators. Reuse/stop sessions only through ownership checks; clean up the owned loopback server as well.
- Preserve logs, screenshots and reports under `../out/android`. Validate bounded paths before recursive cleanup; do not erase signing material, dependency caches or previous release artifacts as a routine build fix.
