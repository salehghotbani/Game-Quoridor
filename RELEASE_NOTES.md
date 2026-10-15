# Quoridor 2.0.0

[English guide](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/README.md) · [راهنمای فارسی](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/README.fa.md) · [Changelog](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/CHANGELOG.md) · [MIT license](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/LICENSE)

## English

Quoridor 2.0.0 rebuilds the project as a desktop board game. Play locally with another person or face a robot at **Easy**, **Medium**, or **Hard** difficulty. The start menu supports new matches, saved games, and resuming a paused match.

The interface offers **English and Persian**, including a right-to-left Persian layout, and **Dark and Light** appearance. Language and appearance can change during a match without restarting it or interrupting the robot's turn. Each launch starts in Dark mode.

New `.qsave` files retain the complete match. Compatible old text-based `.G3M` files can also be imported. Loading an invalid file leaves the current match available. The VLC player, old video, and unrelated sections have been removed. The code and contributor workflow have also been reorganized.

### Packages

| File | Purpose |
| --- | --- |
| `Quoridor-2.0.0-windows-x64-setup.exe` | Windows x64 installer with a bundled Java runtime. |
| `Quoridor-2.0.0-windows-x64.zip` | Portable Windows x64 application folder with its runtime. |
| `Quoridor-2.0.0.jar` | Optional runnable JAR for a Java 17+ graphical desktop. |
| `SHA256SUMS.txt` | SHA-256 checksums for the downloadable application packages. |

### Install or run

**Windows installer:** run `Quoridor-2.0.0-windows-x64-setup.exe`, complete installation, then open Quoridor. The Windows packages include Java and do not require a system Java installation.

**Portable Windows package:** extract the entire ZIP, then open `Quoridor/Quoridor.exe` inside the extracted folder. Keep `Quoridor.exe`, `app/`, and `runtime/` together; copying the EXE by itself does not produce a runnable application.

For example, in PowerShell from the download folder:

```powershell
Expand-Archive .\Quoridor-2.0.0-windows-x64.zip -DestinationPath .\Quoridor-2.0.0
& .\Quoridor-2.0.0\Quoridor\Quoridor.exe
```

**JAR:** with Java 17 or newer available on `PATH`:

```powershell
java -jar .\Quoridor-2.0.0.jar
```

To check a download, compare the result of this command with its matching entry in `SHA256SUMS.txt`:

```powershell
Get-FileHash .\Quoridor-2.0.0-windows-x64.zip -Algorithm SHA256
```

### Validation and signing

The automated source tests and bundled-runtime package tests passed. Validation covered the archive contents, notices, version metadata, and a successful launch of the native Windows EXE with its bundled Java runtime. The installer was built and inspected; installation has not been tested.

The portable launcher and setup EXE are both unsigned.

Source build and test instructions are in [README.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/README.md). Original developer credits are maintained in [AUTHORS.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/AUTHORS.md); unconfirmed names remain explicit placeholders.

## فارسی

نسخهٔ **۲.۰.۰ کوریدور** پروژه را به یک بازی رومیزی دسکتاپ تبدیل می‌کند. می‌توانید دونفره روی یک رایانه بازی کنید یا با ربات در سه درجهٔ **آسان، متوسط و سخت** رقابت کنید. منوی شروع، بازی جدید، بارگذاری فایل ذخیره و ادامهٔ مسابقهٔ متوقف‌شده را در اختیار می‌گذارد.

رابط فقط **فارسی و انگلیسی** دارد و از چیدمان راست‌به‌چپ فارسی و ظاهر **تاریک و روشن** پشتیبانی می‌کند. تغییر زبان یا ظاهر، مسابقه را از ابتدا شروع نمی‌کند و نوبت ربات را قطع نمی‌کند. ظاهر پیش‌فرض در هر بار اجرا **تاریک** است.

فایل‌های جدید `.qsave` وضعیت کامل مسابقه را نگه می‌دارند. ذخیره‌های متنی سازگار `.G3M` از نسخهٔ قدیمی نیز قابل‌بارگذاری هستند. فایل نامعتبر جای مسابقهٔ فعلی را نمی‌گیرد. پخش‌کنندهٔ VLC، کلیپ قدیمی و بخش‌های نامرتبط حذف شده‌اند و ساختار کد و راهنمای مشارکت نیز بازنویسی شده است.

### انتخاب بسته و اجرا

- **نصب در ویندوز ۶۴بیتی:** فایل `Quoridor-2.0.0-windows-x64-setup.exe` را اجرا کنید و پس از نصب، کوریدور را باز کنید. بسته‌های ویندوز Java را همراه خود دارند و به نصب جداگانهٔ آن نیاز ندارند.
- **نسخهٔ قابل‌حمل ویندوز:** تمام محتوای `Quoridor-2.0.0-windows-x64.zip` را استخراج و سپس فایل `Quoridor/Quoridor.exe` را اجرا کنید. فایل EXE و پوشه‌های `app/` و `runtime/` باید کنار هم بمانند؛ جابه‌جایی EXE به‌تنهایی کافی نیست.
- **فایل JAR:** برای اجرای `Quoridor-2.0.0.jar`، Java 17 یا جدیدتر و دسکتاپ گرافیکی لازم است. فرمان اجرا در بخش انگلیسی بالا آمده است.
- **بررسی فایل دانلودشده:** خروجی فرمان `Get-FileHash` را با مقدار همان فایل در `SHA256SUMS.txt` مقایسه کنید.

آزمون‌های خودکار کد پروژه و آزمون‌های بسته با Java همراه آن با موفقیت اجرا شده‌اند. محتوای بسته، اطلاعیه‌ها و اطلاعات نسخه بررسی شده و فایل EXE بازی با Java همراه خود با موفقیت باز شده است. نصب‌کننده ساخته و بررسی شده، اما فرایند نصب آن آزموده نشده است. فایل‌های EXE اجراکننده و نصب‌کننده، هر دو بدون امضای دیجیتال هستند.

راهنمای کامل اجرا، ساخت و آزمون در [README.fa.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/README.fa.md) و محل ثبت نام سه توسعه‌دهندهٔ اولیه در [AUTHORS.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.0.0/AUTHORS.md) قرار دارد. نام‌های تأییدنشده با جای‌نگهدار مشخص شده‌اند.
