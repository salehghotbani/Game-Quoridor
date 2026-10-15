package Board;

import java.util.Objects;

/** A board together with the names, opponent mode, and bot difficulty it belongs to. */
public record GameSession(GameState game, GameConfig config) {
    public GameSession {
        Objects.requireNonNull(game, "Game state is required");
        Objects.requireNonNull(config, "Game configuration is required");
    }
}
