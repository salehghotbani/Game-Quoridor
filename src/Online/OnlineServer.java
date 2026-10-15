package Online;

import Board.SaveStore;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Authoritative, in-memory HTTP room server, suitable for a TLS-terminating hosting proxy. */
public final class OnlineServer implements AutoCloseable {
    public record Limits(
            int maxRooms,
            int workerThreads,
            int queuedRequests,
            Duration idleTimeout,
            Duration waitingTimeout,
            Duration closedRetention) {
        public Limits {
            if (maxRooms < 1
                    || maxRooms > 4096
                    || workerThreads < 1
                    || workerThreads > 64
                    || queuedRequests < 1
                    || queuedRequests > 1024) {
                throw new IllegalArgumentException("Invalid server capacity");
            }
            for (Duration duration : List.of(idleTimeout, waitingTimeout, closedRetention)) {
                if (duration.isNegative()
                        || duration.isZero()
                        || duration.compareTo(Duration.ofDays(1)) > 0) {
                    throw new IllegalArgumentException("Invalid room timeout");
                }
            }
        }

        public static Limits defaults() {
            return new Limits(
                    128,
                    8,
                    64,
                    Duration.ofMinutes(2),
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(2));
        }
    }

    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService maintenance;
    private final RoomRegistry rooms;
    private final CorsPolicy cors;

    public OnlineServer(InetSocketAddress address) throws IOException {
        this(address, Limits.defaults());
    }

    public OnlineServer(InetSocketAddress address, Limits limits) throws IOException {
        this(address, limits, Set.of());
    }

