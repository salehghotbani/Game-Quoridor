package Board;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The rules and state of a local two-player Quoridor game. */
public final class GameState {
    public static final int SIZE = 9;
    public static final int STARTING_WALLS = 10;

    public record Cell(int row, int col) {}

    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    public record Wall(int row, int col, Orientation orientation) {}

    public record Snapshot(
            Cell blue,
            Cell green,
            int blueWalls,
            int greenWalls,
            int currentPlayer,
            int winner,
            List<Wall> walls) {
        public Snapshot {
            walls = walls == null ? null : Collections.unmodifiableList(new ArrayList<>(walls));
        }
    }

    private static final int BLUE_PLAYER = 0;
    private static final int GREEN_PLAYER = 1;
    private static final int PLAYER_COUNT = 2;
    private static final int NO_WINNER = -1;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final Cell[] players = new Cell[PLAYER_COUNT];
    private final int[] wallsRemaining = new int[PLAYER_COUNT];
    private final boolean[][] horizontalWalls = new boolean[SIZE - 1][SIZE - 1];
    private final boolean[][] verticalWalls = new boolean[SIZE - 1][SIZE - 1];
    private int currentPlayer;
    private int winner;

    public GameState() {
        reset();
    }

    public void reset() {
        players[BLUE_PLAYER] = new Cell(SIZE - 1, SIZE / 2);
        players[GREEN_PLAYER] = new Cell(0, SIZE / 2);
        Arrays.fill(wallsRemaining, STARTING_WALLS);
        for (boolean[] row : horizontalWalls) {
            Arrays.fill(row, false);
        }
        for (boolean[] row : verticalWalls) {
            Arrays.fill(row, false);
        }
        currentPlayer = BLUE_PLAYER;
        winner = NO_WINNER;
    }

    public int currentPlayer() {
        return currentPlayer;
    }

    public Cell playerPosition(int player) {
        checkPlayer(player);
        return players[player];
    }

    public int wallsRemaining(int player) {
        checkPlayer(player);
        return wallsRemaining[player];
    }

    public int winner() {
        return winner;
    }

    public GameState copy() {
        GameState copy = new GameState();
        System.arraycopy(players, 0, copy.players, 0, players.length);
        System.arraycopy(wallsRemaining, 0, copy.wallsRemaining, 0, wallsRemaining.length);
        for (int row = 0; row < SIZE - 1; row++) {
            System.arraycopy(horizontalWalls[row], 0, copy.horizontalWalls[row], 0, SIZE - 1);
            System.arraycopy(verticalWalls[row], 0, copy.verticalWalls[row], 0, SIZE - 1);
        }
        copy.currentPlayer = currentPlayer;
        copy.winner = winner;
        return copy;
    }

    public Snapshot snapshot() {
        List<Wall> walls = new ArrayList<>();
        for (int row = 0; row < SIZE - 1; row++) {
            for (int col = 0; col < SIZE - 1; col++) {
                for (Orientation orientation : Orientation.values()) {
                    if (hasWall(row, col, orientation)) {
                        walls.add(new Wall(row, col, orientation));
                    }
                }
            }
        }
        return new Snapshot(
                players[BLUE_PLAYER],
                players[GREEN_PLAYER],
                wallsRemaining[BLUE_PLAYER],
                wallsRemaining[GREEN_PLAYER],
                currentPlayer,
                winner,
                walls);
    }

    /** Validates a saved position completely before returning an independent game. */
    public static GameState fromSnapshot(Snapshot snapshot) {
        validateSnapshotFields(snapshot);
        validateSnapshotPawns(snapshot);
        validateSnapshotInventory(snapshot);
        validateSnapshotTurn(snapshot);
        validateSnapshotGoals(snapshot);

        GameState game = new GameState();
        game.restorePlayerState(snapshot);
        game.restoreWalls(snapshot.walls());
        game.requireGoalRoutes();
        return game;
    }

