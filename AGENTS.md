# Repository guidance for coding agents

## Start here

This repository contains Quoridor for Java desktop, native Android and the browser,
plus the shared online-room server. Read [CONTRIBUTING.md](CONTRIBUTING.md) before
changing code. Check `git status --short --branch` and preserve unrelated work.

Before editing an area, also read its scoped guidance:

| Area | Guidance | Main entry points |
| --- | --- | --- |
| Java rules, saves, bots and Swing UI | [src/Board/AGENTS.md](src/Board/AGENTS.md) | `src/Main.java`, `src/Board/Board.java`, `src/Board/GameState.java` |
| Server and shared HTTP client | [src/Online/AGENTS.md](src/Online/AGENTS.md) | `OnlineServer.java`, `OnlineClient.java`, `ServerMain.java` in `src/Online` |
| Browser game and Pages build | [web/AGENTS.md](web/AGENTS.md) | `web/src/app.js`, `web/scripts/build.mjs` |
| Native Android | [android/AGENTS.md](android/AGENTS.md) | `android/app/src/main/java/com/quoridor/mobile`, `android/app/build.gradle` |

Nested guidance adds area-specific rules. Read it explicitly when your session
starts at the repository root. A change to shared Java code can affect desktop,
Android, the server and browser parity tests; inspect those consumers together.
Current human instructions take precedence over repository guidance.

## Product contracts

- Keep this a game-only project. VLC, its clips and dependencies, and the former
  UML/report section were deliberately removed, including from Git history. Do
  not restore them from old commits or add unrelated application sections.
- Preserve two- and four-player local games, the three bot levels, start/setup,
  save/load, local undo/redo and hints, visual practice and winner replay.
  Private rooms and random matchmaking remain in desktop/Android; the maintainer
  temporarily removed remote-play entry from the web UI. Do not restore it
  incidentally. Desktop and web use direct board input; Android keeps explicit tools. Phone/tablet match screens must fit the viewport without page scrolling.
- Desktop supports English/Persian; web and Android also support Spanish, French and Arabic. Do not add Hebrew.
  New text belongs in each platform's existing translation catalog. Preserve
  dark/light appearances and the physical board coordinates in RTL layouts.
- Keep the original team attribution in [AUTHORS.md](AUTHORS.md), both READMEs
  and distributed notices: سیدمحمدصالح قطبانی، محمدجواد بنان، امیرمحمد خسروی.
  Record later contributors separately. Preserve [LICENSE](LICENSE) (MIT) and
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Saved games and online state are cross-platform contracts. Treat format,
  rule, field and version changes as compatibility work; update all affected
  clients and add migration or rejection tests as appropriate.

## Implementation and output boundaries

Use cohesive responsibilities, meaningful names, explicit validation and bounded
work. Keep rules independent of presentation, return defensive state copies and
preserve cancellation/revision guards. Add dependencies only for a concrete need;
desktop/server use the Java standard library, and the web runtime has no external
dependencies. Follow `.editorconfig` and `.gitattributes` (UTF-8, LF).

PowerShell entry scripts locate the repository through their own paths. Reuse
`scripts/ProjectTools.ps1` and the existing Android/Windows helpers for compiler
discovery, version parsing and guarded output paths. Never hard-code a developer's
SDK, JDK, drive, credentials or personal device. Verify absolute targets stay in
the intended output subtree before deleting or moving generated files.

Builds, test evidence and downloaded tools belong under ignored `out`/`build`
paths; browser development dependencies belong in ignored `web/node_modules`.
Do not commit personal saves, IDE settings, local SDK configuration, signing
material, membership tokens or generated binaries. Do not clear all of `out`:
it can contain durable private signing material and useful test evidence.

## Verification by change

Run commands from the repository root in PowerShell. JDK 17+ is required for Java;
web tests additionally use Node.js 24+ and Android has its own SDK requirements.

| Changed area | Relevant verification |
| --- | --- |
| Documentation only | Check relative links and documented commands against the tree; run `git diff --check`. No application suite is required unless behavior or build inputs also change. |
| Java rules, saves, bots or server | `powershell -ExecutionPolicy Bypass -File .\test.ps1 -Headless`; include web parity and Android checks when their shared contracts change. |
| Swing behavior or appearance | `powershell -ExecutionPolicy Bypass -File .\test.ps1` on a graphical desktop, plus visual inspection in both languages/themes. |
| Browser behavior or assets | `powershell -ExecutionPolicy Bypass -File .\test-web.ps1`; see [WEB.md](WEB.md) for build, isolated browsers and server interoperability. |
| Android behavior | `powershell -ExecutionPolicy Bypass -File .\test-android.ps1 -UnitOnly`, then `test-android.ps1` for device/lifecycle changes; see [ANDROID.md](ANDROID.md). |
| Packaging or release metadata | Follow the relevant platform checks in [RELEASING.md](RELEASING.md), [ANDROID.md](ANDROID.md) and [WEB.md](WEB.md). |

For changed Java sources, run `format.ps1 -Check` (or `format.ps1` followed by
the check). The pinned formatter covers Java under `src`, `tests`, `tools`,
`android/app/src` and `web/tests/java`. Finish every change with `git diff --check`.
Use meaningful regression scenarios for behavioral changes, not tests that merely
repeat implementation calculations. Broaden testing when a failure or shared
contract warrants it; do not rerun expensive suites for prose-only edits.

Tests must use temporary saves, loopback servers and owned windows/processes.
Reuse the isolated emulator/browser helpers; do not close a user's browser,
server, emulator or phone. A build or unit-only run is not proof of UI/device
behavior. Report skipped or unavailable checks accurately.

## Documentation, releases and integration

- Update [README.md](README.md) and [README.fa.md](README.fa.md) together when
  features, controls or requirements change. UI documentation uses real captures:
  `capture-screenshots.ps1` is for desktop; use the relevant browser/Android
  capture flow for those platforms and review the resulting images.
- [VERSION](VERSION) is the source version. Follow [RELEASING.md](RELEASING.md)
  for coordinated release metadata. Documentation-only edits do not need a
  version bump or replacement of an existing release asset.
- Android release output must remain **unsigned** unless the maintainer explicitly
  changes that decision. Do not create a release signing key. A debug key used by
  the existing test workflow is separate from distribution signing.
- Public game-server hosting is currently a deployment template and guide in
  [ONLINE.md](ONLINE.md). GitHub Pages hosts static files; online rooms require
  the separate server. Do not claim a public server is deployed or provision one
  as an incidental code change.
- Use a focused `feature/`, `fix/`, `docs/` or `maintenance/` working branch from current `main`. Keep branch names, commit subjects/bodies, pull-request titles/descriptions and added trailers free of AI-tool or vendor attribution. Describe the actual project change. Validate, commit and
  push the complete change, open its PR against **main**, then merge it and sync
  local `main`, following the maintainer's standing push-and-merge workflow.
  Respect required checks and report any integration blocker; never bypass them
  or discard someone else's commits to force a merge.
- Summarize resulting behavior, verification and material limitations in the PR
  and final report. Confirm the remote merge and clean local status before
  reporting completion. Do not report a queued build/deployment as successful.
