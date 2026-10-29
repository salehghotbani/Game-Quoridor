package Board;

import Board.BoardWidgets.CellButton;
import Board.BoardWidgets.SlotButton;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JPanel;

/** Fixed game coordinates, board geometry, and hover/focus wall previews. */
final class BoardSurface extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final double BASE = 536;
    private static final double CELL = 44;
    private static final double GAP = 10;
    private static final double STEP = CELL + GAP;
    private static final double PAD = 30;
    private final CellButton[][] cells;

    private record Target(SlotButton button, GameState.Wall wall) {}

    private final List<Target> targets = new ArrayList<>();
    private final Supplier<GameState> gameSource;
    private final Function<String, String> text;
    private GameState.Wall preview;
    private boolean previewLegal;

    void clearPreview() {
        preview = null;
    }

    void previewWall(GameState.Wall anchor) {
        preview = anchor;
        previewLegal = true;
        repaint();
    }

    private double scale = 1;
    private int originX;
    private int originY;

    BoardSurface(
            CellButton[][] cells,
            Supplier<GameState> gameSource,
            Function<String, String> text,
            BiConsumer<Integer, Integer> movePawn,
            Consumer<GameState.Wall> placeWall) {
        this.cells = cells;
        this.gameSource = gameSource;
        this.text = text;
        setLayout(null);
        setOpaque(false);
        setPreferredSize(new Dimension((int) BASE, (int) BASE));
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                final int r = row;
                final int c = col;
                CellButton button = new CellButton();
                button.setName("cell-" + row + "-" + col);
                button.addActionListener(event -> movePawn.accept(r, c));
                cells[row][col] = button;
                add(button);
            }
        }
        for (GameState.Orientation direction : GameState.Orientation.values()) {
            for (int row = 0; row < GameState.SIZE - 1; row++) {
                for (int col = 0; col < GameState.SIZE - 1; col++) {
                    GameState.Wall wall = new GameState.Wall(row, col, direction);
                    SlotButton slot = new SlotButton();
                    slot.setName(
                            (direction == GameState.Orientation.HORIZONTAL ? "wall-" : "wall-v-")
                                    + row
                                    + "-"
                                    + col);
                    slot.addActionListener(event -> placeWall.accept(wall));
                    slot.setFocusable(false);
                    slot.registerKeyboardAction(
                            event -> slot.doClick(),
                            javax.swing.KeyStroke.getKeyStroke("ENTER"),
                            javax.swing.JComponent.WHEN_FOCUSED);
                    bindArrow(slot, wall, "LEFT", 0, -1);
                    bindArrow(slot, wall, "RIGHT", 0, 1);
                    bindArrow(slot, wall, "UP", -1, 0);
                    bindArrow(slot, wall, "DOWN", 1, 0);
                    slot.addMouseListener(
                            new MouseAdapter() {
                                @Override
                                public void mouseEntered(MouseEvent event) {
                                    showPreview(slot, wall);
                                }

                                @Override
                                public void mouseExited(MouseEvent event) {
                                    clearPreview();
                                    repaint();
                                }
                            });
                    slot.addFocusListener(
                            new FocusAdapter() {
                                @Override
                                public void focusGained(FocusEvent event) {
                                    setWallFocus(slot, wall.orientation());
                                    showPreview(slot, wall);
                                }

                                @Override
                                public void focusLost(FocusEvent event) {
                                    clearPreview();
                                    repaint();
                                }
                            });
                    targets.add(new Target(slot, wall));
                    add(slot);
                }
            }
        }
    }

    private void setWallFocus(SlotButton selected, GameState.Orientation orientation) {
        for (Target target : targets) {
            if (target.wall().orientation() == orientation)
                target.button().setFocusable(target.button() == selected);
        }
    }

    private void bindArrow(SlotButton slot, GameState.Wall wall, String key, int dr, int dc) {
        slot.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(key), key);
        slot.getActionMap()
                .put(
                        key,
                        new javax.swing.AbstractAction() {
                            @Override
                            public void actionPerformed(java.awt.event.ActionEvent event) {
                                int row = wall.row() + dr, col = wall.col() + dc;
                                while (row >= 0
                                        && row < GameState.SIZE - 1
                                        && col >= 0
                                        && col < GameState.SIZE - 1) {
                                    GameState.Wall next =
                                            new GameState.Wall(row, col, wall.orientation());
                                    for (Target target : targets) {
                                        if (target.wall().equals(next)
                                                && target.button().isEnabled()) {
                                            setWallFocus(target.button(), wall.orientation());
                                            target.button().requestFocusInWindow();
                                            return;
                                        }
                                    }
                                    row += dr;
                                    col += dc;
                                }
                            }
                        });
    }

    void updateWalls(Predicate<GameState.Wall> allowed, Function<GameState.Wall, String> label) {
        GameState game = gameSource.get();
        for (Target target : targets) {
            GameState.Wall wall = target.wall();
            SlotButton button = target.button();
            button.setEnabled(allowed.test(wall));
            button.setVisible(!game.hasWall(wall.row(), wall.col(), wall.orientation()));
            button.setToolTipText(label.apply(wall));
            button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            if (wall.equals(preview)) previewLegal = button.isEnabled();
        }
        updateTabStops();
    }

    private void updateTabStops() {
        for (GameState.Orientation orientation : GameState.Orientation.values()) {
            Target selected = null;
            for (Target target : targets) {
                if (target.wall().orientation() == orientation && target.button().isEnabled()) {
                    if (selected == null || target.button().isFocusable()) selected = target;
                }
            }
            setWallFocus(selected == null ? null : selected.button(), orientation);
        }
    }

    private void showPreview(SlotButton slot, GameState.Wall wall) {
        preview = wall;
        previewLegal = slot.isEnabled();
        repaint();
    }

    @Override
    public void doLayout() {
        int size = Math.min(getWidth(), getHeight());
        scale = size / BASE;
        originX = (getWidth() - size) / 2;
        originY = (getHeight() - size) / 2;
        for (int row = 0; row < GameState.SIZE; row++) {
            for (int col = 0; col < GameState.SIZE; col++) {
                cells[row][col].setBounds(
                        x(PAD + col * STEP), y(PAD + row * STEP), px(CELL), px(CELL));
            }
        }
        for (Target target : targets) {
            GameState.Wall wall = target.wall();
            boolean horizontal = wall.orientation() == GameState.Orientation.HORIZONTAL;
            target.button()
                    .setBounds(
                            x(PAD + wall.col() * STEP + (horizontal ? 0 : CELL)),
                            y(PAD + wall.row() * STEP + (horizontal ? CELL : 0)),
                            px(horizontal ? CELL : GAP),
                            px(horizontal ? GAP : CELL));
        }
    }

    private int px(double value) {
        return (int) Math.round(value * scale);
    }

    private int x(double value) {
        return originX + px(value);
    }

    private int y(double value) {
        return originY + px(value);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        Theme.Palette colors = Theme.palette(this);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x(2), y(5), px(BASE - 4), px(BASE - 5), px(22), px(22));
        g.setPaint(
                new GradientPaint(
                        x(0),
                        y(0),
                        Theme.blend(colors.raised(), colors.line(), 0.5f),
                        x(BASE),
                        y(BASE),
                        colors.background()));
        g.fillRoundRect(x(0), y(0), px(BASE), px(BASE - 5), px(22), px(22));
        g.setColor(colors.line());
        g.drawRoundRect(x(3), y(3), px(BASE - 7), px(BASE - 12), px(20), px(20));
        g.setColor(Theme.blend(colors.background(), colors.panel(), 0.4f));
        g.fillRoundRect(
                x(PAD - 8), y(PAD - 8), px(BASE - PAD * 2 + 16), px(BASE - PAD * 2 + 16), 10, 10);
        g.setFont(Theme.font(Font.BOLD, Math.max(9, px(9))));
        String top = text.apply("BLUE'S GOAL");
        String bottom = text.apply("GREEN'S GOAL");
        g.setColor(colors.blue());
        g.drawString(top, x(BASE / 2) - g.getFontMetrics().stringWidth(top) / 2, y(18));
        g.setColor(colors.green());
        g.drawString(
                bottom, x(BASE / 2) - g.getFontMetrics().stringWidth(bottom) / 2, y(BASE - 13));
        g.setStroke(new BasicStroke(Math.max(2f, px(3))));
        g.setColor(colors.blue());
        g.drawLine(x(PAD), y(22), x(BASE - PAD), y(22));
        g.setColor(colors.green());
        g.drawLine(x(PAD), y(BASE - 26), x(BASE - PAD), y(BASE - 26));
        GameState game = gameSource.get();
        if (game != null) {
            if (game.playerCount() == 4) {
                g.setColor(Theme.playerColor(2, colors));
                g.drawLine(x(BASE - 22), y(PAD), x(BASE - 22), y(BASE - PAD));
                g.setColor(colors.gold());
                g.drawLine(x(22), y(PAD), x(22), y(BASE - PAD));
                drawSideGoal(g, text.apply("RED'S GOAL"), BASE - 10, Theme.playerColor(2, colors));
                drawSideGoal(g, text.apply("GOLD'S GOAL"), 13, colors.gold());
            }
            for (int row = 0; row < GameState.SIZE - 1; row++) {
                for (int col = 0; col < GameState.SIZE - 1; col++) {
                    for (GameState.Orientation direction : GameState.Orientation.values()) {
                        if (game.hasWall(row, col, direction)) {
                            drawWall(g, row, col, direction, colors.gold(), false);
                        }
                    }
                }
            }
        }
        if (preview != null) {
            drawWall(
                    g,
                    preview.row(),
                    preview.col(),
                    preview.orientation(),
                    previewLegal ? colors.green() : Theme.playerColor(2, colors),
                    true);
        }
        g.dispose();
    }

    private void drawSideGoal(Graphics2D graphics, String label, double side, Color color) {
        Graphics2D rotated = (Graphics2D) graphics.create();
        rotated.translate(x(side), y(BASE / 2));
        rotated.rotate(-Math.PI / 2);
        rotated.setColor(color);
        rotated.drawString(label, -rotated.getFontMetrics().stringWidth(label) / 2, 0);
        rotated.dispose();
    }

    private void drawWall(
            Graphics2D g,
            int row,
            int col,
            GameState.Orientation direction,
            Color color,
            boolean ghost) {
        int left = x(PAD + col * STEP);
        int top = y(PAD + row * STEP);
        int width = px(direction == GameState.Orientation.HORIZONTAL ? CELL * 2 + GAP : GAP - 2);
        int height = px(direction == GameState.Orientation.HORIZONTAL ? GAP - 2 : CELL * 2 + GAP);
        left += direction == GameState.Orientation.HORIZONTAL ? 0 : px(CELL + 1);
        top += direction == GameState.Orientation.HORIZONTAL ? px(CELL + 1) : 0;
        g.setColor(new Color(0, 0, 0, 65));
        g.fillRoundRect(left, top + px(2), width, height, px(5), px(5));
        g.setPaint(
                new GradientPaint(
                        left,
                        top,
                        Theme.blend(color, Color.WHITE, 0.38f),
                        left + width,
                        top + height,
                        color));
        g.fillRoundRect(left, top, width, height, px(5), px(5));
        g.setColor(Theme.blend(color, Color.WHITE, ghost ? 0.8f : 0.5f));
        g.drawRoundRect(
                left + 1, top + 1, Math.max(1, width - 3), Math.max(1, height - 3), px(4), px(4));
    }
}
