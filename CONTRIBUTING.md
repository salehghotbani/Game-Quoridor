# Contributing to Quoridor

Thank you for improving this Java desktop game. Contributions target the **develop** branch. Keep each change focused, explain its effect, and preserve existing matches and saved games unless a format change is explicitly part of the work.

## راهنمای سریع فارسی

از شاخهٔ `develop` یک شاخهٔ کاری بسازید. ابتدا پروژه و تست‌ها را اجرا کنید، سپس فقط تغییر مرتبط با هدف خود را انجام دهید. پیش از ارسال، کدها را با `format.ps1` مرتب کنید، همهٔ تست‌ها را اجرا کنید و در صورت تغییر رابط، فارسی، انگلیسی و هر دو ظاهر روشن و تاریک را بررسی کنید. Pull request را به `develop` بفرستید و بنویسید چه چیزی تغییر کرده و چگونه آزموده شده است.

نام سه توسعه‌دهندهٔ اولیه در [AUTHORS.md](AUTHORS.md) و جدول‌های README ثبت می‌شود. مشارکت‌کنندگان بعدی می‌توانند نام و توضیح مشارکت خود را در بخشی جداگانه در همان فایل اضافه کنند؛ نام‌های تیم اولیه را جایگزین نکنید.

## 1. Set up a working copy

Use Windows PowerShell and JDK 17 or newer. The `java`, `javac`, and `jar` commands must be available on `PATH`.

