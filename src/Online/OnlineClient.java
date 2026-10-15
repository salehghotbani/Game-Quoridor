package Online;

import Board.BotPlayer;
import Board.GameConfig;
import Board.GameSession;
import Board.GameState.Orientation;
import Board.SaveStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Synchronous transport. Call from a worker, never from Swing's event dispatch thread. */
public final class OnlineClient implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private static final Duration ENTRY_TIMEOUT = Duration.ofSeconds(90);
    private final URI endpoint;
    private final HttpClient http;
    private String token;
    private String roomCode;
    private int roomPlayerCount;
    private int playingSeat = -1;
    private boolean roomClosed;
    private long observedRevision = -1;
    private boolean closed;

    public OnlineClient(String endpoint) {
        this(URI.create(endpoint));
    }

    public OnlineClient(URI endpoint) {
        this.endpoint = checkedEndpoint(endpoint);
        http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(6))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    public synchronized OnlineState createPrivate(int count, String name) throws IOException {
        return enter("create", Map.of("count", Integer.toString(count), "name", name));
    }

    public synchronized OnlineState joinPrivate(String code, String name) throws IOException {
        return enter(
                "join",
                Map.of("code", code.strip().toUpperCase(java.util.Locale.ROOT), "name", name));
    }

    public synchronized OnlineState findMatch(int count, String name) throws IOException {
        return enter("match", Map.of("count", Integer.toString(count), "name", name));
    }

    public synchronized OnlineState state() throws IOException {
        requireMembership();
        return decodeState(request("state", null, true), false);
    }

    public synchronized OnlineState move(int row, int col, long expectedRevision)
            throws IOException {
        requireMembership();
        return decodeState(
                request(
                        "move",
                        Map.of(
                                "row",
                                Integer.toString(row),
                                "col",
                                Integer.toString(col),
                                "revision",
                                Long.toString(expectedRevision)),
                        true),
                false);
    }

    public synchronized OnlineState wall(
            int row, int col, Orientation orientation, long expectedRevision) throws IOException {
        requireMembership();
        Objects.requireNonNull(orientation);
        return decodeState(
                request(
                        "wall",
                        Map.of(
                                "row",
                                Integer.toString(row),
                                "col",
                                Integer.toString(col),
                                "orientation",
                                orientation.name(),
                                "revision",
                                Long.toString(expectedRevision)),
                        true),
                false);
    }

    public synchronized OnlineState leave() throws IOException {
        requireMembership();
        try {
            return decodeState(request("leave", Map.of(), true), false);
        } finally {
            token = null;
        }
    }

    public synchronized boolean hasMembership() {
        return token != null;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            if (token != null) {
                leave();
            }
        } finally {
            token = null;
            closed = true;
        }
    }

    private OnlineState enter(String action, Map<String, String> fields) throws IOException {
        if (closed || token != null) {
            throw new OnlineException("ALREADY_IN_ROOM", 409, "Leave the current room first");
        }
        Map<String, String> response = request(action, fields, false);
        OnlineState state = decodeState(response, true);
        String credential = response.get("token");
        if (credential == null || !credential.matches("[A-Za-z0-9_-]{43}")) {
            throw new IOException("The server returned an invalid credential");
        }
        token = credential;
        return state;
    }

    private void requireMembership() throws OnlineException {
        if (closed || token == null) {
            throw new OnlineException("NO_MEMBERSHIP", 401, "Join a room first");
        }
    }

    private Map<String, String> request(
            String action, Map<String, String> fields, boolean authenticated) throws IOException {
        Duration timeout = authenticated ? TIMEOUT : ENTRY_TIMEOUT;
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(endpoint.resolve("api/" + action))
                        .timeout(timeout)
                        .header("Accept", Wire.CONTENT_TYPE);
        if (authenticated) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (fields == null) {
            builder.GET();
        } else {
            byte[] body = Wire.encode(fields);
            if (body.length > Wire.REQUEST_LIMIT) {
                throw new IOException("Online request is too large");
            }
            builder.header("Content-Type", Wire.CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        }
        CompletableFuture<HttpResponse<byte[]>> pending =
                http.sendAsync(builder.build(), ignored -> new BoundedBody());
        HttpResponse<byte[]> response;
        try {
            response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Online request was interrupted", interrupted);
        } catch (TimeoutException timedOut) {
            pending.cancel(true);
            throw new IOException("Online server request timed out", timedOut);
        } catch (ExecutionException failed) {
            throw new IOException("Cannot reach the online server", failed.getCause());
        }
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            throw new IOException("Online server redirects are not allowed");
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType
                .toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/x-www-form-urlencoded")) {
            throw new IOException("The server returned an unsupported response");
        }
        Map<String, String> result = Wire.decode(response.body(), Wire.RESPONSE_LIMIT);
        if (response.statusCode() != 200) {
            Wire.requireKeys(result, "error", "message");
            String code = result.get("error");
            if (!code.matches("[A-Z_]{1,40}")) {
                throw new IOException("The server returned an invalid error");
            }
            // Error text is deliberately local and never repeats an untrusted response body.
            throw new OnlineException(
                    code, response.statusCode(), "Online request failed: " + code);
        }
        return result;
    }

    private OnlineState decodeState(Map<String, String> fields, boolean credential)
            throws IOException {
        OnlineState state = readState(fields, credential);
        if (credential) {
            roomCode = state.roomCode();
            roomPlayerCount = state.playerCount();
            playingSeat = -1;
            roomClosed = false;
            observedRevision = -1;
        } else if (!state.roomCode().equals(roomCode)
                || state.playerCount() != roomPlayerCount
                || state.revision() < observedRevision
                || (playingSeat >= 0
                        && (state.playerIndex() != playingSeat
                                || state.status() == OnlineState.Status.WAITING))
                || (roomClosed && state.status() != OnlineState.Status.CLOSED)) {
            throw new IOException("The server returned a stale or different room");
        }
        if (state.session() != null) {
            playingSeat = state.playerIndex();
        }
        observedRevision = state.revision();
        roomClosed = state.status() == OnlineState.Status.CLOSED;
        return state;
    }

    private static OnlineState readState(Map<String, String> fields, boolean credential)
            throws IOException {
        Set<String> expected =
                new HashSet<>(
                        Set.of(
                                "protocol",
                                "room",
                                "status",
                                "player",
                                "count",
                                "revision",
                                "names",
                                "moves",
                                "reason"));
        if (credential) {
            expected.add("token");
        }
        int count = Wire.integer(fields, "count", 2, 4);
        int nameCount = Wire.integer(fields, "names", 1, count);
        int moveCount = Wire.integer(fields, "moves", 0, 1000);
        List<String> names = new ArrayList<>();
        List<String> moves = new ArrayList<>();
        for (int index = 0; index < nameCount; index++) {
            expected.add("name" + index);
            String name = fields.get("name" + index);
            try {
                String checked =
                        new GameConfig(
                                        name,
                                        "Player",
                                        GameConfig.Mode.LOCAL,
                                        BotPlayer.Difficulty.EASY)
                                .blueName();
                if (!checked.equals(name)) {
                    throw new IllegalArgumentException("Unnormalized name");
                }
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw new IOException("The server returned an invalid player name", invalid);
            }
            names.add(name);
        }
        for (int index = 0; index < moveCount; index++) {
            expected.add("move" + index);
            String move = fields.get("move" + index);
            if (move == null
                    || !move.matches("[0-3] (M [0-8] [0-8]|W [0-7] [0-7] (HORIZONTAL|VERTICAL))")
                    || move.charAt(0) - '0' >= count) {
                throw new IOException("The server returned invalid move history");
            }
            moves.add(move);
        }
        GameSession session = null;
        if (fields.containsKey("session")) {
            expected.add("session");
            try {
                byte[] bytes = Base64.getDecoder().decode(fields.get("session"));
                String save =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
                session = SaveStore.decode(save);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("The server returned an invalid game", invalid);
            }
            if (session.config().mode() != GameConfig.Mode.LOCAL
                    || session.game().playerCount() != count
                    || !session.config().playerNames().equals(names)) {
                throw new IOException("The room and game configuration disagree");
            }
        }
        if (!fields.keySet().equals(expected)
                || !"1".equals(fields.get("protocol"))
                || !fields.get("room").matches("[A-Z2-9]{8}")
                || !fields.get("reason").matches("[A-Z_]{0,32}")) {
            throw new IOException("The server returned an invalid room");
        }
        try {
            OnlineState.Status status = OnlineState.Status.valueOf(fields.get("status"));
            if ((status == OnlineState.Status.PLAYING && session == null)
                    || (status == OnlineState.Status.WAITING && session != null)) {
                throw new IOException("The room status and game disagree");
            }
            if ((status == OnlineState.Status.PLAYING
                            && (session.game().winner() >= 0 || nameCount != count))
                    || (status == OnlineState.Status.WAITING
                            && (nameCount >= count || moveCount != 0))
                    || (status != OnlineState.Status.CLOSED && !fields.get("reason").isEmpty())
                    || (status == OnlineState.Status.CLOSED && fields.get("reason").isEmpty())
                    || (fields.get("reason").equals("FINISHED")
                            && (session == null || session.game().winner() < 0))) {
                throw new IOException("The server returned an inconsistent room status");
            }
            return new OnlineState(
                    fields.get("room"),
                    status,
                    Wire.integer(fields, "player", 0, count - 1),
                    count,
                    names,
                    Wire.number(fields, "revision", 0, Long.MAX_VALUE),
                    session,
                    fields.get("reason"),
                    moves);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("The server returned an invalid room", invalid);
        }
    }

    private static URI checkedEndpoint(URI source) {
        Objects.requireNonNull(source);
        if (source.getHost() == null
                || source.getUserInfo() != null
                || source.getQuery() != null
                || source.getFragment() != null
                || !(source.getPath().isEmpty() || source.getPath().equals("/"))) {
            throw new IllegalArgumentException("Use the server's base HTTPS address");
        }
        if (!"https".equalsIgnoreCase(source.getScheme())
                && !("http".equalsIgnoreCase(source.getScheme())
                        && localAddress(source.getHost()))) {
            throw new IllegalArgumentException(
                    "Use HTTPS, or HTTP on a loopback or private network address");
        }
        return URI.create(source.toString().replaceAll("/+$", "") + "/");
    }

    private static boolean localAddress(String host) {
        if (host.equalsIgnoreCase("localhost") || host.equals("::1") || host.equals("[::1]")) {
            return true;
        }
        // No DNS resolution: a remote hostname must use HTTPS even if DNS later changes.
        if (!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            return first == 127
                    || first == 10
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        } catch (IOException invalid) {
            return false;
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > Wire.RESPONSE_LIMIT - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Online response is too large"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
