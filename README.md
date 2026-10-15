# Quoridor

A desktop strategy game built with Java Swing. Race to the opposite edge of a 9 × 9 board, place walls to shape your opponent's route, and always leave both players a way through.

Play with a friend on the same computer or challenge a robot at three difficulty levels. The interface supports **English and Persian**, **dark and light appearance**, and saving and loading complete matches. Java's standard library is the only runtime dependency.

[راهنمای فارسی](README.fa.md) · [Contributing](CONTRIBUTING.md) · [Original development team](#original-development-team) · [MIT license](LICENSE)

## Screenshots

These are actual captures of the application. The three gameplay images show the same legal position in different display settings.

| English · dark menu | English · dark game |
| --- | --- |
| [![English start menu in dark mode, with local and robot game options](docs/screenshots/menu-en-dark.png)](docs/screenshots/menu-en-dark.png) | [![English game in dark mode, showing pawns, placed walls, robot difficulty and game controls](docs/screenshots/game-en-dark.png)](docs/screenshots/game-en-dark.png) |
| **English · light game** | **فارسی · بازی با ظاهر روشن** |
| [![The same game position in light mode](docs/screenshots/game-en-light.png)](docs/screenshots/game-en-light.png) | [![Persian game in light mode with a right-to-left interface](docs/screenshots/game-fa-light.png)](docs/screenshots/game-fa-light.png) |

## Features

- Local two-player matches, or a human playing Blue against a Green robot.
- **Easy**, **Medium**, and **Hard** robot opponents. Robot thinking runs in the background so the interface remains responsive.
- A start menu with player names, mode and difficulty selection, loading, and resuming a paused match.
- Versioned UTF-8 `.qsave` files that retain player names, game mode, robot difficulty, pawn positions, walls, remaining walls, and the current turn.
- Import of compatible text-based `.G3M` saves from the original application.
- English and Persian text, Persian numbers and a right-to-left layout, with fixed board coordinates in both languages.
- Dark and light colors for the menu, game, tooltips, messages, and save/load dialogs.

## Requirements

| Purpose | Requirement |
| --- | --- |
| Build and test | JDK 17 or newer; `java`, `javac`, and `jar` on `PATH` |
| Run an already built JAR | Java 17 or newer and a graphical desktop |
| Included scripts | Windows PowerShell |

No Maven, Gradle, external JARs, native plugins, or media player installation is needed.

## Quick start

The current game is on the `develop` branch:

```powershell
git clone --branch develop https://github.com/salehghotbani/Game-Quoridor.git
Set-Location Game-Quoridor
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

If you already have this checkout, run the final command from the project directory. `run.ps1` builds the application and starts it.

To build without launching:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The runnable application is written to `out/Quoridor.jar`:

```powershell
java -jar out/Quoridor.jar
```

The scripts also work when invoked by absolute path from another directory. The JAR includes the project license and author notice under `META-INF`.

## How to play

1. Choose **Two players** or **Play a robot** on the start menu. Enter player names; choose a difficulty for a robot match. Select **Start a new match**.
2. Blue starts at the bottom and moves first. Blue wins by reaching any square on the top row; Green aims for the bottom row.
3. Click a highlighted square to move. You may jump an adjacent opponent when the square behind is accessible. A wall or board edge behind that opponent permits a legal side step instead.
4. To place a wall, choose **Horizontal** or **Vertical**, then click a slot between squares. Hovering or focusing a slot previews the full wall. Each player starts with 10 walls. Walls cannot overlap, cross, or close every route to either goal.
5. Use **Main menu** to pause and **Resume your match** to continue. **New game** restarts with the current player names, mode, and difficulty.

Rules follow [Gigamic's official Quoridor instructions](https://export.gigamic.com/wp-content/uploads/2021/01/INS-RULES-QUORIDOR_10-2016.pdf), with Blue always starting in this application.

### Robot difficulty

| Level | Behavior |
| --- | --- |
| Easy | Varied moves for a relaxed match |
| Medium | Efficient routes and useful wall placements |
| Hard | Tactical search with a bounded thinking time |

### Language and appearance

Use **Language** in the menu or game header to choose **English** or **فارسی**. Use **Appearance** in the menu or beside the board to choose **Dark** or **Light**.

Changing either setting keeps the current match, names, selected wall direction, and pending robot turn intact. Persian uses a right-to-left interface; rows, columns, goals, and movement retain the same meaning. The only interface languages offered are English and Persian.

On launch, a Persian system locale selects Persian; other system locales select English. Appearance starts in Dark mode. Display settings are per window and are not stored in saved matches or remembered between launches.

## Save and load

Select **Save** during a match to write a `.qsave` file. Use **Load** in the game header or **Load a saved match** on the start menu to reopen one. A loaded robot turn continues automatically.

The loader validates saved positions and retains the current match if loading fails. Saves use an atomic replacement: an existing file is kept if a complete atomic write cannot be made. Invalid files and save errors produce a message in the chosen language.

Compatible old text `.G3M` files, including the original `SaveFile.G3M` fixture, can be imported. New saves use `.qsave`.

## Build verification and tests

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

This rebuilds the JAR, compiles the tests, and checks game rules, all robot levels, save/load validation, translation coverage, and the Swing interface. Graphical tests cover both languages and appearances, actual rendered contrast, live display changes, robot turns, and save/load dialogs. They briefly open windows and write screenshots under `out`.

For a machine without a graphical display:

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1 -Headless
```

Any build or test failure returns a nonzero exit code. Generated files and personal `.qsave` files are ignored by Git.

### Refresh the documentation screenshots

```powershell
powershell -ExecutionPolicy Bypass -File .\capture-screenshots.ps1
```

This builds the current application and captures its real Swing interface into `docs/screenshots`. It requires a graphical desktop and does not replace personal saved games.

## Project layout

```text
src/Main.java                    Application entry point
src/Board/                       Rules, robot, saves, localization, and interface
src/Files/Images/                Application icon
tests/                          Rule and Swing integration tests
tools/CaptureScreenshots.java    Documentation screenshot capture utility
docs/screenshots/                Tracked README screenshots
build.ps1 / run.ps1 / test.ps1    Build, launch, and verification scripts
capture-screenshots.ps1          Refresh the README screenshots
AUTHORS.md                       Original development team and credit instructions
CONTRIBUTING.md                  Development workflow and coding conventions
LICENSE                          MIT license
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the architecture map, development setup, coding conventions, formatting, tests, saved-game compatibility, and pull request workflow. Run `format.ps1` to apply the pinned Java formatter, or `format.ps1 -Check` to verify the style.

## Original development team

This software project was originally developed by **three people**. Their names belong in the following three slots; the order does not assign a role or ranking.

| Original developer | Full name |
| --- | --- |
| 1 | `DEVELOPER_1_FULL_NAME` |
| 2 | `DEVELOPER_2_FULL_NAME` |
| 3 | `DEVELOPER_3_FULL_NAME` |

**To complete the names:** replace these exact placeholders in [AUTHORS.md](AUTHORS.md), this table, and the team table in [README.fa.md](README.fa.md). GitHub links are optional and can be added in `AUTHORS.md`. No names have been guessed from commit accounts.

## License

The source code and documentation are released under the [MIT license](LICENSE). Keep the copyright and license notices when redistributing them. The copyright notice refers to **The Quoridor Authors**, whose names are recorded in [AUTHORS.md](AUTHORS.md).

The MIT license text follows the [Open Source Initiative's published MIT license](https://opensource.org/license/mit).
