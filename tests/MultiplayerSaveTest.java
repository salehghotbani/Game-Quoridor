import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.GameState.Cell;
import Board.GameState.Snapshot;
import Board.SaveStore;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** The network text API and v2 saves share strict, independent two-/four-player validation. */
public final class MultiplayerSaveTest {
    private static int checks;

    private MultiplayerSaveTest() {}

    public static void main(String[] arguments) throws Exception {
        configAndSessionValidation();
        twoPlayerCompatibility();
        fourPlayerRoundTrips();
        rejectCorruptFourPlayerMessages();
        textBoundsAndEncoding();
        System.out.println("Multiplayer save tests passed (" + checks + " checks).");
    }

    private static void configAndSessionValidation() {
        List<String> names = new ArrayList<>(List.of(" Blue ", "Green", " قرمز ", "طلایی 😀"));
        GameConfig config = new GameConfig(names, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.HARD);
        names.clear();
        equal(config.playerCount(), 4, "Configuration copies all four names");
        equal(
                config.playerNames(),
                List.of("Blue", "Green", "قرمز", "طلایی 😀"),
                "Each name is normalized independently");
        equal(config.blueName(), "Blue", "Blue compatibility alias");
        equal(config.greenName(), "Green", "Green compatibility alias");
        equal(config.name(3), "طلایی 😀", "Fourth name available by index");
        rejectsArgument(
                () ->
                        new GameConfig(
                                List.of("A", "B", "C"),
                                GameConfig.Mode.LOCAL,
                                BotPlayer.Difficulty.EASY),
                "Three names rejected");
        rejectsArgument(
                () ->
                        new GameConfig(
                                List.of("A", "B", "C", "D"),
                                GameConfig.Mode.BOT,
                                BotPlayer.Difficulty.EASY),
                "Four-player robot configuration rejected");
        rejectsArgument(
                () ->
                        new GameConfig(
                                List.of("A", "B", "C\nD", "D"),
                                GameConfig.Mode.LOCAL,
                                BotPlayer.Difficulty.EASY),
                "Third name uses full validation");
        rejectsArgument(
                () ->
                        new GameConfig(
                                List.of("A", "B", "C", "x".repeat(25)),
                                GameConfig.Mode.LOCAL,
                                BotPlayer.Difficulty.EASY),
                "Fourth name uses full validation");
        rejectsArgument(
                () -> new GameSession(new GameState(), config),
                "Two-player state cannot use four names");
        rejectsArgument(
                () ->
                        new GameSession(
                                new GameState(4),
                                new GameConfig(
                                        "A",
                                        "B",
                                        GameConfig.Mode.LOCAL,
                                        BotPlayer.Difficulty.EASY)),
                "Four-player state cannot use two names");
        try {
            config.playerNames().clear();
            throw new AssertionError("Names must be immutable");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    private static void twoPlayerCompatibility() throws IOException {
        for (GameConfig.Mode mode : GameConfig.Mode.values()) {
            GameSession original =
                    new GameSession(
                            new GameState(),
                            new GameConfig(
                                    "صالح 😀", "Opponent", mode, BotPlayer.Difficulty.MEDIUM));
            String text = SaveStore.encode(original);
            Properties data = properties(text);
            equal(data.getProperty("version"), "1", "Two-player writes remain v1");
            check(
                    data.containsKey("blue.name") && !data.containsKey("player.count"),
                    "V1 key schema is preserved");
            assertSession(SaveStore.decode(text), original, "Network v1 round trip");
            assertSession(
                    SaveStore.decode("\uFEFF" + text), original, "UTF-8 BOM remains supported");
        }
        GameSession original =
                new GameSession(
                        new GameState(),
                        new GameConfig("A", "B", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY));
        Properties modern = new Properties();
        modern.setProperty("version", "2");
        modern.setProperty("player.count", "2");
        modern.setProperty("mode", "LOCAL");
        modern.setProperty("difficulty", "EASY");
        modern.setProperty("turn", "0");
        modern.setProperty("winner", "-1");
        modern.setProperty("wall.count", "0");
        for (int player = 0; player < 2; player++) {
            String prefix = "player." + player + ".";
            modern.setProperty(prefix + "name", original.config().name(player));
            modern.setProperty(
                    prefix + "row", Integer.toString(original.game().playerPosition(player).row()));
            modern.setProperty(prefix + "col", "4");
            modern.setProperty(prefix + "walls", "10");
        }
        assertSession(
                SaveStore.decode(text(modern)),
                original,
                "V2 reader can also migrate a two-player match");
    }

    private static void fourPlayerRoundTrips() throws Exception {
        Path directory = Files.createTempDirectory("quoridor-four-save-");
        try {
            GameSession original = new GameSession(midGame(), config());
            Snapshot before = original.game().snapshot();
            String encoded = SaveStore.encode(original);
            Properties data = properties(encoded);
            equal(data.getProperty("version"), "2", "Four-player save uses v2");
            equal(data.getProperty("player.count"), "4", "Participant count is explicit");
            check(!data.containsKey("blue.name"), "V2 does not mix incompatible key schemas");
            check(encoded.contains("قرمز"), "V2 preserves readable UTF-8 names");
            equal(original.game().snapshot(), before, "Encoding cannot mutate the live game");
            GameSession decoded = SaveStore.decode(encoded);
            assertSession(decoded, original, "Four-player network round trip");
            check(decoded.game() != original.game(), "Decoded game is independently owned");
            Cell move = decoded.game().legalMoves().iterator().next();
            check(decoded.game().moveTo(move.row(), move.col()), "Loaded participant can continue");
            equal(original.game().snapshot(), before, "Loaded action cannot mutate source game");
            Path file = directory.resolve("بازی چهار نفره.qsave");
            SaveStore.save(file, original);
            assertSession(SaveStore.load(file), original, "Four-player file round trip");
            assertSession(
                    SaveStore.decode(Files.readString(file, StandardCharsets.UTF_8)),
                    original,
                    "File and network decoders use the same schema");

            for (int winner = 0; winner < 4; winner++) {
                List<Cell> players =
                        new ArrayList<>(
                                List.of(
                                        new Cell(3, 3),
                                        new Cell(3, 4),
                                        new Cell(4, 3),
                                        new Cell(4, 4)));
                players.set(
                        winner,
                        switch (winner) {
                            case 0 -> new Cell(0, 0);
                            case 1 -> new Cell(8, 8);
                            case 2 -> new Cell(0, 8);
                            default -> new Cell(8, 0);
                        });
                GameState finished =
                        GameState.fromSnapshot(
                                new Snapshot(
                                        players, List.of(5, 5, 5, 5), winner, winner, List.of()));
                GameSession terminal = new GameSession(finished, config());
                assertSession(
                        SaveStore.decode(SaveStore.encode(terminal)),
                        terminal,
                        "Winner " + winner + " persists");
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void rejectCorruptFourPlayerMessages() throws Exception {
        GameSession source = new GameSession(midGame(), config());
        Snapshot before = source.game().snapshot();
        String valid = SaveStore.encode(source);
        for (String[] corruption :
                new String[][] {
                    {"version", "3"},
                    {"player.count", "0"},
                    {"player.count", "3"},
                    {"player.count", "5"},
                    {"player.count", "2147483648"},
                    {"turn", "4"},
                    {"winner", "4"},
                    {"winner", "2"},
                    {"mode", "BOT"},
                    {"mode", "ONLINE"},
                    {"player.2.name", " "},
                    {"player.3.name", "x".repeat(25)},
                    {"player.2.row", "9"},
                    {"player.3.col", "-1"},
                    {"player.0.row", "1"},
                    {"player.2.col", "8"},
                    {"player.3.col", "0"},
                    {"player.2.walls", "6"},
                    {"player.3.walls", "-1"},
                    {"player.0.walls", "5"},
                    {"wall.count", "21"},
                    {"wall.0", "5,2,DIAGONAL"},
                    {"wall.1", "2,5,VERTICAL"},
                    {"wall.1", "2,5,HORIZONTAL"},
                    {"blue.name", "Unexpected old key"}
                }) {
            Properties malformed = properties(valid);
            malformed.setProperty(corruption[0], corruption[1]);
            rejectsIo(
                    () -> SaveStore.decode(text(malformed)),
                    "Corrupt field rejected: " + corruption[0]);
            equal(
                    source.game().snapshot(),
                    before,
                    "Invalid network input cannot mutate the live state");
        }
        Properties missing = properties(valid);
        missing.remove("player.3.name");
        rejectsIo(() -> SaveStore.decode(text(missing)), "Missing fourth name rejected");
        rejectsIo(() -> SaveStore.decode(valid + "\nplayer.count=4\n"), "Duplicate key rejected");
        rejectsIo(
                () -> SaveStore.decode(valid + "\nplayer\\.count=4\n"),
                "Escaped duplicate key rejected");
        rejectsIo(
                () -> SaveStore.decode(valid.substring(0, valid.length() / 2)),
                "Truncated v2 rejected");
    }

    private static void textBoundsAndEncoding() throws Exception {
        String valid = SaveStore.encode(new GameSession(new GameState(4), config()));
        rejectsIo(() -> SaveStore.decode(null), "Null network text rejected");
        rejectsIo(() -> SaveStore.decode(""), "Empty network text rejected");
        rejectsIo(
                () -> SaveStore.decode(valid + "\n#" + "x".repeat(65536)),
                "Modern network text has the same 64 KiB limit as files");
        rejectsIo(
                () -> SaveStore.decode("x".repeat(256 * 1024 + 1)),
                "Absolute legacy bound enforced before parsing");
        rejectsIo(
                () -> SaveStore.decode(valid + "\n#" + "ا".repeat(33000)),
                "UTF-8 byte limit is independent of Java character count");
        rejectsIo(
                () -> SaveStore.decode(valid + "\n#\uD800"),
                "Unpaired UTF-16 in a network message is rejected");
        rejectsIo(
                () -> SaveStore.decode(valid + "\n#\uDC00"), "Unpaired low surrogate is rejected");
    }

    private static GameState midGame() {
        GameState game = new GameState(4);
        check(
                game.moveTo(7, 4) && game.moveTo(4, 1) && game.moveTo(1, 4) && game.moveTo(4, 7),
                "Fixture moves all participants clockwise");
        check(game.placeWall(5, 2, GameState.Orientation.HORIZONTAL), "Fixture blue wall");
        check(game.placeWall(2, 5, GameState.Orientation.VERTICAL), "Fixture red wall");
        equal(game.currentPlayer(), 1, "Fixture is green's turn");
        return game;
    }

    private static GameConfig config() {
        return new GameConfig(
                List.of("Blue = # \\", "Green O'Brien 100%", "قرمز 😀", "طلایی : چهار"),
                GameConfig.Mode.LOCAL,
                BotPlayer.Difficulty.MEDIUM);
    }

    private static Properties properties(String text) throws IOException {
        Properties result = new Properties();
        result.load(new StringReader(text));
        return result;
    }

    private static String text(Properties properties) throws IOException {
        StringWriter result = new StringWriter();
        properties.store(result, "Four-player test");
        return result.toString();
    }

    private static void assertSession(GameSession actual, GameSession expected, String message) {
        equal(actual.config(), expected.config(), message + " config");
        equal(actual.game().snapshot(), expected.game().snapshot(), message + " state");
        equal(actual.game().legalMoves(), expected.game().legalMoves(), message + " moves");
    }

    private static void rejectsArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void rejectsIo(CheckedAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            check(expected.getMessage() != null && !expected.getMessage().isBlank(), message);
        }
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                java.util.Objects.equals(actual, expected),
                message + ": expected " + expected + ", got " + actual);
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
