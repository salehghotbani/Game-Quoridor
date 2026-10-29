package com.quoridor.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import Board.GameSession;
import Board.GameState;

import com.quoridor.mobile.MobileText.Key;

import java.util.ArrayList;
import java.util.List;

/** Compact native player cards; text remains bidi-aware and available to TalkBack. */
final class PlayerInventoryView extends LinearLayout {
    private final MobileStyle style;
    private final MobileText words;
    private final boolean dark;
    private List<String> displayedDescriptions = List.of();
    private int displayedPlayer = -2;

    PlayerInventoryView(Context context, MobileStyle style, MobileText words, boolean dark) {
        super(context);
        this.style = style;
        this.words = words;
        this.dark = dark;
        setOrientation(VERTICAL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(GameSession session, List<String> waitingNames) {
        List<String> descriptions = new ArrayList<>();
        GameState game = session == null ? null : session.game();
        int count = game == null ? waitingNames.size() : game.playerCount();
        for (int player = 0; player < count; player++) {
            descriptions.add(
                    game == null
                            ? words.isolate(waitingNames.get(player))
                            : words.text(
                                    Key.WALLS,
                                    words.isolate(session.config().name(player)),
                                    game.wallsRemaining(player)));
        }
        int activePlayer = game == null ? -1 : game.currentPlayer();
        // Polling an unchanged online position must preserve the focused accessibility card.
        if (displayedPlayer == activePlayer && displayedDescriptions.equals(descriptions)) return;
        displayedPlayer = activePlayer;
        displayedDescriptions = List.copyOf(descriptions);
        removeAllViews();
        LinearLayout row = null;
        for (int player = 0; player < count; player++) {
            if (player == 0) {
                row = new LinearLayout(getContext());
                addView(
                        row,
                        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            int color = MobileStyle.playerColor(player, dark);
            String name =
                    session == null ? waitingNames.get(player) : session.config().name(player);
            int stock = game == null ? 0 : game.wallsRemaining(player);
            String description = descriptions.get(player);
            LinearLayout card = style.card();
            card.setPadding(style.dp(5), style.dp(3), style.dp(5), style.dp(3));
            boolean active = activePlayer == player;
            var background = style.shape(style.panel);
            background.setStroke(style.dp(active ? 2 : 1), active ? color : style.line);
            card.setBackground(background);
            card.setContentDescription(description);
            card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            TextView label = style.label(words.isolate(name), 11, true);
            label.setTextColor(active ? color : style.text);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(label);
            if (session != null) {
                TextView remaining = style.muted(words.text(Key.WALL_COUNT, stock));
                remaining.setTextSize(10);
                remaining.setPadding(0, 0, 0, style.dp(2));
                remaining.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(remaining);
                View reserve = new Reserve(getContext(), stock, count == 4 ? 5 : 10, color);
                reserve.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(reserve, new LayoutParams(LayoutParams.MATCH_PARENT, style.dp(6)));
            }
            LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(style.dp(3), style.dp(3), style.dp(3), style.dp(3));
            row.addView(card, params);
            row.setGravity(Gravity.CENTER_VERTICAL);
        }
        // Also provides a stable, localized summary for automation and assistive consumers.
        setContentDescription(String.join(" · ", descriptions));
    }

    private final class Reserve extends View {
        private final int stock, capacity, color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Reserve(Context context, int stock, int capacity, int color) {
            super(context);
            this.stock = stock;
            this.capacity = capacity;
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = Math.min(style.dp(6), getWidth() / (float) (capacity * 2));
            for (int index = 0; index < capacity; index++) {
                paint.setColor(index < stock ? color : style.line);
                float left = index * width * 1.6f;
                if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) left = getWidth() - left - width;
                canvas.drawRoundRect(
                        left, 0, left + width, getHeight(), style.dp(2), style.dp(2), paint);
            }
        }
    }
}
