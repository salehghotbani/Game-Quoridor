package Board;

/** Formats translated UI text while isolating user-provided bidirectional content. */
final class UiText {
    private static final String FIRST_STRONG_ISOLATE = "\u2068";
    private static final String POP_DIRECTIONAL_ISOLATE = "\u2069";

    private UiText() {}

    static String format(I18n translation, String template, Object... arguments) {
        Object[] formatted = arguments.clone();
        if (translation.rightToLeft()) {
            for (int index = 0; index < formatted.length; index++) {
                if (formatted[index] instanceof String text) {
                    formatted[index] = FIRST_STRONG_ISOLATE + text + POP_DIRECTIONAL_ISOLATE;
                }
            }
        }
        return translation.text(template, formatted);
    }
}
