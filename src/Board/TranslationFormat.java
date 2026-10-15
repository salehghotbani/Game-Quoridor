package Board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates printf arguments and the numbered placeholders Swing formats itself. */
final class TranslationFormat {
    private static final Pattern PRINTF_ARGUMENT =
            Pattern.compile("%(?:(\\d+)\\$)?([-#+ 0,(<]*)(?:\\d+)?(?:\\.\\d+)?([tT])?([a-zA-Z%])");
    private static final Pattern MESSAGE_ARGUMENT = Pattern.compile("\\{\\d+(?:,[^}]*)?}");

    private TranslationFormat() {}

    static int argumentCount(String template) {
        return signature(template).stream().mapToInt(Placeholder::index).max().orElse(0);
    }

    static boolean sameArguments(String source, String translated) {
        return signature(source).equals(signature(translated))
                && messageArguments(source).equals(messageArguments(translated));
    }

    private static List<String> messageArguments(String template) {
        // Swing consumes these templates itself; retain its numbered slots verbatim.
        return MESSAGE_ARGUMENT
                .matcher(template)
                .results()
                .map(match -> match.group())
                .sorted()
                .toList();
    }

    private static List<Placeholder> signature(String template) {
        List<Placeholder> placeholders = new ArrayList<>();
        Matcher matcher = PRINTF_ARGUMENT.matcher(template);
        int ordinaryIndex = 0;
        int previousIndex = 0;
        int consumed = 0;
        while (matcher.find()) {
            rejectUnmatchedPercent(template, consumed, matcher.start());
            consumed = matcher.end();
            String conversion = matcher.group(4);
            if (conversion.equals("%") || conversion.equals("n")) {
                continue;
            }
            int index;
            if (matcher.group(2).indexOf('<') >= 0) {
                index = previousIndex;
            } else if (matcher.group(1) != null) {
                index = Integer.parseInt(matcher.group(1));
            } else {
                index = ++ordinaryIndex;
            }
            if (index < 1) {
                throw new IllegalArgumentException(
                        "Invalid translation argument index: " + template);
            }
            previousIndex = index;
            String datePrefix = matcher.group(3) == null ? "" : matcher.group(3);
            placeholders.add(new Placeholder(index, datePrefix + conversion));
        }
        rejectUnmatchedPercent(template, consumed, template.length());
        placeholders.sort(
                Comparator.comparingInt(Placeholder::index).thenComparing(Placeholder::conversion));
        return placeholders;
    }

    private static void rejectUnmatchedPercent(String template, int start, int end) {
        if (template.substring(start, end).indexOf('%') >= 0) {
            throw new IllegalArgumentException("Malformed translation format: " + template);
        }
    }

    private record Placeholder(int index, String conversion) {}
}
