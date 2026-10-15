import Board.GameState;
import Board.GameState.Cell;
import Board.GameState.Orientation;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Run with java -ea -cp build/classes GameStateTest; no test libraries are needed. */
public final class GameStateTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        initialStateAndInvalidActions();
        allPawnPositions();
        jumpsAndDiagonalWalls();
        wallGeometryAndMovement();
        pathTrapsAndNinthGoal();
        exhaustedWalls();
        completeGamesAndReset();
        randomizedGames();
        snapshotsAndCopies();
        System.out.println("GameState tests passed (" + checks + " checks).");
    }

    private static void initialStateAndInvalidActions() {
        GameState game = new GameState();
        equal(game.currentPlayer(), 0, "Blue starts");
        equal(game.playerPosition(0), new Cell(8, 4), "Blue starts bottom center");
        equal(game.playerPosition(1), new Cell(0, 4), "Green starts top center");
        equal(game.wallsRemaining(0), 10, "Blue wall stock");
        equal(game.wallsRemaining(1), 10, "Green wall stock");
        equal(game.winner(), -1, "No initial winner");
        equal(game.legalMoves(), Set.of(new Cell(7, 4), new Cell(8, 3), new Cell(8, 5)),
                "Opening moves");
        String initial = snapshot(game);
        for (Cell invalid : List.of(new Cell(-1, 4), new Cell(9, 4), new Cell(8, -1),
                new Cell(8, 9), new Cell(8, 4), new Cell(0, 4), new Cell(6, 4))) {
            check(!game.moveTo(invalid.row(), invalid.col()), "Invalid move rejected: " + invalid);
            equal(snapshot(game), initial, "Invalid move is atomic");
        }
        check(game.canPlaceWall(4, 4, Orientation.HORIZONTAL), "Opening wall is legal");
        equal(snapshot(game), initial, "Wall preview is pure");
        for (int[] anchor : new int[][]{{-1, 0}, {0, -1}, {8, 0}, {0, 8}}) {
            check(!game.placeWall(anchor[0], anchor[1], Orientation.HORIZONTAL), "Invalid anchor rejected");
            check(!game.hasWall(anchor[0], anchor[1], Orientation.HORIZONTAL), "Invalid anchor lookup");
        }
        check(!game.canPlaceWall(0, 0, null), "Null orientation rejected");
        check(!game.placeWall(0, 0, null), "Null wall placement rejected");
        check(!game.hasWall(0, 0, null), "Null wall lookup rejected");
        equal(snapshot(game), initial, "Invalid walls are atomic");
        boolean immutable = false;
        try {
            game.legalMoves().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "Legal moves cannot mutate the model");
        for (int player : new int[]{-1, 2}) {
            boolean guarded = false;
            try {
                game.playerPosition(player);
            } catch (IllegalArgumentException expected) {
                guarded = true;
            }
            check(guarded, "Invalid player lookup is guarded");
        }
    }

    private static void allPawnPositions() throws Exception {
        GameState game = new GameState();
        boolean[][] graph = graph(game);
        // Every distinct pawn pair, each player's turn, includes all corners and edge jumps.
        for (int blue = 0; blue < 81; blue++) {
            for (int green = 0; green < 81; green++) {
                if (blue == green) {
                    continue;
                }
                for (int turn = 0; turn < 2; turn++) {
                    setPawns(game, cell(blue), cell(green), turn);
                    equal(game.legalMoves(), oracleMoves(game, graph),
                            "Pawn pair " + blue + "/" + green + ", turn " + turn);
                }
            }
        }
    }

    private static void jumpsAndDiagonalWalls() throws Exception {
        GameState game = new GameState();
        setPawns(game, new Cell(4, 4), new Cell(3, 4), 0);
        check(game.legalMoves().contains(new Cell(2, 4)), "Straight jump over opponent");
        check(!game.legalMoves().contains(new Cell(3, 3)), "No diagonal when straight jump is open");
        check(!game.legalMoves().contains(new Cell(3, 5)), "No opposite diagonal when straight jump is open");
        check(!game.legalMoves().contains(new Cell(3, 4)), "Cannot occupy opponent's cell");

        check(game.placeWall(2, 4, Orientation.HORIZONTAL), "Wall behind opponent");
        setPawns(game, new Cell(4, 4), new Cell(3, 4), 0);
        check(!game.legalMoves().contains(new Cell(2, 4)), "Wall stops straight jump");
        check(game.legalMoves().containsAll(Set.of(new Cell(3, 3), new Cell(3, 5))),
                "Both side steps allowed behind blocked opponent");
        check(game.placeWall(3, 3, Orientation.VERTICAL), "Wall blocking one side step");
        setPawns(game, new Cell(4, 4), new Cell(3, 4), 0);
        check(!game.legalMoves().contains(new Cell(3, 3)), "Side step cannot cross a wall");
        check(game.legalMoves().contains(new Cell(3, 5)), "Other side step remains available");

        game.reset();
        setPawns(game, new Cell(1, 0), new Cell(0, 0), 0);
        equal(game.legalMoves(), Set.of(new Cell(0, 1), new Cell(1, 1), new Cell(2, 0)),
                "Corner opponent allows only the in-board diagonal");
        game.reset();
        check(game.placeWall(3, 4, Orientation.HORIZONTAL), "Wall between adjacent pawns");
        setPawns(game, new Cell(4, 4), new Cell(3, 4), 0);
        check(!game.legalMoves().contains(new Cell(2, 4)), "Cannot jump through wall before opponent");
        check(!game.legalMoves().contains(new Cell(3, 3)), "Cannot side step through wall before opponent");
        equal(game.legalMoves(), oracleMoves(game, graph(game)), "Blocked adjacency matches graph");
    }

    private static void wallGeometryAndMovement() throws Exception {
        for (Orientation orientation : Orientation.values()) {
            GameState game = new GameState();
            check(game.placeWall(3, 3, orientation), "Place first wall");
            equal(game.currentPlayer(), 1, "Wall placement changes turn");
            equal(game.wallsRemaining(0), 9, "Wall placement consumes one wall");
            equal(game.wallsRemaining(1), 10, "Opponent's stock remains intact");
            check(game.hasWall(3, 3, orientation), "Placed wall is queryable");
            String placed = snapshot(game);
            check(!game.placeWall(3, 3, orientation), "Duplicate wall rejected");
            Orientation cross = orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            check(!game.placeWall(3, 3, cross), "Crossing wall rejected");
            for (int offset : new int[]{-1, 1}) {
                int row = 3 + (orientation == Orientation.VERTICAL ? offset : 0);
                int col = 3 + (orientation == Orientation.HORIZONTAL ? offset : 0);
                check(!game.placeWall(row, col, orientation), "One-segment overlap rejected");
            }
            equal(snapshot(game), placed, "Rejected geometry leaves all state intact");
            int endRow = orientation == Orientation.VERTICAL ? 5 : 3;
            int endCol = orientation == Orientation.HORIZONTAL ? 5 : 3;
            check(game.canPlaceWall(endRow, endCol, orientation), "End-to-end walls allowed");
            for (int offset = 0; offset < 2; offset++) {
                Cell pawn = orientation == Orientation.HORIZONTAL
                        ? new Cell(4, 3 + offset) : new Cell(3 + offset, 4);
                Cell across = orientation == Orientation.HORIZONTAL
                        ? new Cell(3, 3 + offset) : new Cell(3 + offset, 3);
                setPawns(game, pawn, new Cell(0, 0), 0);
                check(!game.legalMoves().contains(across), "Wall blocks each of its two segments");
                equal(game.legalMoves(), oracleMoves(game, graph(game)), "Wall movement matches graph");
            }
        }
        for (int row : new int[]{0, 7}) {
            for (int col : new int[]{0, 7}) {
                for (Orientation orientation : Orientation.values()) {
                    check(new GameState().placeWall(row, col, orientation), "Wall anchor at board corner");
                }
            }
        }
    }

    private static void pathTrapsAndNinthGoal() {
        for (int player = 0; player < 2; player++) {
            GameState game = new GameState();
            int row = player == 0 ? 7 : 0;
            check(game.placeWall(row, 4, Orientation.HORIZONTAL), "Place top/bottom of trap");
            check(game.placeWall(row, 3, Orientation.VERTICAL), "Place one side of trap");
            String beforeTrap = snapshot(game);
            check(!game.canPlaceWall(row, 5, Orientation.VERTICAL), "Preview rejects trapping player " + player);
            check(!game.placeWall(row, 5, Orientation.VERTICAL), "Placement rejects trapping player " + player);
            equal(snapshot(game), beforeTrap, "Trap rejection rolls back all state for player " + player);
            check(reachableGoals(graph(game), game.playerPosition(player), player).size() > 0,
                    "Rejected trap leaves a route");
        }
        for (int player = 0; player < 2; player++) {
            GameState game = new GameState();
            int goalBoundary = player == 0 ? 0 : 7;
            check(game.moveTo(7, 4), "Move blue outside the isolated goal cells");
            check(game.moveTo(1, 4), "Move green outside the isolated goal cells");
            check(game.placeWall(goalBoundary, 7, Orientation.VERTICAL), "Separate ninth goal from the first eight");
            for (int col : new int[]{0, 2, 4, 6}) {
                check(game.canPlaceWall(goalBoundary, col, Orientation.HORIZONTAL),
                        "A route via the ninth goal cell is sufficient for player " + player);
                check(game.placeWall(goalBoundary, col, Orientation.HORIZONTAL), "Build goal-row barrier");
            }
            equal(reachableGoals(graph(game), game.playerPosition(player), player), Set.of(8),
                    "Only the ninth goal cell is reachable for player " + player);
        }
    }

    private static void exhaustedWalls() {
        GameState game = new GameState();
        for (int row = 0; row < 5; row++) {
            for (int col : new int[]{0, 2, 4, 6}) {
                check(game.placeWall(row, col, Orientation.HORIZONTAL), "Spend all twenty walls");
            }
        }
        equal(game.wallsRemaining(0), 0, "Blue exhausts stock");
        equal(game.wallsRemaining(1), 0, "Green exhausts stock");
        for (int player = 0; player < 2; player++) {
            equal(game.currentPlayer(), player, "Check exhausted active player");
            String before = snapshot(game);
            check(!game.canPlaceWall(6, 0, Orientation.VERTICAL), "Zero-stock preview rejected");
            check(!game.placeWall(6, 0, Orientation.VERTICAL), "Zero-stock placement rejected");
            equal(snapshot(game), before, "Zero-stock action cannot corrupt the board");
            Cell move = game.legalMoves().iterator().next();
            check(game.moveTo(move.row(), move.col()), "Movement remains available without walls");
        }
    }

    private static void completeGamesAndReset() {
        GameState game = new GameState();
        for (int row = 7; row >= 0; row--) {
            check(game.moveTo(row, 4), "Blue advances to goal");
            if (row > 0) {
                check(game.moveTo(0, row % 2 == 1 ? 3 : 4), "Green waits by moving sideways");
            }
        }
        assertFinished(game, 0);
        game.reset();
        equal(snapshot(game), snapshot(new GameState()), "Reset clears a completed game");
        for (int row = 1; row <= 8; row++) {
            check(game.moveTo(8, row % 2 == 1 ? 3 : 2), "Blue waits by moving sideways");
            check(game.moveTo(row, 4), "Green advances to goal");
        }
        assertFinished(game, 1);
        game.reset();
        check(game.placeWall(7, 4, Orientation.HORIZONTAL), "Place wall before reset");
        check(game.moveTo(1, 4), "Move pawn before reset");
        game.reset();
        equal(snapshot(game), snapshot(new GameState()), "Reset clears walls, stock, positions, and turn");
    }

    private static void assertFinished(GameState game, int winner) {
        equal(game.winner(), winner, "Correct winner");
        equal(game.currentPlayer(), winner, "Winner keeps the turn");
        check(game.legalMoves().isEmpty(), "Finished game has no legal moves");
        String finished = snapshot(game);
        check(!game.moveTo(4, 4), "Finished game rejects moves");
        check(!game.canPlaceWall(4, 4, Orientation.HORIZONTAL), "Finished game rejects wall previews");
        check(!game.placeWall(4, 4, Orientation.HORIZONTAL), "Finished game rejects walls");
        equal(snapshot(game), finished, "Actions after victory cannot change state");
    }

    private static void randomizedGames() {
        Random random = new Random(0x514F5249444F52L);
        for (int round = 0; round < 12; round++) {
            GameState game = new GameState();
            for (int turn = 0; turn < 160 && game.winner() == -1; turn++) {
                boolean[][] graph = graph(game);
                equal(game.legalMoves(), oracleMoves(game, graph), "Random-game moves match graph oracle");
                check(!reachableGoals(graph, game.playerPosition(0), 0).isEmpty(), "Blue always has a goal path");
                check(!reachableGoals(graph, game.playerPosition(1), 1).isEmpty(), "Green always has a goal path");
                if (turn % 10 == 0) {
                    String before = snapshot(game);
                    for (int row = 0; row < 8; row++) {
                        for (int col = 0; col < 8; col++) {
                            for (Orientation orientation : Orientation.values()) {
                                equal(game.canPlaceWall(row, col, orientation),
                                        oracleWall(game, graph, row, col, orientation),
                                        "Random-game wall preview matches graph oracle");
                            }
                        }
                    }
                    equal(snapshot(game), before, "All random-game previews are pure");
                }
                int active = game.currentPlayer();
                int stock = game.wallsRemaining(active);
                boolean wallPlaced = false;
                if (random.nextInt(3) == 0) {
                    for (int attempt = 0; attempt < 24 && !wallPlaced; attempt++) {
                        int row = random.nextInt(8);
                        int col = random.nextInt(8);
                        Orientation orientation = Orientation.values()[random.nextInt(2)];
                        String before = snapshot(game);
                        wallPlaced = game.placeWall(row, col, orientation);
                        if (!wallPlaced) {
                            equal(snapshot(game), before, "Random invalid placement is atomic");
                        }
                    }
                }
                if (wallPlaced) {
                    equal(game.wallsRemaining(active), stock - 1, "Only a placed wall consumes stock");
                } else {
                    List<Cell> moves = new ArrayList<>(game.legalMoves());
                    check(!moves.isEmpty(), "Playable state always permits a pawn move");
                    Cell move = moves.get(random.nextInt(moves.size()));
                    check(game.moveTo(move.row(), move.col()), "Random legal move succeeds");
                    equal(game.wallsRemaining(active), stock, "Movement consumes no wall stock");
                }
                equal(game.currentPlayer(), game.winner() == -1 ? 1 - active : active,
                        "Exactly one turn advances per successful action");
                check(!game.playerPosition(0).equals(game.playerPosition(1)), "Pawns never overlap");
            }
        }
    }

    private static void snapshotsAndCopies() {
        GameState game = new GameState();
        check(game.moveTo(7, 4), "Prepare saved pawn position");
        check(game.placeWall(2, 2, Orientation.HORIZONTAL), "Prepare saved wall");
        GameState.Snapshot saved = game.snapshot();
        GameState loaded = GameState.fromSnapshot(saved);
        equal(loaded.snapshot(), saved, "Snapshot round trip preserves complete state");
        equal(loaded.legalMoves(), game.legalMoves(), "Loaded game preserves legal moves");
        Cell move = loaded.legalMoves().iterator().next();
        check(loaded.moveTo(move.row(), move.col()), "Loaded game remains playable");
        equal(game.snapshot(), saved, "Loaded game is independent from source");
        GameState copy = game.copy();
        equal(copy.snapshot(), saved, "Copy preserves complete state");
        copy.reset();
        equal(game.snapshot(), saved, "Resetting a copy cannot affect source");
        boolean immutable = false;
        try {
            saved.walls().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "Snapshot wall list is immutable");
        List<GameState.Wall> walls = new ArrayList<>();
        GameState.Snapshot defensive = state(new Cell(8, 4), new Cell(0, 4), 10, 10, 0, -1, walls);
        walls.add(new GameState.Wall(3, 3, Orientation.HORIZONTAL));
        check(defensive.walls().isEmpty(), "Snapshot takes a defensive copy of its list");

        reject(null, "Null snapshot");
        reject(state(null, new Cell(0, 4), 10, 10, 0, -1, List.of()), "Null pawn");
        reject(state(new Cell(9, 4), new Cell(0, 4), 10, 10, 0, -1, List.of()), "Out-of-board pawn");
        reject(state(new Cell(8, 4), new Cell(8, 4), 10, 10, 0, -1, List.of()), "Overlapping pawns");
        reject(state(new Cell(8, 4), new Cell(0, 4), 11, 9, 0, -1, List.of()), "Excess stock");
        reject(state(new Cell(8, 4), new Cell(0, 4), -1, 10, 0, -1, List.of()), "Negative stock");
        reject(state(new Cell(8, 4), new Cell(0, 4), 9, 10, 0, -1, List.of()), "Stock disagrees with wall count");
        reject(state(new Cell(8, 4), new Cell(0, 4), 10, 10, 2, -1, List.of()), "Invalid turn");
        reject(state(new Cell(8, 4), new Cell(0, 4), 10, 10, 0, 2, List.of()), "Invalid winner");
        reject(state(new Cell(0, 3), new Cell(0, 4), 10, 10, 0, -1, List.of()), "Unrecorded winner");
        reject(state(new Cell(1, 3), new Cell(0, 4), 10, 10, 0, 0, List.of()), "Winner has not reached goal");
        reject(state(new Cell(0, 3), new Cell(0, 4), 10, 10, 1, 0, List.of()), "Winner and turn disagree");
        reject(state(new Cell(0, 3), new Cell(8, 4), 10, 10, 0, 0, List.of()), "Both pawns claim victory");
        reject(state(new Cell(8, 4), new Cell(0, 4), 10, 10, 0, -1, null), "Null wall list");
        reject(state(new Cell(8, 4), new Cell(0, 4), 9, 10, 0, -1,
                java.util.Arrays.asList((GameState.Wall) null)), "Null wall");
        for (List<GameState.Wall> invalidWalls : List.of(
                List.of(new GameState.Wall(3, 3, Orientation.HORIZONTAL), new GameState.Wall(3, 3, Orientation.HORIZONTAL)),
                List.of(new GameState.Wall(3, 3, Orientation.HORIZONTAL), new GameState.Wall(3, 4, Orientation.HORIZONTAL)),
                List.of(new GameState.Wall(3, 3, Orientation.VERTICAL), new GameState.Wall(4, 3, Orientation.VERTICAL)),
                List.of(new GameState.Wall(3, 3, Orientation.HORIZONTAL), new GameState.Wall(3, 3, Orientation.VERTICAL)),
                List.of(new GameState.Wall(8, 0, Orientation.HORIZONTAL)),
                List.of(new GameState.Wall(0, 0, null)))) {
            reject(state(new Cell(8, 4), new Cell(0, 4), 10 - invalidWalls.size(), 10, 0, -1,
                    invalidWalls), "Invalid saved wall geometry");
        }
        reject(state(new Cell(8, 4), new Cell(0, 4), 8, 9, 0, -1, List.of(
                new GameState.Wall(7, 4, Orientation.HORIZONTAL),
                new GameState.Wall(7, 3, Orientation.VERTICAL),
                new GameState.Wall(7, 5, Orientation.VERTICAL))), "Saved state traps blue");
        reject(state(new Cell(8, 4), new Cell(0, 4), 8, 9, 0, -1, List.of(
                new GameState.Wall(0, 4, Orientation.HORIZONTAL),
                new GameState.Wall(0, 3, Orientation.VERTICAL),
                new GameState.Wall(0, 5, Orientation.VERTICAL))), "Saved state traps green");
        for (int winner = 0; winner < 2; winner++) {
            GameState.Snapshot finished = state(winner == 0 ? new Cell(0, 3) : new Cell(7, 3),
                    winner == 1 ? new Cell(8, 4) : new Cell(1, 4), 10, 10, winner, winner, List.of());
            equal(GameState.fromSnapshot(finished).snapshot(), finished, "Finished game round trip");
        }
        equal(game.snapshot(), saved, "Rejected saved states cannot affect a live game");
    }

    private static GameState.Snapshot state(Cell blue, Cell green, int blueWalls, int greenWalls,
                                            int currentPlayer, int winner, List<GameState.Wall> walls) {
        return new GameState.Snapshot(blue, green, blueWalls, greenWalls, currentPlayer, winner, walls);
    }

    private static void reject(GameState.Snapshot snapshot, String description) {
        boolean rejected = false;
        try {
            GameState.fromSnapshot(snapshot);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, description + " is rejected");
    }

    // The oracle reconstructs explicit board edges from the public wall state and does not
    // reuse the model's movement, overlap, or path algorithms.
    private static boolean[][] graph(GameState game) {
        boolean[][] graph = new boolean[81][81];
        for (int a = 0; a < 81; a++) {
            for (int b = a + 1; b < 81; b++) {
                if (Math.abs(a / 9 - b / 9) + Math.abs(a % 9 - b % 9) == 1) {
                    graph[a][b] = graph[b][a] = true;
                }
            }
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                for (Orientation orientation : Orientation.values()) {
                    if (game.hasWall(row, col, orientation)) {
                        removeWallEdges(graph, row, col, orientation);
                    }
                }
            }
        }
        return graph;
    }

    private static void removeWallEdges(boolean[][] graph, int row, int col, Orientation orientation) {
        for (int offset = 0; offset < 2; offset++) {
            int from = orientation == Orientation.HORIZONTAL ? row * 9 + col + offset : (row + offset) * 9 + col;
            int to = from + (orientation == Orientation.HORIZONTAL ? 9 : 1);
            graph[from][to] = graph[to][from] = false;
        }
    }

    private static Set<Cell> oracleMoves(GameState game, boolean[][] graph) {
        if (game.winner() != -1) {
            return Set.of();
        }
        Cell current = game.playerPosition(game.currentPlayer());
        Cell opponent = game.playerPosition(1 - game.currentPlayer());
        int start = index(current);
        int occupied = index(opponent);
        Set<Cell> moves = new HashSet<>();
        for (int destination = 0; destination < 81; destination++) {
            if (!graph[start][destination]) {
                continue;
            }
            if (destination != occupied) {
                moves.add(cell(destination));
                continue;
            }
            int straightRow = 2 * opponent.row() - current.row();
            int straightCol = 2 * opponent.col() - current.col();
            boolean straightInBoard = straightRow >= 0 && straightRow < 9 && straightCol >= 0 && straightCol < 9;
            int straight = straightRow * 9 + straightCol;
            if (straightInBoard && graph[occupied][straight]) {
                moves.add(cell(straight));
            } else {
                for (int side = 0; side < 81; side++) {
                    if (side != start && graph[occupied][side]) {
                        Cell candidate = cell(side);
                        if ((current.row() == opponent.row() && candidate.col() == opponent.col())
                                || (current.col() == opponent.col() && candidate.row() == opponent.row())) {
                            moves.add(candidate);
                        }
                    }
                }
            }
        }
        return moves;
    }

    private static boolean oracleWall(GameState game, boolean[][] graph, int row, int col, Orientation orientation) {
        if (game.winner() != -1 || game.wallsRemaining(game.currentPlayer()) == 0) {
            return false;
        }
        Orientation cross = orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        if (game.hasWall(row, col, cross)) {
            return false;
        }
        for (int offset = 0; offset < 2; offset++) {
            int from = orientation == Orientation.HORIZONTAL ? row * 9 + col + offset : (row + offset) * 9 + col;
            int to = from + (orientation == Orientation.HORIZONTAL ? 9 : 1);
            if (!graph[from][to]) {
                return false;
            }
        }
        boolean[][] candidate = new boolean[81][];
        for (int cell = 0; cell < 81; cell++) {
            candidate[cell] = graph[cell].clone();
        }
        removeWallEdges(candidate, row, col, orientation);
        return !reachableGoals(candidate, game.playerPosition(0), 0).isEmpty()
                && !reachableGoals(candidate, game.playerPosition(1), 1).isEmpty();
    }

    private static Set<Integer> reachableGoals(boolean[][] graph, Cell start, int player) {
        boolean[] visited = new boolean[81];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(index(start));
        visited[index(start)] = true;
        Set<Integer> goals = new HashSet<>();
        while (!pending.isEmpty()) {
            int cell = pending.remove();
            if (cell / 9 == (player == 0 ? 0 : 8)) {
                goals.add(cell % 9);
            }
            for (int next = 0; next < 81; next++) {
                if (graph[cell][next] && !visited[next]) {
                    visited[next] = true;
                    pending.add(next);
                }
            }
        }
        return goals;
    }

    private static String snapshot(GameState game) {
        StringBuilder value = new StringBuilder();
        value.append(game.currentPlayer()).append('/').append(game.winner());
        for (int player = 0; player < 2; player++) {
            value.append('/').append(game.playerPosition(player)).append('/').append(game.wallsRemaining(player));
        }
        for (Orientation orientation : Orientation.values()) {
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    value.append(game.hasWall(row, col, orientation) ? '1' : '0');
                }
            }
        }
        return value.toString();
    }

    private static void setPawns(GameState game, Cell blue, Cell green, int turn) throws Exception {
        // Isolated test setup avoids exposing arbitrary position mutation in the game API.
        Field players = GameState.class.getDeclaredField("players");
        players.setAccessible(true);
        Cell[] positions = (Cell[]) players.get(game);
        positions[0] = blue;
        positions[1] = green;
        Field active = GameState.class.getDeclaredField("currentPlayer");
        active.setAccessible(true);
        active.setInt(game, turn);
    }

    private static int index(Cell cell) {
        return cell.row() * 9 + cell.col();
    }

    private static Cell cell(int index) {
        return new Cell(index / 9, index % 9);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(actual.equals(expected), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
