import static support.SwingTestSupport.edt;
import static support.SwingTestSupport.find;

import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.GameTutorial;
import Board.I18n;

import java.awt.Container;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

/** Exercises the actual modal practice board and the prominent winner replay action. */
public final class TutorialUiTest {
    public static void main(String[] args) throws Exception {
        for (I18n.Language language : I18n.supportedLanguages()) {
            for (Board.Appearance appearance : Board.Appearance.values()) {
                journey(language, appearance);
            }
        }
        System.out.println(
                "Visual tutorial and winner replay UI passed (both languages and themes).");
    }

    private static void journey(I18n.Language language, Board.Appearance appearance)
            throws Exception {
        GameState game =
                GameState.fromSnapshot(
                        new GameState.Snapshot(
                                new GameState.Cell(1, 4),
                                new GameState.Cell(7, 4),
                                10,
                                10,
                                0,
                                -1,
                                List.of()));
        GameConfig config =
                new GameConfig(
                        "Winner 🎮", "Friend", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.HARD);
        Board board =
                edt(
                        () -> {
                            Board window =
                                    new Board(new GameSession(game, config), language, appearance);
                            window.setVisible(true);
                            return window;
                        });
        try {
            edt(
                    () -> {
                        check(!button(board, "replay-game").isVisible(), "No replay before win");
                        button(board, "cell-0-4").doClick();
                        check(board.currentSession().game().winner() == 0, "Real move wins");
                        board.validate();
                        check(
                                button(board, "replay-game").getHeight() >= 36,
                                "Replay has a visible hit area");
                        check(
                                button(board, "replay-game").isShowing(),
                                "Replay is visible beside winner");
                        capture(board.getContentPane(), "replay-" + language + "-" + appearance);
                        button(board, "replay-game").doClick();
                        check(
                                board.currentSession()
                                        .game()
                                        .snapshot()
                                        .equals(new GameState().snapshot()),
                                "Replay resets position");
                        check(
                                board.currentSession()
                                        .config()
                                        .blueName()
                                        .equals(config.blueName()),
                                "Replay preserves names");
                        check(!button(board, "undo-move").isEnabled(), "Replay resets history");
                        button(board, "cell-7-4").doClick();
                        return null;
                    });
            var live = edt(() -> board.currentSession().game().snapshot());
            SwingUtilities.invokeLater(() -> button(board, "tutorial-game").doClick());
            JDialog dialog = awaitDialog(board);
            for (int index = 0; index < GameTutorial.LESSON_COUNT; index++) {
                int step = index;
                edt(
                        () -> {
                            check(
                                    !button(dialog, "lesson-next").isEnabled(),
                                    "Practice required before next");
                            check(
                                    button(dialog, "lesson-back").isEnabled() == (step > 0),
                                    "Back availability");
                            if (step < 2)
                                capture(
                                        dialog.getContentPane(),
                                        "tutorial-" + step + "-" + language + "-" + appearance);
                            GameTutorial expected = new GameTutorial();
                            expected.load(step);
                            BotPlayer.Action action = expected.target();
                            button(
                                            dialog,
                                            (action.orientation() == null ? "cell-" : "wall-")
                                                    + action.row()
                                                    + "-"
                                                    + action.col())
                                    .doClick();
                            check(
                                    button(dialog, "lesson-next").isEnabled(),
                                    "Legal practice completes step");
                            check(
                                    board.currentSession().game().snapshot().equals(live),
                                    "Practice preserves real match");
                            button(dialog, "lesson-next").doClick();
                            return null;
                        });
            }
            edt(
                    () -> {
                        check(!dialog.isVisible(), "Final step closes tutorial");
                        button(board, "undo-move").doClick();
                        check(
                                board.currentSession()
                                        .game()
                                        .snapshot()
                                        .equals(new GameState().snapshot()),
                                "Live history preserved");
                        return null;
                    });
        } finally {
            edt(
                    () -> {
                        for (Window owned : board.getOwnedWindows()) owned.dispose();
                        board.dispose();
                        return null;
                    });
        }
    }

    private static JDialog awaitDialog(Board board) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            JDialog found =
                    edt(
                            () -> {
                                for (Window owned : board.getOwnedWindows())
                                    if (owned instanceof JDialog dialog
                                            && "visual-tutorial".equals(dialog.getName())
                                            && dialog.isShowing()) return dialog;
                                return null;
                            });
            if (found != null) return found;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Tutorial did not open");
    }

    private static AbstractButton button(Container root, String name) {
        var component = find(root, name);
        if (!(component instanceof AbstractButton button))
            throw new AssertionError("Missing " + name);
        return button;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void capture(Container content, String name) throws Exception {
        support.SwingTestSupport.writeImage(
                support.SwingTestSupport.containerImage(content),
                Path.of("out", "tutorial-ui", name + ".png"));
    }
}
