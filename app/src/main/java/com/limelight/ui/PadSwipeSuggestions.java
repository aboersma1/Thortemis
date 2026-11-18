package com.limelight.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The words a swipe could have meant. The first was typed; the others are offered, and picking one makes it
 * the typed word and offers the rest again (including the one it replaced), so a wrong pick can be undone.
 * Pure Java, no Android dependencies.
 */
final class PadSwipeSuggestions {
    private final List<String> options;
    private int current;

    /** {@code options} are the candidates, best first; the best one has just been typed. */
    PadSwipeSuggestions(List<String> options) {
        this.options = new ArrayList<>(options);
    }

    /** The word now in the game. */
    String typed() {
        return options.get(current);
    }

    /** The other candidates, in their original order. */
    List<String> alternatives() {
        List<String> out = new ArrayList<>(options);
        out.remove(current);
        return out;
    }

    /** Keys to erase to take the typed word back: its letters and the space after it. */
    int eraseCount() {
        return typed().length() + 1;
    }

    /** Makes {@code word}, one of the candidates, the typed word. */
    void choose(String word) {
        int at = options.indexOf(word);
        if (at >= 0) current = at;
    }
}
