package Board;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.metal.MetalFileChooserUI;
import javax.swing.table.TableColumn;

/** Per-window file selection, localized messages, and dialog presentation. */
final class GameDialogs {
    private static final String LOCALIZED_CHILDREN_PROPERTY = "localized.children";
    private static final String SELECTION_ONLY_PROPERTY = "selection.only";
    private static final String EDIT_FILENAME_ACTION = "editFileName";
    private final JFrame owner;
    private final I18n i18n;
    private final Theme.Palette palette;

    GameDialogs(JFrame owner, I18n i18n, Theme.Palette palette) {
        this.owner = owner;
        this.i18n = i18n;
        this.palette = palette;
    }

    private String tr(String template, Object... arguments) {
        return UiText.format(i18n, template, arguments);
    }

    JFileChooser fileChooser(String title, Path savePath) {
        JFileChooser chooser = new LocalizedFileChooser();
        chooser.setDialogTitle(tr(title));
        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        tr("Quoridor matches (*.qsave, *.G3M)"), "qsave", "G3M"));
        if (savePath != null && savePath.toAbsolutePath().getParent() != null) {
            chooser.setCurrentDirectory(savePath.toAbsolutePath().getParent().toFile());
        }
        return chooser;
    }

    void showFileError(String title, String message, boolean activeMatch) {
        String explanation = tr(message);
        if (activeMatch) {
            explanation += "\n" + tr("Your current match is still available.");
        }
        showMessage(title, explanation, JOptionPane.ERROR_MESSAGE);
    }

    void showMessage(String title, String message, int type) {
        JOptionPane pane =
                new JOptionPane(
                        message, type, JOptionPane.DEFAULT_OPTION, null, new Object[] {tr("OK")});
        showPane(pane, title);
    }

    boolean confirmReplace(String filename) {
        String yes = tr("Yes");
        JOptionPane pane =
                new JOptionPane(
                        tr("Replace the saved match “%s”?", filename),
                        JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_NO_OPTION,
                        null,
                        new Object[] {yes, tr("No")},
                        tr("No"));
        showPane(pane, "Replace saved match");
        return yes.equals(pane.getValue());
    }

    private void showPane(JOptionPane pane, String title) {
        pane.setLocale(i18n.locale());
        pane.applyComponentOrientation(owner.getComponentOrientation());
        Theme.apply(pane, Theme.DARK, palette, true);
        JDialog dialog = pane.createDialog(owner, tr(title));
        dialog.setLocale(i18n.locale());
        dialog.applyComponentOrientation(owner.getComponentOrientation());
        dialog.getContentPane().setBackground(palette.panel());
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
    }

    /** A per-window chooser; it never changes Swing's process-wide UI defaults. */
    private final class LocalizedFileChooser extends JFileChooser {
        private static final long serialVersionUID = 1L;

        LocalizedFileChooser() {
            setLocale(i18n.locale());
            setUI(new LocalizedChooserUI(this));
            setAcceptAllFileFilterUsed(false);
            addChoosableFileFilter(
                    new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            return true;
                        }

                        @Override
                        public String getDescription() {
                            return tr("All files");
                        }
                    });
            setFileView(
                    new FileView() {
                        @Override
                        public String getTypeDescription(File file) {
                            return tr(file.isDirectory() ? "Folder" : "File");
                        }
                    });
            getAccessibleContext().setAccessibleName(tr("File chooser"));
            applyComponentOrientation(owner.getComponentOrientation());
            localizeChooserTree(this);
            addPropertyChangeListener(
                    event -> {
                        if (event.getPropertyName()
                                        .equals(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY)
                                || event.getPropertyName()
                                        .equals(JFileChooser.DIRECTORY_CHANGED_PROPERTY)) {
                            SwingUtilities.invokeLater(() -> localizeChooserTree(this));
                        }
                    });
        }

        @Override
        protected JDialog createDialog(Component parent) {
            JDialog dialog = super.createDialog(parent);
            dialog.setLocale(i18n.locale());
            dialog.applyComponentOrientation(owner.getComponentOrientation());
            Theme.apply(dialog.getContentPane(), Theme.DARK, palette, true);
            dialog.getRootPane().setBackground(palette.panel());
            localizeChooserTree(this);
            dialog.pack();
            return dialog;
        }
    }

    private final class LocalizedChooserUI extends MetalFileChooserUI {
        LocalizedChooserUI(JFileChooser chooser) {
            super(chooser);
        }

        @Override
        protected void installStrings(JFileChooser chooser) {
            super.installStrings(chooser);
            saveButtonText = tr("Save");
            openButtonText = tr("Open");
            cancelButtonText = tr("Cancel");
            updateButtonText = tr("Update");
            helpButtonText = tr("Help");
            directoryOpenButtonText = tr("Open folder");
            saveButtonToolTipText = tr("Save selected file");
            openButtonToolTipText = tr("Open selected file");
            cancelButtonToolTipText = tr("Cancel file chooser");
            updateButtonToolTipText = tr("Update directory listing");
            helpButtonToolTipText = tr("File chooser help");
            directoryOpenButtonToolTipText = tr("Open selected directory");
        }

        @Override
        public void installComponents(JFileChooser chooser) {
            super.installComponents(chooser);
            localizeChooserTree(chooser);
        }

        @Override
        protected ActionMap createActionMap() {
            ActionMap actions = super.createActionMap();
            AbstractAction selectionOnly =
                    new AbstractAction() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void actionPerformed(ActionEvent event) {}
                    };
            selectionOnly.setEnabled(false);
            actions.put(EDIT_FILENAME_ACTION, selectionOnly);
            return actions;
        }

        @Override
        protected JPanel createDetailsView(JFileChooser chooser) {
            JPanel details = super.createDetailsView(chooser);
            if (details != null) {
                localizeChooserTree(details);
            }
            return details;
        }

        @Override
        public Action getNewFolderAction() {
            return new AbstractAction(tr("New folder")) {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent event) {
                    JFileChooser chooser = getFileChooser();
                    Path parent = chooser.getCurrentDirectory().toPath();
                    String name = tr("New folder");
                    Path folder = parent.resolve(name);
                    for (int suffix = 2; Files.exists(folder); suffix++) {
                        folder =
                                parent.resolve(
                                        name + " " + String.format(i18n.locale(), "%d", suffix));
                    }
                    try {
                        Files.createDirectory(folder);
                        chooser.rescanCurrentDirectory();
                        chooser.setCurrentDirectory(folder.toFile());
                    } catch (IOException | SecurityException exception) {
                        showMessage(
                                "Error creating new folder",
                                tr("Choose a writable folder and try again."),
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
        }
    }

    private void localizeChooserTree(Component root) {
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        localizeChooserTree(root, visited);
    }

    private void localizeChooserTree(Component component, Set<Component> visited) {
        if (!visited.add(component)) {
            return;
        }
        component.setLocale(i18n.locale());
        component.setComponentOrientation(owner.getComponentOrientation());
        Theme.applyOne(component, Theme.DARK, palette, true);
        if (component instanceof JLabel label && !(label.getParent() instanceof CellRendererPane)) {
            label.setText(chooserText(label.getText()));
        }
        if (component instanceof AbstractButton button) {
            button.setText(chooserText(button.getText()));
        }
        if (component instanceof JComponent child) {
            child.setToolTipText(chooserText(child.getToolTipText()));
            JPopupMenu popup = child.getComponentPopupMenu();
            if (popup != null) {
                localizeChooserTree(popup, visited);
            }
        }
        if (component instanceof JMenu menu) {
            for (Component child : menu.getMenuComponents()) {
                localizeChooserTree(child, visited);
            }
        }
        if (component instanceof JTable table) {
            table.setDefaultEditor(Object.class, null);
            table.setDefaultEditor(String.class, null);
            table.setDefaultEditor(File.class, null);
            for (int index = 0; index < table.getColumnCount(); index++) {
                TableColumn column = table.getColumnModel().getColumn(index);
                column.setCellEditor(null);
                column.setHeaderValue(chooserText(String.valueOf(column.getHeaderValue())));
            }
            table.getTableHeader().repaint();
        }
        if (component instanceof JList<?> list) {
            list.getAccessibleContext().setAccessibleName(tr("Files List"));
            keepFileSelectionOnly(list);
        }
        if (component instanceof JTable table) {
            table.getAccessibleContext().setAccessibleName(tr("Files Details"));
        }
        if (component instanceof Container parent) {
            if (parent instanceof JComponent child
                    && child.getClientProperty(LOCALIZED_CHILDREN_PROPERTY) == null) {
                child.putClientProperty(LOCALIZED_CHILDREN_PROPERTY, Boolean.TRUE);
                parent.addContainerListener(
                        new ContainerAdapter() {
                            @Override
                            public void componentAdded(ContainerEvent event) {
                                localizeChooserTree(event.getChild());
                            }
                        });
            }
            for (Component child : parent.getComponents()) {
                localizeChooserTree(child, visited);
            }
        }
    }

    private static void keepFileSelectionOnly(JList<?> list) {
        if (list.getClientProperty(SELECTION_ONLY_PROPERTY) != null) {
            return;
        }
        list.putClientProperty(SELECTION_ONLY_PROPERTY, Boolean.TRUE);
        // Selection is handled on press/release. A slow second click would otherwise
        // start the JDK's inline renamer, whose error dialogs cannot be localized per window.
        // Double-click folder navigation and all normal selection events still reach the UI.
        for (MouseListener listener : list.getMouseListeners()) {
            list.removeMouseListener(listener);
            list.addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent event) {
                            if (event.getClickCount() != 1) {
                                listener.mouseClicked(event);
                            }
                        }

                        @Override
                        public void mousePressed(MouseEvent event) {
                            listener.mousePressed(event);
                        }

                        @Override
                        public void mouseReleased(MouseEvent event) {
                            listener.mouseReleased(event);
                        }

                        @Override
                        public void mouseEntered(MouseEvent event) {
                            listener.mouseEntered(event);
                        }

                        @Override
                        public void mouseExited(MouseEvent event) {
                            listener.mouseExited(event);
                        }
                    });
        }
    }

    private String chooserText(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }
        String key =
                switch (original.toLowerCase(Locale.ROOT)) {
                    case "look in:" -> "Look in:";
                    case "save in:" -> "Save in:";
                    case "file name:" -> "File name:";
                    case "files of type:" -> "Files of type:";
                    case "all files" -> "All files";
                    case "up one level" -> "Up one level";
                    case "create new folder" -> "Create new folder";
                    case "new folder" -> "New folder";
                    case "filechooser help" -> "File chooser help";
                    case "abort file chooser dialog" -> "Cancel file chooser";
                    case "open selected directory" -> "Open selected directory";
                    case "open selected file" -> "Open selected file";
                    case "save selected file" -> "Save selected file";
                    case "update directory listing" -> "Update directory listing";
                    case "name" -> "Name";
                    case "size" -> "Size";
                    case "type" -> "Type";
                    case "modified" -> "Modified";
                    case "attributes" -> "Attributes";
                    case "open" -> "Open";
                    case "save" -> "Save";
                    case "cancel" -> "Cancel";
                    case "help" -> "Help";
                    case "update" -> "Update";
                    case "home" -> "Home";
                    case "list" -> "List";
                    case "details" -> "Details";
                    case "view" -> "View";
                    case "refresh" -> "Refresh";
                    default -> null;
                };
        return key == null ? original : tr(key);
    }
}
