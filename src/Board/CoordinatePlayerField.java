package Board;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class CoordinatePlayerField {
    private int GreenLocation, BlueLocation;

    CoordinatePlayerField(ArrayList<JButton> jButtonArrayList) { //which Field button blue which green?!
        for (int i = 128; i < jButtonArrayList.size() - 1; i++) {
            if (jButtonArrayList.get(i).getBackground() == Color.GREEN)
                GreenLocation = i;

            if (jButtonArrayList.get(i).getBackground() == Color.BLUE)
                BlueLocation = i;
        }
    }

    protected int getGreenLocation() {
        return GreenLocation;
    }

    protected int getBlueLocation() {
        return BlueLocation;
    }
}