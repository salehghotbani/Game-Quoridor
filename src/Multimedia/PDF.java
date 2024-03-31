package Multimedia;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PDF {
    public PDF(String filePath) {
        try {
            File pdfFile = new File(filePath);
            if (pdfFile.exists()) {
                if (Desktop.isDesktopSupported()) { //The pdf file is support in OS?!
                    Desktop.getDesktop().open(pdfFile); //open it
                    JOptionPane.showMessageDialog(null, "The PDF file will be open now.", "Alert", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "Awt Desktop is not supported!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(null, "File is not exists!\nPlease reaInstall This Game.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}