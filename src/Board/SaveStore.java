package Board;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Versioned, bounded text saves. Loading a save never executes Java object data. */
public final class SaveStore {
    private static final int MAX_SAVE_BYTES = 64 * 1024;
    private static final int MAX_LEGACY_BYTES = 256 * 1024;
    private static final Set<String> BASE_KEYS = Set.of("version", "blue.name", "green.name",
            "mode", "difficulty", "blue.row", "blue.col", "green.row", "green.col",
            "blue.walls", "green.walls", "turn", "winner", "wall.count");

    private SaveStore() { }

    /** Writes in the destination directory, then atomically replaces the completed save. */
    public static void save(Path path, GameSession session) throws IOException {
        Objects.requireNonNull(path, "Save path is required");
        Objects.requireNonNull(session, "Game session is required");
        GameState.Snapshot state = session.game().snapshot();
        try {
            GameState.fromSnapshot(state);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Cannot save an invalid game: " + invalid.getMessage(), invalid);
        }
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("blue.name", session.config().blueName());
        data.setProperty("green.name", session.config().greenName());
        data.setProperty("mode", session.config().mode().name());
        data.setProperty("difficulty", session.config().difficulty().name());
        data.setProperty("blue.row", Integer.toString(state.blue().row()));
        data.setProperty("blue.col", Integer.toString(state.blue().col()));
        data.setProperty("green.row", Integer.toString(state.green().row()));
        data.setProperty("green.col", Integer.toString(state.green().col()));
        data.setProperty("blue.walls", Integer.toString(state.blueWalls()));
        data.setProperty("green.walls", Integer.toString(state.greenWalls()));
        data.setProperty("turn", Integer.toString(state.currentPlayer()));
        data.setProperty("winner", Integer.toString(state.winner()));
        data.setProperty("wall.count", Integer.toString(state.walls().size()));
        for (int i = 0; i < state.walls().size(); i++) {
            GameState.Wall wall = state.walls().get(i);
            data.setProperty("wall." + i, wall.row() + "," + wall.col() + "," + wall.orientation().name());
        }
        StringWriter writer = new StringWriter();
        data.store(writer, "Quoridor save - UTF-8");
        byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SAVE_BYTES) {
            throw new IOException("Save file exceeds the 64 KiB limit");
        }
        Path destination = path.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null || destination.getFileName() == null) {
            throw new IOException("Choose a file path for the saved game");
        }
        Path temporary = Files.createTempFile(parent, ".quoridor-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("This filesystem does not support atomic game saves; the original save was kept", unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Loads the current format or safely imports the original application's text save. */
    public static GameSession load(Path path) throws IOException {
        Objects.requireNonNull(path, "Save path is required");
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(MAX_LEGACY_BYTES + 1);
        }
        if (bytes.length > MAX_LEGACY_BYTES) {
            throw new IOException("Saved game is too large");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Saved game is not valid UTF-8 text", invalid);
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        try {
            List<String> lines = text.lines().toList();
            if (lines.size() >= 3 && (lines.get(2).equals("true") || lines.get(2).equals("false"))) {
                return loadLegacy(lines);
            }
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IOException("Saved game exceeds the 64 KiB limit");
            }
            return loadCurrent(text);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Saved game is corrupt: " + invalid.getMessage(), invalid);
        }
    }

    private static GameSession loadCurrent(String text) throws IOException {
        Properties data = new Properties() {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate field: " + key);
                }
                return super.put(key, value);
            }
        };
        data.load(new StringReader(text));
        String version = required(data, "version");
        if (!version.equals("1")) {
            throw new IOException("Unsupported saved game version: " + version);
        }
        int count = integer(data, "wall.count");
        if (count < 0 || count > 20) {
            throw new IOException("Saved game has an invalid wall count");
        }
        Set<String> expectedKeys = new HashSet<>(BASE_KEYS);
        List<GameState.Wall> walls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = "wall." + i;
            expectedKeys.add(key);
            String[] fields = required(data, key).split(",", -1);
            if (fields.length != 3) {
                throw new IOException("Saved game has an invalid wall: " + key);
            }
            walls.add(new GameState.Wall(strictInteger(fields[0], key), strictInteger(fields[1], key),
                    GameState.Orientation.valueOf(fields[2])));
        }
        if (!data.stringPropertyNames().equals(expectedKeys)) {
            throw new IOException("Saved game contains missing or unexpected fields");
        }
        GameConfig config = new GameConfig(required(data, "blue.name"), required(data, "green.name"),
                GameConfig.Mode.valueOf(required(data, "mode")),
                BotPlayer.Difficulty.valueOf(required(data, "difficulty")));
        GameState game = GameState.fromSnapshot(new GameState.Snapshot(
                new GameState.Cell(integer(data, "blue.row"), integer(data, "blue.col")),
                new GameState.Cell(integer(data, "green.row"), integer(data, "green.col")),
                integer(data, "blue.walls"), integer(data, "green.walls"),
                integer(data, "turn"), integer(data, "winner"), walls));
        return new GameSession(game, config);
    }

    private static GameSession loadLegacy(List<String> lines) throws IOException {
        // The original writer stored 4 header lines, 210 button rows, and 153 graph lines.
        if (lines.size() != 367 || !lines.get(294).equals("hg")) {
            throw new IOException("Legacy saved game is incomplete or corrupt");
        }
        String[] header = lines.get(3).split(" \\. ", -1);
        if (header.length != 13) {
            throw new IOException("Legacy saved game has an invalid header");
        }
        for (int offset : new int[]{0, 5}) {
            for (int i = 0; i < 3; i++) {
                int component = strictInteger(header[offset + i], "legacy color");
                if (component < 0 || component > 255) {
                    throw new IOException("Legacy saved game has an invalid color");
                }
            }
            strictBoolean(header[offset + 3]);
            strictBoolean(header[offset + 4]);
        }
        int activePosition = strictInteger(header[10], "legacy position");
        if (activePosition < 128 || activePosition > 208) {
            throw new IOException("Legacy saved game has an invalid active position");
        }
        int blueWalls = strictInteger(header[11], "legacy blue walls");
        int greenWalls = strictInteger(header[12], "legacy green walls");
        GameState.Cell blue = null;
        GameState.Cell green = null;
        List<GameState.Wall> walls = new ArrayList<>();
        for (int i = 0; i < 210; i++) {
            String[] button = lines.get(i + 4).split(" ", -1);
            if (button.length != 5) {
                throw new IOException("Legacy saved game has an invalid board row");
            }
            int red = strictInteger(button[0], "legacy red");
            int componentGreen = strictInteger(button[1], "legacy green");
            int componentBlue = strictInteger(button[2], "legacy blue");
            if (red < 0 || red > 255 || componentGreen < 0 || componentGreen > 255
                    || componentBlue < 0 || componentBlue > 255) {
                throw new IOException("Legacy saved game has an invalid board color");
            }
            strictBoolean(button[3]);
            strictBoolean(button[4]);
            if (i < 128 && red == 255 && componentGreen == 200 && componentBlue == 0) {
                if (!button[3].equals("true") || !button[4].equals("false")) {
                    throw new IOException("Legacy wall is not placed consistently");
                }
                int anchor = i % 64;
                walls.add(new GameState.Wall(anchor / 8, 7 - anchor % 8,
                        i < 64 ? GameState.Orientation.VERTICAL : GameState.Orientation.HORIZONTAL));
            } else if (i >= 128 && i <= 208) {
                GameState.Cell cell = new GameState.Cell((i - 128) / 9, 8 - (i - 128) % 9);
                if (red == 0 && componentGreen == 0 && componentBlue == 255) {
                    if (blue != null) {
                        throw new IOException("Legacy saved game contains multiple blue pawns");
                    }
                    blue = cell;
                } else if (red == 0 && componentGreen == 255 && componentBlue == 0) {
                    if (green != null) {
                        throw new IOException("Legacy saved game contains multiple green pawns");
                    }
                    green = cell;
                }
            }
        }
        // Graph descriptions are inert text, and are checked against placed walls below.
        for (int i = 214; i < 294; i++) {
            int cellIndex = Math.min(i - 214, 78);
            boolean rowEnd = i < 293 && (cellIndex + 1) % 9 == 0;
            validateLegacyEdge(lines.get(i), rowEnd);
        }
        for (int i = 295; i < 367; i++) {
            validateLegacyEdge(lines.get(i), false);
        }
        if (blue == null || green == null) {
            throw new IOException("Legacy saved game is missing a pawn");
        }
        int turn = lines.get(2).equals("true") ? 0 : 1;
        GameState.Cell active = turn == 0 ? blue : green;
        int activeIndex = 128 + active.row() * 9 + (8 - active.col());
        if (activeIndex != activePosition) {
            throw new IOException("Legacy saved game has inconsistent turn information");
        }
        int winner = blue.row() == 0 ? 0 : green.row() == 8 ? 1 : -1;
        GameState game = GameState.fromSnapshot(new GameState.Snapshot(blue, green,
                blueWalls, greenWalls, winner == -1 ? turn : winner, winner, walls));
        validateLegacyGraph(lines, game);
        return new GameSession(game, new GameConfig(legacyName(lines.get(0), "Player1:"),
                legacyName(lines.get(1), "Player2:"), GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM));
    }

    private static void validateLegacyEdge(String edge, boolean rowEnd) throws IOException {
        if (rowEnd ? !edge.equals("Enter") : !(edge.equals("null")
                || (edge.startsWith("(javax.swing.JButton[") && edge.endsWith("])")))) {
            throw new IOException("Legacy saved game has an invalid graph entry");
        }
    }

    private static void validateLegacyGraph(List<String> lines, GameState game) throws IOException {
        for (int i = 0; i < 80; i++) {
            int index = Math.min(i, 78);
            if (i < 79 && (index + 1) % 9 == 0) {
                continue;
            }
            int row = index / 9;
            int boundary = 7 - index % 9;
            boolean blocked = (row < 8 && game.hasWall(row, boundary, GameState.Orientation.VERTICAL))
                    || (row > 0 && game.hasWall(row - 1, boundary, GameState.Orientation.VERTICAL));
            if (lines.get(214 + i).equals("null") != blocked) {
                throw new IOException("Legacy graph and vertical walls disagree");
            }
        }
        for (int i = 0; i < 72; i++) {
            int index = Math.min(i, 70);
            int row = index / 9;
            int col = 8 - index % 9;
            boolean blocked = (col < 8 && game.hasWall(row, col, GameState.Orientation.HORIZONTAL))
                    || (col > 0 && game.hasWall(row, col - 1, GameState.Orientation.HORIZONTAL));
            if (lines.get(295 + i).equals("null") != blocked) {
                throw new IOException("Legacy graph and horizontal walls disagree");
            }
        }
    }

    private static String legacyName(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()).strip() : name;
    }

    private static void strictBoolean(String value) throws IOException {
        if (!value.equals("true") && !value.equals("false")) {
            throw new IOException("Legacy saved game has an invalid boolean");
        }
    }

    private static String required(Properties data, String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null) {
            throw new IOException("Saved game is missing field: " + key);
        }
        return value;
    }

    private static int integer(Properties data, String key) throws IOException {
        return strictInteger(required(data, key), key);
    }

    private static int strictInteger(String value, String key) throws IOException {
        if (!value.matches("-?(0|[1-9][0-9]*)")) {
            throw new IOException("Saved game has an invalid number: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IOException("Saved game number is out of range: " + key, invalid);
        }
    }
}
