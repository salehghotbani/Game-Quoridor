# Browser edition / نسخهٔ وب

**Play:** <https://salehghotbani.github.io/Game-Quoridor/>

The browser edition is a static HTML/CSS/JavaScript application hosted on GitHub Pages. No Java, Android installation, account, application framework or runtime CDN is required. Local games and robots run entirely in your browser. Internet multiplayer connects to a separate compatible Quoridor Java server; GitHub Pages does not run that server.

## Play and save

- Select a local two/four-player match or an Easy/Medium/Hard robot. Enter names or use the defaults.
- The board keeps its physical direction in English and Persian. Select dark/light appearance independently.
- Move to a highlighted square. For a wall, select horizontal/vertical, choose an anchor on the board and confirm the preview. Every player must retain a path to their goal.
- Undo/redo in robot games restores a whole human/robot turn. Hints suggest a legal pawn move along a shortest route; history records the applied local moves.
- Local recovery is stored in this browser's storage. **Continue saved match** resumes it after a reload; settings are also remembered. Private browsing, clearing site data or blocked storage can remove recovery. Use **Save file** for a portable copy.
- Export/import standard UTF-8 `.qsave` v1/v2 files compatible with desktop and Android. Imports are bounded to 64 KiB and fully validated before replacing the game. A save does not contain online credentials or the undo timeline. Original legacy `.G3M` dumps must first be opened and saved as `.qsave` by the desktop edition.
- Robot search runs in a dedicated worker. Replacing a match, undoing, returning to the menu or hiding the tab cancels stale work.

Arrow keys navigate board squares and wall anchors; Enter/Space selects them. Buttons have accessible labels, keyboard focus and live turn announcements. The layout adapts to mobile portrait and landscape. A current browser with ES modules, module workers, private class fields and Fetch is required. The site must be served over HTTP/HTTPS, not opened as `file://`.

## Build and test locally

Use Node.js 24+ and JDK 17+ for the Java/browser parity and server integration tests. From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-web.ps1
powershell -ExecutionPolicy Bypass -File .\test-web.ps1
```

Or from `web`:

```powershell
npm.cmd ci
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:browser
npm.cmd run serve
```

Open `http://127.0.0.1:4173/Game-Quoridor/`. The prefix matches the published project site so relative asset paths are tested before deployment. The production build writes only an explicit allowlist to **`out/web/site`**, with MIT license, authors, version, source commit metadata and an asset hash manifest. Test reports, development dependencies, Java source, private keys and other release output are excluded.

Node tests cover rules, bots, save validation, Java parity, transport and build boundaries. Playwright uses a fresh isolated headless browser to test local UI, workers, themes/languages, saves and responsive layouts. It does not access your personal browser profile. Browser binaries and Node dev dependencies are build/test tools, not application downloads.

## GitHub Actions and Pages

The workflow is [`.github/workflows/web.yml`](.github/workflows/web.yml). Pull requests run build and tests without deployment permissions. A successful `main` push affecting the web/game sources, build tools or version builds/tests the site, uploads the Pages artifact, then deploys it to the `github-pages` environment. Manual dispatch can redeploy `main`.

In repository **Settings → Pages**, the publishing source is **GitHub Actions**. Only the deployment job has `pages: write` and `id-token: write`; no personal access token is stored in the repository. The workflow uses pinned official actions and a locked test dependency. Failed tests prevent deployment. Existing native GitHub Releases remain separate from the Pages site.

For a fork, enable Actions and choose **GitHub Actions** as the Pages source; its address normally becomes `https://OWNER.github.io/REPOSITORY/`. Asset paths are relative. If the fork uses online play, update the server's allowed origin to that fork's origin (scheme and host, with no repository path).

## Online server and CORS

All clients must use the same compatible server; rooms can contain web, Android and desktop players. Enter an HTTPS server address, your name and the room size, then create/join a private room or find opponents. No public game server is deployed by this change.

Browsers require the server to explicitly permit the site's **origin**. Set this environment variable before starting the updated server:

```powershell
$env:QUORIDOR_ALLOWED_ORIGINS = 'https://salehghotbani.github.io'
powershell -ExecutionPolicy Bypass -File .\run-server.ps1 -Port 8080
```

The repository path `/Game-Quoridor/` is not part of an origin. The comma-separated allowlist defaults to empty. For local browser testing, add the actual development origin, for example `http://127.0.0.1:4173`. In Docker/Render set the same environment variable in the service configuration; see [ONLINE.md](ONLINE.md). Use a TLS reverse proxy for the publicly accessible server. An HTTPS Pages site should connect to an HTTPS game server; local HTTP tests use the local development site.

Allowed origins receive bounded preflight support for GET/POST and the Authorization/Content-Type headers. CORS never replaces bearer authentication or server-side turn/revision validation. Tokens stay in browser memory and are excluded from storage, URLs and save exports. The client rejects redirects and never automatically replays a move POST after an uncertain network outcome; it refreshes the authoritative state instead.

Keep the tab open during an online match. Hidden tabs pause polling, and a participant absent for two minutes closes the room. Closing/leaving a room releases membership when the server is reachable; a server restart discards all in-memory rooms. There are no persistent accounts, chat or ratings.

## Original developers

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

The footer, About dialog and distributed `AUTHORS.md` preserve these names. Application code is covered by the project's [MIT license](LICENSE).

## فارسی

نسخهٔ وب در **[GitHub Pages](https://salehghotbani.github.io/Game-Quoridor/)** اجرا می‌شود. بازی محلی دو و چهارنفری و ربات سه‌سطحی به سرور بازی نیاز ندارند. رابط فارسی و انگلیسی، ظاهر روشن و تاریک، پیش‌نمایش و تأیید دیوار، برگشت/تکرار، راهنما و تاریخچه در دسترس‌اند. فایل استاندارد `.qsave` با نسخه‌های اندروید و رایانه سازگار است؛ فایل قدیمی `.G3M` را ابتدا در نسخهٔ دسکتاپ به qsave تبدیل کنید.

بازی محلی در حافظهٔ همین مرورگر بازیابی می‌شود؛ برای نگه‌داری مطمئن، فایل بازی را صادر کنید. پاک‌کردن داده‌های سایت یا حالت خصوصی ممکن است ذخیرهٔ مرورگر را از بین ببرد. برای بازی آنلاین باید نشانی سرور HTTPS مشترک را وارد کنید و مبدأ `https://salehghotbani.github.io` در متغیر `QUORIDOR_ALLOWED_ORIGINS` سرور مجاز باشد. سرور عمومی جدیدی مستقر نشده است؛ GitHub Pages فقط خود نسخهٔ وب را میزبانی می‌کند.

فایل Action با هر PR آزمون‌ها را اجرا می‌کند و پس از push موفق به `main`، نسخهٔ وب را روی Pages منتشر می‌کند. برای اجرای محلی از `build-web.ps1` و `test-web.ps1` استفاده کنید؛ پیش‌نیازها Node.js 24 و JDK 17 هستند. خروجی سایت در `out/web/site` است و هیچ کلید خصوصی یا فایل اندروید/ویندوز در آن قرار نمی‌گیرد.
