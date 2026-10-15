# Building and releasing Quoridor

## Version source

`VERSION` is the single source of the application version. Its value is a numeric `MAJOR.MINOR.PATCH` without a `v` prefix or leading zeros. Windows installer limits are `255.255.65535`.

The build copies it into `app-version.properties`, the JAR's `Implementation-Version`, the window title, and Windows launcher/installer metadata. Release names and filenames also use it. The GitHub tag is `v` followed by that version, for example `v2.0.0`.

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

Outputs for `2.0.0` are under `out/releases/v2.0.0`:

| Asset | Contents |
| --- | --- |
| `Quoridor-2.0.0-windows-x64-setup.exe` | Per-user Windows installer with menu/shortcut options and its own runtime |
| `Quoridor-2.0.0-windows-x64.zip` | Portable `Quoridor` folder with native EXE, application, runtime, documentation and notices |
| `Quoridor-2.0.0.jar` | Standalone application for a Java 17+ graphical desktop |
| `SHA256SUMS.txt` | SHA-256 hashes of the application assets produced by the build |

The native launcher is also available at `out/windows/v2.0.0/app-image/Quoridor/Quoridor.exe`. Keep its `app` and `runtime` directories beside it. Copying just the EXE is insufficient.

The bundled runtime retains the legal notices and the English/Persian locale data needed by the game. A system Java installation is not needed to run the Windows packages. The installer uses a stable upgrade UUID; keep that UUID when releasing updates to this application.

These build scripts do not code-sign executables. A separately signed distribution requires the publisher's own signing certificate and a fresh checksum file after signing.

## Verify a candidate

```powershell
powershell -ExecutionPolicy Bypass -File .\format.ps1 -Check
powershell -ExecutionPolicy Bypass -File .\test.ps1
powershell -ExecutionPolicy Bypass -File .\tests\TestWindowsPackage.ps1 -PackageZip .\out\releases\v2.0.0\Quoridor-2.0.0-windows-x64.zip
java -jar .\out\releases\v2.0.0\Quoridor-2.0.0.jar --version
```

The package validator extracts into a path containing spaces, checks the x64 executable and its version, validates the archive and notices, and runs tests with the bundled Java runtime. Its child environment excludes system Java settings. Graphical mode also checks the native launcher window and closes only the launcher processes created by the test. `-Headless` skips the GUI/native-window checks; use graphical mode before a Windows release.

Inspect the final assets and compare hashes with `SHA256SUMS.txt`. Verify the installer metadata and perform an installation check in a disposable Windows test environment when available. Building an installer does not install it on the development machine.

## Prepare the release

1. Set `VERSION` and update `CHANGELOG.md`, `RELEASE_NOTES.md`, and version examples in both READMEs.
2. Complete the three original developer names in `AUTHORS.md` and both README tables before final publication. Keep any later contributor credits separate.
3. Run the checks, commit the source, and push it to `develop`.
4. Build the final packages from that commit, then validate the actual ZIP and its checksums.
5. In GitHub Releases, create a **draft** using `vVERSION` and the exact tested commit as its target. Paste `RELEASE_NOTES.md` and attach the installer, portable ZIP, JAR, and checksum file.
6. Review the draft and publish when the files and credits are final. The release tag must identify the source used to build its assets. Do not move a published version tag or silently replace its binaries; increment the version for a correction.

For example, use the GitHub CLI after choosing the tested commit:

```powershell
gh release create v2.0.0 --repo salehghotbani/Game-Quoridor --target TESTED_COMMIT --draft --title "Quoridor 2.0.0" --notes-file RELEASE_NOTES.md .\out\releases\v2.0.0\Quoridor-2.0.0-windows-x64-setup.exe .\out\releases\v2.0.0\Quoridor-2.0.0-windows-x64.zip .\out\releases\v2.0.0\Quoridor-2.0.0.jar .\out\releases\v2.0.0\SHA256SUMS.txt
```

Replace `TESTED_COMMIT` with the full commit SHA. If the tag already exists, verify that it identifies that commit before creating the draft. Update the draft's target and rebuild its assets if the source changes before publication.

## راهنمای کوتاه فارسی

شمارهٔ نسخه فقط در `VERSION` تعیین می‌شود و اسکریپت‌ها آن را در JAR، فایل‌های EXE و نام بسته‌ها ثبت می‌کنند. برای ساخت نصب‌کننده و نسخهٔ قابل‌حمل، `build-windows.ps1 -DownloadWix` را اجرا کنید. خروجی‌ها در `out/releases/v2.0.0` قرار می‌گیرند. برای اجرای نسخهٔ قابل‌حمل، کل ZIP را استخراج کنید و فایل `Quoridor.exe` را همراه پوشه‌های کناری آن نگه دارید.

پیش از انتشار، آزمون بسته، نام سه توسعه‌دهنده و متن توضیحات نسخه را بررسی کنید. فایل‌های نصب‌کننده، ZIP، JAR و `SHA256SUMS.txt` را در Release پیش‌نویس قرار دهید و پس از نهایی‌شدن، منتشر کنید. فایل‌های EXE این فرایند امضای دیجیتال ندارند؛ اسکریپت ساخت نیز برنامه را روی رایانه نصب نمی‌کند.
