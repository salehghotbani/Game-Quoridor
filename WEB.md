# Browser edition / نسخهٔ وب

**[Play Quoridor](https://salehghotbani.github.io/Game-Quoridor/)**

A static, dependency-free HTML/CSS/JavaScript game on GitHub Pages. Play locally
with two or four people, or against Easy/Medium/Hard robots. English/Persian and
dark/light appearance are independent. No account, installation or game server
is needed.

## Play and learn

- Choose a mode and start. Player names are optional, under **Player names**.
- **Learn by playing** opens five short, hands-on lessons: move, wall, jump,
  blocked-jump sidestep and goal. Follow the glowing target. Back revisits a
  lesson; Close/Escape returns to the same match. Practice never changes your
  real position, save or undo history.
- Click a highlighted square to move. Hover/focus a horizontal or vertical gap
  to preview a wall; click it to place the wall immediately. Both orientations
  and pawn moves are available together, with no tool or confirmation button.
  Touch uses the same distinct gutter targets. Every player must retain a path.
- Arrow keys navigate physical squares or wall targets; Enter/Space activates
  them. RTL never mirrors the board or goals.
- **Play again** appears beside a winner. It resets the board and history while
  keeping player names, count, mode and robot level.
- Undo/redo restores a human/robot turn; Hint highlights a legal route move.
  Move history stays collapsed until requested.
- **Continue** resumes local recovery after reload. Browser storage may be
  unavailable or cleared; use **Save file** for a portable copy.
- Import/export UTF-8 `.qsave` v1/v2 compatible with desktop/Android. The 64 KiB
  limit and complete validation apply before replacement. Failed imports preserve
  the existing match. Legacy `.G3M` dumps first need desktop conversion.
- Saves contain position/configuration, not undo history. Robot workers are
  cancelled/fenced on replacement, undo, hidden pages and visual practice.

## Remote play is temporarily removed

As requested by the maintainer, version 2.4.0 removes the remote-competition card,
server URL field, room-code entry and matchmaking from the published web app.
There is no alternate query-string or hidden switch for it. Local multiplayer
and robots remain available. Desktop/Android online rooms are unchanged.

The standalone `web/src/online.js` transport and Java CORS support remain tested
development source for compatibility; that module is excluded from the static
production allowlist. Hosting information for native online play remains in
[ONLINE.md](ONLINE.md). GitHub Pages does not execute the room server.

## Build and test

Use Node.js 24+ and JDK 17+. From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-web.ps1
powershell -ExecutionPolicy Bypass -File .\test-web.ps1
```

Preview the built site with `npm.cmd run serve` from `web/`, then open
`http://127.0.0.1:4173/Game-Quoridor/`. The prefix matches Pages; asset, module,
worker and notice URLs remain relative. Opening the HTML as `file://` is unsupported.

The build publishes only its allowlist to **`out/web/site`**, including MIT,
authors, version, source commit and a hash manifest. Tests, guidance, development
dependencies, signing material and native releases are excluded.

The full test helper starts an owned loopback Java server, tests CORS and the
retained transport, runs Node/Java rule/save parity and uses fresh headless
browsers for local play, workers, visual lessons, replay, storage and responsive
layouts. Native remote play is verified separately. `-UnitOnly` skips real
browser/server coverage; direct Node/browser commands may skip server cases
without the harness. No personal browser profile is used.

## GitHub Actions and Pages

[The workflow](.github/workflows/web.yml) builds/tests PRs without deployment
permissions. Successful relevant `main` pushes upload the exact tested artifact
and deploy it to the `github-pages` environment. Manual dispatch can redeploy.

Choose **GitHub Actions** in repository **Settings → Pages**. Only the deploy job
has `pages: write` and `id-token: write`; no personal access token is stored in
the repository. Official actions are pinned and failed tests prevent deployment.
For a fork, enable Actions and choose the same publishing source; relative assets
support the fork's repository prefix. Native GitHub Releases are separate.

## Original developers

1. سیدمحمدصالح قطبانی
2. محمدجواد بنان
3. امیرمحمد خسروی

The About dialog and distributed `AUTHORS.md` preserve these names. Application
code is covered by the [MIT license](LICENSE).

## فارسی

**[بازی در مرورگر](https://salehghotbani.github.io/Game-Quoridor/)** شامل بازی محلی
دو/چهارنفره و ربات سه‌سطحی است. نام بازیکنان اختیاری و منو کوتاه‌تر شده است.
«با بازی یاد بگیر» پنج تمرین تصویری مستقل دارد و بازی و تاریخچهٔ واقعی را تغییر
نمی‌دهد. برای حرکت، روی خانهٔ مشخص‌شده کلیک کنید؛ برای دیوار، روی شیار افقی یا
عمودی بروید تا پیش‌نمایش دیده شود و با یک کلیک آن را بگذارید. انتخاب ابزار و
تأیید جداگانه در وب حذف شده‌اند؛ اندروید روش قبلی خود را دارد.

پس از برد، «دوباره بازی کن» صفحه و تاریخچه را با همان نام‌ها و تنظیمات از نو
شروع می‌کند. فارسی/انگلیسی، روشن/تاریک و ذخیرهٔ سازگار با نسخه‌های دیگر حفظ شده‌اند.
اتصال با نشانی سرور و بخش «رقابت از راه دور» فعلاً از وب حذف شده‌اند. بازی آنلاین
در اندروید و دسکتاپ باقی است.

ساخت با `build-web.ps1` و آزمون کامل با `test-web.ps1` انجام می‌شود؛ پیش‌نیازها
Node.js 24 و JDK 17 هستند. Action پس از آزمون موفقِ تغییرات مرتبط روی `main`،
بستهٔ بررسی‌شده را روی Pages منتشر می‌کند.
