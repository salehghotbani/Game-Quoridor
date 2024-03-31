package Multimedia;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

import Welcome.Welcome;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

public class Video extends JFrame {
    private void LibraryOfVLC() {
        try {
            //Load the native library of VLC Player
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "src//Files//vlcPlugIn");
            Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void thisWindowClosing() {
        Welcome welcome = new Welcome();
        welcome.setVisible(true);
    }

    public Video(String strPath) {
        try {
            SwingUtilities.invokeLater(() -> {
                LibraryOfVLC();
                setTitle("About Us");
                setSize(430, 500);

                Canvas canvas = new Canvas(); //Create a blank rectangular area of the screen
                canvas.setBackground(Color.black);
                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());
                panel.add(canvas, BorderLayout.CENTER);
                add(panel, BorderLayout.CENTER); //Add panel to JFrame

                //Initialize The MediaPlayer
                MediaPlayerFactory mediaPlayerFactory = new MediaPlayerFactory();
                //Create a media player instance
                EmbeddedMediaPlayer mediaPlayer = mediaPlayerFactory.newEmbeddedMediaPlayer();
                //Place that will be play video
                mediaPlayer.setVideoSurface(mediaPlayerFactory.newVideoSurface(canvas));

                //Hide the mouse cursor inside JFrame
                mediaPlayer.setEnableMouseInputHandling(true);
                //Disable the keyboard inside JFrame
                mediaPlayer.setEnableKeyInputHandling(false);

                mediaPlayer.prepareMedia(strPath);
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