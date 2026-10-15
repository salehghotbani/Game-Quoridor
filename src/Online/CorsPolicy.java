package Online;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** An explicit origin allowlist; desktop requests without Origin keep their existing behavior. */
final class CorsPolicy {
    private static final int MAX_ORIGINS = 32;
    private final Set<String> origins;

    CorsPolicy(Set<String> origins) {
        if (origins == null || origins.size() > MAX_ORIGINS) {
            throw new IllegalArgumentException("At most 32 allowed origins may be configured");
        }
        for (String origin : origins) {
            checkOrigin(origin);
        }
        this.origins = Set.copyOf(origins);
    }

    static Set<String> parse(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return Set.of();
        }
        if (configuration.length() > 4096) {
            throw new IllegalArgumentException("Allowed origins configuration is too large");
        }
        Set<String> result = new HashSet<>();
        for (String part : configuration.split(",", -1)) {
            String origin = part.strip();
            checkOrigin(origin);
            if (!result.add(origin) || result.size() > MAX_ORIGINS) {
                throw new IllegalArgumentException("Allowed origins must be unique (maximum 32)");
            }
        }
        return Set.copyOf(result);
    }

    void prepare(HttpExchange exchange) throws OnlineException {
        exchange.getResponseHeaders().set("Vary", "Origin");
        List<String> values = exchange.getRequestHeaders().get("Origin");
        if (values == null) {
            return;
        }
        if (values.size() != 1 || !origins.contains(values.get(0))) {
            throw rejected();
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", values.get(0));
    }

    void preflight(HttpExchange exchange, String expectedMethod) throws IOException {
        List<String> requested = exchange.getRequestHeaders().get("Access-Control-Request-Method");
        if (exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin") == null
                || requested == null
                || requested.size() != 1
                || !expectedMethod.equals(requested.get(0))) {
            throw rejected();
        }
        List<String> headers = exchange.getRequestHeaders().get("Access-Control-Request-Headers");
        if (headers != null) {
            if (headers.size() != 1 || headers.get(0).length() > 128) {
                throw rejected();
            }
            Set<String> seen = new HashSet<>();
            for (String header : headers.get(0).split(",", -1)) {
                String name = header.strip().toLowerCase(Locale.ROOT);
                if (!Set.of("authorization", "content-type").contains(name) || !seen.add(name)) {
                    throw rejected();
                }
            }
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST");
        exchange.getResponseHeaders()
                .set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void checkOrigin(String value) {
        try {
            URI origin = URI.create(value);
            String scheme = origin.getScheme();
            String host = origin.getHost();
            int port = origin.getPort();
            if (host == null
                    || origin.getUserInfo() != null
                    || origin.getRawQuery() != null
                    || origin.getRawFragment() != null
                    || !origin.getRawPath().isEmpty()
                    || (port != -1 && (port < 1 || port > 65535))
                    || !("https".equals(scheme) || ("http".equals(scheme) && loopback(host)))) {
                throw new IllegalArgumentException();
            }
            String canonical =
                    scheme
                            + "://"
                            + host.toLowerCase(Locale.ROOT)
                            + ((port == -1
                                            || (scheme.equals("https") && port == 443)
                                            || (scheme.equals("http") && port == 80))
                                    ? ""
                                    : ":" + port);
            if (!canonical.equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException(
                    "Use an exact HTTPS origin, or loopback HTTP origin, without a path", invalid);
        }
    }

    private static boolean loopback(String host) {
        if (host.equals("localhost") || host.equals("[::1]")) {
            return true;
        }
        if (!host.matches("127(\\.[0-9]{1,3}){3}")) {
            return false;
        }
        for (String part : host.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static OnlineException rejected() {
        return new OnlineException("ORIGIN_NOT_ALLOWED", 403, "Origin or preflight is not allowed");
    }
}
