package com.quoridor.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameHints;
import Board.GameSession;
import Board.GameState;
import Board.SaveStore;

import Online.OnlineState;

import com.quoridor.mobile.MobileText.Key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Native Android navigation and lifecycle; rules and network authority remain shared Java code. */
public final class MainActivity extends Activity {
    private static final int IMPORT_REQUEST = 10;
    private static final int EXPORT_REQUEST = 11;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService analysis = Executors.newSingleThreadExecutor();
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private SaveStorage storage;
    private MobileText words;
    private MobileStyle style;
    private boolean dark;
    private boolean foreground;
    private boolean gameScreen;
    private boolean thinking;
    private boolean onlineBusy;
    private boolean reconnecting;
    private boolean roomEnded;
    private long analysisGeneration;
    private long roomGeneration;
    private long importGeneration;
    private long pendingImportGeneration = -1;
    private Future<?> calculation;
    private LocalMatch local;
    private OnlineMatch online;
    private OnlineState room;
    private String onlineEndpoint = "";
    private String pendingExport;
    private AlertDialog connecting;
    private LinearLayout content;
    private Spinner mode;
    private Spinner difficulty;
    private Spinner onlineCount;
    private final List<EditText> names = new ArrayList<>();
    private EditText server;
    private EditText nickname;
    private EditText roomCode;
    private QuoridorBoardView board;
    private TextView status;
    private PlayerInventoryView inventory;
    private TextView roomLabel;
    private TextView toolHelp;
    private Button undo;
    private Button redo;
    private Button hint;
    private Button save;
    private Button confirm;
    private Button moveTool;
    private Button horizontalTool;
    private Button verticalTool;
    private Button replay;
    private TutorialDialog tutorial;

    /** Retains domain state, never Views, dialogs, executors, or an Activity callback. */
    private static final class RetainedMatch {
        final LocalMatch local;
        final OnlineMatch online;
        final OnlineState room;
        final boolean gameScreen;
        final boolean onlineBusy;
        final boolean reconnecting;
        final boolean roomEnded;
        final long roomGeneration;
        final long importGeneration;
        final long pendingImportGeneration;
        final String endpoint;

