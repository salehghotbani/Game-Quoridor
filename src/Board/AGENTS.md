# Board package guidance

These instructions apply to `src/Board`. Follow the repository guidance for general style and workflow; this file covers game-specific responsibilities and contracts.

## Keep shared logic separate from Swing

- `GameState` owns rules and snapshot validation. `BotPlayer`, `GoalDistances` and `GameHints` own search and route suggestions. `GameConfig`, `GameSession` and `MatchHistory` own configuration, state ownership and local history.
- `SaveStore` owns bounded file I/O; `CurrentSaveFormat`, `SaveFields` and `LegacySaveReader` own parsing and compatibility. Keep validation here rather than duplicating it in a dialog or controller.
- These shared classes must remain usable without Swing or a desktop JVM. Android copies an explicit source allowlist from [android/app/build.gradle](../../android/app/build.gradle); do not introduce desktop UI or server dependencies into that core.
- `Board` coordinates Swing actions and lifecycle. `BoardSurface` handles board geometry/painting, `BoardWidgets` reusable controls, `Theme` palettes, and `GameDialogs` dialog presentation. Keep game legality in `GameState`, including when validating a wall preview.
- The browser has a separate JavaScript implementation in [web/src](../../web/src). A shared rule, snapshot or save change must preserve its contract and Java-oracle parity; Android reuses the Java implementation directly.

## Rules and state invariants

- Derive dimensions from `GameState.SIZE`. Coordinates are zero-based physical positions: row 0 is top, column 0 is left. Wall anchors occupy rows/columns 0–7 and span two cell edges.
- Seats are Blue 0 (bottom → top), Green 1 (top → bottom), Red 2 (left → right), Gold 3 (right → left). Two-player turns alternate 0→1; four-player turns are clockwise 0→2→1→3. Stock starts at 10 per player for two players, 5 for four.
- Check the edge before an adjacent pawn. Jump over one pawn only when the landing is free and the edge behind it is open. A wall, board edge or occupied landing permits free perpendicular side steps around that pawn; do not chain jumps or land on another pawn.
- Reject overlapping/crossing walls. Touching end-to-end is legal. A candidate wall must leave **every** player a route to **any of all nine** goal-edge cells. Route validation ignores pawn occupancy.
- Invalid actions and `canPlaceWall` must leave the entire state unchanged. After a win, all actions freeze and the winner retains the turn.
- `fromSnapshot` must reject invalid bounds, coincident pawns, duplicate/overlapping walls, missing routes, inconsistent winner/goal/turn data, and impossible stock accounting. Placed walls plus remaining stock total 20. Preserve defensive copies and existing two-player constructors/accessors when extending APIs.

## Bots and history

- Robots support two-player matches only. Search independent copies; retain injected randomness, deterministic candidate ordering and interruption checks. Preserve the bounded search deadline (currently 650 ms) and keep search off the EDT.
- `GoalDistances` measures wall-aware routes to a whole goal edge while ignoring pawns; legal pawn moves still account for occupied squares and jumps. Do not interchange those graphs.
- `MatchHistory` owns independent snapshots, a maximum of 1,000 retained plies, and branch truncation after a new action following undo. Preserve complete human/robot undo/redo behavior in the controllers.
- Worker completion must check its owner, revision/generation and cancellation before applying an action. Loading, replacement, undo, pause and close must fence stale work; language/appearance changes must preserve the live match and history.

## Portable saves

- Two-player games write `.qsave` v1; four-player games write v2. The reader accepts v2 for either supported count. Keep names, mode, difficulty, pawns, stock, walls, turn and winner compatible with desktop, Android and browser.
- Preserve strict Properties keys, duplicate rejection, numeric grammar, UTF-8 validation and name validation through `GameConfig`. Current saves are bounded to 64 KiB; legacy input to 256 KiB. Never partially apply a failed load.
- Keep atomic replacement: a failed save must preserve the previous file. `SaveStore.encode/decode` also defines the network's validated session representation; saves contain neither membership credentials nor undo history.
- Preserve the original `.G3M` import and its coordinate/graph validation. `SaveStoreTest` generates its legacy fixture with `legacyText` in a temporary directory; it also checks the original tracked root `SaveFile.G3M` fixture when present. Keep that fixture unchanged, do not substitute a personal save, and do not require its presence for normal tests.

## Swing, localization and asynchronous ownership

- Create/mutate Swing components on the EDT; perform bot search and HTTP work outside it. Preserve component names used by integration tests.
- Only English and Persian are supported. Add UI templates to `TranslationCatalog` with matching formatting placeholders; use `I18n` and `UiText` for formatting and mixed-direction text. Preserve user names and filenames as literal data.
- Persian may rearrange controls, but must not mirror physical board coordinates, goals or keyboard directions. Keep wall preview separate from confirmation/commit.
- Theme and language are per window. Use `Theme.Palette`; do not modify global `UIManager` defaults or the JVM default locale. Inspect actual rendered disabled, selected, hovered and focused controls in both appearances.
- `OnlineMatchController` owns membership and asynchronous connection generations. Initial entry cancellation must allow a late credential to be received and its own seat released; authenticated polling/actions can be interrupted. An expired/closed online match must remain frozen rather than becoming a local game.

## Choose checks by the changed behavior

Commands below run from the repository root. Domain suites use plain Java `main` assertions and are selected by the existing test runners.

| Change | Relevant coverage |
| --- | --- |
| Moves, walls, snapshots, goals | `GameStateTest`, `FourPlayerTest`; include edge/corner jumps, crowded landings, route traps for all seats and the ninth goal cell |
| Search or hints | `BotPlayerTest`; deterministic tactical positions, useful walls, purity, legal complete games, cancellation and bounded decisions |
| Saves/configuration | `SaveStoreTest`, `MultiplayerSaveTest`; v1/v2/legacy compatibility, corrupt input, Unicode and failed-load preservation |
| History/async ownership | `MatchHistoryTest`, `Board.OnlineMatchControllerTest`, `FeaturesUiTest`; paired turns, branching and stale completions |
| Translations, themes, dialogs or painting | `I18nTest`, `BoardSmokeTest`, `LanguageUiTest`, `AppearanceUiTest`, `SaveDialogTest`, `FeaturesUiTest`; run graphical tests and inspect captures |

- `powershell -ExecutionPolicy Bypass -File .\test.ps1 -Headless` runs model/codec/translation/network checks. Use `test.ps1` without `-Headless` for Swing changes.
- For changes to Android-shared core, also use `test-android.ps1 -UnitOnly`; see [ANDROID.md](../../ANDROID.md) for device/lifecycle coverage.
- For cross-platform rule/save changes, use `test-web.ps1 -UnitOnly` with JDK 17+ available so the Java oracle runs rather than skips. Full `test-web.ps1` adds actual browser/server flows; see [WEB.md](../../WEB.md).
- Use [CONTRIBUTING.md](../../CONTRIBUTING.md) for repository-wide conventions. Keep new scenarios focused on observable behavior instead of reproducing the implementation as the expected result.
