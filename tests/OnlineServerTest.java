import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState;
import Board.GameState.Cell;
import Board.GameState.Orientation;
import Board.SaveStore;

import Online.OnlineClient;
import Online.OnlineException;
import Online.OnlineServer;
import Online.OnlineState;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real loopback HTTP integration tests; no external network, credentials or service is needed. */
public final class OnlineServerTest {
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--slow-request-probe")) {
            slowRequestProbe();
            return;
        }
        var watchdog =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "online-test-watchdog");
                            thread.setDaemon(true);
                            return thread;
                        });
        watchdog.schedule(
                () -> {
                    System.err.println("Online server tests exceeded their 180-second limit");
                    System.exit(1);
                },
                180,
                TimeUnit.SECONDS);
        try {
            endpointPolicy();
            try (OnlineServer server = server(OnlineServer.Limits.defaults())) {
                URI endpoint = endpoint(server);
                privateTwoPlayer(endpoint);
                fourPlayer(endpoint);
                matchmakingAndCancellation(endpoint);
                waitingSeatCompaction(endpoint);
                finishedMatch(endpoint);
                reconnectAfterTransientFailure(endpoint);
                authorizationAndConcurrentMoves(endpoint);
                malformedRequests(endpoint);
                historyBound(endpoint);
            }
            limitsAndExpiry();
            hostileResponses();
            replayedAndMalformedStates();
            slowRequestInFreshJvm();
            System.out.println("Online server tests passed (" + checks + " checks).");
        } finally {
            watchdog.shutdownNow();
        }
    }

    private static void privateTwoPlayer(URI endpoint) throws Exception {
        try (OnlineClient blue = new OnlineClient(endpoint);
                OnlineClient green = new OnlineClient(endpoint)) {
            OnlineState waiting = blue.createPrivate(2, "  بازیکن آبی  ");
            equal(waiting.status(), OnlineState.Status.WAITING, "Creator waits");
            equal(waiting.playerNames(), List.of("بازیکن آبی"), "Names are validated/trimmed");
            equal(waiting.session(), null, "Waiting has no local game");
            check(waiting.roomCode().matches("[A-Z2-9]{8}"), "Private code shape");
            OnlineState joined =
                    green.joinPrivate(
                            waiting.roomCode().toLowerCase(java.util.Locale.ROOT), "Green 🎮");
            equal(joined.playerIndex(), 1, "Joiner index");
            equal(joined.status(), OnlineState.Status.PLAYING, "Full room starts");
            OnlineState state = blue.state();
            equal(
                    state.playerNames(),
                    List.of("بازیکن آبی", "Green 🎮"),
                    "Unicode names round-trip");
            expect("NOT_YOUR_TURN", () -> green.move(1, 4, state.revision()));
            expect("ILLEGAL_MOVE", () -> blue.move(0, 0, state.revision()));
            equal(blue.state().revision(), state.revision(), "Rejected actions preserve revision");
            OnlineState moved = blue.move(7, 4, state.revision());
            equal(moved.moves(), List.of("0 M 7 4"), "Only successful move logged");
            equal(
                    moved.session().game().playerPosition(0),
                    new Cell(7, 4),
                    "Authoritative pawn moved");
            expect("STALE_REVISION", () -> green.move(1, 4, state.revision()));
            OnlineState wall = green.wall(3, 3, Orientation.HORIZONTAL, moved.revision());
            equal(wall.moves(), List.of("0 M 7 4", "1 W 3 3 HORIZONTAL"), "Wall history format");
            equal(wall.session().game().wallsRemaining(1), 9, "Wall stock updates");
            GameState detached = wall.session().game();
            Cell legal = detached.legalMoves().iterator().next();
            detached.moveTo(legal.row(), legal.col());
            equal(
                    wall.session().game().snapshot(),
                    green.state().session().game().snapshot(),
                    "DTO game is a defensive copy");
            boolean immutable = false;
            try {
                wall.moves().add("bad");
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            check(immutable, "Move history immutable");
            OnlineState left = green.leave();
            equal(
                    left.status(),
                    OnlineState.Status.CLOSED,
                    "Explicit leave closes local membership");
            check(!green.hasMembership(), "Leave discards credential");
            OnlineState closed = blue.state();
            equal(closed.reason(), "PLAYER_LEFT", "Peer observes leave");
            equal(
                    closed.session().game().snapshot(),
                    wall.session().game().snapshot(),
                    "Closure keeps final game");
            expect("ROOM_CLOSED", () -> blue.move(6, 4, closed.revision()));
        }
    }

    private static void fourPlayer(URI endpoint) throws Exception {
        List<OnlineClient> players = clients(endpoint, 4);
        try {
            OnlineState waiting = players.get(0).createPrivate(4, "P0");
            for (int index = 1; index < 4; index++) {
                OnlineState state = players.get(index).joinPrivate(waiting.roomCode(), "P" + index);
                equal(
                        state.status(),
                        index == 3 ? OnlineState.Status.PLAYING : OnlineState.Status.WAITING,
                        "Four seats needed");
                equal(state.playerIndex(), index, "Four-player seat identity");
            }
            OnlineState state = players.get(0).state();
            equal(state.session().game().playerCount(), 4, "Four-player save transport");
            int[] order = {0, 2, 1, 3};
            for (int turn = 0; turn < 4; turn++) {
                int index = order[turn];
                equal(state.session().game().wallsRemaining(index), 5, "Four-player inventory");
                Cell target = state.session().game().legalMoves().iterator().next();
                state = players.get(index).move(target.row(), target.col(), state.revision());
                equal(
                        state.session().game().currentPlayer(),
                        order[(turn + 1) % 4],
                        "Four-player clockwise turn order");
            }
            equal(state.moves().size(), 4, "Four-player actions logged");
            try (OnlineClient extra = new OnlineClient(endpoint)) {
                expect("ROOM_FULL", () -> extra.joinPrivate(waiting.roomCode(), "Extra"));
            }
        } finally {
            close(players);
        }
    }

    private static void matchmakingAndCancellation(URI endpoint) throws Exception {
        List<OnlineClient> players = clients(endpoint, 9);
        try {
            OnlineState privateRoom = players.get(0).createPrivate(2, "Private");
            OnlineState two = players.get(1).findMatch(2, "Two A");
            OnlineState four = players.get(2).findMatch(4, "Four A");
            check(
                    !two.roomCode().equals(privateRoom.roomCode()),
                    "Matchmaking cannot enter private room");
            check(!two.roomCode().equals(four.roomCode()), "Queues separated by count");
            expect(
                    "ROOM_NOT_FOUND",
                    () -> players.get(8).joinPrivate(two.roomCode(), "Private-code probe"));
            OnlineState twoB = players.get(3).findMatch(2, "Two B");
            equal(twoB.roomCode(), two.roomCode(), "Two-player queue fills same room");
            equal(twoB.status(), OnlineState.Status.PLAYING, "Public two-player game starts");
            players.get(2).leave();
            OnlineState freshFour = players.get(4).findMatch(4, "New Four A");
            check(!freshFour.roomCode().equals(four.roomCode()), "Cancel removes empty queue room");
            for (int index = 5; index <= 7; index++) {
                OnlineState joined = players.get(index).findMatch(4, "Four " + index);
                equal(joined.roomCode(), freshFour.roomCode(), "Four-player queue fills same room");
            }
            equal(
                    players.get(4).state().status(),
                    OnlineState.Status.PLAYING,
                    "Public four-player game starts");
            expect("ALREADY_IN_ROOM", () -> players.get(4).createPrivate(2, "Again"));
            OnlineState waiting = players.get(8).joinPrivate(privateRoom.roomCode(), "Friend");
            equal(
                    waiting.status(),
                    OnlineState.Status.PLAYING,
                    "Private room survives unrelated queue actions");
        } finally {
            close(players);
        }
    }

    private static void authorizationAndConcurrentMoves(URI endpoint) throws Exception {
        HttpResponse<String> created =
                raw(endpoint, "create", null, Map.of("count", "2", "name", "Raw Blue"));
        equal(created.statusCode(), 200, "Raw room created");
        Map<String, String> fields = decode(created.body());
        String token = fields.get("token");
        check(token != null && token.matches("[A-Za-z0-9_-]{43}"), "Strong opaque token format");
        try (OnlineClient peer = new OnlineClient(endpoint)) {
            OnlineState state = peer.joinPrivate(fields.get("room"), "Peer");
            equal(raw(endpoint, "state", null, null).statusCode(), 401, "Missing auth rejected");
            equal(
                    raw(endpoint, "state", "A".repeat(43), null).statusCode(),
                    401,
                    "Wrong token rejected");
            HttpResponse<String> status = raw(endpoint, "state", token, null);
            check(
                    !status.body().contains(token) && !decode(status.body()).containsKey("token"),
                    "Status does not disclose token");
            Map<String, String> move =
                    Map.of("row", "7", "col", "4", "revision", Long.toString(state.revision()));
            CompletableFuture<HttpResponse<String>> first =
                    HTTP.sendAsync(
                            request(endpoint, "move", token, encode(move)),
                            HttpResponse.BodyHandlers.ofString());
            CompletableFuture<HttpResponse<String>> second =
                    HTTP.sendAsync(
                            request(endpoint, "move", token, encode(move)),
                            HttpResponse.BodyHandlers.ofString());
            int a = first.get(6, TimeUnit.SECONDS).statusCode();
            int b = second.get(6, TimeUnit.SECONDS).statusCode();
            check(
                    (a == 200 && b == 409) || (a == 409 && b == 200),
                    "Concurrent duplicate action accepted exactly once");
            OnlineState after = peer.state();
            equal(after.revision(), state.revision() + 1, "Concurrent revision increments once");
            equal(after.moves(), List.of("0 M 7 4"), "Concurrent history append once");
            equal(
                    raw(endpoint, "leave", token, Map.of()).statusCode(),
                    200,
                    "Raw member can leave");
            equal(raw(endpoint, "state", token, null).statusCode(), 401, "Left token revoked");
        }
    }

    private static void waitingSeatCompaction(URI endpoint) throws Exception {
        List<OnlineClient> players = clients(endpoint, 4);
        try {
            OnlineState room = players.get(0).createPrivate(4, "A");
            players.get(1).joinPrivate(room.roomCode(), "B");
            players.get(2).joinPrivate(room.roomCode(), "C");
            players.get(1).leave();
            OnlineState compacted = players.get(2).state();
            equal(
                    compacted.playerIndex(),
                    1,
                    "Remaining credential follows compacted waiting seat");
            equal(compacted.playerNames(), List.of("A", "C"), "Waiting names compact together");
            players.get(3).joinPrivate(room.roomCode(), "D");
            players.get(1).joinPrivate(room.roomCode(), "B again");
            OnlineState state = players.get(0).state();
            equal(
                    state.playerNames(),
                    List.of("A", "C", "D", "B again"),
                    "Rejoined player receives last seat");
            state = players.get(0).move(7, 4, state.revision());
            final OnlineState redTurn = state;
            expect("NOT_YOUR_TURN", () -> players.get(2).move(1, 4, redTurn.revision()));
            Cell legal = state.session().game().legalMoves().iterator().next();
            state = players.get(3).move(legal.row(), legal.col(), state.revision());
            legal = state.session().game().legalMoves().iterator().next();
            state = players.get(2).move(legal.row(), legal.col(), state.revision());
            check(
                    state.moves().get(2).startsWith("1 M "),
                    "Old token acts only for its current seat");
        } finally {
            close(players);
        }
    }

    private static void finishedMatch(URI endpoint) throws Exception {
        try (OnlineClient blue = new OnlineClient(endpoint);
                OnlineClient green = new OnlineClient(endpoint)) {
            OnlineState room = blue.createPrivate(2, "Blue");
            OnlineState state = green.joinPrivate(room.roomCode(), "Green");
            int turn = 0;
            for (int row = 7; row >= 0; row--) {
                state = blue.move(row, 4, state.revision());
                if (row > 0) {
                    state = green.move(0, turn++ % 2 == 0 ? 3 : 4, state.revision());
                }
            }
            equal(state.status(), OnlineState.Status.CLOSED, "Goal closes online match");
            equal(state.reason(), "FINISHED", "Finished room reason");
            equal(state.session().game().winner(), 0, "Winner transported in final session");
            equal(
                    green.state().session().game().snapshot(),
                    state.session().game().snapshot(),
                    "Peer sees same final result");
            final long revision = state.revision();
            expect("ROOM_CLOSED", () -> green.move(1, 3, revision));
        }
    }

    private static void reconnectAfterTransientFailure(URI target) throws Exception {
        AtomicBoolean unavailable = new AtomicBoolean();
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        proxy.createContext(
                "/",
                exchange -> {
                    try {
                        byte[] body;
                        int status;
                        if (unavailable.get()) {
                            body =
                                    "error=SERVER_UNAVAILABLE&message=retry"
                                            .getBytes(StandardCharsets.US_ASCII);
                            status = 503;
                        } else {
                            HttpRequest.Builder request =
                                    HttpRequest.newBuilder(
                                                    target.resolve(
                                                            exchange.getRequestURI().getPath()))
                                            .timeout(Duration.ofSeconds(5));
                            String authorization =
                                    exchange.getRequestHeaders().getFirst("Authorization");
                            if (authorization != null) {
                                request.header("Authorization", authorization);
                            }
                            if (exchange.getRequestMethod().equals("GET")) {
                                request.GET();
                            } else {
                                request.header("Content-Type", "application/x-www-form-urlencoded")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofByteArray(
                                                        exchange.getRequestBody()
                                                                .readNBytes(2049)));
                            }
                            HttpResponse<byte[]> response =
                                    HTTP.send(
                                            request.build(),
                                            HttpResponse.BodyHandlers.ofByteArray());
                            body = response.body();
                            status = response.statusCode();
                        }
                        exchange.getResponseHeaders()
                                .set("Content-Type", "application/x-www-form-urlencoded");
                        exchange.sendResponseHeaders(status, body.length);
                        exchange.getResponseBody().write(body);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        proxy.start();
        try (OnlineClient client =
                new OnlineClient("http://127.0.0.1:" + proxy.getAddress().getPort())) {
            OnlineState created = client.createPrivate(2, "Reconnect");
            unavailable.set(true);
            expect("SERVER_UNAVAILABLE", client::state);
            check(client.hasMembership(), "Transient failure keeps private reconnect credential");
            unavailable.set(false);
            OnlineState restored = client.state();
            equal(restored.roomCode(), created.roomCode(), "Reconnect returns original room");
            equal(
                    restored.playerIndex(),
                    created.playerIndex(),
                    "Reconnect preserves authority identity");
            equal(
                    restored.revision(),
                    created.revision(),
                    "Reconnect does not add a seat or action");
        } finally {
            proxy.stop(0);
        }
    }

    private static void malformedRequests(URI endpoint) throws Exception {
        equal(
                raw(endpoint, "create", null, Map.of("count", "3", "name", "Invalid")).statusCode(),
                400,
                "Unsupported player count");
        equal(
                HTTP.send(
                                request(endpoint, "create", null, "count=2&name=a&name=b"),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode(),
                400,
                "Duplicate fields rejected");
        equal(
                HTTP.send(
                                request(endpoint, "create", null, "count=2&name=%FF"),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode(),
                400,
                "Malformed UTF-8 rejected");
        equal(
                HTTP.send(
                                request(endpoint, "create", null, "count=2&name=%GG"),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode(),
                400,
                "Malformed form encoding rejected");
        equal(
                raw(
                                endpoint,
                                "create",
                                null,
                                Map.of("count", "2", "name", "Name", "token", "in body"))
                        .statusCode(),
                400,
                "Unknown request fields rejected");
        oversizedRequestBoundaries(endpoint);
        equal(raw(endpoint, "unknown", null, Map.of()).statusCode(), 404, "Unknown endpoint");
        equal(raw(endpoint, "state", null, Map.of()).statusCode(), 405, "Wrong method");
        HttpRequest query =
                HttpRequest.newBuilder(endpoint.resolve("api/state?token=secret"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
        HttpResponse<String> rejected = HTTP.send(query, HttpResponse.BodyHandlers.ofString());
        equal(rejected.statusCode(), 400, "Credentials in URL not accepted");
        check(!rejected.body().contains("secret"), "Error does not echo request data");
        HttpResponse<String> health =
                HTTP.send(
                        HttpRequest.newBuilder(endpoint.resolve("health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        equal(health.statusCode(), 200, "Health endpoint");
        equal(
                decode(health.body()).get("status"),
                "ok",
                "Health data contains no room information");
    }

    private static void historyBound(URI endpoint) throws Exception {
        try (OnlineClient blue = new OnlineClient(endpoint);
                OnlineClient green = new OnlineClient(endpoint)) {
            OnlineState created = blue.createPrivate(2, "Blue");
            OnlineState state = green.joinPrivate(created.roomCode(), "Green");
            for (int move = 0; move < 1004; move++) {
                int player = move % 2;
                int col = (move / 2) % 2 == 0 ? 3 : 4;
                state =
                        (player == 0 ? blue : green)
                                .move(player == 0 ? 8 : 0, col, state.revision());
            }
            equal(state.moves().size(), 1000, "History is bounded to last1000 actual actions");
            equal(state.moves().get(0), "0 M 8 3", "History drops oldest complete records");
            equal(state.moves().get(999), "1 M 0 4", "Most recent action retained");
        }
    }

    private static void oversizedRequestBoundaries(URI endpoint) throws Exception {
        String headerOnly = oversizedHeaderOnly(endpoint, "2202", true);
        check(
                headerOnly.startsWith("HTTP/1.1 413 "),
                "Oversized header-only request receives exact HTTP413");
        try {
            equal(
                    HTTP.send(
                                    request(endpoint, "create", null, "x=" + "a".repeat(2200)),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode(),
                    413,
                    "Fully sent oversized body receives HTTP413 when TCP permits it");
        } catch (IOException rejected) {
            check(
                    isConnectionReset(rejected),
                    "Unread oversized body may be rejected by actual TCP reset only");
        }
        var limits =
                new OnlineServer.Limits(
                        2,
                        1,
                        2,
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1));
        try (OnlineServer server = server(limits)) {
            long start = System.nanoTime();
            String huge = oversizedHeaderOnly(endpoint(server), "999999999", false);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check(
                    huge.startsWith("HTTP/1.1 413 "),
                    "Huge declared body is rejected before waiting for missing bytes");
            check(
                    elapsed < 3000,
                    "Oversized body rejection does not drain attacker-controlled input");
            HttpResponse<String> health =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server).resolve("health"))
                                    .timeout(Duration.ofSeconds(3))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            equal(
                    health.statusCode(),
                    200,
                    "Single worker remains available after huge declared body rejection");
        }
    }

    private static String oversizedHeaderOnly(URI endpoint, String length, boolean halfClose)
            throws Exception {
        try (Socket socket = new Socket("127.0.0.1", endpoint.getPort())) {
            socket.setSoTimeout(3000);
            String headers =
                    "POST /api/create HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                            + length
                            + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            if (halfClose) {
                socket.shutdownOutput();
            }
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (response.size() <= 4096) {
                int count =
                        socket.getInputStream()
                                .read(buffer, 0, Math.min(buffer.length, 4097 - response.size()));
                if (count < 0) {
                    break;
                }
                response.write(buffer, 0, count);
            }
            check(response.size() <= 4096, "Oversized-request error response is bounded");
            return new String(response.toByteArray(), StandardCharsets.US_ASCII);
        }
    }

    private static boolean isConnectionReset(IOException error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof java.net.SocketException
                    && cause.getMessage() != null
                    && cause.getMessage()
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains("connection reset")) {
                return true;
            }
        }
        return false;
    }

    private static void limitsAndExpiry() throws Exception {
        var limits =
                new OnlineServer.Limits(
                        1,
                        2,
                        4,
                        Duration.ofSeconds(3),
                        Duration.ofMillis(350),
                        Duration.ofMillis(150));
        try (OnlineServer server = server(limits);
                OnlineClient first = new OnlineClient(endpoint(server));
                OnlineClient other = new OnlineClient(endpoint(server))) {
            first.createPrivate(2, "First");
            expect("SERVER_FULL", () -> other.createPrivate(2, "Other"));
            first.leave();
            other.createPrivate(2, "Other");
            Thread.sleep(420);
            OnlineState expired = other.state();
            equal(expired.status(), OnlineState.Status.CLOSED, "Waiting expiry closes room");
            equal(expired.reason(), "WAIT_TIMEOUT", "Waiting expiry reason");
            Thread.sleep(200);
            expect("UNAUTHORIZED", other::state);
            try {
                other.leave();
            } catch (OnlineException expected) {
                check(!other.hasMembership(), "Expired leave still clears token");
            }
            first.createPrivate(2, "Reusable capacity");
            check(first.hasMembership(), "Expiry cleanup frees room capacity");
        }
        var idleLimits =
                new OnlineServer.Limits(
                        2,
                        2,
                        4,
                        Duration.ofMillis(300),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1));
        try (OnlineServer server = server(idleLimits);
                OnlineClient first = new OnlineClient(endpoint(server));
                OnlineClient second = new OnlineClient(endpoint(server))) {
            OnlineState room = first.createPrivate(2, "A");
            second.joinPrivate(room.roomCode(), "B");
            Thread.sleep(370);
            equal(
                    first.state().reason(),
                    "PLAYER_TIMEOUT",
                    "Inactive membership timeout closes a started match");
        }
    }

    private static void endpointPolicy() throws Exception {
        for (String rejected :
                List.of(
                        "http://example.com",
                        "http://8.8.8.8",
                        "https://user:secret@example.com",
                        "https://example.com/?token=secret",
                        "https://example.com/path",
                        "ftp://127.0.0.1")) {
            boolean guarded = false;
            try {
                new OnlineClient(rejected);
            } catch (IllegalArgumentException expected) {
                guarded = true;
            }
            check(guarded, "Unsafe endpoint rejected");
        }
        for (String allowed :
                List.of(
                        "https://example.com",
                        "http://127.0.0.1:8080",
                        "http://localhost:8080",
                        "http://192.168.1.2",
                        "http://10.1.2.3",
                        "http://172.16.1.2")) {
            try (OnlineClient client = new OnlineClient(allowed)) {
                check(!client.hasMembership(), "HTTPS/local endpoint accepted without connection");
            }
        }
    }

    private static void hostileResponses() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        HttpServer trap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        trap.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readNBytes(2049);
                    redirected.incrementAndGet();
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        trap.start();
        HttpServer hostile = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        hostile.createContext(
                "/api/create",
                exchange -> {
                    exchange.getRequestBody().readNBytes(2049);
                    exchange.getResponseHeaders()
                            .set(
                                    "Location",
                                    "http://127.0.0.1:" + trap.getAddress().getPort() + "/");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        hostile.start();
        try (OnlineClient client =
                new OnlineClient("http://127.0.0.1:" + hostile.getAddress().getPort())) {
            ioFailure(() -> client.createPrivate(2, "A"), "Redirect rejected");
            equal(redirected.get(), 0, "Redirect target never contacted");
            check(!client.hasMembership(), "Failed entry has no credential");
        } finally {
            hostile.stop(0);
            trap.stop(0);
        }
        HttpServer giant = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        giant.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readNBytes(2049);
                    byte[] bytes = new byte[192 * 1024 + 1];
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/x-www-form-urlencoded");
                    try {
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (IOException disconnected) {
                    } finally {
                        exchange.close();
                    }
                });
        giant.start();
        try (OnlineClient client =
                new OnlineClient("http://127.0.0.1:" + giant.getAddress().getPort())) {
            ioFailure(() -> client.createPrivate(2, "A"), "Response size bounded");
        } finally {
            giant.stop(0);
        }
    }

    private static void slowRequestInFreshJvm() throws Exception {
        String executable =
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                        ? "java.exe"
                        : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        Process process =
                new ProcessBuilder(
                                java,
                                "--add-modules",
                                "jdk.httpserver",
                                "-Dsun.net.httpserver.timerMillis=50",
                                "-cp",
                                System.getProperty("java.class.path"),
                                "OnlineServerTest",
                                "--slow-request-probe")
                        .redirectErrorStream(true)
                        .start();
        try {
            check(
                    process.waitFor(30, TimeUnit.SECONDS),
                    "Slow-request probe finishes within its deadline");
            String output =
                    new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8);
            equal(
                    process.exitValue(),
                    0,
                    "Fresh JVM enforces production request timeout: " + output);
            check(
                    output.contains("Slow request timeout and worker recovery passed"),
                    "Actual stalled body and worker recovery verified");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
        }
    }

    private static void replayedAndMalformedStates() throws Exception {
        Map<String, String> baseline =
                new HashMap<>(
                        Map.of(
                                "protocol",
                                "1",
                                "room",
                                "ABCDEFGH",
                                "status",
                                "PLAYING",
                                "player",
                                "0",
                                "count",
                                "2",
                                "revision",
                                "3",
                                "names",
                                "2",
                                "name0",
                                "Blue",
                                "name1",
                                "Green",
                                "moves",
                                "0"));
        baseline.put("reason", "");
        GameConfig config =
                new GameConfig("Blue", "Green", GameConfig.Mode.LOCAL, BotPlayer.Difficulty.EASY);
        baseline.put(
                "session",
                Base64.getEncoder()
                        .encodeToString(
                                SaveStore.encode(new GameSession(new GameState(), config))
                                        .getBytes(StandardCharsets.UTF_8)));
        AtomicReference<Map<String, String>> stateReply = new AtomicReference<>(baseline);
        HttpServer fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        fixture.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readNBytes(2049);
                    Map<String, String> reply = new HashMap<>(stateReply.get());
                    if (exchange.getRequestURI().getPath().equals("/api/create")) {
                        reply = new HashMap<>(baseline);
                        reply.put("token", "A".repeat(43));
                    } else if (exchange.getRequestURI().getPath().equals("/api/leave")) {
                        reply = new HashMap<>(baseline);
                        reply.put("revision", "10");
                        reply.put("status", "CLOSED");
                        reply.put("reason", "LEFT");
                    }
                    byte[] bytes = encode(reply).getBytes(StandardCharsets.US_ASCII);
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/x-www-form-urlencoded");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        fixture.start();
        try (OnlineClient client =
                new OnlineClient("http://127.0.0.1:" + fixture.getAddress().getPort())) {
            client.createPrivate(2, "Blue");
            for (Map.Entry<String, String> invalid :
                    Map.of("revision", "2", "room", "BCDEFGHJ", "player", "1").entrySet()) {
                Map<String, String> changed = new HashMap<>(baseline);
                changed.put(invalid.getKey(), invalid.getValue());
                stateReply.set(changed);
                ioFailure(client::state, "Replayed state or changed authority rejected");
            }
            Map<String, String> missing = new HashMap<>(baseline);
            missing.remove("room");
            stateReply.set(missing);
            ioFailure(client::state, "Missing room rejected as checked transport error");
            Map<String, String> bot = new HashMap<>(baseline);
            GameConfig botConfig =
                    new GameConfig("Blue", "Green", GameConfig.Mode.BOT, BotPlayer.Difficulty.EASY);
            bot.put(
                    "session",
                    Base64.getEncoder()
                            .encodeToString(
                                    SaveStore.encode(new GameSession(new GameState(), botConfig))
                                            .getBytes(StandardCharsets.UTF_8)));
            stateReply.set(bot);
            ioFailure(client::state, "Online state cannot contain a bot config");
            GameState finished = new GameState();
            int turn = 0;
            for (int row = 7; row >= 0; row--) {
                check(finished.moveTo(row, 4), "Finished fixture blue move");
                if (row > 0) {
                    check(
                            finished.moveTo(0, turn++ % 2 == 0 ? 3 : 4),
                            "Finished fixture green move");
                }
            }
            Map<String, String> wrongStatus = new HashMap<>(baseline);
            wrongStatus.put(
                    "session",
                    Base64.getEncoder()
                            .encodeToString(
                                    SaveStore.encode(new GameSession(finished, config))
                                            .getBytes(StandardCharsets.UTF_8)));
            stateReply.set(wrongStatus);
            ioFailure(client::state, "Finished game cannot claim PLAYING");
            for (String record :
                    List.of(
                            "2 M 0 0",
                            "0 M 9 0",
                            "0 M 1 2 EXTRA",
                            "0 W 8 0 HORIZONTAL",
                            "0 W 0 0 diagonal",
                            "0 M " + "1".repeat(10000))) {
                Map<String, String> invalid = new HashMap<>(baseline);
                invalid.put("moves", "1");
                invalid.put("move0", record);
                stateReply.set(invalid);
                ioFailure(client::state, "Malformed move history rejected before UI parsing");
            }
            Map<String, String> tooMany = new HashMap<>(baseline);
            tooMany.put("moves", "1001");
            stateReply.set(tooMany);
            ioFailure(client::state, "Move history count bounded");
            Map<String, String> newer = new HashMap<>(baseline);
            newer.put("revision", "4");
            stateReply.set(newer);
            equal(
                    client.state().revision(),
                    4L,
                    "Valid newer state accepted after malformed responses");
            stateReply.set(baseline);
            ioFailure(client::state, "Revision pin advances after each successful poll");
            check(
                    client.hasMembership(),
                    "Malformed response does not discard reconnect credential");
        } finally {
            fixture.stop(0);
        }
    }

    private static void slowRequestProbe() throws Exception {
        var limits =
                new OnlineServer.Limits(
                        2,
                        1,
                        2,
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1));
        try (OnlineServer server = server(limits);
                Socket stalled = new Socket("127.0.0.1", server.port())) {
            stalled.setSoTimeout(14000);
            String request =
                    "POST /api/create HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\nname=A";
            long start = System.nanoTime();
            stalled.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            stalled.getOutputStream().flush();
            try {
                equal(stalled.getInputStream().read(), -1, "Server closes incomplete request body");
            } catch (java.net.SocketException closed) {
                // A reset is also a valid transport-level rejection of the incomplete request.
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check(
                    elapsed >= 7500 && elapsed < 14000,
                    "Production timeout is ten seconds, not ten thousand seconds");
            HttpResponse<String> health =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server).resolve("health"))
                                    .timeout(Duration.ofSeconds(3))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            equal(health.statusCode(), 200, "Single worker recovers after slow request timeout");
            System.out.println("Slow request timeout and worker recovery passed");
        }
    }

    private static OnlineServer server(OnlineServer.Limits limits) throws IOException {
        OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0), limits);
        server.start();
        return server;
    }

    private static URI endpoint(OnlineServer server) {
        return URI.create("http://127.0.0.1:" + server.port() + "/");
    }

    private static List<OnlineClient> clients(URI endpoint, int count) {
        List<OnlineClient> clients = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            clients.add(new OnlineClient(endpoint));
        }
        return clients;
    }

    private static void close(List<OnlineClient> clients) throws IOException {
        IOException failure = null;
        for (OnlineClient client : clients) {
            try {
                client.close();
            } catch (IOException failed) {
                failure = failed;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static HttpResponse<String> raw(
            URI endpoint, String action, String token, Map<String, String> fields)
            throws Exception {
        return HTTP.send(
                request(endpoint, action, token, fields == null ? null : encode(fields)),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest request(URI endpoint, String action, String token, String body) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(endpoint.resolve("api/" + action))
                        .timeout(Duration.ofSeconds(6));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return builder.build();
    }

    private static String encode(Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        fields.forEach(
                (key, value) ->
                        parts.add(
                                URLEncoder.encode(key, StandardCharsets.UTF_8)
                                        + "="
                                        + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return String.join("&", parts);
    }

    private static Map<String, String> decode(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);
            fields.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static void expect(String code, Operation operation) throws Exception {
        try {
            operation.run();
        } catch (OnlineException expected) {
            equal(expected.code(), code, "Expected protocol rejection");
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private static void ioFailure(Operation operation, String message) throws Exception {
        try {
            operation.run();
        } catch (IOException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                java.util.Objects.equals(actual, expected),
                message + ": actual=" + actual + ", expected=" + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws Exception;
    }
}
