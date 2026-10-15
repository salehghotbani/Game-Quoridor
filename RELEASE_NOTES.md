# Quoridor 2.1.1

[English guide](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/README.md) · [راهنمای فارسی](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/README.fa.md) · [Online play and hosting](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/ONLINE.md)

## English

This credits-only patch records the confirmed original developers in the documentation and distributed notices. Gameplay, online protocol, save formats, and application behavior are unchanged from 2.1.0.

The desktop game includes the features introduced in 2.1.0:

- **2 or 4 human players**, locally or online. Four-player games use five walls per player and clockwise turns: Blue → Red → Green → Gold.
- **Private rooms** with an eight-character code and **random matchmaking** in separate two- and four-player queues.
- An authoritative HTTP server validates credentials, turn ownership, revisions and every move. Clients synchronize confirmed positions and retry brief disconnections.
- **Undo/redo** in local games, including a full human/robot turn; a legal pawn-move **hint**; and a localized **move history**.
- Four-player `.qsave` files with strict validation. Existing two-player saves and compatible old `.G3M` files remain supported.
- English/Persian, Dark/Light, the three-level robot, and the existing start/save/load workflow remain available.
- A standalone server JAR, Dockerfile, Render Free deployment template and bilingual hosting guide.

### Downloads

| File | Use |
| --- | --- |
| `Quoridor-2.1.1-windows-x64-setup.exe` | Windows x64 installer with Java included |
| `Quoridor-2.1.1-windows-x64.zip` | Extract the entire folder and run `Quoridor/Quoridor.exe`; keep `app/` and `runtime/` beside it |
| `Quoridor-2.1.1.jar` | Desktop application for Java 17+ |
| `Quoridor-2.1.1-server.jar` | Headless room server for Java 17+ |
| `SHA256SUMS.txt` | SHA-256 hashes of all four application/server downloads |

Windows packages include the HTTP/TLS runtime needed for online play and do not require a system Java installation. Both EXEs are unsigned. The installer is built and inspected; installation itself has not been tested on a disposable Windows machine.

### Online setup and limits

**No public server is preconfigured or deployed by this release.** All players enter the same server address. Run the server locally/on a LAN, or deploy the supplied template in your hosting account. GitHub Pages cannot run this Java server. [ONLINE.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/ONLINE.md) gives exact commands and Render setup instructions.

Rooms are in memory: a server restart discards them. A participant absent for two minutes closes a match; an unfilled waiting room expires after fifteen minutes. Brief network failures retry the same membership. Free hosting has cold starts and availability limits. There are no persistent accounts, ratings, chat, or shared rooms between server replicas.

Undo/redo and hints are local-only. Logs keep up to 1,000 moves. A saved file contains the board/configuration for later local play, not an online membership or undo timeline.

Regression coverage includes two/four-player rules, compatible saves, history, real HTTP authority/concurrency/matchmaking, reconnect and cancellation, multiple synchronized Swing windows, expired rooms, both languages/themes, and the packaged runtime/native EXE.

### Original developers

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

The same confirmed credits appear in [AUTHORS.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/AUTHORS.md), both READMEs, and the distributed author/license notices.

## فارسی

نسخهٔ **۲.۱.۱** فقط نام‌های تأییدشدهٔ توسعه‌دهندگان اولیه را در مستندات و فایل‌های نام نویسندگان و مجوزِ بسته‌های انتشار ثبت می‌کند. قواعد بازی، پروتکل آنلاین، قالب ذخیره و رفتار برنامه نسبت به نسخهٔ ۲.۱.۰ تغییری نکرده‌اند.

امکانات بازی از نسخهٔ ۲.۱.۰ در دسترس هستند:

- بازی **دو و چهارنفری، محلی و آنلاین**؛ در حالت چهارنفری هر بازیکن پنج دیوار دارد و نوبت‌ها ساعت‌گرد هستند.
- **اتاق خصوصی با کد هشت‌نویسه‌ای** و **یافتن حریف تصادفی** برای دو یا چهار نفر.
- بررسی مجازبودن حرکت و نوبت در سرور، همگام‌سازی صفحه‌ها و تلاش برای اتصال دوباره پس از قطع کوتاه‌مدت.
- **برگشت و تکرار حرکت** در بازی محلی، برگشت نوبت کامل انسان و ربات، **راهنمای حرکت** و **تاریخچهٔ حرکت‌ها**.
- ذخیرهٔ چهارنفری با حفظ سازگاری ذخیره‌های دونفره و فایل‌های متنی قدیمی سازگار.
- سرور مستقل و فایل‌های آمادهٔ Docker و Render همراه راهنمای فارسی میزبانی.

برای ویندوز، نصب‌کننده یا ZIP را انتخاب کنید. نسخهٔ قابل‌حمل باید کامل استخراج شود و EXE همراه پوشه‌های `app/` و `runtime/` بماند. Java همراه بسته‌های ویندوز است. فایل‌های EXE امضای دیجیتال ندارند؛ فرایند نصب نصب‌کننده در محیط جداگانه آزموده نشده است.

**سرور عمومی از قبل تنظیم یا مستقر نشده است.** همهٔ بازیکنان باید نشانی یک سرور را وارد کنند. برای شروع محلی یا میزبانی رایگان Render، مراحل [ONLINE.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/ONLINE.md) را دنبال کنید. GitHub Pages سرور Java را اجرا نمی‌کند. اتاق‌ها با راه‌اندازی دوبارهٔ سرور از بین می‌روند و پلن رایگان محدودیت خواب و بیدارشدن دارد.

برگشت حرکت و راهنما در بازی محلی فعال‌اند. فایل ذخیره فقط وضعیت بازی و تنظیمات را نگه می‌دارد و اتاق آنلاین یا تاریخچهٔ برگشت حرکت را بازیابی نمی‌کند.

### توسعه‌دهندگان اولیه

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

همین نام‌های تأییدشده در [AUTHORS.md](https://github.com/salehghotbani/Game-Quoridor/blob/v2.1.1/AUTHORS.md)، هر دو README و فایل‌های نام نویسندگان و مجوزِ بسته‌های انتشار آمده‌اند.
