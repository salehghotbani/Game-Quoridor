# Quoridor

A local Java Swing game for two players or one player against a robot. The game uses only the Java standard library; VLC, native media plugins, and the old video are removed.

## Run

Install JDK 17 or newer and make `java`, `javac`, and `jar` available on `PATH`. On Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

The start menu offers a local two-player match, a robot match, and loading a saved game. Enter player names and choose **Easy**, **Medium**, or **Hard** when playing the robot. Blue is the human player and moves first; the robot controls Green.

To build without launching:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
java -jar out/Quoridor.jar
```

The scripts work from another directory when invoked by absolute path. An already built JAR needs only Java 17+ and a graphical desktop.

## Languages

Choose **English** or **فارسی** using the language selector on the start menu or the game screen. The interface, robot difficulty descriptions, tooltips, and save/load dialogs switch together. Persian uses a right-to-left layout and localized numbers; the board's rows, columns, movement rules, and saved data keep the same meaning.

Changing language keeps the current match, player names, wall direction, and robot turn intact. Supported system locales select Persian automatically; other system languages fall back to English. Only English and Persian are offered.

## Play

- Blue starts at the bottom and wins by reaching any cell on the top row. Green aims for the bottom row.
- Click a highlighted square to move. An adjacent opponent can be jumped when the square behind is accessible. A wall or board edge behind the opponent permits a legal side step instead.
- Choose **Horizontal** or **Vertical**, then select a wall slot. The preview shows the full wall. Each player has 10 walls. Walls cannot overlap, cross, or block every route to either player's goal.
- **Easy** plays varied moves. **Medium** follows efficient routes and considers useful walls. **Hard** searches tactical responses within a bounded thinking time. Thinking runs in the background, so the menu stays responsive.
- **New game** restarts the current match. Return to the menu to change players, mode, or difficulty. A paused match can be resumed.

Rules follow [Gigamic's official instructions](https://export.gigamic.com/wp-content/uploads/2021/01/INS-RULES-QUORIDOR_10-2016.pdf), with Blue always starting.

## Save and load

Use **Save game** during a match to write a `.qsave` file. Load it from the start menu or the game screen. The file retains player names, mode, robot difficulty, pawns, walls, remaining wall counts, and whose turn comes next. A loaded robot turn continues automatically.

The original application's text-based `.G3M` saves, including the existing `SaveFile.G3M`, can also be imported. New saves use a bounded, versioned UTF-8 text format. Loading validates the board and preserves the current match when a file is invalid. Saving replaces a completed file atomically; the original is kept if an atomic write is unavailable.

## Test

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

This builds the runnable JAR and runs game-rule, robot, save/load, translation, and Swing integration tests. The interface tests exercise both languages, right-to-left layout, live language switching, and file dialogs. They open and close windows and write visual evidence under `out`. For a machine without a display:

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1 -Headless
```

Tests compile into `out/test-classes`. Any build or test failure returns a nonzero exit code. Generated outputs and personal `.qsave` files are ignored by Git.
