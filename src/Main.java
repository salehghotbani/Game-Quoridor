import Board.AppVersion;
import Board.Board;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("Quoridor " + AppVersion.current());
            return;
        }
        SwingUtilities.invokeLater(() -> new Board().setVisible(true));
    }
}
