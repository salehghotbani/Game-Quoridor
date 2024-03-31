package Board;

import Multimedia.Audio;
import SaveAndLoad.Load;
import SaveAndLoad.Save;
import Welcome.Welcome;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;

public class Board extends JFrame {
    private final DefaultUndirectedGraph<JButton, DefaultEdge> graph = new DefaultUndirectedGraph<>(DefaultEdge.class);
    private final ArrayList<JButton> jButtonArrayList = new ArrayList<>();
    private AddElementToBoard addElementToBoard;
    private boolean isRoundPlayer1 = true;
    private int position, WallOfPlayer1 = 10, WallOfPlayer2 = 10, countVertical = 0, countHorizontal = 0;
    private FieldButton fieldButton;

    public Board() {
        initComponents();
    }

    private void check(JButton sender, int i) { //Set Wall Color Orange
        sender.setBackground(Color.ORANGE);
        sender.setEnabled(false);
        new WallButton().MakeFalse(jButtonArrayList, i);
        isRoundPlayer1 = !isRoundPlayer1;
    }

    private void isWin(String str) { //Check Anyone has Win?!
        WinPage winPage = new WinPage();
        winPage.setTitleOfLabel(str);
        winPage.setVisible(true);
        this.setVisible(false);
    }

    private void RoundPlayer1() {
        addElementToBoard.PlayerColorRound1.setVisible(false);
        addElementToBoard.PlayerColorRound2.setVisible(true);
    }

    private void RoundPlayer2() {
        addElementToBoard.PlayerColorRound1.setVisible(true);
        addElementToBoard.PlayerColorRound2.setVisible(false);
    }

    private void CountWood() { //Update JLabel of count wood
        addElementToBoard.Player1WoodCount.setText("Count Wood : " + WallOfPlayer1);
        addElementToBoard.Player2WoodCount.setText("Count Wood : " + WallOfPlayer2);
    }

    private void WallMouseEntered(int i) { //When mouse entered to the Button
        if (jButtonArrayList.get(i).isEnabled()) {
            jButtonArrayList.get(i).setBackground(Color.gray);
        } else {
            jButtonArrayList.get(i).setBackground(Color.ORANGE);
        }
    }

    private void WallMouseExited(int i) { //When mouse leave the Button
        if (jButtonArrayList.get(i).isEnabled()) {
            jButtonArrayList.get(i).setBackground(null);
        } else {
            jButtonArrayList.get(i).setBackground(Color.ORANGE);
        }
    }

    private void ButtonMouseClick(JButton sender, int i) { //When mouse clicked button
        if (sender.isEnabled()) {
            if (i < 128) { //Wall
                if (new WallButton().shortPath(jButtonArrayList, graph, i, countVertical, countHorizontal)) { //have a short path?
                    if (isRoundPlayer1 && WallOfPlayer1 > 0) {
                        new Audio("src/Files/Audio/WallSound.wav"); //Play Audio
                        WallOfPlayer1--;
                        CountWood(); //update count wood label
                        check(sender, i); //set orange
                        RoundPlayer1(); //Shift shift
                    } else if ((!isRoundPlayer1) && WallOfPlayer2 > 0) {
                        new Audio("src/Files/Audio/WallSound.wav");//Play Audio
                        WallOfPlayer2--;
                        CountWood();//update count wood label
                        check(sender, i); //set orange
                        RoundPlayer2();//Shift shift
                    }
                    countHorizontal = 0;
                    countVertical = 0;
                }
            } else if (i <= 210) { //Field
                new Audio("src/Files/Audio/MenschSound.wav");//Play Audio
                if (isRoundPlayer1) {
                    sender.setBackground(Color.BLUE);
                    if ((i - 127) <= 9) {
                        isWin(addElementToBoard.Player1Name.getText() + " Win");
                    }
                    RoundPlayer1();
                } else {
                    sender.setBackground(Color.GREEN);
                    if ((i - 127) >= 73) {
                        isWin(addElementToBoard.Player2Name.getText() + " Win");
                    }
                    RoundPlayer2();
                }
                isRoundPlayer1 = !isRoundPlayer1;
                jButtonArrayList.get(position).setBackground(null);
            }
            fieldButton = new FieldButton(jButtonArrayList, isRoundPlayer1); //Update fieldButton
            setPositionButton(fieldButton.setPositionButton()); //Position = the coordinate of FieldButton
        }
    }

