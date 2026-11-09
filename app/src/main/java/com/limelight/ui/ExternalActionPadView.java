package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.ui.ActionPadKeyMap.Page;
import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A second-screen button pad for MMO-style play: a tabbed column of interface, target and utility
 * shortcuts on one side, and Shift / Ctrl / Alt plus the 12 action-bar number keys on the other.
 *
 * Every touch is exactly one key press and one key release, sent through {@link KeySink}. Shift, Ctrl
 * and Alt are one-shot by default (tap to arm, the next number key uses it, then it lets go) and lock
 * on a long press. Page buttons ignore armed modifiers and send only their own.
 *
 * Multi-touch works: every button has its own pointer, so several keys can be held at once.
 */
public class ExternalActionPadView extends LinearLayout {

    /** Where key events go. {@code modifiers} is the KeyboardPacket modifier flags in effect for the key. */
    public interface KeySink {
        void sendKey(short vk, boolean down, byte modifiers);
    }

    private static final long LOCK_PRESS_MS = 450;
    /** Pages with more buttons than this split into two columns. */
    private static final int SINGLE_COLUMN_MAX = 8;

    private static final int BG = 0xFF14110D;
    private static final int FACE = 0xFF2B2318;
    private static final int FACE_NUMBER = 0xFF352A1B;
    private static final int FACE_PRESSED = 0xFF5A4620;
    private static final int BORDER = 0xFF8A6A2A;
    private static final int GOLD = 0xFFE0B04A;
    private static final int TEXT = 0xFFF1E3C0;

    private final KeySink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Modifiers the user has armed or locked on the pad, in the order they were armed. */
    private final Set<Integer> armed = new LinkedHashSet<>();
    private final Set<Integer> locked = new HashSet<>();
    private final Map<Integer, TextView> modifierButtons = new HashMap<>();
    private final List<TextView> numberButtons = new ArrayList<>();
    private final List<TextView> tabs = new ArrayList<>();

    /** Buttons currently held down, by the view that is holding them. */
    private final Map<View, Press> pressed = new HashMap<>();
    /** How many isolated buttons are down. While any is, armed modifiers are let go on the host. */
    private int isolatedDown = 0;

    private final LinearLayout pageHost;
    private int pageIndex = 0;

    private static final class Press {
        final Slot slot;
        final List<Integer> pressedMods;
        final byte mask;
        final boolean isolated;

        Press(Slot slot, List<Integer> pressedMods, byte mask, boolean isolated) {
            this.slot = slot;
            this.pressedMods = pressedMods;
            this.mask = mask;
            this.isolated = isolated;
        }
    }

    public ExternalActionPadView(Context context, KeySink sink) {
        super(context);
        this.sink = sink;

        setOrientation(VERTICAL);
        // Swallow touches in the gaps, so they do not move the host's mouse.
        setClickable(true);
        setMotionEventSplittingEnabled(true);
        setBackgroundColor(BG);
        int pad = dp(8);
        setPadding(pad, pad, pad, pad);

        addView(buildTabBar(), new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        body.setMotionEventSplittingEnabled(true);
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        pageHost = new LinearLayout(context);
        pageHost.setOrientation(HORIZONTAL);
        pageHost.setMotionEventSplittingEnabled(true);
        body.addView(pageHost, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(VERTICAL);
        right.setMotionEventSplittingEnabled(true);
        body.addView(right, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        right.addView(buildModifierRow(), weighted(right, 0.7f));
        right.addView(buildNumberBlock(), weighted(right, 4f));

        showPage(0);
    }

    // --- Building the UI ---

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(HORIZONTAL);
        for (int i = 0; i < ActionPadKeyMap.PAGES.size(); i++) {
            final int index = i;
            TextView tab = new TextView(getContext());
            tab.setText(ActionPadKeyMap.PAGES.get(i).title);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tab.setOnClickListener(v -> showPage(index));
            tabs.add(tab);
            bar.addView(tab, weighted(bar, 1f));
        }
        return bar;
    }

    private LinearLayout buildModifierRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setMotionEventSplittingEnabled(true);
        for (int vk : ActionPadKeyMap.MODIFIER_KEYS) {
            TextView button = modifierButton(vk);
            modifierButtons.put(vk, button);
            row.addView(button, weighted(row, 1f));
        }
        refreshModifierButtons();
        return row;
    }

    private LinearLayout buildNumberBlock() {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(VERTICAL);
        block.setMotionEventSplittingEnabled(true);
        List<Slot> numbers = ActionPadKeyMap.NUMBERS;
        for (int start = 0; start < numbers.size(); start += 3) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setMotionEventSplittingEnabled(true);
            for (Slot slot : numbers.subList(start, Math.min(start + 3, numbers.size()))) {
                TextView button = keyButton(slot, 26f, true);
                numberButtons.add(button);
                row.addView(button, weighted(row, 1f));
            }
            block.addView(row, weighted(block, 1f));
        }
        return block;
    }

