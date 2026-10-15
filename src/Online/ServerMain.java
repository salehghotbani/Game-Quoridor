package Online;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

/** Server entry point. Rooms live in memory and are discarded when the service restarts. */
public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] arguments) throws Exception {
        String value = System.getenv().getOrDefault("PORT", "8080");
        if (!value.matches("[0-9]{1,5}")
                || Integer.parseInt(value) < 1
                || Integer.parseInt(value) > 65535) {
            throw new IllegalArgumentException("PORT must be between 1 and 65535");
        }
        OnlineServer server =
                new OnlineServer(new InetSocketAddress("0.0.0.0", Integer.parseInt(value)));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "quoridor-shutdown"));
        server.start();
        System.out.println("Quoridor room server listening on port " + server.port());
        new CountDownLatch(1).await();
    }
}