        RetainedMatch(MainActivity activity) {
            local = activity.local;
            online = activity.online;
            room = activity.room;
            gameScreen = activity.gameScreen;
            onlineBusy = activity.onlineBusy;
            reconnecting = activity.reconnecting;
            roomEnded = activity.roomEnded;
            roomGeneration = activity.roomGeneration;
            importGeneration = activity.importGeneration;
            pendingImportGeneration = activity.pendingImportGeneration;
            endpoint = activity.onlineEndpoint;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("quoridor", MODE_PRIVATE);
        storage = new SaveStorage(this);
        dark = preferences.getBoolean("dark", true);
        boolean persian =
                preferences.getBoolean("persian", Locale.getDefault().getLanguage().equals("fa"));
        words = new MobileText(persian);
        RetainedMatch retained =
                getLastNonConfigurationInstance() instanceof RetainedMatch previous
                        ? previous
                        : null;
        if (state != null) {
            pendingExport = state.getString("export");
            importGeneration = state.getLong("importGeneration", 0);
            pendingImportGeneration = state.getLong("pendingImportGeneration", -1);
            String saved = retained == null ? state.getString("local") : null;
            if (saved != null) {
                try {
                    local = new LocalMatch(SaveStore.decode(saved));
                } catch (IOException invalid) {
                    toast(Key.FILE_ERROR);
                }
            }
        }
        if (retained != null) {
            local = retained.local;
            online = retained.online;
            room = retained.room;
            onlineBusy = retained.onlineBusy;
            reconnecting = retained.reconnecting;
            roomEnded = retained.roomEnded;
            roomGeneration = retained.roomGeneration;
            importGeneration = retained.importGeneration;
            pendingImportGeneration = retained.pendingImportGeneration;
            onlineEndpoint = retained.endpoint;
        }
        if (local != null || (retained != null && retained.gameScreen && online != null)) {
            showGame();
        } else {
            showMenu();
        }
        if (online != null) {
            online.setListener(roomListener(roomGeneration));
            if (online != null && room == null && !roomEnded) {
                showConnectingDialog();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
        if (online != null) {
            online.setForeground(true);
        }
        startRobotIfNeeded();
    }

    @Override
    protected void onStop() {
        foreground = false;
        cancelCalculation();
        saveRecovery();
        if (online != null) {
            online.setForeground(false);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (tutorial != null) {
            tutorial.setOnDismissListener(null);
            tutorial.dismiss();
            tutorial = null;
        }
        cancelCalculation();
        invalidateImport();
        if (isChangingConfigurations()) {
            if (online != null) {
                online.setListener(null);
            }
        } else {
            closeRoom();
        }
        analysis.shutdownNow();
        files.shutdown();
        dismissConnecting();
        super.onDestroy();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        rememberSetup();
        if (online != null) {
            online.setListener(null);
        }
        return new RetainedMatch(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("export", pendingExport);
        state.putLong("importGeneration", importGeneration);
        state.putLong("pendingImportGeneration", pendingImportGeneration);
        if (local != null) {
            try {
                state.putString("local", SaveStore.encode(local.session()));
            } catch (IOException ignored) {
                /* The existing atomic recovery file is preserved. */
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        rememberSetup();
        if (gameScreen) {
            showGame();
        } else {
            showMenu();
        }
    }

    private String t(Key key, Object... values) {
        return words.text(key, values);
    }

    private void newScreen() {
        style = new MobileStyle(this, dark);
        names.clear();
        mode = null;
        server = null;
        board = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(style.background);
        scroll.setLayoutDirection(
                words.persian() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(style.dp(16), style.dp(12), style.dp(16), style.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else {
                        view.setPadding(
                                insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                    }
                    return insets;
                });
        scroll.requestApplyInsets();
        getWindow().setStatusBarColor(style.background);
        getWindow().setNavigationBarColor(style.background);
        int systemBarFlags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && android.os.Build.VERSION.SDK_INT >= 27) {
            systemBarFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else if (!dark) {
            getWindow().setNavigationBarColor(android.graphics.Color.DKGRAY);
        }
        getWindow().getDecorView().setSystemUiVisibility(systemBarFlags);
        LinearLayout header = row(content);
        header.setMinimumHeight(style.dp(64));
        header.setPadding(0, style.dp(6), 0, style.dp(6));
        header.setClipChildren(false);
        header.setClipToPadding(false);
        TextView title = style.label("QUORIDOR", 24, true);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(
                style.button(
                        words.persian() ? "English" : "فارسی",
                        "settings.language",
                        () -> {
                            rememberSetup();
                            words = new MobileText(!words.persian());
                            preferences.edit().putBoolean("persian", words.persian()).apply();
                            if (gameScreen) {
                                showGame();
                            } else {
                                showMenu();
                            }
                        }),
                new LinearLayout.LayoutParams(-2, -2));
        header.addView(
                style.button(
                        t(dark ? Key.DARK : Key.LIGHT),
                        "settings.theme",
                        () -> {
                            rememberSetup();
                            dark = !dark;
                            preferences.edit().putBoolean("dark", dark).apply();
                            if (gameScreen) {
                                showGame();
                            } else {
                                showMenu();
                            }
                        }),
                new LinearLayout.LayoutParams(-2, -2));
    }

    private void showMenu() {
        gameScreen = false;
        newScreen();
        add(content, style.muted(t(Key.TAGLINE)));
        LinearLayout localCard = card();
        add(localCard, style.label(t(Key.LOCAL_TITLE), 22, true));
        add(localCard, style.muted(t(Key.LOCAL_HELP)));
        mode =
                style.spinner(
                        new String[] {t(Key.TWO), t(Key.FOUR), t(Key.BOT)},
                        preferences.getInt("mode", 0),
                        "menu.mode");
        add(localCard, mode);
        difficulty =
                style.spinner(
                        new String[] {t(Key.EASY), t(Key.MEDIUM), t(Key.HARD)},
                        preferences.getInt("difficulty", 1),
                        "menu.difficulty");
        add(localCard, difficulty);
        for (int player = 0; player < 4; player++) {
            EditText input =
                    style.input(
                            words.colorName(player),
                            preferences.getString("name" + player, words.colorName(player)),
                            "menu.name" + player);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            names.add(input);
            add(localCard, input);
        }
        mode.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        updateSetupVisibility();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        updateSetupVisibility();
        Button start = style.button(t(Key.START), "menu.start", this::startLocal);
        style.primary(start);
        add(localCard, start);
        if (storage.hasRecovery()) {
            add(
                    localCard,
                    style.button(
                            t(Key.CONTINUE),
                            "menu.continue",
                            () -> {
                                try {
                                    openLocal(storage.loadRecovery());
                                    toast(Key.RESTORED);
                                } catch (IOException invalid) {
                                    error(Key.FILE_ERROR);
                                }
                            }));
        }
        add(localCard, style.button(t(Key.IMPORT), "menu.load", this::chooseImport));
        buildOnlineSetup();
        LinearLayout links = row(content);
        weighted(links, style.button(t(Key.LEARN), "menu.rules", this::showTutorial));
        weighted(links, style.button(t(Key.ABOUT), "menu.about", this::showAbout));
        add(content, style.muted(t(Key.VERSION, BuildConfig.VERSION_NAME)));
    }

    private void updateSetupVisibility() {
        boolean four = mode.getSelectedItemPosition() == 1;
        boolean robot = mode.getSelectedItemPosition() == 2;
        difficulty.setVisibility(robot ? View.VISIBLE : View.GONE);
        for (int player = 0; player < names.size(); player++) {
            names.get(player)
                    .setVisibility(
                            (player < 2 || four) && !(robot && player == 1)
                                    ? View.VISIBLE
                                    : View.GONE);
        }
    }

    private void rememberSetup() {
        if (mode == null) {
            return;
        }
        SharedPreferences.Editor edit =
                preferences
                        .edit()
                        .putInt("mode", mode.getSelectedItemPosition())
                        .putInt("difficulty", difficulty.getSelectedItemPosition());
        for (int index = 0; index < names.size(); index++) {
            edit.putString("name" + index, names.get(index).getText().toString());
        }
        if (server != null) {
            edit.putString("endpoint", server.getText().toString())
                    .putString("nickname", nickname.getText().toString())
                    .putInt("onlineCount", onlineCount.getSelectedItemPosition());
        }
        edit.apply();
    }

    private void startLocal() {
        rememberSetup();
        int selection = mode.getSelectedItemPosition();
        boolean robot = selection == 2;
        List<String> players = new ArrayList<>();
        for (int player = 0; player < (selection == 1 ? 4 : 2); player++) {
            players.add(
                    robot && player == 1 ? t(Key.ROBOT) : names.get(player).getText().toString());
        }
        try {
            GameConfig config =
                    new GameConfig(
                            players,
                            robot ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                            BotPlayer.Difficulty.values()[difficulty.getSelectedItemPosition()]);
            openLocal(new GameSession(new GameState(players.size()), config));
        } catch (IllegalArgumentException invalid) {
            error(Key.INVALID_SETUP);
        }
    }

    private void openLocal(GameSession session) {
        invalidateImport();
        rememberSetup();
        closeRoom();
        cancelCalculation();
        local = new LocalMatch(session);
        saveRecovery();
        showGame();
        startRobotIfNeeded();
    }

    private void buildOnlineSetup() {
        LinearLayout onlineCard = card();
        add(onlineCard, style.label(t(Key.ONLINE_TITLE), 22, true));
        add(onlineCard, style.muted(t(Key.ONLINE_HELP)));
        server =
                style.input(
                        t(Key.SERVER), preferences.getString("endpoint", ""), "menu.online.server");
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setTextDirection(View.TEXT_DIRECTION_LTR);
        add(onlineCard, server);
        nickname =
                style.input(t(Key.NAME), preferences.getString("nickname", ""), "menu.online.name");
        add(onlineCard, nickname);
        onlineCount =
                style.spinner(
                        new String[] {t(Key.TWO), t(Key.FOUR)},
                        preferences.getInt("onlineCount", 0),
                        "menu.online.count");
        add(onlineCard, onlineCount);
        LinearLayout createRow = row(onlineCard);
        weighted(
                createRow,
                style.button(
                        t(Key.CREATE),
                        "menu.online.create",
                        () -> enterRoom(OnlineMatch.Entry.CREATE)));
        weighted(
                createRow,
                style.button(
                        t(Key.RANDOM),
                        "menu.online.random",
                        () -> enterRoom(OnlineMatch.Entry.RANDOM)));
        roomCode = style.input(t(Key.ROOM_CODE), "", "menu.online.code");
        roomCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        roomCode.setTextDirection(View.TEXT_DIRECTION_LTR);
        add(onlineCard, roomCode);
        add(
                onlineCard,
                style.button(
                        t(Key.JOIN), "menu.online.join", () -> enterRoom(OnlineMatch.Entry.JOIN)));
        add(onlineCard, style.muted(t(Key.ONLINE_NOTICE)));
    }

    private void enterRoom(OnlineMatch.Entry entry) {
        rememberSetup();
        String name;
        try {
            name =
                    new GameConfig(
                                    nickname.getText().toString(),
                                    "Player",
                                    GameConfig.Mode.LOCAL,
                                    BotPlayer.Difficulty.EASY)
                            .blueName();
        } catch (IllegalArgumentException invalid) {
            error(Key.INVALID_SETUP);
            return;
        }
        String endpoint = server.getText().toString().trim();
        String code = roomCode.getText().toString();
        int count = onlineCount.getSelectedItemPosition() == 1 ? 4 : 2;
        invalidateImport();
        closeRoom();
        long generation = roomGeneration;
        try {
            online = new OnlineMatch(endpoint, roomListener(generation));
        } catch (IllegalArgumentException invalid) {
            error(Key.NETWORK_ERROR);
            return;
        }
        onlineEndpoint = endpoint;
        onlineBusy = true;
        roomEnded = false;
        local = null;
        online.enter(entry, count, name, code);
        showConnectingDialog();
    }

    private OnlineMatch.Listener roomListener(long generation) {
        return new OnlineMatch.Listener() {
            @Override
            public void onState(OnlineState state) {
                if (generation != roomGeneration || isDestroyed()) {
                    return;
                }
                dismissConnecting();
                room = state;
                onlineBusy = online != null && online.busy();
                reconnecting = false;
                roomEnded = state.status() == OnlineState.Status.CLOSED;
                if (!gameScreen) {
                    showGame();
                } else {
                    renderGame();
                }
            }

            @Override
            public void onFailure(boolean entering, boolean terminal) {
                if (generation != roomGeneration || isDestroyed()) {
                    return;
                }
                onlineBusy = false;
                if (entering) {
                    dismissConnecting();
                    closeRoom();
                    showMenu();
                    error(Key.NETWORK_ERROR);
                } else {
                    reconnecting = !terminal;
                    roomEnded = terminal;
                    renderGame();
                }
            }
        };
    }

    private void showConnectingDialog() {
        dismissConnecting();
        connecting =
                dialogBuilder()
                        .setMessage(t(Key.CONNECTING))
                        .setNegativeButton(t(Key.CANCEL), (dialog, which) -> closeRoom())
                        .create();
        connecting.setCanceledOnTouchOutside(false);
        connecting.setOnCancelListener(dialog -> closeRoom());
        connecting.show();
    }

    private void showGame() {
        gameScreen = true;
        newScreen();
        LinearLayout navigation = row(content);
        weighted(
                navigation,
                style.button(
                        t(online == null ? Key.MENU : Key.LEAVE), "game.menu", this::requestMenu));
        save = style.button(t(Key.SAVE), "game.save", this::chooseExport);
        weighted(navigation, save);
        weighted(navigation, style.button(t(Key.RULES), "game.rules", this::showTutorial));
        roomLabel = style.muted("");
        roomLabel.setTag("game.room");
        add(content, roomLabel);
        if (online != null) {
            add(content, style.button(t(Key.SHARE), "game.share", this::shareRoom));
        }
        status = style.label("", 22, true);
        status.setTag("game.status");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(style.dp(14), style.dp(10), style.dp(14), style.dp(10));
        status.setBackground(style.shape(style.panel));
        add(content, status);
        replay =
                style.button(
                        t(Key.PLAY_AGAIN),
                        "game.replay",
                        () -> {
                            if (online == null
                                    && local != null
                                    && local.session().game().winner() >= 0) {
                                GameConfig config = local.session().config();
                                openLocal(
                                        new GameSession(
                                                new GameState(config.playerCount()), config));
                            }
                        });
        style.primary(replay);
        add(content, replay);
        inventory = new PlayerInventoryView(this, style, words, dark);
        inventory.setTag("game.inventory");
        add(content, inventory);
        board = new QuoridorBoardView(this);
        board.setTag("game.board");
        board.setLayoutDirection(
                words.persian() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        board.setDark(dark);
        board.setListener(
                new QuoridorBoardView.Listener() {
                    @Override
                    public void onMove(int row, int col) {
                        move(row, col);
                    }

                    @Override
                    public void onWallSelected(GameState.Wall wall, boolean legal) {
                        if (confirm != null) {
                            enable(confirm, wall != null && legal && mayAct());
                        }
                    }
                });
        add(content, board);
        LinearLayout tools = row(content);
        moveTool =
                style.button(t(Key.MOVE), "game.move", () -> setTool(QuoridorBoardView.Mode.MOVE));
        horizontalTool =
                style.button(
                        t(Key.HORIZONTAL),
                        "game.horizontal",
                        () -> setTool(QuoridorBoardView.Mode.HORIZONTAL));
        verticalTool =
                style.button(
                        t(Key.VERTICAL),
                        "game.vertical",
                        () -> setTool(QuoridorBoardView.Mode.VERTICAL));
        weighted(tools, moveTool);
        weighted(tools, horizontalTool);
        weighted(tools, verticalTool);
        toolHelp = style.muted(t(Key.MOVE_HELP));
        add(content, toolHelp);
        confirm = style.button(t(Key.CONFIRM_WALL), "game.confirm", this::placeWall);
        style.primary(confirm);
        add(content, confirm);
        LinearLayout actions = row(content);
        undo =
                style.button(
                        t(Key.UNDO),
                        "game.undo",
                        () -> {
                            cancelCalculation();
                            local.undo();
                            localChanged();
                        });
        redo =
                style.button(
                        t(Key.REDO),
                        "game.redo",
                        () -> {
                            cancelCalculation();
                            local.redo();
                            localChanged();
                        });
        hint = style.button(t(Key.HINT), "game.hint", this::showHint);
        Button history = style.button(t(Key.HISTORY), "game.history", this::showHistory);
        weighted(actions, undo);
        weighted(actions, redo);
        weighted(actions, hint);
        weighted(actions, history);
        undo.setVisibility(online == null ? View.VISIBLE : View.GONE);
        redo.setVisibility(online == null ? View.VISIBLE : View.GONE);
        hint.setVisibility(online == null ? View.VISIBLE : View.GONE);
        setTool(QuoridorBoardView.Mode.MOVE);
        renderGame();
    }

    private GameSession session() {
        return local != null ? local.session() : room == null ? null : room.session();
    }

    private boolean mayAct() {
        GameSession current = session();
        if (current == null || current.game().winner() >= 0 || thinking || tutorial != null) {
            return false;
        }
        return online == null
                ? local != null && !local.robotTurn()
                : !onlineBusy
                        && !roomEnded
                        && !reconnecting
                        && room.status() == OnlineState.Status.PLAYING
                        && current.game().currentPlayer() == room.playerIndex();
    }

    private void renderGame() {
        if (board == null) {
            return;
        }
        GameSession current = session();
        replay.setVisibility(
                online == null && current != null && current.game().winner() >= 0
                        ? View.VISIBLE
                        : View.GONE);
        boolean waiting = current == null;
        board.setVisibility(waiting ? View.GONE : View.VISIBLE);
        if (!waiting) {
            board.setPosition(current.game(), mayAct());
        }
        String heading;
        if (roomEnded && (current == null || current.game().winner() < 0)) {
            heading = t(Key.CLOSED);
        } else if (reconnecting) {
            heading = t(Key.RECONNECTING);
        } else if (waiting) {
            heading =
                    room == null
                            ? t(Key.CONNECTING)
                            : t(Key.WAITING, room.playerNames().size(), room.playerCount());
        } else if (current.game().winner() >= 0) {
            heading = t(Key.WINNER, words.isolate(current.config().name(current.game().winner())));
        } else if (thinking) {
            heading = t(Key.THINKING);
        } else {
            heading =
                    t(
                            online != null && !mayAct() ? Key.WAIT_TURN : Key.TURN,
                            words.isolate(current.config().name(current.game().currentPlayer())));
        }
        if (!heading.contentEquals(status.getText())) {
            status.setText(heading);
        }
        inventory.update(current, room == null ? List.of() : room.playerNames());
        roomLabel.setText(
                online == null
                        ? t(Key.VERSION, BuildConfig.VERSION_NAME)
                        : room == null
                                ? t(Key.CONNECTING)
                                : t(Key.ROOM, room.roomCode())
                                        + " · "
                                        + t(Key.YOU, words.colorName(room.playerIndex())));
        enable(save, current != null);
        enable(undo, local != null && local.canUndo());
        enable(redo, local != null && local.canRedo());
        enable(hint, local != null && mayAct());
        enable(moveTool, mayAct());
        enable(horizontalTool, mayAct());
        enable(verticalTool, mayAct());
        enable(
                confirm,
                mayAct()
                        && board.selectedWall() != null
                        && current.game()
                                .canPlaceWall(
                                        board.selectedWall().row(),
                                        board.selectedWall().col(),
                                        board.selectedWall().orientation()));
    }

    private void setTool(QuoridorBoardView.Mode tool) {
        board.setMode(tool);
        board.clearSelection();
        toolHelp.setText(t(tool == QuoridorBoardView.Mode.MOVE ? Key.MOVE_HELP : Key.WALL_HELP));
        confirm.setVisibility(tool == QuoridorBoardView.Mode.MOVE ? View.GONE : View.VISIBLE);
        moveTool.setTextColor(tool == QuoridorBoardView.Mode.MOVE ? style.accent : style.text);
        horizontalTool.setTextColor(
                tool == QuoridorBoardView.Mode.HORIZONTAL ? style.accent : style.text);
        verticalTool.setTextColor(
                tool == QuoridorBoardView.Mode.VERTICAL ? style.accent : style.text);
    }

    private void move(int row, int col) {
        if (!mayAct()) {
            return;
        }
        if (local != null) {
            if (local.move(row, col)) {
                localChanged();
            }
        } else {
            onlineBusy = true;
            renderGame();
            online.move(row, col, room.revision());
        }
    }

    private void placeWall() {
        if (!mayAct()) {
            return;
        }
        GameState.Wall selected = board.selectedWall();
        if (selected == null) {
            return;
        }
        if (local != null) {
            if (local.wall(selected)) {
                localChanged();
            } else {
                toast(Key.INVALID_WALL);
            }
        } else {
            onlineBusy = true;
            renderGame();
            online.wall(selected, room.revision());
        }
    }

    private void localChanged() {
        board.clearSelection();
        board.setHint(null);
        saveRecovery();
        renderGame();
        startRobotIfNeeded();
    }

    private void startRobotIfNeeded() {
        if (!foreground
                || isDestroyed()
                || tutorial != null
                || local == null
                || !local.robotTurn()
                || thinking) {
            return;
        }
        LocalMatch owner = local;
        GameSession position = owner.session();
        long revision = owner.revision();
        long generation = ++analysisGeneration;
        thinking = true;
        renderGame();
        calculation =
                analysis.submit(
                        () -> {
                            try {
                                BotPlayer.Action action =
                                        new BotPlayer()
                                                .chooseAction(
                                                        position.game(),
                                                        position.config().difficulty());
                                main.post(
                                        () -> {
                                            if (isDestroyed()
                                                    || generation != analysisGeneration
                                                    || local != owner
                                                    || !foreground) {
                                                return;
                                            }
                                            thinking = false;
                                            if (owner.applyRobot(action, revision)) {
                                                localChanged();
                                            } else {
                                                renderGame();
                                            }
                                        });
                            } catch (CancellationException ignored) {
                                /* Replacement/paused matches own their own next calculation. */
                            } catch (RuntimeException failure) {
                                main.post(
                                        () -> {
                                            if (generation == analysisGeneration
                                                    && !isDestroyed()) {
                                                thinking = false;
                                                renderGame();
                                                error(Key.BOT_ERROR);
                                            }
                                        });
                            }
                        });
    }

    private void cancelCalculation() {
        analysisGeneration++;
        if (calculation != null) {
            calculation.cancel(true);
            calculation = null;
        }
        thinking = false;
    }

    private void showHint() {
        if (local == null || !mayAct()) {
            return;
        }
        setTool(QuoridorBoardView.Mode.MOVE);
        board.setHint(GameHints.pawnMove(local.session().game()));
    }

    private void showHistory() {
        GameSession current = session();
        if (current == null) {
            message(t(Key.HISTORY), t(Key.NO_HISTORY));
            return;
        }
        List<String> moves = local != null ? local.moves() : room.moves();
        StringBuilder body = new StringBuilder();
        for (String raw : moves) {
            String[] fields = raw.split(" ");
            int player = Integer.parseInt(fields[0]);
            String name = words.isolate(current.config().name(player));
            int row = Integer.parseInt(fields[2]) + 1, col = Integer.parseInt(fields[3]) + 1;
            body.append(
                            fields[1].equals("M")
                                    ? t(Key.MOVE_LINE, name, row, col)
                                    : t(
                                            Key.WALL_LINE,
                                            name,
                                            t(
                                                    fields[4].equals("HORIZONTAL")
                                                            ? Key.HORIZONTAL
                                                            : Key.VERTICAL),
                                            row,
                                            col))
                    .append('\n');
        }
        message(t(Key.HISTORY), body.length() == 0 ? t(Key.NO_HISTORY) : body.toString());
    }

    private void chooseExport() {
        GameSession current = session();
        if (current == null) {
            return;
        }
        try {
            pendingExport = SaveStore.encode(current);
        } catch (IOException invalid) {
            error(Key.FILE_ERROR);
            return;
        }
        Intent intent =
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_TITLE, "Quoridor.qsave");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    private void chooseImport() {
        rememberSetup();
        pendingImportGeneration = ++importGeneration;
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*");
        startActivityForResult(intent, IMPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (request == EXPORT_REQUEST) {
                pendingExport = null;
            }
            if (request == IMPORT_REQUEST) {
                pendingImportGeneration = -1;
            }
            return;
        }
        android.net.Uri uri = data.getData();
        if (request == EXPORT_REQUEST && pendingExport != null) {
            byte[] bytes = pendingExport.getBytes(StandardCharsets.UTF_8);
            pendingExport = null;
            files.execute(
                    () -> {
                        try (OutputStream stream =
                                getContentResolver().openOutputStream(uri, "wt")) {
                            if (stream == null) {
                                throw new IOException("Document unavailable");
                            }
                            stream.write(bytes);
                            main.post(
                                    () -> {
                                        if (!isDestroyed()) {
                                            toast(Key.SAVED);
                                        }
                                    });
                        } catch (IOException | SecurityException failure) {
                            postFileError();
                        }
                    });
        } else if (request == IMPORT_REQUEST) {
            long generation = pendingImportGeneration;
            pendingImportGeneration = -1;
            if (!currentImport(generation)) {
                return;
            }
            files.execute(
                    () -> {
                        try (InputStream stream = getContentResolver().openInputStream(uri)) {
                            GameSession imported = SaveStorage.read(stream);
                            main.post(
                                    () -> {
                                        if (currentImport(generation)) {
                                            openLocal(imported);
                                        }
                                    });
                        } catch (IOException | SecurityException failure) {
                            main.post(
                                    () -> {
                                        if (currentImport(generation)) {
                                            error(Key.FILE_ERROR);
                                        }
                                    });
                        }
                    });
        }
    }

    private boolean currentImport(long generation) {
        return generation >= 0
                && generation == importGeneration
                && !isDestroyed()
                && !isFinishing();
    }

    private void invalidateImport() {
        importGeneration++;
    }

    private void postFileError() {
        main.post(
                () -> {
                    if (!isDestroyed()) {
                        error(Key.FILE_ERROR);
                    }
                });
    }

    private void saveRecovery() {
        if (local == null || storage == null) {
            return;
        }
        try {
            storage.saveRecovery(local.session());
        } catch (IOException failure) {
            if (!isFinishing()) {
                toast(Key.FILE_ERROR);
            }
        }
    }

    private void requestMenu() {
        if (online != null && !roomEnded) {
            dialogBuilder()
                    .setMessage(t(Key.LEAVE_QUESTION))
                    .setPositiveButton(t(Key.LEAVE), (dialog, which) -> goMenu())
                    .setNegativeButton(t(Key.CANCEL), null)
                    .show();
        } else if (local != null) {
            dialogBuilder()
                    .setMessage(t(Key.MENU_QUESTION))
                    .setPositiveButton(t(Key.MENU), (dialog, which) -> goMenu())
                    .setNegativeButton(t(Key.CANCEL), null)
                    .show();
        } else {
            goMenu();
        }
    }

    private void goMenu() {
        invalidateImport();
        saveRecovery();
        cancelCalculation();
        closeRoom();
        local = null;
        showMenu();
    }

    @Override
    public void onBackPressed() {
        if (gameScreen) {
            requestMenu();
        } else {
            super.onBackPressed();
        }
    }

    private void closeRoom() {
        roomGeneration++;
        if (online != null) {
            online.close();
            online = null;
        }
        room = null;
        onlineBusy = false;
        reconnecting = false;
        roomEnded = false;
    }

    private void dismissConnecting() {
        if (connecting != null) {
            connecting.dismiss();
            connecting = null;
        }
    }

    private void shareRoom() {
        if (room == null) {
            return;
        }
        Intent share =
                new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(
                                Intent.EXTRA_TEXT,
                                t(Key.SHARE_MESSAGE, room.roomCode(), onlineEndpoint));
        startActivity(Intent.createChooser(share, t(Key.SHARE)));
    }

    private void showAbout() {
        StringBuilder notices =
                new StringBuilder(t(Key.VERSION, BuildConfig.VERSION_NAME)).append("\n\n");
        for (String file :
                new String[] {
                    "AUTHORS.md",
                    "LICENSE",
                    "THIRD_PARTY_NOTICES.md",
                    "desugar-LICENSE",
                    "r8-LICENSE"
                }) {
            try (InputStream stream = getAssets().open(file)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1 && bytes.size() <= 32768) {
                    bytes.write(buffer, 0, count);
                }
                notices.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                        .append("\n\n");
            } catch (IOException missing) {
                notices.append(t(Key.ORIGINAL_AUTHORS))
                        .append(":\nسیدمحمدصالح قطبانی\nمحمدجواد بنان\nامیرمحمد خسروی\n");
            }
        }
        message(t(Key.ABOUT), notices.toString());
    }

    private void showTutorial() {
        if (tutorial != null) return;
        cancelCalculation();
        tutorial = new TutorialDialog(this, words, dark);
        tutorial.setOnDismissListener(
                dialog -> {
                    tutorial = null;
                    if (!isDestroyed() && !isFinishing()) {
                        if (gameScreen) renderGame();
                        startRobotIfNeeded();
                    }
                });
        tutorial.show();
        if (gameScreen) renderGame();
    }

    private void error(Key key) {
        message(t(Key.ERROR), t(key));
    }

    private AlertDialog.Builder dialogBuilder() {
        int theme =
                dark
                        ? android.R.style.Theme_Material_Dialog_Alert
                        : android.R.style.Theme_Material_Light_Dialog_Alert;
        ContextThemeWrapper context = new ContextThemeWrapper(this, theme);
        Configuration configuration = new Configuration(getResources().getConfiguration());
        Locale locale = words.persian() ? Locale.forLanguageTag("fa-IR") : Locale.ENGLISH;
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        context.applyOverrideConfiguration(configuration);
        return new AlertDialog.Builder(context);
    }

    private void message(String title, String body) {
        dialogBuilder().setTitle(title).setMessage(body).setPositiveButton(t(Key.OK), null).show();
    }

    private void toast(Key key) {
        Toast.makeText(this, t(key), Toast.LENGTH_LONG).show();
    }

    private void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }

    private LinearLayout card() {
        LinearLayout card = style.card();
        add(content, card);
        return card;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        add(parent, row);
        return row;
    }

    private void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, style.dp(4), 0, style.dp(4));
        parent.addView(child, params);
    }

    private void weighted(LinearLayout row, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(style.dp(2), style.dp(2), style.dp(2), style.dp(2));
        row.addView(child, params);
    }
}
