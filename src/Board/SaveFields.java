package Board;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;

/** Strict scalar parsing shared by the two supported text save formats. */
final class SaveFields {
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");

    private SaveFields() {}

    static String required(Properties data, String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null) {
            throw new IOException("Saved game is missing field: " + key);
        }
        return value;
    }

    static int integer(Properties data, String key) throws IOException {
        return integer(required(data, key), key);
    }

    static int integer(String value, String key) throws IOException {
        if (!INTEGER.matcher(value).matches()) {
            throw new IOException("Saved game has an invalid number: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IOException("Saved game number is out of range: " + key, invalid);
        }
    }

    static boolean legacyBoolean(String value) throws IOException {
        if (!value.equals("true") && !value.equals("false")) {
            throw new IOException("Legacy saved game has an invalid boolean");
        }
        return value.equals("true");
    }
}
