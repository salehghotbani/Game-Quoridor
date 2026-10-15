import Board.BotPlayer;
import Board.GameState;
import Board.GameState.Cell;
import Board.GameState.Orientation;
import Board.GameState.Snapshot;
import Board.GameState.Wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Four-player rules are tested independently of UI, networking, and the two-player robot. */
public final class FourPlayerTest {
    private static int checks;

    private FourPlayerTest() {}

    public static void main(String[] arguments) {
        openingAndTurns();
        multiplePawnJumps();
        goalsAndVictories();
        everyPlayerNeedsARoute();
        inventoriesAndReset();
        snapshotValidation();
        completeMatch();
        randomizedMatches();
        System.out.println("Four-player tests passed (" + checks + " checks).");
    }

    private static void openingAndTurns() {
        GameState game = new GameState(4);
        equal(game.playerCount(), 4, "Four participants");
        equal(game.startingWalls(), 5, "Five walls each");
        equal(
                game.snapshot().players(),
                List.of(new Cell(8, 4), new Cell(0, 4), new Cell(4, 0), new Cell(4, 8)),
                "Opposite edge starting positions");
        equal(new GameState().startingWalls(), 10, "Default two-player stock is unchanged");
        for (int player = 0; player < 4; player++) {
            equal(game.wallsRemaining(player), 5, "Individual opening stock");
        }
        for (int unsupported : new int[] {-1, 0, 1, 3, 5}) {
            rejects(() -> new GameState(unsupported), "Unsupported participant count");
        }
        int[][] moves = {{7, 4}, {1, 4}, {4, 1}, {4, 7}};
        for (int player : new int[] {0, 2, 1, 3}) {
            equal(game.currentPlayer(), player, "Turn follows the board clockwise");
            Snapshot before = game.snapshot();
            check(!game.moveTo(-1, -1), "Invalid move rejected");
            equal(game.snapshot(), before, "Invalid move does not advance turn");
            check(
                    game.moveTo(moves[player][0], moves[player][1]),
                    "Each participant can move inward");
        }
        equal(game.currentPlayer(), 0, "Turn wraps to blue");
        rejects(() -> game.playerPosition(4), "Player index is bounded");
        rejects(() -> game.isGoal(-1, new Cell(0, 0)), "Goal player index is bounded");
        check(!game.isGoal(0, new Cell(0, 9)), "Outside board is not a goal");
        check(!game.isGoal(0, null), "Missing cell is not a goal");
        rejects(
                () -> new BotPlayer().chooseAction(game, BotPlayer.Difficulty.HARD),
                "Four-player robot search is explicitly unsupported");
    }

    private static void multiplePawnJumps() {
        GameState game =
                position(
                        List.of(new Cell(4, 4), new Cell(3, 4), new Cell(2, 4), new Cell(3, 3)),
                        0,
                        List.of());
        equal(
                game.legalMoves(),
                Set.of(new Cell(3, 5), new Cell(5, 4), new Cell(4, 3), new Cell(4, 5)),
                "Occupied landing permits only free side step over the adjacent pawn");
        Snapshot before = game.snapshot();
        check(!game.moveTo(2, 4), "Cannot land on a second pawn");
        check(!game.moveTo(1, 4), "Cannot jump over two pawns");
        check(!game.moveTo(3, 3), "Cannot side step onto another pawn");
        equal(game.snapshot(), before, "Rejected crowded jumps are atomic");
        check(game.moveTo(3, 5), "Unoccupied side step is legal");
        equal(game.currentPlayer(), 2, "Diagonal movement advances clockwise once");

        game =
                position(
                        List.of(new Cell(4, 4), new Cell(3, 4), new Cell(4, 2), new Cell(6, 4)),
                        0,
                        List.of());
        check(game.legalMoves().contains(new Cell(2, 4)), "Straight jump over one adjacent pawn");
        check(
                !game.legalMoves().contains(new Cell(3, 3))
                        && !game.legalMoves().contains(new Cell(3, 5)),
                "Free straight landing prohibits side steps");

        game =
                position(
                        List.of(new Cell(4, 4), new Cell(3, 4), new Cell(3, 3), new Cell(3, 5)),
                        0,
                        List.of(new Wall(2, 4, Orientation.HORIZONTAL)));
        equal(
                game.legalMoves(),
                Set.of(new Cell(5, 4), new Cell(4, 3), new Cell(4, 5)),
                "Occupied side steps are rejected when a wall blocks the straight jump");

        game =
                position(
                        List.of(new Cell(4, 4), new Cell(3, 4), new Cell(4, 3), new Cell(4, 5)),
                        0,
                        List.of());
        equal(
                game.legalMoves(),
                Set.of(new Cell(2, 4), new Cell(5, 4), new Cell(4, 2), new Cell(4, 6)),
                "Each of three adjacent opponents can be jumped separately");

        game =
                position(
                        List.of(new Cell(4, 7), new Cell(4, 8), new Cell(4, 6), new Cell(3, 8)),
                        2,
                        List.of());
        check(
                game.legalMoves().containsAll(Set.of(new Cell(3, 7), new Cell(5, 7))),
                "Horizontal crowded jump offers perpendicular side steps");
        check(
                !game.legalMoves().contains(new Cell(4, 8)),
                "Horizontal jump cannot land on a second pawn");

        game =
                position(
                        List.of(new Cell(1, 4), new Cell(0, 4), new Cell(4, 0), new Cell(4, 8)),
                        0,
                        List.of());
        check(
                game.legalMoves().containsAll(Set.of(new Cell(0, 3), new Cell(0, 5))),
                "Board edge permits both free diagonals");
        check(game.moveTo(0, 3), "Edge diagonal may win");
        equal(game.winner(), 0, "Diagonal victory recognized");
    }

