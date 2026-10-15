package Board;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

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
    private static final String PALETTE_PROPERTY = "quoridor.palette";
    private static final String COMBO_PROPERTY = "quoridor.combo";
    private static final String RENDERER_PROPERTY = "quoridor.renderer";
    private static final String BUTTON_UI_PROPERTY = "quoridor.basicButton";

    record Palette(
            Color background,
            Color panel,
            Color raised,
            Color line,
            Color text,
            Color muted,
            Color blue,
            Color green,
            Color gold,
            Color primary,
            Color primaryText,
            Color selection) {}

    static final Palette DARK =
            new Palette(
                    BACKGROUND,
                    PANEL,
                    RAISED,
                    LINE,
                    TEXT,
                    MUTED,
                    BLUE,
                    GREEN,
                    GOLD,
                    GOLD,
                    BACKGROUND,
                    new Color(29, 69, 73));
    static final Palette LIGHT =
            new Palette(
                    new Color(239, 244, 250),
                    Color.WHITE,
                    new Color(225, 234, 244),
                    new Color(190, 204, 221),
                    new Color(28, 43, 63),
                    new Color(82, 101, 122),
                    new Color(47, 101, 185),
                    new Color(22, 107, 85),
                    new Color(139, 94, 19),
                    new Color(241, 193, 111),
                    new Color(28, 43, 63),
                    new Color(208, 234, 228));

    private Theme() {}

    static Palette palette(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current instanceof JComponent child
                    && child.getClientProperty(PALETTE_PROPERTY) instanceof Palette palette) {
                return palette;
            }
        }
        return DARK;
    }

    static Color remap(Color color, Palette old, Palette next) {
        if (color == null) {
            return null;
        }
        Color[] before = {
            old.background(),
            old.panel(),
            old.raised(),
            old.line(),
            old.text(),
            old.muted(),
            old.blue(),
            old.green(),
            old.gold()
        };
        Color[] after = {
            next.background(),
            next.panel(),
            next.raised(),
            next.line(),
            next.text(),
            next.muted(),
            next.blue(),
            next.green(),
            next.gold()
        };
        for (int index = 0; index < before.length; index++) {
            if (color.equals(before[index])) {
                return after[index];
            }
        }
        return color;
    }

    static void apply(Component component, Palette old, Palette next, boolean standard) {
        applyOne(component, old, next, standard);
        if (component instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                apply(child, old, next, standard);
            }
        }
    }

    static void applyOne(Component component, Palette old, Palette next, boolean standardControls) {
        if (component instanceof JComponent child) {
            child.putClientProperty(PALETTE_PROPERTY, next);
        }
        component.setForeground(
                standardControls ? next.text() : remap(component.getForeground(), old, next));
        component.setBackground(
                standardControls ? next.panel() : remap(component.getBackground(), old, next));
        if (component instanceof Button button) {
            button.setForeground(button.primary ? next.primaryText() : next.text());
        }
        if (component instanceof JTextField field) {
            styleTextField(field, next);
        }
        if (component instanceof JComboBox<?> combo) {
            styleComboBox(combo, next, standardControls);
        }
        if (component instanceof JList<?> list) {
            list.setSelectionBackground(next.selection());
            list.setSelectionForeground(next.text());
            if (standardControls) {
                styleRenderer(list);
            }
        }
        if (component instanceof JTable table) {
            styleTable(table, next);
        }
        if (standardControls) {
            styleStandardButton(component, next);
        }
        component.repaint();
    }

    private static void styleTextField(JTextField field, Palette colors) {
        field.setBackground(colors.background());
        field.setForeground(colors.text());
        field.setDisabledTextColor(colors.muted());
        field.setCaretColor(colors.gold());
        field.setSelectionColor(colors.selection());
        field.setSelectedTextColor(colors.text());
        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(colors.line()),
                        BorderFactory.createEmptyBorder(9, 11, 9, 11)));
    }

    private static void styleComboBox(
            JComboBox<?> combo, Palette colors, boolean standardControls) {
        combo.setBackground(colors.raised());
        combo.setForeground(colors.text());
        if (combo.getClientProperty(COMBO_PROPERTY) == null) {
            combo.putClientProperty(COMBO_PROPERTY, Boolean.TRUE);
            combo.setUI(new PaletteComboBoxUI());
        }
        combo.setBorder(BorderFactory.createLineBorder(colors.line()));
        if (standardControls) {
            styleRenderer(combo);
        }
    }

    private static void styleTable(JTable table, Palette colors) {
        table.setGridColor(colors.line());
        table.setSelectionBackground(colors.selection());
        table.setSelectionForeground(colors.text());
        table.getTableHeader().setBackground(colors.raised());
        table.getTableHeader().setForeground(colors.text());
        table.setBackground(colors.panel());
        table.setForeground(colors.text());
    }

    private static void styleStandardButton(Component component, Palette colors) {
        if (component instanceof JButton button && !(button instanceof Button)) {
            if (button.getClientProperty(BUTTON_UI_PROPERTY) == null) {
                button.putClientProperty(BUTTON_UI_PROPERTY, Boolean.TRUE);
                button.setUI(new PaletteButtonUI());
            }
            button.setBackground(colors.raised());
            button.setForeground(colors.text());
        } else if (component instanceof JToggleButton toggle && !(toggle instanceof Toggle)) {
            if (toggle.getClientProperty(BUTTON_UI_PROPERTY) == null) {
                toggle.putClientProperty(BUTTON_UI_PROPERTY, Boolean.TRUE);
                toggle.setUI(new PaletteToggleUI());
            }
            toggle.setBackground(colors.raised());
            toggle.setForeground(colors.text());
        }
    }

    private static final class PaletteComboBoxUI extends BasicComboBoxUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            super.paint(graphics, component);
            if (comboBox.isFocusOwner()) {
                Graphics2D g = (Graphics2D) graphics.create();
                smooth(g);
                g.setColor(palette(comboBox).gold());
                g.setStroke(new BasicStroke(2));
                g.drawRect(2, 2, comboBox.getWidth() - 5, comboBox.getHeight() - 5);
                g.dispose();
            }
        }

        @Override
        public void paintCurrentValueBackground(
                Graphics graphics, Rectangle bounds, boolean focus) {
            graphics.setColor(palette(comboBox).raised());
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean focus) {
            Palette colors = palette(comboBox);
            Component rendered =
                    comboBox.getRenderer()
                            .getListCellRendererComponent(
                                    listBox, comboBox.getSelectedItem(), -1, false, false);
            rendered.setFont(comboBox.getFont());
            rendered.setForeground(comboBox.isEnabled() ? colors.text() : colors.muted());
            rendered.setBackground(colors.raised());
            rendered.setComponentOrientation(comboBox.getComponentOrientation());
            if (rendered instanceof JComponent child) {
                child.setOpaque(true);
            }
            currentValuePane.paintComponent(
                    graphics,
                    rendered,
                    comboBox,
                    bounds.x + 4,
                    bounds.y,
                    Math.max(0, bounds.width - 8),
                    bounds.height,
                    true);
        }

        @Override
        protected JButton createArrowButton() {
            JButton arrow =
                    new JButton() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        protected void paintComponent(Graphics graphics) {
                            Graphics2D g = (Graphics2D) graphics.create();
                            smooth(g);
                            Palette colors = palette(this);
                            g.setColor(colors.raised());
                            g.fillRect(0, 0, getWidth(), getHeight());
                            g.setColor(colors.text());
                            int x = getWidth() / 2;
                            int y = getHeight() / 2;
                            g.fillPolygon(
                                    new int[] {x - 4, x + 4, x},
                                    new int[] {y - 2, y - 2, y + 3},
                                    3);
                            g.dispose();
                        }
                    };
            arrow.setBorder(BorderFactory.createEmptyBorder());
            return arrow;
        }
    }

    @SuppressWarnings("unchecked")
    private static void styleRenderer(JComboBox<?> combo) {
        if (combo.getClientProperty(RENDERER_PROPERTY) != null) {
            return;
        }
        combo.putClientProperty(RENDERER_PROPERTY, Boolean.TRUE);
        ListCellRenderer<Object> delegate = (ListCellRenderer<Object>) combo.getRenderer();
        ((JComboBox<Object>) combo)
                .setRenderer(
                        (list, value, index, selected, focus) -> {
                            Component rendered =
                                    delegate.getListCellRendererComponent(
                                            list, value, index, selected, focus);
                            rendererColors(rendered, palette(combo), selected);
                            return rendered;
                        });
    }

    @SuppressWarnings("unchecked")
    private static void styleRenderer(JList<?> list) {
        if (list.getClientProperty(RENDERER_PROPERTY) != null) {
            return;
        }
        list.putClientProperty(RENDERER_PROPERTY, Boolean.TRUE);
        ListCellRenderer<Object> delegate = (ListCellRenderer<Object>) list.getCellRenderer();
        ((JList<Object>) list)
                .setCellRenderer(
                        (owner, value, index, selected, focus) -> {
                            Component rendered =
                                    delegate.getListCellRendererComponent(
                                            owner, value, index, selected, focus);
                            rendererColors(rendered, palette(list), selected);
                            return rendered;
                        });
    }

    private static void rendererColors(Component rendered, Palette palette, boolean selected) {
        rendered.setBackground(selected ? palette.selection() : palette.panel());
        rendered.setForeground(palette.text());
        if (rendered instanceof JLabel label) {
            label.setHorizontalAlignment(SwingConstants.LEADING);
        }
    }

    private static void buttonText(
            Graphics2D graphics, AbstractButton button, Color enabled, Color disabled) {
        String text = button.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.setColor(button.isEnabled() ? enabled : disabled);
        TextLayout layout = new TextLayout(text, button.getFont(), graphics.getFontRenderContext());
        float x = (button.getWidth() - layout.getAdvance()) / 2;
        float y =
                (button.getHeight() - layout.getAscent() - layout.getDescent()) / 2
                        + layout.getAscent();
        layout.draw(graphics, x, y);
    }

    private static void standardButtonText(
            Graphics graphics, AbstractButton button, Rectangle rectangle, String text) {
        Graphics2D g = (Graphics2D) graphics.create();
        smooth(g);
        Palette colors = palette(button);
        g.setColor(button.isEnabled() ? colors.text() : colors.muted());
        TextLayout line = new TextLayout(text, button.getFont(), g.getFontRenderContext());
        line.draw(
                g,
                rectangle.x,
                rectangle.y
                        + (rectangle.height - line.getAscent() - line.getDescent()) / 2
                        + line.getAscent());
        g.dispose();
    }

    private static void standardButtonFocus(Graphics graphics, AbstractButton button) {
        Graphics2D g = (Graphics2D) graphics.create();
        smooth(g);
        g.setColor(palette(button).gold());
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(2, 2, button.getWidth() - 5, button.getHeight() - 5, 7, 7);
        g.dispose();
    }

    private static final class PaletteButtonUI extends BasicButtonUI {
        @Override
        protected void paintText(
                Graphics graphics, AbstractButton button, Rectangle rectangle, String text) {
            standardButtonText(graphics, button, rectangle, text);
        }

        @Override
        protected void paintFocus(
                Graphics graphics,
                AbstractButton button,
                Rectangle view,
                Rectangle text,
                Rectangle icon) {
            standardButtonFocus(graphics, button);
        }

        @Override
        protected void paintButtonPressed(Graphics graphics, AbstractButton button) {
            graphics.setColor(palette(button).selection());
            graphics.fillRect(0, 0, button.getWidth(), button.getHeight());
        }
    }

    private static final class PaletteToggleUI extends BasicToggleButtonUI {
        @Override
        protected void paintText(
                Graphics graphics, AbstractButton button, Rectangle rectangle, String text) {
            standardButtonText(graphics, button, rectangle, text);
        }

        @Override
        protected void paintFocus(
                Graphics graphics,
                AbstractButton button,
                Rectangle view,
                Rectangle text,
                Rectangle icon) {
            standardButtonFocus(graphics, button);
        }

        @Override
        protected void paintButtonPressed(Graphics graphics, AbstractButton button) {
            graphics.setColor(palette(button).selection());
            graphics.fillRect(0, 0, button.getWidth(), button.getHeight());
        }
    }

    static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static Color blend(Color first, Color second, float amount) {
        return new Color(
                Math.round(first.getRed() * (1 - amount) + second.getRed() * amount),
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

        void accent(Color color) {
            accent = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            Palette colors = palette(this);
            g.setColor(remap(fill, DARK, colors));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.setColor(remap(accent, DARK, colors));
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
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            FontMetrics metrics = getFontMetrics(getFont());
            Insets insets = getInsets();
            int width =
                    metrics.stringWidth(getText() == null ? "" : getText())
                            + insets.left
                            + insets.right
                            + 12;
            return new Dimension(Math.max(size.width, width), Math.max(size.height, 42));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            Palette colors = palette(this);
            Color base = primary ? colors.primary() : colors.raised();
            if (getModel().isPressed()) {
                base = blend(base, colors.background(), 0.14f);
            } else if (getModel().isRollover()) {
                base = blend(base, Color.WHITE, 0.09f);
            }
            g.setColor(isEnabled() ? base : colors.panel());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setColor(
                    isFocusOwner()
                            ? primary ? colors.primaryText() : colors.gold()
                            : primary ? base : colors.line());
            g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
            buttonText(g, this, primary ? colors.primaryText() : colors.text(), colors.muted());
            g.dispose();
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
            Palette colors = palette(this);
            g.setColor(
                    isSelected()
                            ? colors.selection()
                            : getModel().isRollover() ? colors.raised() : colors.panel());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
            g.setColor(
                    isFocusOwner() ? colors.gold() : isSelected() ? colors.green() : colors.line());
            g.setStroke(new BasicStroke(isSelected() || isFocusOwner() ? 1.5f : 1f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
            buttonText(g, this, colors.text(), colors.muted());
            g.dispose();
        }
    }

    static final class PawnIcon implements Icon {
        private final Color color;
        private final int size;

        PawnIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(new Color(0, 0, 0, 50));
            g.fillOval(x + 2, y + 4, size - 4, size - 4);
            g.setPaint(
                    new GradientPaint(
                            x, y, blend(color, Color.WHITE, 0.28f), x, y + size, color.darker()));
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
