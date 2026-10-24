package Board;

import Board.BoardWidgets.CellButton;
import Board.BoardWidgets.SlotButton;
import Board.BoardWidgets.WrappedLabel;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Five small hands-on lessons rendered by the real desktop board. */
final class TutorialDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String[] TITLES = {
        "Find your path", "Change their path", "Jump a pawn", "Go around", "Reach the edge"
    };
    private static final String[] PROMPTS = {
        "Choose the glowing square. Your goal is the blue edge.",
        "Choose the glowing slot. Walls span two squares; leave a path open.",
        "Choose the square beyond the other pawn to jump over it.",
        "A blocked jump? Step around the pawn.",
        "One last move to the blue edge. You win!"
    };
    private final GameTutorial lesson = new GameTutorial();
    private final I18n words;
    private final CellButton[][] cells = new CellButton[GameState.SIZE][GameState.SIZE];
    private final SlotButton[][] slots = new SlotButton[GameState.SIZE - 1][GameState.SIZE - 1];
    private final BoardSurface surface;
    private final JLabel title = Theme.label("", Font.BOLD, 21, Theme.TEXT);
    private final WrappedLabel prompt = new WrappedLabel(13, Font.PLAIN, Theme.MUTED);
    private final Theme.Button back = new Theme.Button("", false);
    private final Theme.Button next = new Theme.Button("", true);

    TutorialDialog(JFrame owner, I18n words, Theme.Palette palette) {
        super(owner, UiText.format(words, "Learn by playing"), true);
        this.words = words;
        setName("visual-tutorial");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(Theme.PANEL);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        heading.setOpaque(false);
        title.setName("lesson-title");
        heading.add(title, BorderLayout.NORTH);
        prompt.setPreferredSize(new Dimension(450, 42));
        heading.add(prompt, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);
        surface =
                new BoardSurface(
                        cells,
                        slots,
                        lesson::position,
                        () -> GameState.Orientation.HORIZONTAL,
                        text -> UiText.format(words, text),
                        (row, col) -> play(new BotPlayer.Action(row, col, null)),
                        (row, col) ->
                                play(
                                        new BotPlayer.Action(
                                                row, col, GameState.Orientation.HORIZONTAL)));
        content.add(surface, BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        navigation.setOpaque(false);
        back.setName("lesson-back");
        next.setName("lesson-next");
        back.addActionListener(
                event -> {
                    lesson.load(lesson.index() - 1);
                    refresh();
                });
        next.addActionListener(
                event -> {
                    if (!lesson.complete()) return;
                    if (lesson.index() == GameTutorial.LESSON_COUNT - 1) dispose();
                    else {
                        lesson.load(lesson.index() + 1);
                        refresh();
                    }
                });
        Theme.Button close = new Theme.Button(UiText.format(words, "Close"), false);
        close.setName("lesson-close");
        close.addActionListener(event -> dispose());
        navigation.add(back);
        navigation.add(close);
        navigation.add(next);
        content.add(navigation, BorderLayout.SOUTH);
        content.applyComponentOrientation(
                words.rightToLeft()
                        ? ComponentOrientation.RIGHT_TO_LEFT
                        : ComponentOrientation.LEFT_TO_RIGHT);
        surface.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        Theme.apply(content, Theme.DARK, palette, false);
        setContentPane(content);
        getRootPane()
                .registerKeyboardAction(
                        event -> dispose(),
                        KeyStroke.getKeyStroke("ESCAPE"),
                        JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(560, 680);
        setMinimumSize(new Dimension(500, 600));
        setLocationRelativeTo(owner);
        refresh();
    }

    private void play(BotPlayer.Action action) {
        if (lesson.perform(action)) {
            refresh();
            next.requestFocusInWindow();
        }
    }

    private void refresh() {
        title.setText(
                UiText.format(
                        words,
                        "Step %d / %d · %s",
                        lesson.index() + 1,
                        GameTutorial.LESSON_COUNT,
                        UiText.format(words, TITLES[lesson.index()])));
        prompt.setText(UiText.format(words, lesson.complete() ? "Nice!" : PROMPTS[lesson.index()]));
        back.setText(UiText.format(words, "Back"));
        back.setEnabled(lesson.index() > 0);
        next.setText(
                UiText.format(
                        words, lesson.index() == GameTutorial.LESSON_COUNT - 1 ? "Done" : "Next"));
        next.setEnabled(lesson.complete());
        GameState state = lesson.position();
        BotPlayer.Action target = lesson.target();
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                GameState.Cell cell = new GameState.Cell(row, col);
                int occupant =
                        cell.equals(state.playerPosition(0))
                                ? 0
                                : cell.equals(state.playerPosition(1)) ? 1 : -1;
                boolean active =
                        !lesson.complete()
                                && target.orientation() == null
                                && target.row() == row
                                && target.col() == col;
                cells[row][col].update(occupant, active);
                cells[row][col].setHinted(active);
                cells[row][col]
                        .getAccessibleContext()
                        .setAccessibleName(
                                UiText.format(words, "Square")
                                        + " "
                                        + (row + 1)
                                        + ", "
                                        + (col + 1));
            }
        }
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                slots[row][col].setEnabled(
                        !lesson.complete()
                                && target.orientation() != null
                                && target.row() == row
                                && target.col() == col);
                slots[row][col]
                        .getAccessibleContext()
                        .setAccessibleName(
                                UiText.format(words, "Place a wall")
                                        + " "
                                        + (row + 1)
                                        + ", "
                                        + (col + 1));
            }
        }
        surface.clearPreview();
        if (!lesson.complete() && target.orientation() != null) {
            surface.previewWall(new GameState.Cell(target.row(), target.col()));
        }
        surface.repaint();
    }
}
