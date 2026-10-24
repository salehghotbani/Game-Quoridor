package Board;

import java.util.List;
import java.util.Objects;

/** Isolated, legal practice positions. Never owns or mutates a real match. */
public final class GameTutorial {
    public static final int LESSON_COUNT = 5;
    private int index;
    private GameState position;
    private BotPlayer.Action target;
    private boolean complete;

    public GameTutorial() {
        load(0);
    }

    public void load(int lesson) {
        if (lesson < 0 || lesson >= LESSON_COUNT) {
            throw new IllegalArgumentException("Invalid lesson");
        }
        index = lesson;
        complete = false;
        GameState.Cell blue = new GameState.Cell(8, 4);
        GameState.Cell green = new GameState.Cell(0, 4);
        List<GameState.Wall> walls = List.of();
        if (lesson == 2 || lesson == 3) {
            blue = new GameState.Cell(4, 4);
            green = new GameState.Cell(3, 4);
        }
        if (lesson == 3) {
            walls = List.of(new GameState.Wall(2, 4, GameState.Orientation.HORIZONTAL));
        }
        if (lesson == 4) {
            blue = new GameState.Cell(1, 4);
            green = new GameState.Cell(7, 4);
        }
        position =
                GameState.fromSnapshot(
                        new GameState.Snapshot(blue, green, 10 - walls.size(), 10, 0, -1, walls));
        target =
                switch (lesson) {
                    case 0 -> new BotPlayer.Action(7, 4, null);
                    case 1 -> new BotPlayer.Action(3, 3, GameState.Orientation.HORIZONTAL);
                    case 2 -> new BotPlayer.Action(2, 4, null);
                    case 3 -> new BotPlayer.Action(3, 3, null);
                    default -> new BotPlayer.Action(0, 4, null);
                };
    }

    public int index() {
        return index;
    }

    public boolean complete() {
        return complete;
    }

    public GameState position() {
        return position.copy();
    }

    public BotPlayer.Action target() {
        return target;
    }

    public boolean perform(BotPlayer.Action action) {
        if (complete || !Objects.equals(target, action)) {
            return false;
        }
        complete = action.apply(position);
        return complete;
    }
}
