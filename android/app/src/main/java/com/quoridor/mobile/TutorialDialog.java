package com.quoridor.mobile;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import Board.BotPlayer;
import Board.GameState;
import Board.GameTutorial;

import com.quoridor.mobile.MobileText.Key;

/** Independent practice board; Android keeps its explicit wall-preview confirmation. */
final class TutorialDialog extends Dialog {
    private static final Key[] TITLES = {
        Key.LESSON_MOVE, Key.LESSON_WALL, Key.LESSON_JUMP, Key.LESSON_AROUND, Key.LESSON_GOAL
    };
    private static final Key[] PROMPTS = {
        Key.PRACTICE_MOVE,
        Key.PRACTICE_WALL,
        Key.PRACTICE_JUMP,
        Key.PRACTICE_AROUND,
        Key.PRACTICE_GOAL
    };
    private final GameTutorial lesson = new GameTutorial();
    private final MobileText words;
    private final TextView heading;
    private final TextView prompt;
    private final QuoridorBoardView board;
    private final Button confirm;
    private final Button back;
    private final Button next;

    TutorialDialog(Context context, MobileText words, boolean dark) {
        super(
                context,
                dark
                        ? android.R.style.Theme_Material_Dialog_Alert
                        : android.R.style.Theme_Material_Light_Dialog_Alert);
        this.words = words;
        MobileStyle style = new MobileStyle(context, dark);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(style.dp(16), style.dp(12), style.dp(16), style.dp(12));
        content.setBackgroundColor(style.panel);
        content.setLayoutDirection(
                words.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        heading = style.label("", 20, true);
        heading.setTag("tutorial.title");
        content.addView(heading);
        prompt = style.muted("");
        prompt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(prompt);
        board = new QuoridorBoardView(context);
        board.setTag("tutorial.board");
        board.setDark(dark);
        board.setLanguage(words.language());
        board.setLayoutDirection(content.getLayoutDirection());
        board.setListener(
                new QuoridorBoardView.Listener() {
                    @Override
                    public void onMove(int row, int col) {
                        play(new BotPlayer.Action(row, col, null));
                    }

                    @Override
                    public void onWallSelected(GameState.Wall wall, boolean legal) {
                        if (confirm != null)
                            confirm.setEnabled(legal && wall != null && matches(wall));
                    }
                });
        content.addView(
                board,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        confirm =
                style.button(
                        words.text(Key.CONFIRM_WALL),
                        "tutorial.confirm",
                        () -> {
                            GameState.Wall wall = board.selectedWall();
                            if (wall != null)
                                play(
                                        new BotPlayer.Action(
                                                wall.row(), wall.col(), wall.orientation()));
                        });
        style.primary(confirm);
        content.addView(confirm);
        LinearLayout navigation = new LinearLayout(context);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        back = style.button(words.text(Key.BACK), "tutorial.back", () -> load(lesson.index() - 1));
        Button close =
                style.button(words.text(Key.CLOSE_TUTORIAL), "tutorial.close", this::dismiss);
        next =
                style.button(
                        "",
                        "tutorial.next",
                        () -> {
                            if (!lesson.complete()) return;
                            if (lesson.index() == GameTutorial.LESSON_COUNT - 1) dismiss();
                            else load(lesson.index() + 1);
                        });
        style.primary(next);
        for (Button button : new Button[] {back, close, next})
            navigation.addView(
                    button,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(navigation);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        setContentView(scroll);
        setTitle(words.text(Key.LEARN));
        refresh();
    }

    private boolean matches(GameState.Wall wall) {
        BotPlayer.Action target = lesson.target();
        return wall.row() == target.row()
                && wall.col() == target.col()
                && wall.orientation() == target.orientation();
    }

    private void load(int index) {
        lesson.load(index);
        refresh();
    }

    private void play(BotPlayer.Action action) {
        if (lesson.perform(action)) {
            refresh();
            next.requestFocus();
        }
    }

    private void refresh() {
        BotPlayer.Action target = lesson.target();
        heading.setText(
                words.text(Key.LESSON_PROGRESS, lesson.index() + 1, GameTutorial.LESSON_COUNT)
                        + " · "
                        + words.text(TITLES[lesson.index()]));
        prompt.setText(words.text(lesson.complete() ? Key.NICE : PROMPTS[lesson.index()]));
        board.setPosition(lesson.position(), !lesson.complete());
        board.setMode(
                target.orientation() == null
                        ? QuoridorBoardView.Mode.MOVE
                        : QuoridorBoardView.Mode.HORIZONTAL);
        board.focusPosition(new GameState.Cell(target.row(), target.col()));
        board.setHint(
                lesson.complete() || target.orientation() != null
                        ? null
                        : new GameState.Cell(target.row(), target.col()));
        board.setWallHint(
                lesson.complete() || target.orientation() == null
                        ? null
                        : new GameState.Wall(target.row(), target.col(), target.orientation()));
        confirm.setVisibility(
                target.orientation() == null || lesson.complete() ? View.GONE : View.VISIBLE);
        confirm.setEnabled(false);
        back.setEnabled(lesson.index() > 0);
        next.setEnabled(lesson.complete());
        next.setText(
                words.text(lesson.index() == GameTutorial.LESSON_COUNT - 1 ? Key.DONE : Key.NEXT));
    }
}
