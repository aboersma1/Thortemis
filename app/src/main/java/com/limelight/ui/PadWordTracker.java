package com.limelight.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows the word being typed on the keyboard tab, so the suggestion strip can offer completions. It only
 * knows the keys pressed on the pad, not what is in the game's chat box, so anything that moves the cursor or
 * ends the word starts it again from nothing. Pure Java, no Android dependencies.
 */
final class PadWordTracker {
    private final StringBuilder letters = new StringBuilder();
    private final List<Boolean> shifted = new ArrayList<>();

    /** A lower-case letter or apostrophe was typed; {@code shift} says Shift was down for it. */
    void letter(char c, boolean shift) {
        letters.append(c);
        shifted.add(shift);
    }

    void backspace() {
        if (letters.length() == 0) return;
        letters.deleteCharAt(letters.length() - 1);
        shifted.remove(shifted.size() - 1);
    }

    /** Space, Enter, a digit, an arrow: the word is over, or can no longer be followed. */
    void reset() {
        letters.setLength(0);
        shifted.clear();
    }

    /** The word so far, in lower case. */
    String prefix() {
        return letters.toString();
    }

    /** How the word so far was capitalised, so a completion can be capitalised the same way. */
    PadInput.Caps caps() {
        if (shifted.isEmpty() || !shifted.get(0)) return PadInput.Caps.NONE;
        if (shifted.size() < 2) return PadInput.Caps.FIRST;
        for (boolean s : shifted) if (!s) return PadInput.Caps.FIRST;
        return PadInput.Caps.ALL;
    }
}
