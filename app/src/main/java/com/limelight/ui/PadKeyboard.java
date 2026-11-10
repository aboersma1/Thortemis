package com.limelight.ui;

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

        LinearLayout digits = typingRow(0);
        digits.addView(special("Bksp", ActionPadKeyMap.VK_BACK, null, () -> capture.onBackspace()), kit.weighted(digits, 1.6f));
        addView(digits, kit.weighted(this, 1f));

        addView(typingRow(1), kit.weighted(this, 1f));

        LinearLayout home = typingRow(2);
        home.addView(special("Enter", ActionPadKeyMap.VK_RETURN, onSubmit, () -> capture.onDone()), kit.weighted(home, 1.8f));
        addView(home, kit.weighted(this, 1f));

        LinearLayout bottomLetters = kit.box(HORIZONTAL);
        bottomLetters.addView(shiftKey(), kit.weighted(bottomLetters, 1.6f));
        addTypingKeys(bottomLetters, 3);
        addView(bottomLetters, kit.weighted(this, 1f));

        LinearLayout last = kit.box(HORIZONTAL);
        if (capture == null) {
            last.addView(special("Esc", ActionPadKeyMap.VK_ESCAPE, onSubmit, null), kit.weighted(last, 1f));
            last.addView(special("Tab", ActionPadKeyMap.VK_TAB, null, null), kit.weighted(last, 1f));
            last.addView(kit.modifierButton(ActionPadKeyMap.VK_LCONTROL), kit.weighted(last, 1f));
            last.addView(kit.modifierButton(ActionPadKeyMap.VK_LMENU), kit.weighted(last, 1f));
        }
        last.addView(special("Space", ActionPadKeyMap.VK_SPACE, null, () -> capture.onChar(" ")), kit.weighted(last, capture == null ? 3f : 8f));
        if (capture == null) {
            String[] arrows = {"←", "↑", "↓", "→"};
            int[] codes = {0x25, 0x26, 0x28, 0x27};
            for (int i = 0; i < arrows.length; i++) {
                last.addView(special(arrows[i], codes[i], null, null), kit.weighted(last, 0.8f));
            }
        }
        addView(last, kit.weighted(this, 1f));
    }

    private boolean shifted() {
        return capture == null ? kit.input.isArmed(SHIFT) : captureShift;
    }

    private LinearLayout typingRow(int index) {
        LinearLayout row = kit.box(HORIZONTAL);
        addTypingKeys(row, index);
        return row;
    }

    private void addTypingKeys(LinearLayout row, int index) {
        for (String pair : ActionPadKeyMap.TYPING_ROWS[index]) {
            final String plain = pair.substring(0, 1), upper = pair.substring(1);
            ActionPadKeyMap.Slot slot = ActionPadKeyMap.key(plain, ActionPadKeyMap.vkOf(plain.charAt(0)));
            final TextView key = capture == null
                    ? kit.keyButton(slot, plain, 15, false, null)
                    : kit.tapButton(plain, 15, () -> capture.onChar(shifted() ? upper : plain));
            kit.addRefresher(() -> key.setText(shifted() ? upper : plain));
            row.addView(key, kit.weighted(row, 1f));
        }
    }

    /** A non-typing key: sends {@code vk} to the host, or runs {@code onCapture} while renaming (no key if that is null). */
    private TextView special(String label, int vk, Runnable afterRelease, Runnable onCapture) {
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
