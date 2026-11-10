package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ActionPadKeyMapTest {

    private static ActionPadKeyMap.Slot find(String label) {
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            for (ActionPadKeyMap.Slot slot : page.slots) {
                if (slot.label.equals(label)) return slot;
            }
        }
        for (ActionPadKeyMap.Slot slot : ActionPadKeyMap.NUMBERS) {
            if (slot.label.equals(label)) return slot;
        }
        return null;
    }

    @Test
    public void numberBlockIsActionBarOne() {
        assertEquals(12, ActionPadKeyMap.NUMBERS.size());
        assertEquals('1', ActionPadKeyMap.NUMBERS.get(0).vk);
        assertEquals('9', ActionPadKeyMap.NUMBERS.get(8).vk);
        assertEquals('0', ActionPadKeyMap.NUMBERS.get(9).vk);
        assertEquals(ActionPadKeyMap.VK_OEM_MINUS, ActionPadKeyMap.NUMBERS.get(10).vk);
        assertEquals(ActionPadKeyMap.VK_OEM_PLUS, ActionPadKeyMap.NUMBERS.get(11).vk);
    }

    @Test
    public void numberButtonsTakeArmedModifiersAndPageButtonsDoNot() {
        for (ActionPadKeyMap.Slot slot : ActionPadKeyMap.NUMBERS) {
            assertTrue(slot.label, !slot.isolated);
            assertEquals(slot.label, 0, slot.mods.length);
        }
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            for (ActionPadKeyMap.Slot slot : page.slots) {
                assertTrue(slot.label, slot.isolated);
            }
        }
    }

    @Test
    public void pagesFitAndLabelsAreUnique() {
        Set<String> labels = new HashSet<>();
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            assertTrue(page.title, page.slots.size() <= 12);
            for (ActionPadKeyMap.Slot slot : page.slots) {
                assertTrue("duplicate label " + slot.label, labels.add(slot.label));
            }
        }
    }

    @Test
    public void comboButtonsCarryTheirModifiers() {
        ActionPadKeyMap.Slot prev = find("Previous enemy");
        assertNotNull(prev);
        assertEquals(ActionPadKeyMap.VK_TAB, prev.vk);
        assertEquals(1, prev.mods.length);
        assertEquals(ActionPadKeyMap.VK_LSHIFT, prev.mods[0]);

        ActionPadKeyMap.Slot hideUi = find("Hide UI");
        assertNotNull(hideUi);
        assertEquals('Z', hideUi.vk);
        assertEquals(ActionPadKeyMap.VK_LMENU, hideUi.mods[0]);
    }

    @Test
    public void utilityKeysMatchWindowsVirtualKeyCodes() {
        assertEquals(0x90, find("Auto-run").vk);       // Num Lock
        assertEquals(0x6F, find("Walk / run").vk);     // Numpad /
        assertEquals(0x2C, find("Screenshot").vk);     // Print Screen
        assertEquals(0x21, find("Chat page up").vk);   // Page Up
        assertEquals(0x22, find("Chat page down").vk); // Page Down
        assertEquals(0x70, find("Self").vk);           // F1
        assertEquals(0x74, find("Party 4").vk);        // F5
    }

    @Test
    public void modifierFlagsMatchTheStreamProtocol() {
        // Same values as KeyboardPacket.MODIFIER_SHIFT, _CTRL and _ALT.
        assertEquals(0x01, ActionPadKeyMap.modifierFlag(ActionPadKeyMap.VK_LSHIFT));
        assertEquals(0x02, ActionPadKeyMap.modifierFlag(ActionPadKeyMap.VK_LCONTROL));
        assertEquals(0x04, ActionPadKeyMap.modifierFlag(ActionPadKeyMap.VK_LMENU));
        assertEquals(0, ActionPadKeyMap.modifierFlag('A'));
    }

    @Test
    public void fKeyPageHasF1ToF12() {
        ActionPadKeyMap.Page page = null;
        for (ActionPadKeyMap.Page p : ActionPadKeyMap.PAGES) if (p.title.equals("F-Keys")) page = p;
        assertNotNull(page);
        assertEquals(12, page.slots.size());
        assertEquals("F1", page.slots.get(0).label);
        assertEquals(0x70, page.slots.get(0).vk);
        assertEquals("F12", page.slots.get(11).label);
        assertEquals(0x7B, page.slots.get(11).vk);
    }

    @Test
    public void macroTypesEnterTextEnter() {
        int[] keys = ActionPadKeyMap.macroKeys("/sit");
        assertEquals(6, keys.length);
        assertEquals(0x0D, keys[0]);
        assertEquals(0xBF, keys[1]);
        assertEquals('S', keys[2]);
        assertEquals('I', keys[3]);
        assertEquals('T', keys[4]);
        assertEquals(0x0D, keys[5]);
    }

    @Test
    public void everyChatMacroIsTypeable() {
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            for (ActionPadKeyMap.Slot slot : page.slots) {
                if (slot.macro != null) {
                    assertTrue(slot.label, slot.isolated);
                    assertEquals(slot.macro.length() + 2, ActionPadKeyMap.macroKeys(slot.macro).length);
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void macroRejectsCharactersThatNeedShift() {
        ActionPadKeyMap.macroKeys("/Sit");
    }

    @Test
    public void extraButtonsAreF8ToF12ThenShiftF8ToF12() {
        assertEquals(10, ActionPadKeyMap.EXTRAS.size());
        assertEquals(0x77, ActionPadKeyMap.EXTRAS.get(0).vk);   // F8
        assertEquals(0x7B, ActionPadKeyMap.EXTRAS.get(4).vk);   // F12
        assertEquals(0, ActionPadKeyMap.EXTRAS.get(4).mods.length);
        assertEquals(0x77, ActionPadKeyMap.EXTRAS.get(5).vk);
        assertEquals(ActionPadKeyMap.VK_LSHIFT, ActionPadKeyMap.EXTRAS.get(5).mods[0]);
    }

    @Test
    public void typingKeyboardCoversTheUsKeysWithoutDuplicates() {
        Set<Integer> seen = new HashSet<>();
        int keys = 0;
        for (String[] row : ActionPadKeyMap.TYPING_ROWS) {
            for (String pair : row) {
                assertEquals(pair, 2, pair.length());
                assertTrue("duplicate key " + pair, seen.add(ActionPadKeyMap.vkOf(pair.charAt(0))));
                keys++;
            }
        }
        assertEquals(12 + 12 + 11 + 10, keys);
        assertEquals(0xBD, ActionPadKeyMap.vkOf('-'));
        assertEquals(0xDE, ActionPadKeyMap.vkOf('\''));
        assertEquals('Q', ActionPadKeyMap.vkOf('q'));
    }

    @Test
    public void remapListHasNoDuplicateKeysAndNamesThem() {
        Set<Integer> seen = new HashSet<>();
        for (ActionPadKeyMap.KeyDef key : ActionPadKeyMap.KEYS) {
            assertTrue("duplicate key code for " + key.label, seen.add(key.vk));
            assertEquals(key.label, ActionPadKeyMap.labelFor(key.vk));
        }
        assertEquals("Shift", ActionPadKeyMap.labelFor(ActionPadKeyMap.VK_LSHIFT));
        assertEquals("0xFE", ActionPadKeyMap.labelFor(0xFE));
        // Every default button can be shown by name in the remap screen.
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            for (ActionPadKeyMap.Slot slot : page.slots) {
                if (slot.macro == null) assertTrue(slot.label, !ActionPadKeyMap.labelFor(slot.vk).startsWith("0x"));
            }
        }
    }

    @Test
    public void modifierMasksRoundTrip() {
        for (int mask = 0; mask < 8; mask++) {
            assertEquals(mask, ActionPadKeyMap.maskOf(ActionPadKeyMap.modsFromMask(mask)));
        }
        assertEquals(2, ActionPadKeyMap.modsFromMask(5).length);
    }

    @Test
    public void chatButtonsOpenTheKeyboardAndMacrosDoNot() {
        assertTrue(find("Open chat").opensKeyboard);
        assertTrue(find("Slash command").opensKeyboard);
        assertTrue(!find("Reply").opensKeyboard);
        assertTrue(find("Sit").macro != null && !find("Sit").opensKeyboard);
    }
}
