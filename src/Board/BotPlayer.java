package Board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Bounded, local game AI. Searches only independent copies of the supplied position. */
public final class BotPlayer {
    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public record Action(int row, int col, GameState.Orientation orientation) {
        public boolean apply(GameState game) {
            Objects.requireNonNull(game, "game");
            return orientation == null
                    ? game.moveTo(row, col)
                    : game.placeWall(row, col, orientation);
        }
    }

    private record Candidate(Action action, GameState game, int score) {}

    private record ReplyScore(int value, boolean completed) {}

    private static final int NO_WINNER = -1;
    private static final int WIN_SCORE = 1_000_000;
    private static final int IMMEDIATE_WIN_DISCOUNT = 100;
    private static final int DISTANCE_WEIGHT = 100;
    private static final int WALL_STOCK_WEIGHT = 4;
    private static final int NEXT_TURN_BONUS = 35;
    private static final int ROOT_BRANCH_LIMIT = 8;
    private static final int REPLY_BRANCH_LIMIT = 6;
    private static final int EASY_VARIATION_FREQUENCY = 5;
    private static final int EASY_DISTANCE_ALLOWANCE = 1;
    private static final long SEARCH_NANOS = 650_000_000L;
    private static final Comparator<Candidate> BEST_FIRST =
            Comparator.comparingInt(Candidate::score)
                    .reversed()
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
        if (game.playerCount() != 2) {
            throw new IllegalArgumentException("Robot games support exactly two players");
        }
        if (game.winner() != NO_WINNER) {
            return null;
        }
        int player = game.currentPlayer();
        Action winningMove = immediateWinningMove(game);
        if (winningMove != null) {
            return winningMove;
        }
        if (difficulty == Difficulty.EASY) {
            return easyMove(game, player);
        }
        long deadline = System.nanoTime() + SEARCH_NANOS;
        List<Candidate> candidates = rankedCandidates(game, player, deadline);
        Candidate best = candidates.get(0);
        if (difficulty == Difficulty.MEDIUM || best.score() >= WIN_SCORE) {
            return best.action();
        }
        return tacticalAction(candidates, player, deadline);
    }

    private Action tacticalAction(List<Candidate> candidates, int player, long deadline) {
        // Search the strongest root choices against pruned opponent replies, then a
        // pawn response. A completed branch is committed only before the deadline.
        Candidate best = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        int rootLimit = Math.min(ROOT_BRANCH_LIMIT, candidates.size());
        for (int root = 0; root < rootLimit && System.nanoTime() < deadline; root++) {
            checkCancelled();
            Candidate candidate = candidates.get(root);
            ReplyScore reply = worstReply(candidate.game(), player, deadline, bestScore);
            if (reply.completed() && reply.value() > bestScore) {
                bestScore = reply.value();
                best = candidate;
            }
        }
        return best.action();
    }

    private ReplyScore worstReply(GameState game, int player, long deadline, int incumbentScore) {
        List<Candidate> replies = rankedCandidates(game, 1 - player, deadline);
        int worst = Integer.MAX_VALUE;
        for (int reply = 0; reply < Math.min(REPLY_BRANCH_LIMIT, replies.size()); reply++) {
            if (System.nanoTime() >= deadline) {
                return new ReplyScore(worst, false);
            }
            GameState responsePosition = replies.get(reply).game();
            int score =
                    responsePosition.winner() != NO_WINNER
                            ? evaluate(responsePosition, player)
                            : bestPawnResponse(responsePosition, player);
            worst = Math.min(worst, score);
            if (worst < incumbentScore) {
                break;
            }
        }
        return new ReplyScore(worst, true);
    }

    private Action easyMove(GameState game, int player) {
        List<Action> shortest = new ArrayList<>();
        List<Action> varied = new ArrayList<>();
        int bestDistance = Integer.MAX_VALUE;
        int[] distance = GoalDistances.toGoal(game, player);
        for (GameState.Cell move : game.legalMoves()) {
            int value = distance[GoalDistances.index(move)];
            if (value < bestDistance) {
                bestDistance = value;
                shortest.clear();
            }
            if (value == bestDistance) {
                shortest.add(new Action(move.row(), move.col(), null));
            }
        }
        for (GameState.Cell move : game.legalMoves()) {
            if (distance[GoalDistances.index(move)] <= bestDistance + EASY_DISTANCE_ALLOWANCE) {
                varied.add(new Action(move.row(), move.col(), null));
            }
        }
        List<Action> options = random.nextInt(EASY_VARIATION_FREQUENCY) == 0 ? varied : shortest;
        return options.get(random.nextInt(options.size()));
    }

    private List<Candidate> rankedCandidates(GameState game, int player, long deadline) {
        List<Candidate> choices = new ArrayList<>();
        if (game.winner() != NO_WINNER) {
            return choices;
        }
        addMoveCandidates(choices, game, player);
        if (game.wallsRemaining(game.currentPlayer()) > 0) {
            addWallCandidates(choices, game, player, deadline);
        }
        choices.sort(BEST_FIRST);
        return choices;
    }

    private void addMoveCandidates(List<Candidate> choices, GameState game, int player) {
        for (GameState.Cell move : game.legalMoves()) {
            checkCancelled();
            Action action = new Action(move.row(), move.col(), null);
            GameState next = game.copy();
            action.apply(next);
            choices.add(new Candidate(action, next, evaluate(next, player)));
        }
    }

    private void addWallCandidates(
            List<Candidate> choices, GameState game, int player, long deadline) {
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                for (GameState.Orientation orientation : GameState.Orientation.values()) {
                    checkCancelled();
                    if (System.nanoTime() >= deadline) {
                        return;
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
        if (game.winner() != NO_WINNER) {
            return game.winner() == player ? WIN_SCORE : -WIN_SCORE;
        }
        int next = game.currentPlayer();
        if (immediateWinningMove(game) != null) {
            return next == player
                    ? WIN_SCORE - IMMEDIATE_WIN_DISCOUNT
                    : -WIN_SCORE + IMMEDIATE_WIN_DISCOUNT;
        }
        int opponent = 1 - player;
        int ownDistance =
                GoalDistances.toGoal(game, player)[
                        GoalDistances.index(game.playerPosition(player))];
        int opponentDistance =
                GoalDistances.toGoal(game, opponent)[
                        GoalDistances.index(game.playerPosition(opponent))];
        return (opponentDistance - ownDistance) * DISTANCE_WEIGHT
                + (game.wallsRemaining(player) - game.wallsRemaining(opponent)) * WALL_STOCK_WEIGHT
                + (next == player ? NEXT_TURN_BONUS : -NEXT_TURN_BONUS);
    }

    private static Action immediateWinningMove(GameState game) {
        for (GameState.Cell move : game.legalMoves()) {
            if (game.isGoal(game.currentPlayer(), move)) {
                return new Action(move.row(), move.col(), null);
            }
        }
        return null;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Bot search cancelled");
        }
    }
}
