package com.limelight.ui;

/**
 * The keyboard tab's layouts. A layout is one or more layers, a layer is rows, and a row is key tokens
 * separated by spaces. A token is a typing key ("q"), a named key ("Enter"), or "_" for a gap, and may end
 * in ":weight" to make it wider than 1. Rows are filled edge to edge, so each row's weights should add up.
 */
final class PadKeyboardLayouts {
    static final String CLASSIC = "classic";
    static final String GRID = "grid";
    static final String LAYERS = "layers";
    static final String DEFAULT = LAYERS;

    static final String[] IDS = {CLASSIC, GRID, LAYERS};
    static final String[] NAMES = {"Classic", "Square grid", "Two layers"};

    /** Switches to the symbols layer, and back to the letters layer. */
    static final String SYMBOLS_KEY = "#+=";
    static final String LETTERS_KEY = "ABC";

    private static final String DIGITS = "1 2 3 4 5 6 7 8 9 0";

    private static final String[][] CLASSIC_LAYERS = {{
            "1 2 3 4 5 6 7 8 9 0 - = Bksp:1.6",
            "q w e r t y u i o p [ ]",
            "a s d f g h j k l ; ' Enter:1.8",
            "Shift:1.6 z x c v b n m , . /",
            "Esc Tab Ctrl Alt Space:3 ←:0.8 ↑:0.8 ↓:0.8 →:0.8"}};

    private static final String[][] GRID_LAYERS = {{
            DIGITS,
            "q w e r t y u i o p",
            "a s d f g h j k l ;",
            "z x c v b n m , . /",
            "Shift:2 Esc Tab - = [ ] ' Bksp",
            "Ctrl Alt Space:3 Enter ← ↑ ↓ →"}};

    /**
     * Two layers that share their frame: Shift is always the left key of row 4, Backspace the right key
     * of row 4, the layer switch the left key of row 5 and Enter its right key, so nothing moves when
     * the layer changes. Letters and the punctuation used most in chat are on the first layer.
     */
    private static final String[][] LAYERS_LAYERS = {{
            DIGITS,
            "q w e r t y u i o p",
            "a s d f g h j k l '",
            "Shift:1.5 z x c v b n m Bksp:1.5",
            SYMBOLS_KEY + ":1.5 Esc , Space:3 . / Enter:1.5"}, {
            DIGITS,
            "- = [ ] ; ' , . / Tab",
            "← ↑ ↓ → Home End PgUp PgDn Ins Del",
            "Shift:1.5 Ctrl:2 Alt:2 Esc:3 Bksp:1.5",
            LETTERS_KEY + ":1.5 Space:7 Enter:1.5"}};

    private PadKeyboardLayouts() {}

    /** The layers of the layout with this id (the default one if the id is unknown). */
    static String[][] layers(String id) {
        if (CLASSIC.equals(id)) return CLASSIC_LAYERS;
        if (GRID.equals(id)) return GRID_LAYERS;
        return LAYERS_LAYERS;
    }

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[IDS.length - 1];
    }

    static String keyName(String token) {
        int colon = token.lastIndexOf(':');
        return colon > 0 ? token.substring(0, colon) : token;
    }

    static float weight(String token) {
        int colon = token.lastIndexOf(':');
        return colon > 0 ? Float.parseFloat(token.substring(colon + 1)) : 1f;
    }

    /** The Windows key code of a named key that is not a letter, digit or modifier, or -1 if there is none. */
    static int namedKeyVk(String name) {
        switch (name) {
            case "Bksp": return ActionPadKeyMap.VK_BACK;
            case "Enter": return ActionPadKeyMap.VK_RETURN;
            case "Esc": return ActionPadKeyMap.VK_ESCAPE;
            case "Tab": return ActionPadKeyMap.VK_TAB;
            case "Space": return ActionPadKeyMap.VK_SPACE;
            case "Home": return 0x24;
            case "End": return 0x23;
            case "PgUp": return ActionPadKeyMap.VK_PRIOR;
            case "PgDn": return ActionPadKeyMap.VK_NEXT;
            case "Ins": return 0x2D;
            case "Del": return 0x2E;
            case "←": return 0x25;
            case "↑": return 0x26;
            case "→": return 0x27;
            case "↓": return 0x28;
            default: return -1;
        }
    }
}
