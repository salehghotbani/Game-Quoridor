package com.quoridor.mobile;

import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;

import Board.GameSession;
import Board.GameState;
import Board.SaveStore;

import Online.OnlineClient;
import Online.OnlineException;
import Online.OnlineState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android transport against the test runner's dedicated JVM room server. */
@SuppressWarnings("deprecation")
public final class OnlineInteropTest extends InstrumentationTestCase {
    private URI endpoint;
    private final List<OnlineClient> connections = new ArrayList<>();
    private final List<OnlineMatch> controllers = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertTrue(
                "Use the platform InstrumentationTestRunner",
                getInstrumentation() instanceof InstrumentationTestRunner);
        Bundle arguments = ((InstrumentationTestRunner) getInstrumentation()).getArguments();
        String server = arguments == null ? null : arguments.getString("server");
        assertTrue(
                "Online tests require an isolated room server: am instrument -e server http://10.0.2.2:PORT",
                server != null && !server.trim().isEmpty());
        endpoint = URI.create(server.replaceAll("/+$", "") + "/");
        HttpURLConnection health =
                (HttpURLConnection) endpoint.resolve("health").toURL().openConnection();
        try {
            health.setConnectTimeout(3000);
            health.setReadTimeout(3000);
            health.setInstanceFollowRedirects(false);
            assertEquals(
                    "The supplied isolated JVM room server must be healthy",
                    200,
                    health.getResponseCode());
        } finally {
            health.disconnect();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            getInstrumentation().runOnMainSync(() -> controllers.forEach(OnlineMatch::close));
            for (OnlineClient connection : connections) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            super.tearDown();
        }
    }

    public void testPrivateTwoPlayerUnicodeMovesWallsAndDesktopSave() throws Exception {
        OnlineClient blue = client();
        OnlineClient green = client();
        OnlineState room = blue.createPrivate(2, "آبی Android 🎮");
        assertEquals(OnlineState.Status.WAITING, room.status());
        assertNull(room.session());
        OnlineState joined = green.joinPrivate(room.roomCode(), "Green Desktop");
        assertEquals(1, joined.playerIndex());
        assertEquals(OnlineState.Status.PLAYING, joined.status());
        OnlineState initial = blue.state();
        assertEquals(List.of("آبی Android 🎮", "Green Desktop"), initial.playerNames());
        expect("NOT_YOUR_TURN", () -> green.move(1, 4, initial.revision()));
        expect("ILLEGAL_MOVE", () -> blue.move(0, 0, initial.revision()));
        assertEquals(initial.revision(), blue.state().revision());
        OnlineState moved = blue.move(7, 4, initial.revision());
        assertEquals(initial.revision() + 1, moved.revision());
        assertEquals(new GameState.Cell(7, 4), moved.session().game().playerPosition(0));
        expect("STALE_REVISION", () -> green.move(1, 4, initial.revision()));
        OnlineState wall = green.wall(3, 3, GameState.Orientation.HORIZONTAL, moved.revision());
        assertEquals(List.of("0 M 7 4", "1 W 3 3 HORIZONTAL"), wall.moves());
        assertEquals(9, wall.session().game().wallsRemaining(1));
        assertSameSession(wall.session(), blue.state().session());
        assertPortable(wall.session(), "1");
        GameState detached = wall.session().game();
        assertTrue(detached.moveTo(6, 4));
        assertSameSession(wall.session(), blue.state().session());
        green.leave();
        OnlineState closed = blue.state();
        assertEquals(OnlineState.Status.CLOSED, closed.status());
        assertEquals("PLAYER_LEFT", closed.reason());
        assertSameSession(wall.session(), closed.session());
        expect("ROOM_CLOSED", () -> blue.move(6, 4, closed.revision()));
    }

    public void testPrivateFourPlayerClockwiseAuthorityAndDesktopSave() throws Exception {
        List<OnlineClient> players = List.of(client(), client(), client(), client());
        List<String> names = List.of("آبی", "Green", "قرمز", "Gold 🎮");
        OnlineState created = players.get(0).createPrivate(4, names.get(0));
        for (int index = 1; index < 4; index++) {
            OnlineState joined =
                    players.get(index).joinPrivate(created.roomCode(), names.get(index));
            assertEquals(index, joined.playerIndex());
            assertEquals(
                    index == 3 ? OnlineState.Status.PLAYING : OnlineState.Status.WAITING,
                    joined.status());
        }
        OnlineState state = players.get(0).state();
        assertEquals(names, state.playerNames());
        assertEquals(4, state.session().game().playerCount());
        for (int index = 0; index < 4; index++) {
            assertEquals(5, state.session().game().wallsRemaining(index));
        }
        assertEquals(0, state.session().game().currentPlayer());
        long revision = state.revision();
        state = players.get(0).move(7, 4, revision);
        assertEquals(revision + 1, state.revision());
        assertEquals(2, state.session().game().currentPlayer());
        revision = state.revision();
        state = players.get(2).wall(3, 3, GameState.Orientation.HORIZONTAL, revision);
        assertEquals(revision + 1, state.revision());
        assertEquals(1, state.session().game().currentPlayer());
        state = players.get(1).move(1, 4, state.revision());
        assertEquals(3, state.session().game().currentPlayer());
        state = players.get(3).wall(5, 5, GameState.Orientation.VERTICAL, state.revision());
        assertEquals(0, state.session().game().currentPlayer());
        assertEquals(4, state.session().game().wallsRemaining(2));
        assertEquals(4, state.session().game().wallsRemaining(3));
        assertEquals(
                List.of("0 M 7 4", "2 W 3 3 HORIZONTAL", "1 M 1 4", "3 W 5 5 VERTICAL"),
                state.moves());
        for (int index = 0; index < 4; index++) {
            OnlineState peer = players.get(index).state();
            assertEquals(index, peer.playerIndex());
            assertSameSession(state.session(), peer.session());
        }
        assertPortable(state.session(), "2");
        players.get(2).leave();
        assertEquals("PLAYER_LEFT", players.get(0).state().reason());
    }

    public void testRandomQueuesSeparateCountsPrivateRoomsAndCancellation() throws Exception {
        OnlineClient privateOwner = client();
        OnlineClient twoA = client();
        OnlineClient fourA = client();
        OnlineClient twoB = client();
        OnlineState privateRoom = privateOwner.createPrivate(2, "Private");
        OnlineState two = twoA.findMatch(2, "Two A");
        OnlineState four = fourA.findMatch(4, "چهار A");
        assertFalse(two.roomCode().equals(privateRoom.roomCode()));
        assertFalse(two.roomCode().equals(four.roomCode()));
        expect("ROOM_NOT_FOUND", () -> twoB.joinPrivate(two.roomCode(), "Code probe"));
        OnlineState matched = twoB.findMatch(2, "Two B");
        assertEquals(two.roomCode(), matched.roomCode());
        assertEquals(OnlineState.Status.PLAYING, matched.status());
        fourA.leave();
        OnlineClient replacement = client();
        OnlineState fresh = replacement.findMatch(4, "چهار تازه");
        assertFalse(
                "Cancel must remove an empty waiting queue room",
                four.roomCode().equals(fresh.roomCode()));
        for (int index = 1; index < 4; index++) {
            OnlineState joined = client().findMatch(4, "Player " + index);
            assertEquals(fresh.roomCode(), joined.roomCode());
        }
        assertEquals(OnlineState.Status.PLAYING, replacement.state().status());
        assertEquals(4, replacement.state().session().game().playerCount());
        assertEquals(OnlineState.Status.WAITING, privateOwner.state().status());
        assertEquals(
                OnlineState.Status.PLAYING,
                fourA.joinPrivate(privateRoom.roomCode(), "Friend").status());
    }

    public void testOnlineMatchCallbacksActionsAndCloseAreOwned() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch playing = new CountDownLatch(1);
        CountDownLatch moved = new CountDownLatch(1);
        AtomicReference<OnlineState> latest = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        OnlineMatch controller =
                new OnlineMatch(
                        endpoint.toString(),
                        new OnlineMatch.Listener() {
                            @Override
                            public void onState(OnlineState state) {
                                if (Looper.myLooper() != Looper.getMainLooper()) {
                                    failure.set(
                                            new AssertionError(
                                                    "Online callback must run on the main looper"));
                                }
                                latest.set(state);
                                callbacks.incrementAndGet();
                                if (state.status() == OnlineState.Status.WAITING) {
                                    waiting.countDown();
                                }
                                if (state.status() == OnlineState.Status.PLAYING) {
                                    playing.countDown();
                                }
                                if (state.moves().size() == 1) {
                                    moved.countDown();
                                }
                            }

                            @Override
                            public void onFailure(boolean entering, boolean terminal) {
                                failure.set(
                                        new AssertionError(
                                                "Unexpected controller failure: entering="
                                                        + entering
                                                        + ", terminal="
                                                        + terminal));
                            }
                        });
        controllers.add(controller);
        getInstrumentation()
                .runOnMainSync(
                        () -> controller.enter(OnlineMatch.Entry.CREATE, 2, "Controller", ""));
        assertTrue(
                "Controller must publish its own waiting room",
                waiting.await(10, TimeUnit.SECONDS));
        OnlineClient unrelated = client();
        OnlineState otherRoom = unrelated.createPrivate(2, "Unrelated");
        OnlineClient peer = client();
        peer.joinPrivate(latest.get().roomCode(), "Peer");
        assertTrue("Polling must observe the joined peer", playing.await(10, TimeUnit.SECONDS));
        long revision = latest.get().revision();
        getInstrumentation().runOnMainSync(() -> controller.move(7, 4, revision));
        assertTrue(
                "Background controller action must return authoritative state",
                moved.await(10, TimeUnit.SECONDS));
        assertEquals(new GameState.Cell(7, 4), latest.get().session().game().playerPosition(0));
        getInstrumentation().runOnMainSync(controller::close);
        int closedCallbacks = callbacks.get();
        OnlineState closed = awaitClosed(peer);
        assertEquals("PLAYER_LEFT", closed.reason());
        assertEquals(OnlineState.Status.WAITING, unrelated.state().status());
        assertEquals(otherRoom.roomCode(), unrelated.state().roomCode());
        getInstrumentation()
                .runOnMainSync(
                        () -> {
                            controller.setForeground(true);
                            controller.move(6, 4, closed.revision());
                        });
        SystemClock.sleep(1250);
        getInstrumentation().waitForIdleSync();
        assertEquals(
                "Closed controller must fence every later callback",
                closedCallbacks,
                callbacks.get());
        assertNull(failure.get());
    }

    private OnlineClient client() {
        OnlineClient connection = new OnlineClient(endpoint);
        connections.add(connection);
        return connection;
    }

    private static OnlineState awaitClosed(OnlineClient peer) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        OnlineState state;
        do {
            state = peer.state();
            if (state.status() == OnlineState.Status.CLOSED) {
                return state;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Owned controller close did not release its server membership");
        return state;
    }

    private static void assertPortable(GameSession expected, String version) throws Exception {
        String text = SaveStore.encode(expected);
        assertTrue(text.contains("version=" + version));
        try (ByteArrayInputStream stream =
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            assertSameSession(expected, SaveStorage.read(stream));
        }
        assertSameSession(expected, SaveStore.decode(text));
    }

    private static void assertSameSession(GameSession expected, GameSession actual) {
        assertEquals(expected.config(), actual.config());
        assertEquals(expected.game().snapshot(), actual.game().snapshot());
    }

    private static void expect(String code, Operation operation) throws Exception {
        try {
            operation.run();
        } catch (OnlineException expected) {
            assertEquals(code, expected.code());
            return;
        }
        fail("Expected protocol rejection " + code);
    }

    private interface Operation {
        void run() throws Exception;
    }
}
