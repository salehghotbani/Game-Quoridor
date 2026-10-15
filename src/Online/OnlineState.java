package Online;

import Board.GameSession;

import java.util.List;
import java.util.Objects;

/** Public room information. Authentication credentials are never part of a room snapshot. */
public record OnlineState(
        String roomCode,
        Status status,
        int playerIndex,
        int playerCount,
        List<String> playerNames,
        long revision,
        GameSession session,
        String reason,
        List<String> moves) {
    public enum Status {
        WAITING,
        PLAYING,
        CLOSED
    }

    public OnlineState {
        Objects.requireNonNull(roomCode);
        Objects.requireNonNull(status);
        playerNames = List.copyOf(playerNames);
        moves = List.copyOf(moves);
        reason = Objects.requireNonNull(reason);
        if ((playerCount != 2 && playerCount != 4)
                || playerIndex < 0
                || playerIndex >= playerCount
                || playerNames.isEmpty()
                || playerIndex >= playerNames.size()
                || playerNames.size() > playerCount
                || revision < 0
                || moves.size() > 1000) {
            throw new IllegalArgumentException("Invalid room state");
        }
        session = copy(session);
    }

    @Override
    public GameSession session() {
        return copy(session);
    }

    private static GameSession copy(GameSession session) {
        return session == null ? null : new GameSession(session.game().copy(), session.config());
    }
}
