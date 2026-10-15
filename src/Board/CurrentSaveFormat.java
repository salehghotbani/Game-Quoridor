package Board;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Encodes and validates version 1 of the portable Properties save format. */
final class CurrentSaveFormat {
    private static final String VERSION = "1";
    private static final int MAX_PLACED_WALLS = 20;
    private static final Set<String> BASE_KEYS =
            Set.of(
                    "version",
                    "blue.name",
                    "green.name",
                    "mode",
                    "difficulty",
                    "blue.row",
                    "blue.col",
                    "green.row",
                    "green.col",
                    "blue.walls",
                    "green.walls",
                    "turn",
                    "winner",
                    "wall.count");

    private CurrentSaveFormat() {}

    static String write(GameState.Snapshot state, GameConfig config) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", VERSION);
        writeConfig(data, config);
        writeState(data, state);
        StringWriter writer = new StringWriter();
        data.store(writer, "Quoridor save - UTF-8");
        return writer.toString();
    }

    static GameSession read(String text) throws IOException {
        Properties data = new UniqueProperties();
        data.load(new StringReader(text));
        requireCurrentVersion(data);
        int count = wallCount(data);
        List<GameState.Wall> walls = readWalls(data, count);
        requireExactKeys(data, count);
        GameConfig config = readConfig(data);
        GameState game = GameState.fromSnapshot(readState(data, walls));
        return new GameSession(game, config);
    }

    private static void writeConfig(Properties data, GameConfig config) {
        data.setProperty("blue.name", config.blueName());
        data.setProperty("green.name", config.greenName());
        data.setProperty("mode", config.mode().name());
        data.setProperty("difficulty", config.difficulty().name());
    }

    private static void writeState(Properties data, GameState.Snapshot state) {
        setInteger(data, "blue.row", state.blue().row());
        setInteger(data, "blue.col", state.blue().col());
        setInteger(data, "green.row", state.green().row());
        setInteger(data, "green.col", state.green().col());
        setInteger(data, "blue.walls", state.blueWalls());
        setInteger(data, "green.walls", state.greenWalls());
        setInteger(data, "turn", state.currentPlayer());
        setInteger(data, "winner", state.winner());
        setInteger(data, "wall.count", state.walls().size());
        for (int index = 0; index < state.walls().size(); index++) {
            GameState.Wall wall = state.walls().get(index);
            data.setProperty(
                    wallKey(index),
                    wall.row() + "," + wall.col() + "," + wall.orientation().name());
        }
    }

    private static void setInteger(Properties data, String key, int value) {
        data.setProperty(key, Integer.toString(value));
    }

    private static void requireCurrentVersion(Properties data) throws IOException {
        String version = SaveFields.required(data, "version");
        if (!version.equals(VERSION)) {
            throw new IOException("Unsupported saved game version: " + version);
        }
    }

    private static int wallCount(Properties data) throws IOException {
        int count = SaveFields.integer(data, "wall.count");
        if (count < 0 || count > MAX_PLACED_WALLS) {
            throw new IOException("Saved game has an invalid wall count");
        }
        return count;
    }

    private static List<GameState.Wall> readWalls(Properties data, int count) throws IOException {
        List<GameState.Wall> walls = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String key = wallKey(index);
            String[] fields = SaveFields.required(data, key).split(",", -1);
            if (fields.length != 3) {
                throw new IOException("Saved game has an invalid wall: " + key);
            }
            walls.add(
                    new GameState.Wall(
                            SaveFields.integer(fields[0], key),
                            SaveFields.integer(fields[1], key),
                            GameState.Orientation.valueOf(fields[2])));
        }
        return walls;
    }

    private static void requireExactKeys(Properties data, int wallCount) throws IOException {
        Set<String> expected = new HashSet<>(BASE_KEYS);
        for (int index = 0; index < wallCount; index++) {
            expected.add(wallKey(index));
        }
        if (!data.stringPropertyNames().equals(expected)) {
            throw new IOException("Saved game contains missing or unexpected fields");
        }
    }

    private static GameConfig readConfig(Properties data) throws IOException {
        return new GameConfig(
                SaveFields.required(data, "blue.name"),
                SaveFields.required(data, "green.name"),
                GameConfig.Mode.valueOf(SaveFields.required(data, "mode")),
                BotPlayer.Difficulty.valueOf(SaveFields.required(data, "difficulty")));
    }

    private static GameState.Snapshot readState(Properties data, List<GameState.Wall> walls)
            throws IOException {
        return new GameState.Snapshot(
                new GameState.Cell(
                        SaveFields.integer(data, "blue.row"), SaveFields.integer(data, "blue.col")),
                new GameState.Cell(
                        SaveFields.integer(data, "green.row"),
                        SaveFields.integer(data, "green.col")),
                SaveFields.integer(data, "blue.walls"),
                SaveFields.integer(data, "green.walls"),
                SaveFields.integer(data, "turn"),
                SaveFields.integer(data, "winner"),
                walls);
    }

    private static String wallKey(int index) {
        return "wall." + index;
    }

    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException("Duplicate field: " + key);
            }
            return super.put(key, value);
        }
    }
}
