package Board;

import java.util.Objects;

/** Player names and the rules for who controls the green pawn. */
public record GameConfig(String blueName, String greenName, Mode mode,
                         BotPlayer.Difficulty difficulty) {
    public enum Mode { LOCAL, BOT }

    public GameConfig {
        blueName = checkedName(blueName);
        greenName = checkedName(greenName);
        Objects.requireNonNull(mode, "Game mode is required");
        Objects.requireNonNull(difficulty, "Bot difficulty is required");
    }

    private static String checkedName(String name) {
        Objects.requireNonNull(name, "Player name is required");
        String cleaned = name.strip();
        if (cleaned.isEmpty() || cleaned.codePointCount(0, cleaned.length()) > 24
                || cleaned.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.SURROGATE)) {
            throw new IllegalArgumentException("Player names must contain 1 to 24 characters without control characters");
        }
        return cleaned;
    }
}
