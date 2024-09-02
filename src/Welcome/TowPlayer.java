package Welcome;

import Board.Board;
import javax.swing.*;

public class TowPlayer {

    TowPlayer(Welcome welcome) {
        String NamePlayerOne = JOptionPane.showInputDialog(null, "Enter The Name Of Player 1", "Name Of Player 1", JOptionPane.INFORMATION_MESSAGE);
        if (NamePlayerOne == null || (NamePlayerOne.equals(""))) { //If is null == Click cancel or close button
            return;
        }
        String NamePlayerTwe = JOptionPane.showInputDialog(null, "Enter The Name Of Player 2", "Name Of Player 2", JOptionPane.INFORMATION_MESSAGE);
        if (NamePlayerTwe == null || (NamePlayerTwe.equals(""))) { //If is null == Click cancel or close button
            return;
        }
        showBoard(NamePlayerOne, NamePlayerTwe);
        welcome.setVisible(false);
    }

    private void showBoard(String NamePlayerOne, String NamePlayerTwe) {
        Board board = new Board();
        board.setVisible(true);
        board.setPlayerText(NamePlayerOne, NamePlayerTwe); //set player name
        board.MakeAGraph();
    }
}
