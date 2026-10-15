import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.I18n;
import Board.SaveStore;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.imageio.ImageIO;

/** Exercises displayed file dialogs, including cancellation and failed save/load operations. */
public final class SaveDialogTest {
    private static int checks;
    private static Board board;
    private static Path directory;
    private static Path workingDirectory;
    private static final List<Board> boards = new ArrayList<>();
    private static boolean capturedPersianDialog;
    private static final Set<I18n.Language> inspectedDetails = EnumSet.noneOf(I18n.Language.class);
    private static final Set<String> UNTRANSLATED_CHOOSER = Set.of("Save", "Open", "Cancel", "File Name:", "Files of Type:",
            "Look In:", "Save In:", "Up One Level", "Home", "Create New Folder", "List", "Details",
            "New Folder", "Refresh", "View", "All Files", "Open selected file", "Save selected file",
            "Abort file chooser dialog", "Update directory listing", "FileChooser help", "Open selected directory");

    private SaveDialogTest() { }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("SaveDialogTest requires a graphical desktop.");
        }
        directory = Files.createTempDirectory("quoridor-file-dialog-test-");
        Locale systemLocale = Locale.getDefault();
        String defaultCancel = UIManager.getString("FileChooser.cancelButtonText", Locale.ENGLISH);
        // A blocked native file chooser must never leave the test JVM running indefinitely.
        var watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "save-dialog-test-deadline");
            thread.setDaemon(true);
            return thread;
        });
        Thread emergencyCleanup = new Thread(SaveDialogTest::cleanTemporaryFilesQuietly,
                "save-dialog-test-cleanup");
        Runtime.getRuntime().addShutdownHook(emergencyCleanup);
        watchdog.schedule(() -> {
            System.err.println("SaveDialogTest exceeded its 90-second deadline.");
            System.exit(1);
        }, 90, TimeUnit.SECONDS);
        try {
            for (I18n.Language language : I18n.Language.values()) {
                workingDirectory = Files.createDirectory(directory.resolve(language.name()));
                SwingUtilities.invokeAndWait(() -> {
                    board = new Board(new GameSession(new GameState(), new GameConfig("صالح", "Green",
                            GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM)), language);
                    boards.add(board);
                    board.setVisible(true);
                    click("cell-7-4");
                });
                saveAndCancel();
                loadAndFailure();
                legacyImport();
            }
            // Reopen each chooser with both differently localized windows still alive.
            for (Board candidate : boards) {
                board = candidate;
                saveSuggestion();
            }
            equal(Locale.getDefault(), systemLocale, "Dialogs leave the process locale unchanged");
            equal(UIManager.getString("FileChooser.cancelButtonText", Locale.ENGLISH), defaultCancel,
                    "Persian dialogs do not overwrite global Swing translations");
            unsupportedSystemLocale();
            System.out.println("Displayed save/load dialog tests passed (" + checks + " checks).");
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    disposeDialogs();
                    for (Board candidate : boards) { candidate.dispose(); }
                });
                cleanTemporaryFiles();
                Runtime.getRuntime().removeShutdownHook(emergencyCleanup);
            } finally {
                watchdog.shutdownNow();
            }
        }
    }

    private static void unsupportedSystemLocale() throws Exception {
        Locale before = Locale.getDefault();
        Locale beforeDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale beforeFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag("he-IL"));
            SwingUtilities.invokeAndWait(() -> {
                board = new Board(new GameSession(new GameState(), new GameConfig("صالح", "Green",
                        GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM)));
                boards.add(board);
                board.setVisible(true);
                equal(board.currentLanguage(), I18n.Language.ENGLISH,
                        "An unsupported system locale falls back to English");
            });
            saveSuggestion();
        } finally {
            Locale.setDefault(before);
            Locale.setDefault(Locale.Category.DISPLAY, beforeDisplay);
            Locale.setDefault(Locale.Category.FORMAT, beforeFormat);
        }
    }

    private static void saveAndCancel() throws Exception {
        GameSession original = state();
        Path saved = workingDirectory.resolve("بازی mixed-name.qsave");
        Path suggestion = chooser("save-game", workingDirectory.resolve("بازی mixed-name"), false,
                JOptionPane.YES_OPTION, false);
        equal(suggestion.getFileName().toString(), "Quoridor.qsave", "New match has a default save filename");
        check(Files.isRegularFile(saved), "Save chooser appends .qsave");
        assertSession(SaveStore.load(saved), original, "Save chooser stores displayed game");
        byte[] savedBytes = Files.readAllBytes(saved);

        Path cancelled = workingDirectory.resolve("cancelled.qsave");
        suggestion = chooser("save-game", cancelled, true, JOptionPane.YES_OPTION, false);
        equal(suggestion, saved, "Successful save remembers its selected path");
        check(!Files.exists(cancelled), "Cancelled save creates no file");
        equal(saveSuggestion(), saved, "Cancelled save preserves selected path");
        assertSession(state(), original, "Cancelled save preserves game");

        SwingUtilities.invokeAndWait(() -> click("new-game"));
        equal(saveSuggestion().getFileName().toString(), "Quoridor.qsave", "New game resets selected save path");
        check(Arrays.equals(savedBytes, Files.readAllBytes(saved)), "New game preserves prior save bytes");
    }

    private static void loadAndFailure() throws Exception {
        Path saved = workingDirectory.resolve("بازی mixed-name.qsave");
        GameSession original = SaveStore.load(saved);
        chooser("load-game-in-game", saved, false, JOptionPane.YES_OPTION, false);
        assertSession(state(), original, "Open chooser restores displayed game");
        equal(saveSuggestion(), saved, "Successful load remembers path");

        chooser("load-game-in-game", workingDirectory.resolve("ignored.qsave"), true, JOptionPane.YES_OPTION, false);
        assertSession(state(), original, "Cancelled load preserves game");
        equal(saveSuggestion(), saved, "Cancelled load preserves path");
        Path corrupt = workingDirectory.resolve("corrupt.qsave");
        Files.writeString(corrupt, "version=999\n");
        chooser("load-game-in-game", corrupt, false, JOptionPane.YES_OPTION, true);
        assertSession(state(), original, "Failed load preserves game");
        equal(saveSuggestion(), saved, "Failed load preserves path");

        Path other = workingDirectory.resolve("other match.qsave");
        SaveStore.save(other, new GameSession(new GameState(), original.config()));
        byte[] otherBytes = Files.readAllBytes(other);
        chooser("save-game", other, false, JOptionPane.NO_OPTION, false);
        check(Arrays.equals(otherBytes, Files.readAllBytes(other)), "Declined overwrite preserves bytes");
        equal(saveSuggestion(), saved, "Declined overwrite preserves selected path");

        Path blockedSave = Files.createDirectory(workingDirectory.resolve("directory.qsave"));
        Path marker = blockedSave.resolve("keep.txt");
        Files.writeString(marker, "preserve marker");
        chooser("save-game", blockedSave, false, JOptionPane.YES_OPTION, true);
        equal(saveSuggestion(), saved, "Failed save keeps the last valid path");
        equal(Files.readString(marker), "preserve marker", "Failed save preserves destination contents");
        assertSession(state(), original, "Failed save preserves game");
    }

    private static void legacyImport() throws Exception {
        Path legacy = Path.of(System.getProperty("quoridor.test.project", "."), "SaveFile.G3M")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(legacy)) {
            System.out.println("Optional original SaveFile.G3M fixture is absent; legacy dialog import skipped.");
            return;
        }
        byte[] legacyBytes = Files.readAllBytes(legacy);
        GameSession expected = SaveStore.load(legacy);
        chooser("load-game-in-game", legacy, false, JOptionPane.YES_OPTION, false);
        assertSession(state(), expected, "Legacy file opens through chooser");
        equal(saveSuggestion().getFileName().toString(), "Quoridor.qsave",
                "Imported legacy save has no overwrite path");
        Path imported = workingDirectory.resolve("imported.qsave");
        chooser("save-game", imported, false, JOptionPane.YES_OPTION, false);
        assertSession(SaveStore.load(imported), state(), "Imported match saves in new format");
        check(Arrays.equals(legacyBytes, Files.readAllBytes(legacy)), "Original legacy file remains unchanged");
    }

    /** Observes the filename suggested by the actual UI, without reading private board fields. */
    private static Path saveSuggestion() throws Exception {
        return chooser("save-game", workingDirectory.resolve("never-written.qsave"), true, JOptionPane.NO_OPTION, false);
    }

    private static Path chooser(String action, Path selected, boolean cancel, int overwrite,
                                boolean expectError) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Path> suggestion = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            I18n translation = new I18n(board.currentLanguage());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            boolean[] chosen = {false};
            boolean[] sawError = {false};
            Timer timer = new Timer(30, null);
            timer.addActionListener(event -> {
                try {
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("Dialog did not complete: " + action);
                    }
                    for (Window window : Window.getWindows()) {
                        if (!window.isShowing() || !(window instanceof JDialog dialog) || !ownedByBoard(dialog)) {
                            continue;
                        }
                        JFileChooser fileChooser = find(dialog, JFileChooser.class);
                        if (fileChooser != null && !chosen[0]) {
                            chosen[0] = true;
                            assertDialogLanguage(dialog, fileChooser, translation);
                            equal(dialog.getTitle(), translation.text(action.equals("save-game")
                                    ? "Save your match" : "Load a saved match"), "Chooser title uses its window language");
                            check(buttonWithText(fileChooser, translation.text(action.equals("save-game") ? "Save" : "Open")) != null,
                                    "Chooser approval button is localized");
                            check(buttonWithText(fileChooser, translation.text("Cancel")) != null,
                                    "Chooser cancellation button is localized");
                            check(containsLabel(fileChooser, translation.text("File name:")), "Filename label is localized");
                            check(containsLabel(fileChooser, translation.text("Files of type:")), "Filetype label is localized");
                            equal(fileChooser.getFileFilter().getDescription(),
                                    translation.text("Quoridor matches (*.qsave, *.G3M)"), "Chooser file filter is localized");
                            if (translation.rightToLeft()) {
                                assertNoEnglishChooserStrings(fileChooser,
                                        Collections.newSetFromMap(new IdentityHashMap<>()));
                            } else if (Locale.getDefault().getLanguage().equals("he")
                                    || Locale.getDefault().getLanguage().equals("iw")) {
                                assertNoHebrewControlStrings(fileChooser);
                            }
                            if (translation.language() == I18n.Language.PERSIAN && action.equals("save-game")
                                    && !capturedPersianDialog) {
                                capture(dialog);
                                capturedPersianDialog = true;
                            }
                            if (inspectedDetails.add(translation.language())) {
                                assertDetailsView(fileChooser, translation);
                            }
                            if (fileChooser.getSelectedFile() != null) {
                                suggestion.set(fileChooser.getSelectedFile().toPath());
                            }
                            fileChooser.setSelectedFile(selected.toFile());
                            if (cancel) { fileChooser.cancelSelection(); }
                            else { fileChooser.approveSelection(); }
                            return;
                        }
                        JOptionPane pane = find(dialog, JOptionPane.class);
                        if (pane != null) {
                            assertDialogLanguage(dialog, pane, translation);
                            if (dialog.getTitle().equals(translation.text("Replace saved match"))) {
                                check(messageText(pane.getMessage()).contains(selected.getFileName().toString()),
                                        "Localized overwrite prompt preserves the filename");
                                clickDialogOption(dialog, translation.text(overwrite == JOptionPane.YES_OPTION ? "Yes" : "No"));
                            } else if (dialog.getTitle().equals(translation.text("Couldn't save this match"))
                                    || dialog.getTitle().equals(translation.text("Couldn't load this match"))) {
                                sawError[0] = true;
                                String reason = action.equals("save-game") ? "Choose a writable folder and try again."
                                        : "Choose a valid Quoridor .qsave or compatible old .G3M file.";
                                check(messageText(pane.getMessage()).contains(translation.text(reason)),
                                        "File error explanation is localized");
                                check(messageText(pane.getMessage()).contains(translation.text("Your current match is still available.")),
                                        "File error preserves the localized current-match explanation");
                                clickDialogOption(dialog, translation.text("OK"));
                            } else {
                                throw new AssertionError("Unexpected dialog: " + dialog.getTitle());
                            }
                            return;
                        }
                    }
                } catch (Throwable problem) {
                    failure.set(problem);
                    disposeDialogs();
                    timer.stop();
                }
            });
            timer.start();
            try {
                click(action);
                if (failure.get() != null) {
                    throw new AssertionError("Dialog automation failed", failure.get());
                }
                check(chosen[0], "File dialog displayed for " + action);
                check(sawError[0] == expectError, "Expected file error state for " + action);
                equal(board.currentLanguage(), translation.language(), "Save/load preserves the selected UI language");
            } finally {
                timer.stop();
            }
        });
        return suggestion.get();
    }

    private static void assertDialogLanguage(JDialog dialog, JComponent content, I18n translation) {
        equal(content.getLocale(), translation.locale(), "Dialog content has its instance locale");
        equal(dialog.getLocale(), translation.locale(), "Dialog window has its instance locale");
        check(content.getComponentOrientation().isLeftToRight() != translation.rightToLeft(),
                "Dialog content has the selected writing direction");
        check(dialog.getComponentOrientation().isLeftToRight() != translation.rightToLeft(),
                "Dialog window has the selected writing direction");
    }

    private static void clickDialogOption(JDialog dialog, String label) {
        AbstractButton button = buttonWithText(dialog, label);
        check(button != null && button.isShowing(), "Localized dialog option is displayed: " + label);
        button.doClick(0);
    }

    private static void assertDetailsView(JFileChooser chooser, I18n translation) {
        JList<?> list = find(chooser, JList.class);
        check(list != null, "Chooser list view exists");
        equal(list.getAccessibleContext().getAccessibleName(), translation.text("Files List"),
                "Chooser list accessibility name is localized");
        AbstractButton details = buttonWithTooltip(chooser, translation.text("Details"));
        check(details != null && details.isEnabled(), "Localized Details view action is available");
        details.doClick(0);
        JTable table = find(chooser, JTable.class);
        check(table != null && table.isShowing(), "Chooser Details table is displayed");
        equal(table.getAccessibleContext().getAccessibleName(), translation.text("Files Details"),
                "Chooser details accessibility name is localized");
        Set<String> headers = Set.of(translation.text("Name"), translation.text("Size"), translation.text("Type"),
                translation.text("Modified"), translation.text("Attributes"));
        for (int i = 0; i < table.getColumnCount(); i++) {
            String header = String.valueOf(table.getColumnModel().getColumn(i).getHeaderValue());
            check(headers.contains(header), "Chooser detail header is localized: " + header);
        }
        AbstractButton listView = buttonWithTooltip(chooser, translation.text("List"));
        check(listView != null && listView.isEnabled(), "Localized List view action is available");
        listView.doClick(0);
    }

    private static AbstractButton buttonWithTooltip(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getToolTipText())) { return button; }
            if (child instanceof Container container) {
                AbstractButton nested = buttonWithTooltip(container, text);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }

    private static AbstractButton buttonWithText(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) { return button; }
            if (child instanceof Container container) {
                AbstractButton nested = buttonWithText(container, text);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }

    private static boolean containsLabel(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && text.equalsIgnoreCase(label.getText())) { return true; }
            if (child instanceof Container container && containsLabel(container, text)) { return true; }
        }
        return false;
    }

    private static String messageText(Object message) {
        if (message instanceof Object[] messages) {
            StringBuilder combined = new StringBuilder();
            for (Object part : messages) { combined.append(messageText(part)).append('\n'); }
            return combined.toString();
        }
        if (message instanceof JLabel label) { return label.getText(); }
        return String.valueOf(message);
    }

    private static void assertNoEnglishChooserStrings(Component component, Set<Component> visited) {
        if (!visited.add(component)) { return; }
        String text = component instanceof JLabel label ? label.getText()
                : component instanceof AbstractButton button ? button.getText() : null;
        if (text != null && !text.isBlank()) {
            check(!UNTRANSLATED_CHOOSER.contains(text), "Persian chooser label/menu is translated: " + text);
        }
        if (component instanceof JComponent control) {
            if (control.getToolTipText() != null) {
                check(!UNTRANSLATED_CHOOSER.contains(control.getToolTipText()),
                        "Persian chooser tooltip is translated: " + control.getToolTipText());
            }
            JPopupMenu popup = control.getComponentPopupMenu();
            if (popup != null) { assertNoEnglishChooserStrings(popup, visited); }
        }
        if (component instanceof JMenu menu) {
            for (Component child : menu.getMenuComponents()) { assertNoEnglishChooserStrings(child, visited); }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) { assertNoEnglishChooserStrings(child, visited); }
        }
    }

    private static void assertNoHebrewControlStrings(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton button) {
                for (String text : new String[]{button.getText(), button.getToolTipText()}) {
                    if (text != null) {
                        check(text.codePoints().noneMatch(codePoint -> codePoint >= 0x0590 && codePoint <= 0x05FF),
                                "Unsupported system locale does not change chooser controls: " + text);
                    }
                }
            }
            if (child instanceof Container container) { assertNoHebrewControlStrings(container); }
        }
    }

    private static void capture(JDialog dialog) throws IOException {
        Container content = dialog.getContentPane();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        content.printAll(graphics);
        graphics.dispose();
        Path output = Path.of(System.getProperty("quoridor.test.output", "out"), "language-save-dialog-fa.png");
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(image, "png", output.toFile());
    }

    private static GameSession state() throws Exception {
        AtomicReference<GameSession> session = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> session.set(board.currentSession()));
        return session.get();
    }

    private static boolean ownedByBoard(Window window) {
        for (Window owner = window.getOwner(); owner != null; owner = owner.getOwner()) {
            if (owner == board) { return true; }
        }
        return false;
    }

    private static void disposeDialogs() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog && ownedByBoard(window)) { window.dispose(); }
        }
    }

    private static void cleanTemporaryFiles() throws IOException {
        if (directory == null || !Files.exists(directory)) { return; }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void cleanTemporaryFilesQuietly() {
        try {
            cleanTemporaryFiles();
        } catch (IOException failure) {
            System.err.println("Could not remove SaveDialogTest temporary files: " + failure.getMessage());
        }
    }

    private static void click(String name) {
        AbstractButton button = named(board, name);
        if (button == null || !button.isEnabled() || !button.isShowing()) {
            throw new AssertionError("Unavailable button: " + name);
        }
        button.doClick(0);
    }

    private static AbstractButton named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName()) && child instanceof AbstractButton button) { return button; }
            if (child instanceof Container container) {
                AbstractButton nested = named(container, name);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) { return type.cast(child); }
            if (child instanceof Container container) {
                T nested = find(container, type);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }

    private static void assertSession(GameSession actual, GameSession expected, String message) {
        equal(actual.game().snapshot(), expected.game().snapshot(), message + " state");
        equal(actual.config(), expected.config(), message + " config");
    }

    private static void equal(Object actual, Object expected, String message) {
        check(Objects.equals(actual, expected), message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
}
