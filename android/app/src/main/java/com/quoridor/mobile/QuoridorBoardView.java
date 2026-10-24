package com.quoridor.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import Board.GameState;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Native board drawing and input. Only the activity commits a selected wall. */
public final class QuoridorBoardView extends View {
    public enum Mode {
        MOVE,
        HORIZONTAL,
        VERTICAL
    }

    public interface Listener {
        void onMove(int row, int col);

        /** A null wall clears the activity's pending confirmation. */
        void onWallSelected(GameState.Wall wall, boolean legal);
    }

    private static final int SIZE = GameState.SIZE;
    private static final int WALL_ANCHORS = SIZE - 1;
    private static final int WALL_NODE_OFFSET = 100;
    private static final int NO_NODE = Integer.MIN_VALUE;
    private static final float GAP_FRACTION = 0.18f;
    private static final int VIEWPORT_MARGIN_DP = 72;
    private static final String[] ENGLISH_PLAYERS = {"Blue", "Green", "Red", "Gold"};
    private static final String[] PERSIAN_PLAYERS = {"آبی", "سبز", "قرمز", "طلایی"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF grid = new RectF();
    private final RectF rectangle = new RectF();
    private final Path pawnBody = new Path();
    private final int[] screenLocation = new int[2];
    private final int touchSlop;
    private final AccessibilityManager accessibility;
    private final AccessibilityNodeProvider nodeProvider = new BoardNodes();
    private GameState game = new GameState();
    private Set<GameState.Cell> legalMoves = Collections.emptySet();
    private Listener listener;
    private Mode mode = Mode.MOVE;
    private GameState.Wall selectedWall;
    private GameState.Cell hint;
    private GameState.Wall wallHint;
    private boolean selectedLegal;
    private boolean inputEnabled;
    private boolean dark = true;
    private float pitch;
    private float tile;
    private float gap;
    private float downX;
    private float downY;
    private boolean trackingTouch;
    private int keyboardRow = SIZE - 1;
    private int keyboardCol = SIZE / 2;
    private int accessibilityFocus = NO_NODE;
    private int hoveredNode = NO_NODE;

    public QuoridorBoardView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        accessibility =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setMinimumWidth(dp(240));
        setMinimumHeight(dp(240));
        updateDescription();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPosition(GameState position, boolean enabled) {
        Objects.requireNonNull(position, "position");
        boolean changed = !game.snapshot().equals(position.snapshot());
        boolean wasEnabled = inputEnabled;
        game = position.copy();
        inputEnabled = enabled && game.winner() < 0;
        legalMoves = inputEnabled ? game.legalMoves() : Collections.emptySet();
        if (changed) {
            GameState.Cell pawn = game.playerPosition(game.currentPlayer());
            keyboardRow = pawn.row();
            keyboardCol = pawn.col();
            hint = null;
        }
        if (changed || !inputEnabled) {
            clearSelection();
        }
        updateDescription();
        invalidate();
        if (changed || wasEnabled != inputEnabled) {
            announceBoardChanged();
        }
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        invalidate();
    }

    public void setMode(Mode mode) {
        Mode replacement = Objects.requireNonNull(mode, "mode");
        if (this.mode == replacement) {
            return;
        }
        clearVirtualFocus();
        this.mode = replacement;
        clearSelection();
        updateDescription();
        announceBoardChanged();
        invalidate();
    }

    public void setHint(GameState.Cell hint) {
        this.hint = hint != null && legalMoves.contains(hint) ? hint : null;
        invalidate();
    }

    public void setWallHint(GameState.Wall wall) {
        wallHint =
                wall != null && game.canPlaceWall(wall.row(), wall.col(), wall.orientation())
                        ? wall
                        : null;
        invalidate();
    }

    public void focusPosition(GameState.Cell cell) {
        if (cell == null
                || cell.row() < 0
                || cell.row() >= SIZE
                || cell.col() < 0
                || cell.col() >= SIZE) {
            throw new IllegalArgumentException("Invalid focus position");
        }
        keyboardRow = cell.row();
        keyboardCol = cell.col();
        invalidate();
    }

    public void clearSelection() {
        boolean hadSelection = selectedWall != null;
        selectedWall = null;
        selectedLegal = false;
        if (hadSelection && listener != null) {
            listener.onWallSelected(null, false);
        }
        invalidate();
    }

    public GameState.Wall selectedWall() {
        return selectedWall;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = dp(360);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            available =
                    Math.max(
                            0,
                            MeasureSpec.getSize(widthMeasureSpec)
                                    - getPaddingLeft()
                                    - getPaddingRight());
        }
        // ScrollView gives wrap-content children unbounded height. Respect the current window's
        // configuration so a wide landscape board can still fit after the headers scroll away.
        int screenHeightDp = getResources().getConfiguration().screenHeightDp;
        int viewportHeight =
                screenHeightDp > 0
                        ? dp(screenHeightDp)
                        : getResources().getDisplayMetrics().heightPixels;
        if (viewportHeight > 0) {
            available = Math.min(available, Math.max(1, viewportHeight - dp(VIEWPORT_MARGIN_DP)));
        }
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            available =
                    Math.min(
                            available,
                            Math.max(
                                    0,
                                    MeasureSpec.getSize(heightMeasureSpec)
                                            - getPaddingTop()
                                            - getPaddingBottom()));
        }
        setMeasuredDimension(
                resolveSize(available + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(available + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateGeometry();
    }

    private void updateGeometry() {
        float width = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        float height = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        float side = Math.min(width, height);
        float margin = side * 0.045f;
        float left = getPaddingLeft() + (width - side) / 2 + margin;
        float top = getPaddingTop() + (height - side) / 2 + margin;
        float extent = Math.max(0, side - margin * 2);
        grid.set(left, top, left + extent, top + extent);
        pitch = extent / (SIZE - GAP_FRACTION);
        gap = pitch * GAP_FRACTION;
        tile = pitch - gap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateGeometry();
        if (pitch <= 0) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(
                new LinearGradient(
                        grid.left,
                        grid.top,
                        grid.right,
                        grid.bottom,
                        dark ? Color.rgb(47, 65, 87) : Color.rgb(216, 227, 237),
                        dark ? Color.rgb(12, 23, 39) : Color.rgb(177, 195, 213),
                        Shader.TileMode.CLAMP));
        canvas.drawRoundRect(
                grid.left - gap,
                grid.top - gap,
                grid.right + gap,
                grid.bottom + gap,
                gap,
                gap,
                paint);
        paint.setShader(null);
        drawGoals(canvas);
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                cellBounds(row, col, rectangle);
                drawTile(canvas, row, col);
                GameState.Cell cell = new GameState.Cell(row, col);
                if (inputEnabled && mode == Mode.MOVE && legalMoves.contains(cell)) {
                    paint.setColor(playerColor(game.currentPlayer()));
                    canvas.drawCircle(
                            rectangle.centerX(), rectangle.centerY(), tile * 0.13f, paint);
                }
                if (cell.equals(hint)) {
                    paint.setColor(dark ? Color.WHITE : Color.rgb(12, 41, 67));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(dp(2), tile * 0.06f));
                    canvas.drawCircle(
                            rectangle.centerX(), rectangle.centerY(), tile * 0.34f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
        for (int row = 0; row < WALL_ANCHORS; row++) {
            for (int col = 0; col < WALL_ANCHORS; col++) {
                for (GameState.Orientation orientation : GameState.Orientation.values()) {
                    if (game.hasWall(row, col, orientation)) {
                        drawWall(canvas, new GameState.Wall(row, col, orientation), false);
                    }
                }
            }
        }
        for (int player = 0; player < game.playerCount(); player++) {
            drawPawn(canvas, player);
        }
        if (selectedWall != null) {
            drawWall(canvas, selectedWall, true);
        }
        if (wallHint != null && selectedWall == null) {
            wallBounds(wallHint, rectangle);
            drawFocus(canvas, rectangle);
        }
        if (hasFocus() && inputEnabled) {
            if (mode == Mode.MOVE) {
                cellBounds(keyboardRow, keyboardCol, rectangle);
            } else {
                virtualBounds(
                        WALL_NODE_OFFSET
                                + Math.min(keyboardRow, WALL_ANCHORS - 1) * WALL_ANCHORS
                                + Math.min(keyboardCol, WALL_ANCHORS - 1),
                        rectangle);
            }
            drawFocus(canvas, rectangle);
        }
        if (accessibilityFocus != NO_NODE) {
            virtualBounds(accessibilityFocus, rectangle);
            drawFocus(canvas, rectangle);
        }
    }

    private void drawTile(Canvas canvas, int row, int col) {
        int base =
                dark
                        ? ((row + col) % 2 == 0 ? Color.rgb(43, 64, 89) : Color.rgb(38, 57, 79))
                        : ((row + col) % 2 == 0
                                ? Color.rgb(229, 237, 245)
                                : Color.rgb(220, 230, 240));
        float depth = tile * 0.06f;
        paint.setColor(dark ? Color.rgb(15, 28, 44) : Color.rgb(155, 177, 198));
        canvas.drawRoundRect(
                rectangle.left,
                rectangle.top + depth,
                rectangle.right,
                rectangle.bottom,
                tile * 0.19f,
                tile * 0.19f,
                paint);
        paint.setShader(
                new LinearGradient(
                        rectangle.left,
                        rectangle.top,
                        rectangle.right,
                        rectangle.bottom,
                        blend(base, Color.WHITE, 0.12f),
                        base,
                        Shader.TileMode.CLAMP));
        canvas.drawRoundRect(
                rectangle.left,
                rectangle.top,
                rectangle.right,
                rectangle.bottom - depth,
                tile * 0.19f,
                tile * 0.19f,
                paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, tile * 0.02f));
        paint.setColor(blend(base, Color.WHITE, dark ? 0.12f : 0.65f));
        canvas.drawRoundRect(
                rectangle.left + 1,
                rectangle.top + 1,
                rectangle.right - 1,
                rectangle.bottom - depth - 1,
                tile * 0.19f,
                tile * 0.19f,
                paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGoals(Canvas canvas) {
        float offset = gap * 0.65f;
        paint.setStrokeWidth(Math.max(dp(2), gap * 0.4f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(playerColor(0));
        canvas.drawLine(grid.left, grid.top - offset, grid.right, grid.top - offset, paint);
        paint.setColor(playerColor(1));
        canvas.drawLine(grid.left, grid.bottom + offset, grid.right, grid.bottom + offset, paint);
        if (game.playerCount() == 4) {
            paint.setColor(playerColor(2));
            canvas.drawLine(grid.right + offset, grid.top, grid.right + offset, grid.bottom, paint);
            paint.setColor(playerColor(3));
            canvas.drawLine(grid.left - offset, grid.top, grid.left - offset, grid.bottom, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawPawn(Canvas canvas, int player) {
        GameState.Cell position = game.playerPosition(player);
        cellBounds(position.row(), position.col(), rectangle);
        float center = rectangle.centerX();
        float top = rectangle.top;
        int color = playerColor(player);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(
                center - tile * 0.33f,
                top + tile * 0.73f,
                center + tile * 0.35f,
                top + tile * 0.91f,
                paint);
        if (game.currentPlayer() == player) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(1.5f), tile * 0.045f));
            paint.setColor(color);
            canvas.drawCircle(center, rectangle.centerY(), tile * 0.43f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        pawnBody.reset();
        pawnBody.moveTo(center - tile * 0.13f, top + tile * 0.36f);
        pawnBody.cubicTo(
                center - tile * 0.08f,
                top + tile * 0.52f,
                center - tile * 0.21f,
                top + tile * 0.67f,
                center - tile * 0.26f,
                top + tile * 0.74f);
        pawnBody.quadTo(center, top + tile * 0.89f, center + tile * 0.26f, top + tile * 0.74f);
        pawnBody.cubicTo(
                center + tile * 0.21f,
                top + tile * 0.67f,
                center + tile * 0.08f,
                top + tile * 0.52f,
                center + tile * 0.13f,
                top + tile * 0.36f);
        pawnBody.close();
        paint.setAlpha(255);
        paint.setShader(
                new LinearGradient(
                        center - tile * 0.25f,
                        top,
                        center + tile * 0.3f,
                        top + tile,
                        blend(color, Color.WHITE, 0.6f),
                        blend(color, Color.BLACK, 0.3f),
                        Shader.TileMode.CLAMP));
        canvas.drawPath(pawnBody, paint);
        canvas.drawRoundRect(
                center - tile * 0.29f,
                top + tile * 0.74f,
                center + tile * 0.29f,
                top + tile * 0.85f,
                tile * 0.06f,
                tile * 0.06f,
                paint);
        canvas.drawCircle(center, top + tile * 0.25f, tile * 0.18f, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(120, 255, 255, 255));
        canvas.drawOval(
                center - tile * 0.1f,
                top + tile * 0.12f,
                center - tile * 0.01f,
                top + tile * 0.18f,
                paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(tile * 0.21f);
        paint.setFakeBoldText(true);
        float baseline = top + tile * 0.61f - (paint.ascent() + paint.descent()) / 2;
        canvas.drawText(number(player + 1), rectangle.centerX(), baseline, paint);
        paint.setFakeBoldText(false);
    }

    private void drawWall(Canvas canvas, GameState.Wall wall, boolean preview) {
        wallBounds(wall, rectangle);
        paint.setStyle(Paint.Style.FILL);
        int color =
                preview
                        ? (selectedLegal
                                ? (dark ? Color.rgb(95, 233, 184) : Color.rgb(9, 119, 80))
                                : (dark ? Color.rgb(255, 115, 121) : Color.rgb(180, 43, 57)))
                        : (dark ? Color.rgb(240, 200, 139) : Color.rgb(144, 99, 45));
        paint.setColor(Color.argb(65, 0, 0, 0));
        canvas.drawRoundRect(
                rectangle.left,
                rectangle.top + gap * 0.2f,
                rectangle.right,
                rectangle.bottom + gap * 0.2f,
                gap * 0.3f,
                gap * 0.3f,
                paint);
        paint.setShader(
                new LinearGradient(
                        rectangle.left,
                        rectangle.top,
                        rectangle.right,
                        rectangle.bottom,
                        blend(color, Color.WHITE, 0.45f),
                        color,
                        Shader.TileMode.CLAMP));
        paint.setAlpha(preview ? 220 : 255);
        canvas.drawRoundRect(rectangle, gap * 0.3f, gap * 0.3f, paint);
        paint.setShader(null);
        paint.setAlpha(255);
        if (preview) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(dark ? Color.WHITE : Color.rgb(10, 30, 47));
            paint.setStrokeWidth(Math.max(1, gap * 0.15f));
            canvas.drawRoundRect(rectangle, gap * 0.3f, gap * 0.3f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawFocus(Canvas canvas, RectF bounds) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(dark ? Color.rgb(255, 216, 115) : Color.rgb(104, 64, 0));
        canvas.drawRoundRect(bounds, tile * 0.1f, tile * 0.1f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int playerColor(int player) {
        return MobileStyle.playerColor(player, dark);
    }

    private static int blend(int color, int target, float amount) {
        return Color.rgb(
                Math.round(Color.red(color) * (1 - amount) + Color.red(target) * amount),
                Math.round(Color.green(color) * (1 - amount) + Color.green(target) * amount),
                Math.round(Color.blue(color) * (1 - amount) + Color.blue(target) * amount));
    }

    private void cellBounds(int row, int col, RectF out) {
        float left = grid.left + col * pitch;
        float top = grid.top + row * pitch;
        out.set(left, top, left + tile, top + tile);
    }

    private void wallBounds(GameState.Wall wall, RectF out) {
        float left = grid.left + wall.col() * pitch;
        float top = grid.top + wall.row() * pitch;
        float halfWidth = gap * 0.4f;
        if (wall.orientation() == GameState.Orientation.HORIZONTAL) {
            float center = top + pitch - gap / 2;
            out.set(left, center - halfWidth, left + pitch * 2 - gap, center + halfWidth);
        } else {
            float center = left + pitch - gap / 2;
            out.set(center - halfWidth, top, center + halfWidth, top + pitch * 2 - gap);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!inputEnabled || pitch <= 0) {
            return false;
        }
        if (event.getPointerCount() > 1) {
            trackingTouch = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            trackingTouch = grid.contains(event.getX(), event.getY());
            downX = event.getX();
            downY = event.getY();
            return trackingTouch;
        }
        if (!trackingTouch) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            trackingTouch = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            trackingTouch = false;
            if (Math.abs(event.getX() - downX) <= touchSlop
                    && Math.abs(event.getY() - downY) <= touchSlop) {
                int node = hitNode(event.getX(), event.getY());
                if (node != NO_NODE) {
                    performClick();
                    activateNode(node);
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int hitNode(float x, float y) {
        if (pitch <= 0 || !grid.contains(x, y)) {
            return NO_NODE;
        }
        if (mode == Mode.MOVE) {
            int row = Math.min(SIZE - 1, (int) ((y - grid.top) / pitch));
            int col = Math.min(SIZE - 1, (int) ((x - grid.left) / pitch));
            cellBounds(row, col, rectangle);
            return rectangle.contains(x, y) ? row * SIZE + col : NO_NODE;
        }
        int row = clamp(Math.round((y - grid.top + gap / 2) / pitch) - 1, 0, WALL_ANCHORS - 1);
        int col = clamp(Math.round((x - grid.left + gap / 2) / pitch) - 1, 0, WALL_ANCHORS - 1);
        return WALL_NODE_OFFSET + row * WALL_ANCHORS + col;
    }

    private boolean activateNode(int node) {
        if (!inputEnabled || listener == null || !validNode(node)) {
            return false;
        }
        if (mode == Mode.MOVE) {
            int row = node / SIZE;
            int col = node % SIZE;
            if (!legalMoves.contains(new GameState.Cell(row, col))) {
                return false;
            }
            listener.onMove(row, col);
        } else {
            int anchor = node - WALL_NODE_OFFSET;
            selectedWall =
                    new GameState.Wall(
                            anchor / WALL_ANCHORS,
                            anchor % WALL_ANCHORS,
                            mode == Mode.HORIZONTAL
                                    ? GameState.Orientation.HORIZONTAL
                                    : GameState.Orientation.VERTICAL);
            selectedLegal =
                    game.canPlaceWall(
                            selectedWall.row(), selectedWall.col(), selectedWall.orientation());
            listener.onWallSelected(selectedWall, selectedLegal);
            invalidate();
        }
        sendVirtualEvent(node, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!inputEnabled) {
            return super.onKeyDown(keyCode, event);
        }
        int limit = mode == Mode.MOVE ? SIZE - 1 : WALL_ANCHORS - 1;
        keyboardRow = Math.min(keyboardRow, limit);
        keyboardCol = Math.min(keyboardCol, limit);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                keyboardRow = Math.max(0, keyboardRow - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                keyboardRow = Math.min(limit, keyboardRow + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                keyboardCol = Math.max(0, keyboardCol - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                keyboardCol = Math.min(limit, keyboardCol + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                return activateNode(
                        mode == Mode.MOVE
                                ? keyboardRow * SIZE + keyboardCol
                                : WALL_NODE_OFFSET + keyboardRow * WALL_ANCHORS + keyboardCol);
            default:
                return super.onKeyDown(keyCode, event);
        }
        invalidate();
        return true;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (game != null) {
            updateDescription();
            announceBoardChanged();
            invalidate();
        }
    }

    private boolean persian() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    private String number(int value) {
        return String.format(persian() ? new Locale("fa", "IR") : Locale.ENGLISH, "%d", value);
    }

    private String playerName(int player) {
        return (persian() ? PERSIAN_PLAYERS : ENGLISH_PLAYERS)[player];
    }

    private void updateDescription() {
        StringBuilder text = new StringBuilder(persian() ? "صفحهٔ کوریدور. " : "Quoridor board. ");
        for (int player = 0; player < game.playerCount(); player++) {
            GameState.Cell cell = game.playerPosition(player);
            text.append(playerName(player))
                    .append(persian() ? "، ردیف " : ", row ")
                    .append(number(cell.row() + 1))
                    .append(persian() ? "، ستون " : ", column ")
                    .append(number(cell.col() + 1))
                    .append(". ");
        }
        setContentDescription(text);
    }

    private String describeNode(int node) {
        if (node < WALL_NODE_OFFSET) {
            int row = node / SIZE;
            int col = node % SIZE;
            String description =
                    (persian() ? "خانه، ردیف " : "Square, row ")
                            + number(row + 1)
                            + (persian() ? "، ستون " : ", column ")
                            + number(col + 1);
            for (int player = 0; player < game.playerCount(); player++) {
                GameState.Cell position = game.playerPosition(player);
                if (position.row() == row && position.col() == col) {
                    description += (persian() ? "، مهرهٔ " : ", pawn ") + playerName(player);
                }
            }
            if (inputEnabled && legalMoves.contains(new GameState.Cell(row, col))) {
                description += persian() ? "، حرکت مجاز" : ", legal move";
            }
            return description;
        }
        int anchor = node - WALL_NODE_OFFSET;
        return (mode == Mode.HORIZONTAL
                        ? (persian() ? "دیوار افقی" : "Horizontal wall")
                        : (persian() ? "دیوار عمودی" : "Vertical wall"))
                + (persian() ? "، ردیف " : ", row ")
                + number(anchor / WALL_ANCHORS + 1)
                + (persian() ? "، ستون " : ", column ")
                + number(anchor % WALL_ANCHORS + 1);
    }

    private boolean validNode(int node) {
        return mode == Mode.MOVE
                ? node >= 0 && node < SIZE * SIZE
                : node >= WALL_NODE_OFFSET && node < WALL_NODE_OFFSET + WALL_ANCHORS * WALL_ANCHORS;
    }

    private void virtualBounds(int node, RectF out) {
        if (node < WALL_NODE_OFFSET) {
            cellBounds(node / SIZE, node % SIZE, out);
        } else {
            int anchor = node - WALL_NODE_OFFSET;
            float centerX = grid.left + (anchor % WALL_ANCHORS + 1) * pitch - gap / 2;
            float centerY = grid.top + (anchor / WALL_ANCHORS + 1) * pitch - gap / 2;
            out.set(
                    centerX - pitch / 2,
                    centerY - pitch / 2,
                    centerX + pitch / 2,
                    centerY + pitch / 2);
        }
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return nodeProvider;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (accessibility != null
                && accessibility.isEnabled()
                && accessibility.isTouchExplorationEnabled()) {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                if (hoveredNode != NO_NODE) {
                    sendVirtualEvent(hoveredNode, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                    hoveredNode = NO_NODE;
                }
                return true;
            }
            int node = hitNode(event.getX(), event.getY());
            if (node != hoveredNode) {
                if (node != NO_NODE) {
                    sendVirtualEvent(node, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                }
                if (hoveredNode != NO_NODE) {
                    sendVirtualEvent(hoveredNode, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                }
                hoveredNode = node;
            }
            return node != NO_NODE;
        }
        return super.dispatchHoverEvent(event);
    }

    private void sendVirtualEvent(int node, int type) {
        if (accessibility == null || !accessibility.isEnabled() || getParent() == null) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.Button");
        event.setSource(this, node);
        event.setContentDescription(describeNode(node));
        getParent().requestSendAccessibilityEvent(this, event);
    }

    private void announceBoardChanged() {
        if (accessibility != null && accessibility.isEnabled()) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    private void clearVirtualFocus() {
        if (accessibilityFocus != NO_NODE) {
            sendVirtualEvent(
                    accessibilityFocus, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            accessibilityFocus = NO_NODE;
        }
        hoveredNode = NO_NODE;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class BoardNodes extends AccessibilityNodeProvider {
        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int node) {
            if (node == View.NO_ID) {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(QuoridorBoardView.this);
                onInitializeAccessibilityNodeInfo(info);
                int count = mode == Mode.MOVE ? SIZE * SIZE : WALL_ANCHORS * WALL_ANCHORS;
                for (int index = 0; index < count; index++) {
                    info.addChild(
                            QuoridorBoardView.this,
                            mode == Mode.MOVE ? index : WALL_NODE_OFFSET + index);
                }
                return info;
            }
            if (!validNode(node)) {
                return null;
            }
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            info.setPackageName(getContext().getPackageName());
            info.setClassName("android.widget.Button");
            info.setSource(QuoridorBoardView.this, node);
            info.setParent(QuoridorBoardView.this);
            info.setContentDescription(describeNode(node));
            info.setEnabled(true);
            boolean clickable =
                    inputEnabled
                            && (mode != Mode.MOVE
                                    || legalMoves.contains(
                                            new GameState.Cell(node / SIZE, node % SIZE)));
            info.setClickable(clickable);
            info.setFocusable(true);
            info.setVisibleToUser(isShown());
            virtualBounds(node, rectangle);
            Rect bounds = new Rect();
            rectangle.roundOut(bounds);
            info.setBoundsInParent(bounds);
            getLocationOnScreen(screenLocation);
            bounds.offset(screenLocation[0], screenLocation[1]);
            info.setBoundsInScreen(bounds);
            if (clickable) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            }
            boolean focused = node == accessibilityFocus;
            info.setAccessibilityFocused(focused);
            info.addAction(
                    focused
                            ? AccessibilityNodeInfo.AccessibilityAction
                                    .ACTION_CLEAR_ACCESSIBILITY_FOCUS
                            : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            return info;
        }

        @Override
        public boolean performAction(int node, int action, Bundle arguments) {
            if (node == View.NO_ID) {
                return QuoridorBoardView.this.performAccessibilityAction(action, arguments);
            }
            if (!validNode(node)) {
                return false;
            }
            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                return activateNode(node);
            }
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                if (accessibilityFocus == node) {
                    return false;
                }
                clearVirtualFocus();
                accessibilityFocus = node;
                sendVirtualEvent(node, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                invalidate();
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    && accessibilityFocus == node) {
                clearVirtualFocus();
                invalidate();
                return true;
            }
            return false;
        }

        @Override
        public AccessibilityNodeInfo findFocus(int focus) {
            return focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
                            && accessibilityFocus != NO_NODE
                    ? createAccessibilityNodeInfo(accessibilityFocus)
                    : null;
        }
    }
}
