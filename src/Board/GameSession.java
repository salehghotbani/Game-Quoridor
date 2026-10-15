package Board;

import java.util.Objects;

/**
 * A mutable game with its immutable match configuration. Call {@link GameState#copy()} when handing
 * the state to an independent owner.
 */
public record GameSession(GameState game, GameConfig config) {
    public GameSession {
        Objects.requireNonNull(game, "Game state is required");
        Objects.requireNonNull(config, "Game configuration is required");
        if (game.playerCount() != config.playerCount()) {
            throw new IllegalArgumentException(
                    "Game state and player names must have the same player count");
        }
    }
}
