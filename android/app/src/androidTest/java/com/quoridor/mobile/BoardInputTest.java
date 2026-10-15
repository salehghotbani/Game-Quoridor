package com.quoridor.mobile;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import Board.GameState;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Real Android View/Canvas/touch tests; does not depend on Activity control IDs or mock Android.
 */
@SuppressWarnings("deprecation")
public final class BoardInputTest extends InstrumentationTestCase {
    public void testEveryWallAnchorPreviewsWithoutCommitting() throws Exception {
        onUi(
                () -> {
                    GameState game = new GameState();
                    GameState.Snapshot opening = game.snapshot();
                    Recording listener = new Recording();
                    QuoridorBoardView board = board(game, listener);
                    for (QuoridorBoardView.Mode mode :
                            new QuoridorBoardView.Mode[] {
                                QuoridorBoardView.Mode.HORIZONTAL, QuoridorBoardView.Mode.VERTICAL
                            }) {
                        board.setMode(mode);
                        assertEquals(
                                64,
                                board.getAccessibilityNodeProvider()
                                        .createAccessibilityNodeInfo(View.NO_ID)
                                        .getChildCount());
                        for (int row = 0; row < 8; row++) {
                            for (int col = 0; col < 8; col++) {
                                tap(board, bounds(board, 100 + row * 8 + col));
                                GameState.Wall selected = board.selectedWall();
                                assertNotNull(selected);
                                assertEquals(row, selected.row());
                                assertEquals(col, selected.col());
                                assertEquals(
                                        mode == QuoridorBoardView.Mode.HORIZONTAL
                                                ? GameState.Orientation.HORIZONTAL
                                                : GameState.Orientation.VERTICAL,
                                        selected.orientation());
                                assertEquals(selected, listener.wall);
                                assertEquals(
                                        game.canPlaceWall(row, col, selected.orientation()),
                                        listener.legal);
                                assertEquals(opening, game.snapshot());
                            }
                        }
                    }
                    int selections = listener.selections;
                    tap(board, bounds(board, 100));
                    tap(board, bounds(board, 100));
                    assertEquals(selections + 2, listener.selections);
                    assertEquals(opening, game.snapshot());
                    board.clearSelection();
                    assertNull(board.selectedWall());
                    assertNull(listener.wall);
                    assertFalse(listener.legal);
                });
    }

    public void testOnlyLegalMovesAreDispatchedAndDisabledInputIsFrozen() throws Exception {
        onUi(
                () -> {
                    GameState game = new GameState();
                    Recording listener = new Recording();
                    QuoridorBoardView board = board(game, listener);
                    assertEquals(
                            81,
                            board.getAccessibilityNodeProvider()
                                    .createAccessibilityNodeInfo(View.NO_ID)
                                    .getChildCount());
                    tap(board, bounds(board, 4)); // Opponent's starting pawn.
                    tap(board, bounds(board, 8 * 9 + 4)); // Own starting pawn.
                    assertEquals(0, listener.moves);
                    Rect first = bounds(board, 0);
                    Rect second = bounds(board, 1);
                    tapAt(board, (first.right + second.left) / 2f, first.exactCenterY());
                    assertEquals(0, listener.moves); // Tapping a gap in move mode is not a square.
                    tap(board, bounds(board, 7 * 9 + 4));
                    assertEquals(1, listener.moves);
                    assertEquals(7, listener.row);
                    assertEquals(4, listener.col);
                    assertEquals(new GameState.Cell(8, 4), game.playerPosition(0));

                    Rect move = bounds(board, 7 * 9 + 4);
                    gesture(
                            board,
                            move.exactCenterX(),
                            move.exactCenterY(),
                            move.exactCenterX() + 100,
                            move.exactCenterY());
                    assertEquals(1, listener.moves); // A scroll/drag cannot turn into a move.
                    board.setPosition(game, false);
                    tap(board, move);
                    board.setMode(QuoridorBoardView.Mode.HORIZONTAL);
                    tap(board, bounds(board, 100));
                    assertEquals(1, listener.moves);
                    assertEquals(0, listener.selections);
                    assertNull(board.selectedWall());
                });
    }

    public void testIllegalPreviewAndStaleSelectionAreReported() throws Exception {
        onUi(
                () -> {
                    GameState game = new GameState();
                    assertTrue(game.placeWall(0, 0, GameState.Orientation.HORIZONTAL));
                    Recording listener = new Recording();
                    QuoridorBoardView board = board(game, listener);
                    board.setMode(QuoridorBoardView.Mode.HORIZONTAL);
                    tap(board, bounds(board, 100));
                    assertEquals(
                            new GameState.Wall(0, 0, GameState.Orientation.HORIZONTAL),
                            board.selectedWall());
                    assertFalse(listener.legal);
                    assertEquals(9, game.wallsRemaining(0));
                    tap(board, bounds(board, 102));
                    assertEquals(
                            game.canPlaceWall(0, 2, GameState.Orientation.HORIZONTAL),
                            listener.legal);
                    assertNotNull(board.selectedWall());
                    assertTrue(game.moveTo(1, 4));
                    board.setPosition(game, true);
                    assertNull(board.selectedWall());
                    assertNull(listener.wall);
                    assertFalse(listener.legal);
                });
    }

