package Board;

import Online.OnlineClient;
import Online.OnlineServer;
import Online.OnlineState;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

/** Uses owned loopback servers to verify request ownership, polling and event-thread callbacks. */
public final class OnlineMatchControllerTest {
    private static int checks;

    private OnlineMatchControllerTest() {}

    public static void main(String[] args) throws Exception {
        realMatchAndOwnership();
        matchmakingAndFourPlayerJoin();
        reconnectAndTerminalFailures();
        cancelPendingRequests();
        cancelReservedMembership();
        initialFailures();
        System.out.println("Online controller tests passed (" + checks + " checks).");
    }

    private static void realMatchAndOwnership() throws Exception {
        try (OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.start();
            URI endpoint = endpoint(server.port());
            Events events = new Events();
            OnlineMatchController controller = edt(() -> new OnlineMatchController(events));
            try (OnlineClient opponent = new OnlineClient(endpoint)) {
                edt(
                        () ->
                                controller.start(
                                        endpoint,
                                        OnlineMatchController.Action.CREATE,
                                        2,
                                        "Blue",
                                        ""));
                await(() -> events.latest() != null, "Controller creates a waiting room");
                OnlineState waiting = events.latest();
                check(waiting.status() == OnlineState.Status.WAITING, "Create response is waiting");
                check(
                        controller.active() && controller.connected(),
                        "Waiting membership is connected");
                await(() -> !controller.busy(), "Initial request releases busy state");
                OnlineState joined = opponent.joinPrivate(waiting.roomCode(), "Green");
                await(
                        () -> events.latest().status() == OnlineState.Status.PLAYING,
                        "Polling starts the match");
                await(() -> !controller.busy(), "Playing response releases busy state");
                long openingRevision = events.latest().revision();
                edt(
                        () -> {
                            controller.move(7, 4, openingRevision);
                            controller.move(7, 4, openingRevision);
                        });
                await(
                        () -> events.latest().revision() == openingRevision + 1,
                        "One submitted move is applied once");
                check(
                        events.latest().moves().size() == 1,
                        "Busy controller does not duplicate a user move");
                OnlineState opponentTurn = opponent.state();
                check(
                        opponentTurn.session().game().currentPlayer() == 1,
                        "Server owns the turn order");
                OnlineState reply = opponent.move(1, 4, opponentTurn.revision());
                await(
                        () -> events.latest().revision() == reply.revision(),
                        "Polling sees the opponent reply");
                await(() -> !controller.busy(), "Opponent update releases busy state");
                edt(
                        () ->
                                controller.wall(
                                        3, 3, GameState.Orientation.HORIZONTAL, reply.revision()));
                await(
                        () -> events.latest().revision() == reply.revision() + 1,
                        "Wall requests use the expected revision");
                check(
                        events.latest().session().game().wallsRemaining(0) == 9,
                        "Server records wall stock");
                await(() -> !controller.busy(), "Wall response releases busy state");
                edt(() -> controller.move(6, 4, openingRevision));
                await(
                        () -> events.failures.contains("STALE_REVISION"),
                        "Stale requests report a stable local code");
                check(
                        controller.active() && controller.connected(),
                        "Rejected move retains membership");
                edt(controller::leave);
                check(
                        !controller.active() && !controller.connected() && !controller.busy(),
                        "Leave clears flags immediately");
                await(() -> roomClosed(opponent), "Asynchronous leave closes only its owned room");
                check(joined.playerIndex() == 1, "Opponent membership has its own identity");
            } finally {
                edt(controller::close);
            }
            check(events.onEventThread.get(), "Every controller callback runs on the event thread");

            Events replacementEvents = new Events();
            OnlineMatchController replacement =
                    edt(() -> new OnlineMatchController(replacementEvents));
            try (OnlineClient observer = new OnlineClient(endpoint)) {
                edt(
                        () ->
                                replacement.start(
                                        endpoint,
                                        OnlineMatchController.Action.CREATE,
                                        4,
                                        "Old host",
                                        ""));
                await(() -> replacementEvents.latest() != null, "Old room is created");
                String oldCode = replacementEvents.latest().roomCode();
                observer.joinPrivate(oldCode, "Old guest");
                edt(
                        () ->
                                replacement.start(
                                        endpoint,
                                        OnlineMatchController.Action.CREATE,
                                        2,
                                        "New host",
                                        ""));
                await(
                        () -> !replacementEvents.latest().roomCode().equals(oldCode),
                        "A new generation creates a new room");
                String newCode = replacementEvents.latest().roomCode();
                await(
                        () -> observerRemainsAlone(observer),
                        "Replaced waiting membership is released in the background");
                Thread.sleep(1200);
                check(
                        replacement.active() && replacement.connected(),
                        "Old cleanup cannot leave the new membership");
                check(
                        replacementEvents.latest().roomCode().equals(newCode),
                        "Late old callbacks cannot replace the new room");
            } finally {
                edt(replacement::close);
            }
        }
    }

