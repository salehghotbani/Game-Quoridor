import Board.Board;
import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.I18n;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Captures the real Swing content for the README, without changing game saves. */
public final class CaptureScreenshots {
    private CaptureScreenshots() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: CaptureScreenshots <output-directory>");
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Screenshot capture requires a graphical desktop.");
        }
        Path destination = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(destination);
        SwingUtilities.invokeAndWait(() -> captureAll(destination));
    }

    private static void captureAll(Path destination) {
        Board board = new Board(null, I18n.Language.ENGLISH, Board.Appearance.DARK);
        board.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        board.setSize(new Dimension(1000, 750));
        try {
            board.setVisible(true);
            menuName(board, "Player 1");
            click(board, "bot-game");
            difficulty(board).setSelectedItem(BotPlayer.Difficulty.HARD);
            capture(board, destination.resolve("menu-en-dark.png"));

            GameState position = progressedGame();
            GameConfig english = new GameConfig("Player 1", "Robot", GameConfig.Mode.BOT,
                    BotPlayer.Difficulty.HARD);
            board.startSession(new GameSession(position, english));
            capture(board, destination.resolve("game-en-dark.png"));
            board.setAppearance(Board.Appearance.LIGHT);
            capture(board, destination.resolve("game-en-light.png"));

            board.setLanguage(I18n.Language.PERSIAN);
            GameConfig persian = new GameConfig("بازیکن ۱", "ربات", GameConfig.Mode.BOT,
                    BotPlayer.Difficulty.HARD);
            board.startSession(new GameSession(position, persian));
            board.setAppearance(Board.Appearance.DARK);
            capture(board, destination.resolve("game-fa-dark.png"));
            board.setAppearance(Board.Appearance.LIGHT);
            capture(board, destination.resolve("game-fa-light.png"));
            if (!board.currentSession().game().snapshot().equals(position.snapshot())) {
                throw new IllegalStateException("The documentation position changed during capture.");
            }
            click(board, "back-menu");
            menuName(board, "بازیکن ۱");
            capture(board, destination.resolve("menu-fa-light.png"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            board.dispose();
        }
    }

    /** Every action is validated by the production rules; blue has the next turn. */
    private static GameState progressedGame() {
        GameState game = new GameState();
        legal(game.moveTo(7, 4));
        legal(game.moveTo(1, 4));
        legal(game.placeWall(1, 3, GameState.Orientation.HORIZONTAL));
        legal(game.placeWall(6, 4, GameState.Orientation.HORIZONTAL));
        legal(game.moveTo(7, 3));
        legal(game.moveTo(1, 5));
        legal(game.moveTo(6, 3));
        legal(game.moveTo(2, 5));
        legal(game.placeWall(2, 5, GameState.Orientation.VERTICAL));
        legal(game.placeWall(5, 2, GameState.Orientation.VERTICAL));
        legal(game.moveTo(5, 3));
        legal(game.moveTo(3, 5));
        if (game.currentPlayer() != 0 || game.winner() != -1
                || game.wallsRemaining(0) != 8 || game.wallsRemaining(1) != 8) {
            throw new IllegalStateException("The documentation position is inconsistent.");
        }
        return game;
    }

    private static void legal(boolean accepted) {
        if (!accepted) {
            throw new IllegalStateException("A documentation move is no longer legal.");
        }
    }

    private static void capture(Board board, Path target) throws IOException {
        board.invalidate();
        board.validate();
        Container content = board.getContentPane();
        layout(content);
        checkVisibleControls(content, content, new I18n(board.currentLanguage()));
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            content.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0f);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        System.out.printf("Captured %s (%d x %d, %,d bytes)%n", target.getFileName(),
                image.getWidth(), image.getHeight(), Files.size(target));
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component component : parent.getComponents()) {
            if (component instanceof Container container) {
                layout(container);
            }
        }
    }

    private static void checkVisibleControls(Container parent, Container content, I18n text) {
        for (Component component : parent.getComponents()) {
            if (component.isShowing() && component instanceof JComponent control) {
                if (control.getName() != null) {
                    Rectangle bounds = SwingUtilities.convertRectangle(control.getParent(),
                            control.getBounds(), content);
                    if (bounds.width <= 0 || bounds.height <= 0 || bounds.x < 0 || bounds.y < 0
                            || bounds.x + bounds.width > content.getWidth()
                            || bounds.y + bounds.height > content.getHeight()) {
                        throw new IllegalStateException("Clipped screenshot control: " + control.getName());
                    }
                }
                if (control instanceof JLabel label && (text.text("Language").equals(label.getText())
                        || text.text("Appearance").equals(label.getText()))
                        && label.getWidth() < label.getPreferredSize().width) {
                    throw new IllegalStateException("Clipped screenshot label: " + label.getText());
                }
            }
            if (component instanceof Container container) {
                checkVisibleControls(container, content, text);
            }
        }
    }

    private static void menuName(Board board, String name) {
        ((JTextField) find(board, "blue-name")).setText(name);
    }

    private static JComboBox<?> difficulty(Board board) {
        return (JComboBox<?>) find(board, "difficulty");
    }

    private static void click(Board board, String name) {
        ((AbstractButton) find(board, name)).doClick(0);
    }

    private static Component find(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container container) {
                Component nested = find(container, name);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }
}
