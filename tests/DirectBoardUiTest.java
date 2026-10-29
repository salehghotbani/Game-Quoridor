import static support.SwingTestSupport.containerImage;
import static support.SwingTestSupport.edt;
import static support.SwingTestSupport.find;
import static support.SwingTestSupport.writeImage;

import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.I18n;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Real board targets and hover/focus painting, in physical coordinates under RTL. */
public final class DirectBoardUiTest {
    public static void main(String[] arguments) throws Exception {
        Board board =
                edt(
                        () -> {
                            Board value = new Board(null, I18n.Language.ENGLISH);
                            value.setSize(920, 710);
                            value.setVisible(true);
                            return value;
                        });
        try {
            for (I18n.Language language : I18n.Language.values()) {
                for (Board.Appearance appearance : Board.Appearance.values()) {
                    for (int players : new int[] {2, 4}) {
                        edt(
                                () -> {
                                    board.setLanguage(language);
                                    board.setAppearance(appearance);
                                    board.startSession(
                                            new GameSession(
                                                    new GameState(players),
                                                    new GameConfig(
                                                            players == 2
                                                                    ? List.of("Blue", "Green")
                                                                    : List.of(
                                                                            "Blue", "Green", "Red",
                                                                            "Gold"),
                                                            GameConfig.Mode.LOCAL,
                                                            BotPlayer.Difficulty.MEDIUM)));
                                    board.validate();
                                    require(
                                            find(board, "horizontal") == null
                                                    && find(board, "vertical") == null,
                                            "No direction selector remains");
                                    require(
                                            !find(board, "extra-actions").isVisible(),
                                            "Secondary controls start collapsed");
                                    AbstractButton horizontal = button(board, "wall-0-0");
                                    AbstractButton vertical = button(board, "wall-v-0-0");
                                    require(
                                            horizontal.isEnabled() && vertical.isEnabled(),
                                            "Both orientations are immediately available");
                                    require(
                                            !horizontal
                                                    .getBounds()
                                                    .intersects(vertical.getBounds()),
                                            "Gutters have distinct hit areas");
                                    require(
                                            SwingUtilities.getDeepestComponentAt(
                                                            horizontal.getParent(),
                                                            horizontal.getX()
                                                                    + horizontal.getWidth() / 2,
                                                            horizontal.getY()
                                                                    + horizontal.getHeight() / 2)
                                                    == horizontal,
                                            "Mouse geometry hits the intended gap");
                                    JComponent surface = (JComponent) horizontal.getParent();
                                    GameState.Snapshot before =
                                            board.currentSession().game().snapshot();
                                    BufferedImage normal = containerImage(surface);
                                    hover(horizontal, MouseEvent.MOUSE_ENTERED);
                                    BufferedImage preview = containerImage(surface);
                                    require(
                                            different(normal, preview),
                                            "Hover paints the full wall preview");
                                    require(
                                            before.equals(board.currentSession().game().snapshot()),
                                            "Hover is presentation only");
                                    hover(horizontal, MouseEvent.MOUSE_EXITED);
                                    require(
                                            !different(normal, containerImage(surface)),
                                            "Leaving clears preview");
                                    horizontal.doClick(0);
                                    require(
                                            board.currentSession()
                                                    .game()
                                                    .hasWall(
                                                            0, 0, GameState.Orientation.HORIZONTAL),
                                            "One click places a horizontal wall");
                                    require(
                                            !button(board, "wall-v-0-0").isEnabled(),
                                            "Crossing target disabled");
                                    before = board.currentSession().game().snapshot();
                                    button(board, "wall-v-0-0").doClick(0);
                                    require(
                                            before.equals(board.currentSession().game().snapshot()),
                                            "Invalid target cannot mutate a match");
                                    button(board, "wall-v-3-3").doClick(0);
                                    require(
                                            board.currentSession()
                                                    .game()
                                                    .hasWall(3, 3, GameState.Orientation.VERTICAL),
                                            "Vertical placement needs no mode change");
                                    AbstractButton keyboard = button(board, "wall-6-6");
                                    var action =
                                            keyboard.getActionForKeyStroke(
                                                    KeyStroke.getKeyStroke("ENTER"));
                                    require(
                                            action != null,
                                            "Enter activates the focused wall target");
                                    action.actionPerformed(
                                            new java.awt.event.ActionEvent(keyboard, 0, "ENTER"));
                                    require(
                                            board.currentSession()
                                                    .game()
                                                    .hasWall(
                                                            6, 6, GameState.Orientation.HORIZONTAL),
                                            "Keyboard places the focused wall");
                                    button(board, "more-actions").doClick(0);
                                    board.validate();
                                    require(
                                            find(board, "load-game-in-game").isShowing()
                                                    && find(board, "move-history").isShowing()
                                                    && find(board, "new-game").isShowing(),
                                            "Secondary actions remain available under More");
                                    for (String secondary :
                                            List.of(
                                                    "load-game-in-game",
                                                    "move-history",
                                                    "new-game")) {
                                        JComponent control = (JComponent) find(board, secondary);
                                        require(
                                                control.getVisibleRect().height
                                                        == control.getHeight(),
                                                "Expanded actions fit the minimum window");
                                    }
                                    writeImage(
                                            containerImage(board.getContentPane()),
                                            Path.of(
                                                    "out/direct-board/"
                                                            + players
                                                            + "-"
                                                            + language
                                                            + "-"
                                                            + appearance
                                                            + ".png"));
                                    AbstractButton from = button(board, "wall-v-5-4");
                                    GameState.Snapshot beforeArrow =
                                            board.currentSession().game().snapshot();
                                    from.getActionMap()
                                            .get("RIGHT")
                                            .actionPerformed(
                                                    new java.awt.event.ActionEvent(
                                                            from, 0, "RIGHT"));
                                    require(
                                            button(board, "wall-v-5-5").isFocusable(),
                                            "Right arrow keeps physical coordinates under RTL");
                                    require(
                                            beforeArrow.equals(
                                                    board.currentSession().game().snapshot()),
                                            "Arrow navigation never commits");
                                    button(board, "more-actions").doClick(0);
                                    require(
                                            find(board, "cell-0-0").getX()
                                                    < find(board, "cell-0-8").getX(),
                                            "RTL never mirrors the board");
                                    return null;
                                });
                    }
                }
            }
            System.out.println(
                    "Direct desktop wall targets, preview isolation, keyboard and More controls passed.");
        } finally {
            edt(
                    () -> {
                        board.dispose();
                        return null;
                    });
        }
    }

    private static AbstractButton button(Container root, String name) {
        return (AbstractButton) find(root, name);
    }

    private static void hover(Component component, int kind) {
        component.dispatchEvent(
                new MouseEvent(
                        component,
                        kind,
                        System.currentTimeMillis(),
                        0,
                        component.getWidth() / 2,
                        component.getHeight() / 2,
                        0,
                        false));
    }

    private static boolean different(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++)
            for (int x = 0; x < first.getWidth(); x++)
                if (first.getRGB(x, y) != second.getRGB(x, y)) return true;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
