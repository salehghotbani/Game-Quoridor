package Board;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Imports the original 9-by-9 Swing-button/graph text dump without its old dependencies. */
final class LegacySaveReader {
    private static final int COLUMNS = 9;
    private static final int ANCHORS_PER_ROW = 8;
    private static final int ANCHORS_PER_ORIENTATION = 64;
    private static final int FIRST_PAWN_BUTTON = 128;
    private static final int LAST_PAWN_BUTTON = 208;
    private static final int HEADER_LINES = 4;
    private static final int BUTTON_ROWS = 210;
    private static final int FILE_LINES = 367;
    private static final int HORIZONTAL_EDGES_START = 214;
    private static final int HORIZONTAL_EDGES_COUNT = 80;
    private static final int GRAPH_SEPARATOR = 294;
    private static final int VERTICAL_EDGES_START = 295;
    private static final int VERTICAL_EDGES_COUNT = 72;

    private LegacySaveReader() {}

    static boolean recognizes(List<String> lines) {
        return lines.size() >= 3 && (lines.get(2).equals("true") || lines.get(2).equals("false"));
    }

    static GameSession read(List<String> lines) throws IOException {
        requireCompleteFile(lines);
        Header header = readHeader(lines.get(3));
        BoardData board = readBoard(lines);
        validateGraphEntries(lines);
        requirePawns(board);
        int turn = lines.get(2).equals("true") ? 0 : 1;
        requireActivePosition(board, turn, header.activePosition());
        int winner = board.blue().row() == 0 ? 0 : board.green().row() == ANCHORS_PER_ROW ? 1 : -1;
        GameState game =
                GameState.fromSnapshot(
                        new GameState.Snapshot(
                                board.blue(),
                                board.green(),
                                header.blueWalls(),
                                header.greenWalls(),
                                winner == -1 ? turn : winner,
                                winner,
                                board.walls()));
        validateGraphWalls(lines, game);
        GameConfig config =
                new GameConfig(
                        playerName(lines.get(0), "Player1:"),
                        playerName(lines.get(1), "Player2:"),
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.MEDIUM);
        return new GameSession(game, config);
    }

    private static void requireCompleteFile(List<String> lines) throws IOException {
        if (lines.size() != FILE_LINES || !lines.get(GRAPH_SEPARATOR).equals("hg")) {
            throw new IOException("Legacy saved game is incomplete or corrupt");
        }
    }

    private static Header readHeader(String line) throws IOException {
        String[] fields = line.split(" \\. ", -1);
        if (fields.length != 13) {
            throw new IOException("Legacy saved game has an invalid header");
        }
        for (int offset : new int[] {0, 5}) {
            for (int component = 0; component < 3; component++) {
                int value = SaveFields.integer(fields[offset + component], "legacy color");
                if (!validColorComponent(value)) {
                    throw new IOException("Legacy saved game has an invalid color");
                }
            }
            SaveFields.legacyBoolean(fields[offset + 3]);
            SaveFields.legacyBoolean(fields[offset + 4]);
        }
        int activePosition = SaveFields.integer(fields[10], "legacy position");
        if (activePosition < FIRST_PAWN_BUTTON || activePosition > LAST_PAWN_BUTTON) {
            throw new IOException("Legacy saved game has an invalid active position");
        }
        return new Header(
                activePosition,
                SaveFields.integer(fields[11], "legacy blue walls"),
                SaveFields.integer(fields[12], "legacy green walls"));
    }

    private static BoardData readBoard(List<String> lines) throws IOException {
        GameState.Cell blue = null;
        GameState.Cell green = null;
        List<GameState.Wall> walls = new ArrayList<>();
        for (int index = 0; index < BUTTON_ROWS; index++) {
            ButtonStyle button = readButton(lines.get(index + HEADER_LINES));
            if (index < FIRST_PAWN_BUTTON && button.orange()) {
                if (!button.visible() || button.enabled()) {
                    throw new IOException("Legacy wall is not placed consistently");
                }
                walls.add(wallForButton(index));
            } else if (index >= FIRST_PAWN_BUTTON && index <= LAST_PAWN_BUTTON) {
                GameState.Cell cell = cellForButton(index);
                if (button.blue()) {
                    if (blue != null) {
                        throw new IOException("Legacy saved game contains multiple blue pawns");
                    }
                    blue = cell;
                } else if (button.green()) {
                    if (green != null) {
                        throw new IOException("Legacy saved game contains multiple green pawns");
                    }
                    green = cell;
                }
            }
        }
        return new BoardData(blue, green, walls);
    }

    private static ButtonStyle readButton(String line) throws IOException {
        String[] fields = line.split(" ", -1);
        if (fields.length != 5) {
            throw new IOException("Legacy saved game has an invalid board row");
        }
        int red = SaveFields.integer(fields[0], "legacy red");
        int green = SaveFields.integer(fields[1], "legacy green");
        int blue = SaveFields.integer(fields[2], "legacy blue");
        if (!validColorComponent(red)
                || !validColorComponent(green)
                || !validColorComponent(blue)) {
            throw new IOException("Legacy saved game has an invalid board color");
        }
        return new ButtonStyle(
                red,
                green,
                blue,
                SaveFields.legacyBoolean(fields[3]),
                SaveFields.legacyBoolean(fields[4]));
    }

