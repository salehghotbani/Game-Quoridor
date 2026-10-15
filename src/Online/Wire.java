package Online;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Small bounded form protocol shared by the server and desktop transport. */
final class Wire {
    static final int REQUEST_LIMIT = 2048;
    static final int RESPONSE_LIMIT = 192 * 1024;
    static final String CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    private Wire() {}

    static byte[] encode(Map<String, String> fields) {
        StringBuilder text = new StringBuilder();
        fields.forEach(
                (key, value) -> {
                    if (text.length() != 0) {
                        text.append('&');
                    }
                    text.append(encodePart(key));
                    text.append('=');
                    text.append(encodePart(value));
                });
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String encodePart(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError("UTF-8 is required by every Java runtime", impossible);
        }
    }

    static Map<String, String> decode(byte[] bytes, int limit) throws IOException {
        if (bytes.length > limit) {
            throw new IOException("Protocol body is too large");
        }
        for (byte value : bytes) {
            if (value < 0) {
                throw new IOException("Protocol fields must use percent-encoded UTF-8");
            }
        }
        String text = new String(bytes, StandardCharsets.US_ASCII);
        Map<String, String> fields = new LinkedHashMap<>();
        if (text.isEmpty()) {
            return fields;
        }
        for (String pair : text.split("&", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || fields.size() >= 1100) {
                throw new IOException("Invalid protocol field");
            }
            String key = decodePart(pair.substring(0, separator));
            String value = decodePart(pair.substring(separator + 1));
            if (!key.matches("[A-Za-z][A-Za-z0-9]*") || fields.putIfAbsent(key, value) != null) {
                throw new IOException("Invalid or duplicate protocol field");
            }
        }
        return fields;
    }

    static void requireKeys(Map<String, String> fields, String... keys) throws IOException {
        if (!fields.keySet().equals(Set.of(keys))) {
            throw new IOException("Missing or unexpected protocol fields");
        }
    }

    static int integer(Map<String, String> fields, String key, int min, int max)
            throws IOException {
        long value = number(fields, key, min, max);
        return (int) value;
    }

    static long number(Map<String, String> fields, String key, long min, long max)
            throws IOException {
        String value = fields.get(key);
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) {
            throw new IOException("Invalid numeric protocol field");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                throw new IOException("Numeric protocol field is out of range");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid numeric protocol field", invalid);
        }
    }

    private static String decodePart(String part) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(part.length());
        for (int index = 0; index < part.length(); index++) {
            char next = part.charAt(index);
            if (next == '%') {
                if (index + 2 >= part.length()) {
                    throw new IOException("Incomplete percent encoding");
                }
                int high = Character.digit(part.charAt(++index), 16);
                int low = Character.digit(part.charAt(++index), 16);
                if (high < 0 || low < 0) {
                    throw new IOException("Invalid percent encoding");
                }
                bytes.write(high * 16 + low);
            } else {
                bytes.write(next == '+' ? ' ' : next);
            }
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Invalid protocol UTF-8", invalid);
        }
    }
}
