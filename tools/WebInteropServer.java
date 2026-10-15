import Online.OnlineServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

/** Isolated browser interoperability server; the owning test process closes its stdin. */
public final class WebInteropServer {
    private WebInteropServer() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 1) {
            throw new IllegalArgumentException("Expected a port file and optional browser origins");
        }
        Set<String> origins = Set.copyOf(Arrays.asList(arguments).subList(1, arguments.length));
        try (OnlineServer server =
                new OnlineServer(
                        new InetSocketAddress("127.0.0.1", 0),
                        OnlineServer.Limits.defaults(),
                        origins)) {
            server.start();
            String endpoint = "http://127.0.0.1:" + server.port();
            Path endpointFile = Path.of(arguments[0]);
            Path pendingFile = endpointFile.resolveSibling(endpointFile.getFileName() + ".pending");
            Files.writeString(pendingFile, endpoint, StandardCharsets.US_ASCII);
            Files.move(
                    pendingFile,
                    endpointFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("ENDPOINT=" + endpoint);
            while (true) {
                int input = System.in.read();
                if (input == -1 || input == '\n') {
                    break;
                }
            }
        }
    }
}
