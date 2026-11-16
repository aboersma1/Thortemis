package com.limelight.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Button tables for the action pad: WoW's default key bindings as Windows virtual-key codes (what
 * Moonlight sends to the host), plus the key list used by the remap screen and the on-screen keyboard.
 * Pure data, no Android dependencies. Players change keys in the app (see {@link PadConfig}); edit the
 * tables below to change the defaults.
 */
public final class ActionPadKeyMap {
    private ActionPadKeyMap() {}

    // Windows virtual-key codes. Letters and digits are their ASCII codes.
    public static final int VK_BACK = 0x08;
    public static final int VK_TAB = 0x09;
    public static final int VK_RETURN = 0x0D;
    public static final int VK_ESCAPE = 0x1B;
    public static final int VK_SPACE = 0x20;
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

    // Same values as KeyboardPacket.MODIFIER_* and MouseButtonPacket.BUTTON_*.
    public static final byte MOD_SHIFT = 0x01;
    public static final byte MOD_CTRL = 0x02;
    public static final byte MOD_ALT = 0x04;
    public static final byte MOUSE_LEFT = 0x01;
    public static final byte MOUSE_RIGHT = 0x03;

    /** The modifier keys the pad can hold, in display order; each has the {@link #modifierFlag} at the same index. */
    public static final int[] MODIFIER_KEYS = {VK_LSHIFT, VK_LCONTROL, VK_LMENU};
    private static final String[] MODIFIER_LABELS = {"Shift", "Ctrl", "Alt"};
    private static final byte[] MODIFIER_FLAGS = {MOD_SHIFT, MOD_CTRL, MOD_ALT};

    /** The KeyboardPacket modifier flag for a modifier key, or 0 for any other key. */
    public static byte modifierFlag(int vk) {
        int i = indexOf(MODIFIER_KEYS, vk);
        return i < 0 ? 0 : MODIFIER_FLAGS[i];
    }

    /** Short label for a modifier button. */
    public static String modifierLabel(int vk) {
        int i = indexOf(MODIFIER_KEYS, vk);
        return i < 0 ? "" : MODIFIER_LABELS[i];
    }

    /** The modifier keys named by a flag mask (bit 0 Shift, bit 1 Ctrl, bit 2 Alt), in display order. */
    public static int[] modsFromMask(int mask) {
        int[] out = new int[Integer.bitCount(mask & 7)];
        int n = 0;
        for (int i = 0; i < MODIFIER_KEYS.length; i++) {
            if ((mask & MODIFIER_FLAGS[i]) != 0) out[n++] = MODIFIER_KEYS[i];
        }
        return out;
    }

    public static int maskOf(int[] mods) {
        int mask = 0;
        for (int mod : mods) mask |= modifierFlag(mod);
        return mask;
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }

    // --- Buttons ---

    /**
     * One button. {@code mods} are held around {@code vk} (Shift+Tab). An {@code isolated} button
     * ignores any modifier the user is holding on the pad, so only its own modifiers reach the game.
     * {@code id} is the default label, which never changes, so saved edits keep finding the button.
     */
    public static final class Slot {
        public final String id;
        public final String label;
        public final int vk;
        public final int[] mods;
        public final boolean isolated;
        /** Text typed into the chat box (Enter, the text, Enter) instead of a single key, or null. */
        public final String macro;
        /** Brings up the on-screen keyboard after the key, for typing a chat message. */
        public final boolean opensKeyboard;
        /** Outline colour chosen by the player (ARGB), or 0 for the theme's. */
        public final int color;

        private Slot(String id, String label, int vk, int[] mods, boolean isolated, String macro, boolean opensKeyboard, int color) {
            this.id = id;
            this.label = label;
            this.vk = vk;
            this.mods = mods;
            this.isolated = isolated;
            this.macro = macro;
            this.opensKeyboard = opensKeyboard;
            this.color = color;
        }

        /** This button with the player's changes applied. */
        public Slot with(String label, int vk, int[] mods, int color) {
            return new Slot(id, label, vk, mods, isolated, macro, opensKeyboard, color);
        }

