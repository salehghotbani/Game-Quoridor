package Board;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.text.AttributedString;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;

/** Desktop match setup, board, robot lifecycle, and saved-game entry points. */
public final class Board extends JFrame {
    private static final long serialVersionUID = 1L;
    private final CardLayout navigation = new CardLayout();
    private final JPanel views = new JPanel(navigation);
    private final CellButton[][] cells = new CellButton[GameState.SIZE][GameState.SIZE];
    private final SlotButton[][] slots = new SlotButton[GameState.SIZE - 1][GameState.SIZE - 1];
    private final JTextField blueName = nameField("Blue", "blue-name");
    private final JTextField greenName = nameField("Green", "green-name");
    private final Theme.Toggle localMode = new Theme.Toggle("Two players");
    private final Theme.Toggle botMode = new Theme.Toggle("Play a robot");
    private final JComboBox<BotPlayer.Difficulty> difficulty = new JComboBox<>(BotPlayer.Difficulty.values());
    private final JLabel modeHint = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel opponentLabel = Theme.label("GREEN PLAYER", Font.BOLD, 11, Theme.MUTED);
    private final JPanel difficultyPanel = new JPanel(new BorderLayout(0, 9));
    private final Theme.Button resume = new Theme.Button("Resume your match", false);
    private final Theme.Button start = new Theme.Button("Start a new match  →", true);
    private final WrappedLabel turn = new WrappedLabel(17, Font.BOLD, Theme.TEXT);
    private final JLabel blueWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel greenWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final WrappedLabel status = new WrappedLabel(12, Font.PLAIN, Theme.MUTED);
    private final JLabel matchLabel = Theme.label("", Font.BOLD, 11, Theme.MUTED);
    private final Theme.Toggle horizontal = new Theme.Toggle("Horizontal");
    private final Theme.Toggle vertical = new Theme.Toggle("Vertical");
    private final PlayerCard blueCard = new PlayerCard(0, blueWalls);
    private final PlayerCard greenCard = new PlayerCard(1, greenWalls);
    private final PlayingSurface surface = new PlayingSurface();
    private final BotPlayer robot = new BotPlayer();
    private GameSession session;
    private GameState game;
    private GameState.Orientation orientation = GameState.Orientation.HORIZONTAL;
    private GameState.Cell preview;
    private SwingWorker<BotPlayer.Action, Void> botWorker;
    private long revision;
    private boolean inGame;
    private boolean botBusy;
    private Path savePath;

    /** Opens the main menu. */
    public Board() { this(null); }

