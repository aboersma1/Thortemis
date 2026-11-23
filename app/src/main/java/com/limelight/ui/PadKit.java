package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * What the pad's panels share: the theme and sizes, the input state, and the factory for styled buttons.
 * Buttons that show state (armed modifiers, the hold toggle, shifted keyboard labels) register a
 * refresher here, and every change in {@link PadInput} runs them all.
 */
final class PadKit {
    private static final long LOCK_PRESS_MS = 450;

    final Context context;
    final ActionPadSettings settings;
    ActionPadTheme theme;
    final PadInput input;
    private final float density;
    private final List<Runnable> refreshers = new ArrayList<>();

    PadKit(Context context, ActionPadSettings settings, PadInput input) {
        this.context = context;
        this.settings = settings;
        this.theme = settings.theme;
        this.input = input;
        this.density = context.getResources().getDisplayMetrics().density;
        input.onChanged = this::refresh;
    }

    /** Picks up a theme the player just changed; buttons built afterwards use it. */
    void applyTheme() {
        theme = settings.theme;
    }

    // --- Sizes and shapes ---

    int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    /** Layout params that give {@code weight} of the parent's length, with a small margin all round. */
    LinearLayout.LayoutParams weighted(LinearLayout parent, float weight) {
        LinearLayout.LayoutParams lp = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, weight);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    /** An empty row or column that passes several touches to its buttons at once. */
    LinearLayout box(int orientation) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(orientation);
        box.setMotionEventSplittingEnabled(true);
        return box;
    }

    GradientDrawable rect(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        if (theme.flat) {
            d.setColor(fill);
        } else {
            d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            d.setColors(new int[]{lighten(fill), fill});
        }
        d.setCornerRadius(dp(10));
        d.setStroke(dp(2), stroke);
        return d;
    }

    private static int lighten(int c) {
        return 0xFF000000 | Math.min(255, ((c >> 16) & 0xFF) + 22) << 16
                | Math.min(255, ((c >> 8) & 0xFF) + 22) << 8 | Math.min(255, (c & 0xFF) + 28);
    }

    private StateListDrawable keyBackground(int face, int edge, int accent) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rect(theme.facePressed, accent));
        states.addState(new int[]{android.R.attr.state_activated}, rect(face, accent));
        states.addState(new int[]{}, rect(face, edge));
        return states;
    }

    // --- Buttons ---

    /** Centred bold text in the theme's colour; {@code baseSp} is scaled by the player's text size. */
    TextView text(String label, float baseSp) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(theme.text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * settings.labelScale);
        return view;
    }

    /** A button that responds to a tap. */
    TextView tapButton(String label, float baseSp, Runnable onTap) {
        TextView view = text(label, baseSp);
        view.setBackground(keyBackground(theme.face, theme.border, theme.accent));
        view.setOnClickListener(v -> {
            haptic(v);
            onTap.run();
        });
        return view;
    }

    void haptic(View view) {
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** What a held-down button does when a finger goes down and when it lifts or the touch is cancelled. */
    interface PressHandler {
        void down();

        void up(boolean cancelled);
    }

    /** A button that acts on touch-down and touch-up, so several can be held at once. */
    @SuppressLint("ClickableViewAccessibility")
    TextView pressButton(String label, float baseSp, int face, int edge, int accent, PressHandler handler) {
        final TextView view = text(label, baseSp);
        view.setBackground(keyBackground(face, edge, accent));
        view.setMaxLines(2);
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                haptic(v);
                handler.down();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                handler.up(action == MotionEvent.ACTION_CANCEL);
            }
            return true;
        });
        return view;
    }

    /**
     * A key button: one press when the finger goes down, one release when it lifts. {@code modified}
     * buttons take a lit outline while Shift, Ctrl or Alt is armed. {@code afterRelease} runs when the
     * finger lifts (not when the touch is cancelled).
     */
    TextView keyButton(Slot slot, String label, float baseSp, boolean modified, Runnable afterRelease) {
        return keyButton(slot, label, baseSp, modified, afterRelease, null);
    }

    /** Same, and {@code onPress} runs when the finger lands, just before the key goes down (so armed Shift is still armed). */
    TextView keyButton(Slot slot, String label, float baseSp, boolean modified, Runnable afterRelease, Runnable onPress) {
        int edge = slot.color != 0 ? slot.color : theme.border;
        int accent = slot.color != 0 ? slot.color : theme.accent;
        final PadInput.Press[] held = {null};
        final TextView view = pressButton(label, baseSp, modified ? theme.faceNumber : theme.face, edge, accent,
                new PressHandler() {
                    @Override
                    public void down() {
                        if (onPress != null) onPress.run();
                        held[0] = input.press(slot);
                    }

                    @Override
                    public void up(boolean cancelled) {
                        input.release(held[0]);
                        held[0] = null;
                        if (!cancelled && afterRelease != null) afterRelease.run();
                    }
                });
        view.setContentDescription(slot.label);
        if (settings.iconButtons && PadIcons.has(slot.id)) showIcon(view, slot.id);
        if (modified) addRefresher(() -> view.setActivated(input.anyArmed()));
        return view;
    }

    /** Replaces the button's words with its icon, centred. */
    private void showIcon(TextView view, String id) {
        int size = Math.round(dp(28) * Math.max(0.8f, settings.labelScale));
        android.graphics.drawable.Drawable icon = PadIcons.make(id, size, theme.text);
        android.text.SpannableString icon1 = new android.text.SpannableString(" ");
        icon1.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(icon1);
    }

    /** Shift, Ctrl or Alt: a tap arms it for the next key, a long press locks it. */
    @SuppressLint("ClickableViewAccessibility")
    TextView modifierButton(final int vk) {
        final TextView view = text(ActionPadKeyMap.modifierLabel(vk), 14);
        final boolean[] lockFired = {false};
        final Runnable lock = () -> {
            lockFired[0] = true;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            input.lockModifier(vk);
        };
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lockFired[0] = false;
                    haptic(v);
                    v.postDelayed(lock, LOCK_PRESS_MS);
                    break;
                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(lock);
                    if (!lockFired[0]) input.toggleModifier(vk);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(lock);
                    break;
                default:
                    break;
            }
            return true;
        });
        addRefresher(() -> {
            boolean isLocked = input.isLocked(vk), isArmed = input.isArmed(vk);
            view.setBackground(isLocked ? rect(theme.accent, theme.accent) : rect(theme.face, isArmed ? theme.accent : theme.border));
            view.setTextColor(isLocked ? theme.bg : isArmed ? theme.accent : theme.text);
        });
        return view;
    }

    // --- State refresh ---

    /** Runs {@code refresher} now and after every change to the armed modifiers or the hold toggle. */
    void addRefresher(Runnable refresher) {
        refreshers.add(refresher);
        refresher.run();
    }

    void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    /** Drops every refresher, before the buttons they belong to are rebuilt. */
    void forget() {
        refreshers.clear();
    }
}