    private static void reconnectAndTerminalFailures() throws Exception {
        try (OnlineServer backend = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            backend.start();
            try (FaultProxy proxy = new FaultProxy(endpoint(backend.port()))) {
                Events events = new Events();
                OnlineMatchController controller = edt(() -> new OnlineMatchController(events));
                try {
                    edt(
                            () ->
                                    controller.start(
                                            proxy.endpoint(),
                                            OnlineMatchController.Action.CREATE,
                                            2,
                                            "Host",
                                            ""));
                    await(
                            () -> events.latest() != null && !controller.busy(),
                            "Proxy membership is created");
                    String code = events.latest().roomCode();
                    proxy.stateFailure = 500;
                    await(
                            () -> events.reconnecting.contains(true),
                            "Temporary poll failure reports reconnecting");
                    check(
                            controller.active() && !controller.connected(),
                            "Temporary poll failure preserves membership");
                    check(
                            events.failures.isEmpty(),
                            "Transient polling does not repeatedly raise action errors");
                    proxy.stateFailure = 0;
                    await(
                            () -> controller.connected() && events.reconnecting.contains(false),
                            "Polling reconnects automatically");
                    check(
                            events.latest().roomCode().equals(code),
                            "Reconnection retains the original room");

                    for (int status : List.of(401, 404, 410)) {
                        int oldFailures = events.failures.size();
                        proxy.stateFailure = status;
                        await(
                                () -> events.failures.size() > oldFailures,
                                "Terminal poll error is reported");
                        check(
                                !controller.active() && !controller.connected(),
                                "Terminal poll error ends membership");
                        await(() -> !controller.busy(), "Terminal poll error releases busy state");
                        int pollsAfterFailure = proxy.stateRequests.get();
                        Thread.sleep(1200);
                        check(
                                proxy.stateRequests.get() == pollsAfterFailure,
                                "Terminal poll error stops polling");
                        proxy.stateFailure = 0;
                        int statesBeforeRestart = events.states.size();
                        edt(
                                () ->
                                        controller.start(
                                                proxy.endpoint(),
                                                OnlineMatchController.Action.CREATE,
                                                2,
                                                "Again",
                                                ""));
                        await(
                                () ->
                                        events.states.size() > statesBeforeRestart
                                                && controller.connected(),
                                "Controller can start after terminal failure");
                    }
                } finally {
                    edt(controller::close);
                }
                check(
                        events.onEventThread.get(),
                        "Reconnection callbacks remain on the event thread");
            }
        }
    }

