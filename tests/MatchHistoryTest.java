import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.MatchHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exercises timeline branching, snapshot ownership and retention without creating Swing windows.
 */
public final class MatchHistoryTest {
    private static int checks;

    private MatchHistoryTest() {}

    public static void main(String[] args) {
        emptyHistoryAndBoundaries();
        independentSnapshots();
        branchAndDescriptionOwnership();
        duplicateAndInvalidRecords();
        finishedGameAndConfiguration();
        pairedBotTurns();
        fourPlayerSnapshots();
        boundedHistory();
        resetHistory();
        System.out.println("MatchHistory tests passed (" + checks + " checks).");
    }

    private static void emptyHistoryAndBoundaries() {
        GameSession initial = session(new GameState());
        MatchHistory history = new MatchHistory(initial);
        equal(history.size(), 0, "Initial timeline has no applied moves");
        equal(history.retainedPlies(), 0, "Initial timeline has no redo moves");
        equal(history.descriptions(), List.of(), "Initial descriptions are empty");
        check(!history.canUndo() && !history.canRedo(), "Initial timeline has neither direction");
        sameSession(history.current(), initial, "Initial position is retained");
        rejects(IllegalStateException.class, history::undo, "Undo stops at the baseline");
        rejects(IllegalStateException.class, history::redo, "Redo stops at the latest move");
    }

    private static void independentSnapshots() {
        GameState game = new GameState();
        GameSession live = session(game);
        MatchHistory history = new MatchHistory(live);
        GameState.Snapshot opening = game.snapshot();
        check(game.moveTo(7, 4), "Live opening move succeeds");
        equal(history.current().game().snapshot(), opening, "Constructor copies its input");
        check(history.record(live, "Blue moves"), "A changed position is recorded");
        GameState.Snapshot firstMove = game.snapshot();
        check(game.placeWall(3, 3, GameState.Orientation.HORIZONTAL), "Live wall move succeeds");
        equal(history.current().game().snapshot(), firstMove, "Recording copies its input");

        GameSession exported = history.current();
        check(exported.game().moveTo(1, 4), "Returned current game is mutable independently");
        equal(
                history.current().game().snapshot(),
                firstMove,
                "Current does not expose stored state");
        GameSession undone = history.undo();
        check(
                undone.game().placeWall(2, 2, GameState.Orientation.VERTICAL),
                "Undo result can change");
        equal(
                history.current().game().snapshot(),
                opening,
                "Undo does not expose its stored baseline");
        GameSession redone = history.redo();
        check(
                redone.game().placeWall(4, 4, GameState.Orientation.VERTICAL),
                "Redo result can change");
        equal(
                history.current().game().snapshot(),
                firstMove,
                "Redo does not expose its stored state");
        check(
                history.current().game() != history.current().game(),
                "Each current call owns a new game");
    }

    private static void branchAndDescriptionOwnership() {
        GameSession live = session(new GameState());
        MatchHistory history = new MatchHistory(live);
        check(live.game().moveTo(7, 4), "Branch fixture first move");
        history.record(live, "First");
        check(live.game().moveTo(1, 4), "Branch fixture second move");
        history.record(live, "Second");
        List<String> published = history.descriptions();
        rejects(
                UnsupportedOperationException.class,
                () -> published.add("Injected"),
                "Published descriptions cannot be edited");

        live = history.undo();
        equal(history.size(), 1, "Undo reduces applied plies");
        equal(history.retainedPlies(), 2, "Undo preserves the redo branch");
        equal(history.descriptions(), List.of("First"), "Descriptions follow the active cursor");
        equal(published, List.of("First", "Second"), "Published descriptions remain a snapshot");
        check(history.canRedo(), "Undo enables redo");
        check(
                live.game().placeWall(3, 3, GameState.Orientation.VERTICAL),
                "Alternative branch move");
        history.record(live, "Alternative");
        check(!history.canRedo(), "A new move discards the redo branch");
        equal(history.retainedPlies(), 2, "Branch replacement does not append old redo moves");
        equal(
                history.descriptions(),
                List.of("First", "Alternative"),
                "Only the new branch remains");
        history.undo();
        sameSession(history.redo(), live, "Redo follows the replacement branch");
    }

