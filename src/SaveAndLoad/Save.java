package SaveAndLoad;

import Board.Board;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

public class Save {
    String tmpString = "";

    public Save(ArrayList<JButton> jButtonArrayList, String Player1Name, String Player2Name, Board board, JButton PlayerColorRound1, JButton PlayerColorRound2, int WallOfPlayer1, int WallOfPlayer2, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        //saving player's name
        tmpString += Player1Name + "\n" +
                Player2Name + "\n";

        //Keep turns
        tmpString += board.getIsRoundPlayer1() + "\n";
        //Here we store the information that identifies the first and second player in the game environment.
        tmpString += PlayerColorRound1.getBackground().getRed() + " . " +
                PlayerColorRound1.getBackground().getGreen() + " . " +
                PlayerColorRound1.getBackground().getBlue() + " . " +
                PlayerColorRound1.isVisible() + " . " +
                PlayerColorRound1.isEnabled() + " . ";

        tmpString += PlayerColorRound2.getBackground().getRed() + " . " +
                PlayerColorRound2.getBackground().getGreen() + " . " +
                PlayerColorRound2.getBackground().getBlue() + " . " +
                PlayerColorRound2.isVisible() + " . " +
                PlayerColorRound2.isEnabled() + " . ";
        //This line stores the number of remaining walls for each player as well as the position.
        tmpString += board.getPosition() + " . " + WallOfPlayer1 + " . " + WallOfPlayer2 + "\n";
        //Here the specifications related to the buttons such as color, etc. are stored.
        for (int i = 0; i < jButtonArrayList.size() - 1; i++) {
            tmpString += jButtonArrayList.get(i).getBackground().getRed() + " " +
                    jButtonArrayList.get(i).getBackground().getGreen() + " " +
                    jButtonArrayList.get(i).getBackground().getBlue() + " " +
                    jButtonArrayList.get(i).isVisible() + " " +
                    jButtonArrayList.get(i).isEnabled() + "\n";
        }
        tmpString += jButtonArrayList.get(jButtonArrayList.size() - 1).getBackground().getRed() + " " +
                jButtonArrayList.get(jButtonArrayList.size() - 1).getBackground().getGreen() + " " +
                jButtonArrayList.get(jButtonArrayList.size() - 1).getBackground().getBlue() + " " +
                jButtonArrayList.get(jButtonArrayList.size() - 1).isVisible() + " " +
                jButtonArrayList.get(jButtonArrayList.size() - 1).isEnabled() + "\n";
        //We retrieve the edges.
        for (int i = 128; i < jButtonArrayList.size() - 3; i++) {
            if ((i - 127) % 9 != 0) {
                tmpString += graph.getEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 1)) + "\n";
            } else {
                tmpString += "Enter\n";
            }
        }
        tmpString += graph.getEdge(jButtonArrayList.get(jButtonArrayList.size() - 4), jButtonArrayList.get(jButtonArrayList.size() - 3)) + "\nhg\n";

        for (int i = 128; i < jButtonArrayList.size() - 11; i++) {
            tmpString += graph.getEdge(jButtonArrayList.get(i), jButtonArrayList.get(i + 9)) + "\n";
        }
        tmpString += graph.getEdge(jButtonArrayList.get(jButtonArrayList.size() - 12), jButtonArrayList.get(jButtonArrayList.size() - 3));
        //Here we write in the file all the information that is in the tmpString string.
        try {
            if (!new File("SaveFile.G3M").exists()) {
                new File("SaveFile.G3M");
            }
            JOptionPane.showMessageDialog(null, "Saved");
            FileWriter fileWriter = new FileWriter("SaveFile.G3M");
            fileWriter.write(tmpString);
            fileWriter.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "You have an error: " + e.getMessage(), "Alert", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}