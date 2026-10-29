package com.quoridor.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks both complete catalogs and the actual arguments supplied by the Android screens. */
public final class MobileTextTest {
    private static final Pattern ARGUMENT = Pattern.compile("%([sd])");

    @Test
    public void additionalCatalogsAreCompleteAndPreserveArguments() throws Exception {
        Field english = MobileText.Key.class.getDeclaredField("english");
        english.setAccessible(true);
        for (String language : new String[] {"es", "fr", "ar"}) {
            assertEquals(
                    MobileText.Key.values().length, MobileLanguages.CATALOGS.get(language).size());
            for (MobileText.Key key : MobileText.Key.values()) {
                String translated = MobileLanguages.CATALOGS.get(language).get(key);
                assertNotNull(key.name(), translated);
                assertEquals(signature((String) english.get(key)), signature(translated));
                assertFalse(new MobileText(language).text(key, screenArguments(key)).isBlank());
            }
        }
        assertFalse(MobileText.LANGUAGES.contains("he"));
        assertTrue(new MobileText("ar").rtl());
        assertFalse(new MobileText("fr").rtl());
    }

    @Test
    public void everyKeyIsTranslatedWithTheSameArgumentTypesAndArity() throws Exception {
        Field english = MobileText.Key.class.getDeclaredField("english");
        Field persian = MobileText.Key.class.getDeclaredField("persian");
        english.setAccessible(true);
        persian.setAccessible(true);
        for (MobileText.Key key : MobileText.Key.values()) {
            String source = (String) english.get(key);
            String translation = (String) persian.get(key);
            assertFalse(key + " empty English", source.trim().isEmpty());
            assertFalse(key + " empty Persian", translation.trim().isEmpty());
            assertNotEquals(key + " must be translated", source, translation);
            List<String> signature = signature(source);
            assertEquals(key + " placeholder semantics", signature, signature(translation));
            Object[] arguments = screenArguments(key);
            assertEquals(key + " screen argument count", signature.size(), arguments.length);
            for (boolean isPersian : new boolean[] {false, true}) {
                String result = new MobileText(isPersian).text(key, arguments);
                assertFalse(key + " formatted empty", result.trim().isEmpty());
                assertFalse(key + " replacement character", result.contains("\uFFFD"));
                for (Object argument : arguments) {
                    if (argument instanceof String) {
                        assertTrue(
                                key + " preserves user text", result.contains((String) argument));
                    }
                }
            }
        }
    }

    @Test
    public void twoExplicitLanguagesHaveNativeLabelsAndStableMappings() {
        MobileText english = new MobileText(false);
        MobileText persian = new MobileText(true);
        assertFalse(english.persian());
        assertTrue(persian.persian());
        String[] en = {"Blue", "Green", "Red", "Gold"};
        String[] fa = {"آبی", "سبز", "قرمز", "طلایی"};
        for (int player = 0; player < en.length; player++) {
            assertEquals(en[player], english.colorName(player));
            assertEquals(fa[player], persian.colorName(player));
        }
        assertEquals("Easy", english.difficulty(0));
        assertEquals("Medium", english.difficulty(1));
        assertEquals("Hard", english.difficulty(2));
        assertEquals("آسان", persian.difficulty(0));
        assertEquals("متوسط", persian.difficulty(1));
        assertEquals("قوی", persian.difficulty(2));
    }

    @Test
    public void unicodeNamesPercentAndApostrophesRemainLiteralWithoutChangingGlobalLocale() {
        Locale before = Locale.getDefault();
        String name = "سارا O'Reilly 100% 🟦";
        for (boolean isPersian : new boolean[] {false, true}) {
            MobileText text = new MobileText(isPersian);
            assertTrue(text.text(MobileText.Key.TURN, name).contains(name));
            assertEquals("\u2068" + name + "\u2069", text.isolate(name));
            assertTrue(text.text(MobileText.Key.WALLS, text.isolate(name), 21).contains(name));
            assertEquals(before, Locale.getDefault());
        }
        assertTrue(new MobileText(false).text(MobileText.Key.WAITING, 2, 4).contains("2 / 4"));
        assertTrue(new MobileText(true).text(MobileText.Key.WAITING, 2, 4).contains("۲ از ۴"));
    }

    private static Object[] screenArguments(MobileText.Key key) {
        String name = "نام O'Reilly 50%";
        switch (key) {
            case TURN:
            case PASS_HELP:
            case WINNER:
            case ROOM:
            case YOU:
            case WAIT_TURN:
            case VERSION:
                return new Object[] {name};
            case WALLS:
                return new Object[] {name, 10};
            case WALL_COUNT:
                return new Object[] {10};
            case WAITING:
                return new Object[] {2, 4};
            case SHARE_MESSAGE:
                return new Object[] {"A1B2C3D4", "https://example.test/"};
            case LESSON_PROGRESS:
                return new Object[] {1, 5};
            case MOVE_LINE:
                return new Object[] {name, 9, 1};
            case WALL_LINE:
                return new Object[] {name, "افقی", 8, 1};
            default:
                return new Object[0];
        }
    }

    private static List<String> signature(String template) {
        List<String> result = new ArrayList<>();
        Matcher matcher = ARGUMENT.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}
