package Board;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Strict portable Properties saves: original two-player v1 and counted-player v2. */
final class CurrentSaveFormat {
    private static final String VERSION_ONE = "1";
    private static final String VERSION_TWO = "2";
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
        if (state.playerCount() != config.playerCount()) {
            throw new IOException("Game state and configuration have different player counts");
        }
        if (state.playerCount() == 2) {
            data.setProperty("version", VERSION_ONE);
            writeConfig(data, config);
            writeState(data, state);
        } else {
            data.setProperty("version", VERSION_TWO);
            setInteger(data, "player.count", state.playerCount());
            data.setProperty("mode", config.mode().name());
            data.setProperty("difficulty", config.difficulty().name());
            for (int player = 0; player < state.playerCount(); player++) {
                String prefix = playerKey(player);
                data.setProperty(prefix + "name", config.name(player));
                setInteger(data, prefix + "row", state.playerPosition(player).row());
                setInteger(data, prefix + "col", state.playerPosition(player).col());
                setInteger(data, prefix + "walls", state.wallsRemaining(player));
            }
            writeCommonState(data, state);
        }
        StringWriter writer = new StringWriter();
        data.store(writer, "Quoridor save - UTF-8");
        return writer.toString();
    }

    static GameSession read(String text) throws IOException {
        Properties data = new UniqueProperties();
        data.load(new StringReader(text));
        String version = SaveFields.required(data, "version");
        if (!version.equals(VERSION_ONE) && !version.equals(VERSION_TWO)) {
            throw new IOException("Unsupported saved game version: " + version);
        }
        int count = wallCount(data);
        List<GameState.Wall> walls = readWalls(data, count);
        if (version.equals(VERSION_TWO)) {
            return readVersionTwo(data, walls);
        }
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
        writeCommonState(data, state);
    }

    private static void writeCommonState(Properties data, GameState.Snapshot state) {
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

    private static GameSession readVersionTwo(Properties data, List<GameState.Wall> walls)
            throws IOException {
        int count = SaveFields.integer(data, "player.count");
        if (count != 2 && count != 4) {
            throw new IOException("Saved game player count must be 2 or 4");
        }
        Set<String> expected =
                new HashSet<>(
                        Set.of(
                                "version",
                                "player.count",
                                "mode",
                                "difficulty",
                                "turn",
                                "winner",
                                "wall.count"));
        List<String> names = new ArrayList<>();
        List<GameState.Cell> players = new ArrayList<>();
        List<Integer> stocks = new ArrayList<>();
        for (int player = 0; player < count; player++) {
            String prefix = playerKey(player);
            for (String suffix : List.of("name", "row", "col", "walls")) {
                expected.add(prefix + suffix);
            }
            names.add(SaveFields.required(data, prefix + "name"));
            players.add(
                    new GameState.Cell(
                            SaveFields.integer(data, prefix + "row"),
                            SaveFields.integer(data, prefix + "col")));
            stocks.add(SaveFields.integer(data, prefix + "walls"));
        }
        for (int index = 0; index < walls.size(); index++) {
            expected.add(wallKey(index));
        }
        if (!data.stringPropertyNames().equals(expected)) {
            throw new IOException("Saved game contains missing or unexpected fields");
        }
        GameConfig config =
                new GameConfig(
                        names,
                        GameConfig.Mode.valueOf(SaveFields.required(data, "mode")),
                        BotPlayer.Difficulty.valueOf(SaveFields.required(data, "difficulty")));
        GameState game =
                GameState.fromSnapshot(
                        new GameState.Snapshot(
                                players,
                                stocks,
                                SaveFields.integer(data, "turn"),
                                SaveFields.integer(data, "winner"),
                                walls));
        return new GameSession(game, config);
    }

    private static String playerKey(int player) {
        return "player." + player + ".";
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
