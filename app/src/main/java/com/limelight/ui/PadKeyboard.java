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
    /**
     * Swipe typing and suggestions are on (a setting, beta): a strip above the keys offers words, a slide across
     * the letters types one, and letters type when the finger lifts (so a tap can be told from a swipe).
     */
    private final boolean swipe;
    private final PadWordTracker tracker = new PadWordTracker();
    private final TextView[] chips = new TextView[3];
    private final String[] chipWords = new String[3];
    /** The swiped word and its alternatives, or null when the strip is showing completions or nothing. */
    private PadSwipeSuggestions suggestions;
    private PadInput.Caps swipeCaps = PadInput.Caps.NONE;
    private final Runnable clearSuggestions = () -> {
        suggestions = null;
        refreshStrip();
    };
    /** How long a swiped word's alternatives stay on offer. */
    private static final long SUGGESTION_MS = 5000;

    PadKeyboard(PadKit kit, Capture capture, Runnable onSubmit) {
        super(kit.context);
        this.kit = kit;
        this.capture = capture;
        this.onSubmit = onSubmit;
        this.swipe = capture == null && kit.settings.swipeTyping;
        if (swipe) PadSwipeDictionary.load(kit.context);
        setOrientation(VERTICAL);
        setMotionEventSplittingEnabled(true);
        // The strip is always there while the setting is on, so the keys never change size or place.
        if (swipe) addView(buildStrip(), new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(44)));

        // Renaming a button always uses the classic layout, without the keys that only make sense in the game.
        String[][] layers = PadKeyboardLayouts.layers(capture == null ? kit.settings.keyboardLayout : PadKeyboardLayouts.CLASSIC);
        for (final String[] rows : layers) {
            // Swipe typing watches the first layer, where the letters are.
            LinearLayout layer = swipe && rows == layers[0]
                    ? new PadSwipeLayer(kit, rows, this::onSwipe)
                    : kit.box(VERTICAL);
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
        final boolean letter = c >= 'a' && c <= 'z';
        // Digits and punctuation end the word being followed; an apostrophe belongs to it.
        Runnable press = null;
        if (swipe) press = c == '\'' ? () -> onLetter('\'') : this::onOther;
        final TextView key;
        if (capture != null) {
            key = kit.tapButton(plain, 15, () -> capture.onChar(shifted() ? upper : plain));
        } else if (swipe && letter) {
            key = swipeLetter(slot, plain, c);
        } else {
            key = kit.keyButton(slot, plain, 15, false, null, press);
        }
        kit.addRefresher(() -> key.setText(shifted() ? upper : plain));
        return key;
    }

    /**
     * A letter while swipe typing is on. It types when the finger lifts, not when it lands, because only then
     * is it known that the touch was a tap and not the start of a swipe.
     */
    private TextView swipeLetter(final ActionPadKeyMap.Slot slot, String label, final char letter) {
        return kit.pressButton(label, 15, kit.theme.face, kit.theme.border, kit.theme.accent, new PadKit.PressHandler() {
            @Override
            public void down() { }

            @Override
            public void up(boolean cancelled) {
                if (cancelled) return;
                onLetter(letter);
                kit.input.release(kit.input.press(slot));
            }
        });
    }

    // --- Suggestion strip ---

    /** Three word slots across the top: completions while typing, the swiped word and its alternatives after a swipe. */
    private LinearLayout buildStrip() {
        PadSwipeDictionary.load(kit.context);
        LinearLayout strip = kit.box(HORIZONTAL);
        for (int i = 0; i < chips.length; i++) {
            final int slot = i;
            chips[i] = kit.tapButton("", 15, () -> onChip(slot));
            chips[i].setSingleLine(true);
            chips[i].setVisibility(INVISIBLE);
            strip.addView(chips[i], kit.weighted(strip, 1f));
        }
        return strip;
    }

    private static String capitalised(String word, PadInput.Caps caps) {
        if (caps == PadInput.Caps.ALL) return word.toUpperCase(java.util.Locale.US);
        if (caps == PadInput.Caps.FIRST) return word.substring(0, 1).toUpperCase(java.util.Locale.US) + word.substring(1);
        return word;
    }

    /** Fills the strip: completions of the word being typed, else the last swipe, else nothing. */
    private void refreshStrip() {
        if (!swipe) return;
        String[] labels = new String[chips.length];
        java.util.Arrays.fill(chipWords, null);
        boolean dim = false;
        String prefix = tracker.prefix();
        PadSwipe.Dictionary dictionary = PadSwipeDictionary.get();
        if (!prefix.isEmpty()) {
            if (dictionary != null) {
                java.util.List<String> words = dictionary.complete(prefix, chips.length);
                for (int i = 0; i < words.size(); i++) {
                    chipWords[i] = words.get(i);
                    labels[i] = capitalised(words.get(i), tracker.caps());
                }
            }
        } else if (suggestions != null) {
            // The word that was typed sits first, dimmed; tapping an alternative swaps it in.
            labels[0] = capitalised(suggestions.typed(), swipeCaps);
            dim = true;
            java.util.List<String> others = suggestions.alternatives();
            for (int i = 0; i < others.size() && i + 1 < chips.length; i++) {
                chipWords[i + 1] = others.get(i);
                labels[i + 1] = capitalised(others.get(i), swipeCaps);
            }
        }
        for (int i = 0; i < chips.length; i++) {
            chips[i].setText(labels[i] == null ? "" : labels[i]);
            chips[i].setVisibility(labels[i] == null ? INVISIBLE : VISIBLE);
            chips[i].setAlpha(dim && i == 0 ? 0.55f : 1f);
        }
    }

    private void onChip(int slot) {
        String word = chipWords[slot];
        if (word == null) return;
        removeCallbacks(clearSuggestions);
        if (!tracker.prefix().isEmpty()) {
            // Finish the word being typed: take back what was typed of it and type the whole word and a space.
            kit.input.replaceWord(tracker.prefix().length(), word, tracker.caps());
            tracker.reset();
        } else if (suggestions != null) {
            kit.input.replaceWord(suggestions.eraseCount(), word, swipeCaps);
            suggestions.choose(word);
            postDelayed(clearSuggestions, SUGGESTION_MS);
        }
        refreshStrip();
    }

    private void onSwipe(java.util.List<String> words) {
        swipeCaps = kit.input.typeWord(words.get(0));
        tracker.reset();
        suggestions = new PadSwipeSuggestions(words);
        removeCallbacks(clearSuggestions);
        postDelayed(clearSuggestions, SUGGESTION_MS);
        refreshStrip();
    }

    /** A letter or apostrophe is about to be typed on the keyboard. */
    private void onLetter(char c) {
        if (kit.input.isArmed(ActionPadKeyMap.VK_LCONTROL) || kit.input.isArmed(ActionPadKeyMap.VK_LMENU)) {
            onOther();
            return;
        }
        suggestions = null;
        tracker.letter(c, kit.input.isArmed(SHIFT));
        refreshStrip();
    }

    private void onBackspace() {
        suggestions = null;
        tracker.backspace();
        refreshStrip();
    }

    /** Any other key: the word is over, or the cursor moved, so there is nothing to complete. */
    private void onOther() {
        suggestions = null;
        tracker.reset();
        refreshStrip();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(clearSuggestions);
        super.onDetachedFromWindow();
    }

    /** A non-typing key: sends {@code vk} to the host, or runs {@code onCapture} while renaming (no key if that is null). */
    private TextView special(String label, Runnable afterRelease, Runnable onCapture) {
        int vk = PadKeyboardLayouts.namedKeyVk(label);
        if (capture == null) {
            Runnable onPress = !swipe ? null : label.equals("Bksp") ? this::onBackspace : this::onOther;
            return kit.keyButton(ActionPadKeyMap.key(label, vk), label, 14, false, afterRelease, onPress);
        }
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
