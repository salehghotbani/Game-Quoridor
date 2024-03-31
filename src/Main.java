import Welcome.Welcome;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        Welcome welcome = new Welcome();
        welcome.setVisible(true);
        welcome.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }
}