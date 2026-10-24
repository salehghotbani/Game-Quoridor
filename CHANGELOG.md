# Changelog

## 2.4.0 — 2026-10-24

- Five hands-on visual lessons on web, desktop and Android: movement, wall placement, jumps, blocked-jump sidesteps and reaching the goal. Practice is separate from live matches, recovery saves and undo history.
- A prominent **Play again** action beside the winner, resetting the board/history while preserving names, mode, player count and robot difficulty.
- A quieter web menu with optional player-name fields, concise controls and an on-demand tutorial.
- Direct web-board input: hover/focus previews a horizontal or vertical wall; clicking its gap places it. Legal pawn squares remain clickable at the same time. Keyboard and touch input work without choosing a tool.
- Temporarily removed server-link rooms and remote matchmaking from the published web UI. Native Android/desktop online play and the tested server/transport remain available. Android keeps explicit move/wall tools and wall confirmation.
- Regression coverage for legal tutorial positions, live-match isolation, stale robot replies, replay and all supported languages/themes.

## 2.3.0

- Browser edition with local two/four-player games, three robot levels in a worker, English/Persian and dark/light appearance.
- Responsive accessible board with wall previews/confirmation, local paired undo/redo, hints, history, browser recovery and desktop/Android-compatible qsave import/export.
- Browser rooms and random matchmaking using the existing protocol; explicit-origin CORS support in the Java server without changing native-client authentication.
- Dependency-free static build, Node/Java parity tests, isolated browser tests and GitHub Actions for pull-request checks and automatic `main` deployment to GitHub Pages.
- Original author names, MIT license and actual browser screenshots included in the site/documentation. Native 2.2.0 release downloads remain available separately.

This file records notable changes to the application. Installation and package details for the current version are in [RELEASE_NOTES.md](RELEASE_NOTES.md).

## 2.2.0 — 2026-10-15

### Added

- Native Android app for API 26+, sharing Java rules, robot, save codecs, history and online protocol with desktop.
- Touch board with explicit wall preview/confirmation, legal move highlights, TalkBack virtual squares, keyboard navigation and fixed coordinates under RTL.
- Android local two/four-player matches, three robot levels, paired undo/redo, hints and move history.
- English/Persian and dark/light Android UI, device recovery saves, bounded document import/export and in-app author/license notices.
- Android/desktop private rooms and random matchmaking using the existing server, with foreground polling and configuration-change retention.
- Pinned Android Gradle build, unsigned APK distribution with optional signing helpers, host unit tests and isolated-emulator integration tests. Version 2.2.0 is published unsigned by the maintainer's choice; no release key is created.

### Changed

- Replaced the desktop-only HTTP client transport with shared bounded `HttpURLConnection`, retaining strict protocol, credentials, revision checks, deadlines, cancellation and no redirect/POST replay behavior.
- Shared the same pawn-hint helper between both interfaces; game rules, server protocol and save formats remain compatible.
- Updated source/release instructions to `main`, and retained the maintainer requirement to merge pushed working branches into `main`.
- Added Android backport and Gradle wrapper license notices separately from the application's MIT license.

## 2.1.1 — 2026-10-15

### Changed

- Confirmed the original developers, in order: سیدمحمدصالح قطبانی، محمدجواد بنان، امیرمحمد خسروی.
- Updated the English/Persian documentation and distributed author/license notices with the confirmed credits.
- Rebuilt versioned release packages for this credits-only patch. Gameplay, online protocol, save formats, and application behavior are unchanged from 2.1.0.

## 2.1.0 — 2026-10-15

### Added

- Local and online four-player matches with five walls per player, all four goal edges, clockwise turns, crowded-pawn jumps, and route checks for every player.
- Private online rooms, code-based joining, and separate two/four-player random matchmaking queues.
- An authoritative bounded HTTP room server, HTTPS client, revision/seat checks, transient reconnect and safe cancellation/room cleanup.
- Local undo/redo with paired human/robot turns, a legal pawn-move hint, and a localized bounded move history.
- Strict four-player save format version 2, retaining two-player version 1 and compatible legacy import.
- Standalone server packaging, Docker/Render deployment templates, and a bilingual online/LAN/hosting guide.
- End-to-end regression tests for multiple real Swing clients, matchmaking, server restarts, stale/malformed responses, cancellation and request timeouts.

### Changed

- Expanded the setup and player panels for four people and translated every new control into Persian.
- Included HTTP/TLS modules in the native runtime, the server JAR in release assets, and hosting instructions/screenshots in distributions.
- Kept expired/closed online games frozen and rejected server responses that roll revisions backward or change room authority.

Rooms are in memory; no public service is provisioned by the repository. Deployment and free-host limits are documented in [ONLINE.md](ONLINE.md).

## 2.0.0 — 2026-10-15

### Added

- Local two-player matches and a robot opponent with Easy, Medium, and Hard difficulty levels.
- A start menu with player names, mode and difficulty selection, loading, and resuming a paused match.
- Versioned UTF-8 `.qsave` files covering names, mode, difficulty, pawns, walls, remaining wall counts, turn, and winner.
- Safe import of compatible text-based `.G3M` saves from the original application.
- English and Persian interfaces, including Persian numbers, right-to-left layout, localized dialogs, and accessible labels.
- Independent Dark and Light appearance choices for the menu, game, tooltips, and dialogs.
- Windows x64 installer and portable ZIP packages with a bundled Java runtime, plus a standalone Java 17+ JAR and SHA-256 checksums.
- A single `VERSION` source for application metadata, JAR and Windows package versions, the window title, and the `--version` command.
- Automated game-rule, robot, persistence, translation, and graphical interface regression tests.
- English and Persian documentation with real game screenshots, a contribution guide, MIT license, and a place to credit the three original developers.
- A documented Windows packaging and release process with candidate validation and a draft-release review before publication.

### Changed

- Rebuilt the application around Quoridor gameplay using Java 17 or newer and the Java standard library.
- Kept language and appearance changes independent of the active match and pending robot turn. Appearance defaults to Dark on each launch.
- Separated game rules, robot search, Swing components, dialogs, save formats, and translation responsibilities into focused classes.
- Standardized source formatting and contributor workflows.

### Fixed

- Pawn movement, jumps, legal side steps, wall geometry, wall inventory, and route-preservation checks.
- Invalid save-file handling, including bounded input, strict state validation, and atomic replacement of completed saves.
- Stale robot results after pausing, replacing, or closing a match.
- Contrast, selected combo values, disabled controls, and minimum-window layouts in both languages and appearances.

### Removed

- The bundled VLC player, old video clip, unused multimedia dependencies, and unrelated application sections.

### Versioning rationale

`2.0.0` starts a new major line after the published `v1.0.0-beta.2` prerelease. The application workflow, supported runtime, and default save format have changed substantially. Compatible old `.G3M` saves can still be imported; new games are saved as `.qsave`.

For subsequent releases, use `MAJOR.MINOR.PATCH` consistently:

- **PATCH:** fixes that preserve supported save formats and established launch workflows.
- **MINOR:** additional features that preserve existing supported workflows and data compatibility.
- **MAJOR:** intentional changes that remove supported workflows or break save-format or runtime compatibility.

The save-file format has its own version field. Change that field when the data format changes; application version numbers and save-format versions serve different purposes.

## v1.0.0-beta.2 — 2024-09-02

Previous published prerelease of the original application. Its GitHub release uses the tag [`publish`](https://github.com/salehghotbani/Game-Quoridor/releases/tag/publish).
