package Multimedia;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import javax.swing.*;

import Welcome.Welcome;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

public class Video extends JFrame {

    private File extractResource(String resourcePath) throws Exception {
        InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            throw new Exception("Resource not found: " + resourcePath);
        }

        File tempFile = File.createTempFile("video", null);
        tempFile.deleteOnExit();

        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }

    private void LibraryOfVLC() {
        try {
            // Set VLC_PLUGIN_PATH to point to the VLC plugins directory
            String pluginPath = "src/Files/vlcPlugIn";
            System.setProperty("VLC_PLUGIN_PATH", pluginPath);

            // Load the native VLC library
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), pluginPath);
            Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to load VLC library: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void thisWindowClosing() {
        Welcome welcome = new Welcome();
        welcome.setVisible(true);
    }

    public Video(String videoResourcePath) {
        try {
            SwingUtilities.invokeLater(() -> {
                LibraryOfVLC();
                setTitle("Video Player");
                setSize(430, 500);

                Canvas canvas = new Canvas();
                canvas.setBackground(Color.black);
                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());
                panel.add(canvas, BorderLayout.CENTER);
                add(panel, BorderLayout.CENTER);

                MediaPlayerFactory mediaPlayerFactory = new MediaPlayerFactory();
                EmbeddedMediaPlayer mediaPlayer = mediaPlayerFactory.newEmbeddedMediaPlayer();
                mediaPlayer.setVideoSurface(mediaPlayerFactory.newVideoSurface(canvas));

                mediaPlayer.setEnableMouseInputHandling(true);
                mediaPlayer.setEnableKeyInputHandling(false);

                // Extract the video file from the JAR and get its path
                File videoFile = null;
                try {
                    videoFile = extractResource(videoResourcePath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                mediaPlayer.prepareMedia(videoFile.getAbsolutePath());
                mediaPlayer.play();
            });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                thisWindowClosing();
            }
        });
    }
}