    public void testPersianAndThemeDoNotMirrorOrMutateTheBoard() throws Exception {
        onUi(
                () -> {
                    GameState game = new GameState(4);
                    GameState.Snapshot opening = game.snapshot();
                    Recording listener = new Recording();
                    QuoridorBoardView board = board(game, listener);
                    board.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                    Rect physicalLeft = bounds(board, 4 * 9);
                    assertTrue(
                            board.getAccessibilityNodeProvider()
                                    .createAccessibilityNodeInfo(4 * 9)
                                    .getContentDescription()
                                    .toString()
                                    .contains("Red"));
                    Rect move = bounds(board, 7 * 9 + 4);
                    tap(board, move);
                    board.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    assertEquals(physicalLeft, bounds(board, 4 * 9));
                    assertTrue(
                            board.getAccessibilityNodeProvider()
                                    .createAccessibilityNodeInfo(4 * 9)
                                    .getContentDescription()
                                    .toString()
                                    .contains("قرمز"));
                    assertTrue(board.getContentDescription().toString().contains("آبی"));
                    tap(board, move);
                    assertEquals(2, listener.moves);
                    assertEquals(7, listener.row);
                    assertEquals(4, listener.col);
                    Rect empty = bounds(board, 0);
                    board.setMode(QuoridorBoardView.Mode.VERTICAL);
                    tap(board, bounds(board, 100 + 2 * 8 + 3));
                    GameState.Wall selected = board.selectedWall();
                    Bitmap dark = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888);
                    Bitmap light = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888);
                    try {
                        board.setDark(true);
                        board.draw(new Canvas(dark));
                        board.setDark(false);
                        board.draw(new Canvas(light));
                        assertTrue(
                                dark.getPixel(empty.centerX(), empty.centerY())
                                        != light.getPixel(empty.centerX(), empty.centerY()));
                        assertEquals(selected, board.selectedWall());
                        assertEquals(opening, game.snapshot());
                    } finally {
                        dark.recycle();
                        light.recycle();
                    }
                });
    }

    public void testVirtualAccessibilityActionsAndFocus() throws Exception {
        onUi(
                () -> {
                    Recording listener = new Recording();
                    QuoridorBoardView board = board(new GameState(), listener);
                    AccessibilityNodeProvider nodes = board.getAccessibilityNodeProvider();
                    assertTrue(
                            nodes.performAction(
                                    7 * 9 + 4, AccessibilityNodeInfo.ACTION_CLICK, null));
                    assertEquals(1, listener.moves);
                    assertFalse(nodes.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null));
                    assertTrue(
                            nodes.performAction(
                                    7 * 9 + 4,
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                                    null));
                    assertNotNull(nodes.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
                    assertTrue(
                            nodes.performAction(
                                    7 * 9 + 4,
                                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                                    null));
                    assertNull(nodes.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
                    board.setMode(QuoridorBoardView.Mode.HORIZONTAL);
                    assertTrue(
                            nodes.performAction(
                                    100 + 3 * 8 + 2, AccessibilityNodeInfo.ACTION_CLICK, null));
                    assertEquals(
                            new GameState.Wall(3, 2, GameState.Orientation.HORIZONTAL),
                            board.selectedWall());
                    board.setPosition(new GameState(), false);
                    assertFalse(nodes.performAction(100, AccessibilityNodeInfo.ACTION_CLICK, null));
                });
    }

    public void testRectangularLayoutKeepsSquarePhysicalGeometry() throws Exception {
        onUi(
                () -> {
                    QuoridorBoardView board = board(new GameState(), new Recording());
                    board.measure(
                            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
                    board.layout(0, 0, board.getMeasuredWidth(), board.getMeasuredHeight());
                    assertEquals(900, board.getMeasuredWidth());
                    assertEquals(600, board.getMeasuredHeight());
                    Rect topLeft = bounds(board, 0);
                    Rect bottomRight = bounds(board, 80);
                    assertTrue(
                            Math.abs(
                                            (bottomRight.right - topLeft.left)
                                                    - (bottomRight.bottom - topLeft.top))
                                    <= 1);
                    assertTrue(topLeft.left >= 0 && topLeft.top >= 0);
                    assertTrue(bottomRight.right <= 900 && bottomRight.bottom <= 600);
                    assertTrue(Math.abs((topLeft.left + bottomRight.right) / 2f - 450) <= 1);
                });
    }

    public void testLandscapeScrollAndPortraitMeasurementsPreserveInputAndGlobalConfiguration()
            throws Exception {
        onUi(this::checkLandscapeScrollAndPortraitMeasurement);
    }

    private void checkLandscapeScrollAndPortraitMeasurement() {
        Context original = getInstrumentation().getTargetContext();
        Configuration before = new Configuration(original.getResources().getConfiguration());
        Configuration landscape = new Configuration(before);
        landscape.orientation = Configuration.ORIENTATION_LANDSCAPE;
        landscape.screenWidthDp = 914;
        landscape.screenHeightDp = 411;
        Context landscapeContext = original.createConfigurationContext(landscape);
        float density = landscapeContext.getResources().getDisplayMetrics().density;
        int width = Math.round(914 * density);
        int viewportHeight = Math.round(411 * density);
        Recording listener = new Recording();
        QuoridorBoardView wide = new QuoridorBoardView(landscapeContext);
        wide.setListener(listener);
        wide.setPosition(new GameState(), true);
        wide.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        wide.layout(0, 0, wide.getMeasuredWidth(), wide.getMeasuredHeight());
        assertEquals(width, wide.getMeasuredWidth());
        assertTrue(
                "Whole board fits landscape viewport", wide.getMeasuredHeight() < viewportHeight);
        Rect first = bounds(wide, 0);
        Rect last = bounds(wide, 80);
        assertTrue(last.bottom <= wide.getMeasuredHeight());
        assertTrue(Math.abs((last.right - first.left) - (last.bottom - first.top)) <= 1);
        assertTrue(Math.abs((first.left + last.right) / 2f - width / 2f) <= 1);
        tap(wide, bounds(wide, 7 * 9 + 4));
        assertEquals(1, listener.moves);
        assertEquals(7, listener.row);
        assertEquals(4, listener.col);
        wide.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        assertEquals(first, bounds(wide, 0));
        wide.setMode(QuoridorBoardView.Mode.VERTICAL);
        tap(wide, bounds(wide, 100 + 7 * 8 + 7));
        assertEquals(new GameState.Wall(7, 7, GameState.Orientation.VERTICAL), listener.wall);

        Configuration portrait = new Configuration(before);
        portrait.orientation = Configuration.ORIENTATION_PORTRAIT;
        portrait.screenWidthDp = 411;
        portrait.screenHeightDp = 914;
        QuoridorBoardView tall =
                new QuoridorBoardView(original.createConfigurationContext(portrait));
        int portraitWidth = Math.round(411 * density);
        tall.measure(
                View.MeasureSpec.makeMeasureSpec(portraitWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(
                "Portrait intrinsic square is unchanged", portraitWidth, tall.getMeasuredHeight());
        assertEquals(before.orientation, original.getResources().getConfiguration().orientation);
        assertEquals(
                before.screenHeightDp, original.getResources().getConfiguration().screenHeightDp);
        assertEquals(
                before.screenWidthDp, original.getResources().getConfiguration().screenWidthDp);
    }

    private QuoridorBoardView board(GameState game, Recording listener) {
        QuoridorBoardView board = new QuoridorBoardView(getInstrumentation().getTargetContext());
        board.setListener(listener);
        board.setPosition(game, true);
        board.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        board.layout(0, 0, 900, 900);
        return board;
    }

    private static Rect bounds(QuoridorBoardView board, int node) {
        AccessibilityNodeInfo info =
                board.getAccessibilityNodeProvider().createAccessibilityNodeInfo(node);
        assertNotNull(info);
        Rect bounds = new Rect();
        info.getBoundsInParent(bounds);
        info.recycle();
        return bounds;
    }

    private static void tap(QuoridorBoardView board, Rect bounds) {
        tapAt(board, bounds.exactCenterX(), bounds.exactCenterY());
    }

    private static void tapAt(QuoridorBoardView board, float x, float y) {
        gesture(board, x, y, x, y);
    }

    private static void gesture(QuoridorBoardView board, float x, float y, float endX, float endY) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, endX, endY, 0);
        try {
            board.onTouchEvent(down);
            board.onTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private interface UiCheck {
        void run() throws Exception;
    }

    private void onUi(UiCheck check) throws Exception {
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
    }

    private static final class Recording implements QuoridorBoardView.Listener {
        int moves;
        int selections;
        int row;
        int col;
        GameState.Wall wall;
        boolean legal;

        @Override
        public void onMove(int row, int col) {
            moves++;
            this.row = row;
            this.col = col;
        }

        @Override
        public void onWallSelected(GameState.Wall wall, boolean legal) {
            selections++;
            this.wall = wall;
            this.legal = legal;
        }
    }
}
