package Board;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Build metadata copied from the repository's VERSION file. */
public final class AppVersion {
    private static final String RESOURCE = "/app-version.properties";
    private static final String DEVELOPMENT = "development";
    private static final String CURRENT = loadVersion();

    private AppVersion() {}

    public static String current() {
        return CURRENT;
    }

    private static String loadVersion() {
        try (InputStream source = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (source == null) {
                return DEVELOPMENT;
            }
            Properties metadata = new Properties();
            metadata.load(new InputStreamReader(source, StandardCharsets.UTF_8));
            String version = metadata.getProperty("version");
            if (!isWindowsVersion(version)) {
                throw new IllegalStateException("Invalid application version metadata.");
            }
            String manifestVersion = AppVersion.class.getPackage().getImplementationVersion();
            if (manifestVersion != null && !manifestVersion.equals(version)) {
                throw new IllegalStateException(
                        "Application resource and manifest versions disagree.");
            }
            return version;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read application version metadata.", exception);
        }
    }

    private static boolean isWindowsVersion(String version) {
        if (version == null
                || !version.matches(
                        "(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})")) {
            return false;
        }
        String[] parts = version.split("\\.");
        return Integer.parseInt(parts[0]) <= 255
                && Integer.parseInt(parts[1]) <= 255
                && Integer.parseInt(parts[2]) <= 65535;
    }
}
