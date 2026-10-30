package com.quoridor.mobile;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.InstrumentationTestCase;
import android.util.AtomicFile;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.SaveStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Actual Android AtomicFile/document streams, isolated from the target app's saved match. */
@SuppressWarnings("deprecation")
public final class SaveStorageTest extends InstrumentationTestCase {
    private File fixtureParent;
    private File directory;
    private Context context;
    private SaveStorage storage;

    public void testSharedFileBackportAndSupplementaryNamesOnDevice() throws Exception {
        GameConfig config =
                new GameConfig(
                        "صالح 🎮",
                        "\uD83D\uDE00".repeat(24),
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.EASY);
        GameSession session = new GameSession(new GameState(), config);
        assertTrue(session.game().moveTo(7, 4));
        java.nio.file.Path path = new File(directory, "portable.qsave").toPath();
        SaveStore.save(path, session);
        GameSession restored = SaveStore.load(path);
        assertEquals(session.game().snapshot(), restored.game().snapshot());
        assertEquals(config.playerNames(), restored.config().playerNames());
        for (String invalid : new String[] {"valid\uD800suffix", "valid\uDC00suffix", "A\u0001B"}) {
            try {
                new GameConfig(invalid, "Friend", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY);
                fail("Malformed Unicode/control name accepted");
            } catch (IllegalArgumentException expected) {
                // The same strict Unicode validation applies before and after API 24.
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Instrumentation runs with the target application's UID. The separate test APK's
        // Context can point at another UID's private files, which that process cannot create.
        Context targetApp = getInstrumentation().getTargetContext();
        fixtureParent = targetApp.getFilesDir().getCanonicalFile();
        assertTrue("Target app files directory must exist", fixtureParent.isDirectory());
        directory = new File(fixtureParent, "save-storage-test-" + UUID.randomUUID());
        assertEquals(fixtureParent, directory.getCanonicalFile().getParentFile());
        assertTrue("Cannot create isolated target-UID fixture: " + directory, directory.mkdir());
        context =
                new ContextWrapper(targetApp) {
                    @Override
                    public File getFilesDir() {
                        return directory;
                    }
                };
        storage = new SaveStorage(context);
        assertFalse(storage.hasRecovery());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (directory != null) {
                assertEquals(fixtureParent, directory.getCanonicalFile().getParentFile());
                assertTrue(directory.getName().startsWith("save-storage-test-"));
                File[] files = directory.listFiles();
                if (files != null) {
                    String parent = directory.getCanonicalPath() + File.separator;
                    for (File file : files) {
                        assertTrue(
                                "Only this test's own fixture may be removed",
                                file.getCanonicalPath().startsWith(parent));
                        assertTrue("Recovery fixtures must be files", file.isFile());
                        assertTrue(file.delete());
                    }
                }
                assertTrue(directory.delete());
            }
        } finally {
            super.tearDown();
        }
    }

    public void testEnglishPersianTwoFourAndEveryBotLevelRoundTrip() throws Exception {
        for (boolean persian : List.of(false, true)) {
            for (int count : new int[] {2, 4}) {
                roundTrip(
                        session(count, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY, persian));
            }
            for (BotPlayer.Difficulty level : BotPlayer.Difficulty.values()) {
                roundTrip(session(2, GameConfig.Mode.BOT, level, persian));
            }
        }
    }

    public void testIndependentDesktopV1AndV2TextFixturesImportAndResave() throws Exception {
        String two =
                "# Desktop UTF-8 save\nversion=1\nblue.name=آبی 🎮\ngreen.name=Robot\nmode=BOT\ndifficulty=HARD\nblue.row=6\nblue.col=4\ngreen.row=1\ngreen.col=4\nblue.walls=10\ngreen.walls=9\nturn=0\nwinner=-1\nwall.count=1\nwall.0=3,3,HORIZONTAL\n";
        GameSession imported = read(two.getBytes(StandardCharsets.UTF_8));
        assertEquals(GameConfig.Mode.BOT, imported.config().mode());
        assertEquals(BotPlayer.Difficulty.HARD, imported.config().difficulty());
        assertEquals("آبی 🎮", imported.config().blueName());
        assertEquals(new GameState.Cell(6, 4), imported.game().playerPosition(0));
        assertEquals(9, imported.game().wallsRemaining(1));
        assertTrue(imported.game().hasWall(3, 3, GameState.Orientation.HORIZONTAL));
        roundTrip(imported);
        String four =
                "# Desktop four-player save\nversion=2\nplayer.count=4\nmode=LOCAL\ndifficulty=EASY\nplayer.0.name=Blue\nplayer.0.row=8\nplayer.0.col=4\nplayer.0.walls=5\nplayer.1.name=سبز\nplayer.1.row=0\nplayer.1.col=4\nplayer.1.walls=5\nplayer.2.name=Red\nplayer.2.row=4\nplayer.2.col=0\nplayer.2.walls=5\nplayer.3.name=طلایی\nplayer.3.row=4\nplayer.3.col=8\nplayer.3.walls=5\nturn=0\nwinner=-1\nwall.count=0\n";
        GameSession importedFour = read(four.getBytes(StandardCharsets.UTF_8));
        assertEquals(4, importedFour.game().playerCount());
        assertEquals(List.of("Blue", "سبز", "Red", "طلایی"), importedFour.config().playerNames());
        assertEquals(new GameState(4).snapshot(), importedFour.game().snapshot());
        roundTrip(importedFour);
    }

    public void testAtomicFileInterruptedWriteRecoversPriorCompleteMatch() throws Exception {
        GameSession original = session(4, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.MEDIUM, true);
        storage.saveRecovery(original);
        byte[] before = recoveryBytes();
        AtomicFile interrupted = new AtomicFile(recoveryFile());
        FileOutputStream partial = interrupted.startWrite();
        partial.write("version=2\ntruncated".getBytes(StandardCharsets.UTF_8));
        partial.flush();
        partial.close(); // Simulate a process dying before finishWrite/failWrite.
        SaveStorage reopened = new SaveStorage(context);
        assertSameSession(original, reopened.loadRecovery());
        assertTrue(Arrays.equals(before, recoveryBytes()));
        assertFalse(new File(directory, "resume.qsave.new").exists());
        assertFalse(new File(directory, "resume.qsave.bak").exists());
        roundTrip(session(2, GameConfig.Mode.BOT, BotPlayer.Difficulty.HARD, false));
    }

    public void testRejectedSessionAndDocumentImportsPreserveExistingRecovery() throws Exception {
        GameSession original = session(2, GameConfig.Mode.BOT, BotPlayer.Difficulty.HARD, true);
        storage.saveRecovery(original);
        byte[] before = recoveryBytes();
        try {
            storage.saveRecovery(null);
            fail("Null session must be rejected before AtomicFile.startWrite");
        } catch (NullPointerException expected) {
        }
        assertTrue(Arrays.equals(before, recoveryBytes()));
        expectIo(() -> read("version=999\n".getBytes(StandardCharsets.UTF_8)));
        expectIo(() -> read(new byte[] {(byte) 0xc3, 0x28}));
        String valid = SaveStore.encode(original);
        expectIo(() -> read((valid + "\nturn=9\n").getBytes(StandardCharsets.UTF_8)));
        assertSameSession(original, new SaveStorage(context).loadRecovery());
        assertTrue(Arrays.equals(before, recoveryBytes()));
        assertFalse(new File(directory, "resume.qsave.new").exists());
        assertFalse(new File(directory, "resume.qsave.bak").exists());
    }

    public void testBackupOnlyRecoveryIsFoundAndCorruptBackupStillRejected() throws Exception {
        GameSession original = session(4, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY, true);
        storage.saveRecovery(original);
        byte[] committed = recoveryBytes();
        File backup = new File(directory, "resume.qsave.bak");
        assertTrue(recoveryFile().renameTo(backup));
        assertFalse(recoveryFile().exists());
        assertTrue(
                "A committed AtomicFile backup must offer Continue even without its base",
                storage.hasRecovery());
        SaveStorage reopened = new SaveStorage(context);
        assertTrue(reopened.hasRecovery());
        assertSameSession(original, reopened.loadRecovery());
        assertTrue(Arrays.equals(committed, recoveryBytes()));
        assertFalse(backup.exists());
        assertTrue(recoveryFile().delete());
        byte[] corrupt = "corrupt backup candidate".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(backup)) {
            stream.write(corrupt);
        }
        assertTrue(
                "Discovery must not silently discard a candidate before validation",
                storage.hasRecovery());
        expectIo(storage::loadRecovery);
        assertTrue(
                "AtomicFile repair must preserve corrupt bytes rather than inventing a game",
                Arrays.equals(corrupt, recoveryBytes()));
    }

