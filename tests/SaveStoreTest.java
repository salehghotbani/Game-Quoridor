import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.SaveStore;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Persistence tests use isolated temporary files, and never modify a player's save. */
public final class SaveStoreTest {
    private static int checks;

    private SaveStoreTest() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("quoridor-save-test-");
        try {
            configValidation();
            roundTrips(directory);
            rejectCorruptSaves(directory);
            failedWritesPreserveFiles(directory);
            legacyImport(directory);
            System.out.println("SaveStore tests passed (" + checks + " checks).");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void configValidation() {
        GameConfig config =
                new GameConfig(
                        "  صالح  ",
                        "  Green  ",
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.MEDIUM);
        equal(config.blueName(), "صالح", "Player names are stripped");
        equal(config.greenName(), "Green", "Opponent name is stripped");
        new GameConfig("😀".repeat(24), "Green", GameConfig.Mode.BOT, BotPlayer.Difficulty.EASY);
        for (String invalid :
                List.of("", "  ", "x".repeat(25), "😀".repeat(25), "A\nB", "A\tB", "\uD800")) {
            rejectsArgument(
                    () ->
                            new GameConfig(
                                    invalid,
                                    "Green",
                                    GameConfig.Mode.LOCAL,
                                    BotPlayer.Difficulty.EASY),
                    "Invalid name rejected");
        }
        rejectsArgument(
                () ->
                        new GameConfig(
                                null, "Green", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY),
                "Null name rejected");
        rejectsArgument(
                () -> new GameConfig("Blue", "Green", null, BotPlayer.Difficulty.EASY),
                "Null mode rejected");
        rejectsArgument(
                () -> new GameConfig("Blue", "Green", GameConfig.Mode.LOCAL, null),
                "Null difficulty rejected");
        rejectsArgument(() -> new GameSession(null, config), "Null game rejected");
        rejectsArgument(() -> new GameSession(new GameState(), null), "Null config rejected");
    }

    private static void roundTrips(Path directory) throws Exception {
        Path path = directory.resolve("بازی ذخیره شده.qrd");
        for (GameConfig.Mode mode : GameConfig.Mode.values()) {
            for (BotPlayer.Difficulty level : BotPlayer.Difficulty.values()) {
                GameSession session =
                        new GameSession(
                                new GameState(),
                                new GameConfig("صالح 😀 = # \\", "ربات : سبز", mode, level));
                SaveStore.save(path, session);
                assertSession(SaveStore.load(path), session, "Opening " + mode + "/" + level);
                check(
                        Files.readString(path, StandardCharsets.UTF_8).contains("صالح"),
                        "Save file preserves readable UTF-8 names");
                GameState botTurn = midGame();
                check(botTurn.moveTo(6, 4), "Fixture passes turn to green");
                GameSession midgame = new GameSession(botTurn, session.config());
                SaveStore.save(path, midgame);
                assertSession(SaveStore.load(path), midgame, "Green turn " + mode + "/" + level);
            }
        }
        GameState game = midGame();
        GameSession mid = new GameSession(game, config());
        SaveStore.save(path, mid);
        assertSession(SaveStore.load(path), mid, "Midgame with both wall orientations");
        String saved = Files.readString(path);
        game.moveTo(6, 4);
        equal(Files.readString(path), saved, "Live moves cannot change the saved file");
        assertSession(
                SaveStore.load(path),
                new GameSession(
                        GameState.fromSnapshot(
                                new GameState.Snapshot(
                                        new GameState.Cell(7, 4),
                                        new GameState.Cell(1, 4),
                                        9,
                                        9,
                                        0,
                                        -1,
                                        mid.game().snapshot().walls())),
                        config()),
                "Saved state is independent");
        for (int winner = 0; winner <= 1; winner++) {
            GameState finished =
                    GameState.fromSnapshot(
                            new GameState.Snapshot(
                                    new GameState.Cell(winner == 0 ? 0 : 7, 4),
                                    new GameState.Cell(winner == 1 ? 8 : 1, 3),
                                    10,
                                    10,
                                    winner,
                                    winner,
                                    List.of()));
            GameSession session = new GameSession(finished, config());
            SaveStore.save(path, session);
            assertSession(SaveStore.load(path), session, "Finished game " + winner);
            check(
                    SaveStore.load(path).game().legalMoves().isEmpty(),
                    "Loaded winner stays finished");
        }
        GameState allWalls = new GameState();
        int placed = 0;
        outer:
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col += 2) {
                if (allWalls.placeWall(row, col, GameState.Orientation.HORIZONTAL)
                        && ++placed == 20) {
                    break outer;
                }
            }
        }
        equal(placed, 20, "Fixture exhausts both wall stocks");
        GameSession emptyStocks = new GameSession(allWalls, config());
        SaveStore.save(path, emptyStocks);
        assertSession(SaveStore.load(path), emptyStocks, "Twenty walls and exhausted inventories");
    }

    private static void rejectCorruptSaves(Path directory) throws Exception {
        Path path = directory.resolve("corrupt.qrd");
        SaveStore.save(path, new GameSession(midGame(), config()));
        String valid = Files.readString(path);
        Properties original = properties(valid);
        for (String[] corruption :
                new String[][] {
                    {"version", "999"},
                    {"version", ""},
                    {"mode", "ONLINE"},
                    {"difficulty", "EXPERT"},
                    {"blue.name", " "},
                    {"green.name", "x".repeat(25)},
                    {"blue.row", "9"},
                    {"blue.col", "-1"},
                    {"green.row", "7"},
                    {"blue.walls", "-1"},
                    {"blue.walls", "11"},
                    {"blue.walls", "10"},
                    {"green.walls", "2147483648"},
                    {"turn", "2"},
                    {"winner", "1"},
                    {"turn", " 0"},
                    {"wall.count", "21"},
                    {"wall.count", "-1"},
                    {"wall.count", "1"},
                    {"wall.0", "0,0,DIAGONAL"},
                    {"wall.0", "8,0,HORIZONTAL"},
                    {"wall.0", "0,0,HORIZONTAL,EXTRA"},
                    {"wall.0", "0,0"},
                    {"wall.0", "0,0.1,HORIZONTAL"},
                    {"wall.1", original.getProperty("wall.0")},
                    {"surprise", "unexpected"}
                }) {
            Properties changed = properties(valid);
            changed.setProperty(corruption[0], corruption[1]);
            write(path, changed);
            rejectsIo(() -> SaveStore.load(path), "Corrupt " + corruption[0] + " rejected");
        }
        for (String key : original.stringPropertyNames()) {
            Properties missing = properties(valid);
            missing.remove(key);
            write(path, missing);
            rejectsIo(() -> SaveStore.load(path), "Missing " + key + " rejected");
        }
        for (String invalid :
                List.of(
                        "",
                        "version=1\n",
                        valid.substring(0, valid.length() / 2),
                        valid + "turn=0\n",
                        valid + "\\u0074urn=0\n",
                        valid + "broken=\\uZZZZ\n")) {
            Files.writeString(path, invalid);
            rejectsIo(
                    () -> SaveStore.load(path), "Truncated, duplicate, or malformed file rejected");
        }
        Files.write(path, new byte[] {(byte) 0xC3, (byte) 0x28});
        rejectsIo(() -> SaveStore.load(path), "Malformed UTF-8 rejected");
        Files.writeString(path, valid + "#" + "x".repeat(64 * 1024));
        rejectsIo(() -> SaveStore.load(path), "Oversized current format rejected");
        Files.writeString(path, "x".repeat(256 * 1024 + 1));
        rejectsIo(() -> SaveStore.load(path), "Oversized input bounded before parsing");
        rejectsIo(() -> SaveStore.load(directory.resolve("missing.qrd")), "Missing path rejected");
        rejectsIo(() -> SaveStore.load(directory), "Directory cannot be loaded");
        Properties blocked = properties(valid);
        blocked.setProperty("wall.count", "4");
        blocked.setProperty("blue.walls", "6");
        blocked.setProperty("green.walls", "10");
        blocked.setProperty("green.row", "0");
        blocked.setProperty("green.col", "4");
        blocked.setProperty("wall.0", "0,3,HORIZONTAL");
        blocked.setProperty("wall.1", "0,5,HORIZONTAL");
        blocked.setProperty("wall.2", "0,2,VERTICAL");
        blocked.setProperty("wall.3", "0,6,VERTICAL");
        write(path, blocked);
        rejectsIo(() -> SaveStore.load(path), "Walls blocking every route are rejected");
    }

    private static void failedWritesPreserveFiles(Path directory) throws Exception {
        GameSession session = new GameSession(midGame(), config());
        Path original = directory.resolve("original.qrd");
        SaveStore.save(original, session);
        byte[] before = Files.readAllBytes(original);
        rejectsArgument(
                () -> SaveStore.save(original, null), "Null session rejected before writing");
        check(
                Arrays.equals(before, Files.readAllBytes(original)),
                "Rejected save preserves previous bytes");
        Path destination = Files.createDirectory(directory.resolve("existing-directory.qrd"));
        Path marker = destination.resolve("keep.txt");
        Files.writeString(marker, "Keep this file");
        long countBefore;
        try (var files = Files.list(directory)) {
            countBefore = files.count();
        }
        rejectsIo(() -> SaveStore.save(destination, session), "Atomic move failure is reported");
        equal(
                Files.readString(marker),
                "Keep this file",
                "Failed move preserves destination contents");
        try (var files = Files.list(directory)) {
            equal(files.count(), countBefore, "Failed move cleans its temporary file");
        }
        rejectsIo(
                () -> SaveStore.save(directory.resolve("missing-parent/save.qrd"), session),
                "Missing save directory is reported");
        check(
                Arrays.equals(before, Files.readAllBytes(original)),
                "Other path failures preserve prior save");
        GameSession replacement = new GameSession(new GameState(), config());
        SaveStore.save(original, replacement);
        assertSession(
                SaveStore.load(original), replacement, "Existing save can be atomically replaced");
    }

    private static void legacyImport(Path directory) throws Exception {
        Path path = directory.resolve("SaveFile.G3M");
        GameSession expected = new GameSession(midGame(), config());
        String legacy = legacyText(expected);
        Files.writeString(path, legacy);
        byte[] original = Files.readAllBytes(path);
        assertSession(SaveStore.load(path), expected, "Legacy text import");
        check(
                Arrays.equals(original, Files.readAllBytes(path)),
                "Legacy import preserves original bytes");
        GameSession imported = SaveStore.load(path);
        Path modern = directory.resolve("imported.qrd");
        SaveStore.save(modern, imported);
        assertSession(SaveStore.load(modern), expected, "Legacy import resaves in current format");
        List<String> lines = new ArrayList<>(legacy.lines().toList());
        lines.set(3, lines.get(3).replace(" . 9 . 9", " . 10 . 9"));
        Files.writeString(path, String.join("\n", lines));
        rejectsIo(() -> SaveStore.load(path), "Legacy stock/wall mismatch rejected");
        lines = new ArrayList<>(legacy.lines().toList());
        lines.set(214, "null");
        Files.writeString(path, String.join("\n", lines));
        rejectsIo(() -> SaveStore.load(path), "Legacy graph/wall mismatch rejected");
        Files.writeString(path, legacy.substring(0, legacy.length() / 2));
        rejectsIo(() -> SaveStore.load(path), "Truncated legacy save rejected");
        Path existing = Path.of(System.getProperty("quoridor.test.project", "."), "SaveFile.G3M");
        if (Files.isRegularFile(existing)) {
            byte[] existingBytes = Files.readAllBytes(existing);
            GameSession actual = SaveStore.load(existing);
            equal(
                    actual.game().snapshot().walls().size(),
                    3,
                    "Original project save recovers its walls");
            equal(actual.game().wallsRemaining(0), 8, "Original project save keeps blue stock");
            equal(actual.game().wallsRemaining(1), 9, "Original project save keeps green stock");
            equal(
                    actual.config().mode(),
                    GameConfig.Mode.LOCAL,
                    "Original project save restores local mode");
            check(
                    Arrays.equals(existingBytes, Files.readAllBytes(existing)),
                    "Original project save remains unchanged");
        }
    }

    private static String legacyText(GameSession session) {
        GameState game = session.game();
        GameState.Cell active = game.playerPosition(game.currentPlayer());
        List<String> lines = new ArrayList<>();
        lines.add("Player1: " + session.config().blueName());
        lines.add("Player2: " + session.config().greenName());
        lines.add(Boolean.toString(game.currentPlayer() == 0));
        lines.add(
                "0 . 0 . 255 . true . false . 0 . 255 . 0 . false . false . "
                        + (128 + active.row() * 9 + 8 - active.col())
                        + " . "
                        + game.wallsRemaining(0)
                        + " . "
                        + game.wallsRemaining(1));
        for (int i = 0; i < 210; i++) {
            String color = "238 238 238 true true";
            if (i < 128) {
                int anchor = i % 64;
                if (game.hasWall(
                        anchor / 8,
                        7 - anchor % 8,
                        i < 64
                                ? GameState.Orientation.VERTICAL
                                : GameState.Orientation.HORIZONTAL)) {
                    color = "255 200 0 true false";
                }
            } else if (i <= 208) {
                GameState.Cell cell = new GameState.Cell((i - 128) / 9, 8 - (i - 128) % 9);
                if (cell.equals(game.playerPosition(0))) {
                    color = "0 0 255 true false";
                } else if (cell.equals(game.playerPosition(1))) {
                    color = "0 255 0 true false";
                }
            }
            lines.add(color);
        }
        for (int i = 0; i < 80; i++) {
            int index = Math.min(i, 78);
            int row = index / 9;
            int boundary = 7 - index % 9;
            boolean blocked =
                    (row < 8 && game.hasWall(row, boundary, GameState.Orientation.VERTICAL))
                            || (row > 0
                                    && game.hasWall(
                                            row - 1, boundary, GameState.Orientation.VERTICAL));
            lines.add(i < 79 && (index + 1) % 9 == 0 ? "Enter" : legacyEdge(blocked));
        }
        lines.add("hg");
        for (int i = 0; i < 72; i++) {
            int index = Math.min(i, 70);
            int row = index / 9;
            int col = 8 - index % 9;
            boolean blocked =
                    (col < 8 && game.hasWall(row, col, GameState.Orientation.HORIZONTAL))
                            || (col > 0
                                    && game.hasWall(
                                            row, col - 1, GameState.Orientation.HORIZONTAL));
            lines.add(legacyEdge(blocked));
        }
        return String.join("\n", lines);
    }

    private static String legacyEdge(boolean blocked) {
        return blocked ? "null" : "(javax.swing.JButton[example] : javax.swing.JButton[example])";
    }

    private static GameState midGame() {
        GameState game = new GameState();
        check(game.moveTo(7, 4), "Fixture blue moves");
        check(game.moveTo(1, 4), "Fixture green moves");
        check(game.placeWall(4, 3, GameState.Orientation.HORIZONTAL), "Fixture horizontal wall");
        check(game.placeWall(2, 5, GameState.Orientation.VERTICAL), "Fixture vertical wall");
        return game;
    }

    private static GameConfig config() {
        return new GameConfig("صالح", "نیما", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM);
    }

    private static Properties properties(String text) throws IOException {
        Properties data = new Properties();
        data.load(new StringReader(text));
        return data;
    }

    private static void write(Path path, Properties data) throws IOException {
        StringWriter text = new StringWriter();
        data.store(text, "test");
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void assertSession(GameSession actual, GameSession expected, String label) {
        equal(actual.config(), expected.config(), label + " config");
        equal(actual.game().snapshot(), expected.game().snapshot(), label + " state");
        equal(actual.game().legalMoves(), expected.game().legalMoves(), label + " legal moves");
    }

    private static void rejectsIo(CheckedAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            check(
                    expected.getMessage() != null && !expected.getMessage().isBlank(),
                    message + " gives a reason");
        }
    }

    private static void rejectsArgument(CheckedAction action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException | NullPointerException expected) {
            checks++;
        } catch (Exception unexpected) {
            throw new AssertionError(message, unexpected);
        }
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                actual.equals(expected),
                message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
