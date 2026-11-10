package com.limelight.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Button tables for {@link ExternalActionPadView}: WoW's default key bindings, as Windows virtual-key
 * codes (what Moonlight sends to the host). Pure data, no Android dependencies.
 *
 * To use different keys, edit the tables below.
 */
public final class ActionPadKeyMap {
    private ActionPadKeyMap() {}

    // Windows virtual-key codes. Letters and digits are their ASCII codes.
    public static final int VK_TAB = 0x09;
    public static final int VK_RETURN = 0x0D;
    public static final int VK_SPACE = 0x20;
    public static final int VK_ESCAPE = 0x1B;
    public static final int VK_PRIOR = 0x21;    // Page Up
    public static final int VK_NEXT = 0x22;     // Page Down
    public static final int VK_SNAPSHOT = 0x2C; // Print Screen
    public static final int VK_DIVIDE = 0x6F;   // Numpad /
    public static final int VK_F1 = 0x70;
    public static final int VK_NUMLOCK = 0x90;
    public static final int VK_LSHIFT = 0xA0;
    public static final int VK_LCONTROL = 0xA2;
    public static final int VK_LMENU = 0xA4;    // Left Alt
    public static final int VK_OEM_PLUS = 0xBB; // '=' on a US keyboard
    public static final int VK_OEM_MINUS = 0xBD;
    public static final int VK_OEM_PERIOD = 0xBE;
    public static final int VK_OEM_2 = 0xBF;    // '/' on a US keyboard

    // Same values as MouseButtonPacket.BUTTON_*.
    public static final byte MOUSE_LEFT = 0x01;
    public static final byte MOUSE_RIGHT = 0x03;

    /** The touchpad tab, shown after the key pages. */
    public static final String MOUSE_TAB_ID = "mouse";
    public static final String MOUSE_TAB_TITLE = "Mouse";

    // Same values as KeyboardPacket.MODIFIER_*.
    public static final byte MOD_SHIFT = 0x01;
    public static final byte MOD_CTRL = 0x02;
    public static final byte MOD_ALT = 0x04;

    /** The modifier keys the pad can hold, in display order. */
    public static final int[] MODIFIER_KEYS = {VK_LSHIFT, VK_LCONTROL, VK_LMENU};

    /** The KeyboardPacket modifier flag for a modifier key, or 0 for any other key. */
    public static byte modifierFlag(int vk) {
        switch (vk) {
            case VK_LSHIFT: return MOD_SHIFT;
            case VK_LCONTROL: return MOD_CTRL;
            case VK_LMENU: return MOD_ALT;
            default: return 0;
        }
    }

    /**
     * One button. {@code mods} are held around {@code vk} (Shift+Tab). An {@code isolated} button
     * ignores any modifier the user is holding on the pad, so only its own modifiers reach the game.
     */
    public static final class Slot {
        public final String label;
        public final int vk;
        public final int[] mods;
        public final boolean isolated;
        /** Text typed into the chat box (Enter, the text, Enter) instead of a single key, or null. */
        public final String macro;

        Slot(String label, int vk, boolean isolated, int... mods) {
            this.label = label;
            this.vk = vk;
            this.mods = mods;
            this.isolated = isolated;
            this.macro = null;
        }

        Slot(String label, String macro) {
            this.label = label;
            this.vk = VK_RETURN;
            this.mods = new int[0];
            this.isolated = true;
            this.macro = macro;
        }
    }

    public static final class Page {
        /** Stable id, used by the tab setting. */
        public final String id;
        public final String title;
        public final List<Slot> slots;

        Page(String id, String title, Slot... slots) {
            this.id = id;
            this.title = title;
            this.slots = Collections.unmodifiableList(Arrays.asList(slots));
        }
    }

    /** A page button: sends its own modifiers only. */
    private static Slot page(String label, int vk, int... mods) {
        return new Slot(label, vk, true, mods);
    }

    /** A chat button: opens the chat box, types {@code text}, and sends it. */
    private static Slot macro(String label, String text) {
        return new Slot(label, text);
    }

    /** A number-block button: takes whatever Shift, Ctrl or Alt is armed on the pad. */
    private static Slot number(String label, int vk) {
        return new Slot(label, vk, false);
    }

