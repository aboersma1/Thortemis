package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.limelight.ui.ActionPadKeyMap.Slot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PadConfigTest {
    private static final List<Slot> INTERFACE = ActionPadKeyMap.PAGES.get(0).slots;

    private static Slot byId(List<Slot> slots, String id) {
        for (Slot s : slots) if (s.id.equals(id)) return s;
        return null;
    }

    private static List<String> ids(List<Slot> slots) {
        List<String> out = new ArrayList<>();
        for (Slot s : slots) out.add(s.id);
        return out;
    }

    @Test
    public void noEditsLeavesTheDefaultsAlone() {
        PadConfig config = new PadConfig();
        assertTrue(config.isEmpty());
        assertEquals(INTERFACE, config.resolve("interface", INTERFACE, false));
    }

    @Test
    public void remapKeepsTheButtonButChangesWhatItSends() {
        PadConfig config = new PadConfig();
        config.setKey("Bags", 'V', ActionPadKeyMap.MOD_SHIFT | ActionPadKeyMap.MOD_ALT);
        Slot bags = byId(config.resolve("interface", INTERFACE, false), "Bags");
        assertEquals('V', bags.vk);
        assertEquals(2, bags.mods.length);
        assertEquals(ActionPadKeyMap.VK_LSHIFT, bags.mods[0]);
        assertEquals(ActionPadKeyMap.VK_LMENU, bags.mods[1]);
        assertEquals("Bags", bags.label);
    }

    @Test
    public void remapToAPlainKeyDropsTheDefaultModifiers() {
        PadConfig config = new PadConfig();
        List<Slot> target = ActionPadKeyMap.PAGES.get(1).slots;
        config.setKey("Previous enemy", 'Q', 0);
        Slot prev = byId(config.resolve("target", target, false), "Previous enemy");
        assertEquals('Q', prev.vk);
        assertEquals(0, prev.mods.length);
    }

    @Test
    public void renameAndRecolorKeepTheKey() {
        PadConfig config = new PadConfig();
        config.setName("Map", "  World map ");
        config.setColor("Map", 0xFFE53935);
        Slot map = byId(config.resolve("interface", INTERFACE, false), "Map");
        assertEquals("World map", map.label);
        assertEquals(0xFFE53935, map.color);
        assertEquals('M', map.vk);
        config.setName("Map", "   ");
        assertEquals("Map", byId(config.resolve("interface", INTERFACE, false), "Map").label);
    }

    @Test
    public void hiddenButtonsDisappearUnlessAsked() {
        PadConfig config = new PadConfig();
        config.setHidden("Guild", true);
        assertEquals(INTERFACE.size() - 1, config.resolve("interface", INTERFACE, false).size());
        assertEquals(INTERFACE.size(), config.resolve("interface", INTERFACE, true).size());
        assertTrue(config.isHidden("Guild"));
    }

    @Test
    public void moveReordersWithinTheGroupAndStopsAtTheEnds() {
        PadConfig config = new PadConfig();
        config.move("interface", INTERFACE, "Map", -1);
        List<String> ids = ids(config.resolve("interface", INTERFACE, false));
        assertEquals("Map", ids.get(3));
        assertEquals("Quest Log", ids.get(4));
        config.move("interface", INTERFACE, "Character", -1);
        assertEquals("Character", ids(config.resolve("interface", INTERFACE, false)).get(0));
        config.move("interface", INTERFACE, "System", 1);
        assertEquals("System", ids(config.resolve("interface", INTERFACE, false)).get(INTERFACE.size() - 1));
    }

    @Test
    public void resettingEverythingBringsBackTheDefaults() {
        PadConfig config = new PadConfig();
        config.setKey("Bags", 'V', 0);
        config.setHidden("Map", true);
        config.move("interface", INTERFACE, "Map", -1);
        config.clear();
        assertTrue(config.isEmpty());
        assertEquals(INTERFACE, config.resolve("interface", INTERFACE, false));
    }

    @Test
    public void resetButtonKeepsOtherEdits() {
        PadConfig config = new PadConfig();
        config.setKey("Bags", 'V', 0);
        config.setName("Map", "World");
        config.resetButton("Bags");
        List<Slot> out = config.resolve("interface", INTERFACE, false);
        assertEquals('B', byId(out, "Bags").vk);
        assertEquals("World", byId(out, "Map").label);
    }

    @Test
    public void savesAndLoadsEverything() {
        PadConfig config = new PadConfig();
        config.setKey("Bags", 'V', ActionPadKeyMap.MOD_CTRL);
        config.setName("Map", "World map");
        config.setColor("Social", 0xFF1E88E5);
        config.setHidden("Guild", true);
        config.move("interface", INTERFACE, "Map", -2);
        PadConfig loaded = PadConfig.parse(config.serialize());
        assertEquals(config.serialize(), loaded.serialize());
        List<Slot> a = config.resolve("interface", INTERFACE, true);
        List<Slot> b = loaded.resolve("interface", INTERFACE, true);
        assertEquals(ids(a), ids(b));
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).label, b.get(i).label);
            assertEquals(a.get(i).vk, b.get(i).vk);
            assertEquals(a.get(i).color, b.get(i).color);
        }
        assertTrue(loaded.isHidden("Guild"));
    }

    @Test
    public void damagedSavesAreSkippedNotFatal() {
        PadConfig loaded = PadConfig.parse("garbage\nE\tBags\tnotanumber\t0\t0\t0\t\nE\tMap\t86\t0\t0\t0\t\nE\ttoo\tshort\n");
        assertEquals('V', byId(loaded.resolve("interface", INTERFACE, false), "Map").vk);
        assertEquals('B', byId(loaded.resolve("interface", INTERFACE, false), "Bags").vk);
        assertTrue(PadConfig.parse(null).isEmpty());
    }

    @Test
    public void namesCannotBreakTheSaveFormat() {
        PadConfig config = new PadConfig();
        config.setName("Map", "a\tb\nc");
        PadConfig loaded = PadConfig.parse(config.serialize());
        assertEquals("a b c", byId(loaded.resolve("interface", INTERFACE, false), "Map").label);
    }

    @Test
    public void everyButtonHasAUniqueId() {
        Set<String> seen = new HashSet<>();
        List<Slot> all = new ArrayList<>(ActionPadKeyMap.NUMBERS);
        all.addAll(ActionPadKeyMap.EXTRAS);
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) all.addAll(page.slots);
        for (Slot s : all) assertTrue("duplicate id " + s.id, seen.add(s.id));
    }

    @Test
    public void unchangedButtonsAreTheSameObjects() {
        PadConfig config = new PadConfig();
        config.setHidden("Guild", true);
        assertSame(INTERFACE.get(0), config.resolve("interface", INTERFACE, false).get(0));
    }
}
