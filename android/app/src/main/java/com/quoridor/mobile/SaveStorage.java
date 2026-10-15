package com.quoridor.mobile;

import android.content.Context;
import android.util.AtomicFile;

import Board.GameSession;
import Board.SaveStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** App-private recovery and bounded document streams; never treats content URIs as paths. */
final class SaveStorage {
    private static final int LIMIT = 256 * 1024;
    private final AtomicFile recovery;

    SaveStorage(Context context) {
        recovery = new AtomicFile(new File(context.getFilesDir(), "resume.qsave"));
    }

    boolean hasRecovery() {
        File base = recovery.getBaseFile();
        // Older AtomicFile writes can leave only the committed backup after a crash.
        // Presence offers recovery; openRead and the strict parser still validate it.
        return base.exists() || new File(base.getPath() + ".bak").exists();
    }

    void saveRecovery(GameSession session) throws IOException {
        byte[] data = SaveStore.encode(session).getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream = null;
        try {
            stream = recovery.startWrite();
            stream.write(data);
            recovery.finishWrite(stream);
        } catch (IOException failure) {
            if (stream != null) {
                recovery.failWrite(stream);
            }
            throw failure;
        }
    }

    GameSession loadRecovery() throws IOException {
        try (InputStream stream = recovery.openRead()) {
            return read(stream);
        }
    }

    static GameSession read(InputStream stream) throws IOException {
        if (stream == null) {
            throw new IOException("Document is unavailable");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count > LIMIT - bytes.size()) {
                throw new IOException("Saved game is too large");
            }
            bytes.write(buffer, 0, count);
        }
        String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray()))
                        .toString();
        return SaveStore.decode(text);
    }
}
