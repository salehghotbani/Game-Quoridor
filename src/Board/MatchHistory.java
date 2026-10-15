package Board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, in-memory move history for a match. Every stored and returned game has its own mutable
 * state. Callers should confine a history instance to one thread, normally the Swing event thread.
 */
public final class MatchHistory {
    /** Maximum retained moves; the baseline position occupies one additional snapshot. */
    public static final int MAX_PLIES = 1000;

    private final List<GameSession> positions = new ArrayList<>();
    private final List<String> moveDescriptions = new ArrayList<>();
    private int cursor;

    public MatchHistory(GameSession initial) {
        reset(initial);
    }

    /** Starts a new history at an independent copy of the supplied position. */
    public void reset(GameSession initial) {
        GameSession baseline = copySession(initial);
        positions.clear();
        moveDescriptions.clear();
        positions.add(baseline);
        cursor = 0;
    }

    /**
     * Appends an independent position and discards any redo branch. An unchanged position and
     * configuration are ignored without discarding redo. Returns whether a move was recorded.
     */
    public boolean record(GameSession session, String moveDescription) {
        Objects.requireNonNull(moveDescription, "Move description is required");
        GameSession recorded = copySession(session);
        GameSession previous = positions.get(cursor);
        if (recorded.config().equals(previous.config())
                && recorded.game().snapshot().equals(previous.game().snapshot())) {
            return false;
        }

        positions.subList(cursor + 1, positions.size()).clear();
        moveDescriptions.subList(cursor, moveDescriptions.size()).clear();
        positions.add(recorded);
        moveDescriptions.add(moveDescription);
        cursor++;
        if (moveDescriptions.size() > MAX_PLIES) {
            positions.remove(0);
            moveDescriptions.remove(0);
            cursor--;
        }
        return true;
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < moveDescriptions.size();
    }

    /** Returns the preceding independent position, or throws at the retained baseline. */
    public GameSession undo() {
        if (!canUndo()) {
            throw new IllegalStateException("No earlier position is retained");
        }
        GameSession previous = copySession(positions.get(cursor - 1));
        cursor--;
        return previous;
    }

    /** Returns the next independent position, or throws when no redo move remains. */
    public GameSession redo() {
        if (!canRedo()) {
            throw new IllegalStateException("No later position is retained");
        }
        GameSession next = copySession(positions.get(cursor + 1));
        cursor++;
        return next;
    }

    public GameSession current() {
        return copySession(positions.get(cursor));
    }

    /** Number of applied moves still retained; excludes undone moves and trimmed older moves. */
    public int size() {
        return cursor;
    }

    /** Total retained moves, including the redo branch. */
    public int retainedPlies() {
        return moveDescriptions.size();
    }

    /** Immutable descriptions of the currently applied, retained moves in chronological order. */
    public List<String> descriptions() {
        return List.copyOf(moveDescriptions.subList(0, cursor));
    }

    private static GameSession copySession(GameSession session) {
        Objects.requireNonNull(session, "Game session is required");
        return new GameSession(session.game().copy(), session.config());
    }
}
