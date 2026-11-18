package com.limelight.ui;

import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the pad sends, and the state behind it: which of Shift, Ctrl and Alt are armed or locked,
 * which buttons are down, which mouse buttons are held. Views only report touches; this class turns
 * them into key and mouse events for the {@link ExternalActionPadView.KeySink}.
 *
 * Every press is matched by a release, however the touches end ({@link #releaseAll} lets go of the rest),
 * so no key or button is left stuck on the host. Pure Java: timing goes through a {@link Scheduler}.
 */
final class PadInput {
    /** Runs a task later on the UI thread. */
    interface Scheduler {
        void post(Runnable task, long delayMs);

        void cancelAll();
    }

    /** A button that is down. Hand it back to {@link #release}. */
    static final class Press {
        final Slot slot;
        final List<Integer> pressedMods = new ArrayList<>();
        byte mask;
        boolean isolated;

        Press(Slot slot) {
            this.slot = slot;
        }
    }

    /** How a typed word was capitalised: not at all, its first letter (Shift armed), or every letter (Shift locked). */
    enum Caps { NONE, FIRST, ALL }

    /** Gap between the key presses of a chat macro. */
    static final long MACRO_STEP_MS = 35;
    /** How long a tap-click holds the button down. */
    static final long CLICK_HOLD_MS = 45;

    private final ExternalActionPadView.KeySink sink;
    private final Scheduler scheduler;
    /** Called when the armed or locked modifiers, or the hold toggle, change. */
    Runnable onChanged = () -> { };

    /** Armed modifiers, in the order they were armed. */
    private final Set<Integer> armed = new LinkedHashSet<>();
    private final Set<Integer> locked = new HashSet<>();
    private final List<Press> down = new ArrayList<>();
    /** How many isolated buttons are down. While any is, armed modifiers are let go on the host. */
    private int isolatedDown = 0;
    private final Map<Integer, Integer> mouseHeld = new HashMap<>();
    private boolean holdOn = false;

    PadInput(ExternalActionPadView.KeySink sink, Scheduler scheduler) {
        this.sink = sink;
        this.scheduler = scheduler;
    }

    // --- Modifiers ---

    boolean isArmed(int vk) {
        return armed.contains(vk);
    }

    boolean isLocked(int vk) {
        return locked.contains(vk);
    }

    boolean anyArmed() {
        return !armed.isEmpty();
    }

    /** A tap on a modifier button: arms it for the next key, or lets it go. */
    void toggleModifier(int vk) {
        if (armed.contains(vk)) disarm(vk); else arm(vk, false);
        onChanged.run();
    }

    /** A long press on a modifier button: keeps it down until it is tapped off. */
    void lockModifier(int vk) {
        arm(vk, true);
        onChanged.run();
    }

    void releaseModifiers() {
        for (int mod : new ArrayList<>(armed)) disarm(mod);
        onChanged.run();
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

    /** After a key, armed modifiers that are not locked let go. */
    private void releaseOneShot() {
        boolean changed = false;
        for (int mod : new ArrayList<>(armed)) {
            if (!locked.contains(mod)) {
                disarm(mod);
                changed = true;
            }
        }
        if (changed) onChanged.run();
    }

    /** Lets go of armed modifiers on the host (they stay armed on the pad) while an isolated button is down. */
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

    // --- Keys ---

    /**
     * Presses a button. Returns what to pass to {@link #release} when the finger lifts, or null for a
     * chat macro, which types itself and has nothing to release.
     */
    Press press(Slot slot) {
        if (slot.macro != null) {
            runMacro(slot.macro, !slot.opensKeyboard);
            return null;
        }
        Press p = new Press(slot);
        // A button with its own modifiers, or an isolated one, must not pick up Shift, Ctrl or Alt held on the pad.
        p.isolated = slot.isolated || slot.mods.length > 0;
        if (p.isolated && isolatedDown++ == 0) suspendArmed();
        byte mask = heldMask();
        for (int mod : slot.mods) {
            sink.sendKey((short) mod, true, mask);
            mask |= ActionPadKeyMap.modifierFlag(mod);
            p.pressedMods.add(mod);
        }
        sink.sendKey((short) slot.vk, true, mask);
        p.mask = mask;
        down.add(p);
        return p;
    }

    void release(Press p) {
        if (p == null || !down.remove(p)) return;
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

    /** Types a chat line, one key press every {@link #MACRO_STEP_MS}; with {@code send} false it is left in the box. Armed modifiers are let go first. */
    private void runMacro(String text, boolean send) {
        releaseModifiers();
        long delay = 0;
        for (int code : ActionPadKeyMap.macroKeys(text, send)) {
            postCode(code, delay);
            delay += MACRO_STEP_MS;
        }
    }

    /** Presses and releases a key code later, with Shift held around it when the code carries {@link ActionPadKeyMap#MACRO_SHIFT}. */
    private void postCode(int code, long delay) {
        final short key = (short) (code & 0xFF);
        final boolean shift = (code & ActionPadKeyMap.MACRO_SHIFT) != 0;
        scheduler.post(() -> {
            byte mask = 0;
            if (shift) {
                sink.sendKey((short) ActionPadKeyMap.VK_LSHIFT, true, mask);
                mask = ActionPadKeyMap.modifierFlag(ActionPadKeyMap.VK_LSHIFT);
            }
            sink.sendKey(key, true, mask);
            sink.sendKey(key, false, mask);
            if (shift) sink.sendKey((short) ActionPadKeyMap.VK_LSHIFT, false, (byte) 0);
        }, delay);
    }

    /**
     * Types a word and a space, as a swipe on the keyboard does. Armed Shift capitalises the first letter, or
     * every letter while it is locked. Ctrl and Alt are let go, since a word is not a shortcut.
     */
    Caps typeWord(String word) {
        Caps caps = isLocked(ActionPadKeyMap.VK_LSHIFT) ? Caps.ALL : isArmed(ActionPadKeyMap.VK_LSHIFT) ? Caps.FIRST : Caps.NONE;
        for (int mod : new ArrayList<>(armed)) if (mod != ActionPadKeyMap.VK_LSHIFT) disarm(mod);
        onChanged.run();
        int[] codes = ActionPadKeyMap.typingKeys(word + " ");
        final byte lockedMask = isLocked(ActionPadKeyMap.VK_LSHIFT) ? ActionPadKeyMap.modifierFlag(ActionPadKeyMap.VK_LSHIFT) : 0;
        long delay = 0;
        for (int i = 0; i < codes.length; i++) {
            final short key = (short) (codes[i] & 0xFF);
            if (i == 0 && isArmed(ActionPadKeyMap.VK_LSHIFT)) {
                // The first letter goes out now, while the armed Shift is down; a one-shot Shift lets go after it.
                byte mask = heldMask();
                sink.sendKey(key, true, mask);
                sink.sendKey(key, false, mask);
                releaseOneShot();
                continue;
            }
            scheduler.post(() -> {
                sink.sendKey(key, true, lockedMask);
                sink.sendKey(key, false, lockedMask);
            }, delay);
            delay += MACRO_STEP_MS;
        }
        return caps;
    }

    /**
     * Takes back a word typed by {@link #typeWord} and types another in its place: {@code erase} Backspaces
     * (the word and its space), then {@code word} and a space, capitalised the way the first one was.
     */
    void replaceWord(int erase, String word, Caps caps) {
        long delay = 0;
        for (int i = 0; i < erase; i++) {
            postCode(ActionPadKeyMap.VK_BACK, delay);
            delay += MACRO_STEP_MS;
        }
        int[] codes = ActionPadKeyMap.typingKeys(word + " ");
        for (int i = 0; i < codes.length; i++) {
            boolean letter = codes[i] >= 'A' && codes[i] <= 'Z';
            boolean capital = letter && (caps == Caps.ALL || caps == Caps.FIRST && i == 0);
            postCode(capital ? codes[i] | ActionPadKeyMap.MACRO_SHIFT : codes[i], delay);
            delay += MACRO_STEP_MS;
        }
    }

    // --- Mouse ---

    void moveMouse(short dx, short dy) {
        sink.sendMouseMove(dx, dy);
    }

    void scroll(byte notches) {
        sink.sendMouseScroll(notches);
    }

    void mouseButton(byte button, boolean isDown) {
        Integer held = mouseHeld.get((int) button);
        int n = held == null ? 0 : held;
        if (isDown) {
            if (n == 0) sink.sendMouseButton(button, true);
            mouseHeld.put((int) button, n + 1);
        } else if (n == 1) {
            mouseHeld.remove((int) button);
            sink.sendMouseButton(button, false);
        } else if (n > 1) {
            mouseHeld.put((int) button, n - 1);
        }
    }

    /** A quick click: the button goes down now and up shortly after. */
    void click(final byte button) {
        mouseButton(button, true);
        scheduler.post(() -> mouseButton(button, false), CLICK_HOLD_MS);
    }

    boolean isHoldOn() {
        return holdOn;
    }

    /** Toggles the left button held down, for dragging without keeping a finger on Left. */
    void toggleHold() {
        holdOn = !holdOn;
        mouseButton(ActionPadKeyMap.MOUSE_LEFT, holdOn);
        onChanged.run();
    }

    // --- Letting go ---

    /** Lets go of every key that is down, and any modifier held aside for an isolated button. */
    void releaseKeys() {
        for (Press p : new ArrayList<>(down)) release(p);
    }

    void releaseMouse() {
        for (int button : new ArrayList<>(mouseHeld.keySet())) sink.sendMouseButton((byte) button, false);
        mouseHeld.clear();
        if (holdOn) {
            holdOn = false;
            onChanged.run();
        }
    }

    /** Lets go of everything the pad holds: keys, mouse buttons, armed and locked modifiers, pending typing. */
    void releaseAll() {
        scheduler.cancelAll();
        releaseKeys();
        releaseMouse();
        releaseModifiers();
    }
}