    private static void validateSnapshotFields(Snapshot snapshot) {
        if (snapshot == null
                || snapshot.blue() == null
                || snapshot.green() == null
                || snapshot.walls() == null) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotPawns(Snapshot snapshot) {
        if (!insideBoard(snapshot.blue().row(), snapshot.blue().col())
                || !insideBoard(snapshot.green().row(), snapshot.green().col())
                || snapshot.blue().equals(snapshot.green())) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotInventory(Snapshot snapshot) {
        int placedWalls =
                STARTING_WALLS * PLAYER_COUNT - snapshot.blueWalls() - snapshot.greenWalls();
        if (snapshot.blueWalls() < 0
                || snapshot.blueWalls() > STARTING_WALLS
                || snapshot.greenWalls() < 0
                || snapshot.greenWalls() > STARTING_WALLS
                || snapshot.walls().size() != placedWalls) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotTurn(Snapshot snapshot) {
        if (!isPlayer(snapshot.currentPlayer())
                || snapshot.winner() < NO_WINNER
                || snapshot.winner() >= PLAYER_COUNT) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotGoals(Snapshot snapshot) {
        boolean blueFinished = snapshot.blue().row() == goalRow(BLUE_PLAYER);
        boolean greenFinished = snapshot.green().row() == goalRow(GREEN_PLAYER);
        boolean consistent =
                switch (snapshot.winner()) {
                    case NO_WINNER -> !blueFinished && !greenFinished;
                    case BLUE_PLAYER ->
                            blueFinished
                                    && !greenFinished
                                    && snapshot.currentPlayer() == BLUE_PLAYER;
                    case GREEN_PLAYER ->
                            greenFinished
                                    && !blueFinished
                                    && snapshot.currentPlayer() == GREEN_PLAYER;
                    default -> false;
                };
        if (!consistent) {
            throw new IllegalArgumentException("Saved winner, turn, and goal positions disagree");
        }
    }

    private static IllegalArgumentException invalidSnapshot() {
        return new IllegalArgumentException("Invalid saved game state");
    }

    private void restorePlayerState(Snapshot snapshot) {
        players[BLUE_PLAYER] = snapshot.blue();
        players[GREEN_PLAYER] = snapshot.green();
        wallsRemaining[BLUE_PLAYER] = snapshot.blueWalls();
        wallsRemaining[GREEN_PLAYER] = snapshot.greenWalls();
        currentPlayer = snapshot.currentPlayer();
        winner = snapshot.winner();
    }

    private void restoreWalls(List<Wall> walls) {
        for (Wall wall : walls) {
            if (wall == null || !wallFits(wall.row(), wall.col(), wall.orientation())) {
                throw new IllegalArgumentException(
                        "Saved walls overlap, cross, or lie outside the board");
            }
            wallGrid(wall.orientation())[wall.row()][wall.col()] = true;
        }
    }

    private void requireGoalRoutes() {
        if (!hasPath(BLUE_PLAYER, null) || !hasPath(GREEN_PLAYER, null)) {
            throw new IllegalArgumentException(
                    "Saved walls must leave a goal route for both players");
        }
    }

    public Set<Cell> legalMoves() {
        return legalMovesFor(currentPlayer);
    }

    Set<Cell> legalMovesFor(int activePlayer) {
        if (winner != NO_WINNER) {
            return Collections.emptySet();
        }
        Set<Cell> moves = new LinkedHashSet<>();
        Cell player = players[activePlayer];
        Cell opponent = players[1 - activePlayer];
        for (int[] direction : DIRECTIONS) {
            int row = player.row() + direction[0];
            int col = player.col() + direction[1];
            if (!insideBoard(row, col) || blocked(player.row(), player.col(), row, col)) {
                continue;
            }
            if (row != opponent.row() || col != opponent.col()) {
                moves.add(new Cell(row, col));
                continue;
            }
            int jumpRow = row + direction[0];
            int jumpCol = col + direction[1];
            if (insideBoard(jumpRow, jumpCol) && !blocked(row, col, jumpRow, jumpCol)) {
                moves.add(new Cell(jumpRow, jumpCol));
            } else {
                // A wall or the board edge behind the opponent permits a side step.
                addSideStep(moves, row, col, direction[1], direction[0]);
                addSideStep(moves, row, col, -direction[1], -direction[0]);
            }
        }
        return Collections.unmodifiableSet(moves);
    }

    private void addSideStep(Set<Cell> moves, int row, int col, int rowStep, int colStep) {
        int sideRow = row + rowStep;
        int sideCol = col + colStep;
        if (insideBoard(sideRow, sideCol) && !blocked(row, col, sideRow, sideCol)) {
            moves.add(new Cell(sideRow, sideCol));
        }
    }

    public boolean moveTo(int row, int col) {
        Cell destination = new Cell(row, col);
        if (!legalMoves().contains(destination)) {
            return false;
        }
        players[currentPlayer] = destination;
        if (row == goalRow(currentPlayer)) {
            winner = currentPlayer;
        } else {
            currentPlayer = 1 - currentPlayer;
        }
        return true;
    }

    /** Checks a candidate without changing the turn, inventory, walls, or pawn positions. */
    public boolean canPlaceWall(int row, int col, Orientation orientation) {
        if (winner != NO_WINNER
                || wallsRemaining[currentPlayer] == 0
                || !wallFits(row, col, orientation)) {
            return false;
        }
        Wall candidate = new Wall(row, col, orientation);
        return hasPath(BLUE_PLAYER, candidate) && hasPath(GREEN_PLAYER, candidate);
    }

    private boolean wallFits(int row, int col, Orientation orientation) {
        if (!insideAnchors(row, col) || orientation == null) {
            return false;
        }
        if (horizontalWalls[row][col] || verticalWalls[row][col]) {
            return false;
        }
        if (orientation == Orientation.HORIZONTAL) {
            if ((col > 0 && horizontalWalls[row][col - 1])
                    || (col < SIZE - 2 && horizontalWalls[row][col + 1])) {
                return false;
            }
        } else if ((row > 0 && verticalWalls[row - 1][col])
                || (row < SIZE - 2 && verticalWalls[row + 1][col])) {
            return false;
        }
        return true;
    }

    public boolean placeWall(int row, int col, Orientation orientation) {
        if (!canPlaceWall(row, col, orientation)) {
            return false;
        }
        wallGrid(orientation)[row][col] = true;
        wallsRemaining[currentPlayer]--;
        currentPlayer = 1 - currentPlayer;
        return true;
    }

    public boolean hasWall(int row, int col, Orientation orientation) {
        if (!insideAnchors(row, col) || orientation == null) {
            return false;
        }
        return wallGrid(orientation)[row][col];
    }

    private boolean[][] wallGrid(Orientation orientation) {
        return orientation == Orientation.HORIZONTAL ? horizontalWalls : verticalWalls;
    }

    private boolean hasPath(int player, Wall candidate) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Cell start = players[player];
        queue.add(start);
        visited[start.row()][start.col()] = true;
        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            if (cell.row() == goalRow(player)) {
                return true;
            }
            for (int[] direction : DIRECTIONS) {
                int row = cell.row() + direction[0];
                int col = cell.col() + direction[1];
                if (insideBoard(row, col)
                        && !visited[row][col]
                        && !blocked(cell.row(), cell.col(), row, col, candidate)) {
                    visited[row][col] = true;
                    queue.add(new Cell(row, col));
                }
            }
        }
        return false;
    }

    private boolean blocked(int fromRow, int fromCol, int toRow, int toCol) {
        return blocked(fromRow, fromCol, toRow, toCol, null);
    }

    private boolean blocked(int fromRow, int fromCol, int toRow, int toCol, Wall candidate) {
        if (fromRow != toRow) {
            int boundary = Math.min(fromRow, toRow);
            return (fromCol < SIZE - 1 && horizontalWalls[boundary][fromCol])
                    || (fromCol > 0 && horizontalWalls[boundary][fromCol - 1])
                    || (candidate != null
                            && candidate.orientation() == Orientation.HORIZONTAL
                            && candidate.row() == boundary
                            && (candidate.col() == fromCol || candidate.col() == fromCol - 1));
        }
        int boundary = Math.min(fromCol, toCol);
        return (fromRow < SIZE - 1 && verticalWalls[fromRow][boundary])
                || (fromRow > 0 && verticalWalls[fromRow - 1][boundary])
                || (candidate != null
                        && candidate.orientation() == Orientation.VERTICAL
                        && candidate.col() == boundary
                        && (candidate.row() == fromRow || candidate.row() == fromRow - 1));
    }

    private static boolean insideBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private static boolean insideAnchors(int row, int col) {
        return row >= 0 && row < SIZE - 1 && col >= 0 && col < SIZE - 1;
    }

    private static int goalRow(int player) {
        return player == BLUE_PLAYER ? 0 : SIZE - 1;
    }

    private static void checkPlayer(int player) {
        if (!isPlayer(player)) {
            throw new IllegalArgumentException("Player must be 0 or 1");
        }
    }

    private static boolean isPlayer(int player) {
        return player >= 0 && player < PLAYER_COUNT;
    }
}
