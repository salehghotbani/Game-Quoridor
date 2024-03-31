package Welcome;

import Board.Board;
import Multimedia.PDF;
import Multimedia.Video;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class Welcome extends JFrame {

    public Welcome() {
        initComponents();
    }

    private void TwoPlayerButtonMouseClicked() {
        new TowPlayer(this);
    }

    private void loadButtonMouseClicked() {
        Board board = new Board();
        board.MakeAGraph();
        board.StartLoading();
        board.setVisible(true);
        this.setVisible(false);
    }

    private void HelpButtonMouseClicked() {
        new PDF("src//Files//PDF//HowToPlay.pdf");
    }

    private void AboutUsLabelMouseClicked() {
        Video video = new Video("src//Files//Video//AboutUs.mp4");
        video.setVisible(true);
        video.setResizable(false);
        this.setVisible(false);
    }

    private void initComponents() {
        JPanel panel1 = new JPanel();
        JButton twoPlayerButton = new JButton(), helpButton = new JButton(), loadButton = new JButton(), AboutUsButton = new JButton();
        JLabel label1 = new JLabel();

        //======== this ========
        setTitle("Welcome");
        setResizable(false);
        setMinimumSize(new Dimension(5, 5));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImage(new ImageIcon(getClass().getResource("/Files/Images/com.nozhaco.quoridor_512x512.png")).getImage());
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //======== panel1 ========
        {
            panel1.setLayout(null);

            //---- TwoPlayerButton ----
            twoPlayerButton.setText("New Game");
            twoPlayerButton.setForeground(Color.black);
            twoPlayerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            twoPlayerButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    TwoPlayerButtonMouseClicked();
                }
            });
            panel1.add(twoPlayerButton);
            twoPlayerButton.setBounds(170, 310, 100, 35);

            //---- HelpButton ----
            helpButton.setText("How Play");
            helpButton.setForeground(Color.black);
            helpButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            helpButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    HelpButtonMouseClicked();
                }
            });
            panel1.add(helpButton);
            helpButton.setBounds(170, 445, 100, 35);

            //---- LoadButton ----
            loadButton.setText("Load");
            loadButton.setForeground(Color.black);
            loadButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            loadButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    loadButtonMouseClicked();
                }
            });
            panel1.add(loadButton);
            loadButton.setBounds(170, 380, 100, 35);

            //---- AboutUsButton ----
            AboutUsButton.setText("About Us");
            AboutUsButton.setForeground(Color.BLACK);
            AboutUsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            AboutUsButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    AboutUsLabelMouseClicked();
                }
            });
            panel1.add(AboutUsButton);
            AboutUsButton.setBounds(170, 505, 100, 35);

            //---- label1 ----
            label1.setIcon(new ImageIcon(getClass().getResource("/Files/Images/BackgroundWelcome.jpg")));
            panel1.add(label1);
            label1.setBounds(0, 0, 445, 580);

        }
        contentPane.add(panel1);
        panel1.setBounds(0, 0, 445, 580);

        {
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

        pack();
        setLocationRelativeTo(getOwner());
    }
}