        /** Same, with the player's own chat text in place of the default. Ignored for a button that is not a chat macro. */
        public Slot with(String label, int vk, int[] mods, int color, String message) {
            String text = macro != null && message != null && canType(message) ? message : macro;
            return new Slot(id, label, vk, mods, isolated, text, opensKeyboard, color);
        }
    }

    public static final class Page {
        /** Stable id, used by the tab setting. */
        public final String id;
        public final String title;
        public final List<Slot> slots;
        /** Takes the whole screen, with no modifier row or number block beside it, and may hold up to {@link #MAX_FULL_PAGE} buttons. */
        public final boolean fullWidth;

        Page(String id, String title, Slot... slots) {
            this(id, title, false, slots);
        }

        Page(String id, String title, boolean fullWidth, Slot... slots) {
            this.id = id;
            this.title = title;
            this.fullWidth = fullWidth;
            this.slots = Collections.unmodifiableList(Arrays.asList(slots));
        }
    }

    /** A page button: sends its own modifiers only. */
    private static Slot page(String label, int vk, int... mods) {
        return new Slot(label, label, vk, mods, true, null, false, 0);
    }

    /** A chat button: sends its key, then brings up the keyboard. */
    private static Slot chat(String label, int vk) {
        return new Slot(label, label, vk, new int[0], true, null, true, 0);
    }

    /** A chat button that types {@code text} into the chat box and sends it. */
    private static Slot macro(String label, String text) {
        return new Slot(label, label, VK_RETURN, new int[0], true, text, false, 0);
    }

    /** A button that takes whatever Shift, Ctrl or Alt is armed on the pad. */
    private static Slot number(String label, int vk, int... mods) {
        return new Slot(label, label, vk, mods, false, null, false, 0);
    }

    /** A key on the on-screen keyboard: takes armed modifiers, like a number button. */
    public static Slot key(String label, int vk) {
        return number(label, vk);
    }

    /** Most buttons on a full-screen page, laid out in rows of {@link #FULL_PAGE_ROWS}. */
    public static final int MAX_FULL_PAGE = 24;
    public static final int FULL_PAGE_ROWS = 6;

