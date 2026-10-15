package Board;

import java.util.Comparator;
import java.util.Objects;

/** Platform-neutral legal pawn hint; it never modifies the supplied game. */
public final class GameHints {
    private GameHints() {}

    public static GameState.Cell pawnMove(GameState game) {
        Objects.requireNonNull(game, "game");
        if (game.winner() >= 0) {
            return null;
        }
        int[] distances = GoalDistances.toGoal(game, game.currentPlayer());
        return game.legalMoves().stream()
                .min(Comparator.comparingInt(cell -> distances[GoalDistances.index(cell)]))
                .orElse(null);
    }
}
