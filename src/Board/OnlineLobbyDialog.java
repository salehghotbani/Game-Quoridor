package Board;

import Online.OnlineState;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Nonmodal online setup and waiting room, with per-window text, direction and appearance. */
final class OnlineLobbyDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SERVER = "http://localhost:8080";

    interface SubmitListener {
        void submit(
                URI server,
                OnlineMatchController.Action action,
                int playerCount,
                String name,
                String code);
    }

    private final I18n i18n;
    private final Theme.Palette palette;
    private final SubmitListener submitListener;
    private final Runnable onLeave;
    private final JTextField server = field(DEFAULT_SERVER, "online-server");
    private final JTextField name = field("", "online-name");
    private final JComboBox<Integer> playerCount = new JComboBox<>(new Integer[] {2, 4});
    private final JTextField code = field("", "online-code");
    private final Theme.Button create;
    private final Theme.Button join;
    private final Theme.Button match;
    private final Theme.Button cancel;
    private final BoardWidgets.WrappedLabel status;
    private final JLabel roomCode;
    private final BoardWidgets.WrappedLabel shareCode;
    private OnlineState lastState;
    private boolean busy;
    private boolean dismissed;

    OnlineLobbyDialog(
            JFrame owner,
            I18n i18n,
            Theme.Palette palette,
            String initialName,
            SubmitListener submitListener,
            Runnable onLeave) {
        super(owner, false);
        this.i18n = Objects.requireNonNull(i18n, "Translation is required");
        this.palette = Objects.requireNonNull(palette, "Palette is required");
        this.submitListener = Objects.requireNonNull(submitListener, "Submit listener is required");
        this.onLeave = Objects.requireNonNull(onLeave, "Leave callback is required");
        setTitle(tr("Play online"));
        setName("online-lobby");
        setLocale(i18n.locale());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        name.setText(Objects.requireNonNull(initialName, "Initial name is required"));
        playerCount.setName("online-count");
        playerCount.setRenderer(new PlayerCountRenderer());
        playerCount.setFont(Theme.font(Font.PLAIN, 15));
        create = button("Create match", "online-create", true, OnlineMatchController.Action.CREATE);
        join = button("Join match", "online-join", false, OnlineMatchController.Action.JOIN);
        match = button("Find opponents", "online-match", false, OnlineMatchController.Action.MATCH);
        cancel = new Theme.Button(tr("Cancel"), false);
        cancel.setName("online-cancel");
        cancel.addActionListener(event -> dismiss());
        status = new BoardWidgets.WrappedLabel(14, Font.PLAIN, Theme.MUTED);
        status.setName("online-status");
        status.setPreferredSize(new Dimension(480, 62));
        roomCode = Theme.label("", Font.BOLD, 23, Theme.GOLD);
        roomCode.setName("online-room-code");
        shareCode = new BoardWidgets.WrappedLabel(13, Font.PLAIN, Theme.MUTED);
        shareCode.setPreferredSize(new Dimension(480, 35));

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        content.add(header(), BorderLayout.PAGE_START);
        content.add(body(), BorderLayout.CENTER);
        content.add(cancelRow(), BorderLayout.PAGE_END);
        setContentPane(content);
        getRootPane().setDefaultButton(create);
        getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "leave-online");
        getRootPane()
                .getActionMap()
                .put(
                        "leave-online",
                        new AbstractAction() {
                            private static final long serialVersionUID = 1L;

                            @Override
                            public void actionPerformed(ActionEvent event) {
                                dismiss();
                            }
                        });
        code.addActionListener(event -> submit(OnlineMatchController.Action.JOIN));
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        dismiss();
                    }
                });
        Theme.apply(content, Theme.DARK, palette, false);
        applyComponentOrientation(
                i18n.rightToLeft()
                        ? ComponentOrientation.RIGHT_TO_LEFT
                        : ComponentOrientation.LEFT_TO_RIGHT);
        setAccessibleNames();
        setMinimumSize(new Dimension(590, 650));
        setSize(620, 690);
        setLocationRelativeTo(owner);
    }

    void setBusy(boolean next) {
        requireEventThread();
        boolean startingRequest = !busy && next;
        busy = next;
        boolean editable = !busy && lastState == null;
        server.setEnabled(editable);
        name.setEnabled(editable);
        playerCount.setEnabled(editable);
        code.setEnabled(editable);
        create.setEnabled(editable);
        join.setEnabled(editable);
        match.setEnabled(editable);
        if (startingRequest && lastState == null) {
            status.setText(tr("Connecting…"));
        }
    }

    void showState(OnlineState state) {
        requireEventThread();
        lastState = Objects.requireNonNull(state, "Online state is required");
        if (state.status() == OnlineState.Status.WAITING) {
            status.setText(
                    tr(
                            "Waiting for players: %d of %d",
                            state.playerNames().size(), state.playerCount()));
            roomCode.setText(tr("Match code: %s", state.roomCode()));
            shareCode.setText(tr("Share this code with the other players."));
        } else {
            status.setText(
                    tr(
                            state.status() == OnlineState.Status.CLOSED
                                    ? "This match has ended."
                                    : "Connected."));
        }
        setBusy(busy);
    }

    void showConnectionProblem(boolean reconnecting) {
        requireEventThread();
        if (reconnecting) {
            status.setText(tr("Connection lost. Reconnecting…"));
        } else if (lastState != null) {
            showState(lastState);
        } else {
            status.setText(tr("Connected."));
        }
    }

    void showFailure(String errorCode) {
        requireEventThread();
        if (terminalFailure(errorCode)) {
            lastState = null;
            roomCode.setText(" ");
            shareCode.setText("");
        }
        status.setText(tr(failureTemplate(errorCode)));
        setBusy(busy);
    }

    /** Closes successful setup without invoking the leave callback. */
    void closeUI() {
        requireEventThread();
        dismissed = true;
        dispose();
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout(0, 9));
        header.setOpaque(false);
        header.add(
                Theme.label(tr("Play online"), Font.BOLD, 27, Theme.TEXT), BorderLayout.PAGE_START);
        BoardWidgets.WrappedLabel introduction =
                new BoardWidgets.WrappedLabel(15, Font.PLAIN, Theme.MUTED);
        introduction.setText(tr("Host a private match, join with a code, or find opponents."));
        introduction.setPreferredSize(new Dimension(480, 43));
        header.add(introduction, BorderLayout.CENTER);
        return header;
    }

    private JPanel body() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(labeledField("Server address", server));
        body.add(Box.createVerticalStrut(12));
        body.add(labeledField("Your name", name));
        body.add(Box.createVerticalStrut(12));
        JPanel details = new JPanel(new GridLayout(1, 2, 14, 0));
        details.setOpaque(false);
        details.add(labeledField("Players", playerCount));
        details.add(labeledField("Match code", code));
        details.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        body.add(details);
        body.add(Box.createVerticalStrut(18));
        JPanel actions = new JPanel(new GridLayout(1, 3, 9, 0));
        actions.setOpaque(false);
        actions.add(create);
        actions.add(join);
        actions.add(match);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        body.add(actions);
        body.add(Box.createVerticalStrut(18));
        Theme.Card progress = new Theme.Card(Theme.PANEL);
        progress.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        progress.setLayout(new BorderLayout(0, 5));
        progress.add(status, BorderLayout.PAGE_START);
        progress.add(roomCode, BorderLayout.CENTER);
        progress.add(shareCode, BorderLayout.PAGE_END);
        body.add(progress);
        return body;
    }

    private JPanel cancelRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(cancel, BorderLayout.LINE_END);
        return row;
    }

    private JPanel labeledField(String title, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(0, 6));
        row.setOpaque(false);
        JLabel label = Theme.label(tr(title), Font.BOLD, 13, Theme.MUTED);
        label.setLabelFor(field);
        row.add(label, BorderLayout.PAGE_START);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        return row;
    }

    private Theme.Button button(
            String title,
            String componentName,
            boolean primary,
            OnlineMatchController.Action action) {
        Theme.Button button = new Theme.Button(tr(title), primary);
        button.setName(componentName);
        button.addActionListener(event -> submit(action));
        return button;
    }

    private void submit(OnlineMatchController.Action action) {
        if (busy || lastState != null || dismissed) {
            return;
        }
        String playerName;
        try {
            playerName =
                    new GameConfig(
                                    name.getText(),
                                    "Green",
                                    GameConfig.Mode.LOCAL,
                                    BotPlayer.Difficulty.EASY)
                            .blueName();
        } catch (IllegalArgumentException invalid) {
            showFailure("INVALID_NAME");
            name.requestFocusInWindow();
            return;
        }
        URI address;
        try {
            address = URI.create(server.getText().strip());
            if (address.getHost() == null
                    || address.getUserInfo() != null
                    || address.getQuery() != null
                    || address.getFragment() != null
                    || !(address.getPath().isEmpty() || address.getPath().equals("/"))
                    || !("http".equalsIgnoreCase(address.getScheme())
                            || "https".equalsIgnoreCase(address.getScheme()))) {
                throw new IllegalArgumentException("Invalid server address");
            }
        } catch (IllegalArgumentException invalid) {
            showFailure("INVALID_SERVER");
            server.requestFocusInWindow();
            return;
        }
        String room = code.getText().strip().toUpperCase(Locale.ROOT);
        if (action == OnlineMatchController.Action.JOIN && !room.matches("[A-Z2-9]{8}")) {
            showFailure("INVALID_CODE");
            code.requestFocusInWindow();
            return;
        }
        name.setText(playerName);
        code.setText(room);
        submitListener.submit(
                address, action, (Integer) playerCount.getSelectedItem(), playerName, room);
    }

    private void dismiss() {
        if (!dismissed) {
            dismissed = true;
            dispose();
            onLeave.run();
        }
    }

    private void setAccessibleNames() {
        server.getAccessibleContext().setAccessibleName(tr("Server address"));
        name.getAccessibleContext().setAccessibleName(tr("Your name"));
        playerCount.getAccessibleContext().setAccessibleName(tr("Players"));
        code.getAccessibleContext().setAccessibleName(tr("Match code"));
        status.getAccessibleContext().setAccessibleName(tr("Play online"));
    }

    private static String failureTemplate(String code) {
        return switch (code) {
            case "INVALID_NAME" -> "Enter a name with 1 to 24 characters.";
            case "INVALID_SERVER" -> "Enter a valid server address.";
            case "INVALID_CODE" -> "Enter an eight-character match code.";
            case "CONNECTION_FAILED" -> "Cannot reach the server. Check the address and try again.";
            case "ROOM_NOT_FOUND", "NOT_FOUND" -> "This match was not found.";
            case "ROOM_FULL" -> "This match is full.";
            case "ROOM_STARTED", "ALREADY_STARTED" -> "This match has already started.";
            case "NO_MEMBERSHIP", "UNAUTHORIZED", "INVALID_TOKEN" ->
                    "Your online session has expired. Join again.";
            case "STALE_REVISION" -> "The board changed. Please try again.";
            case "NOT_YOUR_TURN" -> "Please wait for your turn.";
            case "ILLEGAL_MOVE", "ILLEGAL_WALL" -> "This move is not legal.";
            case "ROOM_CLOSED", "GAME_OVER" -> "This match has ended.";
            case "SERVER_FULL" -> "The server is full. Please try again later.";
            case "NOT_PLAYING" -> "Please wait for the other players.";
            case "SERVER_ERROR" -> "The server could not complete the request. Please try again.";
            default -> "Check the match details and try again.";
        };
    }

    private static boolean terminalFailure(String code) {
        return switch (code) {
            case "NO_MEMBERSHIP",
                            "UNAUTHORIZED",
                            "INVALID_TOKEN",
                            "ROOM_NOT_FOUND",
                            "ROOM_CLOSED",
                            "NOT_FOUND" ->
                    true;
            default -> false;
        };
    }

    private String tr(String template, Object... arguments) {
        return UiText.format(i18n, template, arguments);
    }

    private static JTextField field(String value, String componentName) {
        JTextField field = new JTextField(value);
        field.setName(componentName);
        field.setFont(Theme.font(Font.PLAIN, 15));
        return field;
    }

    private static void requireEventThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Lobby controls must run on the event thread");
        }
    }

    private final class PlayerCountRenderer extends JLabel implements ListCellRenderer<Integer> {
        private static final long serialVersionUID = 1L;

        PlayerCountRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            setFont(Theme.font(Font.PLAIN, 15));
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Integer> list,
                Integer value,
                int index,
                boolean selected,
                boolean focused) {
            setText(value == null ? "" : String.format(i18n.locale(), "%d", value));
            applyComponentOrientation(OnlineLobbyDialog.this.getComponentOrientation());
            setHorizontalAlignment(SwingConstants.LEADING);
            setBackground(selected ? palette.selection() : palette.raised());
            setForeground(palette.text());
            return this;
        }
    }
}
