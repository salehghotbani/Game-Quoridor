# Building and releasing Quoridor

## Web deployment from main

The browser edition deploys independently of native download releases. Run `test-web.ps1`, push the reviewed working branch, then merge it into `main`. [`.github/workflows/web.yml`](.github/workflows/web.yml) repeats Node/Java/browser tests and deploys their exact static artifact to GitHub Pages. Verify the Actions deployment, public site and `version.json` source commit after merging. See [WEB.md](WEB.md) for setup and CORS. Do not edit or replace existing Android/Windows release assets when publishing only a web update.

## Version source

`VERSION` is the single source of the application version. Its value is a numeric `MAJOR.MINOR.PATCH` without a `v` prefix or leading zeros. Windows installer limits are `255.255.65535`.

The build copies it into `app-version.properties`, the JAR's `Implementation-Version`, the window title, and Windows launcher/installer metadata. Release names and filenames also use it. The GitHub tag is `v` followed by that version, for example `v2.4.0`.

Android reads the same value and encodes `versionCode` as `major * 1000000 + minor * 1000 + patch`; Android minor/patch must remain below 1000. See [ANDROID.md](ANDROID.md).

Application versioning is independent of the `.qsave` data-format version. See [CHANGELOG.md](CHANGELOG.md) for the versioning policy and release history.

## Build the Windows packages

Use Windows x64 with a full x64 JDK 17 or newer containing `java`, `javac`, `jar`, `jlink`, and `jpackage`. Run from the project directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -DownloadWix
```

The installer needs WiX 3. `-DownloadWix` permits downloading the pinned official WiX 3.14.1 build tools into ignored `out/tools`, verifying their SHA-256 checksum, and extracting them locally. It does not install WiX on the machine. Subsequent builds use the cache. You can instead put WiX 3 on `PATH` or pass `-WixDirectory` with the directory containing `candle.exe` and `light.exe`.

For a portable package without WiX or an installer:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -PortableOnly
```

Outputs for `2.4.0` are under `out/releases/v2.4.0`:

| Asset | Contents |
| --- | --- |
| `Quoridor-2.4.0-windows-x64-setup.exe` | Per-user Windows installer with menu/shortcut options and its own runtime |
| `Quoridor-2.4.0-windows-x64.zip` | Portable `Quoridor` folder with native EXE, application, runtime, documentation and notices |
| `Quoridor-2.4.0.jar` | Standalone application for a Java 17+ graphical desktop |
| `Quoridor-2.4.0-server.jar` | Standalone room server for Java 17+, without a desktop requirement |
| `SHA256SUMS.txt` | SHA-256 hashes of the application assets produced by the build |

The native launcher is also available at `out/windows/v2.4.0/app-image/Quoridor/Quoridor.exe`. Keep its `app` and `runtime` directories beside it. Copying just the EXE is insufficient.

The bundled runtime retains the legal notices and the English/Persian locale data needed by the game. A system Java installation is not needed to run the Windows packages. The installer uses a stable upgrade UUID; keep that UUID when releasing updates to this application.

Windows builds also run `build-server.ps1` and emit the versioned server JAR. Hosting instructions and the Docker/Render templates are documented in [ONLINE.md](ONLINE.md). Include the HTTP client and EC cryptography providers when changing the linked runtime; online HTTPS must work in the distributed application.

These build scripts do not code-sign executables. A separately signed distribution requires the publisher's own signing certificate and a fresh checksum file after signing.

## Verify a candidate

```powershell
powershell -ExecutionPolicy Bypass -File .\format.ps1 -Check
powershell -ExecutionPolicy Bypass -File .\test.ps1
powershell -ExecutionPolicy Bypass -File .\tests\TestWindowsPackage.ps1 -PackageZip .\out\releases\v2.4.0\Quoridor-2.4.0-windows-x64.zip
java -jar .\out\releases\v2.4.0\Quoridor-2.4.0.jar --version
```

The package validator extracts into a path containing spaces, checks the x64 executable and its version, validates the archive and notices, and runs tests with the bundled Java runtime. Its child environment excludes system Java settings. Graphical mode also checks the native launcher window and closes only the launcher processes created by the test. `-Headless` skips the GUI/native-window checks; use graphical mode before a Windows release.

