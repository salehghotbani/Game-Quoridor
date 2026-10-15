import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.SaveStore;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Exercises displayed file dialogs, including cancellation and failed save/load operations. */
public final class SaveDialogTest {
    private static int checks;
    private static Board board;
    private static Path directory;

    private SaveDialogTest() { }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("SaveDialogTest requires a graphical desktop.");
        }
        directory = Files.createTempDirectory("quoridor-file-dialog-test-");
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
            SwingUtilities.invokeAndWait(() -> {
                board = new Board(new GameSession(new GameState(), new GameConfig("صالح", "Green",
                        GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM)));
                board.setVisible(true);
                click("cell-7-4");
            });
            saveAndCancel();
            loadAndFailure();
            legacyImport();
            System.out.println("Displayed save/load dialog tests passed (" + checks + " checks).");
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    disposeDialogs();
                    if (board != null) { board.dispose(); }
                });
                cleanTemporaryFiles();
                Runtime.getRuntime().removeShutdownHook(emergencyCleanup);
            } finally {
                watchdog.shutdownNow();
            }
        }
    }

    private static void saveAndCancel() throws Exception {
        GameSession original = state();
        Path saved = directory.resolve("new match.qsave");
        Path suggestion = chooser("save-game", directory.resolve("new match"), false,
                JOptionPane.YES_OPTION, false);
        equal(suggestion.getFileName().toString(), "Quoridor.qsave", "New match has a default save filename");
        check(Files.isRegularFile(saved), "Save chooser appends .qsave");
        assertSession(SaveStore.load(saved), original, "Save chooser stores displayed game");
        byte[] savedBytes = Files.readAllBytes(saved);

        Path cancelled = directory.resolve("cancelled.qsave");
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
        Path saved = directory.resolve("new match.qsave");
        GameSession original = SaveStore.load(saved);
        chooser("load-game-in-game", saved, false, JOptionPane.YES_OPTION, false);
        assertSession(state(), original, "Open chooser restores displayed game");
        equal(saveSuggestion(), saved, "Successful load remembers path");

        chooser("load-game-in-game", directory.resolve("ignored.qsave"), true, JOptionPane.YES_OPTION, false);
        assertSession(state(), original, "Cancelled load preserves game");
        equal(saveSuggestion(), saved, "Cancelled load preserves path");
        Path corrupt = directory.resolve("corrupt.qsave");
        Files.writeString(corrupt, "version=999\n");
        chooser("load-game-in-game", corrupt, false, JOptionPane.YES_OPTION, true);
        assertSession(state(), original, "Failed load preserves game");
        equal(saveSuggestion(), saved, "Failed load preserves path");

        Path other = directory.resolve("other match.qsave");
        SaveStore.save(other, new GameSession(new GameState(), original.config()));
        byte[] otherBytes = Files.readAllBytes(other);
        chooser("save-game", other, false, JOptionPane.NO_OPTION, false);
        check(Arrays.equals(otherBytes, Files.readAllBytes(other)), "Declined overwrite preserves bytes");
        equal(saveSuggestion(), saved, "Declined overwrite preserves selected path");

        Path blockedSave = Files.createDirectory(directory.resolve("directory.qsave"));
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
        Path imported = directory.resolve("imported.qsave");
        chooser("save-game", imported, false, JOptionPane.YES_OPTION, false);
        assertSession(SaveStore.load(imported), state(), "Imported match saves in new format");
        check(Arrays.equals(legacyBytes, Files.readAllBytes(legacy)), "Original legacy file remains unchanged");
    }

    /** Observes the filename suggested by the actual UI, without reading private board fields. */
    private static Path saveSuggestion() throws Exception {
        return chooser("save-game", directory.resolve("never-written.qsave"), true, JOptionPane.NO_OPTION, false);
    }

    private static Path chooser(String action, Path selected, boolean cancel, int overwrite,
                                boolean expectError) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Path> suggestion = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
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
                            if (dialog.getTitle().equals("Replace saved match")) {
                                pane.setValue(overwrite);
                            } else if (dialog.getTitle().startsWith("Couldn't")) {
                                sawError[0] = true;
                                pane.setValue(JOptionPane.OK_OPTION);
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
            } finally {
                timer.stop();
            }
        });
        return suggestion.get();
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
