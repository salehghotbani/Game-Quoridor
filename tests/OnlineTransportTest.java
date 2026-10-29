import Online.OnlineClient;
import Online.OnlineException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises URLConnection's real streaming, error, redirect and cancellation behavior. */
public final class OnlineTransportTest {
    private static final int RESPONSE_LIMIT = 192 * 1024;
    private static final String TOKEN = "A".repeat(43);
    private static final String TYPE = "application/x-www-form-urlencoded; charset=UTF-8";
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        var watchdog =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "transport-test-watchdog");
                            thread.setDaemon(true);
                            return thread;
                        });
        watchdog.schedule(
                () -> {
                    System.err.println("Online transport tests exceeded their 75-second limit");
                    System.exit(1);
                },
                75,
                TimeUnit.SECONDS);
        try {
            requestHeadersAndCredentials();
            compressedResponsesRemainBounded();
            redirectsStayAtOrigin();
            bodyBoundsAndMalformedResponses();
            errorsUseBoundedErrorStream();
            expiredPostPreservesAuthenticationError();
            failedPostIsNotReplayed();
            interruptionCancelsBlockedRequest(false);
            interruptionCancelsBlockedRequest(true);
            wholeRequestDeadlineStopsDrippingBody();
            System.out.println("Online transport tests passed (" + checks + " checks).");
        } finally {
            watchdog.shutdownNow();
        }
    }

    private static void requestHeadersAndCredentials() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<String> credentials = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        AtomicBoolean lengthsMatch = new AtomicBoolean(true);
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    byte[] body = exchange.getRequestBody().readNBytes(2049);
                                    methods.add(exchange.getRequestMethod());
                                    bodies.add(new String(body, StandardCharsets.US_ASCII));
                                    credentials.add(
                                            exchange.getRequestHeaders().getFirst("Authorization"));
                                    queries.add(exchange.getRequestURI().getRawQuery());
                                    if (exchange.getRequestMethod().equals("POST")) {
                                        lengthsMatch.set(
                                                lengthsMatch.get()
                                                        && Integer.toString(body.length)
                                                                .equals(
                                                                        exchange.getRequestHeaders()
                                                                                .getFirst(
                                                                                        "Content-Length"))
                                                        && exchange.getRequestHeaders()
                                                                        .getFirst(
                                                                                "Transfer-Encoding")
                                                                == null);
                                    }
                                    respond(
                                            exchange,
                                            200,
                                            TYPE,
                                            room(exchange.getRequestURI().getPath()));
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            client.createPrivate(2, "بازیکن 🎮");
            client.state();
            client.state();
            equal(
                    methods,
                    List.of("POST", "GET", "GET"),
                    "No response cache or method substitution");
            equal(credentials.get(0), null, "Entry has no bearer credential");
            equal(credentials.get(1), "Bearer " + TOKEN, "Credential uses Authorization header");
            equal(credentials.get(2), "Bearer " + TOKEN, "Credential retained across polls");
            check(
                    bodies.get(0).contains("%D8") && !bodies.get(0).contains(TOKEN),
                    "Unicode form body and no embedded credential");
            check(
                    queries.stream().allMatch(value -> value == null),
                    "No credentials in request URLs");
            check(lengthsMatch.get(), "POST uses a fixed length rather than replayable buffering");
        }
    }

    private static void compressedResponsesRemainBounded() throws Exception {
        for (boolean oversized : List.of(false, true)) {
            try (Fixture server =
                            new Fixture(
                                    exchange -> {
                                        exchange.getRequestBody().readNBytes(2049);
                                        check(
                                                "gzip"
                                                        .equals(
                                                                exchange.getRequestHeaders()
                                                                        .getFirst(
                                                                                "Accept-Encoding")),
                                                "Client negotiates gzip");
                                        byte[] plain =
                                                oversized
                                                        ? new byte[RESPONSE_LIMIT + 1]
                                                        : room(exchange.getRequestURI().getPath());
                                        var bytes = new java.io.ByteArrayOutputStream();
                                        try (var gzip = new java.util.zip.GZIPOutputStream(bytes)) {
                                            gzip.write(plain);
                                        }
                                        exchange.getResponseHeaders()
                                                .set("Content-Encoding", "gzip");
                                        respond(exchange, 200, TYPE, bytes.toByteArray());
                                    });
                    OnlineClient client = new OnlineClient(server.endpoint())) {
                if (oversized) {
                    IOException failure = ioFailure(() -> client.createPrivate(2, "Blue"));
                    check(
                            failure.getMessage().contains("too large"),
                            "Decoded gzip bombs retain response limit");
                    check(
                            !client.hasMembership(),
                            "Invalid compressed entry never acquires membership");
                } else {
                    client.createPrivate(2, "Blue");
                    client.state();
                    check(client.hasMembership(), "Valid gzip entry and polls round-trip");
                }
            }
        }
    }

    private static void redirectsStayAtOrigin() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        boolean globalRedirectDefault = HttpURLConnection.getFollowRedirects();
        try (Fixture trap =
                        new Fixture(
                                exchange -> {
                                    redirected.incrementAndGet();
                                    exchange.getRequestBody().readNBytes(2049);
                                    respond(exchange, 200, TYPE, new byte[0]);
                                });
                Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    if (exchange.getRequestURI().getPath().equals("/api/state")) {
                                        exchange.getResponseHeaders()
                                                .set("Location", trap.endpoint().toString());
                                        exchange.sendResponseHeaders(307, -1);
                                    } else {
                                        respond(
                                                exchange,
                                                200,
                                                TYPE,
                                                room(exchange.getRequestURI().getPath()));
                                    }
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            client.createPrivate(2, "Blue");
            IOException failure = ioFailure(client::state);
            check(
                    failure.getMessage().contains("redirects"),
                    "Redirect receives a specific rejection");
            equal(redirected.get(), 0, "Redirect destination never receives the bearer header");
            check(client.hasMembership(), "Redirect failure preserves reconnect credential");
        }
        equal(
                HttpURLConnection.getFollowRedirects(),
                globalRedirectDefault,
                "No global redirect setting changes");
    }

    private static void bodyBoundsAndMalformedResponses() throws Exception {
        for (boolean chunked : List.of(false, true)) {
            try (Fixture server =
                            new Fixture(
                                    exchange -> {
                                        exchange.getRequestBody().readNBytes(2049);
                                        byte[] oversized = new byte[RESPONSE_LIMIT + 1];
                                        exchange.getResponseHeaders().set("Content-Type", TYPE);
                                        exchange.sendResponseHeaders(
                                                200, chunked ? 0 : oversized.length);
                                        exchange.getResponseBody().write(oversized);
                                    });
                    OnlineClient client = new OnlineClient(server.endpoint())) {
                IOException failure = ioFailure(() -> client.createPrivate(2, "Blue"));
                check(
                        failure.getMessage().contains("too large"),
                        "Fixed/chunked oversized body rejected by byte bound");
                check(!client.hasMembership(), "Oversized entry does not gain membership");
            }
        }
        for (String malformed : List.of("protocol=1&protocol=1", "protocol=%FF", "protocol=%GG")) {
            try (Fixture server =
                            new Fixture(
                                    exchange -> {
                                        exchange.getRequestBody().readNBytes(2049);
                                        respond(
                                                exchange,
                                                200,
                                                TYPE,
                                                malformed.getBytes(StandardCharsets.US_ASCII));
                                    });
                    OnlineClient client = new OnlineClient(server.endpoint())) {
                ioFailure(() -> client.createPrivate(2, "Blue"));
            }
        }
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    respond(
                                            exchange,
                                            200,
                                            "application/json",
                                            "{}".getBytes(StandardCharsets.US_ASCII));
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            check(
                    ioFailure(() -> client.createPrivate(2, "Blue"))
                            .getMessage()
                            .contains("unsupported"),
                    "Unsupported response type rejected");
        }
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    byte[] body = room("/api/create");
                                    exchange.getResponseHeaders().set("Content-Type", TYPE);
                                    exchange.sendResponseHeaders(200, body.length + 10);
                                    exchange.getResponseBody().write(body);
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            ioFailure(() -> client.createPrivate(2, "Blue"));
            check(!client.hasMembership(), "Truncated response cannot install a credential");
        }
    }

    private static void errorsUseBoundedErrorStream() throws Exception {
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    respond(
                                            exchange,
                                            503,
                                            TYPE,
                                            form(
                                                    Map.of(
                                                            "error",
                                                            "SERVER_FULL",
                                                            "message",
                                                            "do not echo private user data")));
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            IOException failure = ioFailure(() -> client.createPrivate(2, "Blue"));
            check(
                    failure instanceof OnlineException,
                    "Non-2xx error body is read from error stream");
            OnlineException online = (OnlineException) failure;
            equal(online.code(), "SERVER_FULL", "Stable error code retained");
            equal(online.httpStatus(), 503, "HTTP status retained");
            check(
                    !online.getMessage().contains("private user data"),
                    "Untrusted server error text is not exposed");
        }
    }

    private static void failedPostIsNotReplayed() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    requests.incrementAndGet();
                                    // Deliberately close after receiving the body, before a status
                                    // line is returned.
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            ioFailure(() -> client.findMatch(2, "Blue"));
            equal(requests.get(), 1, "Failed non-idempotent POST is never automatically replayed");
        }
    }

    private static void expiredPostPreservesAuthenticationError() throws Exception {
        AtomicInteger denied = new AtomicInteger();
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    if (exchange.getRequestURI().getPath().equals("/api/create")) {
                                        respond(exchange, 200, TYPE, room("/api/create"));
                                    } else {
                                        denied.incrementAndGet();
                                        respond(
                                                exchange,
                                                401,
                                                TYPE,
                                                form(
                                                        Map.of(
                                                                "error",
                                                                "UNAUTHORIZED",
                                                                "message",
                                                                "not for display")));
                                    }
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            client.createPrivate(2, "Blue");
            IOException move = ioFailure(() -> client.move(7, 4, 1));
            check(
                    move instanceof OnlineException
                            && ((OnlineException) move).httpStatus() == 401
                            && ((OnlineException) move).code().equals("UNAUTHORIZED"),
                    "Expired POST retains terminal authentication status on JVM and Android");
            check(client.hasMembership(), "Failed action retains credential until explicit leave");
            IOException leave = ioFailure(client::leave);
            check(
                    leave instanceof OnlineException
                            && ((OnlineException) leave).code().equals("UNAUTHORIZED"),
                    "Expired leave has the same stable error contract");
            check(!client.hasMembership(), "Expired leave always discards membership");
            equal(denied.get(), 2, "Authentication failure never causes a POST replay");
        }
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    respond(exchange, 401, "text/html", new byte[0]);
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            check(
                    !(ioFailure(() -> client.createPrivate(2, "Blue")) instanceof OnlineException),
                    "Bodyless 401 fallback still requires the protocol content type");
        }
    }

    private static void interruptionCancelsBlockedRequest(boolean afterHeaders) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRetained = new AtomicBoolean();
        try (Fixture server =
                new Fixture(
                        exchange -> {
                            exchange.getRequestBody().readNBytes(2049);
                            if (afterHeaders) {
                                exchange.getResponseHeaders().set("Content-Type", TYPE);
                                exchange.sendResponseHeaders(200, 0);
                                exchange.getResponseBody().write('a');
                                exchange.getResponseBody().flush();
                            }
                            received.countDown();
                            try {
                                Thread.sleep(90000);
                            } catch (InterruptedException cancelled) {
                                Thread.currentThread().interrupt();
                            }
                        })) {
            OnlineClient client = new OnlineClient(server.endpoint());
            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    client.findMatch(2, "Blue");
                                } catch (Throwable expected) {
                                    failure.set(expected);
                                    interruptRetained.set(Thread.currentThread().isInterrupted());
                                }
                            },
                            "transport-cancellation-probe");
            worker.setDaemon(true);
            worker.start();
            check(received.await(3, TimeUnit.SECONDS), "Blocked request reached server");
            worker.interrupt();
            worker.join(3000);
            check(!worker.isAlive(), "Cancellation disconnects blocked headers/body promptly");
            check(
                    failure.get() instanceof InterruptedIOException,
                    "Cancellation is a checked interrupted I/O error");
            check(interruptRetained.get(), "Cancellation preserves caller interruption flag");
            client.close();
        }
    }

    private static void wholeRequestDeadlineStopsDrippingBody() throws Exception {
        try (Fixture server =
                        new Fixture(
                                exchange -> {
                                    exchange.getRequestBody().readNBytes(2049);
                                    if (!exchange.getRequestURI().getPath().equals("/api/state")) {
                                        respond(
                                                exchange,
                                                200,
                                                TYPE,
                                                room(exchange.getRequestURI().getPath()));
                                        return;
                                    }
                                    exchange.getResponseHeaders().set("Content-Type", TYPE);
                                    exchange.sendResponseHeaders(200, 0);
                                    for (int index = 0; index < 100; index++) {
                                        exchange.getResponseBody().write('a');
                                        exchange.getResponseBody().flush();
                                        try {
                                            Thread.sleep(250);
                                        } catch (InterruptedException cancelled) {
                                            Thread.currentThread().interrupt();
                                            return;
                                        }
                                    }
                                });
                OnlineClient client = new OnlineClient(server.endpoint())) {
            client.createPrivate(2, "Blue");
            long start = System.nanoTime();
            IOException failure = ioFailure(client::state);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check(
                    failure instanceof java.net.SocketTimeoutException,
                    "Whole request deadline is reported as a timeout");
            check(
                    elapsed >= 10000 && elapsed < 16000,
                    "Dripping body cannot reset the twelve-second whole request deadline");
            check(client.hasMembership(), "Timeout preserves reconnect membership");
        }
    }

    private static byte[] room(String path) {
        Map<String, String> fields =
                new HashMap<>(
                        Map.of(
                                "protocol",
                                "1",
                                "room",
                                "ABCDEFGH",
                                "status",
                                "WAITING",
                                "player",
                                "0",
                                "count",
                                "2",
                                "revision",
                                "1",
                                "names",
                                "1",
                                "name0",
                                "Blue",
                                "moves",
                                "0",
                                "reason",
                                ""));
        if (path.equals("/api/create") || path.equals("/api/match")) {
            fields.put("token", TOKEN);
        }
        if (path.equals("/api/leave")) {
            fields.put("status", "CLOSED");
            fields.put("reason", "LEFT");
            fields.put("revision", "2");
        }
        return form(fields);
    }

    private static byte[] form(Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        fields.forEach(
                (key, value) ->
                        parts.add(
                                URLEncoder.encode(key, StandardCharsets.UTF_8)
                                        + "="
                                        + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return String.join("&", parts).getBytes(StandardCharsets.US_ASCII);
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length != 0) {
            exchange.getResponseBody().write(body);
        }
    }

    private static IOException ioFailure(Operation operation) throws Exception {
        try {
            operation.run();
        } catch (IOException expected) {
            checks++;
            return expected;
        }
        throw new AssertionError("Expected checked transport failure");
    }

    private static void equal(Object actual, Object expected, String message) {
        check(java.util.Objects.equals(actual, expected), message);
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

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService workers;

        Fixture(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
            workers =
                    Executors.newFixedThreadPool(
                            2,
                            task -> {
                                Thread thread = new Thread(task, "transport-fixture");
                                thread.setDaemon(true);
                                return thread;
                            });
            server.setExecutor(workers);
            server.createContext(
                    "/",
                    exchange -> {
                        try {
                            handler.handle(exchange);
                        } catch (IOException disconnected) {
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
