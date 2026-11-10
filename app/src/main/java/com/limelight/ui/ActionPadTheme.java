package com.limelight.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Colour schemes for {@link ExternalActionPadView}. Pure data, no Android dependencies.
 *
 * To add a theme, add a line to {@link #ALL} and an entry in res/values/action_pad_arrays.xml
 * (ActionPadThemeTest checks the two stay in step, and that the text stays readable).
 */
public final class ActionPadTheme {
    public static final String DEFAULT_ID = "blue";

    public final String id;
    public final String name;
    /** Panel background (bottom of the gradient). */
    public final int bg;
    /** Panel background (top of the gradient). */
    public final int bgTop;
    public final int face;
    public final int faceNumber;
    public final int facePressed;
    public final int border;
    public final int accent;
    public final int text;
    /** No gradients: solid colours only, so black stays black on an OLED screen. */
    public final boolean flat;

    private ActionPadTheme(String id, String name, int bg, int bgTop, int face, int faceNumber,
                           int facePressed, int border, int accent, int text, boolean flat) {
        this.id = id;
        this.name = name;
        this.bg = bg;
        this.bgTop = bgTop;
        this.face = face;
        this.faceNumber = faceNumber;
        this.facePressed = facePressed;
        this.border = border;
        this.accent = accent;
        this.text = text;
        this.flat = flat;
    }

    /** A theme from five colours; the rest are blends of them. */
    private static ActionPadTheme of(String id, String name, int bg, int face, int border, int accent, int text) {
        return new ActionPadTheme(id, name, bg, mix(bg, accent, 0.10f), face, mix(face, accent, 0.10f),
                mix(face, accent, 0.38f), border, accent, text, false);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static final List<ActionPadTheme> ALL;

    static {
        List<ActionPadTheme> l = new ArrayList<>();
        // The blue look from the first versions, kept exactly.
        l.add(new ActionPadTheme("blue", "Alliance Blue", 0xFF08111F, 0xFF0F2038, 0xFF16294A, 0xFF1B3560,
                0xFF2F6AB8, 0xFF3F72B0, 0xFF7CC8FF, 0xFFE8F3FF, false));
        l.add(of("classic", "Classic", 0xFF14110D, 0xFF2B2318, 0xFF8A6A2A, 0xFFE0B04A, 0xFFF1E3C0));
        l.add(of("tbc", "The Burning Crusade", 0xFF120A1F, 0xFF2A1646, 0xFF7B3FC4, 0xFF7CFC3A, 0xFFEFE6FF));
        l.add(of("wotlk", "Wrath of the Lich King", 0xFF0A0E14, 0xFF1A2430, 0xFF8FA8BC, 0xFFB8F0FF, 0xFFF2FAFF));
        l.add(of("cata", "Cataclysm", 0xFF140806, 0xFF2E120A, 0xFFB8431A, 0xFFFF8A2B, 0xFFFFE9D6));
        l.add(of("mop", "Mists of Pandaria", 0xFF0A1710, 0xFF16301F, 0xFF4E9A6B, 0xFFE2C275, 0xFFEFF7E8));
        l.add(of("wod", "Warlords of Draenor", 0xFF111214, 0xFF26282B, 0xFF7A7F85, 0xFFE23B3B, 0xFFEDEEF0));
        l.add(of("legion", "Legion", 0xFF07120A, 0xFF11301A, 0xFF2FA04A, 0xFF9DFF3C, 0xFFEAFFE0));
        l.add(of("bfa", "Battle for Azeroth", 0xFF06161C, 0xFF0F3340, 0xFF2B8FA8, 0xFFFFC84A, 0xFFE6F8FF));
        l.add(of("shadowlands", "Shadowlands", 0xFF0B0A18, 0xFF1C1A3A, 0xFF8E7CC3, 0xFFB9F2E1, 0xFFEEEBFF));
        l.add(of("dragonflight", "Dragonflight", 0xFF1A0F08, 0xFF3A2312, 0xFFC9803A, 0xFF4FD6C8, 0xFFFFF0DC));
        l.add(of("tww", "The War Within", 0xFF0D0F12, 0xFF1F252B, 0xFF6B5BD2, 0xFFF2C14E, 0xFFF1F1F5));
        l.add(of("midnight", "Midnight", 0xFF140716, 0xFF2D0F2E, 0xFFD04B8A, 0xFFFFD27A, 0xFFFFEAF4));
        l.add(new ActionPadTheme("oled", "OLED Black", 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000,
                0xFF2A2A2A, 0xFF5E5E5E, 0xFFFFFFFF, 0xFFEDEDED, true));
        l.add(of("cyberpunk", "Cyberpunk", 0xFF0A0612, 0xFF170A2B, 0xFFFF2A6D, 0xFF05F0FF, 0xFFFCEE09));
        ALL = Collections.unmodifiableList(l);
    }

    /** The theme with this id, or the default if the id is unknown. */
    public static ActionPadTheme byId(String id) {
        for (ActionPadTheme t : ALL) {
            if (t.id.equals(id)) return t;
        }
        return ALL.get(0);
    }
}
