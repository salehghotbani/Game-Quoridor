# Quoridor 2.2.0 — Android and desktop

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
| `Quoridor-2.2.0-android.apk` | Signed native Android app; Android 8.0+ |
| `Quoridor-2.2.0-windows-x64-setup.exe` | Windows x64 installer with Java included |
| `Quoridor-2.2.0-windows-x64.zip` | Extract fully, run `Quoridor/Quoridor.exe` with `app/` and `runtime/` beside it |
| `Quoridor-2.2.0.jar` | Desktop app for Java 17+ |
| `Quoridor-2.2.0-server.jar` | Headless room server for Java 17+ |
| `SHA256SUMS.txt` | SHA-256 hashes of all five downloads above |

The APK uses a persistent private local distribution key and is not an app-store publication. Windows EXEs are unsigned; the installer is built/inspected without installing it on this development machine. Verification details below distinguish emulator checks from physical-phone testing.

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

برای اندروید `Quoridor-2.2.0-android.apk` را دریافت کنید. APK با کلید خصوصی ثابتِ انتشار امضا شده و در فروشگاه منتشر نشده است. برای ویندوز، نصب‌کننده یا ZIP کامل را انتخاب کنید؛ Java همراه بسته‌های ویندوز است. checksum هر پنج فایل در `SHA256SUMS.txt` قرار دارد.

**سرور عمومی از قبل تنظیم یا مستقر نشده است.** گوشی در Wi-Fi مشترک از IP خصوصی رایانهٔ میزبان و شبیه‌ساز استاندارد از `http://10.0.2.2:8080` استفاده می‌کند. برای اینترنت HTTPS لازم است. راهنمای اندروید و میزبانی در پیوندهای بالای صفحه است.

اتاق‌ها با راه‌اندازی دوبارهٔ سرور از بین می‌روند. هنگام بازی آنلاین برنامه را باز نگه دارید؛ غیبت دو دقیقه‌ای مسابقه را می‌بندد و پایان فرایند برنامه عضویت را حفظ نمی‌کند. فایل ذخیره فقط وضعیت بازی را برای ادامهٔ محلی نگه می‌دارد. راهنما و برگشت حرکت در حالت محلی فعال‌اند.

توسعه‌دهندگان اولیه: **سیدمحمدصالح قطبانی، محمدجواد بنان و امیرمحمد خسروی**. نام‌ها در مستندات، مجوز MIT، بسته‌ها و بخش «سازندگان و مجوز» اندروید حفظ شده‌اند.