Inspect the final assets and compare hashes with `SHA256SUMS.txt`. Verify the installer metadata and perform an installation check in a disposable Windows test environment when available. Building an installer does not install it on the development machine.

## Prepare the release

1. Set `VERSION` and update `CHANGELOG.md`, `RELEASE_NOTES.md`, and version examples in both READMEs.
2. Verify the confirmed original developer credits remain consistent in `AUTHORS.md`, both README tables, `LICENSE`, and the packaged notices: سیدمحمدصالح قطبانی، محمدجواد بنان، امیرمحمد خسروی. Keep this order and any later contributor credits separate.
3. Run the applicable desktop and Android checks, commit the source, push the working branch, merge it into `main`, and push `main`. Verify the merged tree equals the tested source.
4. Build the final packages from that commit, then validate the actual ZIP and its checksums.
5. In GitHub Releases, create a **draft** using `vVERSION` and the exact tested commit as its target. Paste `RELEASE_NOTES.md` and attach the Android APK, Windows installer, portable ZIP, desktop JAR, server JAR, and a checksum file covering all five downloads. State the Android signing status explicitly; the published 2.2.0 native release uses the `-android-unsigned.apk` artifact and requires owner signing before installation.
6. Review the draft and publish when the files and credits are final. The release tag must identify the source used to build its assets. Do not move a published version tag or silently replace its binaries; increment the version for a correction.

For example, use the GitHub CLI after choosing the tested commit:

```powershell
$releaseAssets = @(Get-ChildItem -LiteralPath .\out\release-publication\v2.4.0\assets -File | Select-Object -ExpandProperty FullName)
gh release create v2.4.0 @releaseAssets --repo salehghotbani/Game-Quoridor --target TESTED_COMMIT --draft --title "Quoridor 2.4.0" --notes-file RELEASE_NOTES.md
```

Replace `TESTED_COMMIT` with the full commit SHA. If the tag already exists, verify that it identifies that commit before creating the draft. Update the draft's target and rebuild its assets if the source changes before publication.

## Android and combined release assets

Follow [ANDROID.md](ANDROID.md) for SDK setup and later signing. For this release, run `build-android.ps1 -Unsigned`; no distribution key is created. Run `test-android.ps1` for actual emulator tests, which use a debug-signed QA build. Optional signed builds require the owner's separate signing setup. Android outputs stay separate under `out/android/releases/v2.4.0`, while desktop/server outputs remain under `out/releases/v2.4.0`.

For a combined release, copy the four Windows/JAR downloads and the unsigned APK into a fresh ignored publication directory, then generate a single `SHA256SUMS.txt` for those **five** files. Upload that combined manifest once; do not upload both platform manifests with the same filename. Preserve the original per-platform build outputs and all older published release assets. Check every uploaded asset's size and SHA-256 before publishing. After later signing, generate new checksums for the signed artifact. Android signing material never belongs in the publication directory.

## راهنمای کوتاه فارسی

شمارهٔ نسخه فقط در `VERSION` تعیین می‌شود و اسکریپت‌ها آن را در JAR، فایل‌های EXE و نام بسته‌ها ثبت می‌کنند. برای ساخت نصب‌کننده و نسخهٔ قابل‌حمل، `build-windows.ps1 -DownloadWix` را اجرا کنید. خروجی‌ها در `out/releases/v2.4.0` قرار می‌گیرند. برای اجرای نسخهٔ قابل‌حمل، کل ZIP را استخراج کنید و فایل `Quoridor.exe` را همراه پوشه‌های کناری آن نگه دارید.

پیش از انتشار، آزمون بسته، نام سه توسعه‌دهنده و متن توضیحات نسخه را بررسی کنید. فایل‌های نصب‌کننده، ZIP، JAR بازی، JAR سرور و `SHA256SUMS.txt` را در Release پیش‌نویس قرار دهید و پس از نهایی‌شدن، منتشر کنید. فایل‌های EXE این فرایند امضای دیجیتال ندارند؛ اسکریپت ساخت نیز برنامه را روی رایانه نصب نمی‌کند.
