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
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Live Swing integration, persistence hand-off, and asynchronous robot lifecycle. */
public final class BoardSmokeTest {
    private static int checks;
    private static Board board;

    private BoardSmokeTest() {}

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("BoardSmokeTest requires a graphical desktop.");
        }
        try {
            menuAndLocalMatch();
            winsAndReload();
            robotAndCancellation();
            edt(
                    () -> {
                        check(
                                java.util.Arrays.stream(Window.getWindows())
                                                .filter(Window::isShowing)
                                                .count()
                                        == 1,
                                "All flows use one application window");
                        board.dispose();
                        check(!board.isDisplayable(), "Window disposes cleanly");
                        return null;
                    });
        } finally {
            edt(
                    () -> {
                        if (board != null) {
                            board.dispose();
                        }
                        return null;
                    });
        }
        System.out.println("Swing interface tests passed (" + checks + " checks).");
    }

    private static void menuAndLocalMatch() throws Exception {
        edt(
                () -> {
                    board = new Board(null, I18n.Language.ENGLISH);
                    board.setVisible(true);
                    check(board.isShowing(), "Application window is displayed");
                    check(board.getIconImage() != null, "Game icon is packaged");
                    check(component("menu-view").isShowing(), "Start menu is the default entry");
                    check(board.currentSession() == null, "No hidden match before setup");
                    check(named(board, "cell-").size() == 81, "81 board cells exist");
                    check(
                            named(board, "wall-").size() == 128,
                            "Both orientations have 64 direct targets");
                    JComboBox<?> levels = (JComboBox<?>) component("difficulty");
                    check(levels.getItemCount() == 3, "Three robot difficulty levels");
                    render("menu");
                    click("bot-game");
                    check(levels.isShowing(), "Robot mode reveals difficulty");
                    check(!component("green-name").isEnabled(), "Robot controls green");
                    render("menu-bot");
                    click("local-game");
                    ((JTextField) component("blue-name")).setText("Saleh");
                    ((JTextField) component("green-name")).setText("Sara");
                    click("start-game");
                    check(component("game-view").isShowing(), "Start opens the board");
                    check(state().config().mode() == GameConfig.Mode.LOCAL, "Local mode retained");
                    check(state().config().blueName().equals("Saleh"), "Blue name retained");
                    check(state().config().greenName().equals("Sara"), "Green name retained");
                    assertOpening();
                    render("opening");
                    check(!button("cell-6-4").isEnabled(), "Illegal destination disabled");
                    button("cell-6-4").doClick(0);
                    check(
                            state().game().currentPlayer() == 0,
                            "Illegal click does not consume turn");
                    click("cell-7-4");
                    check(
                            state().game().playerPosition(0).equals(new GameState.Cell(7, 4)),
                            "Pawn listener moves model");
                    check(state().game().currentPlayer() == 1, "Pawn move advances turn");
                    click("wall-0-0");
                    check(state().game().wallsRemaining(1) == 9, "Wall listener consumes one wall");
                    check(
                            state().game().hasWall(0, 0, GameState.Orientation.HORIZONTAL),
                            "Horizontal wall stored");
                    check(!button("wall-0-0").isVisible(), "Placed slot hidden");
                    check(!button("wall-0-1").isEnabled(), "Overlapping slot disabled");
                    check(!button("wall-v-0-0").isEnabled(), "Crossing wall disabled");
                    click("wall-v-3-3");
                    check(
                            state().game().hasWall(3, 3, GameState.Orientation.VERTICAL),
                            "Vertical wall stored");
                    render("walls");
                    GameState.Snapshot beforeMenu = state().game().snapshot();
                    click("back-menu");
                    check(component("menu-view").isShowing(), "Main menu navigation works");
                    click("resume-game");
                    check(
                            state().game().snapshot().equals(beforeMenu),
                            "Resume preserves the match");
                    click("new-game");
                    assertOpening();
                    check(
                            button("wall-0-0").isEnabled() && button("wall-v-0-0").isEnabled(),
                            "Restart exposes both wall directions");
                    return null;
                });
    }

    private static void winsAndReload() throws Exception {
        edt(
                () -> {
                    for (int row = 7; row >= 0; row--) {
                        click("cell-" + row + "-4");
                        if (row > 0) {
                            click("cell-0-" + (row % 2 == 1 ? 3 : 4));
                        }
                    }
                    assertFinished(0, "Saleh");
                    render("winner");
                    click("new-game");
                    for (int row = 1; row <= 8; row++) {
                        click("cell-8-" + (row % 2 == 1 ? 3 : 2));
                        click("cell-" + row + "-4");
                    }
                    assertFinished(1, "Sara");
                    click("new-game");
                    click("wall-2-2");
                    click("cell-1-4");
                    return null;
                });
        Path temporary = Files.createTempFile("quoridor-ui-roundtrip-", ".qsave");
        try {
            GameSession saved = edt(BoardSmokeTest::state);
            SaveStore.save(temporary, saved);
            GameSession loaded = SaveStore.load(temporary);
            edt(
                    () -> {
                        board.startSession(loaded);
                        check(
                                state().game().snapshot().equals(saved.game().snapshot()),
                                "Loaded match renders exact position");
                        check(
                                !button("wall-2-2").isVisible(),
                                "Loaded wall is painted and unavailable");
                        check(
                                state().config().equals(saved.config()),
                                "Loaded match preserves players and mode");
                        GameSession isolated = board.currentSession();
                        isolated.game().reset();
                        check(
                                !state().game().snapshot().equals(isolated.game().snapshot()),
                                "Exposed session cannot mutate live match");
                        return null;
                    });
            Files.writeString(temporary, "version=999\n");
            try {
                SaveStore.load(temporary);
                throw new AssertionError("Corrupt file loaded");
            } catch (IOException expected) {
                edt(
                        () -> {
                            check(
                                    state().game().snapshot().equals(saved.game().snapshot()),
                                    "Invalid load leaves active board intact");
                            return null;
                        });
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        Path legacy = Path.of(System.getProperty("quoridor.test.project", "."), "SaveFile.G3M");
        if (Files.isRegularFile(legacy)) {
            GameSession imported = SaveStore.load(legacy);
            edt(
                    () -> {
                        board.startSession(imported);
                        check(
                                state().game().snapshot().equals(imported.game().snapshot()),
                                "Legacy save displays correctly");
                        render("legacy-load");
                        return null;
                    });
        }
    }

    private static void robotAndCancellation() throws Exception {
        for (BotPlayer.Difficulty level : BotPlayer.Difficulty.values()) {
            edt(
                    () -> {
                        board.startSession(robotSession(level));
                        click("cell-7-4");
                        check(
                                named(board, "cell-").stream().noneMatch(Component::isEnabled),
                                "Human input locked while " + level + " thinks");
                        return null;
                    });
            awaitRobot();
            edt(
                    () -> {
                        check(
                                state().config().difficulty() == level,
                                "Robot difficulty retained: " + level);
                        check(
                                !state().game().playerPosition(1).equals(new GameState.Cell(0, 4))
                                        || state().game().wallsRemaining(1) < 10,
                                "Robot performs one real action: " + level);
                        check(
                                named(board, "cell-").stream().anyMatch(Component::isEnabled),
                                "Human can continue after robot");
                        if (level == BotPlayer.Difficulty.HARD) {
                            render("bot");
                        }
                        return null;
                    });
        }
        // Pause a robot turn, then resume it. Cancelled results cannot play in the menu.
        GameState.Snapshot paused =
                edt(
                        () -> {
                            board.startSession(robotSession(BotPlayer.Difficulty.HARD));
                            click("cell-7-4");
                            click("back-menu");
                            return state().game().snapshot();
                        });
        Thread.sleep(850);
        edt(
                () -> {
                    check(
                            component("menu-view").isShowing(),
                            "Menu remains responsive during cancellation");
                    check(
                            state().game().snapshot().equals(paused),
                            "Paused robot cannot change game");
                    click("resume-game");
                    return null;
                });
        awaitRobot();
        // Replacing an active robot turn must invalidate the old asynchronous result.
        GameState.Snapshot fresh =
                edt(
                        () -> {
                            board.startSession(robotSession(BotPlayer.Difficulty.HARD));
                            click("cell-7-4");
                            board.startSession(
                                    new GameSession(
                                            new GameState(),
                                            new GameConfig(
                                                    "Fresh",
                                                    "Match",
                                                    GameConfig.Mode.LOCAL,
                                                    BotPlayer.Difficulty.MEDIUM)));
                            return state().game().snapshot();
                        });
        Thread.sleep(850);
        edt(
                () -> {
                    check(
                            state().game().snapshot().equals(fresh),
                            "Old robot cannot act in replacement match");
                    check(
                            state().config().blueName().equals("Fresh"),
                            "Replacement config retained");
                    return null;
                });
        // A file saved with Green to move resumes the robot automatically on loading.
        GameState waiting = new GameState();
        check(waiting.moveTo(7, 4), "Prepare saved robot turn");
        Path save = Files.createTempFile("quoridor-bot-turn-", ".qsave");
        try {
            SaveStore.save(
                    save,
                    new GameSession(
                            waiting,
                            new GameConfig(
                                    "صالح",
                                    "Robot",
                                    GameConfig.Mode.BOT,
                                    BotPlayer.Difficulty.HARD)));
            GameSession loaded = SaveStore.load(save);
            edt(
                    () -> {
                        board.startSession(loaded);
                        return null;
                    });
            awaitRobot();
            edt(
                    () -> {
                        check(
                                state().config().blueName().equals("صالح"),
                                "Loaded Unicode player survives bot response");
                        return null;
                    });
        } finally {
            Files.deleteIfExists(save);
        }
    }

    private static GameSession robotSession(BotPlayer.Difficulty level) {
        return new GameSession(
                new GameState(), new GameConfig("Player", "Robot", GameConfig.Mode.BOT, level));
    }

    private static void awaitRobot() throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (edt(() -> state().game().currentPlayer() == 0 || state().game().winner() >= 0)) {
                checks++;
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Robot did not complete its turn within 5 seconds");
    }

    private static void assertOpening() {
        check(state().game().currentPlayer() == 0, "Blue starts");
        check(
                state().game().wallsRemaining(0) == 10 && state().game().wallsRemaining(1) == 10,
                "Wall stocks reset");
        check(
                state().game().playerPosition(0).equals(new GameState.Cell(8, 4)),
                "Blue opening position");
        check(
                state().game().playerPosition(1).equals(new GameState.Cell(0, 4)),
                "Green opening position");
        check(
                named(board, "cell-").stream().filter(Component::isEnabled).count() == 3,
                "Three opening destinations");
        check(
                label("blue-walls").contains("10") && label("green-walls").contains("10"),
                "Stocks displayed");
    }

    private static void assertFinished(int winner, String name) {
        check(state().game().winner() == winner, "Correct winner stored");
        check(
                label("turn").contains(name) && label("turn").contains("wins"),
                "Winner displayed by name");
        check(
                named(board, "cell-").stream().noneMatch(Component::isEnabled),
                "Moves freeze after win");
        check(
                named(board, "wall-").stream().noneMatch(Component::isEnabled),
                "Walls freeze after win");
        check(button("new-game").isEnabled(), "Restart remains available");
    }

    private static GameSession state() {
        return board.currentSession();
    }

    private static void click(String name) {
        support.SwingTestSupport.revealAction(board, name);
        AbstractButton button = button(name);
        check(button.isShowing() && button.isEnabled(), "Available action: " + name);
        button.doClick(0);
    }

    private static AbstractButton button(String name) {
        Component found = component(name);
        if (!(found instanceof AbstractButton button)) {
            throw new AssertionError("Missing button: " + name);
        }
        return button;
    }

    private static String label(String name) {
        Component found = component(name);
        if (!(found instanceof JLabel label)) {
            throw new AssertionError("Missing label: " + name);
        }
        return label.getText();
    }

    private static Component component(String name) {
        Component found = find(board, name);
        if (found == null) {
            throw new AssertionError("Missing component: " + name);
        }
        return found;
    }

    private static List<Component> named(Container container, String prefix) {
        List<Component> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child.getName() != null && child.getName().startsWith(prefix)) {
                result.add(child);
            }
            if (child instanceof Container nested) {
                result.addAll(named(nested, prefix));
            }
        }
        return result;
    }

    private static void render(String name) throws IOException {
        board.validate();
        Path output =
                Path.of(
                        System.getProperty("quoridor.test.output", "out"),
                        "board-" + name + ".png");
        writeImage(containerImage(board.getContentPane()), output);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
