package Board;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Bounded UTF-8 save I/O and atomic replacement; format readers validate the game data. */
public final class SaveStore {
    private static final int MAX_SAVE_BYTES = 64 * 1024;
    private static final int MAX_LEGACY_BYTES = 256 * 1024;

    private SaveStore() {}

    /** Writes in the destination directory, then atomically replaces the completed save. */
    public static void save(Path path, GameSession session) throws IOException {
        Objects.requireNonNull(path, "Save path is required");
        Objects.requireNonNull(session, "Game session is required");
        GameState.Snapshot state = validatedSnapshot(session);
        byte[] bytes =
                CurrentSaveFormat.write(state, session.config()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SAVE_BYTES) {
            throw new IOException("Save file exceeds the 64 KiB limit");
        }
        writeAtomically(path, bytes);
    }

    /** Loads the current format or safely imports the original application's text save. */
    public static GameSession load(Path path) throws IOException {
        Objects.requireNonNull(path, "Save path is required");
        byte[] bytes = readBounded(path);
        String text = decodeUtf8(bytes);
        try {
            List<String> lines = text.lines().toList();
            if (LegacySaveReader.recognizes(lines)) {
                return LegacySaveReader.read(lines);
            }
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IOException("Saved game exceeds the 64 KiB limit");
            }
            return CurrentSaveFormat.read(text);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Saved game is corrupt: " + invalid.getMessage(), invalid);
        }
    }

    private static GameState.Snapshot validatedSnapshot(GameSession session) throws IOException {
        GameState.Snapshot state = session.game().snapshot();
        try {
            GameState.fromSnapshot(state);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Cannot save an invalid game: " + invalid.getMessage(), invalid);
        }
        return state;
    }

    private static byte[] readBounded(Path path) throws IOException {
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(MAX_LEGACY_BYTES + 1);
        }
        if (bytes.length > MAX_LEGACY_BYTES) {
            throw new IOException("Saved game is too large");
        }
        return bytes;
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Saved game is not valid UTF-8 text", invalid);
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Path destination = path.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null || destination.getFileName() == null) {
            throw new IOException("Choose a file path for the saved game");
        }
        Path temporary = Files.createTempFile(parent, ".quoridor-", ".tmp");
        try {
            writeAndSync(temporary, bytes);
            replaceAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeAndSync(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel =
                FileChannel.open(
                        path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void replaceAtomically(Path temporary, Path destination) throws IOException {
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException(
                    "This filesystem does not support atomic game saves; the original save was kept",
                    unsupported);
        }
    }
}
