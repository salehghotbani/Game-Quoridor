package Multimedia;

import java.io.*;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;

public class Audio {
    public Audio(String FilePath) {
        try {
            // The Clip interface represents a special kind of data line whose audio data can be loaded prior to playback, instead of being streamed in real time.
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new File(FilePath)));
            clip.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}