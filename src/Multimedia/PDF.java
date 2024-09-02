package Multimedia;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PDF {
    public PDF(String filePath) {
        try {
            // Attempt to load the file from the classpath
            InputStream inputStream = getClass().getResourceAsStream(filePath);

            if (inputStream != null) {
                // Create a temporary file
                File tempFile = File.createTempFile("tempPDF", ".pdf");
                tempFile.deleteOnExit();  // Ensure it is deleted when the program exits

                // Copy the InputStream to the temporary file
                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                // Now use the temporary file to open the PDF
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(tempFile);  // Open the PDF
                    JOptionPane.showMessageDialog(null, "The PDF file will be opened now.", "Alert", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "Awt Desktop is not supported!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(null, "File not found inside JAR!\nPlease reinstall the application.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
