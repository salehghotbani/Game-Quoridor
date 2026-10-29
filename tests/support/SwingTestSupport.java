package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Shared Swing mechanics; assertions and match scenarios stay in their test suites. */
public final class SwingTestSupport {
    private SwingTestSupport() {}

    public static <T> T edt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            result.set(action.call());
                        } catch (Exception exception) {
                            failure.set(exception);
                        }
                    });
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /** Follow the visible More action before exercising a secondary game command. */
    public static void revealAction(Container owner, String name) {
        if (!java.util.Set.of("new-game", "load-game-in-game", "move-history").contains(name))
            return;
        Component panel = find(owner, "extra-actions");
        if (panel != null && !panel.isVisible()) {
            ((javax.swing.AbstractButton) find(owner, "more-actions")).doClick(0);
            owner.validate();
        }
    }

    public static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static <T> T findType(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findType(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static BufferedImage containerImage(Container content) {
        BufferedImage image =
                new BufferedImage(
                        content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            content.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static void writeImage(BufferedImage image, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("No PNG image writer is available.");
        }
    }
}