    /** The pages, in tab order. A page beside the number block holds at most 12 buttons. */
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
                    chat("Open chat", VK_RETURN),
                    chat("Slash command", VK_OEM_2),
                    page("Reply", 'R'),
                    macro("Sit", "/sit"),
                    macro("Dance", "/dance"),
                    macro("Follow", "/follow"),
                    macro("Assist", "/assist"),
                    macro("Dismount", "/dismount")),
            // Plain lines for whatever channel the chat box opens in. A "/p " or "/s " in front picks one.
            new Page(ActionPadKeyMap.REPLIES_TAB_ID, "Replies", true,
                    macro("Hello", "Hello everyone!"),
                    macro("Goodbye", "Thanks for the run, bye!"),
                    macro("Thanks", "Thanks!"),
                    macro("On my way", "On my way!"),
                    macro("Be right back", "BRB, one minute"),
                    macro("Ready", "Ready when you are"),
                    macro("Wait for me", "Please wait for me"),
                    macro("Need heals", "Need heals!"),
                    macro("Need mana", "Out of mana, need a drink"),
                    macro("Pull now", "Go ahead and pull"),
                    macro("Invite me", "Invite please?"),
                    macro("Sorry", "Sorry about that"),
                    macro("Good job", "Nice job everyone!"),
                    macro("No problem", "No problem!"),
                    macro("Good luck", "Good luck!"),
                    macro("Lets go", "Let's go!"),
                    macro("Hold up", "Hold up, wait for the tank"),
                    macro("Need rez", "Need a rez please"),
                    macro("Buffs please", "Buffs please?"),
                    macro("LFG", "Looking for group, any role"),
                    macro("Summon me", "Summon please?"),
                    macro("Incoming", "Incoming, watch out!"),
                    macro("Nice one", "Nice one!"),
                    macro("AFK", "AFK, back in a few minutes")),
            new Page("fkeys", "F-Keys", fKeys())));

    private static Slot[] fKeys() {
        Slot[] out = new Slot[12];
        for (int i = 0; i < out.length; i++) out[i] = page("F" + (i + 1), VK_F1 + i);
        return out;
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

    /** The optional Extra Buttons: F8 to F12, then Shift plus F8 to F12. */
    public static final List<Slot> EXTRAS = Collections.unmodifiableList(buildExtras());

    private static List<Slot> buildExtras() {
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < 5; i++) out.add(number("Ex" + (i + 1), VK_F1 + 7 + i));
        for (int i = 0; i < 5; i++) out.add(number("Ex" + (i + 6), VK_F1 + 7 + i, VK_LSHIFT));
        return out;
    }

    // --- Tabs ---

    /** The touchpad and on-screen keyboard tabs, shown after the key pages. */
    public static final String REPLIES_TAB_ID = "replies";
    public static final String MOUSE_TAB_ID = "mouse";
    public static final String KEYBOARD_TAB_ID = "keyboard";

    /** Ids of every tab, in display order. */
    public static List<String> tabIds() {
        List<String> ids = new ArrayList<>();
        for (Page page : PAGES) ids.add(page.id);
        ids.add(MOUSE_TAB_ID);
        ids.add(KEYBOARD_TAB_ID);
        return ids;
    }

    /** The name shown for a tab id, in the tab bar and in the editor. */
    public static String tabTitle(String id) {
        for (Page page : PAGES) if (page.id.equals(id)) return page.title;
        if (id.equals(MOUSE_TAB_ID)) return "Mouse";
        if (id.equals(KEYBOARD_TAB_ID)) return "Keyboard";
        throw new IllegalArgumentException("No tab " + id);
    }

    /**
     * {@code shown} with tab {@code id} turned on or off, in display order. The last tab on cannot be
     * turned off, so the pad always has something to show.
     */
    public static Set<String> toggledTabs(Set<String> shown, String id) {
        Set<String> out = new LinkedHashSet<>();
        boolean turnOff = shown.contains(id);
        if (turnOff && shown.size() <= 1) return new LinkedHashSet<>(shown);
        for (String tab : tabIds()) {
            if (tab.equals(id) ? !turnOff : shown.contains(tab)) out.add(tab);
        }
        return out;
    }

    // --- Chat macros ---

    /** Longest chat line a button can type. WoW's chat box takes 255; this keeps a line short enough to read on a button. */
    public static final int MAX_MACRO = 80;
    /** Set on a macro key code that needs Shift held while it is pressed. */
    public static final int MACRO_SHIFT = 0x100;

    /**
     * The key presses that type {@code text} into WoW's chat box: Enter, each character, Enter.
     * Handles letters in either case, digits, space and the punctuation on the on-screen keyboard (US layout).
     * A character that needs Shift has {@link #MACRO_SHIFT} added to its key code.
     */
    public static int[] macroKeys(String text) {
        int[] keys = new int[text.length() + 2];
        keys[0] = VK_RETURN;
        for (int i = 0; i < text.length(); i++) keys[i + 1] = macroCode(text.charAt(i), text);
        keys[keys.length - 1] = VK_RETURN;
        return keys;
    }

    private static int macroCode(char c, String text) {
        if (c == ' ') return VK_SPACE;
        for (String[] row : TYPING_ROWS) {
            for (String pair : row) {
                if (pair.charAt(0) == c) return vkOf(c);
                if (pair.charAt(1) == c) return vkOf(pair.charAt(0)) | MACRO_SHIFT;
            }
        }
        throw new IllegalArgumentException("Unsupported macro character '" + c + "' in " + text);
    }

    /** Whether {@code text} is a chat line a button can type: every character typeable, and not too long. */
    public static boolean canType(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_MACRO) return false;
        try {
            macroKeys(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // --- Keys the player can pick, and the on-screen keyboard ---

    /** A key in the remap screen's list. */
    public static final class KeyDef {
        public final String group;
        public final String label;
        public final int vk;

        KeyDef(String group, String label, int vk) {
            this.group = group;
            this.label = label;
            this.vk = vk;
        }
    }

    public static final List<KeyDef> KEYS = Collections.unmodifiableList(buildKeys());

    private static List<KeyDef> buildKeys() {
        List<KeyDef> k = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) k.add(new KeyDef("Letters", String.valueOf(c), c));
        for (char c = '0'; c <= '9'; c++) k.add(new KeyDef("Digits", String.valueOf(c), c));
        for (int i = 1; i <= 12; i++) k.add(new KeyDef("Function", "F" + i, VK_F1 + i - 1));
        String[] edit = {"Esc", "Tab", "Enter", "Space", "Bksp", "Ins", "Del", "Home", "End", "PgUp", "PgDn", "Left", "Up", "Right", "Down"};
        int[] editVk = {VK_ESCAPE, VK_TAB, VK_RETURN, VK_SPACE, VK_BACK, 0x2D, 0x2E, 0x24, 0x23, VK_PRIOR, VK_NEXT, 0x25, 0x26, 0x27, 0x28};
        for (int i = 0; i < edit.length; i++) k.add(new KeyDef("Navigation", edit[i], editVk[i]));
        for (char c : ";=,-./`[\\]'".toCharArray()) k.add(new KeyDef("Symbols", String.valueOf(c), vkOf(c)));
        for (int i = 0; i <= 9; i++) k.add(new KeyDef("Numpad", "KP" + i, 0x60 + i));
        String[] pad = {"KP*", "KP+", "KP-", "KP.", "KP/", "NumLk", "Caps", "PrtSc"};
        int[] padVk = {0x6A, 0x6B, 0x6D, 0x6E, VK_DIVIDE, VK_NUMLOCK, 0x14, VK_SNAPSHOT};
        for (int i = 0; i < pad.length; i++) k.add(new KeyDef("Numpad", pad[i], padVk[i]));
        return k;
    }

    /** What to show for a key code: its name in the list, a modifier's name, or the code in hex. */
    public static String labelFor(int vk) {
        for (KeyDef key : KEYS) if (key.vk == vk) return key.label;
        String mod = modifierLabel(vk);
        return mod.isEmpty() ? String.format("0x%02X", vk) : mod;
    }

    /** The key code for a typed character: letters and digits are their own codes, punctuation is looked up. */
    public static int vkOf(char c) {
        switch (c) {
            case ' ': return VK_SPACE;
            case ';': return 0xBA;
            case '=': return VK_OEM_PLUS;
            case ',': return 0xBC;
            case '-': return VK_OEM_MINUS;
            case '.': return VK_OEM_PERIOD;
            case '/': return VK_OEM_2;
            case '`': return 0xC0;
            case '[': return 0xDB;
            case '\\': return 0xDC;
            case ']': return 0xDD;
            case '\'': return 0xDE;
            default: return Character.toUpperCase(c);
        }
    }

    /** The "plain" + "shifted" pair for a typing key, such as "1!" for '1'. */
    public static String typingPair(char plain) {
        for (String[] row : TYPING_ROWS) {
            for (String pair : row) if (pair.charAt(0) == plain) return pair;
        }
        throw new IllegalArgumentException("No typing key " + plain);
    }

    /** Each typing key as "plain" + "shifted" characters; the keyboard shows the shifted one while Shift is armed. */
    public static final String[][] TYPING_ROWS = {
            {"1!", "2@", "3#", "4$", "5%", "6^", "7&", "8*", "9(", "0)", "-_", "=+"},
            {"qQ", "wW", "eE", "rR", "tT", "yY", "uU", "iI", "oO", "pP", "[{", "]}"},
            {"aA", "sS", "dD", "fF", "gG", "hH", "jJ", "kK", "lL", ";:", "'\""},
            {"zZ", "xX", "cC", "vV", "bB", "nN", "mM", ",<", ".>", "/?"},
    };
}