    private static void matchmakingAndFourPlayerJoin() throws Exception {
        try (OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.start();
            URI endpoint = endpoint(server.port());
            Events firstEvents = new Events();
            Events secondEvents = new Events();
            OnlineMatchController first = edt(() -> new OnlineMatchController(firstEvents));
            OnlineMatchController second = edt(() -> new OnlineMatchController(secondEvents));
            try {
                edt(
                        () ->
                                first.start(
                                        endpoint,
                                        OnlineMatchController.Action.MATCH,
                                        2,
                                        "First",
                                        ""));
                await(
                        () -> firstEvents.latest() != null,
                        "First matchmaking player enters the queue");
                edt(
                        () ->
                                second.start(
                                        endpoint,
                                        OnlineMatchController.Action.MATCH,
                                        2,
                                        "Second",
                                        ""));
                await(
                        () ->
                                secondEvents.latest() != null
                                        && secondEvents.latest().status()
                                                == OnlineState.Status.PLAYING,
                        "Second matchmaking player completes the room");
                await(
                        () -> firstEvents.latest().status() == OnlineState.Status.PLAYING,
                        "First matchmaking player sees the match start");
                check(
                        firstEvents.latest().roomCode().equals(secondEvents.latest().roomCode()),
                        "Matchmaking joins both controllers into one room");
                check(
                        firstEvents.latest().playerIndex() == 0
                                && secondEvents.latest().playerIndex() == 1,
                        "Matchmaking retains distinct player identities");
            } finally {
                edt(first::close);
                edt(second::close);
            }

            Events joinedEvents = new Events();
            OnlineMatchController joined = edt(() -> new OnlineMatchController(joinedEvents));
            try (OnlineClient host = new OnlineClient(endpoint);
                    OnlineClient red = new OnlineClient(endpoint);
                    OnlineClient gold = new OnlineClient(endpoint)) {
                String code = host.createPrivate(4, "Blue").roomCode();
                edt(
                        () ->
                                joined.start(
                                        endpoint,
                                        OnlineMatchController.Action.JOIN,
                                        2,
                                        "Green",
                                        code));
                await(
                        () -> joinedEvents.latest() != null,
                        "Join action enters an existing private room");
                check(
                        joinedEvents.latest().playerCount() == 4,
                        "Joined room uses the server's player count");
                red.joinPrivate(code, "Red");
                gold.joinPrivate(code, "Gold");
                await(
                        () -> joinedEvents.latest().status() == OnlineState.Status.PLAYING,
                        "Four-player joined room starts after all seats fill");
                check(
                        joinedEvents.latest().session().game().playerCount() == 4,
                        "Controller transports the complete four-player state");
                check(
                        joinedEvents
                                .latest()
                                .session()
                                .config()
                                .playerNames()
                                .equals(List.of("Blue", "Green", "Red", "Gold")),
                        "Controller preserves every participant name");
            } finally {
                edt(joined::close);
            }
        }
    }

    private static void cancelPendingRequests() throws Exception {
        try (OnlineServer backend = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            backend.start();
            try (FaultProxy proxy = new FaultProxy(endpoint(backend.port()))) {
                proxy.blockCreate = true;
                Events events = new Events();
                OnlineMatchController controller = edt(() -> new OnlineMatchController(events));
                try {
                    edt(
                            () ->
                                    controller.start(
                                            proxy.endpoint(),
                                            OnlineMatchController.Action.CREATE,
                                            2,
                                            "Delayed",
                                            ""));
                    check(
                            proxy.createEntered.await(5, TimeUnit.SECONDS),
                            "Initial request reaches the delayed server");
                    long before = System.nanoTime();
                    edt(controller::leave);
                    check(
                            Duration.ofNanos(System.nanoTime() - before).toMillis() < 500,
                            "Cancelling initial IO does not block the event thread");
                    check(
                            !controller.active() && !controller.busy(),
                            "Pending cancellation clears membership flags");
                    proxy.releaseCreate.countDown();
                    Thread.sleep(350);
                    check(
                            events.states.isEmpty() && events.failures.isEmpty(),
                            "Cancelled initial response cannot update the interface");
                    check(
                            proxy.createRequests.get() == 1,
                            "Cancellation never retries a create POST");
                    proxy.blockCreate = false;
                    edt(
                            () ->
                                    controller.start(
                                            proxy.endpoint(),
                                            OnlineMatchController.Action.CREATE,
                                            2,
                                            "Current",
                                            ""));
                    await(
                            () -> events.latest() != null && !controller.busy(),
                            "A new generation can start after cancellation");
                    proxy.blockState = true;
                    check(
                            proxy.stateEntered.await(5, TimeUnit.SECONDS),
                            "Polling reaches the delayed server");
                    int statesBeforeLeave = events.states.size();
                    before = System.nanoTime();
                    edt(controller::close);
                    check(
                            Duration.ofNanos(System.nanoTime() - before).toMillis() < 500,
                            "Closing pending polling does not block the event thread");
                    proxy.releaseState.countDown();
                    Thread.sleep(350);
                    check(
                            events.states.size() == statesBeforeLeave,
                            "Closed controller ignores a late poll response");
                    check(
                            !controller.active() && !controller.connected() && !controller.busy(),
                            "Close clears every controller flag");
                } finally {
                    proxy.releaseCreate.countDown();
                    proxy.releaseState.countDown();
                    edt(controller::close);
                }
            }
        }
    }