    private static void goalsAndVictories() {
        for (int player = 0; player < 4; player++) {
            for (int coordinate = 0; coordinate < GameState.SIZE; coordinate++) {
                List<Cell> cells =
                        new ArrayList<>(
                                List.of(
                                        new Cell(3, 3),
                                        new Cell(3, 4),
                                        new Cell(4, 3),
                                        new Cell(4, 4)));
                Cell near =
                        switch (player) {
                            case 0 -> new Cell(1, coordinate);
                            case 1 -> new Cell(7, coordinate);
                            case 2 -> new Cell(coordinate, 7);
                            default -> new Cell(coordinate, 1);
                        };
                Cell goal =
                        switch (player) {
                            case 0 -> new Cell(0, coordinate);
                            case 1 -> new Cell(8, coordinate);
                            case 2 -> new Cell(coordinate, 8);
                            default -> new Cell(coordinate, 0);
                        };
                cells.set(player, near);
                GameState game = position(cells, player, List.of());
                check(game.isGoal(player, goal), "Every square of each goal edge counts");
                check(game.moveTo(goal.row(), goal.col()), "Goal edge is reachable");
                equal(game.winner(), player, "Correct participant wins");
                equal(game.currentPlayer(), player, "Winner retains the turn");
                Snapshot finished = game.snapshot();
                check(game.legalMoves().isEmpty(), "Victory freezes moves");
                check(
                        !game.moveTo(4, 4) && !game.placeWall(2, 2, Orientation.HORIZONTAL),
                        "Victory freezes all actions");
                equal(game.snapshot(), finished, "Terminal actions cannot mutate state");
                equal(
                        GameState.fromSnapshot(finished).snapshot(),
                        finished,
                        "Four-player finished state restores");
            }
        }
    }

    private static void everyPlayerNeedsARoute() {
        GameState redTrap =
                position(
                        List.of(new Cell(8, 4), new Cell(0, 4), new Cell(0, 0), new Cell(4, 8)),
                        0,
                        List.of(new Wall(0, 0, Orientation.HORIZONTAL)));
        rejectWall(
                redTrap,
                0,
                1,
                Orientation.VERTICAL,
                "Wall cannot trap red even when blue and green retain paths");
        GameState goldTrap =
                position(
                        List.of(new Cell(8, 4), new Cell(0, 4), new Cell(4, 0), new Cell(0, 8)),
                        1,
                        List.of(new Wall(0, 7, Orientation.HORIZONTAL)));
        rejectWall(
                goldTrap,
                0,
                6,
                Orientation.VERTICAL,
                "Wall cannot trap gold even when all other routes survive");

        List<Wall> redWalls =
                List.of(
                        new Wall(0, 0, Orientation.HORIZONTAL),
                        new Wall(0, 1, Orientation.VERTICAL));
        rejects(
                () ->
                        position(
                                List.of(
                                        new Cell(8, 4),
                                        new Cell(0, 4),
                                        new Cell(0, 0),
                                        new Cell(4, 8)),
                                0,
                                redWalls),
                "Loading a red-only trap is rejected");
        List<Wall> goldWalls =
                List.of(
                        new Wall(0, 7, Orientation.HORIZONTAL),
                        new Wall(0, 6, Orientation.VERTICAL));
        rejects(
                () ->
                        position(
                                List.of(
                                        new Cell(8, 4),
                                        new Cell(0, 4),
                                        new Cell(4, 0),
                                        new Cell(0, 8)),
                                0,
                                goldWalls),
                "Loading a gold-only trap is rejected");

        List<Wall> barrier =
                List.of(
                        new Wall(0, 7, Orientation.VERTICAL),
                        new Wall(2, 7, Orientation.VERTICAL),
                        new Wall(4, 7, Orientation.VERTICAL),
                        new Wall(6, 7, Orientation.VERTICAL));
        GameState ninthGoal = position(new GameState(4).snapshot().players(), 0, barrier);
        check(
                ninthGoal.canPlaceWall(3, 3, Orientation.HORIZONTAL),
                "Red route through ninth goal square is retained");
        equal(
                GameState.fromSnapshot(ninthGoal.snapshot()).snapshot(),
                ninthGoal.snapshot(),
                "A path to only the last goal square is valid");
    }

