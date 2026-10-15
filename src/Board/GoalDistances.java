package Board;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Wall-aware distance estimates for the robot; pawn occupancy is deliberately ignored. */
final class GoalDistances {
    private static final int BOARD_SIZE = GameState.SIZE;
    private static final int CELL_COUNT = BOARD_SIZE * BOARD_SIZE;
    private static final int LAST_COORDINATE = BOARD_SIZE - 1;
    private static final int UNREACHABLE = 100;

    private GoalDistances() {}

    /** Reverse BFS gives every square's distance to any square on the player's goal edge. */
    static int[] toGoal(GameState game, int player) {
        int[] distances = new int[CELL_COUNT];
        Arrays.fill(distances, UNREACHABLE);
        boolean[][] graph = openEdges(game);
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (game.isGoal(player, new GameState.Cell(row, col))) {
                    int cell = row * BOARD_SIZE + col;
                    distances[cell] = 0;
                    pending.add(cell);
                }
            }
        }
        while (!pending.isEmpty()) {
            int cell = pending.remove();
            for (int next = 0; next < CELL_COUNT; next++) {
                if (graph[cell][next] && distances[next] > distances[cell] + 1) {
                    distances[next] = distances[cell] + 1;
                    pending.add(next);
                }
            }
        }
        return distances;
    }

    static int index(GameState.Cell cell) {
        return cell.row() * BOARD_SIZE + cell.col();
    }

    private static boolean[][] openEdges(GameState game) {
        boolean[][] graph = new boolean[CELL_COUNT][CELL_COUNT];
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int from = row * BOARD_SIZE + col;
                if (row < LAST_COORDINATE) {
                    boolean blocked =
                            game.hasWall(row, col, GameState.Orientation.HORIZONTAL)
                                    || game.hasWall(row, col - 1, GameState.Orientation.HORIZONTAL);
                    graph[from][from + BOARD_SIZE] = graph[from + BOARD_SIZE][from] = !blocked;
                }
                if (col < LAST_COORDINATE) {
                    boolean blocked =
                            game.hasWall(row, col, GameState.Orientation.VERTICAL)
                                    || game.hasWall(row - 1, col, GameState.Orientation.VERTICAL);
                    graph[from][from + 1] = graph[from + 1][from] = !blocked;
                }
            }
        }
        return graph;
    }
}
