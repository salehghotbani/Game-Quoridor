package Board;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Dependency-free visual components shared by the menu and board. */
final class Theme {
    static final Color BACKGROUND = new Color(15, 23, 39);
    static final Color PANEL = new Color(24, 35, 54);
    static final Color RAISED = new Color(32, 46, 67);
    static final Color LINE = new Color(52, 68, 91);
    static final Color TEXT = new Color(238, 243, 250);
    static final Color MUTED = new Color(155, 173, 195);
    static final Color BLUE = new Color(86, 158, 255);
    static final Color GREEN = new Color(49, 207, 174);
    static final Color GOLD = new Color(242, 192, 113);
    static final Color WOOD = new Color(207, 170, 121);

    private Theme() { }

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static Color blend(Color first, Color second, float amount) {
        return new Color(Math.round(first.getRed() * (1 - amount) + second.getRed() * amount),
                Math.round(first.getGreen() * (1 - amount) + second.getGreen() * amount),
                Math.round(first.getBlue() * (1 - amount) + second.getBlue() * amount));
    }

    static class Card extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;
        private Color accent = LINE;

        Card(Color fill) {
            this.fill = fill;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        }

        void accent(Color color) { accent = color; repaint(); }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.setColor(accent);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    static final class Button extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean primary;

        Button(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(font(Font.BOLD, 13));
            setForeground(primary ? BACKGROUND : TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            setPreferredSize(new Dimension(getPreferredSize().width + 8, 42));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            Color base = primary ? GOLD : RAISED;
            if (getModel().isPressed()) { base = base.darker(); }
            else if (getModel().isRollover()) { base = blend(base, Color.WHITE, 0.09f); }
            g.setColor(isEnabled() ? base : PANEL);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setColor(isFocusOwner() ? GOLD : primary ? base : LINE);
            g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    static final class Toggle extends JToggleButton {
        private static final long serialVersionUID = 1L;

        Toggle(String text) {
            super(text);
            setFont(font(Font.BOLD, 13));
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(10, 9, 10, 9));
            setPreferredSize(new Dimension(110, 42));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(isSelected() ? blend(PANEL, GREEN, 0.2f) : getModel().isRollover() ? RAISED : PANEL);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
            g.setColor(isSelected() ? GREEN : isFocusOwner() ? GOLD : LINE);
            g.setStroke(new BasicStroke(isSelected() || isFocusOwner() ? 1.5f : 1f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    static final class PawnIcon implements Icon {
        private final Color color;
        private final int size;

        PawnIcon(Color color, int size) { this.color = color; this.size = size; }

        @Override
        public int getIconWidth() { return size; }
        @Override
        public int getIconHeight() { return size; }
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(x + 2, y + 4, size - 4, size - 4);
            g.setPaint(new GradientPaint(x, y, blend(color, Color.WHITE, 0.28f), x, y + size, color.darker()));
            g.fillOval(x + 2, y + 1, size - 4, size - 4);
            g.setColor(blend(color, Color.WHITE, 0.6f));
            g.setStroke(new BasicStroke(1.3f));
            g.drawOval(x + 3, y + 2, size - 6, size - 6);
            g.setColor(new Color(255, 255, 255, 80));
            g.fillOval(x + size / 4, y + size / 5, size / 4, size / 5);
            g.dispose();
        }
    }
}
