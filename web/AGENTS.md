# Web edition guidance

These instructions apply to `web/`. See [WEB.md](../WEB.md) for operation and [ONLINE.md](../ONLINE.md) for the separate Java room server.

## Boundaries and build

- Keep the application dependency-free: native HTML/CSS and ES modules, with no runtime framework or CDN. `@playwright/test` is a pinned development dependency; use the lockfile.
- Keep `game.js`, `bot.js`, `save.js` and `local-session.js` independent of the DOM. Match the shared Java rules/save protocol; use the Java parity tests when changing them.
- `app.js` owns UI/lifecycle, `board-view.js` owns board rendering/input, `i18n.js` owns translations, and `online.js` owns transport/membership validation. Run robot search through `bot-worker.js`.
- `scripts/build.mjs` publishes an explicit production allowlist into `../out/web/site`. Add a new production module/asset to that allowlist and its boundary tests when necessary. Guidance, tests, `node_modules` and unrelated output must stay outside the site.
- Keep asset, module, worker and notice URLs relative. Test under `/Game-Quoridor/`, including nested module imports; opening `index.html` as `file://` is unsupported. Read the root `VERSION`; do not hardcode a second release version.

## UI and state contracts

- Support English/Persian and dark/light independently. Add visible text and accessible labels through `i18n.js`; render user names as text, never HTML. Changing preferences must preserve names, setup, history and the live position.
- RTL changes text/layout direction, never board coordinates, goal edges, wall geometry or physical arrow-key navigation. Keep the board square, all goal strips visible after scrolling in short landscape viewports, and wall touch targets usable.
- Selecting a wall only previews it; confirmation applies it. Invalid actions/imports must leave the current game intact. Keep keyboard focus, live announcements and virtual grid semantics when rerendering.
- `LocalSession` owns defensive snapshots and bounded history. Robot undo/redo restores a human/robot turn. Worker replies require matching owner, generation and revision; replacement, undo, menu navigation and a hidden document cancel stale work.
- Preserve strict UTF-8 `.qsave` v1/v2 compatibility and the 64 KiB limit in `save.js`. Validate before replacement. Browser recovery/preferences may be unavailable; gameplay and portable export must still work. Legacy `.G3M` conversion belongs to the desktop importer.
- Saves/recovery contain a position and configuration, not online credentials or the undo timeline. A restored/imported snapshot starts new history.

## Online lifecycle

- Pages hosts static files only. Do not invent or preconfigure a public game server. HTTPS endpoints are required except explicit loopback HTTP; keep endpoint validation, redirect rejection and bounded response parsing.
- Keep bearer credentials private in memory and send them only in authorization headers. Never put them in URLs, logs, browser storage or exports. CORS allowlists on the Java server do not replace authentication/revision checks.
- Serialize client operations; let server state and revision authorize moves. Never replay an uncertain action POST automatically. Preserve membership after transient reads, and refresh authoritative state before further actions.
- Closing during entry must let the reserved credential arrive and release that seat. Guard stale callbacks by owner/generation. Pause polling when hidden; leave on explicit exit/page teardown. A back/forward-cache restore cannot reuse the released client. Terminal room closure must freeze online moves and stop endless retry UI.

## Verification and owned output

- Use Node.js 24+; JDK 17+ is needed for parity and actual-server checks. From the repository root, `powershell -ExecutionPolicy Bypass -File .\test-web.ps1` runs the build, Node tests, CORS/server interoperability and Playwright. `-SkipInstall` reuses already installed locked dependencies.
- For a focused change, run its `node --test web/tests/<name>.test.js`; browser/UI/worker/lifecycle changes need the relevant Playwright cases. `-UnitOnly` deliberately skips browser/server coverage. Direct browser runs without `QUORIDOR_TEST_SERVER` skip online cases; do not report those as tested.
- For responsive work, inspect viewport-only captures and actual bounds, not just full-page screenshots. Cover EN/FA, both themes, physical RTL coordinates and short landscape with goal strips included.
- From the repository root, `powershell -ExecutionPolicy Bypass -File .\build-web.ps1` builds the site; `npm.cmd run serve` from `web/` serves the loopback preview. The full test helper owns its Java server and preview/browser processes. Use fresh isolated headless profiles, never a personal browser profile or an unrequested public room/server.
- Keep generated sites, reports, screenshots, Java fixtures and caches under `../out/web`. Validate containment and symlinks before recursive cleanup; preserve unrelated native releases. Close only processes started by the current test session.
