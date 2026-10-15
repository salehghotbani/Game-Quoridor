import Board.BotPlayer;
import Board.GameState;
import Board.GameState.Cell;
import Board.GameState.Orientation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BotPlayerTest {
    private static int checks;
    private static long slowestNanos;

    public static void main(String[] args) throws Exception {
        immediateWins();
        immediateDefense();
        usefulWalls();
        variedEasyMoves();
        legalPureActions();
        tacticalSearchComparison();
        exhaustedWalls();
        finishedAndCancelled();
        completedGames();
        System.out.printf("BotPlayer tests passed (%d checks; slowest decision %.0f ms).%n",
                checks, slowestNanos / 1_000_000.0);
    }

    private static void immediateWins() {
        for (BotPlayer.Difficulty difficulty : BotPlayer.Difficulty.values()) {
            for (int player = 0; player < 2; player++) {
                GameState game = position(player == 0 ? new Cell(1, 4) : new Cell(8, 0),
                        player == 1 ? new Cell(7, 4) : new Cell(0, 0), player);
                BotPlayer.Action action = choose(new BotPlayer(new Random(7)), game, difficulty);
                check(action.orientation() == null, "Every level takes an immediate pawn win");
                check(action.apply(game), "Winning bot move applies");
                check(game.winner() == player, "Bot chooses the winning square");
            }
        }
    }

    private static void immediateDefense() {
        for (BotPlayer.Difficulty difficulty : List.of(BotPlayer.Difficulty.MEDIUM, BotPlayer.Difficulty.HARD)) {
            for (int player = 0; player < 2; player++) {
                GameState game = position(player == 0 ? new Cell(5, 4) : new Cell(1, 4),
                        player == 1 ? new Cell(3, 4) : new Cell(7, 4), player);
                BotPlayer.Action action = choose(new BotPlayer(new Random(8)), game, difficulty);
                check(action.apply(game), "Defensive action applies");
                check(game.winner() == -1, "Defense state is unfinished");
                int opponentGoal = game.currentPlayer() == 0 ? 0 : 8;
                check(game.legalMoves().stream().noneMatch(move -> move.row() == opponentGoal),
                        "Bot prevents the opponent's immediate winning move when defense exists");
            }
        }
    }

    private static void variedEasyMoves() {
        GameState game = new GameState();
        java.util.Set<BotPlayer.Action> actions = new java.util.HashSet<>();
        BotPlayer bot = new BotPlayer(new Random(19));
        for (int trial = 0; trial < 40; trial++) {
            actions.add(choose(bot, game, BotPlayer.Difficulty.EASY));
        }
        check(actions.size() > 1, "Easy varies its opening moves");
        check(actions.stream().anyMatch(action -> action.row() == 7), "Easy favors forward progress");
    }

    private static void usefulWalls() {
        for (BotPlayer.Difficulty difficulty : List.of(BotPlayer.Difficulty.MEDIUM, BotPlayer.Difficulty.HARD)) {
            GameState game = GameState.fromSnapshot(new GameState.Snapshot(new Cell(4, 4), new Cell(0, 0),
                    9, 10, 1, -1, List.of(new GameState.Wall(3, 4, Orientation.VERTICAL))));
            int beforeBlue = distances(game, 0)[4 * 9 + 4];
            int beforeGreen = distances(game, 1)[0];
            BotPlayer.Action action = choose(new BotPlayer(new Random(3)), game, difficulty);
            check(action.orientation() != null, "A bot behind in the race chooses an effective delaying wall");
            check(action.apply(game), "Useful wall is legal");
            int blueDelay = distances(game, 0)[4 * 9 + 4] - beforeBlue;
            int greenDelay = distances(game, 1)[0] - beforeGreen;
            check(blueDelay - greenDelay > 1, "Chosen wall gains more time than the turn spent placing it");
        }
    }

    private static void legalPureActions() {
        Random random = new Random(345);
        GameState game = new GameState();
        for (int step = 0; step < 28; step++) {
            if (game.winner() != -1) {
                game.reset();
            }
            if (step % 4 == 0) {
                for (BotPlayer.Difficulty difficulty : BotPlayer.Difficulty.values()) {
                    GameState trial = game.copy();
                    BotPlayer.Action action = choose(new BotPlayer(new Random(step)), trial, difficulty);
                    check(action != null && action.apply(trial), "All levels return a legal action in varied states");
                }
            }
            boolean wall = false;
            if (random.nextBoolean()) {
                for (int attempt = 0; attempt < 24 && !wall; attempt++) {
                    wall = game.placeWall(random.nextInt(8), random.nextInt(8),
                            Orientation.values()[random.nextInt(2)]);
                }
            }
            if (!wall) {
                List<Cell> moves = new ArrayList<>(game.legalMoves());
                Cell move = moves.get(random.nextInt(moves.size()));
                check(game.moveTo(move.row(), move.col()), "Prepare varied test position");
            }
        }
    }

    private static void exhaustedWalls() {
        GameState game = new GameState();
        for (int row = 0; row < 5; row++) {
            for (int col : new int[]{0, 2, 4, 6}) {
                check(game.placeWall(row, col, Orientation.HORIZONTAL), "Prepare a game without wall stock");
            }
        }
        for (BotPlayer.Difficulty difficulty : BotPlayer.Difficulty.values()) {
            GameState trial = game.copy();
            for (int player = 0; player < 2; player++) {
                BotPlayer.Action action = choose(new BotPlayer(new Random(9)), trial, difficulty);
                check(action.orientation() == null, "Bot with no wall stock chooses a move");
                check(action.apply(trial), "No-stock bot move applies");
            }
        }
    }

    private static void tacticalSearchComparison() {
        GameState game = GameState.fromSnapshot(new GameState.Snapshot(new Cell(5, 1), new Cell(5, 0),
                9, 9, 1, -1, List.of(new GameState.Wall(5, 0, Orientation.HORIZONTAL),
                new GameState.Wall(5, 1, Orientation.VERTICAL))));
        BotPlayer.Action medium = choose(new BotPlayer(new Random(1)), game, BotPlayer.Difficulty.MEDIUM);
        BotPlayer.Action hard = choose(new BotPlayer(new Random(1)), game, BotPlayer.Difficulty.HARD);
        check(!medium.equals(hard), "Hard lookahead makes a different tactical choice from the greedy medium level");
        GameState mediumLine = game.copy();
        check(medium.apply(mediumLine), "Medium tactical move applies");
        check(mediumLine.legalMoves().contains(new Cell(3, 1)), "Greedy move gives blue a forward jump");
        GameState hardLine = game.copy();
        check(hard.apply(hardLine), "Hard tactical move applies");
        check(!hardLine.legalMoves().contains(new Cell(3, 1)), "Hard avoids immediately accelerating the opponent");
        if (Boolean.getBoolean("quoridor.test.verbose")) {
            System.out.println("Adjacent-pawn tactics: medium=" + medium + "; hard=" + hard);
        }
    }

    private static void finishedAndCancelled() throws Exception {
        GameState finished = GameState.fromSnapshot(new GameState.Snapshot(new Cell(0, 3),
                new Cell(1, 4), 10, 10, 0, 0, List.of()));
        for (BotPlayer.Difficulty difficulty : BotPlayer.Difficulty.values()) {
            check(choose(new BotPlayer(), finished, difficulty) == null, "Only a terminal game returns null");
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread search = new Thread(() -> {
            started.countDown();
            try {
                new BotPlayer().chooseAction(new GameState(), BotPlayer.Difficulty.HARD);
            } catch (CancellationException expected) {
                cancelled.set(true);
                interruptPreserved.set(Thread.currentThread().isInterrupted());
            }
        }, "bot-cancellation-test");
        search.start();
        started.await();
        search.interrupt();
        search.join(1500);
        check(!search.isAlive(), "Interrupted search stops promptly");
        check(cancelled.get(), "Interrupted search signals cancellation");
        check(interruptPreserved.get(), "Search preserves the interrupt flag");
    }

    private static void completedGames() {
        for (BotPlayer.Difficulty difficulty : BotPlayer.Difficulty.values()) {
            for (int round = 0; round < 2; round++) {
                GameState game = new GameState();
                BotPlayer bot = new BotPlayer(new Random(81 + round));
                int turns = 0;
                int botWalls = 0;
                for (; turns < 220 && game.winner() == -1; turns++) {
                    if (game.currentPlayer() == 1) {
                        BotPlayer.Action action = choose(bot, game, difficulty);
                        check(action != null && action.apply(game), "Bot's full-game action remains legal");
                        if (action.orientation() != null) {
                            botWalls++;
                        }
                    } else {
                        int[] distances = distances(game, 0);
                        Cell move = game.legalMoves().stream()
                                .min(Comparator.comparingInt((Cell cell) -> distances[cell.row() * 9 + cell.col()])
                                        .thenComparingInt(Cell::row).thenComparingInt(Cell::col)).orElseThrow();
                        check(game.moveTo(move.row(), move.col()), "Deterministic opponent advances");
                    }
                }
                check(game.winner() != -1, "Game terminates against a deterministic shortest-path mover: " + difficulty);
                if (Boolean.getBoolean("quoridor.test.verbose")) {
                    System.out.println(difficulty + " round " + round + ": winner=" + game.winner()
                            + ", turns=" + turns + ", bot walls=" + botWalls);
                }
            }
        }
    }

    private static BotPlayer.Action choose(BotPlayer bot, GameState game, BotPlayer.Difficulty difficulty) {
        GameState.Snapshot before = game.snapshot();
        long started = System.nanoTime();
        BotPlayer.Action action = bot.chooseAction(game, difficulty);
        long elapsed = System.nanoTime() - started;
        slowestNanos = Math.max(slowestNanos, elapsed);
        check(elapsed < 1_500_000_000L, "Bot decision stays below 1.5 seconds: " + difficulty);
        check(game.snapshot().equals(before), "Bot search leaves its input unchanged");
        return action;
    }

    private static GameState position(Cell blue, Cell green, int turn) {
        return GameState.fromSnapshot(new GameState.Snapshot(blue, green, 10, 10, turn, -1, List.of()));
    }

    private static int[] distances(GameState game, int player) {
        int[] distances = new int[81];
        Arrays.fill(distances, 100);
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        int goal = player == 0 ? 0 : 8;
        for (int col = 0; col < 9; col++) {
            distances[goal * 9 + col] = 0;
            pending.add(new Cell(goal, col));
        }
        while (!pending.isEmpty()) {
            Cell cell = pending.remove();
            for (int[] direction : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int row = cell.row() + direction[0];
                int col = cell.col() + direction[1];
                if (row < 0 || row > 8 || col < 0 || col > 8) {
                    continue;
                }
                boolean blocked = row != cell.row()
                        ? game.hasWall(Math.min(row, cell.row()), col, Orientation.HORIZONTAL)
                        || game.hasWall(Math.min(row, cell.row()), col - 1, Orientation.HORIZONTAL)
                        : game.hasWall(row, Math.min(col, cell.col()), Orientation.VERTICAL)
                        || game.hasWall(row - 1, Math.min(col, cell.col()), Orientation.VERTICAL);
                if (!blocked && distances[row * 9 + col] > distances[cell.row() * 9 + cell.col()] + 1) {
                    distances[row * 9 + col] = distances[cell.row() * 9 + cell.col()] + 1;
                    pending.add(new Cell(row, col));
                }
            }
        }
        return distances;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
