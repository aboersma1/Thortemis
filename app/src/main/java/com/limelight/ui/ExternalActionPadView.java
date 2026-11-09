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

        /** Moves the host's mouse by a relative amount, in host pixels. */
        default void sendMouseMove(short dx, short dy) {}

        /** Presses or releases a mouse button ({@link ActionPadKeyMap#MOUSE_LEFT} or {@link ActionPadKeyMap#MOUSE_RIGHT}). */
        default void sendMouseButton(byte button, boolean down) {}
    }

    private static final long LOCK_PRESS_MS = 450;
    /** Pages with more buttons than this split into two columns. */
    private static final int SINGLE_COLUMN_MAX = 8;
    /** Gap between the key presses of a chat macro. */
    private static final long MACRO_STEP_MS = 35;
    /** A touchpad touch shorter than this, that barely moved, is a click. */
    private static final long TAP_MAX_MS = 250;
    /** How long a tap-click holds the button down. */
    private static final long CLICK_HOLD_MS = 45;
    private static final float TAP_SLOP_DP = 8;
    /** Host pixels moved per dp of finger travel on the touchpad. */
    private static final float TOUCHPAD_SPEED = 2.2f;

    // Blue "Alliance" look: deep navy panel, steel-blue trim, frost-blue highlights.
    private static final int BG = 0xFF08111F;
    private static final int BG_TOP = 0xFF0F2038;
    private static final int FACE = 0xFF16294A;
    private static final int FACE_NUMBER = 0xFF1B3560;
    private static final int FACE_PRESSED = 0xFF2F6AB8;
    private static final int BORDER = 0xFF3F72B0;
    private static final int ACCENT = 0xFF7CC8FF;
    private static final int TEXT = 0xFFE8F3FF;

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

    /** Mouse buttons currently held on the host, with how many touches hold each. */
    private final Map<Integer, Integer> mouseHeld = new HashMap<>();
    private TextView holdButton;
    private boolean holdOn = false;

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
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG});
        setBackground(panel);
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
        for (int i = 0; i < pageCount(); i++) {
            final int index = i;
            TextView tab = new TextView(getContext());
            tab.setText(i < ActionPadKeyMap.PAGES.size() ? ActionPadKeyMap.PAGES.get(i).title : ActionPadKeyMap.MOUSE_TAB_TITLE);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
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
        releaseAllMouse();
        pageIndex = Math.max(0, Math.min(index, pageCount() - 1));
        for (int i = 0; i < tabs.size(); i++) styleTab(tabs.get(i), i == pageIndex);

        pageHost.removeAllViews();
        if (pageIndex == ActionPadKeyMap.PAGES.size()) {
            pageHost.addView(buildMousePanel(), weighted(pageHost, 1f));
            return;
        }
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

    /** The key pages, plus the touchpad tab after them. */
    private static int pageCount() {
        return ActionPadKeyMap.PAGES.size() + 1;
    }

    /** The touchpad beside the number block: a drag surface above Left click, Hold and Right click. */
    private LinearLayout buildMousePanel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setMotionEventSplittingEnabled(true);

        panel.addView(new TouchSurface(getContext()), weighted(panel, 1f));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setMotionEventSplittingEnabled(true);
        row.addView(clickButton("Left", ActionPadKeyMap.MOUSE_LEFT), weighted(row, 1f));
        holdButton = holdButton();
        row.addView(holdButton, weighted(row, 1f));
        row.addView(clickButton("Right", ActionPadKeyMap.MOUSE_RIGHT), weighted(row, 1f));
        refreshHold();
        panel.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(64)));
        return panel;
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView clickButton(String label, final byte button) {
        final TextView view = plainButton(label);
        view.setBackground(keyBackground(false));
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    mouse(button, true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mouse(button, false);
                    break;
                default:
                    break;
            }
            return true;
        });
        return view;
    }

    /** Toggles the left button held down, for dragging windows and items without keeping a finger on Left. */
    @SuppressLint("ClickableViewAccessibility")
    private TextView holdButton() {
        final TextView view = plainButton("Hold");
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                holdOn = !holdOn;
                mouse(ActionPadKeyMap.MOUSE_LEFT, holdOn);
                refreshHold();
            }
            return true;
        });
        return view;
    }

    private TextView plainButton(String label) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(TEXT);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return view;
    }

    private void refreshHold() {
        if (holdButton == null) return;
        holdButton.setBackground(holdOn ? rect(ACCENT, ACCENT) : rect(FACE, BORDER));
        holdButton.setTextColor(holdOn ? BG : TEXT);
    }

    /**
     * Relative mouse control: one finger moves the host's cursor, a quick tap clicks left, a quick
     * two-finger tap clicks right.
     */
    private final class TouchSurface extends TextView {
        private float lastX, lastY, downX, downY, remX, remY;
        private long downTime;
        private int maxPointers;
        private boolean moved;
        private boolean resync;

        TouchSurface(Context context) {
            super(context);
            setText("Touchpad");
            setGravity(Gravity.CENTER);
            setTextColor(BORDER);
            setTypeface(Typeface.DEFAULT_BOLD);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            GradientDrawable d = rect(BG, BORDER);
            d.setColors(new int[]{BG, BG_TOP});
            setBackground(d);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float slop = dp((int) TAP_SLOP_DP);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = e.getEventTime();
                    downX = lastX = e.getX();
                    downY = lastY = e.getY();
                    remX = remY = 0;
                    moved = false;
                    resync = false;
                    maxPointers = 1;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    resync = true;
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    resync = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() != 1) return true;
                    if (resync) {
                        lastX = e.getX();
                        lastY = e.getY();
                        resync = false;
                        return true;
                    }
                    if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) moved = true;
                    if (moved) {
                        float density = getResources().getDisplayMetrics().density;
                        remX += (e.getX() - lastX) / density * TOUCHPAD_SPEED;
                        remY += (e.getY() - lastY) / density * TOUCHPAD_SPEED;
                        int ix = (int) remX, iy = (int) remY;
                        remX -= ix;
                        remY -= iy;
                        ix = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, ix));
                        iy = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, iy));
                        if (ix != 0 || iy != 0) sink.sendMouseMove((short) ix, (short) iy);
                    }
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && e.getEventTime() - downTime <= TAP_MAX_MS) {
                        tapClick(maxPointers >= 2 ? ActionPadKeyMap.MOUSE_RIGHT : ActionPadKeyMap.MOUSE_LEFT);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private void tapClick(final byte button) {
        mouse(button, true);
        handler.postDelayed(() -> mouse(button, false), CLICK_HOLD_MS);
    }

    // --- Mouse sending ---

    private void mouse(byte button, boolean down) {
        Integer held = mouseHeld.get((int) button);
        int n = held == null ? 0 : held;
        if (down) {
            if (n == 0) sink.sendMouseButton(button, true);
            mouseHeld.put((int) button, n + 1);
        } else {
            if (n == 0) return;
            if (n == 1) {
                mouseHeld.remove((int) button);
                sink.sendMouseButton(button, false);
            } else {
                mouseHeld.put((int) button, n - 1);
            }
        }
    }

    private void releaseAllMouse() {
        for (int button : new ArrayList<>(mouseHeld.keySet())) sink.sendMouseButton((byte) button, false);
        mouseHeld.clear();
        holdOn = false;
        refreshHold();
    }

    // --- Chat macros ---

    /** Types a chat line, one key press every {@link #MACRO_STEP_MS}. Armed modifiers are let go first. */
    private void runMacro(String text) {
        for (int mod : new ArrayList<>(armed)) disarm(mod);
        refreshModifierButtons();
        long delay = 0;
        for (int vk : ActionPadKeyMap.macroKeys(text)) {
            final short key = (short) vk;
            handler.postDelayed(() -> {
                sink.sendKey(key, true, (byte) 0);
                sink.sendKey(key, false, (byte) 0);
            }, delay);
            delay += MACRO_STEP_MS;
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
        if (slot.macro != null) {
            runMacro(slot.macro);
            return;
        }
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
        releaseAllMouse();
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
                button.setBackground(rect(ACCENT, ACCENT));
                button.setTextColor(BG);
            } else if (armed.contains(vk)) {
                button.setBackground(rect(FACE, ACCENT));
                button.setTextColor(ACCENT);
            } else {
                button.setBackground(rect(FACE, BORDER));
                button.setTextColor(TEXT);
            }
        }
        // Number buttons show a gold border while a modifier is armed, since it applies to them.
        for (TextView button : numberButtons) button.setActivated(!armed.isEmpty());
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackground(selected ? rect(FACE_PRESSED, ACCENT) : rect(BG, BORDER));
        tab.setTextColor(selected ? ACCENT : TEXT);
    }

    private StateListDrawable keyBackground(boolean number) {
        int face = number ? FACE_NUMBER : FACE;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rect(FACE_PRESSED, ACCENT));
        states.addState(new int[]{android.R.attr.state_activated}, rect(face, ACCENT));
        states.addState(new int[]{}, rect(face, BORDER));
        return states;
    }

    private GradientDrawable rect(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        d.setColors(new int[]{lighten(fill), fill});
        d.setCornerRadius(dp(10));
        d.setStroke(dp(2), stroke);
        return d;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 22);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 22);
        int b = Math.min(255, (c & 0xFF) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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

    /**
     * The pad is clickable so gap touches are swallowed, and Android hands a clickable parent's pressed
     * state to every child, which lit up all the buttons at once. Buttons set their own pressed state.
     */
    @Override
    protected void dispatchSetPressed(boolean pressed) {
    }

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}
