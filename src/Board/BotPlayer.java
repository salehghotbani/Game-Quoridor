package Board;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Bounded, local game AI. Searches only independent copies of the supplied position. */
public final class BotPlayer {
    public enum Difficulty { EASY, MEDIUM, HARD }

    public record Action(int row, int col, GameState.Orientation orientation) {
        public boolean apply(GameState game) {
            Objects.requireNonNull(game, "game");
            return orientation == null ? game.moveTo(row, col) : game.placeWall(row, col, orientation);
        }
    }

    private record Candidate(Action action, GameState game, int score) { }

    private static final int WIN_SCORE = 1_000_000;
    private static final long SEARCH_NANOS = 650_000_000L;
    private static final Comparator<Candidate> BEST_FIRST = Comparator
            .comparingInt(Candidate::score).reversed()
            .thenComparing(candidate -> candidate.action().orientation() != null)
            .thenComparingInt(candidate -> candidate.action().row())
            .thenComparingInt(candidate -> candidate.action().col());
    private final Random random;

    public BotPlayer() {
        this(new Random());
    }

    public BotPlayer(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public Action chooseAction(GameState game, Difficulty difficulty) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(difficulty, "difficulty");
        checkCancelled();
        if (game.winner() != -1) {
            return null;
        }
        int player = game.currentPlayer();
        for (GameState.Cell move : game.legalMoves()) {
            if (move.row() == goal(player)) {
                return new Action(move.row(), move.col(), null);
            }
        }
        if (difficulty == Difficulty.EASY) {
            return easyMove(game, player);
        }
        long deadline = System.nanoTime() + SEARCH_NANOS;
        List<Candidate> candidates = candidates(game, player, deadline, true);
        Candidate best = candidates.get(0);
        if (difficulty == Difficulty.MEDIUM || best.score() >= WIN_SCORE) {
            return best.action();
        }
        // Search the strongest root choices against pruned opponent replies, then a
        // pawn response. A completed branch is committed only before the deadline.
        int bestScore = Integer.MIN_VALUE;
        int rootLimit = Math.min(8, candidates.size());
        for (int root = 0; root < rootLimit && System.nanoTime() < deadline; root++) {
            checkCancelled();
            Candidate candidate = candidates.get(root);
            List<Candidate> replies = candidates(candidate.game(), 1 - player, deadline, true);
            int worst = Integer.MAX_VALUE;
            boolean completed = true;
            for (int reply = 0; reply < Math.min(6, replies.size()); reply++) {
                if (System.nanoTime() >= deadline) {
                    completed = false;
                    break;
                }
                GameState responsePosition = replies.get(reply).game();
                int score = responsePosition.winner() != -1
                        ? evaluate(responsePosition, player) : bestPawnResponse(responsePosition, player);
                worst = Math.min(worst, score);
                if (worst < bestScore) {
                    break;
                }
            }
            if (completed && worst > bestScore) {
                bestScore = worst;
                best = candidate;
            }
        }
        return best.action();
    }

    private Action easyMove(GameState game, int player) {
        List<Action> shortest = new ArrayList<>();
        List<Action> varied = new ArrayList<>();
        int bestDistance = Integer.MAX_VALUE;
        int[] distance = distances(game, player);
        for (GameState.Cell move : game.legalMoves()) {
            int value = distance[index(move)];
            if (value < bestDistance) {
                bestDistance = value;
                shortest.clear();
            }
            if (value == bestDistance) {
                shortest.add(new Action(move.row(), move.col(), null));
            }
        }
        for (GameState.Cell move : game.legalMoves()) {
            if (distance[index(move)] <= bestDistance + 1) {
                varied.add(new Action(move.row(), move.col(), null));
            }
        }
        List<Action> options = random.nextInt(5) == 0 ? varied : shortest;
        return options.get(random.nextInt(options.size()));
    }

    private List<Candidate> candidates(GameState game, int player, long deadline, boolean includeWalls) {
        List<Candidate> choices = new ArrayList<>();
        if (game.winner() != -1) {
            return choices;
        }
        for (GameState.Cell move : game.legalMoves()) {
            checkCancelled();
            Action action = new Action(move.row(), move.col(), null);
            GameState next = game.copy();
            action.apply(next);
            choices.add(new Candidate(action, next, evaluate(next, player)));
        }
        if (includeWalls && game.wallsRemaining(game.currentPlayer()) > 0) {
            for (int row = 0; row < GameState.SIZE - 1; row++) {
                for (int col = 0; col < GameState.SIZE - 1; col++) {
                    for (GameState.Orientation orientation : GameState.Orientation.values()) {
                        checkCancelled();
                        if (System.nanoTime() >= deadline) {
                            choices.sort(BEST_FIRST);
                            return choices;
                        }
                        Action action = new Action(row, col, orientation);
                        GameState next = game.copy();
                        if (action.apply(next)) {
                            choices.add(new Candidate(action, next, evaluate(next, player)));
                        }
                    }
                }
            }
        }
        choices.sort(BEST_FIRST);
        return choices;
    }

    private int bestPawnResponse(GameState game, int player) {
        int best = Integer.MIN_VALUE;
        for (GameState.Cell move : game.legalMoves()) {
            checkCancelled();
            GameState next = game.copy();
            next.moveTo(move.row(), move.col());
            best = Math.max(best, evaluate(next, player));
        }
        return best;
    }

    private static int evaluate(GameState game, int player) {
        if (game.winner() != -1) {
            return game.winner() == player ? WIN_SCORE : -WIN_SCORE;
        }
        int next = game.currentPlayer();
        for (GameState.Cell move : game.legalMoves()) {
            if (move.row() == goal(next)) {
                return next == player ? WIN_SCORE - 100 : -WIN_SCORE + 100;
            }
        }
        int own = distances(game, player)[index(game.playerPosition(player))];
        int opponent = distances(game, 1 - player)[index(game.playerPosition(1 - player))];
        return (opponent - own) * 100
                + (game.wallsRemaining(player) - game.wallsRemaining(1 - player)) * 4
                + (next == player ? 35 : -35);
    }

    /** Reverse BFS gives every cell's distance to any goal, ignoring pawn occupancy. */
    private static int[] distances(GameState game, int player) {
        int[] distance = new int[81];
        java.util.Arrays.fill(distance, 100);
        boolean[][] graph = new boolean[81][81];
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int from = row * 9 + col;
                if (row < 8) {
                    boolean blocked = game.hasWall(row, col, GameState.Orientation.HORIZONTAL)
                            || game.hasWall(row, col - 1, GameState.Orientation.HORIZONTAL);
                    graph[from][from + 9] = graph[from + 9][from] = !blocked;
                }
                if (col < 8) {
                    boolean blocked = game.hasWall(row, col, GameState.Orientation.VERTICAL)
                            || game.hasWall(row - 1, col, GameState.Orientation.VERTICAL);
                    graph[from][from + 1] = graph[from + 1][from] = !blocked;
                }
            }
        }
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int col = 0; col < 9; col++) {
            int cell = goal(player) * 9 + col;
            distance[cell] = 0;
            pending.add(cell);
        }
        while (!pending.isEmpty()) {
            int cell = pending.remove();
            for (int next = 0; next < 81; next++) {
                if (graph[cell][next] && distance[next] > distance[cell] + 1) {
                    distance[next] = distance[cell] + 1;
                    pending.add(next);
                }
            }
        }
        return distance;
    }

    private static int goal(int player) {
        return player == 0 ? 0 : 8;
    }

    private static int index(GameState.Cell cell) {
        return cell.row() * 9 + cell.col();
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Bot search cancelled");
        }
    }
}
