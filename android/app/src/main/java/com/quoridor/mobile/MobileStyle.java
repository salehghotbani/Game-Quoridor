package com.quoridor.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Per-activity colors and native controls; never changes global application styling. */
final class MobileStyle {
    final int background, panel, text, muted, accent, line;
    private final Context context;
    private final Typeface face;

    MobileStyle(Context context, boolean dark) {
        this.context = context;
        face = Typeface.createFromAsset(context.getAssets(), "fonts/Shabnam.ttf");
        background = Color.parseColor(dark ? "#0B1220" : "#EDF2F7");
        panel = Color.parseColor(dark ? "#131F32" : "#FFFFFF");
        text = Color.parseColor(dark ? "#EEF3FA" : "#19283C");
        muted = Color.parseColor(dark ? "#ABBCD0" : "#52637A");
        accent = Color.parseColor(dark ? "#42CFAE" : "#126F61");
        line = Color.parseColor(dark ? "#34445B" : "#D5DEE6");
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    void applyFont(View view) {
        if (view instanceof TextView textView) {
            Typeface previous = textView.getTypeface();
            textView.setTypeface(face, previous == null ? Typeface.NORMAL : previous.getStyle());
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                applyFont(group.getChildAt(index));
            }
        }
    }

    TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTypeface(face);
        view.setTextColor(text);
        view.setTextSize(size);
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) {
            view.setTypeface(face, Typeface.BOLD);
        }
        return view;
    }

    TextView muted(String value) {
        TextView view = label(value, 14, false);
        view.setTextColor(muted);
        return view;
    }

    Button button(String value, String tag, Runnable action) {
        Button button = new Button(context);
        button.setTag(tag);
        button.setTypeface(face);
        button.setText(value);
        button.setTextColor(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMaxLines(2);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(line), shape(panel), null));
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    void primary(Button button) {
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(line), shape(accent), null));
        button.setTextColor(background);
    }

    EditText input(String hint, String value, String tag) {
        EditText input = new EditText(context);
        input.setTag(tag);
        input.setTypeface(face);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(shape(background));
        input.setSelectAllOnFocus(true);
        return input;
    }

    Spinner spinner(String[] values, int selected, String tag) {
        Spinner spinner = new Spinner(context);
        spinner.setTag(tag);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return paint((TextView) super.getView(position, convertView, parent));
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        return paint(
                                (TextView) super.getDropDownView(position, convertView, parent));
                    }

                    private TextView paint(TextView view) {
                        view.setTypeface(face);
                        view.setTextColor(text);
                        view.setTextSize(16);
                        view.setPadding(dp(12), dp(12), dp(12), dp(12));
                        view.setBackgroundColor(panel);
                        return view;
                    }
                };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setMinimumHeight(dp(48));
        spinner.setPopupBackgroundDrawable(shape(panel));
        return spinner;
    }

    LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(16));
        card.setBackground(shape(panel));
        return card;
    }

    GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(1), line);
        return shape;
    }

    static int playerColor(int player, boolean dark) {
        switch (player) {
            case 0:
                return dark ? Color.rgb(90, 191, 255) : Color.rgb(0, 105, 170);
            case 1:
                return dark ? Color.rgb(83, 221, 167) : Color.rgb(9, 115, 77);
            case 2:
                return dark ? Color.rgb(255, 130, 140) : Color.rgb(175, 38, 54);
            default:
                return dark ? Color.rgb(255, 211, 113) : Color.rgb(133, 87, 0);
        }
    }
}
