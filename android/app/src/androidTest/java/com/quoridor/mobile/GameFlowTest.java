package com.quoridor.mobile;

import android.graphics.Rect;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.GameTutorial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Full native control/listener/worker flows, separate from pure LocalMatch model tests. */
@SuppressWarnings("deprecation")
public final class GameFlowTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public void testPassPhoneAndFiveLanguagesPreserveFourPlayerMatch() throws Exception {
        MainActivity activity = getActivity();
        ui(() -> start(activity, 1, 0, new String[] {"Blue", "Green", "Red", "Gold"}));
        ui(() -> assertTrue(tag(activity, "game.pass").performClick()));
        ui(() -> tapCell(board(activity), 7, 4));
        ui(
                () -> {
                    android.app.AlertDialog dialog =
                            (android.app.AlertDialog) field(activity, "handoff");
                    assertTrue(dialog.isShowing());
                    assertEquals(2, local(activity).session().game().currentPlayer());
                    assertFalse((Boolean) field(board(activity), "inputEnabled"));
                    capture(dialog.getWindow().getDecorView(), "android-pass-phone");
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                });
        ui(() -> assertNull(field(activity, "handoff")));
        AtomicReference<GameState.Snapshot> position = new AtomicReference<>();
        ui(() -> position.set(local(activity).session().game().snapshot()));
        for (int index = 0; index < 5; index++) {
            int display = index;
            ui(
                    () -> {
                        assertEquals(position.get(), local(activity).session().game().snapshot());
                        for (String control :
                                new String[] {
                                    "game.board",
                                    "game.move",
                                    "game.horizontal",
                                    "game.vertical",
                                    "game.undo",
                                    "game.redo",
                                    "game.hint",
                                    "game.history",
                                    "game.pass"
                                }) {
                            View view = tag(activity, control);
                            Rect visible = new Rect();
                            assertTrue(
                                    control + " is in the fixed viewport",
                                    view.getGlobalVisibleRect(visible));
                            assertEquals(
                                    control + " is not clipped vertically",
                                    view.getHeight(),
                                    visible.height());
                            assertEquals(
                                    control + " is not clipped horizontally",
                                    view.getWidth(),
                                    visible.width());
                        }
                        capture(activity.getWindow().getDecorView(), "android-language-" + display);
                        assertTrue(tag(activity, "settings.language").performClick());
                    });
        }
        ui(() -> assertEquals("en", ((MobileText) field(activity, "words")).language()));
    }

    public GameFlowTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertTrue(
                getInstrumentation()
                        .getTargetContext()
                        .getSharedPreferences("quoridor", 0)
                        .edit()
                        .remove("passPhone")
                        .remove("language")
                        .putBoolean("persian", false)
                        .putBoolean("dark", true)
                        .putInt("mode", 0)
                        .putInt("difficulty", 1)
                        .putString("name0", "Blue")
                        .putString("name1", "Green")
                        .putString("name2", "Red")
                        .putString("name3", "Gold")
                        .commit());
    }

    public void testLocalControlsWallConfirmationAndSettingsPreservePosition() throws Exception {
        MainActivity activity = getActivity();
        String human = "سارا O'Reilly 50%";
        ui(() -> start(activity, 0, 0, new String[] {human, "Ali"}));
        AtomicReference<LocalMatch> owner = new AtomicReference<>();
        AtomicReference<GameState.Snapshot> afterWall = new AtomicReference<>();
        ui(
                () -> {
                    owner.set(local(activity));
                    tapCell(board(activity), 7, 4);
                    assertEquals(1, local(activity).session().game().currentPlayer());
                    assertEquals(
                            new GameState.Cell(7, 4),
                            local(activity).session().game().playerPosition(0));
                    assertTrue(tag(activity, "game.hint").performClick());
                    GameState.Cell hint = (GameState.Cell) field(board(activity), "hint");
                    assertNotNull(hint);
                    assertTrue(local(activity).session().game().legalMoves().contains(hint));
                    assertTrue(tag(activity, "game.horizontal").performClick());
                    View confirm = tag(activity, "game.confirm");
                    assertEquals(View.VISIBLE, confirm.getVisibility());
                    assertFalse(confirm.isEnabled());
                    tapNode(board(activity), 100 + 3 * 8 + 3);
                    assertTrue(confirm.isEnabled());
                    assertFalse(
                            local(activity)
                                    .session()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.HORIZONTAL));
                    tapNode(board(activity), 100 + 3 * 8 + 3);
                    assertFalse(
                            local(activity)
                                    .session()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.HORIZONTAL));
                    assertTrue(confirm.performClick());
                    assertTrue(
                            local(activity)
                                    .session()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.HORIZONTAL));
                    assertEquals(9, local(activity).session().game().wallsRemaining(1));
                    assertEquals(0, local(activity).session().game().currentPlayer());
                    PlayerInventoryView inventory =
                            (PlayerInventoryView) tag(activity, "game.inventory");
                    assertTrue(inventory.getContentDescription().toString().contains("9 walls"));
                    View playerCards = inventory.getChildAt(0);
                    invoke(activity, "renderGame");
                    assertSame(
                            "An unchanged refresh keeps accessible player cards",
                            playerCards,
                            inventory.getChildAt(0));
                    afterWall.set(local(activity).session().game().snapshot());
                    assertTrue(tag(activity, "game.undo").performClick());
                    assertFalse(
                            local(activity)
                                    .session()
                                    .game()
                                    .hasWall(3, 3, GameState.Orientation.HORIZONTAL));
                    assertEquals(1, local(activity).session().game().currentPlayer());
                    assertTrue(tag(activity, "game.redo").performClick());
                    assertEquals(afterWall.get(), local(activity).session().game().snapshot());
                });
        ui(() -> assertTrue(tag(activity, "settings.language").performClick()));
        ui(
                () -> {
                    assertSame(owner.get(), local(activity));
                    assertEquals(afterWall.get(), local(activity).session().game().snapshot());
                    assertEquals(human, local(activity).session().config().name(0));
                    assertEquals(View.LAYOUT_DIRECTION_RTL, board(activity).getLayoutDirection());
                    assertTrue(
                            ((TextView) tag(activity, "game.status"))
                                    .getText()
                                    .toString()
                                    .contains(human));
                    assertTrue(tag(activity, "settings.theme").performClick());
                });
        ui(
                () -> {
                    assertSame(owner.get(), local(activity));
                    assertEquals(afterWall.get(), local(activity).session().game().snapshot());
                    assertFalse((Boolean) field(activity, "dark"));
                    assertEquals(human, local(activity).session().config().name(0));
                    assertEquals(2, local(activity).moves().size());
                });
    }

    public void testFourPlayerScreenUsesClockwiseTurnsAndFiveWallInventories() throws Exception {
        MainActivity activity = getActivity();
        String[] names = {"Alice", "Bob", "Charlie", "Dina"};
        ui(() -> start(activity, 1, 0, names));
        int[][] moves = {{7, 4}, {4, 1}, {1, 4}, {4, 7}};
        int[] nextPlayers = {2, 1, 3, 0};
        for (int index = 0; index < moves.length; index++) {
            final int move = index;
            ui(
                    () -> {
                        assertEquals(4, local(activity).session().game().playerCount());
                        tapCell(board(activity), moves[move][0], moves[move][1]);
                        assertEquals(
                                nextPlayers[move],
                                local(activity).session().game().currentPlayer());
                        assertTrue(
                                ((TextView) tag(activity, "game.status"))
                                        .getText()
                                        .toString()
                                        .contains(names[nextPlayers[move]]));
                        assertEquals(move + 1, local(activity).moves().size());
                        assertFalse((Boolean) field(activity, "thinking"));
                    });
        }
        ui(
                () -> {
                    String inventory =
                            tag(activity, "game.inventory").getContentDescription().toString();
                    for (int player = 0; player < 4; player++) {
                        assertTrue(inventory.contains(names[player]));
                        assertEquals(5, local(activity).session().game().wallsRemaining(player));
                    }
                    assertEquals(4, inventory.split("5 walls", -1).length - 1);
                    assertTrue(tag(activity, "game.hint").isEnabled());
                    assertTrue(tag(activity, "game.save").isEnabled());
                    assertTrue(tag(activity, "game.undo").performClick());
                    assertEquals(3, local(activity).session().game().currentPlayer());
                    assertEquals(3, local(activity).moves().size());
                    assertTrue(tag(activity, "game.redo").performClick());
                    assertEquals(0, local(activity).session().game().currentPlayer());
                });
    }

    public void testEveryRobotDifficultyRepliesAndRejectsCancelledResults() throws Exception {
        MainActivity activity = getActivity();
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            final int level = difficulty;
            ui(
                    () -> {
                        if (field(activity, "local") != null) {
                            invoke(activity, "goMenu");
                        }
                        start(activity, 2, level, new String[] {"Human " + level});
                    });
            AtomicReference<LocalMatch> owner = new AtomicReference<>();
            AtomicReference<GameState.Snapshot> opening = new AtomicReference<>();
            ui(
                    () -> {
                        owner.set(local(activity));
                        opening.set(local(activity).session().game().snapshot());
                        assertEquals(
                                BotPlayer.Difficulty.values()[level],
                                local(activity).session().config().difficulty());
                        tapCell(board(activity), 7, 4);
                        assertTrue((Boolean) field(activity, "thinking"));
                        assertEquals(1, owner.get().session().game().currentPlayer());
                        assertTrue(tag(activity, "game.undo").isEnabled());
                        assertTrue(tag(activity, "game.undo").performClick());
                        assertFalse((Boolean) field(activity, "thinking"));
                        assertEquals(opening.get(), owner.get().session().game().snapshot());
                    });
            // Even a completed worker's already-queued callback must be fenced by Undo's epoch.
            Thread.sleep(1000);
            ui(
                    () -> {
                        assertSame(owner.get(), local(activity));
                        assertEquals(opening.get(), owner.get().session().game().snapshot());
                        assertEquals(0, owner.get().moves().size());
                        tapCell(board(activity), 7, 4);
                    });
            waitUntil(
                    () ->
                            !((Boolean) field(activity, "thinking"))
                                    && owner.get().moves().size() == 2
                                    && owner.get().session().game().currentPlayer() == 0);
            ui(
                    () -> {
                        GameState.Snapshot afterPair = owner.get().session().game().snapshot();
                        assertEquals(afterPair, GameState.fromSnapshot(afterPair).snapshot());
                        assertTrue(tag(activity, "game.undo").performClick());
                        assertEquals(opening.get(), owner.get().session().game().snapshot());
                        assertEquals(0, owner.get().moves().size());
                        assertFalse(tag(activity, "game.undo").isEnabled());
                        assertTrue(tag(activity, "game.redo").isEnabled());
                        assertTrue(tag(activity, "game.redo").performClick());
                        assertEquals(afterPair, owner.get().session().game().snapshot());
                        assertEquals(2, owner.get().moves().size());
                        assertFalse((Boolean) field(activity, "thinking"));
                    });
        }
    }

    public void testVisualPracticePreservesLiveMatchInBothLanguagesAndThemes() throws Exception {
        MainActivity activity = getActivity();
        ui(
                () -> {
                    start(activity, 0, 0, new String[] {"Practice friend", "Other"});
                });
        ui(() -> tapCell(board(activity), 7, 4));
        AtomicReference<GameState.Snapshot> live = new AtomicReference<>();
        ui(() -> live.set(local(activity).session().game().snapshot()));
        for (int appearance = 0; appearance < 4; appearance++) {
            int display = appearance;
            ui(() -> capture(activity.getWindow().getDecorView(), "android-game-" + display));
            ui(() -> assertTrue(tag(activity, "game.rules").performClick()));
            for (int index = 0; index < GameTutorial.LESSON_COUNT; index++) {
                int step = index;
                ui(
                        () -> {
                            TutorialDialog dialog = (TutorialDialog) field(activity, "tutorial");
                            View root = dialog.getWindow().getDecorView();
                            QuoridorBoardView practice = root.findViewWithTag("tutorial.board");
                            View next = root.findViewWithTag("tutorial.next");
                            assertNotNull(practice);
                            assertFalse(next.isEnabled());
                            GameTutorial expected = new GameTutorial();
                            expected.load(step);
                            BotPlayer.Action action = expected.target();
                            if (step < 2) capture(root, "android-tutorial-" + step + "-" + display);
                            if (action.orientation() == null) {
                                tapCell(practice, action.row(), action.col());
                            } else {
                                tapNode(practice, 100 + action.row() * 8 + action.col());
                                View confirm = root.findViewWithTag("tutorial.confirm");
                                assertTrue(confirm.isEnabled());
                                assertTrue(confirm.performClick());
                            }
                            assertTrue(next.isEnabled());
                            assertEquals(live.get(), local(activity).session().game().snapshot());
                            assertTrue(next.performClick());
                        });
            }
            ui(
                    () -> {
                        assertNull(field(activity, "tutorial"));
                        assertEquals(live.get(), local(activity).session().game().snapshot());
                        if (display < 3)
                            assertTrue(
                                    tag(
                                                    activity,
                                                    display == 1
                                                            ? "settings.language"
                                                            : "settings.theme")
                                            .performClick());
                    });
        }
        ui(
                () -> {
                    assertTrue(tag(activity, "game.undo").performClick());
                    assertEquals(
                            new GameState().snapshot(),
                            local(activity).session().game().snapshot());
                });
    }

    public void testWinnerReplayResetsPositionAndRetainsTwoOrFourPlayerConfiguration()
            throws Exception {
        MainActivity activity = getActivity();
        for (int count : new int[] {2, 4}) {
            ui(
                    () -> {
                        GameState.Snapshot start = new GameState(count).snapshot();
                        java.util.List<GameState.Cell> positions =
                                new java.util.ArrayList<>(start.players());
                        positions.set(0, new GameState.Cell(1, 4));
                        positions.set(1, new GameState.Cell(7, 4));
                        GameState state =
                                GameState.fromSnapshot(
                                        new GameState.Snapshot(
                                                positions,
                                                start.wallsRemaining(),
                                                0,
                                                -1,
                                                java.util.List.of()));
                        GameConfig config =
                                new GameConfig(
                                        java.util.List.of("Winner 🎮", "Friend", "Red", "Gold")
                                                .subList(0, count),
                                        count == 2 ? GameConfig.Mode.BOT : GameConfig.Mode.LOCAL,
                                        BotPlayer.Difficulty.HARD);
                        Method open =
                                MainActivity.class.getDeclaredMethod(
                                        "openLocal", GameSession.class);
                        open.setAccessible(true);
                        open.invoke(activity, new GameSession(state, config));
                    });
            ui(
                    () -> {
                        assertEquals(View.GONE, tag(activity, "game.replay").getVisibility());
                        tapCell(board(activity), 0, 4);
                    });
            ui(
                    () -> {
                        assertEquals(0, local(activity).session().game().winner());
                        assertEquals(View.VISIBLE, tag(activity, "game.replay").getVisibility());
                        assertTrue(
                                "Replay has a real hit area",
                                tag(activity, "game.replay").getHeight() >= 48);
                        capture(activity.getWindow().getDecorView(), "android-replay-" + count);
                        assertTrue(tag(activity, "game.replay").performClick());
                        assertEquals(
                                new GameState(count).snapshot(),
                                local(activity).session().game().snapshot());
                        assertEquals("Winner 🎮", local(activity).session().config().name(0));
                        assertEquals(
                                BotPlayer.Difficulty.HARD,
                                local(activity).session().config().difficulty());
                        assertFalse(tag(activity, "game.undo").isEnabled());
                        assertEquals(View.GONE, tag(activity, "game.replay").getVisibility());
                    });
        }
    }

    private void capture(View view, String name) throws Exception {
        android.graphics.Bitmap image =
                android.graphics.Bitmap.createBitmap(
                        view.getWidth(),
                        view.getHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
        try {
            view.draw(new android.graphics.Canvas(image));
            java.io.File directory =
                    new java.io.File(getInstrumentation().getTargetContext().getFilesDir(), "qa");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (java.io.OutputStream stream =
                    new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
                assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
            }
        } finally {
            image.recycle();
        }
    }

    private static void start(MainActivity activity, int mode, int difficulty, String[] names) {
        ((Spinner) tag(activity, "menu.mode")).setSelection(mode);
        ((Spinner) tag(activity, "menu.difficulty")).setSelection(difficulty);
        for (int index = 0; index < names.length; index++) {
            ((EditText) tag(activity, "menu.name" + index)).setText(names[index]);
        }
        assertTrue(tag(activity, "menu.start").performClick());
    }

    private static View tag(MainActivity activity, String tag) {
        View view = activity.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull("Control exists: " + tag, view);
        return view;
    }

    private static QuoridorBoardView board(MainActivity activity) {
        return (QuoridorBoardView) tag(activity, "game.board");
    }

    private static LocalMatch local(MainActivity activity) throws Exception {
        return (LocalMatch) field(activity, "local");
    }

    private static void tapCell(QuoridorBoardView board, int row, int col) {
        tapNode(board, row * 9 + col);
    }

    private static void tapNode(QuoridorBoardView board, int id) {
        AccessibilityNodeInfo node =
                board.getAccessibilityNodeProvider().createAccessibilityNodeInfo(id);
        assertNotNull(node);
        Rect bounds = new Rect();
        node.getBoundsInParent(bounds);
        node.recycle();
        assertTrue("Board laid out", bounds.width() > 0 && bounds.height() > 0);
        long time = SystemClock.uptimeMillis();
        MotionEvent down =
                MotionEvent.obtain(
                        time,
                        time,
                        MotionEvent.ACTION_DOWN,
                        bounds.exactCenterX(),
                        bounds.exactCenterY(),
                        0);
        MotionEvent up =
                MotionEvent.obtain(
                        time,
                        time + 40,
                        MotionEvent.ACTION_UP,
                        bounds.exactCenterX(),
                        bounds.exactCenterY(),
                        0);
        try {
            assertTrue(board.onTouchEvent(down));
            assertTrue(board.onTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private interface UiCheck {
        void run() throws Exception;
    }

    private interface Check {
        boolean evaluate() throws Exception;
    }

    private void ui(UiCheck check) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                check.run();
                            } catch (Throwable problem) {
                                failure.set(problem);
                            }
                        });
        Throwable problem = failure.get();
        if (problem instanceof Error) {
            throw (Error) problem;
        }
        if (problem instanceof Exception) {
            throw (Exception) problem;
        }
        getInstrumentation().waitForIdleSync();
    }

    private void waitUntil(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> complete = new AtomicReference<>(false);
            ui(() -> complete.set(check.evaluate()));
            if (complete.get()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Native robot worker did not complete within 10 seconds");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
