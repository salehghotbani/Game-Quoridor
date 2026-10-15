package Board;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable names for a two- or four-player match and its local opponent settings. */
public record GameConfig(List<String> playerNames, Mode mode, BotPlayer.Difficulty difficulty) {
    private static final int MAX_NAME_LENGTH = 24;

    public enum Mode {
        LOCAL,
        BOT
    }

    public GameConfig {
        Objects.requireNonNull(playerNames, "Player names are required");
        if (playerNames.size() != 2 && playerNames.size() != 4) {
            throw new IllegalArgumentException("Player count must be 2 or 4");
        }
        playerNames = playerNames.stream().map(GameConfig::checkedName).toList();
        Objects.requireNonNull(mode, "Game mode is required");
        Objects.requireNonNull(difficulty, "Bot difficulty is required");
        if (mode == Mode.BOT && playerNames.size() != 2) {
            throw new IllegalArgumentException("Robot games support exactly two players");
        }
    }

    public GameConfig(
            String blueName, String greenName, Mode mode, BotPlayer.Difficulty difficulty) {
        this(Arrays.asList(blueName, greenName), mode, difficulty);
    }

    public int playerCount() {
        return playerNames.size();
    }

    public String name(int player) {
        return playerNames.get(player);
    }

    public String blueName() {
        return name(0);
    }

    public String greenName() {
        return name(1);
    }

    private static String checkedName(String name) {
        Objects.requireNonNull(name, "Player name is required");
        String cleaned = name.strip();
        if (cleaned.isEmpty()
                || cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH
                || cleaned.codePoints().anyMatch(GameConfig::invalidNameCodePoint)) {
            throw new IllegalArgumentException(
                    "Player names must contain 1 to 24 characters without control characters");
        }
        return cleaned;
    }

    private static boolean invalidNameCodePoint(int codePoint) {
        // Unpaired UTF-16 surrogates cannot round-trip through UTF-8 saves.
        return Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.SURROGATE;
    }
}