    private static void duplicateAndInvalidRecords() {
        GameSession live = session(new GameState());
        MatchHistory history = new MatchHistory(live);
        check(!history.record(live, "No change"), "An identical position is ignored");
        check(live.game().moveTo(7, 4), "Duplicate fixture move");
        history.record(live, "First");
        GameSession opening = history.undo();
        check(!history.record(opening, "No change"), "An identical position after undo is ignored");
        check(history.canRedo(), "Ignoring a duplicate preserves redo");
        rejects(
                NullPointerException.class,
                () -> history.record(null, "Bad"),
                "Null session rejected");
        rejects(
                NullPointerException.class,
                () -> history.record(live, null),
                "Null move description rejected");
        check(history.canRedo(), "Rejected records preserve redo");
        sameSession(history.redo(), live, "Rejected records do not alter later positions");
        rejects(NullPointerException.class, () -> new MatchHistory(null), "Null baseline rejected");
    }

    private static void finishedGameAndConfiguration() {
        GameState game =
                GameState.fromSnapshot(
                        new GameState.Snapshot(
                                new GameState.Cell(1, 4),
                                new GameState.Cell(5, 3),
                                10,
                                10,
                                0,
                                -1,
                                List.of()));
        GameConfig config =
                new GameConfig(
                        "بازیکن یک", "Robot", GameConfig.Mode.BOT, BotPlayer.Difficulty.HARD);
        MatchHistory history = new MatchHistory(new GameSession(game, config));
        check(game.moveTo(0, 4), "Winning move succeeds");
        history.record(new GameSession(game, config), "Winning move");
        equal(history.current().game().winner(), 0, "Winner is recorded");
        GameSession beforeWin = history.undo();
        equal(beforeWin.game().winner(), -1, "Undo returns an unfinished game");
        check(!beforeWin.game().legalMoves().isEmpty(), "Undo after victory restores legal moves");
        equal(beforeWin.config(), config, "Undo preserves names, mode and difficulty");
        GameSession afterWin = history.redo();
        equal(afterWin.game().winner(), 0, "Redo restores the winner");
        check(afterWin.game().legalMoves().isEmpty(), "Redone victory remains terminal");
        equal(afterWin.config(), config, "Redo preserves match configuration");
    }

    private static void pairedBotTurns() {
        GameSession live = session(new GameState());
        MatchHistory history = new MatchHistory(live);
        check(live.game().moveTo(7, 4), "Human move succeeds");
        history.record(live, "Human");
        check(live.game().moveTo(1, 4), "Bot reply succeeds");
        history.record(live, "Robot");
        GameState.Snapshot afterReply = live.game().snapshot();
        history.undo();
        GameSession opening = history.undo();
        equal(
                opening.game().snapshot(),
                new GameState().snapshot(),
                "Two undos rewind a complete turn");
        history.redo();
        equal(history.redo().game().snapshot(), afterReply, "Two redos restore a complete turn");
    }

