package com.limelight.ui;

import com.limelight.ui.ActionPadKeyMap.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the player has changed on the action pad: a new key (with Shift, Ctrl or Alt), a new name or
 * outline colour for a button, buttons hidden, and the order of the buttons within a group.
 * Buttons are found by their default label ({@link Slot#id}), so the defaults can change safely.
 *
 * Saves as plain text, one line per button, so a whole layout fits in one preference (or a profile slot).
 * Pure Java, no Android dependencies.
 */
public final class PadConfig {
    private static final class Edit {
        int vk;           // 0 = keep the default key
        int mods;         // modifier mask, used when vk is set
        int color;        // 0 = the theme's
        boolean hidden;
        String name;      // null = the default label
        String macro;     // null = the default chat text

        boolean isDefault() {
            return vk == 0 && color == 0 && !hidden && name == null && macro == null;
        }
    }

    private final Map<String, Edit> edits = new LinkedHashMap<>();
    private final Map<String, List<String>> order = new LinkedHashMap<>();

    public boolean isEmpty() {
        return edits.isEmpty() && order.isEmpty();
    }

    public void clear() {
        edits.clear();
        order.clear();
    }

    /** Takes over everything from {@code other}. */
    public void replaceWith(PadConfig other) {
        clear();
        edits.putAll(other.edits);
        order.putAll(other.order);
    }

    private Edit edit(String id) {
        Edit e = edits.get(id);
        if (e == null) {
            e = new Edit();
            edits.put(id, e);
        }
        return e;
    }

    /** Drops an edit that has gone back to the defaults, so it does not clutter the saved text. */
    private void tidy(String id) {
        Edit e = edits.get(id);
        if (e != null && e.isDefault()) edits.remove(id);
    }

    public void setKey(String id, int vk, int modsMask) {
        Edit e = edit(id);
        e.vk = vk;
        e.mods = modsMask;
        tidy(id);
    }

    /** A blank name goes back to the default label. */
    public void setName(String id, String name) {
        String clean = name == null ? "" : name.replaceAll("[\\t\\r\\n]", " ").trim();
        edit(id).name = clean.isEmpty() ? null : clean;
        tidy(id);
    }

    /** The text a chat button types. Blank, or text a button cannot type, goes back to the default. */
    public void setMacro(String id, String text) {
        String clean = text == null ? "" : text.replaceAll("[\\t\\r\\n]", " ").trim();
        edit(id).macro = ActionPadKeyMap.canType(clean) ? clean : null;
        tidy(id);
    }

    public void setColor(String id, int color) {
        edit(id).color = color;
        tidy(id);
    }

    public void setHidden(String id, boolean hidden) {
        edit(id).hidden = hidden;
        tidy(id);
    }

    public boolean isHidden(String id) {
        Edit e = edits.get(id);
        return e != null && e.hidden;
    }

    /** Puts one button back to its defaults (its place in the order is kept). */
    public void resetButton(String id) {
        edits.remove(id);
    }

    /** Moves a button earlier ({@code delta} -1) or later (+1) within its group. */
    public void move(String group, List<Slot> defaults, String id, int delta) {
        List<String> ids = new ArrayList<>();
        for (Slot s : resolve(group, defaults, true)) ids.add(s.id);
        int from = ids.indexOf(id);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= ids.size()) return;
        ids.remove(from);
        ids.add(to, id);
        order.put(group, ids);
    }

    /** The buttons of a group as the player sees them: ordered, renamed, remapped, and without hidden ones. */
    public List<Slot> resolve(String group, List<Slot> defaults, boolean includeHidden) {
        List<Slot> remaining = new ArrayList<>(defaults);
        List<Slot> ordered = new ArrayList<>();
        List<String> saved = order.get(group);
        if (saved != null) {
            for (String id : saved) {
                for (int i = 0; i < remaining.size(); i++) {
                    if (remaining.get(i).id.equals(id)) {
                        ordered.add(remaining.remove(i));
                        break;
                    }
                }
            }
        }
        ordered.addAll(remaining);

        List<Slot> out = new ArrayList<>();
        for (Slot slot : ordered) {
            Edit e = edits.get(slot.id);
            if (e == null) {
                out.add(slot);
            } else if (includeHidden || !e.hidden) {
                out.add(slot.with(
                        e.name != null ? e.name : slot.label,
                        e.vk != 0 ? e.vk : slot.vk,
                        e.vk != 0 ? ActionPadKeyMap.modsFromMask(e.mods) : slot.mods,
                        e.color,
                        e.macro));
            }
        }
        return out;
    }

    // --- Saving ---

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Edit> entry : edits.entrySet()) {
            Edit e = entry.getValue();
            sb.append("E\t").append(entry.getKey()).append('\t').append(e.vk).append('\t').append(e.mods)
                    .append('\t').append(e.color).append('\t').append(e.hidden ? 1 : 0)
                    .append('\t').append(e.name == null ? "" : e.name)
                    .append('\t').append(e.macro == null ? "" : e.macro).append('\n');
        }
        for (Map.Entry<String, List<String>> entry : order.entrySet()) {
            sb.append("O\t").append(entry.getKey());
            for (String id : entry.getValue()) sb.append('\t').append(id);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Reads saved text; lines it cannot read are skipped, so a damaged save never breaks the pad. */
    public static PadConfig parse(String text) {
        PadConfig config = new PadConfig();
        if (text == null) return config;
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            try {
                if (f[0].equals("E") && (f.length == 7 || f.length == 8)) {
                    int vk = Integer.parseInt(f[2]);
                    int mods = Integer.parseInt(f[3]);
                    int color = Integer.parseInt(f[4]);
                    Edit e = config.edit(f[1]);
                    e.vk = vk;
                    e.mods = mods;
                    e.color = color;
                    e.hidden = f[5].equals("1");
                    e.name = f[6].isEmpty() ? null : f[6];
                    e.macro = f.length == 8 && ActionPadKeyMap.canType(f[7]) ? f[7] : null;
                    config.tidy(f[1]);
                } else if (f[0].equals("O") && f.length >= 3) {
                    List<String> ids = new ArrayList<>();
                    for (int i = 2; i < f.length; i++) ids.add(f[i]);
                    config.order.put(f[1], ids);
                }
            } catch (NumberFormatException ignored) {
                // Skip a damaged line.
            }
        }
        return config;
    }
}
