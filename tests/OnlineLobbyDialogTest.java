package Board;

import Online.OnlineState;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Displayed checks for online setup validation, RTL presentation and independent appearance. */
public final class OnlineLobbyDialogTest {
    private static int checks;

    private OnlineLobbyDialogTest() {}

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("OnlineLobbyDialogTest requires a graphical desktop.");
        }
        Path output = Path.of(System.getProperty("quoridor.test.output", "out"));
        Files.createDirectories(output);
        for (I18n.Language language : I18n.supportedLanguages()) {
            for (boolean light : List.of(false, true)) {
                exercise(language, light, output);
            }
        }
        System.out.println("Online lobby interface tests passed (" + checks + " checks).");
    }

    private static void exercise(I18n.Language language, boolean light, Path output)
            throws Exception {
        I18n translation = new I18n(language);
        Theme.Palette palette = light ? Theme.LIGHT : Theme.DARK;
        AtomicReference<Submission> submitted = new AtomicReference<>();
        AtomicInteger left = new AtomicInteger();
        JFrame owner = edt(() -> new JFrame("Lobby test"));
        OnlineLobbyDialog lobby =
                edt(
                        () -> {
                            owner.setSize(920, 710);
                            OnlineLobbyDialog dialog =
                                    new OnlineLobbyDialog(
                                            owner,
                                            translation,
                                            palette,
                                            "Blue",
                                            (uri, action, count, name, code) ->
                                                    submitted.set(
                                                            new Submission(
                                                                    uri, action, count, name,
                                                                    code)),
                                            left::incrementAndGet);
                            dialog.setVisible(true);
                            return dialog;
                        });
        try {
            edt(
                    () -> {
                        check(!lobby.isModal(), "Lobby remains nonmodal");
                        check(
                                lobby.getLocale().equals(translation.locale()),
                                "Lobby locale is per instance");
                        check(
                                lobby.getComponentOrientation().isLeftToRight()
                                        != translation.rightToLeft(),
                                "Lobby follows selected direction");
                        for (String control :
                                List.of(
                                        "online-server",
                                        "online-name",
                                        "online-count",
                                        "online-code",
                                        "online-create",
                                        "online-join",
                                        "online-match",
                                        "online-cancel",
                                        "online-status")) {
                            Component component = find(lobby, control);
                            check(
                                    component.isShowing(),
                                    "Named lobby control is visible: " + control);
                            Point origin =
                                    SwingUtilities.convertPoint(
                                            component, 0, 0, lobby.getContentPane());
                            Rectangle bounds = new Rectangle(origin, component.getSize());
                            check(
                                    new Rectangle(lobby.getContentPane().getSize())
                                            .contains(bounds),
                                    "Named lobby control fits: " + control);
                        }
                        for (String control :
                                List.of(
                                        "online-create",
                                        "online-join",
                                        "online-match",
                                        "online-cancel")) {
                            AbstractButton button = (AbstractButton) find(lobby, control);
                            int textWidth =
                                    button.getFontMetrics(button.getFont())
                                            .stringWidth(button.getText());
                            check(
                                    button.getWidth()
                                            >= textWidth
                                                    + button.getInsets().left
                                                    + button.getInsets().right,
                                    "Button text fits: " + control);
                        }
                        JTextField name = (JTextField) find(lobby, "online-name");
                        check(
                                contrast(name.getForeground(), name.getBackground()) >= 4.5,
                                "Name field has readable contrast");
                        check(
                                contrast(name.getSelectedTextColor(), name.getSelectionColor())
                                        >= 4.5,
                                "Name selection remains readable");
                        JComboBox<?> count = (JComboBox<?>) find(lobby, "online-count");
                        @SuppressWarnings("unchecked")
                        JComboBox<Integer> typedCount = (JComboBox<Integer>) count;
                        Component renderer =
                                typedCount
                                        .getRenderer()
                                        .getListCellRendererComponent(
                                                new JList<>(), 4, -1, false, false);
                        check(
                                renderer.getComponentOrientation().isLeftToRight()
                                        != translation.rightToLeft(),
                                "Count renderer follows RTL");
                        check(
                                ((JLabel) renderer)
                                        .getText()
                                        .equals(String.format(translation.locale(), "%d", 4)),
                                "Count renderer uses localized digits");
                        BufferedImage paintedCombo = image(count);
                        Color center =
                                new Color(
                                        paintedCombo.getRGB(
                                                count.getWidth() / 2, count.getHeight() / 2));
                        check(
                                center.equals(palette.raised()),
                                "Actual combo paint follows the chosen appearance");

                        name.setText(" ");
                        click(lobby, "online-create");
                        check(submitted.get() == null, "Invalid name does not submit");
                        check(
                                status(lobby)
                                        .equals(
                                                translation.text(
                                                        "Enter a name with 1 to 24 characters.")),
                                "Invalid name is translated");
                        name.setText("Blue");
                        JTextField server = (JTextField) find(lobby, "online-server");
                        server.setText("ftp://localhost");
                        click(lobby, "online-create");
                        check(submitted.get() == null, "Invalid URL does not submit");
                        check(
                                status(lobby)
                                        .equals(translation.text("Enter a valid server address.")),
                                "Invalid URL is translated");
                        server.setText("http://localhost:8080");
                        JTextField code = (JTextField) find(lobby, "online-code");
                        code.setText("bad");
                        click(lobby, "online-join");
                        check(submitted.get() == null, "Invalid match code does not submit");
                        check(
                                status(lobby)
                                        .equals(
                                                translation.text(
                                                        "Enter an eight-character match code.")),
                                "Invalid match code is translated");
                        name.setText("  بازیکن Blue  ");
                        code.setText(" abcd2345 ");
                        typedCount.setSelectedItem(4);
                        click(lobby, "online-join");
                        Submission join = submitted.get();
                        check(
                                join.action() == OnlineMatchController.Action.JOIN
                                        && join.count() == 4,
                                "Join preserves selected setup");
                        check(
                                join.name().equals("بازیکن Blue") && join.code().equals("ABCD2345"),
                                "Submit normalizes only outer whitespace and code case");
                        click(lobby, "online-match");
                        check(
                                submitted.get().action() == OnlineMatchController.Action.MATCH,
                                "Matchmaking has its own action");
                        click(lobby, "online-create");
                        check(
                                submitted.get().action() == OnlineMatchController.Action.CREATE,
                                "Private creation has its own action");
                        lobby.setBusy(true);
                        check(
                                !find(lobby, "online-create").isEnabled() && !name.isEnabled(),
                                "Busy state prevents duplicate setup");
                        check(
                                find(lobby, "online-cancel").isEnabled(),
                                "Cancel remains available while connecting");
                        lobby.showFailure("ROOM_NOT_FOUND");
                        lobby.setBusy(false);
                        check(
                                status(lobby).equals(translation.text("This match was not found.")),
                                "Request failure survives the final busy callback");
                        check(name.isEnabled(), "Failed setup can be edited and retried");
                        return null;
                    });
            BufferedImage setup = edt(() -> image(lobby.getContentPane()));
            ImageIO.write(
                    setup,
                    "png",
                    output.resolve(
                                    "online-lobby-"
                                            + language.name().toLowerCase(java.util.Locale.ROOT)
                                            + (light ? "-light.png" : "-dark.png"))
                            .toFile());
            edt(
                    () -> {
                        OnlineState waiting =
                                new OnlineState(
                                        "ABCD2345",
                                        OnlineState.Status.WAITING,
                                        0,
                                        4,
                                        List.of("Blue", "Green"),
                                        2,
                                        null,
                                        "",
                                        List.of());
                        lobby.showState(waiting);
                        check(
                                !find(lobby, "online-create").isEnabled(),
                                "Waiting membership disables duplicate creation");
                        check(
                                !find(lobby, "online-server").isEnabled(),
                                "Waiting membership keeps its server stable");
                        check(
                                status(lobby)
                                        .equals(
                                                translation.text(
                                                        "Waiting for players: %d of %d", 2, 4)),
                                "Waiting count is translated");
                        check(
                                ((JLabel) find(lobby, "online-room-code"))
                                        .getText()
                                        .contains("ABCD2345"),
                                "Waiting room displays the shareable code");
                        lobby.showConnectionProblem(true);
                        check(
                                status(lobby)
                                        .equals(translation.text("Connection lost. Reconnecting…")),
                                "Reconnection is translated");
                        lobby.showConnectionProblem(false);
                        check(
                                status(lobby)
                                        .equals(
                                                translation.text(
                                                        "Waiting for players: %d of %d", 2, 4)),
                                "Reconnection restores waiting state");
                        lobby.showFailure("UNAUTHORIZED");
                        check(
                                find(lobby, "online-create").isEnabled(),
                                "Expired membership reopens setup");
                        check(
                                status(lobby)
                                        .equals(
                                                translation.text(
                                                        "Your online session has expired. Join again.")),
                                "Expired membership has a local explanation");
                        for (String error :
                                List.of(
                                        "SERVER_FULL",
                                        "NOT_PLAYING",
                                        "SERVER_ERROR",
                                        "CONNECTION_FAILED",
                                        "STALE_REVISION",
                                        "NOT_YOUR_TURN",
                                        "ILLEGAL_MOVE",
                                        "UNKNOWN_REMOTE_CODE")) {
                            lobby.showFailure(error);
                            check(
                                    !status(lobby).isBlank() && !status(lobby).contains(error),
                                    "Protocol details are replaced by translated explanations");
                        }
                        lobby.showState(waiting);
                        lobby.closeUI();
                        check(left.get() == 0, "Successful UI close does not leave the match");
                        return null;
                    });
        } finally {
            edt(
                    () -> {
                        lobby.closeUI();
                        owner.dispose();
                        return null;
                    });
        }
        edt(
                () -> {
                    JFrame frame = new JFrame();
                    OnlineLobbyDialog cancelled =
                            new OnlineLobbyDialog(
                                    frame,
                                    translation,
                                    palette,
                                    "Blue",
                                    (uri, action, count, name, code) -> {},
                                    left::incrementAndGet);
                    try {
                        cancelled.setVisible(true);
                        cancelled.setBusy(true);
                        click(cancelled, "online-cancel");
                        cancelled.dispatchEvent(
                                new WindowEvent(cancelled, WindowEvent.WINDOW_CLOSING));
                        check(left.get() == 1, "Cancel and window close leave at most once");
                    } finally {
                        cancelled.closeUI();
                        frame.dispose();
                    }
                    return null;
                });
    }

    private static String status(Container owner) {
        return ((JLabel) find(owner, "online-status")).getText();
    }

    private static void click(Container owner, String name) {
        ((AbstractButton) find(owner, name)).doClick();
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container parent) {
                Component nested = search(parent, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        throw new AssertionError("Missing component " + name);
    }

    private static Component search(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container parent) {
                Component nested = search(parent, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static BufferedImage image(Component component) {
        BufferedImage image =
                new BufferedImage(
                        component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return channel(color.getRed()) * 0.2126
                + channel(color.getGreen()) * 0.7152
                + channel(color.getBlue()) * 0.0722;
    }

    private static double channel(int value) {
        double fraction = value / 255.0;
        return fraction <= 0.04045 ? fraction / 12.92 : Math.pow((fraction + 0.055) / 1.055, 2.4);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Submission(
            URI server, OnlineMatchController.Action action, int count, String name, String code) {}
}
