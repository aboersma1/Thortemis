package com.limelight.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Keyboard tab: a typing keyboard that sends keys to the host. Shift, Ctrl and Alt are the pad's own
 * (a tap arms one for the next key). With a {@link Capture} it types into the pad instead, for naming buttons.
 */
final class PadKeyboard extends LinearLayout {
    /** Receives typing while a button is being renamed. */
    interface Capture {
        void onChar(String text);

        void onBackspace();

        void onDone();
    }

    private static final int SHIFT = ActionPadKeyMap.VK_LSHIFT;

    private final PadKit kit;
    private final Capture capture;
    /** Runs after Enter or Esc, to leave a chat the keyboard was opened for. May be null. */
    private final Runnable onSubmit;
    private boolean captureShift;

    PadKeyboard(PadKit kit, Capture capture, Runnable onSubmit) {
        super(kit.context);
        this.kit = kit;
        this.capture = capture;
        this.onSubmit = onSubmit;
        setOrientation(VERTICAL);
        setMotionEventSplittingEnabled(true);

        // Renaming a button always uses the classic layout, without the keys that only make sense in the game.
        String[][] layers = PadKeyboardLayouts.layers(capture == null ? kit.settings.keyboardLayout : PadKeyboardLayouts.CLASSIC);
        for (String[] rows : layers) {
            LinearLayout layer = kit.box(VERTICAL);
            for (String row : rows) layer.addView(buildRow(row), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            layerViews.add(layer);
            addView(layer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }
        showLayer(0);
    }

    private final java.util.List<LinearLayout> layerViews = new java.util.ArrayList<>();

    private void showLayer(int index) {
        for (int i = 0; i < layerViews.size(); i++) layerViews.get(i).setVisibility(i == index ? VISIBLE : GONE);
    }

    private LinearLayout buildRow(String row) {
        LinearLayout out = kit.box(HORIZONTAL);
        for (String token : row.split(" ")) {
            View key = buildKey(PadKeyboardLayouts.keyName(token));
            if (key != null) out.addView(cell(key), new LayoutParams(0, LayoutParams.MATCH_PARENT, PadKeyboardLayouts.weight(token)));
        }
        return out;
    }

    /**
     * Holds a key so that the gap drawn around it still counts as the key. The cells touch each other, so a
     * finger between two keys always lands on one of them. A touch in the gap is passed to the key as if it
     * had landed on its nearest edge.
     */
    @SuppressLint("ClickableViewAccessibility")
    private View cell(final View key) {
        final FrameLayout cell = new FrameLayout(kit.context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        int gap = kit.dp(3);
        lp.setMargins(gap, gap, gap, gap);
        cell.addView(key, lp);
        cell.setOnTouchListener((v, event) -> {
            // Only touches the key did not take get here: the ones in the gap.
            MotionEvent edge = MotionEvent.obtain(event);
            // The key expects touches in its own coordinates, so move the point there and clamp it to the key.
            edge.setLocation(Math.max(0, Math.min(event.getX() - key.getLeft(), key.getWidth() - 1)),
                    Math.max(0, Math.min(event.getY() - key.getTop(), key.getHeight() - 1)));
            key.dispatchTouchEvent(edge);
            edge.recycle();
            return true;
        });
        return cell;
    }

    /** The key for a layout token, or null when it has no use here (while renaming only typing keys remain). */
    private View buildKey(String name) {
        if (name.equals("_")) return new View(kit.context);
        if (name.equals(PadKeyboardLayouts.SYMBOLS_KEY)) return kit.tapButton(name, 14, () -> showLayer(1));
        if (name.equals(PadKeyboardLayouts.LETTERS_KEY)) return kit.tapButton(name, 14, () -> showLayer(0));
        if (name.equals("Shift")) return shiftKey();
        boolean named = PadKeyboardLayouts.namedKeyVk(name) >= 0;
        if (!named && name.length() == 1) return typingKey(name.charAt(0));
        if (capture != null && !name.equals("Bksp") && !name.equals("Enter") && !name.equals("Space")) return null;
        if (name.equals("Ctrl")) return kit.modifierButton(ActionPadKeyMap.VK_LCONTROL);
        if (name.equals("Alt")) return kit.modifierButton(ActionPadKeyMap.VK_LMENU);
        switch (name) {
            case "Bksp": return special(name, null, () -> capture.onBackspace());
            case "Enter": return special(name, onSubmit, () -> capture.onDone());
            case "Esc": return special(name, onSubmit, null);
            case "Space": return special(name, null, () -> capture.onChar(" "));
            default: return special(name, null, null);
        }
    }

    private boolean shifted() {
        return capture == null ? kit.input.isArmed(SHIFT) : captureShift;
    }

    /** A letter, digit or punctuation key; it shows its shifted character while Shift is armed. */
    private TextView typingKey(char c) {
        String pair = ActionPadKeyMap.typingPair(c);
        final String plain = pair.substring(0, 1), upper = pair.substring(1);
        ActionPadKeyMap.Slot slot = ActionPadKeyMap.key(plain, ActionPadKeyMap.vkOf(c));
        final TextView key = capture == null
                ? kit.keyButton(slot, plain, 15, false, null)
                : kit.tapButton(plain, 15, () -> capture.onChar(shifted() ? upper : plain));
        kit.addRefresher(() -> key.setText(shifted() ? upper : plain));
        return key;
    }

    /** A non-typing key: sends {@code vk} to the host, or runs {@code onCapture} while renaming (no key if that is null). */
    private TextView special(String label, Runnable afterRelease, Runnable onCapture) {
        int vk = PadKeyboardLayouts.namedKeyVk(label);
        if (capture == null) return kit.keyButton(ActionPadKeyMap.key(label, vk), label, 14, false, afterRelease);
        return kit.tapButton(label, 14, onCapture);
    }

    private TextView shiftKey() {
        if (capture == null) return kit.modifierButton(SHIFT);
        final TextView key = kit.tapButton("Shift", 14, () -> {
            captureShift = !captureShift;
            kit.refresh();
        });
        kit.addRefresher(() -> key.setTextColor(captureShift ? kit.theme.accent : kit.theme.text));
        return key;
    }
}