    private static void cancelReservedMembership() throws Exception {
        try (OnlineServer backend = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            backend.start();
            try (FaultProxy proxy = new FaultProxy(endpoint(backend.port()));
                    OnlineClient peer = new OnlineClient(endpoint(backend.port()))) {
                proxy.delayCreateResponse = true;
                Events events = new Events();
                OnlineMatchController controller = edt(() -> new OnlineMatchController(events));
                try {
                    edt(
                            () ->
                                    controller.start(
                                            proxy.endpoint(),
                                            OnlineMatchController.Action.CREATE,
                                            2,
                                            "Reserved host",
                                            ""));
                    check(
                            proxy.createForwarded.await(5, TimeUnit.SECONDS),
                            "Backend reserves membership before the delayed entry response");
                    OnlineState joined = peer.joinPrivate(proxy.createdRoom.get(), "Peer");
                    check(
                            joined.status() == OnlineState.Status.PLAYING,
                            "A peer joins the reserved membership");
                    long before = System.nanoTime();
                    edt(controller::leave);
                    check(
                            Duration.ofNanos(System.nanoTime() - before).toMillis() < 500,
                            "Reserved-entry cancellation still returns immediately");
                    proxy.delayCreateResponse = false;
                    edt(
                            () ->
                                    controller.start(
                                            proxy.endpoint(),
                                            OnlineMatchController.Action.CREATE,
                                            2,
                                            "New host",
                                            ""));
                    await(
                            () -> events.latest() != null && controller.connected(),
                            "A new room starts while old entry is awaiting its credential");
                    String currentCode = events.latest().roomCode();
                    proxy.releaseCreateResponse.countDown();
                    await(
                            () -> roomClosed(peer),
                            "Cancelled entry receives its credential and releases the reserved room");
                    check(
                            events.states.stream()
                                    .allMatch(state -> state.roomCode().equals(currentCode)),
                            "Reserved old entry never writes a late interface state");
                    check(
                            controller.active() && controller.connected(),
                            "Reserved old-entry cleanup cannot leave the new room");
                } finally {
                    proxy.releaseCreateResponse.countDown();
                    edt(controller::close);
                }
            }
        }
    }

    private static void initialFailures() throws Exception {
        Events events = new Events();
        OnlineMatchController controller = edt(() -> new OnlineMatchController(events));
        try {
            edt(
                    () ->
                            controller.start(
                                    URI.create("ftp://localhost"),
                                    OnlineMatchController.Action.CREATE,
                                    2,
                                    "Host",
                                    ""));
            await(
                    () -> events.failures.contains("INVALID_SERVER"),
                    "Invalid endpoint is reported without blocking setup");
            await(() -> !controller.busy(), "Initial failure releases busy state");
            check(
                    !controller.active() && !controller.connected(),
                    "Initial failure does not retain a client");
        } finally {
            edt(controller::close);
        }
    }

    private static boolean roomClosed(OnlineClient client) {
        try {
            return client.state().status() == OnlineState.Status.CLOSED;
        } catch (IOException unavailable) {
            return false;
        }
    }

    private static boolean observerRemainsAlone(OnlineClient client) {
        try {
            return client.state().playerNames().equals(List.of("Old guest"));
        } catch (IOException unavailable) {
            return false;
        }
    }

