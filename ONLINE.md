# Online play and hosting

[راهنمای فارسی](#فارسی)

Android and desktop support shared private rooms and random matchmaking for **2 or 4 human players**. Everyone must use the **same server address** and compatible protocol version. There is no built-in public server address: run your own server or deploy the included template, then share its URL.

## Play

Choose **Play online** on the start screen. Enter the server's base address, your name, and the player count.

- **Create match:** make a private room and share its eight-character code. Other players choose **Join match** on the same server and enter that code. The room determines the joining player's count.
- **Find opponents:** join the public queue for your selected count. The match starts automatically when all seats fill. Private rooms never enter this queue.
- **Cancel / Leave room:** leave the queue or room. Leaving an active match closes it for the other players.

The server validates the current turn, movement, walls, and state revision. A client cannot play another seat's turn. Moves appear on other computers after the next poll, normally within about one second. Undo, redo and hints are available in local matches; online matches use the server's confirmed position.

Brief connection failures disable input while the client retries the same membership. A participant absent for two minutes closes the match. Expired credentials or a server restart end the old room; start or join a new one. There are no persistent accounts, ratings, chat, or cross-server matchmaking.

The move log keeps up to 1,000 recent moves. A downloaded `.qsave` contains the current board/configuration for a later local match; it does not restore an online room or the undo timeline.

## Run locally or on a LAN

With a full JDK 17+ available, from the repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-server.ps1 -Port 8080
```

Keep this process running. On that computer use `http://localhost:8080`. Other computers on the same network use its private IPv4 address, for example `http://192.168.1.10:8080`. The host's firewall must allow the chosen inbound port. The scripts do not modify firewall or router settings.

Android phones on the same Wi-Fi use that private IP as well: `localhost` on a phone means the phone itself. In the standard Android emulator, `http://10.0.2.2:8080` reaches the development computer. The Android app polls while in the foreground; keep it open during an online match. Configuration changes retain the room, but process death or a two-minute absence does not preserve membership. See [ANDROID.md](ANDROID.md).

To run the separately downloaded server JAR with Java 17+:

```powershell
$env:PORT = '8080'
java --add-modules jdk.httpserver -Xms32m -Xmx256m -jar .\Quoridor-2.2.0-server.jar
```

`GET /health` returns status `ok` and protocol `1`. This server binds to `0.0.0.0` and reads `PORT`. Use a TLS-terminating reverse proxy for an Internet deployment. The client requires HTTPS for public hosts; HTTP is accepted for localhost and private numeric LAN addresses.

## Free Internet hosting

**Git is version-control software. GitHub Pages hosts static files and cannot run this Java room server.** GitHub can store the source and distribute the release packages. [GitHub Pages documentation](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages).

Render offers a free web-service plan for hobby/testing use. It sleeps after 15 minutes without inbound traffic; a cold start can take about a minute. Its free services can restart and have no persistent local filesystem. This application keeps rooms in memory, so restarts discard them. These limits make it suitable for an initial small deployment, with availability limits. [Render free-service limits](https://render.com/docs/free).

The repository includes a **Dockerfile** and **render.yaml** deployment template. To deploy in your own Render account:

1. Connect this repository and select the `main` branch.
2. Create a Blueprint from `render.yaml`, or a Docker Web Service using the root Dockerfile. Confirm the **Free** plan.
3. Set the health-check path to `/health`; the template sets `PORT=10000`.
4. Deploy, wait for the health check to pass, and copy the assigned `https://…onrender.com` base address into every player's **Server address** field.
5. Automatic deployment is disabled in the template so a normal Git push does not interrupt active rooms. Deploy updates deliberately.

The initial connection allows up to 90 seconds for a cold service to wake; it remains cancellable. Normal requests have a 12-second limit. Successful host builds and loopback HTTP/desktop tests do not prove a cloud deployment: the Docker image and public endpoint must be verified in the hosting account. [Render Docker guide](https://render.com/docs/docker), [Blueprint fields](https://render.com/docs/blueprint-spec).

For a local Docker installation:

```powershell
docker build -t quoridor-server .
docker run --rm -p 8080:8080 -e PORT=8080 quoridor-server
```

The server has bounded request/response sizes, a bounded worker queue, and a default limit of 128 rooms including recently closed rooms. This is a single-instance service; rooms are not shared between replicas. For larger deployments, add shared persistence, traffic controls, and operational monitoring before scaling it.

## فارسی

بازی آنلاین **دو و چهارنفری**، اتاق خصوصی با کد هشت‌نویسه‌ای و **یافتن حریف تصادفی** اضافه شده است. همهٔ بازیکنان باید نشانی یک سرور را وارد کنند. سرور عمومی از قبل در بازی تنظیم نشده است؛ سرور خودتان را اجرا یا با قالب آماده میزبانی کنید و نشانی آن را به دوستان بدهید.

از منوی اصلی **بازی آنلاین** را انتخاب کنید. برای دوستان، **ساخت اتاق** و سپس **ورود به اتاق** با کد را به کار ببرید. برای پیدا کردن بازیکنان دیگر، تعداد نفرات را انتخاب و **یافتن حریف** را بزنید. اتاق‌های خصوصی وارد صف عمومی نمی‌شوند. با تکمیل نفرات، بازی شروع می‌شود. خروج یکی از بازیکنان، مسابقهٔ آنلاین را می‌بندد.

برای شبکهٔ محلی، `run-server.ps1 -Port 8080` را اجرا کنید. نشانی همان رایانه `http://localhost:8080` و نشانی رایانه‌های دیگر، IP خصوصی میزبان مانند `http://192.168.1.10:8080` است. میزبان باید اجازهٔ دسترسی به پورت را در فایروال بدهد؛ اسکریپت تنظیمات شبکه را تغییر نمی‌دهد.

گوشی اندرویدِ متصل به همان Wi-Fi نیز IP خصوصی رایانه را وارد می‌کند؛ `localhost` روی گوشی به خود گوشی اشاره دارد. شبیه‌ساز استاندارد Android با `http://10.0.2.2:8080` به رایانهٔ میزبان می‌رسد. بازی آنلاین بین گوشی و رایانه ممکن است. برنامه را هنگام مسابقه باز نگه دارید؛ تغییرات پیکربندی اتاق را حفظ می‌کنند، اما پایان فرایند برنامه یا غیبت دو دقیقه‌ای عضویت را نگه نمی‌دارد.

برای اینترنت می‌توان از پلن رایگان **Render** استفاده کرد. فایل‌های `Dockerfile` و `render.yaml` آماده‌اند؛ در حساب Render مخزن و شاخهٔ `main` را متصل کنید، پلن **Free** را بررسی کنید و پس از استقرار موفق، نشانی HTTPS سرور را در بازی وارد کنید. **GitHub Pages سرور Java را اجرا نمی‌کند.**

پلن رایگان محدودیت دارد: پس از ۱۵ دقیقه بی‌ترافیکی می‌خوابد و بیدارشدنش ممکن است حدود یک دقیقه طول بکشد. اتاق‌ها در حافظه‌اند و با راه‌اندازی دوبارهٔ سرور از بین می‌روند. اتصال کوتاه‌مدت دوباره برقرار می‌شود، اما غیبت دو دقیقه‌ای یک بازیکن مسابقه را می‌بندد. فایل ذخیره، فقط وضعیت بازی را برای ادامهٔ محلی نگه می‌دارد و اتاق آنلاین یا تاریخچهٔ برگشت حرکت را برنمی‌گرداند.

سرور و اتصال چند پنجره از طریق HTTP واقعی به‌صورت محلی آزمایش می‌شوند. استقرار Docker و نشانی عمومی نیازمند بررسی در حساب میزبان است. دستورات و مراحل دقیق بالا آمده‌اند.
