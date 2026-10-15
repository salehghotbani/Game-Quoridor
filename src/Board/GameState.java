package Board;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The rules and state of a local two-player Quoridor game. */
public final class GameState {
    public static final int SIZE = 9;
    public static final int STARTING_WALLS = 10;

    public record Cell(int row, int col) { }

    public enum Orientation { HORIZONTAL, VERTICAL }

    public record Wall(int row, int col, Orientation orientation) { }

    public record Snapshot(Cell blue, Cell green, int blueWalls, int greenWalls,
                           int currentPlayer, int winner, List<Wall> walls) {
        public Snapshot {
            walls = walls == null ? null : Collections.unmodifiableList(new ArrayList<>(walls));
        }
    }

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final Cell[] players = new Cell[2];
    private final int[] wallsRemaining = new int[2];
    private final boolean[][] horizontalWalls = new boolean[SIZE - 1][SIZE - 1];
    private final boolean[][] verticalWalls = new boolean[SIZE - 1][SIZE - 1];
    private int currentPlayer;
    private int winner;

    public GameState() {
        reset();
    }

    public void reset() {
        players[0] = new Cell(SIZE - 1, SIZE / 2);
        players[1] = new Cell(0, SIZE / 2);
        Arrays.fill(wallsRemaining, STARTING_WALLS);
        for (boolean[] row : horizontalWalls) {
            Arrays.fill(row, false);
        }
        for (boolean[] row : verticalWalls) {
            Arrays.fill(row, false);
        }
        currentPlayer = 0;
        winner = -1;
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
        return new Snapshot(players[0], players[1], wallsRemaining[0], wallsRemaining[1],
                currentPlayer, winner, walls);
    }

    /** Validates a saved position completely before returning an independent game. */
    public static GameState fromSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.blue() == null || snapshot.green() == null
                || snapshot.walls() == null || !insideBoard(snapshot.blue().row(), snapshot.blue().col())
                || !insideBoard(snapshot.green().row(), snapshot.green().col())
                || snapshot.blue().equals(snapshot.green())
                || snapshot.blueWalls() < 0 || snapshot.blueWalls() > STARTING_WALLS
                || snapshot.greenWalls() < 0 || snapshot.greenWalls() > STARTING_WALLS
                || snapshot.walls().size() != STARTING_WALLS * 2 - snapshot.blueWalls() - snapshot.greenWalls()
                || (snapshot.currentPlayer() != 0 && snapshot.currentPlayer() != 1)
                || snapshot.winner() < -1 || snapshot.winner() > 1) {
            throw new IllegalArgumentException("Invalid saved game state");
        }
        boolean blueFinished = snapshot.blue().row() == goalRow(0);
        boolean greenFinished = snapshot.green().row() == goalRow(1);
        if ((snapshot.winner() == -1 && (blueFinished || greenFinished))
                || (snapshot.winner() == 0 && (!blueFinished || greenFinished || snapshot.currentPlayer() != 0))
                || (snapshot.winner() == 1 && (!greenFinished || blueFinished || snapshot.currentPlayer() != 1))) {
            throw new IllegalArgumentException("Saved winner, turn, and goal positions disagree");
        }
        GameState game = new GameState();
        game.players[0] = snapshot.blue();
        game.players[1] = snapshot.green();
        game.wallsRemaining[0] = snapshot.blueWalls();
        game.wallsRemaining[1] = snapshot.greenWalls();
        game.currentPlayer = snapshot.currentPlayer();
        game.winner = snapshot.winner();
        for (Wall wall : snapshot.walls()) {
            if (wall == null || !game.wallFits(wall.row(), wall.col(), wall.orientation())) {
                throw new IllegalArgumentException("Saved walls overlap, cross, or lie outside the board");
            }
            (wall.orientation() == Orientation.HORIZONTAL ? game.horizontalWalls : game.verticalWalls)
                    [wall.row()][wall.col()] = true;
        }
        if (!game.hasPath(0, -1, -1, null) || !game.hasPath(1, -1, -1, null)) {
            throw new IllegalArgumentException("Saved walls must leave a goal route for both players");
        }
        return game;
    }

    public Set<Cell> legalMoves() {
        return legalMovesFor(currentPlayer);
    }

    Set<Cell> legalMovesFor(int activePlayer) {
        if (winner != -1) {
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
        if (winner != -1 || wallsRemaining[currentPlayer] == 0
                || !wallFits(row, col, orientation)) {
            return false;
        }
        return hasPath(0, row, col, orientation) && hasPath(1, row, col, orientation);
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
        boolean[][] walls = orientation == Orientation.HORIZONTAL ? horizontalWalls : verticalWalls;
        walls[row][col] = true;
        wallsRemaining[currentPlayer]--;
        currentPlayer = 1 - currentPlayer;
        return true;
    }

    public boolean hasWall(int row, int col, Orientation orientation) {
        if (!insideAnchors(row, col) || orientation == null) {
            return false;
        }
        return (orientation == Orientation.HORIZONTAL ? horizontalWalls : verticalWalls)[row][col];
    }

    private boolean hasPath(int player, int wallRow, int wallCol, Orientation orientation) {
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
                if (insideBoard(row, col) && !visited[row][col]
                        && !blocked(cell.row(), cell.col(), row, col, wallRow, wallCol, orientation)) {
                    visited[row][col] = true;
                    queue.add(new Cell(row, col));
                }
            }
        }
        return false;
    }

    private boolean blocked(int fromRow, int fromCol, int toRow, int toCol) {
        return blocked(fromRow, fromCol, toRow, toCol, -1, -1, null);
    }

    private boolean blocked(int fromRow, int fromCol, int toRow, int toCol,
                            int wallRow, int wallCol, Orientation orientation) {
        if (fromRow != toRow) {
            int boundary = Math.min(fromRow, toRow);
            return (fromCol < SIZE - 1 && horizontalWalls[boundary][fromCol])
                    || (fromCol > 0 && horizontalWalls[boundary][fromCol - 1])
                    || (orientation == Orientation.HORIZONTAL && wallRow == boundary
                    && (wallCol == fromCol || wallCol == fromCol - 1));
        }
        int boundary = Math.min(fromCol, toCol);
        return (fromRow < SIZE - 1 && verticalWalls[fromRow][boundary])
                || (fromRow > 0 && verticalWalls[fromRow - 1][boundary])
                || (orientation == Orientation.VERTICAL && wallCol == boundary
                && (wallRow == fromRow || wallRow == fromRow - 1));
    }

    private static boolean insideBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private static boolean insideAnchors(int row, int col) {
        return row >= 0 && row < SIZE - 1 && col >= 0 && col < SIZE - 1;
    }

    private static int goalRow(int player) {
        return player == 0 ? 0 : SIZE - 1;
    }

    private static void checkPlayer(int player) {
        if (player != 0 && player != 1) {
            throw new IllegalArgumentException("Player must be 0 or 1");
        }
    }
}
