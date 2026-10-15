package Board;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Per-window English/Persian text. English source templates are the translation keys. */
public final class I18n {
    public enum Language {
        ENGLISH(Locale.ENGLISH, false, "English"),
        PERSIAN(Locale.forLanguageTag("fa-IR"), true, "فارسی");

        private final Locale locale;
        private final boolean rightToLeft;
        private final String nativeName;

        Language(Locale locale, boolean rightToLeft, String nativeName) {
            this.locale = locale;
            this.rightToLeft = rightToLeft;
            this.nativeName = nativeName;
        }

        public Locale locale() {
            return locale;
        }

        public boolean rightToLeft() {
            return rightToLeft;
        }

        @Override
        public String toString() {
            return nativeName;
        }
    }

    private static final Map<String, String> PERSIAN = TranslationCatalog.persian();
    private final Language language;

    public I18n(Language language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    public Language language() {
        return language;
    }

    public Locale locale() {
        return language.locale();
    }

    public boolean rightToLeft() {
        return language.rightToLeft();
    }

    public static Language fromLocale(Locale locale) {
        return locale != null && locale.getLanguage().equalsIgnoreCase("fa")
                ? Language.PERSIAN
                : Language.ENGLISH;
    }

    public static Language[] supportedLanguages() {
        return Language.values();
    }

    /** Formats only a catalogued template; arguments such as player names stay verbatim. */
    public String text(String englishTemplate, Object... arguments) {
        String translated = PERSIAN.get(englishTemplate);
        if (translated == null) {
            throw new IllegalArgumentException("Unknown translation key: " + englishTemplate);
        }
        if (arguments == null
                || arguments.length != TranslationFormat.argumentCount(englishTemplate)) {
            throw new IllegalArgumentException(
                    "Wrong number of translation arguments for: " + englishTemplate);
        }
        return String.format(
                locale(), language == Language.PERSIAN ? translated : englishTemplate, arguments);
    }
}
