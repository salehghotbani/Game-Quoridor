# Android app / نسخهٔ اندروید

[English README](README.md) · [راهنمای فارسی](README.fa.md) · [Online hosting](ONLINE.md) · [Releasing](RELEASING.md)

## Install and play

Download **`Quoridor-2.2.0-android-unsigned.apk`** from [GitHub Releases](https://github.com/salehghotbani/Game-Quoridor/releases). **The maintainer chose an unsigned artifact; sign it with your own key before installation. No release signing key was created.** The app targets **Android 8.0+ (API 26+)**, with no system Java installation needed. Verify its SHA-256 against the release checksum file. This is not a Google Play upload.

The native touch app uses the existing Java rules, robot and save codecs directly; it does not embed Swing, a browser, VLC or the server.

Current source 2.6.0 has a sculpted board, raised tiles and compact player cards
with visual wall reserves. It also includes **Learn by playing**, five isolated visual practice steps,
and a prominent **Play again** button beside a local winner. Practice leaves the
live position and undo history intact. Replay retains names, player count, mode
and robot difficulty while resetting the board/history. Android keeps its explicit
move/horizontal/vertical tools and wall confirmation. The 2.2.0 download above is
the earlier published native release; build current source for these additions.

- Local two/four-player matches, or a two-player game against Easy/Medium/Hard robots.
- English/Persian UI with stable physical board coordinates, and independently selectable dark/light appearance.
- Legal pawn highlights; horizontal/vertical wall selection with a preview and explicit confirmation.
- Local undo/redo, including a complete human/robot turn, legal pawn hints and recent move history.
- Private online rooms and random two/four-player matchmaking with Android or desktop peers on the same server.
- TalkBack virtual board squares/wall anchors and keyboard board navigation.

Names and mode are entered on the start screen. For a wall, choose an orientation, tap the intersection between four cells, inspect the preview and press **Place selected wall**. Selecting a wall does not commit it. The pawn goals and clockwise four-player turn order are the same as desktop.

## Saves and lifecycle

The latest local position is atomically saved in the app's private storage. **Continue saved match** resumes it. **Save** exports a snapshot through Android's document picker; **Load a save file** imports a `.qsave` or compatible old text `.G3M` file through a bounded UTF-8 reader. No broad storage permission is requested. Invalid imports leave the current position and existing recovery file intact.

Android and desktop `.qsave` files are interchangeable. Files retain names, configuration, board, walls, turn and winner; they do not contain online credentials or an undo timeline. History starts fresh after importing/resuming a stored file. Configuration recreation retains the live local history and pending online owner; stale robot/import callbacks cannot replace a newer match.

Online polling pauses in the background and resumes with the same in-memory membership when the app returns. Keep the app open during an online match: the server closes a room after a participant is absent for two minutes. Leaving the online match or finishing the Activity releases membership; pressing Home pauses polling instead. Process death does not retain online membership; the local recovery file remains available. There is no background service or persistent account.

## Server addresses

No public server is deployed or preconfigured. Use the existing [server and hosting guide](ONLINE.md).

- Android phone and desktop on the same Wi-Fi: use the server computer's private IP, for example `http://192.168.1.10:8080`.
- Standard emulator connecting to its host: `http://10.0.2.2:8080`.
- Internet server: its base **HTTPS** URL, with no path, query or embedded credentials.

`localhost` on a phone means the phone, not the computer. Cleartext transport is allowed by the Android manifest for LAN play; the shared client still restricts HTTP to loopback/private numeric addresses, requires HTTPS for public hosts, rejects redirects, and bounds requests/responses. Android's normal TLS certificate checks remain enabled.

## Build prerequisites

Use **JDK 17**, Android SDK platform **35**, build tools **35.0.0**, and the checked-in **Gradle 8.14.3** wrapper with **Android Gradle Plugin 8.13.0**. The first dependency resolution needs network access to Google Maven and Maven Central. `desugar_jdk_libs 2.1.5` supplies supported Java API backports for older Android releases.

Install the SDK with Android Studio or the Android command-line tools. Set `ANDROID_HOME` or pass `-SdkPath` to the PowerShell scripts. For example:

```powershell
$env:ANDROID_HOME = 'E:\sdk'
powershell -ExecutionPolicy Bypass -File .\build-android.ps1 -Configuration Debug
```

Building the current source writes the debug APK under `out/android/releases/v2.6.0`. For Android Studio, open the **`android`** directory; normal Gradle commands work there as well:

```powershell
Set-Location android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The project reads the repository's `VERSION`. The current source `versionName` is `2.6.0`, and `versionCode` is `major * 1000000 + minor * 1000 + patch` (`2006000`). Minor and patch must be below 1000 for Android's monotonic encoding. A new public update must increase the version code.

## Unsigned release and later signing

Build an unsigned artifact from the current source:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-android.ps1 -Unsigned
```

Android will not install an unsigned APK. To sign it later, use Android SDK Build Tools with your own private keystore and alias. For example, from the build-tools directory (replace the private key path and alias):

```powershell
.\zipalign.exe -p -f 4 E:\projects\Game-Quoridor\out\android\releases\v2.2.0\Quoridor-2.2.0-android-unsigned.apk C:\private\Quoridor-2.2.0-aligned.apk
.\apksigner.bat sign --ks C:\private\quoridor.p12 --ks-key-alias your-alias --out C:\private\Quoridor-2.2.0-android.apk C:\private\Quoridor-2.2.0-aligned.apk
.\apksigner.bat verify --verbose C:\private\Quoridor-2.2.0-android.apk
```

`apksigner` prompts for the keystore password. Keep the key private and reuse it for subsequent updates. Signing changes the APK's checksum; compute and publish a new checksum with the signed file.

## Optional signed builds and key backup

If the owner later chooses the project's local signing workflow, create the distribution key once and build a signed release:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-android.ps1 -InitializeSigning
```

Later updates use the same key:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-android.ps1
```

The script aligns the APK, signs it, verifies the signature and atomically promotes the APK/checksum pair into `out/android/releases/v2.6.0`. Signing files stay separately in ignored **`out/android-signing`**:

- `quoridor-local-release.p12` — private persistent distribution key.
- `store-password.txt` — randomly generated key/store password, read from a file rather than printed.

Back up **both files together in a private location before cleaning `out` or moving the project**. Keep the same key for updates to this application's package ID (`com.quoridor.mobile`). These files must never be committed, attached to a release or included in an APK. On Windows, new signing material is restricted to the current user, Administrators and SYSTEM. Existing or incomplete material is never silently overwritten.

Use `-Unsigned` only when another signing process will sign the resulting APK. Debug builds use a development certificate and are not the signed distribution build. Google Play registration, app signing, store metadata and publication are separate owner actions.

## Test

Host unit tests and lint:

```powershell
powershell -ExecutionPolicy Bypass -File .\test-android.ps1 -UnitOnly
```

Complete emulator tests:

```powershell
powershell -ExecutionPolicy Bypass -File .\test-android.ps1
```

The emulator suite requires the installed **Android 35 Google Play x86_64 system image**, platform tools, emulator and working hardware virtualization. It creates its own AVD data under `out/android/emulator`, uses private ADB port `5038` and emulator port `5580`, and refuses occupied ports. It installs only into that owned AVD and cleans up its own processes. Existing AVDs and physical devices are not used. Override the ports when needed; `-KeepEmulator` retains the owned test session for inspection.

The tests cover local state/bot history and stale results, both languages, actual board touch/preview/drawing/accessibility, private atomic saves/import validation, Activity recreation, and real HTTP interoperability with a loopback-only Java room server. Instrumentation logs, screenshots and Gradle reports are kept under `out/android`. The standard desktop/server regression suite is still `test.ps1`.

Release notes distinguish actual emulator checks from physical-phone testing; a successful build alone is not a device test.

## Code and notices

`android/app/src/main/java/com/quoridor/mobile` owns Android presentation, lifecycle and storage adapters. Gradle uses an explicit allowlist from `src/Board` and `src/Online`; do not copy the desktop rules or include Swing/server classes in the APK. Shared state validation and the online protocol are authoritative on both platforms.

`LICENSE`, `AUTHORS.md`, version metadata, [third-party notices](THIRD_PARTY_NOTICES.md) and the backport license are embedded as assets. **Authors & license** displays them, including the exact original developer names:

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

## راهنمای فارسی

نسخهٔ اندروید رابط بومی و لمسی دارد و همان منطق بازی، ربات، قالب ذخیره و پروتکل آنلاین رایانه را استفاده می‌کند. فایل `Quoridor-2.2.0-android-unsigned.apk` را از Release دریافت کنید؛ **این فایل طبق انتخاب شما بدون امضاست و پیش از نصب باید با کلید خودتان امضا شود. کلید انتشار ساخته نشده است.** حداقل نسخهٔ سیستم Android 8.0 است. بازی با دو یا چهار نفر، ربات سه‌سطحی، فارسی و انگلیسی، ظاهر روشن و تاریک، ذخیره/بارگذاری و بازی آنلاین با کاربران رایانه در دسترس است.

برای ساخت، JDK 17 و SDK پلتفرم ۳۵ و Build Tools نسخهٔ 35.0.0 لازم‌اند. `build-android.ps1 -Unsigned` خروجی بدون امضا را می‌سازد. دستورهای `zipalign` و `apksigner` در بخش «Unsigned release and later signing» برای امضای بعدی با کلید خودتان آمده‌اند. اگر بعداً ساخت همراه امضا با اسکریپت پروژه را انتخاب کردید، بار اول `build-android.ps1 -InitializeSigning` و دفعات بعد `build-android.ps1` را اجرا کنید و **هر دو فایل پوشهٔ خصوصی `out/android-signing` را پیش از پاک‌کردن خروجی‌ها یا انتقال پروژه پشتیبان بگیرید**؛ انتشار به‌روزرسانی به همان کلید نیاز دارد. کلید و گذرواژه در Git یا Release قرار نمی‌گیرند.

برای بارگذاری فایل بازی از انتخاب‌گر خود Android استفاده می‌شود و دسترسی گسترده به حافظه لازم نیست. بازی محلی، ذخیرهٔ بازیابی دارد؛ ذخیرهٔ فایل، عضویت آنلاین یا تاریخچهٔ برگشت را منتقل نمی‌کند. تغییرات پیکربندی، بازی زنده را حفظ می‌کنند؛ پایان فرایند برنامه عضویت آنلاین را نگه نمی‌دارد.

برای شبکهٔ محلی گوشی، IP رایانهٔ میزبان مانند `http://192.168.1.10:8080` را وارد کنید. در شبیه‌ساز، `http://10.0.2.2:8080` به میزبان اشاره دارد. برای اینترنت از HTTPS و [راهنمای میزبانی](ONLINE.md) استفاده کنید. سرور عمومی آماده تنظیم نشده است. هنگام بازی آنلاین برنامه را باز نگه دارید؛ غیبت دو دقیقه‌ای اتاق را می‌بندد.

`test-android.ps1` آزمون‌ها را روی شبیه‌ساز جداگانهٔ همین پروژه اجرا می‌کند. دستگاه واقعی و شبیه‌سازهای شخصی استفاده نمی‌شوند. ساخت و امضای APK به معنای انتشار در Google Play یا فروشگاه‌های دیگر نیست.