    private static boolean validColorComponent(int value) {
        return value >= 0 && value <= 255;
    }

    private static GameState.Wall wallForButton(int index) {
        int anchor = index % ANCHORS_PER_ORIENTATION;
        return new GameState.Wall(
                anchor / ANCHORS_PER_ROW,
                ANCHORS_PER_ROW - 1 - anchor % ANCHORS_PER_ROW,
                index < ANCHORS_PER_ORIENTATION
                        ? GameState.Orientation.VERTICAL
                        : GameState.Orientation.HORIZONTAL);
    }

    private static GameState.Cell cellForButton(int index) {
        int cell = index - FIRST_PAWN_BUTTON;
        return new GameState.Cell(cell / COLUMNS, COLUMNS - 1 - cell % COLUMNS);
    }

    private static void requirePawns(BoardData board) throws IOException {
        if (board.blue() == null || board.green() == null) {
            throw new IOException("Legacy saved game is missing a pawn");
        }
    }

    private static void requireActivePosition(BoardData board, int turn, int savedPosition)
            throws IOException {
        GameState.Cell active = turn == 0 ? board.blue() : board.green();
        int activeIndex = FIRST_PAWN_BUTTON + active.row() * COLUMNS + (COLUMNS - 1 - active.col());
        if (activeIndex != savedPosition) {
            throw new IOException("Legacy saved game has inconsistent turn information");
        }
    }

    private static void validateGraphEntries(List<String> lines) throws IOException {
        for (int entry = 0; entry < HORIZONTAL_EDGES_COUNT; entry++) {
            validateEdge(lines.get(HORIZONTAL_EDGES_START + entry), horizontalRowEnd(entry));
        }
        for (int entry = 0; entry < VERTICAL_EDGES_COUNT; entry++) {
            validateEdge(lines.get(VERTICAL_EDGES_START + entry), false);
        }
    }

    private static void validateEdge(String edge, boolean rowEnd) throws IOException {
        boolean graphDescription = edge.startsWith("(javax.swing.JButton[") && edge.endsWith("])");
        if (rowEnd ? !edge.equals("Enter") : !(edge.equals("null") || graphDescription)) {
            throw new IOException("Legacy saved game has an invalid graph entry");
        }
    }

    private static void validateGraphWalls(List<String> lines, GameState game) throws IOException {
        for (int entry = 0; entry < HORIZONTAL_EDGES_COUNT; entry++) {
            if (horizontalRowEnd(entry)) {
                continue;
            }
            int index = sourceCell(entry, HORIZONTAL_EDGES_COUNT);
            int row = index / COLUMNS;
            int boundary = ANCHORS_PER_ROW - 1 - index % COLUMNS;
            boolean blocked =
                    (row < ANCHORS_PER_ROW
                                    && game.hasWall(row, boundary, GameState.Orientation.VERTICAL))
                            || (row > 0
                                    && game.hasWall(
                                            row - 1, boundary, GameState.Orientation.VERTICAL));
            if (lines.get(HORIZONTAL_EDGES_START + entry).equals("null") != blocked) {
                throw new IOException("Legacy graph and vertical walls disagree");
            }
        }
        for (int entry = 0; entry < VERTICAL_EDGES_COUNT; entry++) {
            int index = sourceCell(entry, VERTICAL_EDGES_COUNT);
            int row = index / COLUMNS;
            int col = COLUMNS - 1 - index % COLUMNS;
            boolean blocked =
                    (col < ANCHORS_PER_ROW
                                    && game.hasWall(row, col, GameState.Orientation.HORIZONTAL))
                            || (col > 0
                                    && game.hasWall(
                                            row, col - 1, GameState.Orientation.HORIZONTAL));
            if (lines.get(VERTICAL_EDGES_START + entry).equals("null") != blocked) {
                throw new IOException("Legacy graph and horizontal walls disagree");
            }
        }
    }

    private static boolean horizontalRowEnd(int entry) {
        return entry < HORIZONTAL_EDGES_COUNT - 1
                && (sourceCell(entry, HORIZONTAL_EDGES_COUNT) + 1) % COLUMNS == 0;
    }

    private static int sourceCell(int entry, int entryCount) {
        // The old writer duplicated its penultimate edge instead of writing the last one.
        return Math.min(entry, entryCount - 2);
    }

    private static String playerName(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()).strip() : name;
    }

    private record Header(int activePosition, int blueWalls, int greenWalls) {}

    private record BoardData(
            GameState.Cell blue, GameState.Cell green, List<GameState.Wall> walls) {}

    private record ButtonStyle(
            int red, int greenComponent, int blueComponent, boolean visible, boolean enabled) {
        boolean orange() {
            return red == 255 && greenComponent == 200 && blueComponent == 0;
        }

        boolean blue() {
            return red == 0 && greenComponent == 0 && blueComponent == 255;
        }

        boolean green() {
            return red == 0 && greenComponent == 255 && blueComponent == 0;
        }
    }
}
