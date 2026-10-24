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
import java.util.function.BiConsumer;
import java.util.function.Function;
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
    private final SlotButton[][] slots;
    private final Supplier<GameState> gameSource;
    private final Supplier<GameState.Orientation> wallDirection;
    private final Function<String, String> text;
    private GameState.Cell preview;

    void clearPreview() {
        preview = null;
    }

    void previewWall(GameState.Cell anchor) {
        preview = anchor;
        repaint();
    }

    private double scale = 1;
    private int originX;
    private int originY;

    BoardSurface(
            CellButton[][] cells,
            SlotButton[][] slots,
            Supplier<GameState> gameSource,
            Supplier<GameState.Orientation> wallDirection,
            Function<String, String> text,
            BiConsumer<Integer, Integer> movePawn,
            BiConsumer<Integer, Integer> placeWall) {
        this.cells = cells;
        this.slots = slots;
        this.gameSource = gameSource;
        this.wallDirection = wallDirection;
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
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                final int r = row;
                final int c = col;
                SlotButton slot = new SlotButton();
                slot.setName("wall-" + row + "-" + col);
                slot.addActionListener(event -> placeWall.accept(r, c));
                slot.addMouseListener(
                        new MouseAdapter() {
                            @Override
                            public void mouseEntered(MouseEvent event) {
                                showPreview(slot, r, c);
                            }

                            @Override
                            public void mouseExited(MouseEvent event) {
                                preview = null;
                                repaint();
                            }
                        });
                slot.addFocusListener(
                        new FocusAdapter() {
                            @Override
                            public void focusGained(FocusEvent event) {
                                showPreview(slot, r, c);
                            }

                            @Override
                            public void focusLost(FocusEvent event) {
                                preview = null;
                                repaint();
                            }
                        });
                slots[row][col] = slot;
                add(slot);
            }
        }
    }

    private void showPreview(SlotButton slot, int row, int col) {
        preview = slot.isEnabled() ? new GameState.Cell(row, col) : null;
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
        for (int row = 0; row < GameState.SIZE - 1; row++) {
            for (int col = 0; col < GameState.SIZE - 1; col++) {
                slots[row][col].setBounds(
                        x(PAD + col * STEP + CELL), y(PAD + row * STEP + CELL), px(GAP), px(GAP));
            }
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
            drawWall(g, preview.row(), preview.col(), wallDirection.get(), colors.green(), true);
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