    /** Shows page {@code index}. Lets go of any pressed button first, since its view is about to go. */
    public void showPage(int index) {
        releaseAllKeys();
        pageIndex = Math.max(0, Math.min(index, ActionPadKeyMap.PAGES.size() - 1));
        for (int i = 0; i < tabs.size(); i++) styleTab(tabs.get(i), i == pageIndex);

        pageHost.removeAllViews();
        Page page = ActionPadKeyMap.PAGES.get(pageIndex);
        int columns = page.slots.size() > SINGLE_COLUMN_MAX ? 2 : 1;
        int perColumn = (page.slots.size() + columns - 1) / columns;
        for (int c = 0; c < columns; c++) {
            LinearLayout column = new LinearLayout(getContext());
            column.setOrientation(VERTICAL);
            column.setMotionEventSplittingEnabled(true);
            int from = c * perColumn;
            int to = Math.min(from + perColumn, page.slots.size());
            for (Slot slot : page.slots.subList(from, to)) {
                column.addView(keyButton(slot, columns == 1 ? 18f : 14f, false), weighted(column, 1f));
            }
            pageHost.addView(column, weighted(pageHost, 1f));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView keyButton(final Slot slot, float textSp, boolean number) {
        final TextView button = new TextView(getContext());
        button.setText(number ? slot.label : wrapLabel(slot.label));
        button.setContentDescription(slot.label);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(TEXT);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        button.setMaxLines(2);
        button.setBackground(keyBackground(number));
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    press(v, slot);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    release(v);
                    break;
                default:
                    break;
            }
            return true;
        });
        return button;
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView modifierButton(final int vk) {
        final TextView button = new TextView(getContext());
        button.setText(ActionPadKeyMap.modifierLabel(vk));
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        final boolean[] lockFired = {false};
        final Runnable lock = () -> {
            lockFired[0] = true;
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            arm(vk, true);
            refreshModifierButtons();
        };
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lockFired[0] = false;
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    handler.postDelayed(lock, LOCK_PRESS_MS);
                    break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(lock);
                    if (!lockFired[0]) {
                        if (armed.contains(vk)) disarm(vk); else arm(vk, false);
                        refreshModifierButtons();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(lock);
                    break;
                default:
                    break;
            }
            return true;
        });
        return button;
    }

    // --- Key sending ---

    private void press(View view, Slot slot) {
        if (pressed.containsKey(view)) return;
        // A button with its own modifiers, or an isolated one, must not pick up Shift, Ctrl or Alt held on the pad.
        boolean isolate = slot.isolated || slot.mods.length > 0;
        if (isolate && isolatedDown++ == 0) suspendArmed();
        byte mask = heldMask();
        List<Integer> pressedMods = new ArrayList<>();
        for (int mod : slot.mods) {
            sink.sendKey((short) mod, true, mask);
            mask |= ActionPadKeyMap.modifierFlag(mod);
            pressedMods.add(mod);
        }
        sink.sendKey((short) slot.vk, true, mask);
        pressed.put(view, new Press(slot, pressedMods, mask, isolate));
    }

    private void release(View view) {
        Press p = pressed.remove(view);
        if (p == null) return;
        sink.sendKey((short) p.slot.vk, false, p.mask);
        byte mask = p.mask;
        for (int i = p.pressedMods.size() - 1; i >= 0; i--) {
            int mod = p.pressedMods.get(i);
            mask &= ~ActionPadKeyMap.modifierFlag(mod);
            sink.sendKey((short) mod, false, mask);
        }
        if (p.isolated) {
            if (--isolatedDown == 0) resumeArmed();
        } else {
            releaseOneShot();
        }
    }

    /** Lets go of every pressed button, and gives back any modifier held aside for an isolated one. */
    private void releaseAllKeys() {
        for (View view : new ArrayList<>(pressed.keySet())) {
            view.setPressed(false);
            release(view);
        }
    }

    /** Lets go of everything the pad holds: pressed keys and armed or locked modifiers. */
    public void releaseAll() {
        handler.removeCallbacksAndMessages(null);
        releaseAllKeys();
        for (int mod : new ArrayList<>(armed)) disarm(mod);
        refreshModifierButtons();
    }

    private byte heldMask() {
        if (isolatedDown > 0) return 0;
        byte mask = 0;
        for (int mod : armed) mask |= ActionPadKeyMap.modifierFlag(mod);
        return mask;
    }

    private void arm(int vk, boolean lock) {
        if (!armed.contains(vk)) {
            byte before = heldMask();
            armed.add(vk);
            if (isolatedDown == 0) sink.sendKey((short) vk, true, before);
        }
        if (lock) locked.add(vk);
    }

    private void disarm(int vk) {
        if (!armed.remove(vk)) return;
        locked.remove(vk);
        if (isolatedDown == 0) sink.sendKey((short) vk, false, heldMask());
    }

    /** After a number key, armed modifiers that are not locked let go. */
    private void releaseOneShot() {
        boolean changed = false;
        for (int mod : new ArrayList<>(armed)) {
            if (!locked.contains(mod)) {
                disarm(mod);
                changed = true;
            }
        }
        if (changed) refreshModifierButtons();
    }

    private void suspendArmed() {
        byte mask = 0;
        for (int mod : armed) mask |= ActionPadKeyMap.modifierFlag(mod);
        List<Integer> order = new ArrayList<>(armed);
        for (int i = order.size() - 1; i >= 0; i--) {
            mask &= ~ActionPadKeyMap.modifierFlag(order.get(i));
            sink.sendKey(order.get(i).shortValue(), false, mask);
        }
    }

    private void resumeArmed() {
        byte mask = 0;
        for (int mod : armed) {
            sink.sendKey((short) mod, true, mask);
            mask |= ActionPadKeyMap.modifierFlag(mod);
        }
    }

    // --- Styling ---

    private void refreshModifierButtons() {
        for (Map.Entry<Integer, TextView> entry : modifierButtons.entrySet()) {
            int vk = entry.getKey();
            TextView button = entry.getValue();
            if (locked.contains(vk)) {
                button.setBackground(rect(GOLD, GOLD));
                button.setTextColor(BG);
            } else if (armed.contains(vk)) {
                button.setBackground(rect(FACE, GOLD));
                button.setTextColor(GOLD);
            } else {
                button.setBackground(rect(FACE, BORDER));
                button.setTextColor(TEXT);
            }
        }
        // Number buttons show a gold border while a modifier is armed, since it applies to them.
        for (TextView button : numberButtons) button.setActivated(!armed.isEmpty());
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackground(selected ? rect(FACE_PRESSED, GOLD) : rect(BG, BORDER));
        tab.setTextColor(selected ? GOLD : TEXT);
    }

    private StateListDrawable keyBackground(boolean number) {
        int face = number ? FACE_NUMBER : FACE;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rect(FACE_PRESSED, GOLD));
        states.addState(new int[]{android.R.attr.state_activated}, rect(face, GOLD));
        states.addState(new int[]{}, rect(face, BORDER));
        return states;
    }

    private GradientDrawable rect(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(10));
        d.setStroke(dp(2), stroke);
        return d;
    }

    /** A long, multi-word label drops its last word onto a second line. */
    private static String wrapLabel(String label) {
        int cut = label.lastIndexOf(' ');
        return label.length() > 9 && cut > 0 && label.indexOf('/') < 0
                ? label.substring(0, cut) + "\n" + label.substring(cut + 1)
                : label;
    }

    /** Layout params that give {@code weight} of the parent's length, with a small margin all round. */
    private LayoutParams weighted(LinearLayout parent, float weight) {
        LayoutParams lp = parent.getOrientation() == HORIZONTAL
                ? new LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
                : new LayoutParams(LayoutParams.MATCH_PARENT, 0, weight);
        int margin = dp(3);
        lp.setMargins(margin, margin, margin, margin);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}