    private void thisWindowClosing() {
        jButtonArrayList.clear();
        Welcome welcome = new Welcome();
        welcome.setVisible(true);
    }

    private void SaveMouseClick() { //When Save button clicked
        new Save(jButtonArrayList, addElementToBoard.Player1Name.getText(), addElementToBoard.Player2Name.getText(), this, addElementToBoard.PlayerColorRound1, addElementToBoard.PlayerColorRound2, WallOfPlayer1, WallOfPlayer2, graph);
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        JLabel backgroundImage = new JLabel();

        //======== this ========
        setTitle("Let's Play");
        setMinimumSize(new Dimension(50, 50));
        setIconImage(new ImageIcon(getClass().getResource("/Files/Images/com.nozhaco.quoridor_512x512.png")).getImage()); //Set icon for game
        var contentPane = getContentPane();
        contentPane.setLayout(null);
        setResizable(false);

        //======== panel ========
        {
            panel.setMinimumSize(new Dimension(50, 40));
            panel.setPreferredSize(new Dimension(500, 395));
            panel.setLayout(null);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    thisWindowClosing();
                }
            });

            addElementToBoard = new AddElementToBoard(panel, jButtonArrayList, WallOfPlayer1, WallOfPlayer2);
            for (int i = 0; i <= jButtonArrayList.size() - 2; i++) { //Create MouseClick, mouseEntered, mouseExited for button
                int finalI = i;
                jButtonArrayList.get(i).addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        ButtonMouseClick(jButtonArrayList.get(finalI), finalI);
                    }
                });

                if (i < 128) {
                    jButtonArrayList.get(i).addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            WallMouseEntered(finalI);
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            WallMouseExited(finalI);
                        }
                    });
                }

                panel.add(jButtonArrayList.get(i));
            }

            //SaveButton
            jButtonArrayList.get(jButtonArrayList.size() - 1).addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    SaveMouseClick();
                }
            });

            //Background
            backgroundImage.setIcon(new ImageIcon(getClass().getResource("/Files/Images/BackgroundBoard.jpg"))); //set background image (JLabel is in background)
            backgroundImage.setBounds(0, 0, 500, 395);
            panel.add(backgroundImage);

        }
        contentPane.add(panel);
        panel.setBounds(0, 0, 500, 395);

        {
            // compute preferred size
            Dimension preferredSize = new Dimension();
            for (int i = 0; i < contentPane.getComponentCount(); i++) {
                Rectangle bounds = contentPane.getComponent(i).getBounds();
                preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
            }
            Insets insets = contentPane.getInsets();
            preferredSize.width += insets.right;
            preferredSize.height += insets.bottom;
            contentPane.setMinimumSize(preferredSize);
            contentPane.setPreferredSize(preferredSize);
        }

        for (int i = 0; i < 128; i++) {
            jButtonArrayList.get(i).setBorder(null); //set button border null
            //jButtonArrayList.get(i).setContentAreaFilled(true);
        }

        fieldButton = new FieldButton(jButtonArrayList, isRoundPlayer1);
        setPositionButton(fieldButton.setPositionButton());

        pack();
        setLocationRelativeTo(getOwner());
    }

    protected void setPositionButton(int position) {
        this.position = position;
    }

    public void setPlayerText(String NamePlayerOne, String NamePlayerTwe) {
        addElementToBoard.Player1Name.setText("Player1: " + NamePlayerOne);
        addElementToBoard.Player2Name.setText("Player2: " + NamePlayerTwe);
    }

    public void StartLoading() {
        new Load(jButtonArrayList, addElementToBoard.Player1Name, addElementToBoard.Player2Name, this, addElementToBoard.PlayerColorRound1, addElementToBoard.PlayerColorRound2, graph); //Load a saved game
    }

    public Boolean getIsRoundPlayer1() {
        return isRoundPlayer1;
    }

    public void setIsRoundPlayer1(Boolean isRoundPlayer1) {
        this.isRoundPlayer1 = isRoundPlayer1;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public void MakeAGraph() {
        new MakeGraph(jButtonArrayList, graph);
    }

    public void setWallOfPlayer(int WallOfPlayer1, int WallOfPlayer2) {
        this.WallOfPlayer1 = WallOfPlayer1;
        this.WallOfPlayer2 = WallOfPlayer2;
        CountWood();
    }
}