    private static void rejectWall(
            GameState game, int row, int col, Orientation orientation, String message) {
        Snapshot before = game.snapshot();
        check(!game.canPlaceWall(row, col, orientation), message);
        check(!game.placeWall(row, col, orientation), message + " when committed");
        equal(
                game.snapshot(),
                before,
                "Rejected wall leaves all four stocks and the turn unchanged");
    }

    private static void inventoriesAndReset() {
        GameState game = new GameState(4);
        int placed = 0;
        outer:
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col += 2) {
                int player = game.currentPlayer();
                int before = game.wallsRemaining(player);
                check(
                        game.placeWall(row, col, Orientation.HORIZONTAL),
                        "Horizontal barrier leaves a side corridor");
                equal(
                        game.wallsRemaining(player),
                        before - 1,
                        "Only active participant spends a wall");
                if (++placed == 20) {
                    break outer;
                }
            }
        }
        for (int player = 0; player < 4; player++) {
            equal(game.wallsRemaining(player), 0, "Each participant exhausts exactly five walls");
        }
        rejectWall(game, 6, 0, Orientation.HORIZONTAL, "Empty stock forbids another wall");
        GameState copy = game.copy();
        equal(copy.playerCount(), 4, "Copy retains participant count");
        Snapshot beforeReset = copy.snapshot();
        game.reset();
        equal(
                game.snapshot(),
                new GameState(4).snapshot(),
                "Reset restores all four pawns, stocks and first turn");
        equal(copy.snapshot(), beforeReset, "Reset cannot mutate another owner's copy");
    }

    private static void snapshotValidation() {
        Snapshot valid = new GameState(4).snapshot();
        List<Cell> mutableCells = new ArrayList<>(valid.players());
        List<Integer> mutableStocks = new ArrayList<>(valid.wallsRemaining());
        Snapshot copied = new Snapshot(mutableCells, mutableStocks, 0, -1, new ArrayList<>());
        mutableCells.clear();
        mutableStocks.clear();
        equal(copied, valid, "Snapshot takes defensive copies of every list");
        rejectsMutation(() -> copied.players().clear(), "Snapshot pawn list is immutable");
        rejectsMutation(() -> copied.wallsRemaining().clear(), "Snapshot stock list is immutable");
        equal(copied.blue(), valid.blue(), "Old blue alias remains available");
        equal(copied.greenWalls(), 5, "Old green stock alias remains available");

        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players().subList(0, 3),
                                        List.of(5, 5, 5),
                                        0,
                                        -1,
                                        List.of())),
                "Three-player snapshot rejected");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(valid.players(), List.of(5, 5), 0, -1, List.of())),
                "Missing stocks rejected");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players(),
                                        Arrays.asList(5, 5, null, 5),
                                        0,
                                        -1,
                                        List.of())),
                "Null stock rejected");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players(), List.of(5, 5, 6, 4), 0, -1, List.of())),
                "Four-player stock cannot exceed five");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players(), List.of(5, 5, 5, 4), 0, -1, List.of())),
                "Total stocks and wall count agree");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players(), valid.wallsRemaining(), 4, -1, List.of())),
                "Turn cannot exceed participant count");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(
                                        valid.players(), valid.wallsRemaining(), 0, 4, List.of())),
                "Winner cannot exceed participant count");
        List<Cell> overlapped = new ArrayList<>(valid.players());
        overlapped.set(3, valid.playerPosition(2));
        rejects(() -> position(overlapped, 0, List.of()), "Any pair of pawns cannot overlap");
        List<Cell> goal = new ArrayList<>(valid.players());
        goal.set(2, new Cell(0, 8));
        rejects(() -> position(goal, 0, List.of()), "Red on goal without victory rejected");
        rejects(
                () ->
                        GameState.fromSnapshot(
                                new Snapshot(goal, valid.wallsRemaining(), 0, 2, List.of())),
                "Four-player winner must retain turn");
    }

    private static void completeMatch() {
        GameState game = new GameState(4);
        int plies = 0;
        while (game.winner() == -1 && plies++ < 100) {
            int player = game.currentPlayer();
            Cell move =
                    game.legalMoves().stream()
                            .min(Comparator.comparingInt(cell -> distance(player, cell)))
                            .orElseThrow();
            check(game.moveTo(move.row(), move.col()), "Deterministic four-player move applies");
        }
        check(game.winner() >= 0 && plies < 100, "A complete four-player race terminates");
        check(
                game.isGoal(game.winner(), game.playerPosition(game.winner())),
                "Winner reached their own edge");
    }

    private static int distance(int player, Cell cell) {
        return switch (player) {
            case 0 -> cell.row();
            case 1 -> 8 - cell.row();
            case 2 -> 8 - cell.col();
            default -> cell.col();
        };
    }

    private static void randomizedMatches() {
        Random random = new Random(0x4C0FFEE);
        for (int match = 0; match < 35; match++) {
            GameState game = new GameState(4);
            for (int ply = 0; ply < 90 && game.winner() == -1; ply++) {
                int active = game.currentPlayer();
                Snapshot before = game.snapshot();
                Set<Cell> occupied = new HashSet<>(before.players());
                check(
                        game.legalMoves().stream().noneMatch(occupied::contains),
                        "No legal landing contains another pawn");
                boolean placed = false;
                if (random.nextInt(4) == 0 && game.wallsRemaining(active) > 0) {
                    for (int attempt = 0; attempt < 4 && !placed; attempt++) {
                        placed =
                                game.placeWall(
                                        random.nextInt(8),
                                        random.nextInt(8),
                                        Orientation.values()[random.nextInt(2)]);
                    }
                }
                if (!placed) {
                    List<Cell> moves = new ArrayList<>(game.legalMoves());
                    check(!moves.isEmpty(), "Valid four-player position has a pawn action");
                    Cell move = moves.get(random.nextInt(moves.size()));
                    check(game.moveTo(move.row(), move.col()), "Random legal move applies");
                }
                equal(
                        game.currentPlayer(),
                        game.winner() == -1 ? nextPlayer(active) : active,
                        "Action advances clockwise exactly once");
                equal(
                        GameState.fromSnapshot(game.snapshot()).snapshot(),
                        game.snapshot(),
                        "Random state restores with all four goal routes");
                equal(
                        game.copy().snapshot(),
                        game.snapshot(),
                        "Random copy retains all participants");
                equal(
                        new HashSet<>(game.snapshot().players()).size(),
                        4,
                        "Pawn uniqueness survives actions");
                int total = game.snapshot().walls().size();
                for (int player = 0; player < 4; player++) {
                    int stock = game.wallsRemaining(player);
                    check(
                            stock >= 0 && stock <= 5,
                            "Every stock remains within the four-player limit");
                    total += stock;
                }
                equal(total, 20, "Twenty walls remain accounted for");
                check(!game.moveTo(-1, -1), "Invalid random follow-up rejected");
            }
        }
    }

    private static GameState position(List<Cell> players, int turn, List<Wall> walls) {
        List<Integer> stocks = new ArrayList<>(List.of(5, 5, 5, 5));
        for (int index = 0; index < walls.size(); index++) {
            int player = index % 4;
            stocks.set(player, stocks.get(player) - 1);
        }
        return GameState.fromSnapshot(new Snapshot(players, stocks, turn, -1, walls));
    }

    private static int nextPlayer(int player) {
        return switch (player) {
            case 0 -> 2;
            case 2 -> 1;
            case 1 -> 3;
            default -> 0;
        };
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void rejectsMutation(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            checks++;
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
}
