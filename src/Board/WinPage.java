package Board;

import Welcome.Welcome;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public class WinPage extends JFrame {
    JPanel panel1 = new JPanel();
    JLabel WinLabel = new JLabel();
    JLabel backGround = new JLabel();

    public WinPage() {
        initComponents();
    }

    public void setTitleOfLabel(String str) {
        WinLabel.setText(str);
    }

    private void thisWindowClosing() {
        Welcome welcome = new Welcome();
        welcome.setVisible(true);
    }

    private void initComponents() {

        //======== this ========
        setResizable(false);
        var contentPane = getContentPane();
        contentPane.setLayout(null);

        //======== panel1 ========
        {
            panel1.setLayout(null);
            panel1.add(WinLabel);

            //---- WinLabel ----
            WinLabel.setFont(WinLabel.getFont().deriveFont(WinLabel.getFont().getStyle() | Font.BOLD, WinLabel.getFont().getSize() + 10f));
            panel1.add(WinLabel);
            WinLabel.setBounds(130, 160, 300, 50);
            WinLabel.setForeground(Color.WHITE);
            //---- BackGround ----
            backGround.setIcon(new ImageIcon(getClass().getResource("/Files/Images/Winner.jpg")));
            panel1.add(backGround);
            backGround.setBounds(0, 0, 445, 380);
        }
        contentPane.add(panel1);
        panel1.setBounds(0, 0, 445, 380);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                thisWindowClosing();
            }
        });
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