    /** Opens a supplied match directly, copying its mutable state. */
    public Board(GameSession initialSession) {
        super("Quoridor — Your next move");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        URL icon = Board.class.getResource("/Files/Images/com.nozhaco.quoridor_512x512.png");
        if (icon != null) { setIconImage(new ImageIcon(icon).getImage()); }
        views.add(createMenu(), "menu");
        views.add(createGameView(), "game");
        setContentPane(views);
        setSize(1000, 750);
        setMinimumSize(new Dimension(920, 710));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { cancelRobot(); }
        });
        if (initialSession == null) {
            navigation.show(views, "menu");
            getRootPane().setDefaultButton(start);
        } else { startSession(initialSession); }
    }

    /** Replaces the match and cancels any outstanding robot search. Call on the EDT. */
    public void startSession(GameSession candidate) {
        Objects.requireNonNull(candidate, "session");
        cancelRobot();
        game = candidate.game().copy();
        session = new GameSession(game, candidate.config());
        savePath = null;
        orientation = GameState.Orientation.HORIZONTAL;
        horizontal.setSelected(true);
        preview = null;
        inGame = true;
        navigation.show(views, "game");
        getRootPane().setDefaultButton(null);
        refresh();
        scheduleRobot();
    }

    /** Returns a defensive copy, or null before any match has started. */
    public GameSession currentSession() {
        return session == null ? null : new GameSession(game.copy(), session.config());
    }

    @Override public void dispose() {
        cancelRobot();
        inGame = false;
        super.dispose();
    }

    private JPanel createMenu() {
        JPanel menu = backgroundPanel();
        menu.setName("menu-view");
        menu.setLayout(new BorderLayout(34, 24));
        menu.setBorder(BorderFactory.createEmptyBorder(30, 40, 28, 40));
        JPanel hero = column();
        hero.add(Theme.label("QUORIDOR  /  THE STRATEGY CLASSIC", Font.BOLD, 11, Theme.GOLD));
        hero.add(Box.createVerticalStrut(25));
        hero.add(Theme.label("Your next", Font.BOLD, 48, Theme.TEXT));
        hero.add(Theme.label("great move.", Font.BOLD, 48, Theme.TEXT));
        hero.add(Box.createVerticalStrut(12));
        hero.add(Theme.label("A simple race. A world of possibilities.", Font.PLAIN, 15, Theme.MUTED));
        hero.add(Box.createVerticalStrut(16));
        HeroBoard illustration = new HeroBoard();
        illustration.setAlignmentX(0f);
        illustration.setPreferredSize(new Dimension(340, 280));
        illustration.setMaximumSize(new Dimension(380, 290));
        hero.add(illustration);
        hero.add(Box.createVerticalStrut(9));
        hero.add(Theme.label("MOVE YOUR PAWN. SHAPE THEIR PATH.", Font.BOLD, 11, Theme.GOLD));
        hero.add(Box.createVerticalStrut(9));
        hero.add(Theme.label("Reach the far edge before your opponent.", Font.PLAIN, 13, Theme.MUTED));
        hero.add(Theme.label("Place walls, but always leave a way through.", Font.PLAIN, 13, Theme.MUTED));
        hero.add(Box.createVerticalGlue());
        for (Component component : hero.getComponents()) {
            if (component instanceof JLabel label) {
                label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
            }
        }
        menu.add(hero, BorderLayout.CENTER);
        Theme.Card setup = new Theme.Card(Theme.PANEL);
        setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
        setup.setBorder(BorderFactory.createEmptyBorder(26, 26, 24, 26));
        setup.setPreferredSize(new Dimension(430, 610));
        addLeft(setup, Theme.label("LET'S PLAY", Font.BOLD, 11, Theme.GREEN));
        setup.add(Box.createVerticalStrut(9));
        addLeft(setup, Theme.label("Make it your game.", Font.BOLD, 27, Theme.TEXT));
        setup.add(Box.createVerticalStrut(22));
        localMode.setName("local-game"); botMode.setName("bot-game"); localMode.setSelected(true);
        ButtonGroup modes = new ButtonGroup(); modes.add(localMode); modes.add(botMode);
        localMode.addActionListener(event -> updateMode());
        botMode.addActionListener(event -> updateMode());
        JPanel modeButtons = row(localMode, botMode);
        fixedHeight(modeButtons, 44); addLeft(setup, modeButtons);
        setup.add(Box.createVerticalStrut(11)); addLeft(setup, modeHint);
        setup.add(Box.createVerticalStrut(24));
        JPanel names = row(fieldGroup(Theme.label("BLUE PLAYER", Font.BOLD, 11, Theme.MUTED), blueName),
                fieldGroup(opponentLabel, greenName));
        fixedHeight(names, 69); addLeft(setup, names);
        setup.add(Box.createVerticalStrut(20));
        difficulty.setName("difficulty");
        difficulty.setSelectedItem(BotPlayer.Difficulty.MEDIUM);
        difficulty.setFont(Theme.font(Font.PLAIN, 14));
        difficulty.setBackground(Theme.RAISED); difficulty.setForeground(Theme.TEXT);
        difficulty.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        difficulty.setRenderer(new DifficultyRenderer());
        difficulty.getAccessibleContext().setAccessibleName("Robot difficulty");
        difficultyPanel.setOpaque(false);
        difficultyPanel.add(Theme.label("ROBOT DIFFICULTY", Font.BOLD, 11, Theme.MUTED), BorderLayout.NORTH);
        difficultyPanel.add(difficulty, BorderLayout.CENTER);
        fixedHeight(difficultyPanel, 69); addLeft(setup, difficultyPanel);
        setup.add(Box.createVerticalStrut(18));
        addLeft(setup, Theme.label("Blue moves first. Each player has 10 walls.", Font.PLAIN, 12, Theme.MUTED));
        setup.add(Box.createVerticalGlue());
        start.setName("start-game"); start.setMnemonic('S');
        start.addActionListener(event -> beginFromMenu());
        fixedHeight(start, 46); addLeft(setup, start);
        setup.add(Box.createVerticalStrut(10));
        Theme.Button load = button("Load a saved match", "load-game", this::loadGame);
        fixedHeight(load, 42); addLeft(setup, load);
        resume.setName("resume-game"); resume.addActionListener(event -> resumeGame());
        resume.setVisible(false); fixedHeight(resume, 42);
        setup.add(Box.createVerticalStrut(10)); addLeft(setup, resume);
        menu.add(setup, BorderLayout.EAST);
        updateMode();
        return menu;
    }

    private JPanel createGameView() {
        JPanel content = backgroundPanel(); content.setName("game-view");
        content.setLayout(new BorderLayout(20, 13));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 14, 24));
        JPanel header = new JPanel(new BorderLayout(20, 0)); header.setOpaque(false);
        JPanel title = column();
        title.add(Theme.label("QUORIDOR", Font.BOLD, 25, Theme.TEXT)); title.add(matchLabel);
        header.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2)); actions.setOpaque(false);
        actions.add(button("Save", "save-game", this::saveGame));
        actions.add(button("Load", "load-game-in-game", this::loadGame));
        actions.add(button("Main menu", "back-menu", this::showMenu));
        header.add(actions, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH); content.add(surface, BorderLayout.CENTER);
        content.add(createControls(), BorderLayout.EAST);
        status.setName("status"); status.setPreferredSize(new Dimension(850, 34));
        content.add(status, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createControls() {
        JPanel controls = column(); controls.setPreferredSize(new Dimension(264, 500));
        controls.add(Theme.label("CURRENT TURN", Font.BOLD, 10, Theme.MUTED));
        controls.add(Box.createVerticalStrut(5));
        turn.setName("turn"); fixedHeight(turn, 44); controls.add(turn);
        controls.add(Box.createVerticalStrut(10));
        blueWalls.setName("blue-walls"); greenWalls.setName("green-walls");
        fixedHeight(blueCard, 88); fixedHeight(greenCard, 88);
        controls.add(blueCard); controls.add(Box.createVerticalStrut(10)); controls.add(greenCard);
        controls.add(Box.createVerticalStrut(18));
        controls.add(Theme.label("PLACE A WALL", Font.BOLD, 10, Theme.MUTED));
        controls.add(Box.createVerticalStrut(8));
        horizontal.setName("horizontal"); vertical.setName("vertical"); horizontal.setSelected(true);
        ButtonGroup directions = new ButtonGroup(); directions.add(horizontal); directions.add(vertical);
        horizontal.addActionListener(event -> chooseDirection(GameState.Orientation.HORIZONTAL));
        vertical.addActionListener(event -> chooseDirection(GameState.Orientation.VERTICAL));
        JPanel directionButtons = row(horizontal, vertical);
        fixedHeight(directionButtons, 40); controls.add(directionButtons);
        controls.add(Box.createVerticalStrut(9));
        WrappedLabel instructions = new WrappedLabel(12, Font.PLAIN, Theme.MUTED);
        instructions.setText("Hover a small slot to preview your wall. Click a glowing square to move.");
        fixedHeight(instructions, 50); controls.add(instructions);
        controls.add(Box.createVerticalGlue()); controls.add(Box.createVerticalStrut(12));
        Theme.Button restart = button("New game", "new-game",
                () -> startSession(new GameSession(new GameState(), session.config())));
        fixedHeight(restart, 42); controls.add(restart);
        for (Component component : controls.getComponents()) {
            if (component instanceof JComponent control) { control.setAlignmentX(0f); }
        }
        return controls;
    }

    private void updateMode() {
        boolean bot = botMode.isSelected();
        opponentLabel.setText(bot ? "YOUR OPPONENT" : "GREEN PLAYER"); greenName.setEnabled(!bot);
        if (bot && greenName.getText().equals("Green")) { greenName.setText("Robot"); }
        else if (!bot && greenName.getText().equals("Robot")) { greenName.setText("Green"); }
        difficultyPanel.setVisible(bot);
        modeHint.setText(bot ? "You play blue. The robot plays green." : "A friendly rivalry, on the same screen.");
        difficultyPanel.getParent().revalidate();
    }

    private void beginFromMenu() {
        try {
            GameConfig config = new GameConfig(blueName.getText(), botMode.isSelected() ? "Robot" : greenName.getText(),
                    botMode.isSelected() ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                    (BotPlayer.Difficulty) difficulty.getSelectedItem());
            startSession(new GameSession(new GameState(), config));
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, "Use player names between 1 and 24 characters.",
                    "Check player names", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showMenu() {
        cancelRobot(); inGame = false; preview = null; resume.setVisible(session != null);
        navigation.show(views, "menu"); getRootPane().setDefaultButton(start);
    }

    private void resumeGame() {
        if (session == null) { return; }
        inGame = true; navigation.show(views, "game"); getRootPane().setDefaultButton(null);
        refresh(); scheduleRobot();
    }

    private void chooseDirection(GameState.Orientation direction) { orientation = direction; preview = null; refresh(); }

    private boolean isRobotTurn() {
        return session != null && session.config().mode() == GameConfig.Mode.BOT
                && game.currentPlayer() == 1 && game.winner() < 0;
    }

    private boolean humanCanPlay() {
        return inGame && game != null && game.winner() < 0 && !botBusy && !isRobotTurn();
    }

    private void movePawn(int row, int col) {
        if (humanCanPlay() && game.moveTo(row, col)) { afterAction(); }
    }

    private void placeWall(int row, int col) {
        if (!humanCanPlay()) { return; }
        if (game.placeWall(row, col, orientation)) { afterAction(); }
        else { status.setText("That wall cannot be placed. Both players need a route to their goal."); }
    }

    private void afterAction() { preview = null; refresh(); scheduleRobot(); }

    private void cancelRobot() {
        revision++;
        if (botWorker != null) { botWorker.cancel(true); botWorker = null; }
        botBusy = false;
    }

    private void scheduleRobot() {
        if (!inGame || !isRobotTurn() || botWorker != null) { return; }
        long token = revision;
        GameState snapshot = game.copy();
        BotPlayer.Difficulty level = session.config().difficulty();
        botBusy = true; refresh();
        botWorker = new SwingWorker<>() {
            @Override protected BotPlayer.Action doInBackground() { return robot.chooseAction(snapshot, level); }
            @Override protected void done() {
                if (isCancelled() || token != revision || botWorker != this || !inGame) { return; }
                botWorker = null; botBusy = false;
                try {
                    BotPlayer.Action action = get();
                    if (action == null || !action.apply(game)) { fallbackRobotMove(); }
                    afterAction();
                } catch (CancellationException exception) { refresh(); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt(); fallbackRobotMove(); afterAction();
                } catch (ExecutionException exception) {
                    fallbackRobotMove(); afterAction(); status.setText("The robot played a safe move. Your turn.");
                }
            }
        };
        botWorker.execute();
    }

    private void fallbackRobotMove() {
        Set<GameState.Cell> moves = game.legalMoves();
        if (!moves.isEmpty()) { GameState.Cell first = moves.iterator().next(); game.moveTo(first.row(), first.col()); }
    }

    private void saveGame() {
        JFileChooser chooser = fileChooser("Save your match");
        chooser.setSelectedFile(savePath == null ? new java.io.File("Quoridor.qsave") : savePath.toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".qsave")) {
            target = target.resolveSibling(target.getFileName() + ".qsave");
        }
        if (java.nio.file.Files.exists(target) && JOptionPane.showConfirmDialog(this,
                "Replace the saved match “" + target.getFileName() + "”?", "Replace saved match",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) { return; }
        try {
            SaveStore.save(target, currentSession()); savePath = target;
            status.setText("Match saved to " + target.getFileName() + ". You can continue playing.");
        } catch (IOException exception) { showFileError("Couldn't save this match", "Choose a writable folder and try again."); }
    }

    private void loadGame() {
        JFileChooser chooser = fileChooser("Load a saved match");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        Path source = chooser.getSelectedFile().toPath();
        try {
            GameSession loaded = SaveStore.load(source); startSession(loaded);
            savePath = source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".qsave") ? source : null;
        } catch (IOException | IllegalArgumentException exception) {
            showFileError("Couldn't load this match", "Choose a valid Quoridor .qsave or compatible old .G3M file.");
        }
    }

    private JFileChooser fileChooser(String title) {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Quoridor matches (*.qsave, *.G3M)", "qsave", "G3M"));
        if (savePath != null && savePath.toAbsolutePath().getParent() != null) {
            chooser.setCurrentDirectory(savePath.toAbsolutePath().getParent().toFile());
        }
        return chooser;
    }

    private void showFileError(String title, String message) {
        JOptionPane.showMessageDialog(this, message + (session == null ? "" : "\nYour current match is still available."),
                title, JOptionPane.ERROR_MESSAGE);
    }

    private void refresh() {
        if (game == null) { return; }
        GameConfig config = session.config();
        boolean finished = game.winner() >= 0;
        int active = finished ? game.winner() : game.currentPlayer();
        String player = active == 0 ? config.blueName() : config.greenName();
        turn.setText(finished ? player + " wins!" : botBusy ? player + " is thinking…" : player + " to move");
        turn.setToolTipText(turn.getText()); turn.setForeground(active == 0 ? Theme.BLUE : Theme.GREEN);
        blueWalls.setText(game.wallsRemaining(0) + " walls remaining"); greenWalls.setText(game.wallsRemaining(1) + " walls remaining");
        blueCard.update(config.blueName(), active == 0); greenCard.update(config.greenName(), active == 1);
        matchLabel.setText(config.mode() == GameConfig.Mode.LOCAL ? "LOCAL TWO-PLAYER MATCH" : "VERSUS ROBOT  /  " + config.difficulty().name());
        horizontal.setEnabled(humanCanPlay()); vertical.setEnabled(humanCanPlay());
        status.setForeground(finished ? Theme.GOLD : Theme.MUTED);
        status.setText(finished ? player + " reached the far edge. Choose New game for another match."
                : botBusy ? "The robot is considering its next move…"
                : "Click a glowing square to move, or choose a wall direction and click a slot.");
        Set<GameState.Cell> moves = game.legalMoves();
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                GameState.Cell cell = new GameState.Cell(row, col);
                CellButton button = cells[row][col];
                button.occupant = cell.equals(game.playerPosition(0)) ? 0 : cell.equals(game.playerPosition(1)) ? 1 : -1;
                button.legal = moves.contains(cell) && humanCanPlay(); button.setEnabled(button.legal);
                String name = button.occupant == 0 ? config.blueName() + " pawn" : button.occupant == 1 ? config.greenName() + " pawn"
                        : button.legal ? "Move here" : "Square";
                button.setToolTipText(name + " (" + (row + 1) + ", " + (col + 1) + ")");
                button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            }
        }
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                SlotButton slot = slots[row][col];
                slot.setEnabled(humanCanPlay() && game.canPlaceWall(row, col, orientation));
                slot.setVisible(!game.hasWall(row, col, GameState.Orientation.HORIZONTAL) && !game.hasWall(row, col, GameState.Orientation.VERTICAL));
                slot.setToolTipText("Place " + orientation.name().toLowerCase(Locale.ROOT) + " wall (" + (row + 1) + ", " + (col + 1) + ")");
                slot.getAccessibleContext().setAccessibleName(slot.getToolTipText());
            }
        }
        surface.repaint();
    }

    private static JPanel column() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel;
    }

    private static JPanel row(JComponent first, JComponent second) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 9, 0)); panel.setOpaque(false); panel.add(first); panel.add(second); return panel;
    }

    private static Theme.Button button(String text, String name, Runnable action) {
        Theme.Button button = new Theme.Button(text, false); button.setName(name); button.addActionListener(event -> action.run()); return button;
    }

    private static void fixedHeight(JComponent component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(component.getPreferredSize().width, height));
        component.setMinimumSize(new Dimension(20, height));
    }

    private static void addLeft(JPanel panel, JComponent component) { component.setAlignmentX(0f); panel.add(component); }

    private static JPanel fieldGroup(JLabel label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 9)); panel.setOpaque(false);
        panel.add(label, BorderLayout.NORTH); panel.add(field, BorderLayout.CENTER); return panel;
    }

    private static JTextField nameField(String value, String name) {
        JTextField field = new JTextField(value); field.setName(name); field.setFont(Theme.font(Font.PLAIN, 15));
        field.setForeground(Theme.TEXT); field.setDisabledTextColor(Theme.MUTED); field.setBackground(Theme.BACKGROUND);
        field.setCaretColor(Theme.GOLD); field.setSelectionColor(new Color(43, 97, 137));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(9, 11, 9, 11)));
        field.getAccessibleContext().setAccessibleName(name.equals("blue-name") ? "Blue player name" : "Green player name");
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException { replace(bypass, offset, 0, text, attributes); }
            @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
                String before = bypass.getDocument().getText(0, bypass.getDocument().getLength());
                String candidate = before.substring(0, offset) + (text == null ? "" : text) + before.substring(offset + length);
                if (candidate.codePointCount(0, candidate.length()) <= 24 && candidate.codePoints().noneMatch(Character::isISOControl)) {
                    super.replace(bypass, offset, length, text, attributes);
                }
            }
        });
        return field;
    }

    private static JPanel backgroundPanel() {
        return new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
                g.setPaint(new GradientPaint(0, 0, new Color(22, 35, 55), getWidth(), getHeight(), Theme.BACKGROUND)); g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(86, 158, 255, 7)); g.fillOval(-260, -320, 950, 950); g.dispose();
            }
        };
    }

    private static final class DifficultyRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(value == BotPlayer.Difficulty.EASY ? "Easy — a relaxed challenge" : value == BotPlayer.Difficulty.HARD
                    ? "Hard — think a few moves ahead" : "Medium — a worthy opponent");
            label.setFont(Theme.font(Font.PLAIN, 13)); label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            label.setForeground(Theme.TEXT); label.setBackground(selected ? new Color(44, 70, 92) : Theme.RAISED); return label;
        }
    }

    private static final class WrappedLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        WrappedLabel(int size, int style, Color color) {
            putClientProperty("html.disable", Boolean.TRUE); setFont(Theme.font(style, size)); setForeground(color);
        }
        @Override protected void paintComponent(Graphics graphics) {
            String text = getText(); if (text == null || text.isEmpty() || getWidth() < 2) { return; }
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g); g.setColor(getForeground());
            AttributedString attributed = new AttributedString(text); attributed.addAttribute(TextAttribute.FONT, getFont());
            LineBreakMeasurer lines = new LineBreakMeasurer(attributed.getIterator(), g.getFontRenderContext());
            float y = 0;
            while (lines.getPosition() < text.length()) {
                TextLayout line = lines.nextLayout(getWidth()); y += line.getAscent(); line.draw(g, 0, y); y += line.getDescent() + line.getLeading() + 2;
            }
            g.dispose();
        }
    }

    private final class PlayerCard extends Theme.Card {
        private static final long serialVersionUID = 1L;
        private final int player;
        private final WrappedLabel name = new WrappedLabel(14, Font.BOLD, Theme.TEXT);
        PlayerCard(int player, JLabel walls) {
            super(Theme.PANEL); this.player = player; setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            add(new JLabel(new Theme.PawnIcon(player == 0 ? Theme.BLUE : Theme.GREEN, 34)), BorderLayout.WEST);
            JPanel labels = new JPanel(new BorderLayout(0, 0)); labels.setOpaque(false);
            name.setPreferredSize(new Dimension(180, 34)); labels.add(name, BorderLayout.CENTER);
            JPanel details = column(); details.add(walls); details.add(Theme.label(player == 0 ? "Goal: top edge ↑" : "Goal: bottom edge ↓", Font.PLAIN, 10, Theme.MUTED));
            labels.add(details, BorderLayout.SOUTH); add(labels, BorderLayout.CENTER);
        }
        void update(String text, boolean active) { name.setText(text); name.setToolTipText(text); accent(active ? player == 0 ? Theme.BLUE : Theme.GREEN : Theme.LINE); }
    }

    private static final class CellButton extends JButton {
        private static final long serialVersionUID = 1L;
        private int occupant = -1;
        private boolean legal;
        CellButton() {
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
            Color base = legal ? new Color(244, 219, 172) : Theme.WOOD;
            if (legal && getModel().isRollover()) { base = new Color(255, 232, 187); }
            g.setColor(new Color(76, 47, 27, 70)); g.fillRoundRect(0, 3, getWidth(), getHeight() - 3, 8, 8);
            g.setPaint(new GradientPaint(0, 0, Theme.blend(base, Color.WHITE, 0.12f), 0, getHeight(), base));
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 3, 8, 8);
            if (legal) {
                g.setStroke(new BasicStroke(isFocusOwner() ? 2.5f : 1.6f)); g.setColor(new Color(255, 237, 191));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 5, 7, 7);
            }
            if (occupant >= 0) {
                int size = Math.max(20, getWidth() - 12);
                new Theme.PawnIcon(occupant == 0 ? Theme.BLUE : Theme.GREEN, size).paintIcon(this, g, (getWidth() - size) / 2, (getHeight() - size) / 2 - 1);
            } else if (legal) {
                int size = Math.max(7, getWidth() / 5); g.setColor(new Color(139, 91, 41, 175));
                g.fillOval((getWidth() - size) / 2, (getHeight() - size) / 2 - 1, size, size);
            }
            g.dispose();
        }
    }

    private static final class SlotButton extends JButton {
        private static final long serialVersionUID = 1L;
        SlotButton() {
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
            g.setColor(isEnabled() ? isFocusOwner() || getModel().isRollover() ? Theme.GOLD : new Color(240, 213, 174, 145) : new Color(93, 63, 39, 90));
            int inset = isFocusOwner() ? 1 : 3;
            g.fillOval(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2); g.dispose();
        }
    }

    private final class PlayingSurface extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final double BASE = 536;
        private static final double CELL = 44;
        private static final double GAP = 10;
        private static final double STEP = CELL + GAP;
        private static final double PAD = 30;
        private double scale = 1;
        private int originX;
        private int originY;

        PlayingSurface() {
            setLayout(null); setOpaque(false); setPreferredSize(new Dimension((int) BASE, (int) BASE));
            for (int row = 0; row < GameState.SIZE; row++) {
                for (int col = 0; col < GameState.SIZE; col++) {
                    final int r = row; final int c = col;
                    CellButton button = new CellButton(); button.setName("cell-" + row + "-" + col);
                    button.addActionListener(event -> movePawn(r, c)); cells[row][col] = button; add(button);
                }
            }
            for (int row = 0; row < GameState.SIZE - 1; row++) {
                for (int col = 0; col < GameState.SIZE - 1; col++) {
                    final int r = row; final int c = col;
                    SlotButton slot = new SlotButton(); slot.setName("wall-" + row + "-" + col);
                    slot.addActionListener(event -> placeWall(r, c));
                    slot.addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent event) { showPreview(slot, r, c); }
                        @Override public void mouseExited(MouseEvent event) { preview = null; repaint(); }
                    });
                    slot.addFocusListener(new FocusAdapter() {
                        @Override public void focusGained(FocusEvent event) { showPreview(slot, r, c); }
                        @Override public void focusLost(FocusEvent event) { preview = null; repaint(); }
                    });
                    slots[row][col] = slot; add(slot);
                }
            }
        }

        private void showPreview(SlotButton slot, int row, int col) { preview = slot.isEnabled() ? new GameState.Cell(row, col) : null; repaint(); }

        @Override public void doLayout() {
            int size = Math.min(getWidth(), getHeight()); scale = size / BASE;
            originX = (getWidth() - size) / 2; originY = (getHeight() - size) / 2;
            for (int row = 0; row < GameState.SIZE; row++) {
                for (int col = 0; col < GameState.SIZE; col++) { cells[row][col].setBounds(x(PAD + col * STEP), y(PAD + row * STEP), px(CELL), px(CELL)); }
            }
            for (int row = 0; row < GameState.SIZE - 1; row++) {
                for (int col = 0; col < GameState.SIZE - 1; col++) { slots[row][col].setBounds(x(PAD + col * STEP + CELL), y(PAD + row * STEP + CELL), px(GAP), px(GAP)); }
            }
        }

        private int px(double value) { return (int) Math.round(value * scale); }
        private int x(double value) { return originX + px(value); }
        private int y(double value) { return originY + px(value); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
            g.setColor(new Color(0, 0, 0, 60)); g.fillRoundRect(x(2), y(5), px(BASE - 4), px(BASE - 5), px(22), px(22));
            g.setPaint(new GradientPaint(x(0), y(0), new Color(163, 117, 72), x(BASE), y(BASE), new Color(121, 81, 49)));
            g.fillRoundRect(x(0), y(0), px(BASE), px(BASE - 5), px(22), px(22));
            g.setColor(new Color(240, 204, 156, 100)); g.drawRoundRect(x(3), y(3), px(BASE - 7), px(BASE - 12), px(20), px(20));
            g.setColor(new Color(63, 39, 24, 70)); g.fillRoundRect(x(PAD - 8), y(PAD - 8), px(BASE - PAD * 2 + 16), px(BASE - PAD * 2 + 16), 10, 10);
            g.setFont(Theme.font(Font.BOLD, Math.max(9, px(9))));
            String top = "BLUE'S GOAL"; String bottom = "GREEN'S GOAL";
            g.setColor(new Color(201, 224, 255)); g.drawString(top, x(BASE / 2) - g.getFontMetrics().stringWidth(top) / 2, y(18));
            g.setColor(new Color(164, 245, 220)); g.drawString(bottom, x(BASE / 2) - g.getFontMetrics().stringWidth(bottom) / 2, y(BASE - 13));
            g.setStroke(new BasicStroke(Math.max(2f, px(3))));
            g.setColor(Theme.BLUE); g.drawLine(x(PAD), y(22), x(BASE - PAD), y(22));
            g.setColor(Theme.GREEN); g.drawLine(x(PAD), y(BASE - 26), x(BASE - PAD), y(BASE - 26));
            if (game != null) {
                for (int row = 0; row < GameState.SIZE - 1; row++) {
                    for (int col = 0; col < GameState.SIZE - 1; col++) {
                        for (GameState.Orientation direction : GameState.Orientation.values()) {
                            if (game.hasWall(row, col, direction)) { drawWall(g, row, col, direction, new Color(73, 43, 26), false); }
                        }
                    }
                }
            }
            if (preview != null) { drawWall(g, preview.row(), preview.col(), orientation, Theme.GOLD, true); }
            g.dispose();
        }

        private void drawWall(Graphics2D g, int row, int col, GameState.Orientation direction, Color color, boolean ghost) {
            int left = x(PAD + col * STEP); int top = y(PAD + row * STEP);
            int width = px(direction == GameState.Orientation.HORIZONTAL ? CELL * 2 + GAP : GAP - 2);
            int height = px(direction == GameState.Orientation.HORIZONTAL ? GAP - 2 : CELL * 2 + GAP);
            left += direction == GameState.Orientation.HORIZONTAL ? 0 : px(CELL + 1);
            top += direction == GameState.Orientation.HORIZONTAL ? px(CELL + 1) : 0;
            g.setColor(color); g.fillRoundRect(left, top, width, height, px(5), px(5));
            g.setColor(ghost ? new Color(255, 239, 200) : new Color(179, 125, 72));
            g.drawRoundRect(left + 1, top + 1, Math.max(1, width - 3), Math.max(1, height - 3), px(4), px(4));
        }
    }

    private static final class HeroBoard extends JComponent {
        private static final long serialVersionUID = 1L;
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
            double factor = Math.min(getWidth() / 340.0, getHeight() / 280.0);
            g.translate((getWidth() - 340 * factor) / 2, (getHeight() - 280 * factor) / 2); g.scale(factor, factor); g.rotate(-0.09, 170, 145);
            g.setColor(new Color(0, 0, 0, 45)); g.fillRoundRect(37, 21, 268, 249, 25, 25);
            g.setPaint(new GradientPaint(35, 17, new Color(185, 139, 90), 300, 250, new Color(113, 76, 48))); g.fillRoundRect(30, 12, 268, 249, 22, 22);
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) { g.setColor((row + col) % 2 == 0 ? new Color(216, 182, 137) : new Color(205, 166, 119)); g.fillRoundRect(47 + col * 47, 29 + row * 44, 39, 36, 6, 6); }
            }
            g.setColor(new Color(64, 42, 29)); g.fillRoundRect(88, 73, 7, 80, 4, 4); g.fillRoundRect(142, 153, 86, 7, 4, 4);
            new Theme.PawnIcon(Theme.BLUE, 32).paintIcon(this, g, 145, 207); new Theme.PawnIcon(Theme.GREEN, 32).paintIcon(this, g, 192, 75); g.dispose();
        }
    }
}
