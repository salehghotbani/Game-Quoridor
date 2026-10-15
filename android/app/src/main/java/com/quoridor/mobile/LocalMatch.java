package com.quoridor.mobile;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.MatchHistory;

import java.util.List;
import java.util.Objects;

/** UI-thread-owned local match; asynchronous robot results must match its revision. */
public final class LocalMatch {
    private GameSession session;
    private final MatchHistory history;
    private long revision;

    public LocalMatch(GameSession initial) {
        Objects.requireNonNull(initial);
        session =
                new GameSession(
                        GameState.fromSnapshot(initial.game().snapshot()), initial.config());
        history = new MatchHistory(session);
    }

    public GameSession session() {
        return new GameSession(session.game().copy(), session.config());
    }

    public long revision() {
        return revision;
    }

    public boolean robotTurn() {
        return session.config().mode() == GameConfig.Mode.BOT
                && session.game().winner() < 0
                && session.game().currentPlayer() == 1;
    }

    public boolean move(int row, int col) {
        return play(new BotPlayer.Action(row, col, null), false, revision);
    }

    public boolean wall(GameState.Wall wall) {
        return wall != null
                && play(
                        new BotPlayer.Action(wall.row(), wall.col(), wall.orientation()),
                        false,
                        revision);
    }

    public boolean applyRobot(BotPlayer.Action action, long expectedRevision) {
        return action != null && play(action, true, expectedRevision);
    }

    private boolean play(BotPlayer.Action action, boolean robot, long expectedRevision) {
        if (revision != expectedRevision || robotTurn() != robot || session.game().winner() >= 0) {
            return false;
        }
        int player = session.game().currentPlayer();
        if (!action.apply(session.game())) {
            return false;
        }
        String description =
                player
                        + (action.orientation() == null
                                ? " M " + action.row() + " " + action.col()
                                : " W "
                                        + action.row()
                                        + " "
                                        + action.col()
                                        + " "
                                        + action.orientation());
        history.record(session, description);
        revision++;
        return true;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        if (!history.canUndo()) {
            return;
        }
        do {
            session = history.undo();
        } while (session.config().mode() == GameConfig.Mode.BOT
                && robotTurn()
                && history.canUndo());
        revision++;
    }

    public void redo() {
        if (!history.canRedo()) {
            return;
        }
        do {
            session = history.redo();
        } while (session.config().mode() == GameConfig.Mode.BOT
                && robotTurn()
                && history.canRedo());
        revision++;
    }

    public List<String> moves() {
        return history.descriptions();
    }
}