    private static void boundedHistory() {
        GameSession live = session(new GameState());
        MatchHistory history = new MatchHistory(live);
        List<GameState.Snapshot> expected = new ArrayList<>();
        expected.add(live.game().snapshot());
        int totalMoves = MatchHistory.MAX_PLIES + 37;
        for (int move = 1; move <= totalMoves; move++) {
            GameState.Cell pawn = live.game().playerPosition(live.game().currentPlayer());
            check(live.game().moveTo(pawn.row(), pawn.col() == 4 ? 3 : 4), "Bound fixture move");
            check(
                    history.record(live, "Move " + move),
                    "Every changed bound fixture position records");
            expected.add(live.game().snapshot());
            check(history.retainedPlies() <= MatchHistory.MAX_PLIES, "Retained moves stay bounded");
        }
        equal(history.size(), MatchHistory.MAX_PLIES, "Applied move count is bounded");
        equal(history.retainedPlies(), MatchHistory.MAX_PLIES, "Total retained moves are bounded");
        equal(
                history.descriptions().get(0),
                "Move 38",
                "Oldest descriptions trim with their states");
        for (int undo = 1; undo <= MatchHistory.MAX_PLIES; undo++) {
            equal(
                    history.undo().game().snapshot(),
                    expected.get(totalMoves - undo),
                    "Bounded undo follows the retained timeline");
        }
        check(!history.canUndo(), "Trimmed moves cannot be undone");
        equal(
                history.descriptions(),
                List.of(),
                "The retained baseline has no applied descriptions");
        for (int redo = 1; redo <= MatchHistory.MAX_PLIES; redo++) {
            equal(
                    history.redo().game().snapshot(),
                    expected.get(totalMoves - MatchHistory.MAX_PLIES + redo),
                    "Bounded redo follows the retained timeline");
        }
        check(!history.canRedo(), "Bounded redo ends at the latest position");
    }

    private static void fourPlayerSnapshots() {
        GameState game = new GameState(4);
        GameConfig config =
                new GameConfig(
                        List.of("Blue", "Green", "Red", "Gold"),
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.MEDIUM);
        GameSession live = new GameSession(game, config);
        MatchHistory history = new MatchHistory(live);
        List<GameState.Snapshot> expected = new ArrayList<>();
        expected.add(game.snapshot());
        for (int turn = 0; turn < 8; turn++) {
            int player = game.currentPlayer();
            GameState.Cell move = game.legalMoves().iterator().next();
            check(game.moveTo(move.row(), move.col()), "Four-player fixture uses a legal move");
            history.record(live, "Player " + player);
            expected.add(game.snapshot());
        }
        check(
                game.placeWall(2, 2, GameState.Orientation.HORIZONTAL),
                "Four-player fixture places a wall");
        history.record(live, "Wall");
        expected.add(game.snapshot());
        for (int index = expected.size() - 2; index >= 0; index--) {
            GameSession restored = history.undo();
            equal(
                    restored.game().snapshot(),
                    expected.get(index),
                    "Four-player undo restores every pawn and stock");
            equal(restored.config(), config, "Four-player undo preserves all four names");
        }
        for (int index = 1; index < expected.size(); index++) {
            equal(
                    history.redo().game().snapshot(),
                    expected.get(index),
                    "Four-player redo preserves clockwise turns");
        }
    }

    private static void resetHistory() {
        GameSession live = session(new GameState());
        MatchHistory history = new MatchHistory(live);
        check(live.game().moveTo(7, 4), "Reset fixture move");
        history.record(live, "First");
        history.undo();
        rejects(NullPointerException.class, () -> history.reset(null), "Null reset rejected");
        check(history.canRedo(), "Rejected reset preserves history");
        GameConfig replacement =
                new GameConfig(
                        "New blue", "New green", GameConfig.Mode.BOT, BotPlayer.Difficulty.EASY);
        GameSession reset = new GameSession(live.game(), replacement);
        history.reset(reset);
        check(!history.canUndo() && !history.canRedo(), "Reset clears both directions");
        equal(history.size(), 0, "Reset clears applied count");
        equal(history.retainedPlies(), 0, "Reset discards the previous branch");
        sameSession(
                history.current(), reset, "Reset retains the replacement configuration and state");
        check(reset.game().moveTo(1, 4), "Reset input can change independently");
        equal(
                history.current().game().playerPosition(1),
                new GameState.Cell(0, 4),
                "Reset copies input");
    }

    private static GameSession session(GameState game) {
        return new GameSession(
                game,
                new GameConfig(
                        "Blue", "Green", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM));
    }

    private static void sameSession(GameSession actual, GameSession expected, String message) {
        equal(actual.config(), expected.config(), message + " configuration");
        equal(actual.game().snapshot(), expected.game().snapshot(), message + " state");
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            check(type.isInstance(error), message + ": " + error);
            return;
        }
        throw new AssertionError(message + ": no exception");
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                Objects.equals(actual, expected),
                message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
