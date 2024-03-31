package SaveAndLoad;

import Board.Board;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class Load {

    public Load(ArrayList<JButton> jButtonArrayList, JLabel Player1Name, JLabel Player2Name, Board board, JButton PlayerColorRound1, JButton PlayerColorRound2, DefaultUndirectedGraph<JButton, DefaultEdge> graph) {
        //First we create a series of variables for use in color and position retrieval and movement and start reading the file in which we have stored the information as a string.
        int i = 0, colorR, colorG, colorB, Counter = 0;
        try {
            //checking file exists.
            if (!new File("SaveFile.G3M").exists()) {
                JOptionPane.showMessageDialog(null, "You don't saved game yet.", "Alert", JOptionPane.ERROR_MESSAGE);
            } else {
                //start reading files
                Scanner Reader = new Scanner(new File("SaveFile.G3M"));
                while (Reader.hasNextLine()) {
                    //We retrieve the first player name information.
                    if (Counter == 0) {
                        Player1Name.setText(Reader.nextLine());
                        Counter++;
                    }
                    //Retrieve the second player name information.
                    else if (Counter == 1) {
                        Player2Name.setText(Reader.nextLine());
                        Counter++;
                    }
                    //We retrieve the turn.
                    else if (Counter == 2) {
                        board.setIsRoundPlayer1(Reader.nextLine().equals("true"));
                        Counter++;
                    }
                    //We retrieve the general information of the first and second player, such as color, etc.in the game environment.
                    else if (Counter == 3) {
                        String[] data2 = Reader.nextLine().split(" . ");
                        colorR = Integer.parseInt(data2[0]);
                        colorG = Integer.parseInt(data2[1]);
                        colorB = Integer.parseInt(data2[2]);

                        PlayerColorRound1.setBackground(getColor(colorR, colorG, colorB));
                        PlayerColorRound1.setVisible(data2[3].equals("true"));
                        PlayerColorRound1.setEnabled(data2[4].equals("true"));

                        colorR = Integer.parseInt(data2[5]);
                        colorG = Integer.parseInt(data2[6]);
                        colorB = Integer.parseInt(data2[7]);
                        PlayerColorRound2.setBackground(getColor(colorR, colorG, colorB));
                        PlayerColorRound2.setVisible(data2[8].equals("true"));
                        PlayerColorRound2.setEnabled(data2[9].equals("true"));

                        //Return the position and number of walls.
                        board.setPosition(Integer.parseInt(data2[10]));
                        board.setWallOfPlayer(Integer.parseInt(data2[11]), Integer.parseInt(data2[12]));
                        Counter++;
                    }
                    //We retrieve information about each button.
                    else if (Counter >= 4 && Counter <= 213) {
                        String[] data = Reader.nextLine().split(" ");

                        colorR = Integer.parseInt(data[0]);
                        colorG = Integer.parseInt(data[1]);
                        colorB = Integer.parseInt(data[2]);

                        jButtonArrayList.get(i).setBackground(getColor(colorR, colorG, colorB));
                        jButtonArrayList.get(i).setVisible(data[3].equals("true"));
                        jButtonArrayList.get(i).setEnabled(data[4].equals("true"));
                        i++;
                        Counter++;
                    }
                    //We retrieve the edges
                    else if (Counter >= 214 && Counter <= 293) {
                        if ((Reader.nextLine().equals("null"))) {
                            if (Counter <= 285) {
                                graph.removeAllEdges(jButtonArrayList.get(Counter - 214 + 128), jButtonArrayList.get(Counter - 214 + 129));
                                graph.removeAllEdges(jButtonArrayList.get(Counter - 214 + 128 + 9), jButtonArrayList.get(Counter - 214 + 129 + 9));
                            }
                        }
                        Counter++;
                    } else {
                        if ((Reader.nextLine().equals("null"))) {
                            if (Counter <= 365) {
                                graph.removeAllEdges(jButtonArrayList.get(Counter - 293 + 126), jButtonArrayList.get(Counter - 293 + 126 + 9));
                                graph.removeAllEdges(jButtonArrayList.get(Counter - 293 + 127), jButtonArrayList.get(Counter - 293 + 127 + 9));
                            }
                        }
                        Counter++;
                    }
                }

                //Reading from the file is completed.

                Reader.close();
            }

            //We manage errors and exceptions and send appropriate messages to the user.
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "The saved file is corrupt.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "The saved file is corrupt.\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    //We determine and adjust the colors according to the numbers in the file in the game environment

    private Color getColor(int Red, int Green, int Blue) {
        if (Red == 0 && Green == 0 && Blue == 255) {
            return Color.BLUE;
        } else if (Red == 0 && Green == 255 && Blue == 0) {
            return Color.GREEN;
        } else if (Red == 255 && Green == 200 && Blue == 0) {
            return Color.ORANGE;
        }
        return null;
    }
}