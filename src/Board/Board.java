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
import java.util.ArrayList;
import java.util.List;
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
    public enum Appearance { DARK, LIGHT }
    private Appearance appearance = Appearance.DARK;
    private Theme.Palette palette = Theme.DARK;
    private final JComboBox<Appearance> appearanceMenu = new JComboBox<>(Appearance.values());
    private final JComboBox<Appearance> appearanceGame = new JComboBox<>(Appearance.values());
    private boolean changingAppearance;
    private I18n i18n = new I18n(I18n.Language.ENGLISH);
    private final List<Runnable> translationBindings = new ArrayList<>();
    private final JComboBox<I18n.Language> languageMenu = new JComboBox<>(I18n.supportedLanguages());
    private final JComboBox<I18n.Language> languageGame = new JComboBox<>(I18n.supportedLanguages());
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
    private boolean changingLanguage;
    private boolean updatingNames;
    private boolean blueDefaultName = true;
    private boolean greenDefaultName = true;
    private boolean menuBotSelected;
    private String localGreenName = "Green";
    private String statusTemplate;
    private Object[] statusArguments = new Object[0];

    /** Opens the main menu. */
    public Board() { this(null, I18n.fromLocale(Locale.getDefault())); }

    /** Opens a supplied match directly, copying its mutable state. */
    public Board(GameSession initialSession) {
        this(initialSession, I18n.fromLocale(Locale.getDefault()));
    }

    /** Opens a match or menu in an explicitly selected supported language. */
    public Board(GameSession initialSession, I18n.Language language) {
        this(initialSession, language, Appearance.DARK);
    }

    /** Opens a match or menu with independent language and appearance preferences. */
    public Board(GameSession initialSession, I18n.Language language, Appearance selectedAppearance) {
        super("Quoridor — Your next move");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        URL icon = Board.class.getResource("/Files/Images/com.nozhaco.quoridor_512x512.png");
        if (icon != null) { setIconImage(new ImageIcon(icon).getImage()); }
        views.add(createMenu(), "menu");
        views.add(createGameView(), "game");
        views.setBackground(Theme.BACKGROUND);
        setContentPane(views);
        collectTranslationBindings(views);
        watchDefaultName(blueName, true);
        watchDefaultName(greenName, false);
        setLanguage(language);
        setAppearance(selectedAppearance);
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

    public I18n.Language currentLanguage() { return i18n.language(); }

    public Appearance currentAppearance() { return appearance; }

    /** Updates this window's presentation without replacing the live match or widgets. */
    public void setAppearance(Appearance next) {
        Theme.Palette previous = palette;
        appearance = Objects.requireNonNull(next, "appearance");
        palette = next == Appearance.DARK ? Theme.DARK : Theme.LIGHT;
        changingAppearance = true;
        appearanceMenu.setSelectedItem(next);
        appearanceGame.setSelectedItem(next);
        changingAppearance = false;
        Theme.apply(views, previous, palette, false);
        String previousStatus = statusTemplate;
        Object[] previousArguments = statusArguments;
        refresh();
        if (previousStatus != null) { setStatus(previousStatus, previousArguments); }
        views.revalidate();
        views.repaint();
    }

    /** Changes presentation in place; the match and robot search keep their state. */
    public void setLanguage(I18n.Language language) {
        i18n = new I18n(Objects.requireNonNull(language, "language"));
        changingLanguage = true;
        languageMenu.setSelectedItem(language);
        languageGame.setSelectedItem(language);
        changingLanguage = false;
        for (Runnable binding : translationBindings) { binding.run(); }
        setTitle(tr("Quoridor — Your next move"));
        updatingNames = true;
        if (blueDefaultName) { blueName.setText(tr("Blue")); }
        if (botMode.isSelected()) { greenName.setText(tr("Robot")); }
        else if (greenDefaultName) { greenName.setText(tr("Green")); }
        updatingNames = false;
        updateModeText();
        blueName.getAccessibleContext().setAccessibleName(tr("Blue player name"));
        greenName.getAccessibleContext().setAccessibleName(tr("Green player name"));
        difficulty.getAccessibleContext().setAccessibleName(tr("Robot difficulty"));
        difficulty.repaint();
        appearanceMenu.repaint(); appearanceGame.repaint();
        ComponentOrientation direction = ComponentOrientation.getOrientation(language.locale());
        applyComponentOrientation(direction);
        alignColumns(views, language.rightToLeft());
        // Game coordinates are absolute: changing reading direction never mirrors the board.
        surface.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        String previousStatus = statusTemplate;
        Object[] previousArguments = statusArguments;
        refresh();
        if (previousStatus != null) { setStatus(previousStatus, previousArguments); }
        views.revalidate();
        views.repaint();
    }

    private String tr(String englishTemplate, Object... arguments) {
        Object[] formatted = arguments.clone();
        for (int index = 0; index < formatted.length; index++) {
            if (formatted[index] instanceof String text) { formatted[index] = userText(text); }
        }
        return i18n.text(englishTemplate, formatted);
    }

    private String userText(String text) {
        return currentLanguage().rightToLeft() ? "\u2068" + text + "\u2069" : text;
    }

    private void setStatus(String template, Object... arguments) {
        statusTemplate = template;
        statusArguments = arguments.clone();
        status.setText(tr(template, arguments));
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
        hero.add(heroText("MOVE YOUR PAWN. SHAPE THEIR PATH.", Font.BOLD, 11, Theme.GOLD));
        hero.add(Box.createVerticalStrut(9));
        hero.add(heroText("Reach the far edge before your opponent.", Font.PLAIN, 13, Theme.MUTED));
        hero.add(heroText("Place walls, but always leave a way through.", Font.PLAIN, 13, Theme.MUTED));
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
        JPanel language = row(languagePicker(languageMenu, "language-menu"), appearancePicker(appearanceMenu, "appearance-menu"));
        fixedHeight(language, 30); addLeft(setup, language);
        setup.add(Box.createVerticalStrut(12));
        addLeft(setup, Theme.label("LET'S PLAY", Font.BOLD, 11, Theme.GREEN));
        setup.add(Box.createVerticalStrut(7));
        addLeft(setup, Theme.label("Make it your game.", Font.BOLD, 27, Theme.TEXT));
        setup.add(Box.createVerticalStrut(18));
        localMode.setName("local-game"); botMode.setName("bot-game"); localMode.setSelected(true);
        ButtonGroup modes = new ButtonGroup(); modes.add(localMode); modes.add(botMode);
        localMode.addActionListener(event -> updateMode());
        botMode.addActionListener(event -> updateMode());
        JPanel modeButtons = row(localMode, botMode);
        fixedHeight(modeButtons, 42); addLeft(setup, modeButtons);
        setup.add(Box.createVerticalStrut(8)); addLeft(setup, modeHint);
        setup.add(Box.createVerticalStrut(16));
        JPanel names = row(fieldGroup(Theme.label("BLUE PLAYER", Font.BOLD, 11, Theme.MUTED), blueName),
                fieldGroup(opponentLabel, greenName));
        fixedHeight(names, 65); addLeft(setup, names);
        setup.add(Box.createVerticalStrut(14));
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
        fixedHeight(difficultyPanel, 65); addLeft(setup, difficultyPanel);
        setup.add(Box.createVerticalStrut(12));
        addLeft(setup, Theme.label("Blue moves first. Each player has 10 walls.", Font.PLAIN, 12, Theme.MUTED));
        setup.add(Box.createVerticalGlue());
        start.setName("start-game"); start.setMnemonic('S');
        start.addActionListener(event -> beginFromMenu());
        fixedHeight(start, 44); addLeft(setup, start);
        setup.add(Box.createVerticalStrut(8));
        Theme.Button load = button("Load a saved match", "load-game", this::loadGame);
        fixedHeight(load, 40); addLeft(setup, load);
        resume.setName("resume-game"); resume.addActionListener(event -> resumeGame());
        resume.setVisible(false); fixedHeight(resume, 40);
        setup.add(Box.createVerticalStrut(8)); addLeft(setup, resume);
        menu.add(setup, BorderLayout.LINE_END);
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
        header.add(title, BorderLayout.LINE_START);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 2)); actions.setOpaque(false);
        actions.add(languagePicker(languageGame, "language-game"));
        actions.add(button("Save", "save-game", this::saveGame));
        actions.add(button("Load", "load-game-in-game", this::loadGame));
        actions.add(button("Main menu", "back-menu", this::showMenu));
        header.add(actions, BorderLayout.LINE_END);
        content.add(header, BorderLayout.NORTH); content.add(surface, BorderLayout.CENTER);
        content.add(createControls(), BorderLayout.LINE_END);
        status.setName("status"); status.setPreferredSize(new Dimension(850, 34));
        content.add(status, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createControls() {
        JPanel controls = column(); controls.setPreferredSize(new Dimension(264, 500));
        JPanel appearance = appearancePicker(appearanceGame, "appearance-game");
        fixedHeight(appearance, 30); controls.add(appearance);
        controls.add(Box.createVerticalStrut(10));
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
        if (bot != menuBotSelected) {
            updatingNames = true;
            if (bot) { localGreenName = greenName.getText(); greenName.setText(tr("Robot")); }
            else { greenName.setText(greenDefaultName ? tr("Green") : localGreenName); }
            updatingNames = false;
            menuBotSelected = bot;
        }
        updateModeText();
    }

    private void updateModeText() {
        boolean bot = botMode.isSelected();
        opponentLabel.setText(tr(bot ? "YOUR OPPONENT" : "GREEN PLAYER")); greenName.setEnabled(!bot);
        difficultyPanel.setVisible(bot);
        modeHint.setText(tr(bot ? "You play blue. The robot plays green." : "A friendly rivalry, on the same screen."));
        difficultyPanel.getParent().revalidate();
    }

    private void beginFromMenu() {
        try {
            GameConfig config = new GameConfig(blueName.getText(), botMode.isSelected() ? tr("Robot") : greenName.getText(),
                    botMode.isSelected() ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                    (BotPlayer.Difficulty) difficulty.getSelectedItem());
            startSession(new GameSession(new GameState(), config));
        } catch (IllegalArgumentException exception) {
            showMessage("Check player names", tr("Use player names between 1 and 24 characters."), JOptionPane.INFORMATION_MESSAGE);
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
        else { setStatus("That wall cannot be placed. Both players need a route to their goal."); }
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
                    fallbackRobotMove(); afterAction(); setStatus("The robot played a safe move. Your turn.");
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
        if (java.nio.file.Files.exists(target) && !confirmReplace(target.getFileName().toString())) { return; }
        try {
            SaveStore.save(target, currentSession()); savePath = target;
            setStatus("Match saved to %s. You can continue playing.", target.getFileName().toString());
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
        JFileChooser chooser = new LocalizedFileChooser(); chooser.setDialogTitle(tr(title));
        chooser.setFileFilter(new FileNameExtensionFilter(tr("Quoridor matches (*.qsave, *.G3M)"), "qsave", "G3M"));
        if (savePath != null && savePath.toAbsolutePath().getParent() != null) {
            chooser.setCurrentDirectory(savePath.toAbsolutePath().getParent().toFile());
        }
        return chooser;
    }

    private void showFileError(String title, String message) {
        showMessage(title, tr(message) + (session == null ? "" : "\n" + tr("Your current match is still available.")), JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String title, String message, int type) {
        JOptionPane pane = new JOptionPane(message, type, JOptionPane.DEFAULT_OPTION, null, new Object[]{tr("OK")});
        showPane(pane, title);
    }

    private boolean confirmReplace(String filename) {
        String yes = tr("Yes");
        JOptionPane pane = new JOptionPane(tr("Replace the saved match “%s”?", filename), JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION, null, new Object[]{yes, tr("No")}, tr("No"));
        showPane(pane, "Replace saved match");
        return yes.equals(pane.getValue());
    }

    private void showPane(JOptionPane pane, String title) {
        pane.setLocale(i18n.locale());
        pane.applyComponentOrientation(getComponentOrientation());
        Theme.apply(pane, Theme.DARK, palette, true);
        JDialog dialog = pane.createDialog(this, tr(title));
        dialog.setLocale(i18n.locale());
        dialog.applyComponentOrientation(getComponentOrientation());
        dialog.getContentPane().setBackground(palette.panel());
        dialog.setVisible(true);
        dialog.dispose();
    }

    /** A per-window chooser; it never changes Swing's process-wide UI defaults. */
    private final class LocalizedFileChooser extends JFileChooser {
        private static final long serialVersionUID = 1L;
        LocalizedFileChooser() {
            setLocale(i18n.locale());
            setUI(new LocalizedChooserUI(this));
            setAcceptAllFileFilterUsed(false);
            addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override public boolean accept(java.io.File file) { return true; }
                @Override public String getDescription() { return tr("All files"); }
            });
            setFileView(new javax.swing.filechooser.FileView() {
                @Override public String getTypeDescription(java.io.File file) { return tr(file.isDirectory() ? "Folder" : "File"); }
            });
            getAccessibleContext().setAccessibleName(tr("File chooser"));
            applyComponentOrientation(Board.this.getComponentOrientation());
            localizeChooserTree(this);
            addPropertyChangeListener(event -> {
                if (event.getPropertyName().equals(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY)
                        || event.getPropertyName().equals(JFileChooser.DIRECTORY_CHANGED_PROPERTY)) {
                    SwingUtilities.invokeLater(() -> localizeChooserTree(this));
                }
            });
        }
        @Override protected JDialog createDialog(Component parent) {
            JDialog dialog = super.createDialog(parent);
            dialog.setLocale(i18n.locale());
            dialog.applyComponentOrientation(Board.this.getComponentOrientation());
            Theme.apply(dialog.getContentPane(), Theme.DARK, palette, true);
            dialog.getRootPane().setBackground(palette.panel());
            localizeChooserTree(this);
            dialog.pack();
            return dialog;
        }
    }

    private final class LocalizedChooserUI extends javax.swing.plaf.metal.MetalFileChooserUI {
        LocalizedChooserUI(JFileChooser chooser) { super(chooser); }
        @Override protected void installStrings(JFileChooser chooser) {
            super.installStrings(chooser);
            saveButtonText = tr("Save"); openButtonText = tr("Open"); cancelButtonText = tr("Cancel");
            updateButtonText = tr("Update"); helpButtonText = tr("Help"); directoryOpenButtonText = tr("Open folder");
            saveButtonToolTipText = tr("Save selected file"); openButtonToolTipText = tr("Open selected file");
            cancelButtonToolTipText = tr("Cancel file chooser"); updateButtonToolTipText = tr("Update directory listing");
            helpButtonToolTipText = tr("File chooser help"); directoryOpenButtonToolTipText = tr("Open selected directory");
        }
        @Override public void installComponents(JFileChooser chooser) {
            super.installComponents(chooser);
            localizeChooserTree(chooser);
        }
        @Override protected ActionMap createActionMap() {
            ActionMap actions = super.createActionMap();
            AbstractAction selectionOnly = new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent event) { }
            };
            selectionOnly.setEnabled(false);
            actions.put("editFileName", selectionOnly);
            return actions;
        }
        @Override protected JPanel createDetailsView(JFileChooser chooser) {
            JPanel details = super.createDetailsView(chooser);
            if (details != null) { localizeChooserTree(details); }
            return details;
        }
        @Override public Action getNewFolderAction() {
            return new AbstractAction(tr("New folder")) {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent event) {
                    JFileChooser chooser = getFileChooser();
                    Path parent = chooser.getCurrentDirectory().toPath();
                    String name = tr("New folder");
                    Path folder = parent.resolve(name);
                    for (int suffix = 2; java.nio.file.Files.exists(folder); suffix++) {
                        folder = parent.resolve(name + " " + String.format(i18n.locale(), "%d", suffix));
                    }
                    try {
                        java.nio.file.Files.createDirectory(folder);
                        chooser.rescanCurrentDirectory();
                        chooser.setCurrentDirectory(folder.toFile());
                    } catch (IOException | SecurityException exception) {
                        showMessage("Error creating new folder", tr("Choose a writable folder and try again."), JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
        }
    }

    private void localizeChooserTree(Component root) {
        Set<Component> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        localizeChooserTree(root, visited);
    }

    private void localizeChooserTree(Component component, Set<Component> visited) {
        if (!visited.add(component)) { return; }
        component.setLocale(i18n.locale());
        component.setComponentOrientation(Board.this.getComponentOrientation());
        Theme.applyOne(component, Theme.DARK, palette, true);
        if (component instanceof JLabel label && !(label.getParent() instanceof CellRendererPane)) {
            label.setText(chooserText(label.getText()));
        }
        if (component instanceof AbstractButton button) { button.setText(chooserText(button.getText())); }
        if (component instanceof JComponent child) {
            child.setToolTipText(chooserText(child.getToolTipText()));
            JPopupMenu popup = child.getComponentPopupMenu();
            if (popup != null) { localizeChooserTree(popup, visited); }
        }
        if (component instanceof JMenu menu) {
            for (Component child : menu.getMenuComponents()) { localizeChooserTree(child, visited); }
        }
        if (component instanceof JTable table) {
            table.setDefaultEditor(Object.class, null);
            table.setDefaultEditor(String.class, null);
            table.setDefaultEditor(java.io.File.class, null);
            for (int index = 0; index < table.getColumnCount(); index++) {
                javax.swing.table.TableColumn column = table.getColumnModel().getColumn(index);
                column.setCellEditor(null);
                column.setHeaderValue(chooserText(String.valueOf(column.getHeaderValue())));
            }
            table.getTableHeader().repaint();
        }
        if (component instanceof JList<?> list) {
            list.getAccessibleContext().setAccessibleName(tr("Files List"));
            keepFileSelectionOnly(list);
        }
        if (component instanceof JTable table) { table.getAccessibleContext().setAccessibleName(tr("Files Details")); }
        if (component instanceof Container parent) {
            if (parent instanceof JComponent child && child.getClientProperty("localized.children") == null) {
                child.putClientProperty("localized.children", Boolean.TRUE);
                parent.addContainerListener(new ContainerAdapter() {
                    @Override public void componentAdded(ContainerEvent event) { localizeChooserTree(event.getChild()); }
                });
            }
            for (Component child : parent.getComponents()) { localizeChooserTree(child, visited); }
        }
    }

    private static void keepFileSelectionOnly(JList<?> list) {
        if (list.getClientProperty("selection.only") != null) { return; }
        list.putClientProperty("selection.only", Boolean.TRUE);
        // Selection is handled on press/release. A slow second click would otherwise
        // start the JDK's inline renamer, whose error dialogs cannot be localized per window.
        // Double-click folder navigation and all normal selection events still reach the UI.
        for (MouseListener listener : list.getMouseListeners()) {
            list.removeMouseListener(listener);
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() != 1) { listener.mouseClicked(event); }
                }
                @Override public void mousePressed(MouseEvent event) { listener.mousePressed(event); }
                @Override public void mouseReleased(MouseEvent event) { listener.mouseReleased(event); }
                @Override public void mouseEntered(MouseEvent event) { listener.mouseEntered(event); }
                @Override public void mouseExited(MouseEvent event) { listener.mouseExited(event); }
            });
        }
    }

    private String chooserText(String original) {
        if (original == null || original.isEmpty()) { return original; }
        String key = switch (original.toLowerCase(Locale.ROOT)) {
            case "look in:" -> "Look in:";
            case "save in:" -> "Save in:";
            case "file name:" -> "File name:";
            case "files of type:" -> "Files of type:";
            case "all files" -> "All files";
            case "up one level" -> "Up one level";
            case "create new folder" -> "Create new folder";
            case "new folder" -> "New folder";
            case "filechooser help" -> "File chooser help";
            case "abort file chooser dialog" -> "Cancel file chooser";
            case "open selected directory" -> "Open selected directory";
            case "open selected file" -> "Open selected file";
            case "save selected file" -> "Save selected file";
            case "update directory listing" -> "Update directory listing";
            case "name" -> "Name";
            case "size" -> "Size";
            case "type" -> "Type";
            case "modified" -> "Modified";
            case "attributes" -> "Attributes";
            case "open" -> "Open";
            case "save" -> "Save";
            case "cancel" -> "Cancel";
            case "help" -> "Help";
            case "update" -> "Update";
            case "home" -> "Home";
            case "list" -> "List";
            case "details" -> "Details";
            case "view" -> "View";
            case "refresh" -> "Refresh";
            default -> null;
        };
        return key == null ? original : tr(key);
    }

    private void refresh() {
        if (game == null) { return; }
        GameConfig config = session.config();
        boolean finished = game.winner() >= 0;
        int active = finished ? game.winner() : game.currentPlayer();
        String player = active == 0 ? config.blueName() : config.greenName();
        turn.setText(tr(finished ? "%s wins!" : botBusy ? "%s is thinking…" : "%s to move", player));
        turn.setToolTipText(turn.getText()); turn.setForeground(active == 0 ? palette.blue() : palette.green());
        blueWalls.setText(tr("%d walls remaining", game.wallsRemaining(0))); greenWalls.setText(tr("%d walls remaining", game.wallsRemaining(1)));
        blueCard.update(config.blueName(), active == 0); greenCard.update(config.greenName(), active == 1);
        matchLabel.setText(config.mode() == GameConfig.Mode.LOCAL ? tr("LOCAL TWO-PLAYER MATCH") : tr("VERSUS ROBOT  /  %s", tr(config.difficulty().name())));
        horizontal.setEnabled(humanCanPlay()); vertical.setEnabled(humanCanPlay());
        status.setForeground(finished ? palette.gold() : palette.muted());
        if (finished) { setStatus("%s reached the far edge. Choose New game for another match.", player); }
        else { setStatus(botBusy ? "The robot is considering its next move…"
                : "Click a glowing square to move, or choose a wall direction and click a slot."); }
        Set<GameState.Cell> moves = game.legalMoves();
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                GameState.Cell cell = new GameState.Cell(row, col);
                CellButton button = cells[row][col];
                button.occupant = cell.equals(game.playerPosition(0)) ? 0 : cell.equals(game.playerPosition(1)) ? 1 : -1;
                button.legal = moves.contains(cell) && humanCanPlay(); button.setEnabled(button.legal);
                String name = button.occupant == 0 ? tr("%s pawn", config.blueName()) : button.occupant == 1 ? tr("%s pawn", config.greenName())
                        : tr(button.legal ? "Move here" : "Square");
                button.setToolTipText(tr("%s (%d, %d)", name, row + 1, col + 1));
                button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            }
        }
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                SlotButton slot = slots[row][col];
                slot.setEnabled(humanCanPlay() && game.canPlaceWall(row, col, orientation));
                slot.setVisible(!game.hasWall(row, col, GameState.Orientation.HORIZONTAL) && !game.hasWall(row, col, GameState.Orientation.VERTICAL));
                slot.setToolTipText(tr("Place %s wall (%d, %d)", tr(orientation.name().toLowerCase(Locale.ROOT)), row + 1, col + 1));
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

    private JPanel languagePicker(JComboBox<I18n.Language> selector, String name) {
        selector.setName(name);
        selector.setFont(Theme.font(Font.PLAIN, 12));
        selector.setBackground(Theme.RAISED);
        selector.setForeground(Theme.TEXT);
        selector.setPreferredSize(new Dimension(108, 30));
        selector.setRenderer(new LanguageRenderer());
        selector.addActionListener(event -> {
            if (!changingLanguage) { setLanguage((I18n.Language) selector.getSelectedItem()); }
        });
        selector.getAccessibleContext().setAccessibleName(tr("Language"));
        translationBindings.add(() -> selector.getAccessibleContext().setAccessibleName(tr("Language")));
        JPanel panel = new JPanel(new BorderLayout(7, 0));
        panel.setOpaque(false);
        JLabel label = Theme.label("Language", Font.PLAIN, 11, Theme.MUTED);
        label.putClientProperty("selector.label", Boolean.TRUE);
        panel.add(label, BorderLayout.LINE_START);
        panel.add(selector, BorderLayout.LINE_END);
        return panel;
    }

    private JPanel appearancePicker(JComboBox<Appearance> selector, String name) {
        selector.setName(name);
        selector.setFont(Theme.font(Font.PLAIN, 12));
        selector.setBackground(Theme.RAISED); selector.setForeground(Theme.TEXT);
        selector.setPreferredSize(new Dimension(100, 30));
        selector.setRenderer(new AppearanceRenderer());
        selector.addActionListener(event -> {
            if (!changingAppearance) { setAppearance((Appearance) selector.getSelectedItem()); }
        });
        translationBindings.add(() -> selector.getAccessibleContext().setAccessibleName(tr("Appearance")));
        JPanel panel = new JPanel(new BorderLayout(7, 0)); panel.setOpaque(false);
        JLabel label = Theme.label("Appearance", Font.PLAIN, 11, Theme.MUTED);
        label.putClientProperty("selector.label", Boolean.TRUE);
        panel.add(label, BorderLayout.LINE_START);
        panel.add(selector, BorderLayout.LINE_END);
        return panel;
    }

    private void collectTranslationBindings(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JLabel label && component != turn && component != status && component != modeHint
                    && component != opponentLabel && component != matchLabel && label.getText() != null && !label.getText().isEmpty()) {
                String key = label.getText();
                int originalSize = label.getFont().getSize();
                translationBindings.add(() -> {
                    label.setText(tr(key));
                    if (label.getClientProperty("selector.label") != null) {
                        label.setPreferredSize(null);
                        Dimension natural = label.getPreferredSize();
                        label.setPreferredSize(new Dimension(natural.width + 12, natural.height));
                    }
                    if (originalSize == 48 || originalSize == 27) {
                        label.setFont(Theme.font(Font.BOLD, currentLanguage().rightToLeft() ? originalSize == 48 ? 31 : 21 : originalSize));
                    }
                    if (label.getMaximumSize().width == Integer.MAX_VALUE) {
                        int height = label.getClientProperty("hero.text") != null
                                ? currentLanguage().rightToLeft() ? 34 : 20 : label.getPreferredSize().height;
                        label.setPreferredSize(new Dimension(label.getPreferredSize().width, height));
                        label.setMinimumSize(new Dimension(20, height));
                        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                    }
                });
            } else if (component instanceof AbstractButton button && button.getText() != null && !button.getText().isEmpty()) {
                String key = button.getText();
                translationBindings.add(() -> button.setText(tr(key)));
            }
            if (component instanceof Container child) { collectTranslationBindings(child); }
        }
    }

    private void watchDefaultName(JTextField field, boolean blue) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (!updatingNames) {
                    if (blue) { blueDefaultName = false; }
                    else { greenDefaultName = false; }
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }

    private void alignColumns(Container parent, boolean rightToLeft) {
        parent.setLocale(i18n.locale());
        boolean column = parent.getLayout() instanceof BoxLayout box && box.getAxis() == BoxLayout.Y_AXIS;
        for (Component component : parent.getComponents()) {
            component.setLocale(i18n.locale());
            if (column && component instanceof JComponent child) { child.setAlignmentX(rightToLeft ? 1f : 0f); }
            if (component instanceof JLabel label && label.getText() != null) { label.setHorizontalAlignment(SwingConstants.LEADING); }
            if (component instanceof JTextField field) { field.setHorizontalAlignment(SwingConstants.LEADING); }
            if (component instanceof Container child) { alignColumns(child, rightToLeft); }
        }
    }

    private static void fixedHeight(JComponent component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(component.getPreferredSize().width, height));
        component.setMinimumSize(new Dimension(20, height));
    }

    private static WrappedLabel heroText(String text, int style, int size, Color color) {
        WrappedLabel label = new WrappedLabel(size, style, color);
        label.setText(text);
        label.putClientProperty("hero.text", Boolean.TRUE);
        fixedHeight(label, 20);
        return label;
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

    private JPanel backgroundPanel() {
        JPanel panel = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
                g.setPaint(new GradientPaint(0, 0, Theme.blend(palette.background(), palette.raised(), 0.35f), getWidth(), getHeight(), palette.background())); g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(86, 158, 255, 7)); g.fillOval(-260, -320, 950, 950); g.dispose();
            }
        };
        panel.setBackground(Theme.BACKGROUND);
        return panel;
    }

    private final class DifficultyRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(tr(value == BotPlayer.Difficulty.EASY ? "Easy — a relaxed challenge" : value == BotPlayer.Difficulty.HARD
                    ? "Hard — think a few moves ahead" : "Medium — a worthy opponent"));
            label.applyComponentOrientation(Board.this.getComponentOrientation());
            label.setHorizontalAlignment(SwingConstants.LEADING);
            label.setFont(Theme.font(Font.PLAIN, 13)); label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            label.setForeground(palette.text()); label.setBackground(selected ? palette.selection() : palette.raised()); return label;
        }
    }

    private final class LanguageRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setFont(Theme.font(Font.PLAIN, 12));
            label.applyComponentOrientation(Board.this.getComponentOrientation());
            label.setHorizontalAlignment(SwingConstants.LEADING);
            label.setForeground(palette.text());
            label.setBackground(selected ? palette.selection() : palette.raised());
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }

    private final class AppearanceRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(tr(value == Appearance.LIGHT ? "Light" : "Dark"));
            label.setFont(Theme.font(Font.PLAIN, 12)); label.setHorizontalAlignment(SwingConstants.LEADING);
            label.applyComponentOrientation(Board.this.getComponentOrientation());
            label.setForeground(palette.text()); label.setBackground(selected ? palette.selection() : palette.raised());
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6)); return label;
        }
    }

    private static final class WrappedLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        WrappedLabel(int size, int style, Color color) {
            putClientProperty("html.disable", Boolean.TRUE); setFont(Theme.font(style, size)); setForeground(color);
        }
        @Override public JToolTip createToolTip() { return plainToolTip(this); }
        @Override protected void paintComponent(Graphics graphics) {
            String text = getText(); if (text == null || text.isEmpty() || getWidth() < 2) { return; }
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g); g.setColor(getForeground());
            AttributedString attributed = new AttributedString(text); attributed.addAttribute(TextAttribute.FONT, getFont());
            attributed.addAttribute(TextAttribute.RUN_DIRECTION, getComponentOrientation().isLeftToRight()
                    ? TextAttribute.RUN_DIRECTION_LTR : TextAttribute.RUN_DIRECTION_RTL);
            LineBreakMeasurer lines = new LineBreakMeasurer(attributed.getIterator(), g.getFontRenderContext());
            float y = 0;
            while (lines.getPosition() < text.length()) {
                TextLayout line = lines.nextLayout(getWidth()); y += line.getAscent();
                line.draw(g, getComponentOrientation().isLeftToRight() ? 0 : getWidth() - line.getAdvance(), y);
                y += line.getDescent() + line.getLeading() + 2;
            }
            g.dispose();
        }
    }

    private final class PlayerCard extends Theme.Card {
        private static final long serialVersionUID = 1L;
        private final int player;
        private final WrappedLabel name = new WrappedLabel(14, Font.BOLD, Theme.TEXT);
        private final JLabel pawn = new JLabel();
        PlayerCard(int player, JLabel walls) {
            super(Theme.PANEL); this.player = player; setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            pawn.setIcon(new Theme.PawnIcon(player == 0 ? palette.blue() : palette.green(), 34));
            add(pawn, BorderLayout.LINE_START);
            JPanel labels = new JPanel(new BorderLayout(0, 0)); labels.setOpaque(false);
            name.setPreferredSize(new Dimension(180, 34)); labels.add(name, BorderLayout.CENTER);
            JPanel details = column(); details.add(walls); details.add(Theme.label(player == 0 ? "Goal: top edge ↑" : "Goal: bottom edge ↓", Font.PLAIN, 10, Theme.MUTED));
            labels.add(details, BorderLayout.SOUTH); add(labels, BorderLayout.CENTER);
        }
        void update(String text, boolean active) {
            name.setText(text); name.setToolTipText(text);
            pawn.setIcon(new Theme.PawnIcon(player == 0 ? palette.blue() : palette.green(), 34));
            accent(active ? player == 0 ? palette.blue() : palette.green() : palette.line());
        }
    }

    private static final class CellButton extends JButton {
        private static final long serialVersionUID = 1L;
        private int occupant = -1;
        private boolean legal;
        CellButton() {
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public JToolTip createToolTip() { return plainToolTip(this); }
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
        @Override public JToolTip createToolTip() { return plainToolTip(this); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); Theme.smooth(g);
            g.setColor(isEnabled() ? isFocusOwner() || getModel().isRollover() ? Theme.GOLD : new Color(240, 213, 174, 145) : new Color(93, 63, 39, 90));
            int inset = isFocusOwner() ? 1 : 3;
            g.fillOval(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2); g.dispose();
        }
    }

    private static JToolTip plainToolTip(JComponent owner) {
        JToolTip tooltip = new JToolTip();
        tooltip.putClientProperty("html.disable", Boolean.TRUE);
        tooltip.setComponent(owner);
        tooltip.setComponentOrientation(owner.getComponentOrientation());
        Theme.Palette colors = Theme.palette(owner);
        tooltip.setBackground(colors.raised()); tooltip.setForeground(colors.text());
        tooltip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(colors.line()), BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        return tooltip;
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
            String top = tr("BLUE'S GOAL"); String bottom = tr("GREEN'S GOAL");
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
