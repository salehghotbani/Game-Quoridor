import Online.OnlineServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loopback-only test server. Closing the owning process's stdin shuts it down cleanly. */
public final class AndroidInteropServer {
    private AndroidInteropServer() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected an owned port-file path");
        }
        try (OnlineServer server = new OnlineServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.start();
            Files.writeString(
                    Path.of(arguments[0]),
                    Integer.toString(server.port()),
                    StandardCharsets.US_ASCII);
            while (true) {
                int input = System.in.read();
                if (input == -1 || input == '\n') {
                    break;
                }
            }
        }
    }
}
