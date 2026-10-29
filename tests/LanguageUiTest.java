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
import Board.SaveStore;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;

/** Verifies live translations, RTL layout, and match continuity in both languages. */
public final class LanguageUiTest {
    private static Board board;
    private static int checks;

    private LanguageUiTest() {}

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("LanguageUiTest requires a graphical desktop.");
        }
        Locale originalLocale = Locale.getDefault();
        try {
            defaultLanguages();
            menuAndMatch();
            savedAndFinishedMatch();
            robotSwitch();
            independentWindows();
            check(
                    Locale.getDefault().equals(originalLocale),
                    "Language selection does not change JVM default locale");
        } finally {
            Locale.setDefault(originalLocale);
            edt(
                    () -> {
                        if (board != null) {
                            board.dispose();
                        }
                        return null;
                    });
        }
        System.out.println("Multilingual interface tests passed (" + checks + " checks).");
    }

    private static void defaultLanguages() throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (String tag : List.of("fa-IR", "en-GB", "he-IL", "iw-IL")) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                edt(
                        () -> {
                            Board window = new Board();
                            try {
                                check(
                                        window.currentLanguage()
                                                == (tag.startsWith("fa")
                                                        ? I18n.Language.PERSIAN
                                                        : I18n.Language.ENGLISH),
                                        "Supported system language or English fallback: " + tag);
                            } finally {
                                window.dispose();
                            }
                            return null;
                        });
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    private static void menuAndMatch() throws Exception {
        edt(
                () -> {
                    board = new Board(null, I18n.Language.ENGLISH);
                    board.setSize(920, 710);
                    board.setVisible(true);
                    checkOptions("language-menu");
                    checkOptions("language-game");
                    ((JTextField) component("blue-name")).setText("صالح");
                    ((JTextField) component("green-name")).setText("Nora");
                    click("bot-game");
                    ((JComboBox<?>) component("difficulty"))
                            .setSelectedItem(BotPlayer.Difficulty.HARD);
                    select("language-menu", I18n.Language.PERSIAN);
                    check(
                            board.currentLanguage() == I18n.Language.PERSIAN,
                            "Menu language selector applies Persian");
                    check(
                            !component("menu-view").getComponentOrientation().isLeftToRight(),
                            "Persian menu is RTL");
                    check(
                            ((JComboBox<?>) component("language-game")).getSelectedItem()
                                    == I18n.Language.PERSIAN,
                            "Both language selectors stay synchronized");
                    check(
                            ((JComboBox<?>) component("difficulty")).getSelectedItem()
                                    == BotPlayer.Difficulty.HARD,
                            "Changing language preserves difficulty");
                    check(
                            button("bot-game").isSelected(),
                            "Changing language preserves selected mode");
                    check(
                            ((JTextField) component("blue-name")).getText().equals("صالح"),
                            "Persian player input preserved");
                    check(
                            button("load-game").getText().contains("بارگذاری"),
                            "Menu load action is translated");
                    checkRendererDirection("language-menu", false);
                    checkRendererDirection("difficulty", false);
                    render("menu-fa");
                    click("local-game");
                    ((JTextField) component("green-name")).setText("Nora");
                    click("start-game");
                    check(
                            board.currentSession().config().blueName().equals("صالح"),
                            "Started match retains player name");
                    check(
                            board.currentSession().config().greenName().equals("Nora"),
                            "Latin player name retains its spelling");
                    check(
                            !component("game-view").getComponentOrientation().isLeftToRight(),
                            "Persian game view is RTL");
                    checkRendererDirection("language-game", false);
                    check(
                            button("wall-3-3").getToolTipText().contains("افقی"),
                            "Wall direction is translated");
                    check(label("blue-walls").contains("۱۰"), "Wall count uses Persian digits");
                    check(
                            label("turn").contains("صالح") && hasPersian(label("turn")),
                            "Persian turn includes exact player name");
                    checkBoardCoordinates();
                    render("game-fa");
                    click("cell-7-4");
                    click("wall-v-3-3");
                    GameState.Snapshot position = board.currentSession().game().snapshot();
                    select("language-game", I18n.Language.ENGLISH);
                    check(
                            board.currentSession().game().snapshot().equals(position),
                            "Language switch preserves every pawn and wall");
                    check(
                            board.currentSession()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.VERTICAL),
                            "Language switch preserves the placed vertical wall");
                    check(
                            component("game-view").getComponentOrientation().isLeftToRight(),
                            "English game view is LTR");
                    check(
                            button("wall-3-4").getToolTipText().contains("horizontal"),
                            "English labels restored");
                    check(label("blue-walls").contains("10"), "English digits restored");
                    checkBoardCoordinates();
                    render("game-en");
                    click("back-menu");
                    select("language-menu", I18n.Language.PERSIAN);
                    check(
                            ((JTextField) component("blue-name")).getText().equals("صالح"),
                            "Menu round-trip preserves setup name");
                    click("resume-game");
                    check(
                            board.currentSession().game().snapshot().equals(position),
                            "Language change in menu preserves paused game");
                    return null;
                });
    }

    private static void savedAndFinishedMatch() throws Exception {
        Path save = Files.createTempFile("quoridor-language-roundtrip-", ".qsave");
        try {
            GameSession original = edt(() -> board.currentSession());
            SaveStore.save(save, original);
            GameSession loaded = SaveStore.load(save);
            edt(
                    () -> {
                        board.startSession(loaded);
                        check(
                                board.currentLanguage() == I18n.Language.PERSIAN,
                                "Loading does not replace chosen display language");
                        check(
                                board.currentSession()
                                        .game()
                                        .snapshot()
                                        .equals(original.game().snapshot()),
                                "Translated save/load retains exact board");
                        check(
                                board.currentSession().config().equals(original.config()),
                                "Translated save/load retains exact names and mode");
                        GameState almostWon =
                                GameState.fromSnapshot(
                                        new GameState.Snapshot(
                                                new GameState.Cell(1, 4),
                                                new GameState.Cell(2, 2),
                                                10,
                                                10,
                                                0,
                                                -1,
                                                List.of()));
                        board.startSession(new GameSession(almostWon, original.config()));
                        click("cell-0-4");
                        check(
                                board.currentSession().game().winner() == 0,
                                "Victory works in Persian");
                        check(
                                label("turn").contains("صالح") && !label("turn").contains("wins"),
                                "Victory message is translated");
                        render("winner-fa");
                        select("language-game", I18n.Language.ENGLISH);
                        check(
                                label("turn").contains("wins") && label("turn").contains("صالح"),
                                "Finished match translates without changing winner name");
                        check(
                                board.currentSession().game().winner() == 0,
                                "Changing finished game language does not restart");
                        check(!button("cell-1-4").isEnabled(), "Finished board remains frozen");
                        return null;
                    });
        } finally {
            Files.deleteIfExists(save);
        }
    }

    private static void robotSwitch() throws Exception {
        edt(
                () -> {
                    board.startSession(
                            new GameSession(
                                    new GameState(),
                                    new GameConfig(
                                            "صالح",
                                            "Robot",
                                            GameConfig.Mode.BOT,
                                            BotPlayer.Difficulty.HARD)));
                    click("cell-7-4");
                    GameState.Snapshot thinking = board.currentSession().game().snapshot();
                    select("language-game", I18n.Language.PERSIAN);
                    check(
                            board.currentSession().game().snapshot().equals(thinking),
                            "Language switch does not act for thinking robot");
                    check(
                            hasPersian(label("status")) && !label("status").contains("considering"),
                            "Robot thinking status is translated live");
                    check(
                            !button("cell-1-4").isEnabled(),
                            "Human controls stay locked during robot turn");
                    render("robot-fa");
                    return null;
                });
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline
                && edt(() -> board.currentSession().game().currentPlayer() != 0)) {
            Thread.sleep(20);
        }
        edt(
                () -> {
                    GameState game = board.currentSession().game();
                    check(
                            game.currentPlayer() == 0,
                            "Robot completes its pending turn after language change");
                    check(
                            !game.playerPosition(1).equals(new GameState.Cell(0, 4))
                                    || game.wallsRemaining(1) < 10,
                            "Robot performs a real action");
                    check(
                            game.playerPosition(0).equals(new GameState.Cell(7, 4)),
                            "Human pawn stays in place during translation");
                    return null;
                });
    }

    private static void independentWindows() throws Exception {
        edt(
                () -> {
                    Board english = new Board(null, I18n.Language.ENGLISH);
                    try {
                        check(
                                english.currentLanguage() == I18n.Language.ENGLISH,
                                "Second window has independent English locale");
                        check(
                                board.currentLanguage() == I18n.Language.PERSIAN,
                                "Second window cannot change first window locale");
                        check(
                                ((AbstractButton) find(english, "load-game"))
                                        .getText()
                                        .equals("Load a saved match"),
                                "English window does not inherit Persian labels");
                        check(
                                button("load-game").getText().contains("بارگذاری"),
                                "Persian window remains translated");
                    } finally {
                        english.dispose();
                    }
                    return null;
                });
    }

    private static void checkOptions(String name) {
        JComboBox<?> selector = (JComboBox<?>) component(name);
        check(selector.getItemCount() == 2, "Exactly two supported language options");
        Set<Object> offered = Set.of(selector.getItemAt(0), selector.getItemAt(1));
        check(
                offered.equals(Set.of(I18n.Language.ENGLISH, I18n.Language.PERSIAN)),
                "Only English and Persian are offered");
    }

    private static void checkBoardCoordinates() {
        board.validate();
        check(
                component("cell-0-0").getX() < component("cell-0-8").getX(),
                "RTL does not mirror board column coordinates");
        check(
                component("cell-0-0").getY() < component("cell-8-0").getY(),
                "RTL does not invert goal rows");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkRendererDirection(String name, boolean leftToRight) {
        JComboBox selector = (JComboBox) component(name);
        JList list = new JList();
        list.setComponentOrientation(selector.getComponentOrientation());
        Component rendered =
                selector.getRenderer()
                        .getListCellRendererComponent(
                                list, selector.getSelectedItem(), -1, false, false);
        check(
                rendered.getComponentOrientation().isLeftToRight() == leftToRight,
                "Selected value and dropdown text follow writing direction: " + name);
    }

    private static void select(String name, I18n.Language language) {
        JComboBox<?> combo = (JComboBox<?>) component(name);
        check(combo.isShowing() && combo.isEnabled(), "Language selector available: " + name);
        combo.setSelectedItem(language);
    }

    private static boolean hasPersian(String text) {
        return text.codePoints().anyMatch(code -> code >= 0x0600 && code <= 0x06FF);
    }

    private static void click(String name) {
        support.SwingTestSupport.revealAction(board, name);
        AbstractButton button = button(name);
        check(button.isShowing() && button.isEnabled(), "Action available: " + name);
        button.doClick(0);
    }

    private static AbstractButton button(String name) {
        return (AbstractButton) component(name);
    }

    private static String label(String name) {
        return ((JLabel) component(name)).getText();
    }

    private static Component component(String name) {
        Component found = find(board, name);
        if (found == null) {
            throw new AssertionError("Missing component: " + name);
        }
        return found;
    }

    private static void render(String name) throws IOException {
        board.validate();
        Path output =
                Path.of(
                        System.getProperty("quoridor.test.output", "out"),
                        "language-" + name + ".png");
        writeImage(containerImage(board.getContentPane()), output);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