    private static URI endpoint(int port) {
        return URI.create("http://127.0.0.1:" + port + "/");
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void edt(Runnable action) throws Exception {
        edt(
                () -> {
                    action.run();
                    return null;
                });
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        check(condition.getAsBoolean(), message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Events implements OnlineMatchController.Listener {
        final List<OnlineState> states = new CopyOnWriteArrayList<>();
        final List<String> failures = new CopyOnWriteArrayList<>();
        final List<Boolean> reconnecting = new CopyOnWriteArrayList<>();
        final AtomicBoolean onEventThread = new AtomicBoolean(true);

        @Override
        public void onState(OnlineState state) {
            recordThread();
            states.add(state);
        }

        @Override
        public void onConnectionProblem(boolean problem) {
            recordThread();
            reconnecting.add(problem);
        }

        @Override
        public void onFailure(String code) {
            recordThread();
            failures.add(code);
        }

        @Override
        public void onBusy(boolean busy) {
            recordThread();
        }

        OnlineState latest() {
            return states.isEmpty() ? null : states.get(states.size() - 1);
        }

        private void recordThread() {
            if (!SwingUtilities.isEventDispatchThread()) {
                onEventThread.set(false);
            }
        }
    }

    private static final class FaultProxy implements AutoCloseable {
        final CountDownLatch createEntered = new CountDownLatch(1);
        final CountDownLatch releaseCreate = new CountDownLatch(1);
        final CountDownLatch stateEntered = new CountDownLatch(1);
        final CountDownLatch releaseState = new CountDownLatch(1);
        final CountDownLatch createForwarded = new CountDownLatch(1);
        final CountDownLatch releaseCreateResponse = new CountDownLatch(1);
        final AtomicReference<String> createdRoom = new AtomicReference<>();
        final AtomicInteger stateRequests = new AtomicInteger();
        final AtomicInteger createRequests = new AtomicInteger();
        final HttpServer server;
        final ExecutorService executor;
        final HttpClient transport =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        final URI backend;
        volatile int stateFailure;
        volatile boolean blockCreate;
        volatile boolean blockState;
        volatile boolean delayCreateResponse;

        FaultProxy(URI backend) throws IOException {
            this.backend = backend;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
            executor =
                    Executors.newCachedThreadPool(
                            task -> {
                                Thread thread = new Thread(task, "quoridor-test-proxy");
                                thread.setDaemon(true);
                                return thread;
                            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        URI endpoint() {
            return OnlineMatchControllerTest.endpoint(server.getAddress().getPort());
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/api/create")) {
                    createRequests.incrementAndGet();
                    if (blockCreate) {
                        createEntered.countDown();
                        releaseCreate.await(8, TimeUnit.SECONDS);
                    }
                }
                if (path.equals("/api/state")) {
                    stateRequests.incrementAndGet();
                    if (blockState) {
                        stateEntered.countDown();
                        releaseState.await(8, TimeUnit.SECONDS);
                    }
                    if (stateFailure != 0) {
                        String code =
                                switch (stateFailure) {
                                    case 401 -> "UNAUTHORIZED";
                                    case 404 -> "ROOM_NOT_FOUND";
                                    case 410 -> "ROOM_CLOSED";
                                    default -> "SERVER_ERROR";
                                };
                        respond(
                                exchange,
                                stateFailure,
                                ("error=" + code + "&message=" + code)
                                        .getBytes(StandardCharsets.US_ASCII));
                        return;
                    }
                }
                HttpRequest.Builder request =
                        HttpRequest.newBuilder(backend.resolve(path.substring(1)))
                                .timeout(Duration.ofSeconds(5));
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization != null) {
                    request.header("Authorization", authorization);
                }
                if (exchange.getRequestMethod().equals("GET")) {
                    request.GET();
                } else {
                    request.header(
                            "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    request.POST(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    exchange.getRequestBody().readNBytes(2049)));
                }
                HttpResponse<byte[]> response =
                        transport.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (path.equals("/api/create") && delayCreateResponse) {
                    for (String field :
                            new String(response.body(), StandardCharsets.US_ASCII).split("&")) {
                        if (field.startsWith("room=")) {
                            createdRoom.set(
                                    URLDecoder.decode(field.substring(5), StandardCharsets.UTF_8));
                        }
                    }
                    createForwarded.countDown();
                    releaseCreateResponse.await(8, TimeUnit.SECONDS);
                }
                respond(exchange, response.statusCode(), response.body());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.getResponseHeaders()
                    .set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        @Override
        public void close() {
            releaseCreate.countDown();
            releaseState.countDown();
            releaseCreateResponse.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
