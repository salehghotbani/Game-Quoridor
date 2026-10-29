package Board;

import Board.BoardWidgets.CellButton;
import Board.BoardWidgets.HeroBoard;
import Board.BoardWidgets.SlotButton;
import Board.BoardWidgets.WrappedLabel;

import Online.OnlineState;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Desktop match setup, board, robot lifecycle, and saved-game entry points. */
public final class Board extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final String MENU_VIEW = "menu";
    private static final String GAME_VIEW = "game";
    private static final String SAVE_EXTENSION = ".qsave";
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 750;
    private static final int MINIMUM_WINDOW_WIDTH = 920;
    private static final int MINIMUM_WINDOW_HEIGHT = 710;

    public enum Appearance {
        DARK,
        LIGHT
    }

    private Appearance appearance = Appearance.DARK;
    private Theme.Palette palette = Theme.DARK;
    private final JComboBox<Appearance> appearanceMenu = new JComboBox<>(Appearance.values());
    private final JComboBox<Appearance> appearanceGame = new JComboBox<>(Appearance.values());
    private boolean changingAppearance;
    private I18n i18n = new I18n(I18n.Language.ENGLISH);
    private final List<Runnable> translationBindings = new ArrayList<>();
    private final JComboBox<I18n.Language> languageMenu =
            new JComboBox<>(I18n.supportedLanguages());
    private final JComboBox<I18n.Language> languageGame =
            new JComboBox<>(I18n.supportedLanguages());
    private final CardLayout navigation = new CardLayout();
    private final JPanel views = new JPanel(navigation);
    private final CellButton[][] cells = new CellButton[GameState.SIZE][GameState.SIZE];
    private final SlotButton[][] slots = new SlotButton[GameState.SIZE - 1][GameState.SIZE - 1];
    private final JTextField blueName = nameField("Blue", "blue-name");
    private final JTextField greenName = nameField("Green", "green-name");
    private final JTextField redName = nameField("Red", "red-name");
    private final JTextField goldName = nameField("Gold", "gold-name");
    private final JComboBox<Integer> playerCount = new JComboBox<>(new Integer[] {2, 4});
    private JPanel playerCountPanel;
    private final JPanel extraNames = new JPanel(new GridLayout(1, 2, 12, 0));
    private final Theme.Toggle localMode = new Theme.Toggle("Two players");
    private final Theme.Toggle botMode = new Theme.Toggle("Play a robot");
    private final JComboBox<BotPlayer.Difficulty> difficulty =
            new JComboBox<>(BotPlayer.Difficulty.values());
    private final JLabel modeHint = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel ruleHint = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel opponentLabel = Theme.label("GREEN PLAYER", Font.BOLD, 11, Theme.MUTED);
    private final JPanel difficultyPanel = new JPanel(new BorderLayout(0, 9));
    private final Theme.Button resume = new Theme.Button("Resume your match", false);
    private final Theme.Button start = new Theme.Button("Start a new match  →", true);
    private final WrappedLabel turn = new WrappedLabel(17, Font.BOLD, Theme.TEXT);
    private final JLabel blueWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel greenWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel redWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel goldWalls = Theme.label("", Font.PLAIN, 12, Theme.MUTED);
    private final WrappedLabel status = new WrappedLabel(12, Font.PLAIN, Theme.MUTED);
    private final JLabel matchLabel = Theme.label("", Font.BOLD, 11, Theme.MUTED);
    private final Theme.Toggle horizontal = new Theme.Toggle("Horizontal");
    private final Theme.Toggle vertical = new Theme.Toggle("Vertical");
    private final PlayerCard blueCard = new PlayerCard(0, blueWalls);
    private final PlayerCard greenCard = new PlayerCard(1, greenWalls);
    private final PlayerCard redCard = new PlayerCard(2, redWalls);
    private final PlayerCard goldCard = new PlayerCard(3, goldWalls);
    private final Theme.Button undo = button("Undo", "undo-move", this::undoMove);
    private final Theme.Button redo = button("Redo", "redo-move", this::redoMove);
    private final Theme.Button hint = button("Hint", "hint-move", this::showHint);
    private final Theme.Button historyButton =
            button("Move history", "move-history", this::showHistory);
    private Theme.Button restart;
    private final Theme.Button replay = new Theme.Button("Play again", true);
    private Theme.Button menuAction;
    private MatchHistory history;
    private GameState.Cell suggestedMove;
    private OnlineMatchController online;
    private OnlineLobbyDialog lobby;
    private OnlineState onlineState;
    private boolean onlineEnded;
    private final BoardSurface surface =
            new BoardSurface(
                    cells,
                    slots,
                    () -> this.game,
                    () -> this.orientation,
                    this::tr,
                    this::movePawn,
                    this::placeWall);
    private final BotPlayer robot = new BotPlayer();
    private GameSession session;
    private GameState game;
    private GameState.Orientation orientation = GameState.Orientation.HORIZONTAL;

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
    public Board() {
        this(null, I18n.fromLocale(Locale.getDefault()));
    }

    /** Opens a supplied match directly, copying its mutable state. */
    public Board(GameSession initialSession) {
        this(initialSession, I18n.fromLocale(Locale.getDefault()));
    }

    /** Opens a match or menu in an explicitly selected supported language. */
    public Board(GameSession initialSession, I18n.Language language) {
        this(initialSession, language, Appearance.DARK);
    }

    /** Opens a match or menu with independent language and appearance preferences. */
    public Board(
            GameSession initialSession, I18n.Language language, Appearance selectedAppearance) {
        super("Quoridor — Your next move");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        URL icon = Board.class.getResource("/Files/Images/com.nozhaco.quoridor_512x512.png");
        if (icon != null) {
            setIconImage(new ImageIcon(icon).getImage());
        }
        views.add(createMenu(), MENU_VIEW);
        views.add(createGameView(), GAME_VIEW);
        views.setBackground(Theme.BACKGROUND);
        setContentPane(views);
        collectTranslationBindings(views);
        watchDefaultName(blueName, true);
        watchDefaultName(greenName, false);
        setLanguage(language);
        setAppearance(selectedAppearance);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(new Dimension(MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT));
        setLocationRelativeTo(null);
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        cancelRobot();
                        disconnectOnline();
                    }
                });
        if (initialSession == null) {
            navigation.show(views, MENU_VIEW);
            getRootPane().setDefaultButton(start);
        } else {
            startSession(initialSession);
        }
    }

    public I18n.Language currentLanguage() {
        return i18n.language();
    }

    public Appearance currentAppearance() {
        return appearance;
    }

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
        if (previousStatus != null) {
            setStatus(previousStatus, previousArguments);
        }
        views.revalidate();
        views.repaint();
    }

    /** Changes presentation in place; the match and robot search keep their state. */
    public void setLanguage(I18n.Language language) {
        String previousRed = tr("Red");
        String previousGold = tr("Gold");
        i18n = new I18n(Objects.requireNonNull(language, "language"));
        changingLanguage = true;
        languageMenu.setSelectedItem(language);
        languageGame.setSelectedItem(language);
        changingLanguage = false;
        for (Runnable binding : translationBindings) {
            binding.run();
        }
        setTitle(tr("Quoridor — Your next move") + " · " + AppVersion.current());
        updatingNames = true;
        if (blueDefaultName) {
            blueName.setText(tr("Blue"));
        }
        if (botMode.isSelected()) {
            greenName.setText(tr("Robot"));
        } else if (greenDefaultName) {
            greenName.setText(tr("Green"));
        }
        updatingNames = false;
        if (redName.getText().equals(previousRed)) {
            redName.setText(tr("Red"));
        }
        if (goldName.getText().equals(previousGold)) {
            goldName.setText(tr("Gold"));
        }
        updateModeText();
        blueName.getAccessibleContext().setAccessibleName(tr("Blue player name"));
        greenName.getAccessibleContext().setAccessibleName(tr("Green player name"));
        redName.getAccessibleContext().setAccessibleName(tr("Red player name"));
        goldName.getAccessibleContext().setAccessibleName(tr("Gold player name"));
        playerCount.getAccessibleContext().setAccessibleName(tr("Players"));
        difficulty.getAccessibleContext().setAccessibleName(tr("Robot difficulty"));
        difficulty.repaint();
        appearanceMenu.repaint();
        appearanceGame.repaint();
        ComponentOrientation direction = ComponentOrientation.getOrientation(language.locale());
        applyComponentOrientation(direction);
        alignColumns(views, language.rightToLeft());
        // Game coordinates are absolute: changing reading direction never mirrors the board.
        surface.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        String previousStatus = statusTemplate;
        Object[] previousArguments = statusArguments;
        refresh();
        if (previousStatus != null) {
            setStatus(previousStatus, previousArguments);
        }
        views.revalidate();
        views.repaint();
    }

    private String tr(String englishTemplate, Object... arguments) {
        return UiText.format(i18n, englishTemplate, arguments);
    }

    private void setStatus(String template, Object... arguments) {
        statusTemplate = template;
        statusArguments = arguments.clone();
        status.setText(tr(template, arguments));
    }

    /** Replaces the match and cancels any outstanding robot search. Call on the EDT. */
    public void startSession(GameSession candidate) {
        Objects.requireNonNull(candidate, "session");
        disconnectOnline();
        openSession(candidate);
    }

    private void openSession(GameSession candidate) {
        cancelRobot();
        game = candidate.game().copy();
        session = new GameSession(game, candidate.config());
        history = new MatchHistory(session);
        onlineEnded = false;
        suggestedMove = null;
        savePath = null;
        orientation = GameState.Orientation.HORIZONTAL;
        horizontal.setSelected(true);
        surface.clearPreview();
        inGame = true;
        navigation.show(views, GAME_VIEW);
        getRootPane().setDefaultButton(null);
        refresh();
        scheduleRobot();
    }

    /** Returns a defensive copy, or null before any match has started. */
    public GameSession currentSession() {
        return session == null ? null : new GameSession(game.copy(), session.config());
    }

    @Override
    public void dispose() {
        cancelRobot();
        disconnectOnline();
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
        hero.add(
                Theme.label(
                        "A simple race. A world of possibilities.", Font.PLAIN, 15, Theme.MUTED));
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
        hero.add(
                heroText(
                        "Place walls, but always leave a way through.",
                        Font.PLAIN,
                        13,
                        Theme.MUTED));
        Theme.Button onlineButton = button("Play online", "online-game", this::showOnlineLobby);
        fixedHeight(onlineButton, 40);
        hero.add(Box.createVerticalStrut(10));
        hero.add(onlineButton);
        hero.add(Box.createVerticalStrut(8));
        hero.add(button("Learn by playing", "tutorial-menu", this::showTutorial));
        hero.add(Box.createVerticalGlue());
        for (Component component : hero.getComponents()) {
            if (component instanceof JLabel label) {
                label.setMaximumSize(
                        new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
            }
        }
        menu.add(hero, BorderLayout.CENTER);
        Theme.Card setup = new Theme.Card(Theme.PANEL);
        setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
        setup.setBorder(BorderFactory.createEmptyBorder(26, 26, 24, 26));
        setup.setPreferredSize(new Dimension(430, 610));
        JPanel language =
                row(
                        languagePicker(languageMenu, "language-menu"),
                        appearancePicker(appearanceMenu, "appearance-menu"));
        fixedHeight(language, 30);
        addLeft(setup, language);
        setup.add(Box.createVerticalStrut(12));
        addLeft(setup, Theme.label("LET'S PLAY", Font.BOLD, 11, Theme.GREEN));
        setup.add(Box.createVerticalStrut(7));
        addLeft(setup, Theme.label("Make it your game.", Font.BOLD, 27, Theme.TEXT));
        setup.add(Box.createVerticalStrut(18));
        localMode.setName("local-game");
        botMode.setName("bot-game");
        localMode.setSelected(true);
        ButtonGroup modes = new ButtonGroup();
        modes.add(localMode);
        modes.add(botMode);
        localMode.addActionListener(event -> updateMode());
        botMode.addActionListener(event -> updateMode());
        JPanel modeButtons = row(localMode, botMode);
        fixedHeight(modeButtons, 42);
        addLeft(setup, modeButtons);
        setup.add(Box.createVerticalStrut(8));
        addLeft(setup, modeHint);
        setup.add(Box.createVerticalStrut(8));
        playerCount.setName("player-count");
        playerCount.setFont(Theme.font(Font.PLAIN, 13));
        playerCount.setBackground(Theme.RAISED);
        playerCount.setForeground(Theme.TEXT);
        playerCount.addActionListener(event -> updateModeText());
        playerCountPanel = row(Theme.label("Players", Font.PLAIN, 12, Theme.MUTED), playerCount);
        fixedHeight(playerCountPanel, 30);
        addLeft(setup, playerCountPanel);
        setup.add(Box.createVerticalStrut(16));
        JPanel names =
                row(
                        fieldGroup(
                                Theme.label("BLUE PLAYER", Font.BOLD, 11, Theme.MUTED), blueName),
                        fieldGroup(opponentLabel, greenName));
        fixedHeight(names, 65);
        addLeft(setup, names);
        extraNames.setOpaque(false);
        extraNames.add(fieldGroup(Theme.label("RED PLAYER", Font.BOLD, 11, Theme.MUTED), redName));
        extraNames.add(
                fieldGroup(Theme.label("GOLD PLAYER", Font.BOLD, 11, Theme.MUTED), goldName));
        fixedHeight(extraNames, 65);
        addLeft(setup, extraNames);
        setup.add(Box.createVerticalStrut(14));
        difficulty.setName("difficulty");
        difficulty.setSelectedItem(BotPlayer.Difficulty.MEDIUM);
        difficulty.setFont(Theme.font(Font.PLAIN, 14));
        difficulty.setBackground(Theme.RAISED);
        difficulty.setForeground(Theme.TEXT);
        difficulty.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        difficulty.setRenderer(new DifficultyRenderer());
        difficulty.getAccessibleContext().setAccessibleName("Robot difficulty");
        difficultyPanel.setOpaque(false);
        difficultyPanel.add(
                Theme.label("ROBOT DIFFICULTY", Font.BOLD, 11, Theme.MUTED), BorderLayout.NORTH);
        difficultyPanel.add(difficulty, BorderLayout.CENTER);
        fixedHeight(difficultyPanel, 65);
        addLeft(setup, difficultyPanel);
        setup.add(Box.createVerticalStrut(12));
        addLeft(setup, ruleHint);
        setup.add(Box.createVerticalGlue());
        start.setName("start-game");
        start.setMnemonic('S');
        start.addActionListener(event -> beginFromMenu());
        fixedHeight(start, 44);
        addLeft(setup, start);
        setup.add(Box.createVerticalStrut(8));
        Theme.Button load = button("Load a saved match", "load-game", this::loadGame);
        fixedHeight(load, 40);
        addLeft(setup, load);
        resume.setName("resume-game");
        resume.addActionListener(event -> resumeGame());
        resume.setVisible(false);
        fixedHeight(resume, 40);
        setup.add(Box.createVerticalStrut(8));
        addLeft(setup, resume);
        JScrollPane setupScroll = scrollPanel(setup, 446);
        menu.add(setupScroll, BorderLayout.LINE_END);
        updateMode();
        return menu;
    }

    private JPanel createGameView() {
        JPanel content = backgroundPanel();
        content.setName("game-view");
        content.setLayout(new BorderLayout(20, 13));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 14, 24));
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        JPanel title = column();
        title.add(Theme.label("QUORIDOR", Font.BOLD, 25, Theme.TEXT));
        title.add(matchLabel);
        header.add(title, BorderLayout.LINE_START);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 2));
        actions.setOpaque(false);
        actions.add(languagePicker(languageGame, "language-game"));
        actions.add(button("Save", "save-game", this::saveGame));
        actions.add(button("How to play", "tutorial-game", this::showTutorial));
        actions.add(button("Load", "load-game-in-game", this::loadGame));
        menuAction = button("Main menu", "back-menu", this::showMenu);
        actions.add(menuAction);
        header.add(actions, BorderLayout.LINE_END);
        content.add(header, BorderLayout.NORTH);
        content.add(surface, BorderLayout.CENTER);
        content.add(scrollPanel(createControls(), 284), BorderLayout.LINE_END);
        status.setName("status");
        status.setPreferredSize(new Dimension(850, 34));
        content.add(status, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createControls() {
        JPanel controls = column();
        controls.setPreferredSize(new Dimension(264, 500));
        JPanel appearance = appearancePicker(appearanceGame, "appearance-game");
        fixedHeight(appearance, 30);
        controls.add(appearance);
        controls.add(Box.createVerticalStrut(10));
        controls.add(Theme.label("CURRENT TURN", Font.BOLD, 10, Theme.MUTED));
        controls.add(Box.createVerticalStrut(5));
        turn.setName("turn");
        fixedHeight(turn, 44);
        controls.add(turn);
        replay.setName("replay-game");
        replay.addActionListener(event -> restartMatch());
        fixedHeight(replay, 42);
        replay.setVisible(false);
        controls.add(replay);
        controls.add(Box.createVerticalStrut(10));
        blueWalls.setName("blue-walls");
        greenWalls.setName("green-walls");
        fixedHeight(blueCard, 88);
        fixedHeight(greenCard, 88);
        controls.add(blueCard);
        controls.add(Box.createVerticalStrut(10));
        controls.add(greenCard);
        redWalls.setName("red-walls");
        goldWalls.setName("gold-walls");
        fixedHeight(redCard, 88);
        fixedHeight(goldCard, 88);
        controls.add(redCard);
        controls.add(goldCard);
        redCard.setVisible(false);
        goldCard.setVisible(false);
        controls.add(Box.createVerticalStrut(18));
        controls.add(Theme.label("PLACE A WALL", Font.BOLD, 10, Theme.MUTED));
        controls.add(Box.createVerticalStrut(8));
        horizontal.setName("horizontal");
        vertical.setName("vertical");
        horizontal.setSelected(true);
        ButtonGroup directions = new ButtonGroup();
        directions.add(horizontal);
        directions.add(vertical);
        horizontal.addActionListener(event -> chooseDirection(GameState.Orientation.HORIZONTAL));
        vertical.addActionListener(event -> chooseDirection(GameState.Orientation.VERTICAL));
        JPanel directionButtons = row(horizontal, vertical);
        fixedHeight(directionButtons, 40);
        controls.add(directionButtons);
        controls.add(Box.createVerticalStrut(8));
        JPanel historyActions = row(undo, redo);
        fixedHeight(historyActions, 36);
        controls.add(historyActions);
        controls.add(Box.createVerticalStrut(6));
        JPanel helpActions = row(hint, historyButton);
        fixedHeight(helpActions, 36);
        controls.add(helpActions);
        controls.add(Box.createVerticalGlue());
        controls.add(Box.createVerticalStrut(12));
        restart = button("New game", "new-game", this::restartMatch);
        fixedHeight(restart, 42);
        controls.add(restart);
        for (Component component : controls.getComponents()) {
            if (component instanceof JComponent control) {
                control.setAlignmentX(0f);
            }
        }
        return controls;
    }

    private void updateMode() {
        boolean bot = botMode.isSelected();
        if (bot != menuBotSelected) {
            updatingNames = true;
            if (bot) {
                localGreenName = greenName.getText();
                greenName.setText(tr("Robot"));
            } else {
                greenName.setText(greenDefaultName ? tr("Green") : localGreenName);
            }
            updatingNames = false;
            menuBotSelected = bot;
        }
        updateModeText();
    }

    private void updateModeText() {
        boolean bot = botMode.isSelected();
        playerCount.setEnabled(!bot);
        playerCountPanel.setVisible(!bot);
        extraNames.setVisible(!bot && Integer.valueOf(4).equals(playerCount.getSelectedItem()));
        ruleHint.setText(
                tr(
                        !bot && Integer.valueOf(4).equals(playerCount.getSelectedItem())
                                ? "Blue moves first. Each player has 5 walls."
                                : "Blue moves first. Each player has 10 walls."));
        opponentLabel.setText(tr(bot ? "YOUR OPPONENT" : "GREEN PLAYER"));
        greenName.setEnabled(!bot);
        difficultyPanel.setVisible(bot);
        modeHint.setText(
                tr(
                        bot
                                ? "You play blue. The robot plays green."
                                : "A friendly rivalry, on the same screen."));
        difficultyPanel.getParent().revalidate();
    }

    private void beginFromMenu() {
        try {
            List<String> names = new ArrayList<>();
            names.add(blueName.getText());
            names.add(botMode.isSelected() ? tr("Robot") : greenName.getText());
            if (!botMode.isSelected() && Integer.valueOf(4).equals(playerCount.getSelectedItem())) {
                names.add(redName.getText());
                names.add(goldName.getText());
            }
            GameConfig config =
                    new GameConfig(
                            names,
                            botMode.isSelected() ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                            (BotPlayer.Difficulty) difficulty.getSelectedItem());
            startSession(new GameSession(new GameState(config.playerCount()), config));
        } catch (IllegalArgumentException exception) {
            showMessage(
                    "Check player names",
                    tr("Use player names between 1 and 24 characters."),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showMenu() {
        cancelRobot();
        if (onlineEnded || online != null && online.active()) {
            disconnectOnline();
            session = null;
            game = null;
            history = null;
        }
        inGame = false;
        surface.clearPreview();
        resume.setVisible(session != null);
        navigation.show(views, MENU_VIEW);
        getRootPane().setDefaultButton(start);
    }

    private void resumeGame() {
        if (session == null) {
            return;
        }
        inGame = true;
        navigation.show(views, GAME_VIEW);
        getRootPane().setDefaultButton(null);
        refresh();
        scheduleRobot();
    }

    private void chooseDirection(GameState.Orientation direction) {
        orientation = direction;
        surface.clearPreview();
        refresh();
    }

    private boolean isRobotTurn() {
        return session != null
                && (online == null || !online.active())
                && session.config().mode() == GameConfig.Mode.BOT
                && game.currentPlayer() == 1
                && game.winner() < 0;
    }

    private boolean humanCanPlay() {
        return inGame
                && !onlineEnded
                && game != null
                && game.winner() < 0
                && !botBusy
                && !isRobotTurn()
                && (online == null
                        || !online.active()
                        || online.connected()
                                && !online.busy()
                                && onlineState != null
                                && onlineState.status() == OnlineState.Status.PLAYING
                                && game.currentPlayer() == onlineState.playerIndex());
    }

    private void movePawn(int row, int col) {
        if (humanCanPlay() && online != null && online.active()) {
            online.move(row, col, onlineState.revision());
            return;
        }
        if (humanCanPlay() && game.moveTo(row, col)) {
            afterAction();
        }
    }

    private void placeWall(int row, int col) {
        if (!humanCanPlay()) {
            return;
        }
        if (online != null && online.active()) {
            online.wall(row, col, orientation, onlineState.revision());
            return;
        }
        if (game.placeWall(row, col, orientation)) {
            afterAction();
        } else {
            setStatus("That wall cannot be placed. Every player needs a route to their goal.");
        }
    }

    private void afterAction() {
        if (history != null) {
            history.record(currentSession(), describeAction(history.current().game(), game));
        }
        suggestedMove = null;
        surface.clearPreview();
        refresh();
        scheduleRobot();
    }

    private void cancelRobot() {
        revision++;
        if (botWorker != null) {
            botWorker.cancel(true);
            botWorker = null;
        }
        botBusy = false;
    }

    private void scheduleRobot() {
        if (!inGame || !isRobotTurn() || botWorker != null) {
            return;
        }
        long token = revision;
        GameState snapshot = game.copy();
        BotPlayer.Difficulty level = session.config().difficulty();
        botBusy = true;
        refresh();
        botWorker =
                new SwingWorker<>() {
                    @Override
                    protected BotPlayer.Action doInBackground() {
                        return robot.chooseAction(snapshot, level);
                    }

                    @Override
                    protected void done() {
                        if (isCancelled() || token != revision || botWorker != this || !inGame) {
                            return;
                        }
                        botWorker = null;
                        botBusy = false;
                        try {
                            BotPlayer.Action action = get();
                            if (action == null || !action.apply(game)) {
                                fallbackRobotMove();
                            }
                            afterAction();
                        } catch (CancellationException exception) {
                            refresh();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            fallbackRobotMove();
                            afterAction();
                        } catch (ExecutionException exception) {
                            fallbackRobotMove();
                            afterAction();
                            setStatus("The robot played a safe move. Your turn.");
                        }
                    }
                };
        botWorker.execute();
    }

    private void fallbackRobotMove() {
        Set<GameState.Cell> moves = game.legalMoves();
        if (!moves.isEmpty()) {
            GameState.Cell first = moves.iterator().next();
            game.moveTo(first.row(), first.col());
        }
    }

    private void saveGame() {
        JFileChooser chooser = fileChooser("Save your match");
        chooser.setSelectedFile(savePath == null ? new File("Quoridor.qsave") : savePath.toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SAVE_EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + SAVE_EXTENSION);
        }
        if (Files.exists(target) && !confirmReplace(target.getFileName().toString())) {
            return;
        }
        try {
            SaveStore.save(target, currentSession());
            savePath = target;
            setStatus(
                    "Match saved to %s. You can continue playing.",
                    target.getFileName().toString());
        } catch (IOException exception) {
            showFileError("Couldn't save this match", "Choose a writable folder and try again.");
        }
    }

    private void loadGame() {
        JFileChooser chooser = fileChooser("Load a saved match");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            GameSession loaded = SaveStore.load(source);
            startSession(loaded);
            savePath =
                    source.getFileName()
                                    .toString()
                                    .toLowerCase(Locale.ROOT)
                                    .endsWith(SAVE_EXTENSION)
                            ? source
                            : null;
        } catch (IOException | IllegalArgumentException exception) {
            showFileError(
                    "Couldn't load this match",
                    "Choose a valid Quoridor .qsave or compatible old .G3M file.");
        }
    }

    private GameDialogs dialogs() {
        return new GameDialogs(this, i18n, palette);
    }

    private JFileChooser fileChooser(String title) {
        return dialogs().fileChooser(title, savePath);
    }

    private void showFileError(String title, String message) {
        dialogs().showFileError(title, message, session != null);
    }

    private void showMessage(String title, String message, int type) {
        dialogs().showMessage(title, message, type);
    }

    private boolean confirmReplace(String filename) {
        return dialogs().confirmReplace(filename);
    }

    private void refresh() {
        if (game == null) {
            return;
        }
        GameConfig config = session.config();
        boolean finished = game.winner() >= 0;
        int active = finished ? game.winner() : game.currentPlayer();
        String player = config.name(active);
        turn.setText(
                tr(finished ? "%s wins!" : botBusy ? "%s is thinking…" : "%s to move", player));
        turn.setToolTipText(turn.getText());
        turn.setForeground(Theme.playerColor(active, palette));
        blueWalls.setText(tr("%d walls remaining", game.wallsRemaining(0)));
        greenWalls.setText(tr("%d walls remaining", game.wallsRemaining(1)));
        blueCard.update(config.blueName(), active == 0);
        greenCard.update(config.greenName(), active == 1);
        boolean four = game.playerCount() == 4;
        for (PlayerCard card : List.of(blueCard, greenCard, redCard, goldCard)) {
            card.setCompact(four);
        }
        redCard.setVisible(four);
        goldCard.setVisible(four);
        if (four) {
            redWalls.setText(tr("%d walls remaining", game.wallsRemaining(2)));
            goldWalls.setText(tr("%d walls remaining", game.wallsRemaining(3)));
            redCard.update(config.name(2), active == 2);
            goldCard.update(config.name(3), active == 3);
        }
        boolean networked =
                online != null
                        && online.active()
                        && onlineState != null
                        && onlineState.session() != null;
        undo.setEnabled(!networked && history != null && history.canUndo());
        redo.setEnabled(!networked && !botBusy && history != null && history.canRedo());
        hint.setEnabled(!networked && humanCanPlay());
        restart.setEnabled(!networked);
        restart.setText(tr(finished ? "Play again" : "New game"));
        replay.setVisible(finished && !networked);
        replay.setText(tr("Play again"));
        menuAction.setText(tr(networked ? "Leave room" : "Main menu"));
        matchLabel.setText(
                networked
                        ? tr(
                                "ONLINE ROOM %s / YOU: %s",
                                onlineState.roomCode(), config.name(onlineState.playerIndex()))
                        : config.mode() == GameConfig.Mode.LOCAL
                                ? tr(four ? "LOCAL FOUR-PLAYER MATCH" : "LOCAL TWO-PLAYER MATCH")
                                : tr("VERSUS ROBOT  /  %s", tr(config.difficulty().name())));
        horizontal.setEnabled(humanCanPlay());
        vertical.setEnabled(humanCanPlay());
        status.setForeground(finished ? palette.gold() : palette.muted());
        if (finished) {
            setStatus("%s reached the far edge. Play again?", player);
        } else {
            setStatus(
                    botBusy
                            ? "The robot is considering its next move…"
                            : "Click a glowing square to move, or choose a wall direction and click a slot.");
        }
        if (networked && !online.connected()) {
            setStatus("Connection lost. Retrying…");
        } else if (networked && !humanCanPlay() && !finished) {
            setStatus("Waiting for %s to play.", player);
        }
        if (onlineEnded) {
            setStatus("The online room is closed. Start a new match from the menu.");
        }
        Set<GameState.Cell> moves = game.legalMoves();
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                GameState.Cell cell = new GameState.Cell(row, col);
                CellButton button = cells[row][col];
                int occupant = -1;
                for (int index = 0; index < game.playerCount(); index++) {
                    if (cell.equals(game.playerPosition(index))) {
                        occupant = index;
                        break;
                    }
                }
                boolean legal = moves.contains(cell) && humanCanPlay();
                button.update(occupant, legal);
                button.setActive(occupant == active);
                button.setTurn(active);
                button.setHinted(cell.equals(suggestedMove));
                String name =
                        occupant >= 0
                                ? tr("%s pawn", config.name(occupant))
                                : tr(legal ? "Move here" : "Square");
                button.setToolTipText(tr("%s (%d, %d)", name, row + 1, col + 1));
                button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            }
        }
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                SlotButton slot = slots[row][col];
                slot.setEnabled(humanCanPlay() && game.canPlaceWall(row, col, orientation));
                slot.setVisible(
                        !game.hasWall(row, col, GameState.Orientation.HORIZONTAL)
                                && !game.hasWall(row, col, GameState.Orientation.VERTICAL));
                slot.setToolTipText(
                        tr(
                                "Place %s wall (%d, %d)",
                                tr(orientation.name().toLowerCase(Locale.ROOT)), row + 1, col + 1));
                slot.getAccessibleContext().setAccessibleName(slot.getToolTipText());
            }
        }
        surface.repaint();
    }

    private void restartMatch() {
        if (session != null && (online == null || !online.active())) {
            startSession(
                    new GameSession(
                            new GameState(session.config().playerCount()), session.config()));
        }
    }

    private void showTutorial() {
        cancelRobot();
        refresh();
        new TutorialDialog(this, i18n, palette).setVisible(true);
        refresh();
        scheduleRobot();
    }

    private void undoMove() {
        if (history == null || !history.canUndo() || online != null && online.active()) {
            return;
        }
        cancelRobot();
        GameSession restored = history.undo();
        if (restored.config().mode() == GameConfig.Mode.BOT) {
            while (restored.game().currentPlayer() == 1 && history.canUndo()) {
                restored = history.undo();
            }
        }
        restoreHistory(restored);
    }

    private void redoMove() {
        if (history == null || !history.canRedo() || botBusy || online != null && online.active()) {
            return;
        }
        cancelRobot();
        GameSession restored = history.redo();
        if (restored.config().mode() == GameConfig.Mode.BOT) {
            while (restored.game().winner() < 0
                    && restored.game().currentPlayer() == 1
                    && history.canRedo()) {
                restored = history.redo();
            }
        }
        restoreHistory(restored);
    }

    private void restoreHistory(GameSession restored) {
        game = restored.game();
        session = new GameSession(game, restored.config());
        suggestedMove = null;
        surface.clearPreview();
        refresh();
        scheduleRobot();
    }

    private void showHint() {
        if (!humanCanPlay() || online != null && online.active()) {
            return;
        }
        suggestedMove = GameHints.pawnMove(game);
        refresh();
        if (suggestedMove != null) {
            setStatus(
                    "Suggested move: row %d, column %d.",
                    suggestedMove.row() + 1, suggestedMove.col() + 1);
        }
    }

    private static String describeAction(GameState before, GameState after) {
        int player = before.currentPlayer();
        GameState.Cell destination = after.playerPosition(player);
        if (!before.playerPosition(player).equals(destination)) {
            return player + " M " + destination.row() + " " + destination.col();
        }
        for (GameState.Wall wall : after.snapshot().walls()) {
            if (!before.hasWall(wall.row(), wall.col(), wall.orientation())) {
                return player
                        + " W "
                        + wall.row()
                        + " "
                        + wall.col()
                        + " "
                        + wall.orientation().name();
            }
        }
        return "";
    }

    private void showHistory() {
        List<String> entries =
                onlineState != null
                        ? onlineState.moves()
                        : history == null ? List.of() : history.descriptions();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            String[] parts = entries.get(index).split(" ");
            int player = Integer.parseInt(parts[0]);
            int row = Integer.parseInt(parts[2]) + 1;
            int col = Integer.parseInt(parts[3]) + 1;
            String action =
                    parts[1].equals("M")
                            ? tr("%s moved to (%d, %d)", session.config().name(player), row, col)
                            : tr(
                                    "%s placed a %s wall at (%d, %d)",
                                    session.config().name(player),
                                    tr(parts[4].toLowerCase(Locale.ROOT)),
                                    row,
                                    col);
            text.append(tr("%d. %s", index + 1, action)).append('\n');
        }
        JTextArea log = new JTextArea(text.isEmpty() ? tr("No moves yet.") : text.toString());
        log.setName("history-log");
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(Theme.font(Font.PLAIN, 14));
        log.applyComponentOrientation(getComponentOrientation());
        log.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(500, 330));
        dialogs().showContent("Move history", scroll);
    }

    private void showOnlineLobby() {
        if (lobby != null && lobby.isDisplayable()) {
            lobby.toFront();
            return;
        }
        if (online == null) {
            online =
                    new OnlineMatchController(
                            new OnlineMatchController.Listener() {
                                @Override
                                public void onState(OnlineState state) {
                                    acceptOnlineState(state);
                                }

                                @Override
                                public void onConnectionProblem(boolean reconnecting) {
                                    if (lobby != null) {
                                        lobby.showConnectionProblem(reconnecting);
                                    }
                                    refresh();
                                }

                                @Override
                                public void onFailure(String code) {
                                    if (onlineState != null
                                            && onlineState.session() != null
                                            && Set.of(
                                                            "UNAUTHORIZED",
                                                            "NO_MEMBERSHIP",
                                                            "ROOM_NOT_FOUND",
                                                            "ROOM_CLOSED")
                                                    .contains(code)) {
                                        onlineEnded = true;
                                    }
                                    refresh();
                                    if (lobby != null && lobby.isDisplayable()) {
                                        lobby.showFailure(code);
                                    } else if (!onlineEnded) {
                                        setStatus(
                                                "The online action failed. The board will synchronize again.");
                                    }
                                }

                                @Override
                                public void onBusy(boolean busy) {
                                    if (lobby != null) {
                                        lobby.setBusy(busy);
                                    }
                                    refresh();
                                }
                            });
        }
        lobby =
                new OnlineLobbyDialog(
                        this,
                        i18n,
                        palette,
                        blueName.getText(),
                        (uri, action, count, name, code) -> {
                            onlineState = null;
                            online.start(uri, action, count, name, code);
                        },
                        this::disconnectOnline);
        lobby.setVisible(true);
    }

    private void acceptOnlineState(OnlineState state) {
        OnlineState previous = onlineState;
        if (previous != null
                && (!previous.roomCode().equals(state.roomCode())
                        || previous.playerCount() != state.playerCount()
                        || state.revision() < previous.revision())) {
            return;
        }
        onlineState = state;
        if (lobby != null && lobby.isDisplayable()) {
            lobby.showState(state);
        }
        if (state.session() != null) {
            if (previous == null || previous.session() == null) {
                openSession(state.session());
                if (lobby != null) {
                    lobby.closeUI();
                    lobby = null;
                }
            } else if (previous.revision() != state.revision()) {
                game = state.session().game();
                session = new GameSession(game, state.session().config());
                suggestedMove = null;
                surface.clearPreview();
            }
        }
        if (state.status() == OnlineState.Status.CLOSED) {
            onlineEnded = state.session() != null;
            if (!onlineEnded && lobby != null) {
                lobby.showFailure("ROOM_CLOSED");
            }
            if (online != null) {
                online.leave();
            }
        }
        refresh();
    }

    private void disconnectOnline() {
        if (lobby != null) {
            lobby.closeUI();
            lobby = null;
        }
        if (online != null) {
            online.close();
            online = null;
        }
        onlineState = null;
    }

    private static JScrollPane scrollPanel(JPanel panel, int width) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setPreferredSize(new Dimension(width, 500));
        return scroll;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JPanel row(JComponent first, JComponent second) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 9, 0));
        panel.setOpaque(false);
        panel.add(first);
        panel.add(second);
        return panel;
    }

    private static Theme.Button button(String text, String name, Runnable action) {
        Theme.Button button = new Theme.Button(text, false);
        button.setName(name);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel languagePicker(JComboBox<I18n.Language> selector, String name) {
        selector.setName(name);
        selector.setFont(Theme.font(Font.PLAIN, 12));
        selector.setBackground(Theme.RAISED);
        selector.setForeground(Theme.TEXT);
        selector.setPreferredSize(new Dimension(108, 30));
        selector.setRenderer(new LanguageRenderer());
        selector.addActionListener(
                event -> {
                    if (!changingLanguage) {
                        setLanguage((I18n.Language) selector.getSelectedItem());
                    }
                });
        selector.getAccessibleContext().setAccessibleName(tr("Language"));
        translationBindings.add(
                () -> selector.getAccessibleContext().setAccessibleName(tr("Language")));
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
        selector.setBackground(Theme.RAISED);
        selector.setForeground(Theme.TEXT);
        selector.setPreferredSize(new Dimension(100, 30));
        selector.setRenderer(new AppearanceRenderer());
        selector.addActionListener(
                event -> {
                    if (!changingAppearance) {
                        setAppearance((Appearance) selector.getSelectedItem());
                    }
                });
        translationBindings.add(
                () -> selector.getAccessibleContext().setAccessibleName(tr("Appearance")));
        JPanel panel = new JPanel(new BorderLayout(7, 0));
        panel.setOpaque(false);
        JLabel label = Theme.label("Appearance", Font.PLAIN, 11, Theme.MUTED);
        label.putClientProperty("selector.label", Boolean.TRUE);
        panel.add(label, BorderLayout.LINE_START);
        panel.add(selector, BorderLayout.LINE_END);
        return panel;
    }

    private void collectTranslationBindings(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JLabel label
                    && component != turn
                    && component != status
                    && component != modeHint
                    && component != opponentLabel
                    && component != matchLabel
                    && label.getText() != null
                    && !label.getText().isEmpty()) {
                String key = label.getText();
                int originalSize = label.getFont().getSize();
                translationBindings.add(
                        () -> {
                            label.setText(tr(key));
                            if (label.getClientProperty("selector.label") != null) {
                                label.setPreferredSize(null);
                                Dimension natural = label.getPreferredSize();
                                label.setPreferredSize(
                                        new Dimension(natural.width + 12, natural.height));
                            }
                            if (originalSize == 48 || originalSize == 27) {
                                label.setFont(
                                        Theme.font(
                                                Font.BOLD,
                                                currentLanguage().rightToLeft()
                                                        ? originalSize == 48 ? 31 : 21
                                                        : originalSize));
                            }
                            if (label.getMaximumSize().width == Integer.MAX_VALUE) {
                                int height =
                                        label.getClientProperty("hero.text") != null
                                                ? currentLanguage().rightToLeft() ? 34 : 20
                                                : label.getPreferredSize().height;
                                label.setPreferredSize(
                                        new Dimension(label.getPreferredSize().width, height));
                                label.setMinimumSize(new Dimension(20, height));
                                label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                            }
                        });
            } else if (component instanceof AbstractButton button
                    && button.getText() != null
                    && !button.getText().isEmpty()) {
                String key = button.getText();
                translationBindings.add(() -> button.setText(tr(key)));
            }
            if (component instanceof Container child) {
                collectTranslationBindings(child);
            }
        }
    }

    private void watchDefaultName(JTextField field, boolean blue) {
        field.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            private void changed() {
                                if (!updatingNames) {
                                    if (blue) {
                                        blueDefaultName = false;
                                    } else {
                                        greenDefaultName = false;
                                    }
                                }
                            }

                            @Override
                            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                                changed();
                            }

                            @Override
                            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                                changed();
                            }

                            @Override
                            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                                changed();
                            }
                        });
    }

    private void alignColumns(Container parent, boolean rightToLeft) {
        parent.setLocale(i18n.locale());
        boolean column =
                parent.getLayout() instanceof BoxLayout box && box.getAxis() == BoxLayout.Y_AXIS;
        for (Component component : parent.getComponents()) {
            component.setLocale(i18n.locale());
            if (column && component instanceof JComponent child) {
                child.setAlignmentX(rightToLeft ? 1f : 0f);
            }
            if (component instanceof JLabel label && label.getText() != null) {
                label.setHorizontalAlignment(SwingConstants.LEADING);
            }
            if (component instanceof JTextField field) {
                field.setHorizontalAlignment(SwingConstants.LEADING);
            }
            if (component instanceof Container child) {
                alignColumns(child, rightToLeft);
            }
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

    private static void addLeft(JPanel panel, JComponent component) {
        component.setAlignmentX(0f);
        panel.add(component);
    }

    private static JPanel fieldGroup(JLabel label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setOpaque(false);
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static JTextField nameField(String value, String name) {
        JTextField field = new JTextField(value);
        field.setName(name);
        field.setFont(Theme.font(Font.PLAIN, 15));
        field.setForeground(Theme.TEXT);
        field.setDisabledTextColor(Theme.MUTED);
        field.setBackground(Theme.BACKGROUND);
        field.setCaretColor(Theme.GOLD);
        field.setSelectionColor(new Color(43, 97, 137));
        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.LINE),
                        BorderFactory.createEmptyBorder(9, 11, 9, 11)));
        field.getAccessibleContext()
                .setAccessibleName(
                        name.equals("blue-name") ? "Blue player name" : "Green player name");
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(
                        new DocumentFilter() {
                            @Override
                            public void insertString(
                                    FilterBypass bypass,
                                    int offset,
                                    String text,
                                    AttributeSet attributes)
                                    throws BadLocationException {
                                replace(bypass, offset, 0, text, attributes);
                            }

                            @Override
                            public void replace(
                                    FilterBypass bypass,
                                    int offset,
                                    int length,
                                    String text,
                                    AttributeSet attributes)
                                    throws BadLocationException {
                                String before =
                                        bypass.getDocument()
                                                .getText(0, bypass.getDocument().getLength());
                                String candidate =
                                        before.substring(0, offset)
                                                + (text == null ? "" : text)
                                                + before.substring(offset + length);
                                if (candidate.codePointCount(0, candidate.length()) <= 24
                                        && candidate
                                                .codePoints()
                                                .noneMatch(Character::isISOControl)) {
                                    super.replace(bypass, offset, length, text, attributes);
                                }
                            }
                        });
        return field;
    }

    private JPanel backgroundPanel() {
        JPanel panel =
                new JPanel() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void paintComponent(Graphics graphics) {
                        super.paintComponent(graphics);
                        Graphics2D g = (Graphics2D) graphics.create();
                        Theme.smooth(g);
                        g.setPaint(
                                new GradientPaint(
                                        0,
                                        0,
                                        Theme.blend(palette.background(), palette.raised(), 0.35f),
                                        getWidth(),
                                        getHeight(),
                                        palette.background()));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(new Color(86, 158, 255, 7));
                        g.fillOval(-260, -320, 950, 950);
                        g.dispose();
                    }
                };
        panel.setBackground(Theme.BACKGROUND);
        return panel;
    }

    private final class DifficultyRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label =
                    (JLabel)
                            super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(
                    tr(
                            value == BotPlayer.Difficulty.EASY
                                    ? "Easy — a relaxed challenge"
                                    : value == BotPlayer.Difficulty.HARD
                                            ? "Hard — think a few moves ahead"
                                            : "Medium — a worthy opponent"));
            label.applyComponentOrientation(Board.this.getComponentOrientation());
            label.setHorizontalAlignment(SwingConstants.LEADING);
            label.setFont(Theme.font(Font.PLAIN, 13));
            label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            label.setForeground(palette.text());
            label.setBackground(selected ? palette.selection() : palette.raised());
            return label;
        }
    }

    private final class LanguageRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label =
                    (JLabel)
                            super.getListCellRendererComponent(list, value, index, selected, focus);
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

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label =
                    (JLabel)
                            super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(tr(value == Appearance.LIGHT ? "Light" : "Dark"));
            label.setFont(Theme.font(Font.PLAIN, 12));
            label.setHorizontalAlignment(SwingConstants.LEADING);
            label.applyComponentOrientation(Board.this.getComponentOrientation());
            label.setForeground(palette.text());
            label.setBackground(selected ? palette.selection() : palette.raised());
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }
    }

    private final class PlayerCard extends Theme.Card {
        private static final long serialVersionUID = 1L;
        private final int player;
        private final WrappedLabel name = new WrappedLabel(14, Font.BOLD, Theme.TEXT);
        private final JLabel pawn = new JLabel();
        private final JLabel goal;
        private boolean compact;

        PlayerCard(int player, JLabel walls) {
            super(Theme.PANEL);
            this.player = player;
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            pawn.setIcon(new Theme.PawnIcon(Theme.playerColor(player, palette), 34));
            add(pawn, BorderLayout.LINE_START);
            JPanel labels = new JPanel(new BorderLayout(0, 0));
            labels.setOpaque(false);
            name.setPreferredSize(new Dimension(180, 34));
            labels.add(name, BorderLayout.CENTER);
            JPanel details = column();
            details.add(walls);
            goal =
                    Theme.label(
                            switch (player) {
                                case 0 -> "Goal: top edge ↑";
                                case 1 -> "Goal: bottom edge ↓";
                                case 2 -> "Goal: right edge →";
                                default -> "Goal: left edge ←";
                            },
                            Font.PLAIN,
                            10,
                            Theme.MUTED);
            details.add(goal);
            // The logical font supplies arrows missing from the bundled Persian font.
            goal.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
            labels.add(details, BorderLayout.SOUTH);
            add(labels, BorderLayout.CENTER);
        }

        void update(String text, boolean active) {
            name.setText(text);
            name.setToolTipText(text);
            pawn.setIcon(new Theme.PawnIcon(Theme.playerColor(player, palette), compact ? 26 : 34));
            accent(active ? Theme.playerColor(player, palette) : palette.line());
        }

        void setCompact(boolean value) {
            compact = value;
            fixedHeight(this, value ? 52 : 88);
            setBorder(BorderFactory.createEmptyBorder(value ? 5 : 10, 12, value ? 5 : 10, 12));
            name.setPreferredSize(new Dimension(180, value ? 20 : 34));
            name.setFont(Theme.font(Font.BOLD, value ? 12 : 14));
            goal.setVisible(!value);
            pawn.setToolTipText(goal.getText());
        }
    }
}
