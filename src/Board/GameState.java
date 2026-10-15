package Board;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The rules and state of a two- or four-player Quoridor game. */
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
            List<Cell> players,
            List<Integer> wallsRemaining,
            int currentPlayer,
            int winner,
            List<Wall> walls) {
        public Snapshot {
            players = immutableOrNull(players);
            wallsRemaining = immutableOrNull(wallsRemaining);
            walls = immutableOrNull(walls);
        }

        public Snapshot(
                Cell blue,
                Cell green,
                int blueWalls,
                int greenWalls,
                int currentPlayer,
                int winner,
                List<Wall> walls) {
            this(
                    Arrays.asList(blue, green),
                    List.of(blueWalls, greenWalls),
                    currentPlayer,
                    winner,
                    walls);
        }

        public int playerCount() {
            return players == null ? 0 : players.size();
        }

        public Cell playerPosition(int player) {
            return players.get(player);
        }

        public int wallsRemaining(int player) {
            return wallsRemaining.get(player);
        }

        public Cell blue() {
            return playerPosition(0);
        }

        public Cell green() {
            return playerPosition(1);
        }

        public int blueWalls() {
            return wallsRemaining(0);
        }

        public int greenWalls() {
            return wallsRemaining(1);
        }
    }

    private static final int BLUE_PLAYER = 0;
    private static final int GREEN_PLAYER = 1;
    private static final int TWO_PLAYERS = 2;
    private static final int FOUR_PLAYERS = 4;
    private static final int NO_WINNER = -1;
    // Blue is south, red west, green north and gold east: follow the board clockwise.
    private static final int[] FOUR_PLAYER_NEXT = {2, 3, 1, 0};
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final Cell[] players;
    private final int[] wallsRemaining;
    private final boolean[][] horizontalWalls = new boolean[SIZE - 1][SIZE - 1];
    private final boolean[][] verticalWalls = new boolean[SIZE - 1][SIZE - 1];
    private int currentPlayer;
    private int winner;

    public GameState() {
        this(TWO_PLAYERS);
    }

    public GameState(int playerCount) {
        if (!supportedPlayerCount(playerCount)) {
            throw new IllegalArgumentException("Player count must be 2 or 4");
        }
        players = new Cell[playerCount];
        wallsRemaining = new int[playerCount];
        reset();
    }

    public int playerCount() {
        return players.length;
    }

    public int startingWalls() {
        return STARTING_WALLS * TWO_PLAYERS / playerCount();
    }

    public boolean isGoal(int player, Cell cell) {
        checkPlayer(player);
        return cell != null && insideBoard(cell.row(), cell.col()) && atGoal(player, cell);
    }

    public void reset() {
        players[BLUE_PLAYER] = new Cell(SIZE - 1, SIZE / 2);
        players[GREEN_PLAYER] = new Cell(0, SIZE / 2);
        if (playerCount() == FOUR_PLAYERS) {
            players[2] = new Cell(SIZE / 2, 0);
            players[3] = new Cell(SIZE / 2, SIZE - 1);
        }
        Arrays.fill(wallsRemaining, startingWalls());
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
        GameState copy = new GameState(playerCount());
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
                Arrays.asList(players),
                Arrays.stream(wallsRemaining).boxed().toList(),
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

        GameState game = new GameState(snapshot.playerCount());
        game.restorePlayerState(snapshot);
        game.restoreWalls(snapshot.walls());
        game.requireGoalRoutes();
        return game;
    }

    private static void validateSnapshotFields(Snapshot snapshot) {
        if (snapshot == null
                || snapshot.players() == null
                || !supportedPlayerCount(snapshot.playerCount())
                || snapshot.wallsRemaining() == null
                || snapshot.wallsRemaining().size() != snapshot.playerCount()
                || snapshot.walls() == null) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotPawns(Snapshot snapshot) {
        Set<Cell> positions = new LinkedHashSet<>();
        for (Cell player : snapshot.players()) {
            if (player == null
                    || !insideBoard(player.row(), player.col())
                    || !positions.add(player)) {
                throw invalidSnapshot();
            }
        }
    }

    private static void validateSnapshotInventory(Snapshot snapshot) {
        int initialStock = STARTING_WALLS * TWO_PLAYERS / snapshot.playerCount();
        int placedWalls = initialStock * snapshot.playerCount();
        for (Integer stock : snapshot.wallsRemaining()) {
            if (stock == null || stock < 0 || stock > initialStock) {
                throw invalidSnapshot();
            }
            placedWalls -= stock;
        }
        if (snapshot.walls().size() != placedWalls) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotTurn(Snapshot snapshot) {
        if (snapshot.currentPlayer() < 0
                || snapshot.currentPlayer() >= snapshot.playerCount()
                || snapshot.winner() < NO_WINNER
                || snapshot.winner() >= snapshot.playerCount()) {
            throw invalidSnapshot();
        }
    }

    private static void validateSnapshotGoals(Snapshot snapshot) {
        for (int player = 0; player < snapshot.playerCount(); player++) {
            if (atGoal(player, snapshot.playerPosition(player)) != (snapshot.winner() == player)) {
                throw new IllegalArgumentException(
                        "Saved winner, turn, and goal positions disagree");
            }
        }
        if (snapshot.winner() != NO_WINNER && snapshot.currentPlayer() != snapshot.winner()) {
            throw new IllegalArgumentException("Saved winner must retain the turn");
        }
    }

    private static IllegalArgumentException invalidSnapshot() {
        return new IllegalArgumentException("Invalid saved game state");
    }

    private void restorePlayerState(Snapshot snapshot) {
        for (int player = 0; player < playerCount(); player++) {
            players[player] = snapshot.playerPosition(player);
            wallsRemaining[player] = snapshot.wallsRemaining(player);
        }
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
        for (int player = 0; player < playerCount(); player++) {
            if (!hasPath(player, null)) {
                throw new IllegalArgumentException(
                        "Saved walls must leave a goal route for every player");
            }
        }
    }

    public Set<Cell> legalMoves() {
        return legalMovesFor(currentPlayer);
    }

    Set<Cell> legalMovesFor(int activePlayer) {
        checkPlayer(activePlayer);
        if (winner != NO_WINNER) {
            return Collections.emptySet();
        }
        Set<Cell> moves = new LinkedHashSet<>();
        Cell player = players[activePlayer];
        for (int[] direction : DIRECTIONS) {
            int row = player.row() + direction[0];
            int col = player.col() + direction[1];
            if (!insideBoard(row, col) || blocked(player.row(), player.col(), row, col)) {
                continue;
            }
            if (!occupied(row, col)) {
                moves.add(new Cell(row, col));
                continue;
            }
            int jumpRow = row + direction[0];
            int jumpCol = col + direction[1];
            if (insideBoard(jumpRow, jumpCol)
                    && !blocked(row, col, jumpRow, jumpCol)
                    && !occupied(jumpRow, jumpCol)) {
                moves.add(new Cell(jumpRow, jumpCol));
            } else {
                // A wall, edge, or another pawn behind the adjacent pawn permits a side step.
                addSideStep(moves, row, col, direction[1], direction[0]);
                addSideStep(moves, row, col, -direction[1], -direction[0]);
            }
        }
        return Collections.unmodifiableSet(moves);
    }

    private void addSideStep(Set<Cell> moves, int row, int col, int rowStep, int colStep) {
        int sideRow = row + rowStep;
        int sideCol = col + colStep;
        if (insideBoard(sideRow, sideCol)
                && !blocked(row, col, sideRow, sideCol)
                && !occupied(sideRow, sideCol)) {
            moves.add(new Cell(sideRow, sideCol));
        }
    }

    public boolean moveTo(int row, int col) {
        Cell destination = new Cell(row, col);
        if (!legalMoves().contains(destination)) {
            return false;
        }
        players[currentPlayer] = destination;
        if (atGoal(currentPlayer, destination)) {
            winner = currentPlayer;
        } else {
            advanceTurn();
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
        for (int player = 0; player < playerCount(); player++) {
            if (!hasPath(player, candidate)) {
                return false;
            }
        }
        return true;
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
        advanceTurn();
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
            if (atGoal(player, cell)) {
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

    private boolean occupied(int row, int col) {
        for (Cell player : players) {
            if (player.row() == row && player.col() == col) {
                return true;
            }
        }
        return false;
    }

    private void advanceTurn() {
        currentPlayer =
                playerCount() == TWO_PLAYERS ? 1 - currentPlayer : FOUR_PLAYER_NEXT[currentPlayer];
    }

    private static boolean atGoal(int player, Cell cell) {
        return switch (player) {
            case 0 -> cell.row() == 0;
            case 1 -> cell.row() == SIZE - 1;
            case 2 -> cell.col() == SIZE - 1;
            case 3 -> cell.col() == 0;
            default -> false;
        };
    }

    private void checkPlayer(int player) {
        if (player < 0 || player >= playerCount()) {
            throw new IllegalArgumentException("Player index is outside this game");
        }
    }

    private static boolean supportedPlayerCount(int count) {
        return count == TWO_PLAYERS || count == FOUR_PLAYERS;
    }

    private static <T> List<T> immutableOrNull(List<T> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
