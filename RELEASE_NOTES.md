# Quoridor 2.7.1 — Android compatibility candidate

Current source supports **Android 5.0+ (API 21+)**, including Galaxy S5 updated to
Android 5/6. Shabnam, native dialogs, system-bar contrast, accessibility and shared
save/network APIs are compatible with the older runtime. Two/four-player games,
three robot levels, saves, five languages and explicit Android wall tools remain.

The Android distribution remains **unsigned**; sign it before installation using
your own key. No release key was created. The published 2.2.0 artifacts listed
below have not been replaced and still require Android 8. Physical Samsung/ARM
hardware has not been tested; see [ANDROID.md](ANDROID.md) for owned-emulator
verification and current build instructions.

نسخهٔ فعلی ۲.۷.۱ از اندروید ۵ و جدیدتر پشتیبانی می‌کند؛ Galaxy S5 با اندروید ۵ یا
۶ را شامل می‌شود. اندروید ۴.۴ پشتیبانی نمی‌شود. خروجی انتشار همچنان بدون امضا است
و برای نصب باید با کلید خودتان امضا شود. دانلودهای قدیمی زیر جایگزین نشده‌اند.

---

## Published Quoridor 2.2.0 — Android and desktop

[English guide](https://github.com/salehghotbani/Game-Quoridor/blob/v2.2.0/README.md) · [راهنمای فارسی](https://github.com/salehghotbani/Game-Quoridor/blob/v2.2.0/README.fa.md) · [Android](https://github.com/salehghotbani/Game-Quoridor/blob/v2.2.0/ANDROID.md) · [Online hosting](https://github.com/salehghotbani/Game-Quoridor/blob/v2.2.0/ONLINE.md)

## English

This release adds a **native Android app for Android 8.0+ (API 26+)**. It directly shares Java rules, robot, save formats and online protocol with desktop.

- Native touch board, legal move highlights, horizontal/vertical wall previews and explicit confirmation; TalkBack virtual squares and keyboard navigation.
- Local **two/four-player** matches or a two-player game against **Easy, Medium or Hard robots**.
- **English/Persian**, stable physical board coordinates, **dark/light** appearance and themed dialogs.
- Local undo/redo including paired human/robot turns, legal pawn hints and move history.
- Atomic device recovery, document import/export and desktop-compatible `.qsave` files; compatible legacy text `.G3M` imports.
- **Shared Android/desktop rooms**, private codes and random two/four-player matchmaking on the existing Java server.
- Configuration recreation retains the live match and room owner; stale robot/import callbacks cannot replace a newer game.

Shared HTTP transport now uses bounded `HttpURLConnection`, preserving strict room/revision validation, bearer credentials, normal TLS checks, deadlines, cancellation, no redirects and no automatic POST replay. Rules, save formats and server protocol remain compatible with 2.1.x.

### Downloads

| File | Use |
| --- | --- |
| `Quoridor-2.2.0-android-unsigned.apk` | Native Android app for Android 8.0+; sign before installing |
| `Quoridor-2.2.0-windows-x64-setup.exe` | Windows x64 installer with Java included |
| `Quoridor-2.2.0-windows-x64.zip` | Extract fully, run `Quoridor/Quoridor.exe` with `app/` and `runtime/` beside it |
| `Quoridor-2.2.0.jar` | Desktop app for Java 17+ |
| `Quoridor-2.2.0-server.jar` | Headless room server for Java 17+ |
| `SHA256SUMS.txt` | SHA-256 hashes of all five downloads above |

**The APK is unsigned at the maintainer's request and must be signed before installation. No release signing key was created.** The Android guide explains later signing. Windows EXEs are unsigned; the installer is built/inspected without installing it on this development machine. Emulator tests use a debug-signed QA build of the same application source; physical phones have not been tested. This is not an app-store publication.

**No public server is preconfigured or deployed.** All players enter the same base address. Phones on shared Wi-Fi use the server computer's private IP; the standard emulator reaches its host at `http://10.0.2.2:8080`. Public hosts require HTTPS. Docker/Render Free instructions are in the hosting guide; GitHub Pages cannot run the Java server.

Rooms disappear on server restart. A two-minute participant absence closes a room. Android polls while in the foreground; process death does not preserve membership. Saves restore a board/configuration for local play, not online credentials or undo history. Undo/redo and hints are local-only. There are no persistent accounts, ratings or chat.

### Original developers

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

The names remain in `AUTHORS.md`, both READMEs, MIT copyright and distributed notices. Android's **Authors & license** displays these and its [third-party notices](https://github.com/salehghotbani/Game-Quoridor/blob/v2.2.0/THIRD_PARTY_NOTICES.md).

## فارسی

نسخهٔ **۲.۲.۰** برنامهٔ **بومی و لمسی اندروید برای Android 8.0 و جدیدتر** را اضافه می‌کند. منطق بازی، ربات، قالب ذخیره و پروتکل آنلاین با نسخهٔ رایانه مشترک‌اند.

- بازی محلی **دو و چهارنفری** و ربات دونفره با سه سطح **آسان، متوسط و قوی**.
- **فارسی و انگلیسی**، ظاهر **روشن و تاریک**، حرکت‌های مجاز، پیش‌نمایش دیوار و تأیید جداگانهٔ آن.
- برگشت و تکرار حرکت، برگشت نوبت کامل انسان و ربات، راهنمای حرکت و تاریخچه.
- ذخیرهٔ بازیابی روی دستگاه و واردکردن/صادرکردن فایل با انتخاب‌گر Android؛ ذخیره‌ها با رایانه سازگارند.
- اتاق آنلاین مشترک میان کاربران **اندروید و رایانه**، کد خصوصی و یافتن حریف تصادفی برای دو یا چهار نفر.
- حفظ بازی و اتاق هنگام تغییرات پیکربندی و جلوگیری از اعمال نتیجهٔ قدیمی ربات یا بارگذاری فایل روی بازی جدید.

برای اندروید `Quoridor-2.2.0-android-unsigned.apk` را دریافت کنید. **طبق انتخاب صاحب پروژه، APK بدون امضاست و پیش از نصب باید با کلید خودتان امضا شود؛ کلید انتشار ساخته نشده است.** راهنمای امضای بعدی در `ANDROID.md` آمده است. آزمون‌های شبیه‌ساز روی نسخهٔ QA با امضای توسعه از همین کد انجام شده‌اند؛ گوشی واقعی آزمایش نشده است. برای ویندوز، نصب‌کننده یا ZIP کامل را انتخاب کنید؛ Java همراه بسته‌های ویندوز است. checksum هر پنج فایل در `SHA256SUMS.txt` قرار دارد.

**سرور عمومی از قبل تنظیم یا مستقر نشده است.** گوشی در Wi-Fi مشترک از IP خصوصی رایانهٔ میزبان و شبیه‌ساز استاندارد از `http://10.0.2.2:8080` استفاده می‌کند. برای اینترنت HTTPS لازم است. راهنمای اندروید و میزبانی در پیوندهای بالای صفحه است.

اتاق‌ها با راه‌اندازی دوبارهٔ سرور از بین می‌روند. هنگام بازی آنلاین برنامه را باز نگه دارید؛ غیبت دو دقیقه‌ای مسابقه را می‌بندد و پایان فرایند برنامه عضویت را حفظ نمی‌کند. فایل ذخیره فقط وضعیت بازی را برای ادامهٔ محلی نگه می‌دارد. راهنما و برگشت حرکت در حالت محلی فعال‌اند.

توسعه‌دهندگان اولیه: **سیدمحمدصالح قطبانی، محمدجواد بنان و امیرمحمد خسروی**. نام‌ها در مستندات، مجوز MIT، بسته‌ها و بخش «سازندگان و مجوز» اندروید حفظ شده‌اند.
