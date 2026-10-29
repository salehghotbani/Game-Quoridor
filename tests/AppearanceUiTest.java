import static support.SwingTestSupport.containerImage;
import static support.SwingTestSupport.edt;
import static support.SwingTestSupport.find;
import static support.SwingTestSupport.findType;

import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.I18n;
import Board.SaveStore;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/** Exercises appearance changes through the visible controls without changing a match. */
public final class AppearanceUiTest {
    private static int checks;
    private static Board board;
    private static Path directory;
    private static final List<Board> windows = new ArrayList<>();

    private AppearanceUiTest() {}

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("AppearanceUiTest requires a graphical desktop.");
        }
        directory = Files.createTempDirectory("quoridor-appearance-test-");
        Color originalPanel = UIManager.getColor("Panel.background");
        Color originalField = UIManager.getColor("TextField.background");
        var watchdog =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "appearance-ui-test-deadline");
                            thread.setDaemon(true);
                            return thread;
                        });
        Thread emergencyCleanup =
                new Thread(AppearanceUiTest::cleanQuietly, "appearance-ui-test-cleanup");
        Runtime.getRuntime().addShutdownHook(emergencyCleanup);
        watchdog.schedule(
                () -> {
                    System.err.println("AppearanceUiTest exceeded its 90-second deadline.");
                    System.exit(1);
                },
                90,
                TimeUnit.SECONDS);
        try {
            menuAndDraft();
            matchAndPause();
            layoutAndPalette();
            saveAndLoad();
            robotTurn();
            finishedMatch();
            independentWindows();
            dialogAppearance();
            equal(
                    UIManager.getColor("Panel.background"),
                    originalPanel,
                    "Appearance does not change global panel colors");
            equal(
                    UIManager.getColor("TextField.background"),
                    originalField,
                    "Appearance does not change global input colors");
            System.out.println("Appearance interface tests passed (" + checks + " checks).");
        } finally {
            try {
                edt(
                        () -> {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof JDialog) {
                                    window.dispose();
                                }
                            }
                            for (Board window : windows) {
                                window.dispose();
                            }
                            return null;
                        });
                clean();
                Runtime.getRuntime().removeShutdownHook(emergencyCleanup);
            } finally {
                watchdog.shutdownNow();
            }
        }
    }

    private static void menuAndDraft() throws Exception {
        edt(
                () -> {
                    board = new Board(null, I18n.Language.ENGLISH);
                    windows.add(board);
                    board.setSize(920, 710);
                    board.setVisible(true);
                    equal(
                            board.currentAppearance(),
                            Board.Appearance.DARK,
                            "Default appearance is dark");
                    checkOptions("appearance-menu");
                    checkOptions("appearance-game");
                    field("blue-name").setText("صالح Alice");
                    field("green-name").setText("Nora");
                    combo("difficulty").setSelectedItem(BotPlayer.Difficulty.HARD);
                    for (I18n.Language language : I18n.Language.values()) {
                        combo("language-menu").setSelectedItem(language);
                        for (boolean bot : new boolean[] {false, true}) {
                            click(bot ? "bot-game" : "local-game");
                            String blue = field("blue-name").getText();
                            String green = field("green-name").getText();
                            BufferedImage dark = null;
                            for (Board.Appearance appearance : Board.Appearance.values()) {
                                select("appearance-menu", appearance);
                                equal(
                                        board.currentAppearance(),
                                        appearance,
                                        "Menu selector applies appearance");
                                equal(
                                        combo("appearance-game").getSelectedItem(),
                                        appearance,
                                        "Both selectors stay synchronized");
                                equal(
                                        board.currentLanguage(),
                                        language,
                                        "Appearance preserves language");
                                equal(
                                        field("blue-name").getText(),
                                        blue,
                                        "Appearance preserves blue draft name");
                                equal(
                                        field("green-name").getText(),
                                        green,
                                        "Appearance preserves opponent draft name");
                                equal(
                                        combo("difficulty").getSelectedItem(),
                                        BotPlayer.Difficulty.HARD,
                                        "Appearance preserves difficulty");
                                check(
                                        button(bot ? "bot-game" : "local-game").isSelected(),
                                        "Appearance preserves chosen mode");
                                check(
                                        board.currentSession() == null,
                                        "Appearance selection does not start a match");
                                checkRenderer("appearance-menu", appearance, language);
                                checkActualCombo(combo("appearance-menu"), appearance);
                                checkActualCombo(combo("language-menu"), appearance);
                                if (bot) {
                                    checkActualCombo(combo("difficulty"), appearance);
                                }
                                checkFieldContrast(field("blue-name"), true);
                                checkFieldContrast(field("green-name"), !bot);
                                checkLayout();
                                BufferedImage image =
                                        render(
                                                "menu-"
                                                        + language.name().toLowerCase()
                                                        + "-"
                                                        + appearance.name().toLowerCase()
                                                        + (bot ? "-bot" : "-local"));
                                checkBrightness(image, appearance);
                                BufferedImage tile = controlImage(button("cell-0-0"));
                                checkColorMode(
                                        new Color(
                                                tile.getRGB(
                                                        tile.getWidth() / 2, tile.getHeight() / 2)),
                                        appearance,
                                        "Board tiles follow the selected appearance");
                                checkButtonContrast(
                                        image, "start-game", "load-game", "local-game", "bot-game");
                                if (appearance == Board.Appearance.DARK) {
                                    dark = image;
                                } else {
                                    checkImageDifference(
                                            dark,
                                            image,
                                            "Menu appearance changes its actual pixels");
                                }
                            }
                        }
                    }
                    click("local-game");
                    field("green-name").setText("Nora");
                    click("start-game");
                    return null;
                });
    }

    private static void matchAndPause() throws Exception {
        edt(
                () -> {
                    click("cell-7-4");
                    click("wall-v-3-3");
                    GameSession before = board.currentSession();
                    select("appearance-game", Board.Appearance.DARK);
                    assertSession(
                            board.currentSession(),
                            before,
                            "Dark appearance preserves moved pawns and placed wall");
                    check(
                            board.currentSession()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.VERTICAL),
                            "Appearance preserves the vertical wall");
                    select("appearance-game", Board.Appearance.LIGHT);
                    assertSession(
                            board.currentSession(),
                            before,
                            "Light appearance preserves moved pawns and placed wall");
                    click("back-menu");
                    select("appearance-menu", Board.Appearance.DARK);
                    check(
                            component("menu-view").isShowing(),
                            "Appearance change keeps the paused match in the menu");
                    assertSession(
                            board.currentSession(),
                            before,
                            "Appearance in menu preserves paused game");
                    click("resume-game");
                    assertSession(
                            board.currentSession(),
                            before,
                            "Resume after appearance change keeps exact match");
                    check(
                            board.currentSession()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.VERTICAL),
                            "Placed vertical wall survives pause and appearance changes");
                    return null;
                });
    }

    private static void layoutAndPalette() throws Exception {
        edt(
                () -> {
                    for (GameConfig.Mode mode : GameConfig.Mode.values()) {
                        board.startSession(new GameSession(new GameState(), config(mode)));
                        for (I18n.Language language : I18n.Language.values()) {
                            combo("language-game").setSelectedItem(language);
                            BufferedImage dark = null;
                            for (Board.Appearance appearance : Board.Appearance.values()) {
                                GameSession before = board.currentSession();
                                select("appearance-game", appearance);
                                assertSession(
                                        board.currentSession(),
                                        before,
                                        "Appearance leaves opening match unchanged");
                                equal(
                                        board.currentLanguage(),
                                        language,
                                        "Game language and appearance are independent");
                                checkRenderer("appearance-game", appearance, language);
                                checkActualCombo(combo("appearance-game"), appearance);
                                checkActualCombo(combo("language-game"), appearance);
                                checkLayout();
                                BufferedImage image =
                                        render(
                                                "game-"
                                                        + language.name().toLowerCase()
                                                        + "-"
                                                        + appearance.name().toLowerCase()
                                                        + "-"
                                                        + mode.name().toLowerCase());
                                checkBrightness(image, appearance);
                                checkButtonContrast(
                                        image,
                                        "save-game",
                                        "load-game-in-game",
                                        "back-menu",
                                        "new-game",
                                        "more-actions");
                                Color background = new Color(image.getRGB(4, 4));
                                contrast(
                                        component("status").getForeground(),
                                        background,
                                        4.5,
                                        "Status remains readable");
                                contrast(
                                        component("turn").getForeground(),
                                        background,
                                        3.0,
                                        "Large active-player text remains readable");
                                if (appearance == Board.Appearance.DARK) {
                                    dark = image;
                                } else {
                                    checkImageDifference(
                                            dark,
                                            image,
                                            "Game appearance changes its actual pixels");
                                }
                            }
                        }
                    }
                    return null;
                });
    }

    private static void saveAndLoad() throws Exception {
        Path save = directory.resolve("بازی appearance.qsave");
        GameSession original =
                edt(
                        () -> {
                            board.startSession(
                                    new GameSession(
                                            new GameState(), config(GameConfig.Mode.LOCAL)));
                            click("cell-7-4");
                            click("wall-2-3");
                            select("appearance-game", Board.Appearance.LIGHT);
                            return board.currentSession();
                        });
        SaveStore.save(save, original);
        GameSession loaded = SaveStore.load(save);
        edt(
                () -> {
                    select("appearance-game", Board.Appearance.DARK);
                    board.startSession(loaded);
                    assertSession(
                            board.currentSession(),
                            original,
                            "Save/load retains game independently of appearance");
                    equal(
                            board.currentAppearance(),
                            Board.Appearance.DARK,
                            "Loading keeps the selected appearance");
                    equal(
                            board.currentLanguage(),
                            I18n.Language.PERSIAN,
                            "Loading keeps selected language");
                    return null;
                });
    }

    private static void robotTurn() throws Exception {
        GameState.Snapshot thinking =
                edt(
                        () -> {
                            board.startSession(
                                    new GameSession(new GameState(), config(GameConfig.Mode.BOT)));
                            click("cell-7-4");
                            GameState.Snapshot before = board.currentSession().game().snapshot();
                            select("appearance-game", Board.Appearance.LIGHT);
                            select("appearance-game", Board.Appearance.DARK);
                            select("appearance-game", Board.Appearance.LIGHT);
                            equal(
                                    board.currentSession().game().snapshot(),
                                    before,
                                    "Appearance does not act for the thinking robot");
                            check(
                                    !button("cell-1-4").isEnabled(),
                                    "Human controls stay locked during robot turn");
                            check(!button("wall-0-0").isEnabled(), "Horizontal input disabled");
                            check(!button("wall-v-0-0").isEnabled(), "Vertical input disabled");
                            render("robot-light-persian");
                            return before;
                        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline
                && edt(() -> board.currentSession().game().currentPlayer() != 0)) {
            Thread.sleep(20);
        }
        GameState.Snapshot after =
                edt(
                        () -> {
                            GameState.Snapshot finished = board.currentSession().game().snapshot();
                            check(
                                    finished.currentPlayer() == 0,
                                    "Robot completes its pending turn after appearance changes");
                            check(
                                    oneRobotAction(thinking, finished),
                                    "Robot performs exactly one legal action");
                            equal(
                                    board.currentAppearance(),
                                    Board.Appearance.LIGHT,
                                    "Robot completion keeps appearance");
                            equal(
                                    board.currentLanguage(),
                                    I18n.Language.PERSIAN,
                                    "Robot completion keeps language");
                            return finished;
                        });
        Thread.sleep(200);
        equal(
                edt(() -> board.currentSession().game().snapshot()),
                after,
                "No duplicate robot action arrives later");
    }

    private static boolean oneRobotAction(GameState.Snapshot before, GameState.Snapshot after) {
        if (!before.blue().equals(after.blue()) || before.blueWalls() != after.blueWalls()) {
            return false;
        }
        GameState replay = GameState.fromSnapshot(before);
        boolean move =
                !before.green().equals(after.green())
                        && before.greenWalls() == after.greenWalls()
                        && before.walls().equals(after.walls());
        boolean wall =
                before.green().equals(after.green())
                        && before.greenWalls() - 1 == after.greenWalls()
                        && after.walls().size() == before.walls().size() + 1
                        && after.walls().containsAll(before.walls());
        if (move) {
            return replay.moveTo(after.green().row(), after.green().col())
                    && replay.snapshot().equals(after);
        }
        if (wall) {
            GameState.Wall placed =
                    after.walls().stream()
                            .filter(candidate -> !before.walls().contains(candidate))
                            .findFirst()
                            .orElseThrow();
            return replay.placeWall(placed.row(), placed.col(), placed.orientation())
                    && replay.snapshot().equals(after);
        }
        return false;
    }

    private static void finishedMatch() throws Exception {
        edt(
                () -> {
                    GameState won =
                            GameState.fromSnapshot(
                                    new GameState.Snapshot(
                                            new GameState.Cell(0, 4),
                                            new GameState.Cell(2, 3),
                                            10,
                                            10,
                                            0,
                                            0,
                                            List.of()));
                    board.startSession(new GameSession(won, config(GameConfig.Mode.LOCAL)));
                    for (I18n.Language language : I18n.Language.values()) {
                        board.setLanguage(language);
                        for (Board.Appearance appearance : Board.Appearance.values()) {
                            select("appearance-game", appearance);
                            equal(
                                    board.currentSession().game().winner(),
                                    0,
                                    "Changing winner appearance retains the winner");
                            check(
                                    board.currentSession().game().legalMoves().isEmpty(),
                                    "Finished board stays frozen after appearance change");
                            check(!button("wall-0-0").isEnabled(), "Horizontal input disabled");
                            check(!button("wall-v-0-0").isEnabled(), "Vertical input disabled");
                            render(
                                    "winner-"
                                            + language.name().toLowerCase()
                                            + "-"
                                            + appearance.name().toLowerCase());
                        }
                    }
                    return null;
                });
    }

    private static void independentWindows() throws Exception {
        edt(
                () -> {
                    Board other = new Board(null, I18n.Language.ENGLISH, Board.Appearance.DARK);
                    windows.add(other);
                    other.setSize(920, 710);
                    other.setVisible(true);
                    JTextField otherName = (JTextField) find(other, "blue-name");
                    Color before = otherName.getBackground();
                    BufferedImage otherBefore = image(other);
                    board.setAppearance(Board.Appearance.DARK);
                    board.setAppearance(Board.Appearance.LIGHT);
                    equal(
                            other.currentAppearance(),
                            Board.Appearance.DARK,
                            "Second window has independent dark appearance");
                    equal(
                            other.currentLanguage(),
                            I18n.Language.ENGLISH,
                            "Second window keeps its own language");
                    equal(
                            otherName.getBackground(),
                            before,
                            "Changing one window leaves other window colors unchanged");
                    equalImages(
                            otherBefore,
                            image(other),
                            "Changing one window leaves other window pixels unchanged");
                    other.dispose();
                    return null;
                });
    }

    private static void dialogAppearance() throws Exception {
        Path existing = directory.resolve("existing.qsave");
        SaveStore.save(existing, edt(() -> board.currentSession()));
        for (I18n.Language language : I18n.Language.values()) {
            for (Board.Appearance appearance : Board.Appearance.values()) {
                edt(
                        () -> {
                            board.startSession(
                                    new GameSession(
                                            new GameState(), config(GameConfig.Mode.LOCAL)));
                            board.setLanguage(language);
                            board.setAppearance(appearance);
                            runSaveDialog(existing, language, appearance);
                            return null;
                        });
            }
        }
    }

    private static void runSaveDialog(
            Path existing, I18n.Language language, Board.Appearance appearance) throws Exception {
        I18n text = new I18n(language);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean[] chooserSeen = {false};
        boolean[] confirmationSeen = {false};
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        Timer timer = new Timer(30, null);
        timer.addActionListener(
                event -> {
                    try {
                        if (System.nanoTime() > deadline) {
                            throw new AssertionError("Appearance dialog timed out");
                        }
                        for (Window window : Window.getWindows()) {
                            if (!(window instanceof JDialog dialog) || !dialog.isShowing()) {
                                continue;
                            }
                            JFileChooser chooser = findType(dialog, JFileChooser.class);
                            if (chooser != null && !chooserSeen[0]) {
                                chooserSeen[0] = true;
                                checkColorMode(
                                        chooser.getBackground(),
                                        appearance,
                                        "Chooser follows appearance");
                                BufferedImage shown = containerImage(dialog.getContentPane());
                                checkColorMode(
                                        new Color(shown.getRGB(2, 2)),
                                        appearance,
                                        "Painted chooser outer spacing follows appearance");
                                for (JComboBox<?> selector : allCombos(chooser)) {
                                    checkActualCombo(selector, appearance);
                                }
                                JTextField filename = findType(chooser, JTextField.class);
                                check(filename != null, "Chooser filename input exists");
                                checkFieldContrast(filename, true);
                                capture(
                                        dialog.getContentPane(),
                                        "save-dialog-"
                                                + language.name().toLowerCase()
                                                + "-"
                                                + appearance.name().toLowerCase());
                                chooser.setSelectedFile(existing.toFile());
                                chooser.approveSelection();
                                return;
                            }
                            JOptionPane pane = findType(dialog, JOptionPane.class);
                            if (pane != null) {
                                confirmationSeen[0] = true;
                                checkColorMode(
                                        pane.getBackground(),
                                        appearance,
                                        "Confirmation follows appearance");
                                AbstractButton no = textButton(dialog, text.text("No"));
                                check(
                                        no != null && no.isShowing(),
                                        "Localized confirmation option exists");
                                contrast(
                                        no.getForeground(),
                                        no.getBackground(),
                                        4.5,
                                        "Confirmation option remains readable");
                                capture(
                                        dialog.getContentPane(),
                                        "overwrite-dialog-"
                                                + language.name().toLowerCase()
                                                + "-"
                                                + appearance.name().toLowerCase());
                                no.doClick(0);
                                return;
                            }
                        }
                    } catch (Throwable problem) {
                        failure.set(problem);
                        for (Window window : Window.getWindows()) {
                            if (window instanceof JDialog) {
                                window.dispose();
                            }
                        }
                        timer.stop();
                    }
                });
        timer.start();
        try {
            click("save-game");
            if (failure.get() != null) {
                throw new AssertionError("Appearance dialog automation failed", failure.get());
            }
            check(
                    chooserSeen[0] && confirmationSeen[0],
                    "Both chooser and overwrite confirmation are exercised");
            equal(board.currentAppearance(), appearance, "Dialog keeps selected appearance");
            equal(board.currentLanguage(), language, "Dialog keeps selected language");
        } finally {
            timer.stop();
        }
    }

    private static GameConfig config(GameConfig.Mode mode) {
        return new GameConfig(
                "صالح Alice",
                mode == GameConfig.Mode.BOT ? "Robot" : "Nora",
                mode,
                BotPlayer.Difficulty.HARD);
    }

    private static void checkOptions(String name) {
        JComboBox<?> selector = combo(name);
        check(selector.getItemCount() == 2, "Exactly two appearance choices");
        equal(
                Set.of(selector.getItemAt(0), selector.getItemAt(1)),
                Set.of(Board.Appearance.DARK, Board.Appearance.LIGHT),
                "Only dark and light are offered");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkRenderer(
            String name, Board.Appearance appearance, I18n.Language language) {
        JComboBox selector = combo(name);
        JList list = new JList();
        list.setComponentOrientation(selector.getComponentOrientation());
        Component rendered =
                selector.getRenderer()
                        .getListCellRendererComponent(list, appearance, -1, false, false);
        check(rendered instanceof JLabel, "Appearance selector renders a readable label");
        equal(
                ((JLabel) rendered).getText(),
                new I18n(language).text(appearance == Board.Appearance.DARK ? "Dark" : "Light"),
                "Appearance choices use selected language");
        check(
                rendered.getComponentOrientation().isLeftToRight() != language.rightToLeft(),
                "Appearance renderer follows language direction");
        contrast(
                rendered.getForeground(),
                rendered.getBackground(),
                4.5,
                "Appearance selector remains readable");
    }

    private static void checkFieldContrast(JTextField field, boolean enabled) {
        contrast(
                enabled ? field.getForeground() : field.getDisabledTextColor(),
                field.getBackground(),
                enabled ? 4.5 : 3.0,
                "Player/file input text remains readable");
        if (enabled) {
            contrast(
                    field.getSelectedTextColor(),
                    field.getSelectionColor(),
                    4.5,
                    "Selected input text remains readable");
            contrast(
                    field.getCaretColor(),
                    field.getBackground(),
                    3.0,
                    "Input caret remains visible");
        }
    }

    /**
     * Compare actual painted glyphs with the same disabled control rendered without its caption.
     */
    private static BufferedImage controlImage(JComponent control) {
        BufferedImage image =
                new BufferedImage(
                        control.getWidth(), control.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        control.printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static void checkLayout() {
        board.validate();
        for (String name :
                List.of(
                        "appearance-menu",
                        "appearance-game",
                        "language-menu",
                        "language-game",
                        "start-game",
                        "load-game",
                        "resume-game",
                        "save-game",
                        "load-game-in-game",
                        "back-menu",
                        "new-game",
                        "more-actions",
                        "wall-v-0-0",
                        "blue-name",
                        "green-name",
                        "difficulty",
                        "turn",
                        "status")) {
            Component control = component(name);
            if (!control.isShowing()) {
                continue;
            }
            Rectangle bounds =
                    SwingUtilities.convertRectangle(
                            control.getParent(), control.getBounds(), board.getContentPane());
            check(
                    bounds.width > 0
                            && bounds.height > 0
                            && bounds.x >= 0
                            && bounds.y >= 0
                            && bounds.x + bounds.width <= board.getContentPane().getWidth()
                            && bounds.y + bounds.height <= board.getContentPane().getHeight(),
                    "Control fits minimum window: " + name + " " + bounds);
            if (control instanceof JComponent component) {
                Rectangle visible = component.getVisibleRect();
                check(
                        visible.width == control.getWidth()
                                && visible.height == control.getHeight(),
                        "Control is not clipped: " + name);
            }
        }
        checkSettingsLabels(board.getContentPane());
    }

    private static void checkSettingsLabels(Container parent) {
        I18n text = new I18n(board.currentLanguage());
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label
                    && label.isShowing()
                    && (text.text("Language").equals(label.getText())
                            || text.text("Appearance").equals(label.getText()))) {
                check(
                        label.getPreferredSize().width <= label.getWidth(),
                        "Setting label fits without clipping: "
                                + label.getText()
                                + " (needs "
                                + label.getPreferredSize().width
                                + ", has "
                                + label.getWidth()
                                + ")");
            }
            if (child instanceof Container container) {
                checkSettingsLabels(container);
            }
        }
    }

    /**
     * Inspect the rasterized value area; BasicComboBoxUI can ignore renderer background properties.
     */
    private static void checkActualCombo(JComboBox<?> selector, Board.Appearance appearance) {
        Window window = SwingUtilities.getWindowAncestor(selector);
        if (window != null) {
            window.validate();
        }
        check(
                selector.getWidth() > 32 && selector.getHeight() > 6,
                "Combo selected value has usable dimensions");
        BufferedImage rendered = controlImage(selector);
        int left = selector.getComponentOrientation().isLeftToRight() ? 4 : 24;
        int right =
                selector.getWidth() - (selector.getComponentOrientation().isLeftToRight() ? 24 : 4);
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int y = 3; y < rendered.getHeight() - 3; y++) {
            for (int x = left; x < right; x++) {
                frequency.merge(rendered.getRGB(x, y), 1, Integer::sum);
            }
        }
        check(!frequency.isEmpty(), "Combo has a visible selected-value area");
        int dominant =
                frequency.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElseThrow()
                        .getKey();
        Color background = new Color(dominant);
        checkColorMode(background, appearance, "Painted combo value background follows appearance");
        contrast(selector.getForeground(), background, 4.5, "Painted combo value remains readable");
    }

    private static List<JComboBox<?>> allCombos(Container parent) {
        List<JComboBox<?>> selectors = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> selector && selector.isShowing()) {
                selectors.add(selector);
            }
            if (child instanceof Container container) {
                selectors.addAll(allCombos(container));
            }
        }
        return selectors;
    }

    private static void checkButtonContrast(BufferedImage image, String... names) {
        for (String name : names) {
            AbstractButton button = button(name);
            if (!button.isShowing()) continue;
            java.awt.Point sample =
                    SwingUtilities.convertPoint(
                            button, button.getWidth() - 10, 10, board.getContentPane());
            contrast(
                    button.getForeground(),
                    new Color(image.getRGB(sample.x, sample.y)),
                    4.5,
                    "Action text remains readable: " + name);
        }
    }

    private static void checkBrightness(BufferedImage image, Board.Appearance appearance) {
        checkColorMode(
                new Color(image.getRGB(4, 4)),
                appearance,
                "Rendered background follows appearance");
    }

    private static void checkColorMode(Color color, Board.Appearance appearance, String message) {
        double brightness = luminance(color);
        check(
                appearance == Board.Appearance.DARK ? brightness < 0.15 : brightness > 0.60,
                message + " (luminance " + brightness + ")");
    }

    private static void contrast(
            Color foreground, Color background, double minimum, String message) {
        double a = luminance(foreground);
        double b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        check(
                ratio >= minimum,
                message
                        + " (contrast "
                        + String.format(java.util.Locale.ROOT, "%.2f", ratio)
                        + ")");
    }

    private static double luminance(Color color) {
        double[] components = {
            color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0
        };
        for (int i = 0; i < 3; i++) {
            components[i] =
                    components[i] <= 0.04045
                            ? components[i] / 12.92
                            : Math.pow((components[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * components[0] + 0.7152 * components[1] + 0.0722 * components[2];
    }

    private static void checkImageDifference(
            BufferedImage dark, BufferedImage light, String message) {
        long changed = 0;
        for (int y = 0; y < dark.getHeight(); y++) {
            for (int x = 0; x < dark.getWidth(); x++) {
                if (dark.getRGB(x, y) != light.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        check(changed > dark.getWidth() * (long) dark.getHeight() / 5, message);
    }

    private static void equalImages(BufferedImage a, BufferedImage b, String message) {
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    throw new AssertionError(message);
                }
            }
        }
        checks++;
    }

    private static BufferedImage render(String name) throws IOException {
        board.validate();
        BufferedImage image = image(board);
        writeImage(image, name);
        return image;
    }

    private static BufferedImage image(Board window) {
        BufferedImage image =
                new BufferedImage(
                        window.getContentPane().getWidth(),
                        window.getContentPane().getHeight(),
                        BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        window.getContentPane().printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static void capture(Container content, String name) throws IOException {
        writeImage(containerImage(content), name);
    }

    private static void writeImage(BufferedImage image, String name) throws IOException {
        Path output =
                Path.of(
                        System.getProperty("quoridor.test.output", "out"),
                        "appearance-" + name + ".png");
        support.SwingTestSupport.writeImage(image, output);
    }

    private static void select(String name, Board.Appearance appearance) {
        JComboBox<?> selector = combo(name);
        check(
                selector.isShowing() && selector.isEnabled(),
                "Appearance selector is available: " + name);
        selector.setSelectedItem(appearance);
    }

    private static void click(String name) {
        support.SwingTestSupport.revealAction(board, name);
        AbstractButton button = button(name);
        check(button.isShowing() && button.isEnabled(), "Action is available: " + name);
        button.doClick(0);
    }

    private static Component component(String name) {
        Component component = find(board, name);
        if (component == null) {
            throw new AssertionError("Missing component: " + name);
        }
        return component;
    }

    private static AbstractButton button(String name) {
        return (AbstractButton) component(name);
    }

    private static JComboBox<?> combo(String name) {
        return (JComboBox<?>) component(name);
    }

    private static JTextField field(String name) {
        return (JTextField) component(name);
    }

    private static AbstractButton textButton(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container container) {
                AbstractButton found = textButton(container, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertSession(GameSession actual, GameSession expected, String message) {
        equal(actual.game().snapshot(), expected.game().snapshot(), message + " state");
        equal(actual.config(), expected.config(), message + " configuration");
    }

    private static void clean() throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void cleanQuietly() {
        try {
            clean();
        } catch (IOException failure) {
            System.err.println("Cannot clean appearance test files: " + failure.getMessage());
        }
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                Objects.equals(actual, expected),
                message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
