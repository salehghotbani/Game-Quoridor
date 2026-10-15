package Online;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Real HTTP CORS regressions; browser permission never replaces bearer authorization. */
public final class OnlineCorsTest {
    private static final String ORIGIN = "https://salehghotbani.github.io";
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    // The room server speaks HTTP/1.1. Do not inject h2c upgrade negotiation
                    // into exact early-rejection tests that intentionally never drain a body.
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        configuration();
        defaultDeny();
        explicitAllowlist();
        System.out.println("Online CORS tests passed (" + checks + " checks).");
    }

    private static void configuration() {
        equal(CorsPolicy.parse(null), Set.of(), "Missing configuration denies browser origins");
        equal(CorsPolicy.parse("  "), Set.of(), "Blank configuration denies browser origins");
        equal(
                CorsPolicy.parse(" " + ORIGIN + ", http://localhost:5173 "),
                Set.of(ORIGIN, "http://localhost:5173"),
                "Explicit comma-separated origins");
        for (String invalid :
                new String[] {
                    "*",
                    "null",
                    "https://example.com/",
                    "https://example.com/path",
                    "https://user:secret@example.com",
                    "https://example.com?x=1",
                    "https://example.com#x",
                    "http://example.com",
                    "http://192.168.1.1",
                    "https://EXAMPLE.com",
                    "https://example.com:443",
                    "http://localhost:0",
                    "http://localhost:65536",
                    ORIGIN + ",",
                    ORIGIN + "," + ORIGIN
                }) {
            boolean rejected = false;
            try {
                CorsPolicy.parse(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid origin configuration is rejected");
        }
        new CorsPolicy(
                Set.of("http://127.0.0.1:1234", "http://[::1]:5173", "https://example.com:8443"));
        checks++;
    }

    private static void defaultDeny() throws Exception {
        try (OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.start();
            HttpResponse<String> desktop = send(server, "/health", "GET", null, null, null, null);
            equal(desktop.statusCode(), 200, "Existing desktop health works without Origin");
            equal(header(desktop, "Vary"), "Origin", "Even non-CORS responses vary by origin");
            HttpResponse<String> browser = send(server, "/health", "GET", ORIGIN, null, null, null);
            equal(browser.statusCode(), 403, "No origins allowed by default");
            absent(browser, "Access-Control-Allow-Origin");
            HttpResponse<String> preflight =
                    send(server, "/api/create", "OPTIONS", ORIGIN, "POST", "content-type", null);
            equal(preflight.statusCode(), 403, "Default preflight denied");
            absent(preflight, "Access-Control-Allow-Origin");
        }
    }

    private static void explicitAllowlist() throws Exception {
        OnlineServer.Limits limits =
                new OnlineServer.Limits(
                        1,
                        2,
                        8,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(2));
        try (OnlineServer server =
                new OnlineServer(new InetSocketAddress("127.0.0.1", 0), limits, Set.of(ORIGIN))) {
            server.start();
            HttpResponse<String> health = send(server, "/health", "GET", ORIGIN, null, null, null);
            allowed(health, 200);
            HttpResponse<String> preflight =
                    send(
                            server,
                            "/api/state",
                            "OPTIONS",
                            ORIGIN,
                            "GET",
                            "Authorization, Content-Type",
                            null);
            allowed(preflight, 204);
            equal(
                    header(preflight, "Access-Control-Allow-Methods"),
                    "GET, POST",
                    "Only GET/POST advertised");
            equal(
                    header(preflight, "Access-Control-Allow-Headers"),
                    "Authorization, Content-Type",
                    "Exact supported headers");
            equal(preflight.body(), "", "Preflight has no room/token body");
            allowed(
                    send(server, "/api/create", "OPTIONS", ORIGIN, "POST", "content-type", null),
                    204);
            allowed(send(server, "/health", "OPTIONS", ORIGIN, "GET", null, null), 204);
            allowed(send(server, "/api/state", "GET", ORIGIN, null, null, null), 401);
            allowed(send(server, "/missing", "GET", ORIGIN, null, null, null), 404);
            allowed(send(server, "/health?token=invalid", "GET", ORIGIN, null, null, null), 400);
            for (String badOrigin :
                    new String[] {
                        "null",
                        "https://evil.example",
                        ORIGIN + ".evil.example",
                        ORIGIN + "/path",
                        ORIGIN + "/"
                    }) {
                HttpResponse<String> rejected =
                        send(server, "/api/create", "POST", badOrigin, null, null, null);
                equal(rejected.statusCode(), 403, "Unknown origin cannot allocate a room");
                absent(rejected, "Access-Control-Allow-Origin");
            }
            rejectedBodyDoesNotAllocate(server);
            HttpResponse<String> duplicateOrigin =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server, "/health"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Origin", ORIGIN)
                                    .header("Origin", "https://evil.example")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            equal(duplicateOrigin.statusCode(), 403, "Multiple origins rejected");
            absent(duplicateOrigin, "Access-Control-Allow-Origin");
            for (String badHeaders :
                    new String[] {
                        "x-custom",
                        "authorization, x-custom",
                        "authorization,authorization",
                        "",
                        "content-type,"
                    }) {
                HttpResponse<String> rejected =
                        send(server, "/api/move", "OPTIONS", ORIGIN, "POST", badHeaders, null);
                allowed(rejected, 403);
                absent(rejected, "Access-Control-Allow-Methods");
            }
            allowed(
                    send(server, "/api/move", "OPTIONS", ORIGIN, "DELETE", "authorization", null),
                    403);
            allowed(
                    send(server, "/api/state", "OPTIONS", ORIGIN, "POST", "authorization", null),
                    403);
            HttpResponse<String> missingOrigin =
                    send(server, "/api/move", "OPTIONS", null, "POST", "authorization", null);
            equal(
                    missingOrigin.statusCode(),
                    403,
                    "Preflight requires an explicitly allowed origin");
            absent(missingOrigin, "Access-Control-Allow-Origin");

            HttpResponse<String> created =
                    send(server, "/api/create", "POST", ORIGIN, null, null, "count=2&name=Blue");
            allowed(created, 200);
            Map<String, String> fields = form(created.body());
            String token = fields.get("token");
            check(
                    token != null && token.matches("[A-Za-z0-9_-]{43}"),
                    "Actual entry still returns bearer credential");
            HttpResponse<String> cookieOnly =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server, "/api/state"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Origin", ORIGIN)
                                    .header("Cookie", "token=" + token)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            allowed(cookieOnly, 401);
            HttpResponse<String> state =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server, "/api/state"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Origin", ORIGIN)
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            allowed(state, 200);
            check(!state.body().contains(token), "Public state excludes credential");
            HttpResponse<String> wrongOriginToken =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server, "/api/state"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Origin", "https://evil.example")
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            equal(wrongOriginToken.statusCode(), 403, "Valid token does not bypass origin policy");
            absent(wrongOriginToken, "Access-Control-Allow-Origin");
            allowed(
                    send(server, "/api/create", "POST", ORIGIN, null, null, "count=2&name=Other"),
                    503);
            HttpResponse<String> desktop =
                    HTTP.send(
                            HttpRequest.newBuilder(endpoint(server, "/api/state"))
                                    .timeout(Duration.ofSeconds(3))
                                    .header("Authorization", "Bearer " + token)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            equal(desktop.statusCode(), 200, "Desktop bearer access stays compatible");
            absent(desktop, "Access-Control-Allow-Origin");
        }
    }

    private static HttpResponse<String> send(
            OnlineServer server,
            String path,
            String method,
            String origin,
            String requestedMethod,
            String requestedHeaders,
            String body)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(endpoint(server, path)).timeout(Duration.ofSeconds(3));
        if (origin != null) request.header("Origin", origin);
        if (requestedMethod != null)
            request.header("Access-Control-Request-Method", requestedMethod);
        if (requestedHeaders != null)
            request.header("Access-Control-Request-Headers", requestedHeaders);
        if (body != null)
            request.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void rejectedBodyDoesNotAllocate(OnlineServer server) throws Exception {
        // Reject before reading an untrusted body. With drainAmount=0 the JDK may reset a
        // connection with unread bytes; a browser cannot read a denied origin's response.
        // The header-only checks above require HTTP 403; this separate raw socket probe
        // permits only that response or a verified TCP reset, never an arbitrary I/O error.
        byte[] body = "count=2&name=Rejected".getBytes(StandardCharsets.US_ASCII);
        String headers =
                "POST /api/create HTTP/1.1\r\nHost: 127.0.0.1:"
                        + server.port()
                        + "\r\nOrigin: https://evil.example\r\n"
                        + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                        + body.length
                        + "\r\nConnection: close\r\n\r\n";
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            try {
                String reply =
                        new String(
                                socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                check(
                        reply.startsWith("HTTP/1.1 403 "),
                        "Fully sent disallowed-origin POST is rejected");
                check(
                        !reply.toLowerCase(java.util.Locale.ROOT)
                                .contains("access-control-allow-origin:"),
                        "Rejected body response never grants origin access");
            } catch (SocketException reset) {
                check(
                        reset.getMessage() != null
                                && reset.getMessage()
                                        .toLowerCase(java.util.Locale.ROOT)
                                        .contains("connection reset"),
                        "Only verified unread-body TCP reset is accepted");
            }
        }
        allowed(send(server, "/health", "GET", ORIGIN, null, null, null), 200);
        // The later sole-room create succeeds only if all rejected requests allocated no room.
    }

    private static URI endpoint(OnlineServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] field = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(field[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private static void allowed(HttpResponse<String> response, int status) {
        equal(response.version(), HttpClient.Version.HTTP_1_1, "CORS fixture uses HTTP/1.1");
        equal(response.statusCode(), status, "Expected HTTP status");
        equal(
                header(response, "Access-Control-Allow-Origin"),
                ORIGIN,
                "Exact configured origin only");
        equal(header(response, "Vary"), "Origin", "CORS cache varies by origin");
        absent(response, "Access-Control-Allow-Credentials");
        absent(response, "Set-Cookie");
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static void absent(HttpResponse<String> response, String name) {
        equal(header(response, name), null, "Header absent: " + name);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(
                java.util.Objects.equals(actual, expected),
                message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