    public OnlineServer(InetSocketAddress address, Limits limits, Set<String> allowedOrigins)
            throws IOException {
        cors = new CorsPolicy(allowedOrigins);
        configureHttpLimits();
        rooms = new RoomRegistry(limits);
        server = HttpServer.create(address, limits.queuedRequests());
        workers =
                new ThreadPoolExecutor(
                        limits.workerThreads(),
                        limits.workerThreads(),
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(limits.queuedRequests()),
                        task -> {
                            Thread thread = new Thread(task, "quoridor-http");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        maintenance =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "quoridor-room-cleanup");
                            thread.setDaemon(true);
                            return thread;
                        });
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
        maintenance.scheduleWithFixedDelay(rooms::cleanup, 1, 1, TimeUnit.SECONDS);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        maintenance.shutdownNow();
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        try {
            cors.prepare(exchange);
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestURI().getRawQuery() != null) {
                throw failure("INVALID_REQUEST", 400);
            }
            if (path.equals("/health")) {
                if (exchange.getRequestMethod().equals("OPTIONS")) {
                    cors.preflight(exchange, "GET");
                    return;
                }
                if (!exchange.getRequestMethod().equals("GET")) {
                    throw failure("METHOD_NOT_ALLOWED", 405);
                }
                respond(exchange, 200, Map.of("status", "ok", "protocol", "1"));
                return;
            }
            String action = path.startsWith("/api/") ? path.substring(5) : "";
            if (!List.of("create", "join", "match", "state", "move", "wall", "leave")
                    .contains(action)) {
                throw failure("NOT_FOUND", 404);
            }
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                cors.preflight(exchange, action.equals("state") ? "GET" : "POST");
                return;
            }
            if (!exchange.getRequestMethod().equals(action.equals("state") ? "GET" : "POST")) {
                throw failure("METHOD_NOT_ALLOWED", 405);
            }
            Map<String, String> fields;
            if (action.equals("state")) {
                fields = Map.of();
            } else {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                if (type == null
                        || !type.toLowerCase(Locale.ROOT)
                                .startsWith("application/x-www-form-urlencoded")) {
                    throw failure("INVALID_REQUEST", 400);
                }
                String length = exchange.getRequestHeaders().getFirst("Content-Length");
                if (length != null
                        && (!length.matches("[0-9]{1,9}")
                                || Long.parseLong(length) > Wire.REQUEST_LIMIT)) {
                    throw failure("BODY_TOO_LARGE", 413);
                }
                byte[] body = exchange.getRequestBody().readNBytes(Wire.REQUEST_LIMIT + 1);
                if (body.length > Wire.REQUEST_LIMIT) {
                    throw failure("BODY_TOO_LARGE", 413);
                }
                fields = Wire.decode(body, Wire.REQUEST_LIMIT);
            }
            RoomRegistry.Reply reply = rooms.handle(action, fields, credential(exchange));
            respond(exchange, 200, encode(reply));
        } catch (OnlineException rejected) {
            reject(exchange, rejected.httpStatus(), rejected.code());
        } catch (IOException | IllegalArgumentException invalid) {
            reject(exchange, 400, "INVALID_REQUEST");
        } catch (RuntimeException unexpected) {
            reject(exchange, 500, "SERVER_ERROR");
        } finally {
            exchange.close();
        }
    }

    private static String credential(HttpExchange exchange) throws OnlineException {
        List<String> values = exchange.getRequestHeaders().get("Authorization");
        if (values == null) {
            return null;
        }
        if (values.size() != 1 || !values.get(0).matches("Bearer [A-Za-z0-9_-]{43}")) {
            throw failure("UNAUTHORIZED", 401);
        }
        return values.get(0).substring(7);
    }

    private static Map<String, String> encode(RoomRegistry.Reply reply) throws IOException {
        OnlineState state = reply.state();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("protocol", "1");
        fields.put("room", state.roomCode());
        fields.put("status", state.status().name());
        fields.put("player", Integer.toString(state.playerIndex()));
        fields.put("count", Integer.toString(state.playerCount()));
        fields.put("revision", Long.toString(state.revision()));
        fields.put("names", Integer.toString(state.playerNames().size()));
        for (int index = 0; index < state.playerNames().size(); index++) {
            fields.put("name" + index, state.playerNames().get(index));
        }
        fields.put("reason", state.reason());
        fields.put("moves", Integer.toString(state.moves().size()));
        for (int index = 0; index < state.moves().size(); index++) {
            fields.put("move" + index, state.moves().get(index));
        }
        if (state.session() != null) {
            fields.put(
                    "session",
                    Base64.getEncoder()
                            .encodeToString(
                                    SaveStore.encode(state.session())
                                            .getBytes(StandardCharsets.UTF_8)));
        }
        if (reply.credential() != null) {
            fields.put("token", reply.credential());
        }
        return fields;
    }

    private static void reject(HttpExchange exchange, int status, String code) {
        try {
            respond(exchange, status, Map.of("error", code, "message", code));
        } catch (IOException disconnected) {
            // No request details, credentials or player data are written to server logs.
        }
    }

    private static void respond(HttpExchange exchange, int status, Map<String, String> fields)
            throws IOException {
        byte[] body = Wire.encode(fields);
        if (body.length > Wire.RESPONSE_LIMIT) {
            throw new IOException("Online response exceeds its limit");
        }
        exchange.getResponseHeaders().set("Content-Type", Wire.CONTENT_TYPE);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static OnlineException failure(String code, int status) {
        return new OnlineException(code, status, code);
    }

    private static void configureHttpLimits() {
        // OpenJDK 17 converts these request/response values from seconds internally.
        Map.of(
                        "sun.net.httpserver.maxReqTime",
                        "10",
                        "sun.net.httpserver.maxRspTime",
                        "10",
                        "sun.net.httpserver.idleInterval",
                        "15",
                        "sun.net.httpserver.maxIdleConnections",
                        "16",
                        "sun.net.httpserver.maxReqHeaders",
                        "24",
                        "sun.net.httpserver.maxReqHeaderSize",
                        "8192",
                        "sun.net.httpserver.drainAmount",
                        "0",
                        "jdk.httpserver.maxConnections",
                        "128",
                        "sun.net.httpserver.nodelay",
                        "true")
                .forEach(
                        (key, value) -> {
                            if (System.getProperty(key) == null) {
                                System.setProperty(key, value);
                            }
                        });
    }
}
