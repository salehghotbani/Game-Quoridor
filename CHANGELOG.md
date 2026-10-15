# Changelog

This file records notable changes to the application. Installation and package details for the current version are in [RELEASE_NOTES.md](RELEASE_NOTES.md).

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