    public void testCorruptAndOversizedStreamsAreBoundedAndRejected() throws Exception {
        expectIo(() -> SaveStorage.read(null));
        expectIo(() -> read(new byte[0]));
        expectIo(() -> read(new byte[] {(byte) 0xff}));
        String current =
                SaveStore.encode(
                        session(2, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY, false));
        expectIo(
                () ->
                        read(
                                (current + "\n#" + "x".repeat(70 * 1024))
                                        .getBytes(StandardCharsets.UTF_8)));
        CountingStream huge = new CountingStream(10 * 1024 * 1024);
        expectIo(() -> SaveStorage.read(huge));
        assertTrue(
                "Document importer must stop after its 256KiB bound plus one read buffer",
                huge.consumed <= 256 * 1024 + 4096);
        assertTrue(
                "Oversized provider contents must not be fully consumed",
                huge.consumed < huge.length);
        GameSession original = session(4, GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY, false);
        storage.saveRecovery(original);
        try (FileOutputStream stream = new FileOutputStream(recoveryFile())) {
            stream.write("corrupt recovery".getBytes(StandardCharsets.UTF_8));
        }
        byte[] corrupt = recoveryBytes();
        expectIo(storage::loadRecovery);
        assertTrue(
                "Failed recovery loading must not rewrite the source",
                Arrays.equals(corrupt, recoveryBytes()));
    }

