package Board;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;

/** Reusable board illustrations, pawn squares, wall slots, and wrapped text. */
final class BoardWidgets {
    private BoardWidgets() {}

    static final class WrappedLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        WrappedLabel(int size, int style, Color color) {
            putClientProperty("html.disable", Boolean.TRUE);
            setFont(Theme.font(style, size));
            setForeground(color);
        }

        @Override
        public JToolTip createToolTip() {
            return plainToolTip(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            String text = getText();
            if (text == null || text.isEmpty() || getWidth() < 2) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setColor(getForeground());
            AttributedString attributed = new AttributedString(text);
            attributed.addAttribute(TextAttribute.FONT, getFont());
            attributed.addAttribute(
                    TextAttribute.RUN_DIRECTION,
                    getComponentOrientation().isLeftToRight()
                            ? TextAttribute.RUN_DIRECTION_LTR
                            : TextAttribute.RUN_DIRECTION_RTL);
            LineBreakMeasurer lines =
                    new LineBreakMeasurer(attributed.getIterator(), g.getFontRenderContext());
            float y = 0;
            while (lines.getPosition() < text.length()) {
                TextLayout line = lines.nextLayout(getWidth());
                y += line.getAscent();
                line.draw(
                        g,
                        getComponentOrientation().isLeftToRight()
                                ? 0
                                : getWidth() - line.getAdvance(),
                        y);
                y += line.getDescent() + line.getLeading() + 2;
            }
            g.dispose();
        }
    }

    static final class CellButton extends JButton {
        private static final long serialVersionUID = 1L;
        private int occupant = -1;
        private boolean legal;
        private boolean hinted;

        CellButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void update(int nextOccupant, boolean legalMove) {
            occupant = nextOccupant;
            legal = legalMove;
            setEnabled(legalMove);
        }

        void setHinted(boolean value) {
            hinted = value;
        }

        @Override
        public JToolTip createToolTip() {
            return plainToolTip(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            Color base = legal ? new Color(244, 219, 172) : Theme.WOOD;
            if (legal && getModel().isRollover()) {
                base = new Color(255, 232, 187);
            }
            g.setColor(new Color(76, 47, 27, 70));
            g.fillRoundRect(0, 3, getWidth(), getHeight() - 3, 8, 8);
            g.setPaint(
                    new GradientPaint(
                            0, 0, Theme.blend(base, Color.WHITE, 0.12f), 0, getHeight(), base));
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 3, 8, 8);
            if (legal) {
                g.setStroke(new BasicStroke(isFocusOwner() ? 2.5f : 1.6f));
                g.setColor(new Color(255, 237, 191));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 5, 7, 7);
            }
            if (occupant >= 0) {
                int size = Math.max(20, getWidth() - 12);
                new Theme.PawnIcon(Theme.playerColor(occupant, Theme.DARK), size)
                        .paintIcon(this, g, (getWidth() - size) / 2, (getHeight() - size) / 2 - 1);
            } else if (legal) {
                int size = Math.max(7, getWidth() / 5);
                g.setColor(new Color(139, 91, 41, 175));
                g.fillOval((getWidth() - size) / 2, (getHeight() - size) / 2 - 1, size, size);
            }
            if (hinted && legal) {
                g.setColor(new Color(116, 67, 16));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 11, 7, 7);
            }
            g.dispose();
        }
    }

    static final class SlotButton extends JButton {
        private static final long serialVersionUID = 1L;

        SlotButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public JToolTip createToolTip() {
            return plainToolTip(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setColor(
                    isEnabled()
                            ? isFocusOwner() || getModel().isRollover()
                                    ? Theme.GOLD
                                    : new Color(240, 213, 174, 145)
                            : new Color(93, 63, 39, 90));
            int inset = isFocusOwner() ? 1 : 3;
            g.fillOval(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2);
            g.dispose();
        }
    }

    private static JToolTip plainToolTip(JComponent owner) {
        JToolTip tooltip = new JToolTip();
        tooltip.putClientProperty("html.disable", Boolean.TRUE);
        tooltip.setComponent(owner);
        tooltip.setComponentOrientation(owner.getComponentOrientation());
        Theme.Palette colors = Theme.palette(owner);
        tooltip.setBackground(colors.raised());
        tooltip.setForeground(colors.text());
        tooltip.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(colors.line()),
                        BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        return tooltip;
    }

    static final class HeroBoard extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            double factor = Math.min(getWidth() / 340.0, getHeight() / 280.0);
            g.translate((getWidth() - 340 * factor) / 2, (getHeight() - 280 * factor) / 2);
            g.scale(factor, factor);
            g.rotate(-0.09, 170, 145);
            g.setColor(new Color(0, 0, 0, 45));
            g.fillRoundRect(37, 21, 268, 249, 25, 25);
            g.setPaint(
                    new GradientPaint(
                            35, 17, new Color(185, 139, 90), 300, 250, new Color(113, 76, 48)));
            g.fillRoundRect(30, 12, 268, 249, 22, 22);
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) {
                    g.setColor(
                            (row + col) % 2 == 0
                                    ? new Color(216, 182, 137)
                                    : new Color(205, 166, 119));
                    g.fillRoundRect(47 + col * 47, 29 + row * 44, 39, 36, 6, 6);
                }
            }
            g.setColor(new Color(64, 42, 29));
            g.fillRoundRect(88, 73, 7, 80, 4, 4);
            g.fillRoundRect(142, 153, 86, 7, 4, 4);
            new Theme.PawnIcon(Theme.BLUE, 32).paintIcon(this, g, 145, 207);
            new Theme.PawnIcon(Theme.GREEN, 32).paintIcon(this, g, 192, 75);
            g.dispose();
        }
    }
}