```powershell
git clone --branch develop https://github.com/salehghotbani/Game-Quoridor.git
Set-Location Game-Quoridor
git switch -c codex/describe-your-change
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

The full test suite opens Swing windows. On a machine without a graphical desktop, use `test.ps1 -Headless` for the model, robot, saves, and translation checks. UI changes still need the full suite on a graphical desktop before review.

Run the game with `run.ps1`; build without launching with `build.ps1`. Scripts locate the project relative to their own files, so invocation by absolute path also works.

## 2. Find the right responsibility

| Area | Files | Responsibility |
| --- | --- | --- |
| Game rules | `src/Board/GameState.java` | Legal moves, wall validation, turns, winners, snapshots |
| Robot | `BotPlayer.java`, `GoalDistances.java` | Bounded search, candidate evaluation, pure route distances |
| Match configuration | `GameConfig.java`, `GameSession.java` | Player names, mode, difficulty, ownership of match state |
| Save files | `SaveStore.java`, `CurrentSaveFormat.java`, `LegacySaveReader.java`, `SaveFields.java` | File I/O, current format, legacy import, field parsing |
| Translations | `I18n.java`, `TranslationCatalog.java`, `TranslationFormat.java`, `UiText.java` | Supported languages, catalog, placeholder checks, UI text direction |
| Application controller | `Board.java` | Setup, navigation, match actions, robot lifecycle, display selections |
| Board and widgets | `BoardSurface.java`, `BoardWidgets.java`, `Theme.java` | Geometry, preview and painting, reusable controls, palettes |
| Dialogs | `GameDialogs.java` | Per-window chooser and message presentation |
| Test support | `tests/support/SwingTestSupport.java` | EDT execution, component lookup, image capture mechanics |
| Build tools | Root `.ps1` scripts and `scripts/ProjectTools.ps1` | Command orchestration, output boundaries, compilation |

All game classes currently use the `Board` package. The entry point is `src/Main.java`. Keep the rules and save formats independent of Swing. A new helper should own a cohesive responsibility rather than forward a single call without adding clarity.

## 3. Coding conventions

- Use meaningful names, short focused methods, explicit imports, and one statement per line.
- Prefer early validation and small named steps over nested branches or long compound conditions.
- Name domain constants when their meaning is not obvious. Derive board dimensions from `GameState.SIZE` instead of repeating `9`, `8`, or `81`.
- Keep mutable state private. Return defensive copies where callers could otherwise change a live match.
- Preserve deterministic iteration order and injected randomness in the robot. Check cancellation and keep its search deadline bounded.
- Explain invariants and non-obvious decisions in comments. Do not repeat the implementation in prose or leave commented-out alternatives.
- Avoid shared mutable presentation state, wildcard imports, unused code, speculative frameworks, and new runtime dependencies without a concrete need.
- Use UTF-8 and LF line endings. `.editorconfig` and `.gitattributes` record the repository conventions.

### Apply the Java formatter

```powershell
powershell -ExecutionPolicy Bypass -File .\format.ps1
powershell -ExecutionPolicy Bypass -File .\format.ps1 -Check
```

The formatter is [google-java-format 1.24.0](https://github.com/google/google-java-format/releases/tag/v1.24.0), using AOSP four-space indentation. Long string literals are preserved so translation keys remain readable and stable. The first run downloads the official release into ignored `out/tools`; each run verifies its pinned SHA-256 checksum. Formatting is a development tool, not a game dependency.

For offline use, copy the exact `google-java-format-1.24.0-all-deps.jar` into `out/tools` before running the command. The checksum in `format.ps1` must match. Normal build, run, and test commands do not download the formatter.

## 4. Preserve the existing contracts

### Rules and robot

Keep Blue as player 0 and Green as player 1. Preserve legal jump and side-step rules, wall overlap/crossing checks, route availability for both goals, and frozen play after a win. Snapshot restoration must reject inconsistent turns, winners, wall counts, positions, and blocked routes.

Robot search must operate on copies of the input position. A cancelled or stale worker must never act on a replacement match. Add a deterministic scenario when changing search behavior; avoid tests that depend on random luck or exact wall-clock speed.

### Swing, language, and appearance

Create and mutate Swing components on the event dispatch thread. Keep robot thinking off that thread. Preserve the revision/cancellation checks when starting, loading, restarting, pausing, or closing a match.

The supported interface languages are **English and Persian**. Add every new user-facing template to `TranslationCatalog`; keep the same formatting placeholders in both languages. Use the existing text helpers for mixed-direction text and leave player names and filenames unchanged.

Persian layout may move controls, but must not mirror the board's row/column coordinates or goals. Theme and language selection are per window. Do not change global `UIManager` defaults or the JVM locale to style a window. Verify selected, disabled, hovered, and keyboard-focused controls in both appearances.

Named Swing components are used by integration tests. Preserve those names or update the affected tests intentionally as part of the same change.

### Saved matches

Keep `.qsave` version 1 compatible unless the change includes a documented version/migration decision. Retain bounded UTF-8 input, strict validation, atomic replacement, and preservation of the current match when loading fails. Legacy `.G3M` import must keep working with the tracked `SaveFile.G3M` fixture.

Use temporary files for tests. Do not overwrite the legacy fixture, a personal save, or the tracked screenshots as part of a normal test.

## 5. Verify the change

```powershell
powershell -ExecutionPolicy Bypass -File .\format.ps1 -Check
powershell -ExecutionPolicy Bypass -File .\test.ps1
git diff --check
```

Choose meaningful tests for changed behavior. Keep assertions independent of implementation details where practical; do not duplicate production calculations as the expected result. Shared test support should contain mechanics, while each suite retains its scenarios and assertions.

For UI work, also inspect the captured images under `out`. Check the minimum window size, English/Persian, dark/light, local/robot matches, saving/loading, and switching display settings while a robot turn is pending. Contrast checks should examine rendered controls, not only their color properties.

For a refactor, compare the before/after public API, saved-file semantics, and relevant scenario results. A passing compiler alone does not establish behavior preservation.

## 6. Update documentation and credits

- Update [README.md](README.md) and [README.fa.md](README.fa.md) when controls, requirements, features, or commands change.
- Refresh real application screenshots for visual changes with `capture-screenshots.ps1`, then review the files in `docs/screenshots`. This command deliberately updates tracked documentation assets.
- Keep original-author names consistent in [AUTHORS.md](AUTHORS.md) and both README tables. Record later contributors separately with an accurate description of their work.
- Preserve the [MIT license](LICENSE) and copyright notices. The build bundles `LICENSE` and `AUTHORS.md` in the JAR, so rebuild after changing credits.

## 7. Submit a focused pull request

Commit a complete, reviewable change and push your working branch. Open the pull request against **develop**. Explain the concrete problem, resulting behavior, and verification performed. For a visual change, include before/after screenshots; for a save-format change, describe compatibility and fixtures.

Do not include `out`, formatter JARs, personal saves, IDE configuration, or unrelated files. If a check cannot run, say which one and why rather than claiming it passed. Keep unrelated cleanup in a separate change.

When reporting a bug, include reproduction steps, expected and actual behavior, Java/OS versions, game mode and robot level, language/appearance, and a minimal compatible save when relevant.
