package com.quoridor.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;

import Online.OnlineState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual recreation, retained room callbacks, and per-window dialogs on the Android runtime. */
@SuppressWarnings("deprecation")
public final class LifecycleTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public LifecycleTest() {
        super(MainActivity.class);
    }

    public void testFixedGameViewportSurvivesBothOrientations() throws Exception {
        MainActivity fitted = getActivity();
        onUi(() -> invoke(fitted, "openLocal", new Class<?>[] {GameSession.class}, localSession()));
        Object owner = field(fitted, "local");
        for (int orientation :
                new int[] {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }) {
            onUi(() -> fitted.setRequestedOrientation(orientation));
            int expected =
                    orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            : android.content.res.Configuration.ORIENTATION_PORTRAIT;
            waitUntil(() -> fitted.getResources().getConfiguration().orientation == expected);
            getInstrumentation().waitForIdleSync();
            assertSame(owner, field(fitted, "local"));
            onUi(
                    () -> {
                        for (String tag :
                                new String[] {
                                    "game.board",
                                    "game.menu",
                                    "game.save",
                                    "game.rules",
                                    "game.move",
                                    "game.horizontal",
                                    "game.vertical",
                                    "game.undo",
                                    "game.history"
                                }) {
                            View view = fitted.getWindow().getDecorView().findViewWithTag(tag);
                            android.graphics.Rect visible = new android.graphics.Rect();
                            assertTrue(tag, view.getGlobalVisibleRect(visible));
                            assertEquals(tag + " height fits", view.getHeight(), visible.height());
                            assertEquals(tag + " width fits", view.getWidth(), visible.width());
                        }
                        assertTrue(
                                fitted.getWindow()
                                        .getDecorView()
                                        .findViewWithTag("game.horizontal")
                                        .performClick());
                    });
            onUi(
                    () -> {
                        View confirm =
                                fitted.getWindow().getDecorView().findViewWithTag("game.confirm");
                        android.graphics.Rect visible = new android.graphics.Rect();
                        assertTrue(confirm.getGlobalVisibleRect(visible));
                        assertEquals(confirm.getHeight(), visible.height());
                    });
        }
    }

    public void testLocalHistorySurvivesActualRecreation() throws Exception {
        MainActivity first = getActivity();
        AtomicReference<LocalMatch> owner = new AtomicReference<>();
        AtomicReference<GameState.Snapshot> position = new AtomicReference<>();
        onUi(
                () -> {
                    invoke(first, "openLocal", new Class<?>[] {GameSession.class}, localSession());
                    click(first, "game.board", 7, 4);
                    click(first, "game.board", 4, 1);
                    LocalMatch match = (LocalMatch) field(first, "local");
                    assertEquals(2, match.moves().size());
                    owner.set(match);
                    position.set(match.session().game().snapshot());
                });
        MainActivity next = recreate(first);
        onUi(
                () -> {
                    assertSame(owner.get(), field(next, "local"));
                    assertEquals(position.get(), owner.get().session().game().snapshot());
                    View undo = next.getWindow().getDecorView().findViewWithTag("game.undo");
                    assertTrue(undo.isEnabled());
                    assertTrue(undo.performClick());
                    assertEquals(1, owner.get().moves().size());
                    assertTrue(owner.get().canRedo());
                });
    }

    public void testEntryResponseDuringRecreationKeepsAndReplaysMembership() throws Exception {
        try (RoomFixture server = new RoomFixture(true)) {
            MainActivity first = getActivity();
            onUi(() -> enter(first, server.endpoint()));
            assertTrue("Fixture reserved the seat", server.reserved.await(5, TimeUnit.SECONDS));
            Object owner = field(first, "online");
            MainActivity next = recreate(first);
            onUi(() -> assertSame(owner, field(next, "online")));
            assertEquals("Configuration destruction did not leave", 0, server.leaves.get());
            server.release.countDown();
            waitUntil(() -> field(next, "room") != null);
            onUi(
                    () -> {
                        OnlineState room = (OnlineState) field(next, "room");
                        assertEquals(OnlineState.Status.WAITING, room.status());
                        assertEquals("ABCDEFGH", room.roomCode());
                        assertSame(owner, field(next, "online"));
                        assertTrue((Boolean) field(next, "gameScreen"));
                        assertFalse((Boolean) field(next, "onlineBusy"));
                        assertEquals(0, server.leaves.get());
                        invoke(next, "closeRoom", new Class<?>[0]);
                    });
            assertTrue(
                    "Explicit close releases only the retained membership",
                    server.left.await(5, TimeUnit.SECONDS));
        }
    }

    public void testDetachedTerminalFailureIsReplayedAfterRebinding() throws Exception {
        try (RoomFixture server = new RoomFixture(false)) {
            AtomicReference<OnlineMatch> owner = new AtomicReference<>();
            CountDownLatch entered = new CountDownLatch(1);
            AtomicInteger oldFailures = new AtomicInteger();
            onUi(
                    () -> {
                        OnlineMatch connection =
                                new OnlineMatch(
                                        server.endpoint(),
                                        new OnlineMatch.Listener() {
                                            @Override
                                            public void onState(OnlineState state) {
                                                entered.countDown();
                                            }

                                            @Override
                                            public void onFailure(
                                                    boolean entering, boolean terminal) {
                                                oldFailures.incrementAndGet();
                                            }
                                        });
                        owner.set(connection);
                        connection.enter(OnlineMatch.Entry.CREATE, 2, "Alice", "");
                    });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                onUi(() -> owner.get().setListener(null));
                server.expired = true;
                assertTrue(server.polledExpired.await(5, TimeUnit.SECONDS));
                waitUntil(() -> (Boolean) field(owner.get(), "hasFailure"));
                AtomicInteger states = new AtomicInteger();
                AtomicInteger terminalFailures = new AtomicInteger();
                onUi(
                        () ->
                                owner.get()
                                        .setListener(
                                                new OnlineMatch.Listener() {
                                                    @Override
                                                    public void onState(OnlineState state) {
                                                        states.incrementAndGet();
                                                    }

                                                    @Override
                                                    public void onFailure(
                                                            boolean entering, boolean terminal) {
                                                        assertFalse(entering);
                                                        if (terminal) {
                                                            terminalFailures.incrementAndGet();
                                                        }
                                                    }
                                                }));
                assertEquals(0, oldFailures.get());
                assertEquals(1, states.get());
                assertEquals(1, terminalFailures.get());
            } finally {
                onUi(() -> owner.get().close());
            }
            assertTrue(server.left.await(5, TimeUnit.SECONDS));
        }
    }

    public void testImportTicketIsInvalidatedByNewLocalMatchAndMenu() throws Exception {
        MainActivity activity = getActivity();
        onUi(
                () -> {
                    setField(activity, "importGeneration", 50L);
                    setField(activity, "pendingImportGeneration", 50L);
                    invoke(
                            activity,
                            "openLocal",
                            new Class<?>[] {GameSession.class},
                            localSession());
                    Object owner = field(activity, "local");
                    assertFalse(
                            (Boolean)
                                    invoke(
                                            activity,
                                            "currentImport",
                                            new Class<?>[] {long.class},
                                            50L));
                    Intent staleResult =
                            new Intent()
                                    .setData(
                                            Uri.parse(
                                                    "content://never.open.this.invalid/document"));
                    invoke(
                            activity,
                            "onActivityResult",
                            new Class<?>[] {int.class, int.class, Intent.class},
                            10,
                            Activity.RESULT_OK,
                            staleResult);
                    assertSame(owner, field(activity, "local"));
                    assertEquals(
                            -1L, ((Long) field(activity, "pendingImportGeneration")).longValue());
                    long ticket = (Long) field(activity, "importGeneration");
                    assertTrue(
                            (Boolean)
                                    invoke(
                                            activity,
                                            "currentImport",
                                            new Class<?>[] {long.class},
                                            ticket));
                    invoke(activity, "goMenu", new Class<?>[0]);
                    assertFalse(
                            (Boolean)
                                    invoke(
                                            activity,
                                            "currentImport",
                                            new Class<?>[] {long.class},
                                            ticket));
                    assertNull(field(activity, "local"));
                });
    }

    public void testNativeDialogThemeAndPersianArePerActivity() throws Exception {
        MainActivity activity = getActivity();
        onUi(
                () -> {
                    String originalLocale =
                            activity.getResources()
                                    .getConfiguration()
                                    .getLocales()
                                    .get(0)
                                    .getLanguage();
                    for (boolean persian : new boolean[] {true, false}) {
                        for (boolean dark : new boolean[] {true, false}) {
                            setField(activity, "dark", dark);
                            MobileText words = new MobileText(persian);
                            setField(activity, "words", words);
                            AlertDialog.Builder builder =
                                    (AlertDialog.Builder)
                                            invoke(activity, "dialogBuilder", new Class<?>[0]);
                            AlertDialog dialog =
                                    builder.setMessage(persian ? "آزمایش" : "Test")
                                            .setPositiveButton(words.text(MobileText.Key.OK), null)
                                            .create();
                            try {
                                dialog.show();
                                assertEquals(
                                        persian ? "fa" : "en",
                                        dialog.getContext()
                                                .getResources()
                                                .getConfiguration()
                                                .getLocales()
                                                .get(0)
                                                .getLanguage());
                                TypedValue background = new TypedValue();
                                assertTrue(
                                        dialog.getContext()
                                                .getTheme()
                                                .resolveAttribute(
                                                        android.R.attr.colorBackground,
                                                        background,
                                                        true));
                                assertTrue(
                                        dark
                                                ? Color.red(background.data) < 120
                                                : Color.red(background.data) > 180);
                                assertEquals(
                                        words.text(MobileText.Key.OK),
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                                .getText()
                                                .toString());
                            } finally {
                                dialog.dismiss();
                            }
                        }
                    }
                    assertEquals(
                            originalLocale,
                            activity.getResources()
                                    .getConfiguration()
                                    .getLocales()
                                    .get(0)
                                    .getLanguage());
                });
    }

    private MainActivity recreate(MainActivity activity) throws Exception {
        Instrumentation.ActivityMonitor monitor =
                getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        try {
            onUi(activity::recreate);
            Activity replacement = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Replacement Activity appeared", replacement);
            getInstrumentation().waitForIdleSync();
            setActivity(replacement);
            return (MainActivity) replacement;
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private static void enter(MainActivity activity, String endpoint) throws Exception {
        invoke(activity, "goMenu", new Class<?>[0]);
        ((EditText) activity.getWindow().getDecorView().findViewWithTag("menu.online.server"))
                .setText(endpoint);
        ((EditText) activity.getWindow().getDecorView().findViewWithTag("menu.online.name"))
                .setText("Alice");
        assertTrue(
                activity.getWindow()
                        .getDecorView()
                        .findViewWithTag("menu.online.create")
                        .performClick());
    }

    private static void click(MainActivity activity, String tag, int row, int col) {
        QuoridorBoardView board = activity.getWindow().getDecorView().findViewWithTag(tag);
        assertTrue(
                board.getAccessibilityNodeProvider()
                        .performAction(
                                row * 9 + col,
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,
                                null));
    }

    private static GameSession localSession() {
        return new GameSession(
                new GameState(4),
                new GameConfig(
                        List.of("Blue", "Green", "Red", "Gold"),
                        GameConfig.Mode.LOCAL,
                        BotPlayer.Difficulty.EASY));
    }

    private interface Check {
        boolean evaluate() throws Exception;
    }

    private interface UiCheck {
        void run() throws Exception;
    }

    private void waitUntil(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            onUi(() -> done.set(check.evaluate()));
            if (done.get()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Asynchronous lifecycle transition timed out");
    }

    private void onUi(UiCheck check) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                check.run();
                            } catch (Throwable problem) {
                                failure.set(problem);
                            }
                        });
        Throwable problem = failure.get();
        if (problem instanceof Error) {
            throw (Error) problem;
        }
        if (problem instanceof Exception) {
            throw (Exception) problem;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... values)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(target, values);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Error) {
                throw (Error) failure.getCause();
            }
            if (failure.getCause() instanceof Exception) {
                throw (Exception) failure.getCause();
            }
            throw failure;
        }
    }

    /**
     * One owned socket server provides real bounded HTTP replies without unavailable HttpServer.
     */
    private static final class RoomFixture implements AutoCloseable {
        final CountDownLatch reserved = new CountDownLatch(1);
        final CountDownLatch release;
        final CountDownLatch left = new CountDownLatch(1);
        final CountDownLatch polledExpired = new CountDownLatch(1);
        final AtomicInteger leaves = new AtomicInteger();
        final ServerSocket socket;
        final Thread server;
        volatile boolean closed;
        volatile boolean expired;
        volatile Exception problem;

        RoomFixture(boolean delayed) throws Exception {
            socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(500);
            release = new CountDownLatch(delayed ? 1 : 0);
            server = new Thread(this::serve, "quoridor-lifecycle-fixture");
            server.setDaemon(true);
            server.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        private void serve() {
            while (!closed) {
                try (Socket request = socket.accept()) {
                    request.setSoTimeout(3000);
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            request.getInputStream(), StandardCharsets.US_ASCII));
                    String first = reader.readLine();
                    int contentLength = 0;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                            contentLength =
                                    Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                        }
                    }
                    for (int index = 0; index < contentLength; index++) {
                        reader.read();
                    }
                    if (first == null) {
                        continue;
                    }
                    boolean entering = first.contains("/api/create");
                    boolean leaving = first.contains("/api/leave");
                    if (entering) {
                        reserved.countDown();
                        release.await(7, TimeUnit.SECONDS);
                    }
                    if (leaving) {
                        leaves.incrementAndGet();
                        left.countDown();
                    }
                    boolean rejected = expired && !entering && !leaving;
                    String body =
                            rejected
                                    ? "error=NOT_FOUND&message=Gone"
                                    : "protocol=1&room=ABCDEFGH&status="
                                            + (leaving ? "CLOSED" : "WAITING")
                                            + "&player=0&count=2&revision="
                                            + (leaving ? "2" : "1")
                                            + "&names=1&name0=Alice&moves=0&reason="
                                            + (leaving ? "LEFT" : "")
                                            + (entering
                                                    ? "&token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                                                    : "");
                    byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
                    OutputStream output = request.getOutputStream();
                    output.write(
                            ((rejected ? "HTTP/1.1 410 Gone" : "HTTP/1.1 200 OK")
                                            + "\r\nContent-Type: application/x-www-form-urlencoded; charset=UTF-8\r\nContent-Length: "
                                            + bytes.length
                                            + "\r\nConnection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                    output.write(bytes);
                    output.flush();
                    if (rejected) {
                        polledExpired.countDown();
                    }
                } catch (SocketTimeoutException ignored) {
                    // Periodically observe fixture ownership shutdown.
                } catch (Exception failure) {
                    if (!closed) {
                        problem = failure;
                        closed = true;
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            release.countDown();
            socket.close();
            try {
                server.join(3000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Fixture shutdown was interrupted", interrupted);
            }
            assertFalse("Owned fixture thread stopped", server.isAlive());
            if (problem != null) {
                throw new IOException("Fixture HTTP failed", problem);
            }
        }
    }
}
