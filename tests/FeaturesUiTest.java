import static support.SwingTestSupport.edt;
import static support.SwingTestSupport.find;

import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.I18n;

import Online.OnlineServer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Real Swing journeys through four-player tools, private rooms, and random matchmaking. */
public final class FeaturesUiTest {
    private static int checks;
    private static final List<Board> windows = new ArrayList<>();

    private FeaturesUiTest() {}

    public static void main(String[] args) throws Exception {
        var watchdog =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "feature-ui-deadline");
                            thread.setDaemon(true);
                            return thread;
                        });
        watchdog.schedule(
                () -> {
                    System.err.println("FeaturesUiTest exceeded 90 seconds.");
                    System.exit(1);
                },
                90,
                TimeUnit.SECONDS);
        try {
            localTools();
            botHistory();
            try (OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
                server.start();
                String address = "http://127.0.0.1:" + server.port();
                privateRoom(address);
                pausedLocalLobby(address);
                matchmaking(address);
            }
            expiredRoom();
            expiredWaitingLocal();
            System.out.println("New feature interface tests passed (" + checks + " checks).");
        } finally {
            edt(
                    () -> {
                        for (Board board : windows) {
                            board.dispose();
                        }
                        return null;
                    });
            watchdog.shutdownNow();
        }
    }

    private static Board window(I18n.Language language, Board.Appearance appearance)
            throws Exception {
        return edt(
                () -> {
                    Board board = new Board(null, language, appearance);
                    windows.add(board);
                    board.setVisible(true);
                    return board;
                });
    }

    private static void localTools() throws Exception {
        Board board = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
        edt(
                () -> {
                    combo(board, "player-count").setSelectedItem(4);
                    field(board, "blue-name").setText("Blue friend");
                    field(board, "green-name").setText("Green friend");
                    field(board, "red-name").setText("Red friend");
                    field(board, "gold-name").setText("Gold friend");
                    click(board, "start-game");
                    equal(
                            board.currentSession().game().playerCount(),
                            4,
                            "Four-player menu starts four pawns");
                    equal(
                            board.currentSession().game().wallsRemaining(3),
                            5,
                            "Five walls per player");
                    equal(
                            board.currentSession().config().name(2),
                            "Red friend",
                            "Extra player name retained");
                    click(board, "cell-7-4");
                    click(board, "cell-4-1");
                    click(board, "cell-1-4");
                    click(board, "cell-4-7");
                    GameState.Snapshot position = board.currentSession().game().snapshot();
                    click(board, "undo-move");
                    equal(
                            board.currentSession().game().currentPlayer(),
                            3,
                            "Undo returns to gold's turn");
                    click(board, "redo-move");
                    equal(
                            board.currentSession().game().snapshot(),
                            position,
                            "Redo restores exact position");
                    click(board, "hint-move");
                    check(
                            ((JLabel) component(board, "status"))
                                    .getText()
                                    .contains("Suggested move"),
                            "Hint identifies coordinates");
                    equal(
                            board.currentSession().game().snapshot(),
                            position,
                            "Hint does not play a move");
                    click(board, "wall-2-2");
                    equal(
                            board.currentSession().game().wallsRemaining(0),
                            4,
                            "Wall consumes four-player inventory");
                    click(board, "undo-move");
                    equal(
                            board.currentSession().game().snapshot(),
                            position,
                            "Undo restores wall geometry and inventory");
                    board.setLanguage(I18n.Language.PERSIAN);
                    board.setAppearance(Board.Appearance.LIGHT);
                    equal(
                            ((AbstractButton) component(board, "undo-move")).getText(),
                            "برگشت حرکت",
                            "New tools translated");
                    equal(
                            board.currentSession().game().snapshot(),
                            position,
                            "Presentation changes preserve all four pawns");
                    board.validate();
                    support.SwingTestSupport.writeImage(
                            support.SwingTestSupport.containerImage(board.getContentPane()),
                            output().resolve("four-player-fa-light.png"));
                    click(board, "new-game");
                    equal(
                            board.currentSession().game().playerCount(),
                            4,
                            "Restart preserves player count");
                    equal(
                            board.currentSession().config().name(3),
                            "Gold friend",
                            "Restart preserves extra names");
                    board.dispose();
                    return null;
                });
    }

    private static void botHistory() throws Exception {
        Board board = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
        GameSession initial =
                new GameSession(
                        new GameState(),
                        new GameConfig(
                                "Human", "Robot", GameConfig.Mode.BOT, BotPlayer.Difficulty.EASY));
        edt(
                () -> {
                    board.startSession(initial);
                    click(board, "cell-7-4");
                    return null;
                });
        await(
                () -> board.currentSession().game().currentPlayer() == 0,
                "Robot completes its reply");
        edt(
                () -> {
                    GameState.Snapshot after = board.currentSession().game().snapshot();
                    click(board, "undo-move");
                    equal(
                            board.currentSession().game().snapshot(),
                            initial.game().snapshot(),
                            "Bot undo rewinds full human/robot turn");
                    click(board, "redo-move");
                    equal(
                            board.currentSession().game().snapshot(),
                            after,
                            "Bot redo restores both exact moves");
                    board.dispose();
                    return null;
                });
    }

    private static void privateRoom(String address) throws Exception {
        Board host = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
        openLobby(host, address, "Host", 2, "online-create", "");
        await(
                () ->
                        lobby(host) != null
                                && !((JLabel) component(lobby(host), "online-room-code"))
                                        .getText()
                                        .isBlank(),
                "Host receives room code");
        String code =
                edt(
                        () -> {
                            var match =
                                    Pattern.compile("[A-Z2-9]{8}")
                                            .matcher(
                                                    ((JLabel)
                                                                    component(
                                                                            lobby(host),
                                                                            "online-room-code"))
                                                            .getText());
                            check(match.find(), "Room code is displayed");
                            return match.group();
                        });
        Board guest = window(I18n.Language.PERSIAN, Board.Appearance.LIGHT);
        openLobby(guest, address, "مهمان", 2, "online-join", code);
        List<Board> room = List.of(host, guest);
        await(
                () -> room.stream().allMatch(board -> board.currentSession() != null),
                "Both private-room windows start");
        await(() -> ((AbstractButton) component(host, "cell-7-4")).isEnabled(), "Host can move");
        edt(
                () -> {
                    check(
                            !component(guest, "cell-1-4").isEnabled(),
                            "Other seat cannot play out of turn");
                    check(!component(host, "undo-move").isEnabled(), "Online undo disabled");
                    check(!component(host, "hint-move").isEnabled(), "Online hints disabled");
                    click(host, "cell-7-4");
                    return null;
                });
        await(
                () -> samePosition(room) && host.currentSession().game().currentPlayer() == 1,
                "Move synchronizes both windows");
        await(
                () -> component(guest, "cell-1-4").isEnabled(),
                "Guest's controls enable on its turn");
        edt(
                () -> {
                    click(guest, "cell-1-4");
                    return null;
                });
        await(
                () -> samePosition(room) && host.currentSession().game().currentPlayer() == 0,
                "Reply synchronizes both windows");
        edt(
                () -> {
                    guest.validate();
                    support.SwingTestSupport.writeImage(
                            support.SwingTestSupport.containerImage(guest.getContentPane()),
                            output().resolve("online-fa-light.png"));
                    click(host, "back-menu");
                    check(
                            host.currentSession() == null,
                            "Leaving online game does not expose a local resume");
                    return null;
                });
        await(
                () -> ((JLabel) component(guest, "status")).getText().contains("بسته"),
                "Peer sees room closure");
        edt(
                () -> {
                    check(
                            !component(guest, "cell-2-4").isEnabled(),
                            "Closed game cannot accept local moves");
                    host.dispose();
                    guest.dispose();
                    return null;
                });
    }

    private static void matchmaking(String address) throws Exception {
        List<Board> room = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Board board = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
            room.add(board);
            openLobby(board, address, "Player " + index, 4, "online-match", "");
            if (index < 3) {
                await(
                        () ->
                                lobby(board) != null
                                        && !((JLabel) component(lobby(board), "online-room-code"))
                                                .getText()
                                                .isBlank(),
                        "Matchmaking accepts waiting player");
            }
        }
        await(
                () -> room.stream().allMatch(board -> board.currentSession() != null),
                "Four strangers matched into one game");
        for (int seat : new int[] {0, 2, 1, 3}) {
            Board board = room.get(seat);
            String destination =
                    switch (seat) {
                        case 0 -> "cell-7-4";
                        case 1 -> "cell-1-4";
                        case 2 -> "cell-4-1";
                        default -> "cell-4-7";
                    };
            await(
                    () -> component(board, destination).isEnabled(),
                    "Correct clockwise online seat enabled");
            edt(
                    () -> {
                        click(board, destination);
                        return null;
                    });
            await(
                    () ->
                            samePosition(room)
                                    && !room.get(0)
                                            .currentSession()
                                            .game()
                                            .playerPosition(seat)
                                            .equals(new GameState(4).playerPosition(seat)),
                    "Four-player move synchronizes everyone");
        }
        edt(
                () -> {
                    equal(
                            room.get(0).currentSession().game().currentPlayer(),
                            0,
                            "Online clockwise round completes");
                    for (Board board : room) {
                        board.dispose();
                    }
                    return null;
                });
    }

    private static void pausedLocalLobby(String address) throws Exception {
        Board board = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
        GameState.Snapshot before =
                edt(
                        () -> {
                            board.startSession(
                                    new GameSession(
                                            new GameState(),
                                            new GameConfig(
                                                    "Local A",
                                                    "Local B",
                                                    GameConfig.Mode.LOCAL,
                                                    BotPlayer.Difficulty.EASY)));
                            click(board, "cell-7-4");
                            GameState.Snapshot position = board.currentSession().game().snapshot();
                            click(board, "back-menu");
                            return position;
                        });
        openLobby(board, address, "Waiting friend", 4, "online-create", "");
        await(
                () ->
                        lobby(board) != null
                                && !((JLabel) component(lobby(board), "online-room-code"))
                                        .getText()
                                        .isBlank(),
                "Paused local game can open and create online room");
        edt(
                () -> {
                    click(lobby(board), "online-cancel");
                    equal(
                            board.currentSession().game().snapshot(),
                            before,
                            "Cancel waiting preserves paused local board");
                    click(board, "resume-game");
                    check(
                            component(board, "cell-1-4").isEnabled(),
                            "Cancel waiting allows local game to resume");
                    click(board, "undo-move");
                    equal(
                            board.currentSession().game().snapshot(),
                            new GameState().snapshot(),
                            "Paused local history also survives waiting/cancel");
                    board.dispose();
                    return null;
                });
    }

    private static void expiredRoom() throws Exception {
        OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.port();
        Board first = window(I18n.Language.ENGLISH, Board.Appearance.DARK);
        Board second = window(I18n.Language.ENGLISH, Board.Appearance.LIGHT);
        try {
            String address = "http://127.0.0.1:" + port;
            openLobby(first, address, "First", 2, "online-match", "");
            await(
                    () ->
                            lobby(first) != null
                                    && !((JLabel) component(lobby(first), "online-room-code"))
                                            .getText()
                                            .isBlank(),
                    "First seat waits before restart test");
            openLobby(second, address, "Second", 2, "online-match", "");
            await(
                    () -> first.currentSession() != null && second.currentSession() != null,
                    "Both seats established before restart");
            GameState.Snapshot before = edt(() -> first.currentSession().game().snapshot());
            server.close();
            server = new OnlineServer(new InetSocketAddress("127.0.0.1", port));
            server.start();
            await(
                    () ->
                            ((JLabel) component(first, "status"))
                                            .getText()
                                            .contains("room is closed")
                                    && ((JLabel) component(second, "status"))
                                            .getText()
                                            .contains("room is closed"),
                    "Server restart ends expired memberships in both windows");
            edt(
                    () -> {
                        check(
                                !component(first, "cell-7-4").isEnabled(),
                                "Expired online seat cannot silently become local");
                        check(
                                !component(second, "cell-1-4").isEnabled(),
                                "Other expired seat remains frozen");
                        equal(
                                first.currentSession().game().snapshot(),
                                before,
                                "Expired membership preserves last confirmed position");
                        first.dispose();
                        second.dispose();
                        return null;
                    });
        } finally {
            server.close();
        }
    }

    private static void expiredWaitingLocal() throws Exception {
        OnlineServer.Limits limits =
                new OnlineServer.Limits(
                        8,
                        2,
                        8,
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(600),
                        java.time.Duration.ofSeconds(2));
        try (OnlineServer server =
                new OnlineServer(new InetSocketAddress("127.0.0.1", 0), limits)) {
            server.start();
            Board board = window(I18n.Language.ENGLISH, Board.Appearance.LIGHT);
            GameState.Snapshot before =
                    edt(
                            () -> {
                                board.startSession(
                                        new GameSession(
                                                new GameState(),
                                                new GameConfig(
                                                        "Local A",
                                                        "Local B",
                                                        GameConfig.Mode.LOCAL,
                                                        BotPlayer.Difficulty.EASY)));
                                click(board, "cell-7-4");
                                GameState.Snapshot position =
                                        board.currentSession().game().snapshot();
                                click(board, "back-menu");
                                return position;
                            });
            openLobby(
                    board, "http://127.0.0.1:" + server.port(), "Waiting", 4, "online-create", "");
            await(
                    () ->
                            lobby(board) != null
                                    && !((JLabel) component(lobby(board), "online-room-code"))
                                            .getText()
                                            .isBlank(),
                    "Waiting-expiry test obtains its first room");
            String firstCode =
                    edt(() -> ((JLabel) component(lobby(board), "online-room-code")).getText());
            await(
                    () -> component(lobby(board), "online-create").isEnabled(),
                    "Waiting expiry allows another attempt");
            edt(
                    () -> {
                        click(lobby(board), "online-create");
                        return null;
                    });
            await(
                    () ->
                            !((JLabel) component(lobby(board), "online-room-code"))
                                            .getText()
                                            .isBlank()
                                    && !((JLabel) component(lobby(board), "online-room-code"))
                                            .getText()
                                            .equals(firstCode),
                    "Same dialog can create a new room after waiting expiry");
            edt(
                    () -> {
                        click(lobby(board), "online-cancel");
                        click(board, "resume-game");
                        equal(
                                board.currentSession().game().snapshot(),
                                before,
                                "Waiting expiry and retry preserve paused local position");
                        check(
                                component(board, "cell-1-4").isEnabled(),
                                "Waiting expiry does not freeze resumed local game");
                        board.dispose();
                        return null;
                    });
        }
    }

    private static void openLobby(
            Board board, String address, String name, int count, String action, String code)
            throws Exception {
        edt(
                () -> {
                    click(board, "online-game");
                    JDialog dialog = lobby(board);
                    field(dialog, "online-server").setText(address);
                    field(dialog, "online-name").setText(name);
                    combo(dialog, "online-count").setSelectedItem(count);
                    field(dialog, "online-code").setText(code);
                    click(dialog, action);
                    return null;
                });
    }

    private static boolean samePosition(List<Board> boards) {
        var reference = boards.get(0).currentSession();
        return reference != null
                && boards.stream()
                        .allMatch(
                                board ->
                                        board.currentSession() != null
                                                && reference
                                                        .game()
                                                        .snapshot()
                                                        .equals(
                                                                board.currentSession()
                                                                        .game()
                                                                        .snapshot()));
    }

    private static JDialog lobby(Board board) {
        for (Window owned : board.getOwnedWindows()) {
            if (owned instanceof JDialog dialog
                    && dialog.isDisplayable()
                    && "online-lobby".equals(dialog.getName())) {
                return dialog;
            }
        }
        return null;
    }

    private static Path output() {
        return Path.of(System.getProperty("quoridor.test.output", "out"));
    }

    private static void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (edt(condition)) {
                checks++;
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError(message);
    }

    private static Component component(Container owner, String name) {
        Component found = find(owner, name);
        if (found == null) {
            throw new AssertionError("Missing control: " + name);
        }
        return found;
    }

    private static JTextField field(Container owner, String name) {
        return (JTextField) component(owner, name);
    }

    private static JComboBox<?> combo(Container owner, String name) {
        return (JComboBox<?>) component(owner, name);
    }

    private static void click(Container owner, String name) {
        AbstractButton button = (AbstractButton) component(owner, name);
        check(button.isEnabled(), "Enabled control: " + name);
        button.scrollRectToVisible(
                new java.awt.Rectangle(0, 0, button.getWidth(), button.getHeight()));
        button.doClick(0);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                java.util.Objects.equals(actual, expected),
                message + " (" + actual + " vs " + expected + ")");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
