package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionPadThemeTest {

    private static final String ARRAYS = "src/main/res/values/action_pad_arrays.xml";

    private static List<String> arrayItems(String name) throws Exception {
        String xml = new String(Files.readAllBytes(new File(ARRAYS).toPath()), "UTF-8");
        Matcher block = Pattern.compile("<string-array name=\"" + name + "\">(.*?)</string-array>", Pattern.DOTALL).matcher(xml);
        assertTrue(name, block.find());
        Matcher item = Pattern.compile("<item>(.*?)</item>").matcher(block.group(1));
        List<String> out = new ArrayList<>();
        while (item.find()) out.add(item.group(1));
        return out;
    }

    /** WCAG contrast ratio between two opaque colours, 1 (none) to 21 (black on white). */
    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * channel((c >> 16) & 0xFF) + 0.7152 * channel((c >> 8) & 0xFF) + 0.0722 * channel(c & 0xFF);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @Test
    public void idsAreUniqueAndDefaultExists() {
        Set<String> ids = new HashSet<>();
        for (ActionPadTheme t : ActionPadTheme.ALL) assertTrue(t.id, ids.add(t.id));
        assertEquals(ActionPadTheme.DEFAULT_ID, ActionPadTheme.ALL.get(0).id);
        assertEquals(ActionPadTheme.ALL.get(0), ActionPadTheme.byId("no-such-theme"));
    }

    @Test
    public void textAndAccentStayReadable() {
        for (ActionPadTheme t : ActionPadTheme.ALL) {
            assertTrue(t.id + " text on face", contrast(t.text, t.face) >= 4.5);
            assertTrue(t.id + " text on number face", contrast(t.text, t.faceNumber) >= 4.5);
            assertTrue(t.id + " text on pressed face", contrast(t.text, t.facePressed) >= 3.0);
            assertTrue(t.id + " text on panel", contrast(t.text, t.bg) >= 4.5);
            // Selected tabs and locked modifiers draw the accent as text, or panel colour on an accent fill.
            assertTrue(t.id + " accent on face", contrast(t.accent, t.face) >= 3.0);
            assertTrue(t.id + " accent on pressed face", contrast(t.accent, t.facePressed) >= 1.5);
            assertTrue(t.id + " panel on accent", contrast(t.bg, t.accent) >= 3.0);
            // Buttons need an outline you can see against the panel.
            assertTrue(t.id + " border on panel", contrast(t.border, t.bg) >= 1.8);
        }
    }

    @Test
    public void oledThemeIsPureBlackAndFlat() {
        ActionPadTheme oled = ActionPadTheme.byId("oled");
        assertEquals(0xFF000000, oled.bg);
        assertEquals(0xFF000000, oled.bgTop);
        assertEquals(0xFF000000, oled.face);
        assertTrue(oled.flat);
    }

    @Test
    public void everyExpansionHasATheme() {
        for (String id : new String[]{"classic", "tbc", "wotlk", "cata", "mop", "wod", "legion", "bfa",
                "shadowlands", "dragonflight", "tww", "midnight", "oled", "cyberpunk"}) {
            assertEquals(id, ActionPadTheme.byId(id).id);
        }
    }

    @Test
    public void settingsArraysMatchTheCode() throws Exception {
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ActionPadTheme t : ActionPadTheme.ALL) {
            ids.add(t.id);
            names.add(t.name);
        }
        assertEquals(ids, arrayItems("action_pad_theme_values"));
        assertEquals(names, arrayItems("action_pad_theme_names"));
        assertEquals(ActionPadKeyMap.tabIds(), arrayItems("action_pad_tab_values"));
        assertEquals(new ArrayList<>(ActionPadSettings.defaultTabs()), arrayItems("action_pad_tab_defaults"));
    }

    @Test
    public void fKeysAreHiddenByDefaultAndMouseIsShown() {
        Set<String> tabs = ActionPadSettings.defaultTabs();
        assertTrue(!tabs.contains("fkeys"));
        assertTrue(tabs.contains("mouse"));
        assertTrue(tabs.contains("interface"));
    }
}