    /** The pages, in tab order. A page holds at most 12 buttons. */
    public static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
            new Page("interface", "Interface",
                    page("Character", 'C'),
                    page("Spellbook", 'P'),
                    page("Talents", 'N'),
                    page("Quest Log", 'L'),
                    page("Map", 'M'),
                    page("Social", 'O'),
                    page("Guild", 'J'),
                    page("Group Finder", 'I'),
                    page("Achievements", 'Y'),
                    page("Professions", 'K'),
                    page("Bags", 'B'),
                    // Escape opens the game menu ("System") when nothing else is open.
                    page("System", VK_ESCAPE)),
            new Page("target", "Target",
                    page("Self", VK_F1),
                    page("Party 1", VK_F1 + 1),
                    page("Party 2", VK_F1 + 2),
                    page("Party 3", VK_F1 + 3),
                    page("Party 4", VK_F1 + 4),
                    page("Next enemy", VK_TAB),
                    page("Previous enemy", VK_TAB, VK_LSHIFT)),
            new Page("utility", "Utility",
                    page("Sheathe weapons", 'Z'),
                    page("Auto-run", VK_NUMLOCK),
                    page("Walk / run", VK_DIVIDE),
                    page("Sit / stand", 'X'),
                    page("Hide UI", 'Z', VK_LMENU),
                    page("Enemy nameplates", 'V'),
                    page("Friendly nameplates", 'V', VK_LSHIFT),
                    page("Chat page up", VK_PRIOR),
                    page("Chat page down", VK_NEXT),
                    page("Screenshot", VK_SNAPSHOT)),
            new Page("chat", "Chat",
                    page("Open chat", VK_RETURN),
                    page("Slash command", VK_OEM_2),
                    page("Reply", 'R'),
                    macro("Sit", "/sit"),
                    macro("Dance", "/dance"),
                    macro("Follow", "/follow"),
                    macro("Assist", "/assist"),
                    macro("Dismount", "/dismount")),
            new Page("fkeys", "F-Keys", fKeys())));

    /** Ids of every tab, in display order. */
    public static List<String> tabIds() {
        List<String> ids = new ArrayList<>();
        for (Page page : PAGES) ids.add(page.id);
        ids.add(MOUSE_TAB_ID);
        return ids;
    }

    private static Slot[] fKeys() {
        Slot[] out = new Slot[12];
        for (int i = 0; i < out.length; i++) out[i] = page("F" + (i + 1), VK_F1 + i);
        return out;
    }

    /**
     * The key presses that type {@code text} into WoW's chat box: Enter, each character, Enter.
     * Supports lower-case letters, digits, space, '/' and '.', which need no Shift on a US layout.
     */
    public static int[] macroKeys(String text) {
        int[] keys = new int[text.length() + 2];
        keys[0] = VK_RETURN;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int vk;
            if (c >= 'a' && c <= 'z') vk = Character.toUpperCase(c);
            else if (c >= '0' && c <= '9') vk = c;
            else if (c == ' ') vk = VK_SPACE;
            else if (c == '/') vk = VK_OEM_2;
            else if (c == '.') vk = VK_OEM_PERIOD;
            else throw new IllegalArgumentException("Unsupported macro character '" + c + "' in " + text);
            keys[i + 1] = vk;
        }
        keys[keys.length - 1] = VK_RETURN;
        return keys;
    }

    /** Action Bar 1, slots 1 to 12, shown as the 4 x 3 number block. */
    public static final List<Slot> NUMBERS = Collections.unmodifiableList(buildNumbers());

    private static List<Slot> buildNumbers() {
        List<Slot> out = new ArrayList<>();
        for (char c = '1'; c <= '9'; c++) out.add(number(String.valueOf(c), c));
        out.add(number("0", '0'));
        out.add(number("-", VK_OEM_MINUS));
        out.add(number("=", VK_OEM_PLUS));
        return out;
    }

    /** Short label for a modifier button. */
    public static String modifierLabel(int vk) {
        switch (vk) {
            case VK_LSHIFT: return "Shift";
            case VK_LCONTROL: return "Ctrl";
            case VK_LMENU: return "Alt";
            default: return "";
        }
    }
}
