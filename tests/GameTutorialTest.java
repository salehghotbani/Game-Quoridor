import Board.BotPlayer;
import Board.GameTutorial;

/** Practice targets are legal real rules and never leak mutable state. */
public final class GameTutorialTest {
    public static void main(String[] args) {
        GameTutorial tutorial = new GameTutorial();
        for (int index = 0; index < GameTutorial.LESSON_COUNT; index++) {
            tutorial.load(index);
            var before = tutorial.position().snapshot();
            tutorial.position().moveTo(7, 4);
            if (!before.equals(tutorial.position().snapshot()))
                throw new AssertionError("Mutable lesson escaped");
            if (tutorial.perform(new BotPlayer.Action(-1, -1, null)))
                throw new AssertionError("Wrong target accepted");
            if (!before.equals(tutorial.position().snapshot()))
                throw new AssertionError("Wrong action changed lesson");
            if (!tutorial.perform(tutorial.target()) || !tutorial.complete())
                throw new AssertionError("Lesson cannot finish: " + index);
            if (tutorial.perform(tutorial.target()))
                throw new AssertionError("Completed lesson acted twice");
            if (index == 4 && tutorial.position().winner() != 0)
                throw new AssertionError("Goal lesson did not win");
            tutorial.load(index);
            if (tutorial.complete() || !tutorial.position().snapshot().equals(before))
                throw new AssertionError("Replay lesson not reset");
        }
        for (int invalid : new int[] {-1, GameTutorial.LESSON_COUNT}) {
            try {
                tutorial.load(invalid);
                throw new AssertionError("Invalid lesson accepted");
            } catch (IllegalArgumentException expected) {
                /* Bound checked. */
            }
        }
        System.out.println("Tutorial rule, isolation and reset checks passed (5 lessons).");
    }
}
