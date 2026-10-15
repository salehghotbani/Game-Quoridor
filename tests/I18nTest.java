import Board.I18n;
import Board.I18n.Language;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catalog, formatting, language isolation, and source-key completeness tests. */
public final class I18nTest {
    private static final Pattern ARGUMENT = Pattern.compile("%([sd])");
    private static int checks;

    private I18nTest() {}

    public static void main(String[] args) throws Exception {
        languageContract();
        completeCatalog();
        formattingAndNames();
        invalidRequests();
        sourceKeys();
        System.out.println("I18n tests passed (" + checks + " checks).");
    }

    private static void languageContract() {
        equal(
                Arrays.asList(I18n.supportedLanguages()),
                List.of(Language.ENGLISH, Language.PERSIAN),
                "Exactly English and Persian are supported");
        Language[] modified = I18n.supportedLanguages();
        modified[0] = Language.PERSIAN;
        equal(
                I18n.supportedLanguages()[0],
                Language.ENGLISH,
                "Supported-language array is defensive");
        equal(Language.ENGLISH.toString(), "English", "English has its native display name");
        equal(Language.PERSIAN.toString(), "فارسی", "Persian has its native display name");
        check(!Language.ENGLISH.rightToLeft(), "English is left to right");
        check(Language.PERSIAN.rightToLeft(), "Persian is right to left");
        for (String tag : List.of("fa", "fa-IR", "fa-AF", "fa-IR-u-nu-arabext")) {
            equal(
                    I18n.fromLocale(Locale.forLanguageTag(tag)),
                    Language.PERSIAN,
                    "Persian locale selected: " + tag);
        }
        for (Locale locale :
                List.of(
                        Locale.ENGLISH,
                        Locale.ROOT,
                        Locale.forLanguageTag("he"),
                        Locale.forLanguageTag("he-IL"),
                        new Locale("iw", "IL"),
                        Locale.forLanguageTag("ar"),
                        Locale.forLanguageTag("de-DE"))) {
            equal(
                    I18n.fromLocale(locale),
                    Language.ENGLISH,
                    "Other locales fall back to English: " + locale);
        }
        equal(I18n.fromLocale(null), Language.ENGLISH, "Missing locale falls back to English");
        Locale before = Locale.getDefault();
        I18n english = new I18n(Language.ENGLISH);
        I18n persian = new I18n(Language.PERSIAN);
        equal(english.language(), Language.ENGLISH, "Instance retains its selected language");
        equal(persian.locale().getLanguage(), "fa", "Persian formatting locale");
        equal(english.locale().getLanguage(), "en", "English formatting locale");
        check(persian.rightToLeft() && !english.rightToLeft(), "Directions are per instance");
        equal(
                english.text("Save"),
                "Save",
                "Creating a Persian instance cannot change English output");
        equal(persian.text("Save"), "ذخیره", "Persian output remains independent");
        equal(Locale.getDefault(), before, "Localization cannot change global locale");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> catalog() throws Exception {
        Field field = I18n.class.getDeclaredField("PERSIAN");
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }

    private static void completeCatalog() throws Exception {
        Map<String, String> catalog = catalog();
        check(catalog.size() >= 100, "Catalog covers the application and standard dialogs");
        Set<String> required =
                Set.of(
                        "Language",
                        "Appearance",
                        "Dark",
                        "Light",
                        "English",
                        "فارسی",
                        "QUORIDOR",
                        "Two players",
                        "Play a robot",
                        "BLUE PLAYER",
                        "GREEN PLAYER",
                        "Start a new match  →",
                        "Resume your match",
                        "Load a saved match",
                        "Save",
                        "Load",
                        "Main menu",
                        "New game",
                        "Horizontal",
                        "Vertical",
                        "Easy — a relaxed challenge",
                        "Medium — a worthy opponent",
                        "Hard — think a few moves ahead",
                        "%s to move",
                        "%s wins!",
                        "%s is thinking…",
                        "%d walls remaining",
                        "%s pawn",
                        "%s reached the far edge. Choose New game for another match.",
                        "Match saved to %s. You can continue playing.",
                        "Replace the saved match “%s”?",
                        "Place %s wall (%d, %d)",
                        "%s (%d, %d)",
                        "BLUE'S GOAL",
                        "GREEN'S GOAL",
                        "Goal: top edge ↑",
                        "Goal: bottom edge ↓",
                        "Blue player name",
                        "Green player name",
                        "Robot difficulty",
                        "File name:",
                        "Files of type:",
                        "Look in:",
                        "Save in:",
                        "All files",
                        "File chooser",
                        "Files List",
                        "Files Details",
                        "Up one level",
                        "Home",
                        "Create new folder",
                        "List",
                        "Details",
                        "Name",
                        "Size",
                        "Type",
                        "Date modified",
                        "Folder",
                        "New folder",
                        "OK",
                        "Yes",
                        "No",
                        "Cancel",
                        "Open",
                        "Save your match",
                        "Replace saved match",
                        "Couldn't save this match",
                        "Couldn't load this match",
                        "Your current match is still available.",
                        "Update",
                        "File chooser help",
                        "Open selected directory",
                        "Modified",
                        "Attributes",
                        "Cannot rename {0}",
                        "Error Renaming File or Folder",
                        "Error creating new folder");
        check(
                catalog.keySet().containsAll(required),
                "Every required UI and dialog key is catalogued");
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            check(
                    !entry.getKey().isBlank() && !entry.getValue().isBlank(),
                    "Catalog entries cannot be blank");
            check(
                    entry.getValue()
                            .codePoints()
                            .noneMatch(codePoint -> codePoint >= 0x0590 && codePoint <= 0x05FF),
                    "Catalog contains no unsupported-language resources");
            Object[] arguments = sampleArguments(entry.getKey());
            String english = new I18n(Language.ENGLISH).text(entry.getKey(), arguments);
            String persian = new I18n(Language.PERSIAN).text(entry.getKey(), arguments);
            check(
                    !english.isBlank() && !persian.isBlank(),
                    "Every catalog template formats in both languages");
            equal(
                    english,
                    String.format(Locale.ENGLISH, entry.getKey(), arguments),
                    "English retains original source wording");
            equal(
                    formatSlots(entry.getKey()),
                    formatSlots(entry.getValue()),
                    "Placeholder semantics match");
            equal(
                    braceSlots(entry.getKey()),
                    braceSlots(entry.getValue()),
                    "Swing numbered placeholders match");
        }
        boolean immutable = false;
        try {
            catalog.put("Unexpected", "ناخواسته");
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "Catalog cannot be modified");
    }