    private void roundTrip(GameSession expected) throws Exception {
        storage.saveRecovery(expected);
        assertTrue(storage.hasRecovery());
        assertSameSession(expected, new SaveStorage(context).loadRecovery());
        assertSameSession(expected, read(recoveryBytes()));
        assertSameSession(
                expected, SaveStore.decode(new String(recoveryBytes(), StandardCharsets.UTF_8)));
        assertFalse(new File(directory, "resume.qsave.new").exists());
        assertFalse(new File(directory, "resume.qsave.bak").exists());
    }

    private static GameSession session(
            int count, GameConfig.Mode mode, BotPlayer.Difficulty level, boolean persian) {
        List<String> names =
                count == 2
                        ? (persian
                                ? List.of("بازیکن 🎮", "ربات سبز")
                                : List.of("Player 🎮", "Green Robot"))
                        : (persian
                                ? List.of("آبی", "سبز", "قرمز", "طلایی")
                                : List.of("Blue", "Green", "Red", "Gold"));
        GameState game = new GameState(count);
        assertTrue(game.moveTo(7, 4));
        assertTrue(game.placeWall(3, 3, GameState.Orientation.HORIZONTAL));
        return new GameSession(game, new GameConfig(names, mode, level));
    }

    private static GameSession read(byte[] bytes) throws IOException {
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            return SaveStorage.read(stream);
        }
    }

    private File recoveryFile() {
        return new File(directory, "resume.qsave");
    }

    private byte[] recoveryBytes() throws IOException {
        try (InputStream stream = new FileInputStream(recoveryFile())) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }

    private static void assertSameSession(GameSession expected, GameSession actual) {
        assertEquals(expected.config(), actual.config());
        assertEquals(expected.game().snapshot(), actual.game().snapshot());
    }

    private static void expectIo(Operation operation) throws Exception {
        try {
            operation.run();
        } catch (IOException expected) {
            return;
        }
        fail("Expected bounded save-file rejection");
    }

    private interface Operation {
        void run() throws Exception;
    }

    private static final class CountingStream extends InputStream {
        final int length;
        int consumed;

        CountingStream(int length) {
            this.length = length;
        }

        @Override
        public int read() {
            if (consumed == length) {
                return -1;
            }
            consumed++;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int count) {
            if (consumed == length) {
                return -1;
            }
            int available = Math.min(count, length - consumed);
            Arrays.fill(buffer, offset, offset + available, (byte) 'x');
            consumed += available;
            return available;
        }
    }
}
