package com.limelight.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-tab choices the player makes in Edit: a new name for the tab, and what sits on the right of it
 * (the number block, nothing, or another page's buttons). Plain Java so it can be tested.
 */
final class PadTabOptions {
    /** The default: modifier row and number block on the right. */
    static final String NUMBERS = "numbers";
    /** Nothing on the right; the tab's own buttons take the whole width. */
    static final String HIDDEN = "hidden";
    static final int MAX_NAME = 12;
    /** The Target tab always keeps the number block, as the player asked. */
    private static final String ALWAYS_NUMBERS = "target";

    private static final class Entry {
        String name = "";
        String side = NUMBERS;
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    static PadTabOptions parse(String text) {
        PadTabOptions out = new PadTabOptions();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 3 || f[0].isEmpty()) continue;
            if (!validId(f[0])) continue;
            out.setName(f[0], f[1]);
            out.setSide(f[0], f[2]);
        }
        return out;
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('\t').append(e.getValue().name).append('\t').append(e.getValue().side);
        }
        return sb.toString();
    }

    /** The tab's own name if the player gave it one, otherwise {@code fallback}. */
    String name(String id, String fallback) {
        Entry e = entries.get(id);
        return e == null || e.name.isEmpty() ? fallback : e.name;
    }

    boolean hasName(String id) {
        Entry e = entries.get(id);
        return e != null && !e.name.isEmpty();
    }

    /** What sits on the right of a tab: {@link #NUMBERS}, {@link #HIDDEN}, or a page id. */
    String side(String id) {
        if (!canChooseSide(id)) return NUMBERS;
        Entry e = entries.get(id);
        if (e == null) return NUMBERS;
        String side = e.side;
        if (side.equals(NUMBERS) || side.equals(HIDDEN)) return side;
        return sideChoices(id).contains(side) ? side : NUMBERS;
    }

    void setName(String id, String name) {
        if (!validId(id)) return;
        String clean = name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').trim();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME).trim();
        Entry e = entry(id);
        e.name = clean;
        tidy(id);
    }

    void setSide(String id, String side) {
        if (!validId(id) || side == null) return;
        Entry e = entry(id);
        e.side = side.isEmpty() ? NUMBERS : side;
        tidy(id);
    }

    /** Key pages that sit beside a side panel can choose one. Full-screen pages, Mouse, Keyboard and Target cannot. */
    static boolean canChooseSide(String id) {
        if (ALWAYS_NUMBERS.equals(id)) return false;
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) if (page.id.equals(id)) return !page.fullWidth;
        return false;
    }

    /** What a tab can show on its right: numbers, nothing, or any other ordinary page. */
    static List<String> sideChoices(String id) {
        List<String> out = new ArrayList<>();
        out.add(NUMBERS);
        out.add(HIDDEN);
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            if (!page.fullWidth && !page.id.equals(id)) out.add(page.id);
        }
        return out;
    }

    private static boolean validId(String id) {
        try {
            ActionPadKeyMap.tabTitle(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Entry entry(String id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry();
            entries.put(id, e);
        }
        return e;
    }

    private void tidy(String id) {
        Entry e = entries.get(id);
        if (e != null && e.name.isEmpty() && e.side.equals(NUMBERS)) entries.remove(id);
    }
}