    private static Object[] sampleArguments(String key) {
        List<Object> arguments = new ArrayList<>();
        Matcher matcher = ARGUMENT.matcher(key);
        while (matcher.find()) {
            arguments.add(matcher.group(1).equals("d") ? 12 : "Ali% O'Brien علی 😀");
        }
        return arguments.toArray();
    }

    private static List<String> formatSlots(String key) {
        return ARGUMENT.matcher(key).results().map(match -> match.group()).toList();
    }

    private static List<String> braceSlots(String key) {
        return Pattern.compile("\\{\\d+(?:,[^}]*)?}")
                .matcher(key)
                .results()
                .map(match -> match.group())
                .sorted()
                .toList();
    }

    private static void formattingAndNames() {
        String name = "علی O'Brien 100% 😀";
        String file = "بازی O'Brien 100%.qsave";
        for (Language language : I18n.supportedLanguages()) {
            I18n text = new I18n(language);
            check(
                    text.text("%s wins!", name).contains(name),
                    "Names retain Unicode, apostrophes, and percent signs");
            check(text.text("%s pawn", name).contains(name), "Pawn label preserves the exact name");
            check(
                    text.text("Replace the saved match “%s”?", file).contains(file),
                    "Overwrite dialog preserves filename");
            check(
                    text.text("Match saved to %s. You can continue playing.", file).contains(file),
                    "Save feedback preserves filename");
            String coordinates = text.text("Place %s wall (%d, %d)", text.text("Horizontal"), 1, 9);
            check(
                    coordinates.contains(text.text("Horizontal")),
                    "Wall orientation is translated within coordinates");
            char zero = DecimalFormatSymbols.getInstance(text.locale()).getZeroDigit();
            check(
                    coordinates.indexOf((char) (zero + 1)) >= 0
                            && coordinates.indexOf((char) (zero + 9)) >= 0,
                    "Coordinates use the selected locale's digits");
            check(
                    text.text("%d walls remaining", 10).contains("" + (char) (zero + 1) + zero),
                    "Wall inventory uses localized digits");
            check(
                    text.text("Cannot rename {0}").contains("{0}"),
                    "Swing formats its own numbered error arguments");
        }
        equal(
                new I18n(Language.ENGLISH).text("BLUE'S GOAL"),
                "BLUE'S GOAL",
                "Apostrophes remain literal");
        check(
                new I18n(Language.PERSIAN)
                        .text("Quoridor matches (*.qsave, *.G3M)")
                        .contains("\u2066*.qsave, *.G3M\u2069"),
                "Latin filename globs retain left-to-right order in Persian");
    }

    private static void invalidRequests() {
        for (Language language : I18n.supportedLanguages()) {
            I18n text = new I18n(language);
            rejects(() -> text.text("This key is missing"), "Unknown keys fail explicitly");
            rejects(() -> text.text(null), "Null keys fail explicitly");
            rejects(() -> text.text("%s wins!"), "Missing arguments are rejected");
            rejects(() -> text.text("%s wins!", "Ali", "extra"), "Extra arguments are rejected");
            rejects(
                    () -> text.text("Save", 1),
                    "Static labels cannot silently ignore extra arguments");
            rejects(
                    () -> text.text("%d walls remaining", "ten"),
                    "Wrong numeric argument type is rejected");
            rejects(() -> text.text("Save", (Object[]) null), "Null argument arrays are rejected");
        }
    }

    private static void sourceKeys() throws Exception {
        Path sourceDirectory =
                Path.of(System.getProperty("quoridor.test.project", "."), "src", "Board");
        if (!Files.isDirectory(sourceDirectory)) {
            return;
        }
        try (var sources = Files.walk(sourceDirectory)) {
            for (Path source :
                    sources.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                checkSourceKeys(source);
            }
        }
    }

    private static void checkSourceKeys(Path source) throws Exception {
        String sourceCode = Files.readString(source, StandardCharsets.UTF_8);
        Matcher calls =
                Pattern.compile(
                                "(?:\\b(?:tr|text|button)|Theme\\.label|new Theme\\.(?:Button|Toggle))"
                                        + "\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                        .matcher(sourceCode);
        while (calls.find()) {
            String key = calls.group(1).translateEscapes();
            if (key.isEmpty()) {
                continue;
            }
            Object[] arguments = sampleArguments(key);
            for (Language language : I18n.supportedLanguages()) {
                check(
                        !new I18n(language).text(key, arguments).isBlank(),
                        source.getFileName() + " source key is translated: " + key);
            }
        }
    }

    private static void rejects(Runnable action, String description) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, description);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(actual.equals(expected), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
