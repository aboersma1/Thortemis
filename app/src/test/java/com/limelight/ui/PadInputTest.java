package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.ui.ActionPadKeyMap.Slot;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PadInputTest {
    private static final int SHIFT = ActionPadKeyMap.VK_LSHIFT;
    private static final int ALT = ActionPadKeyMap.VK_LMENU;

    /** Records what reaches the host, and checks that no key or button is released that was not down. */
    private static final class Host implements ExternalActionPadView.KeySink {
        final List<String> events = new ArrayList<>();
        final Map<Integer, Integer> keysDown = new HashMap<>();
        final Map<Integer, Integer> buttonsDown = new HashMap<>();

        @Override
        public void sendKey(short vk, boolean down, byte modifiers) {
            events.add(String.format("%s%02X/%d", down ? "+" : "-", vk & 0xFF, modifiers));
            int n = keysDown.getOrDefault((int) vk, 0) + (down ? 1 : -1);
            assertTrue("key 0x" + Integer.toHexString(vk) + " released while up", n >= 0);
            keysDown.put((int) vk, n);
        }

        @Override
        public void sendMouseButton(byte button, boolean down) {
            events.add(String.format("%sM%d", down ? "+" : "-", button));
            int n = buttonsDown.getOrDefault((int) button, 0) + (down ? 1 : -1);
            assertTrue("button released while up", n >= 0);
            buttonsDown.put((int) button, n);
        }

        boolean nothingHeld() {
            for (int n : keysDown.values()) if (n != 0) return false;
            for (int n : buttonsDown.values()) if (n != 0) return false;
            return true;
        }
    }

    private static final class Later implements PadInput.Scheduler {
        final List<long[]> delays = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void post(Runnable task, long delayMs) {
            delays.add(new long[]{delayMs, tasks.size()});
            tasks.add(task);
        }

        @Override
        public void cancelAll() {
            delays.clear();
            tasks.clear();
        }

        void runAll() {
            List<long[]> order = new ArrayList<>(delays);
            Collections.sort(order, Comparator.comparingLong(a -> a[0]));
            List<Runnable> copy = new ArrayList<>(tasks);
            cancelAll();
            for (long[] d : order) copy.get((int) d[1]).run();
        }
    }

    private Host host;
    private Later later;
    private PadInput input;

    @Before
    public void setUp() {
        host = new Host();
        later = new Later();
        input = new PadInput(host, later);
    }

    private static Slot slot(List<Slot> slots, String id) {
        for (Slot s : slots) if (s.id.equals(id)) return s;
        throw new AssertionError(id);
    }

    private Slot number(String id) {
        return slot(ActionPadKeyMap.NUMBERS, id);
    }

    private static java.util.List<Slot> repliesSlots() {
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) if (page.id.equals("replies")) return page.slots;
        throw new AssertionError("no replies page");
    }

    private Slot page(String id) {
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            for (Slot s : page.slots) if (s.id.equals(id)) return s;
        }
        throw new AssertionError(id);
    }

    @Test
    public void aNumberKeyIsOnePressAndOneRelease() {
        input.release(input.press(number("5")));
        assertEquals(Arrays.asList("+35/0", "-35/0"), host.events);
    }

    @Test
    public void armedShiftWrapsTheNextNumberKeyThenLetsGo() {
        input.toggleModifier(SHIFT);
        input.release(input.press(number("1")));
        assertEquals(Arrays.asList("+A0/0", "+31/1", "-31/1", "-A0/0"), host.events);
        assertFalse(input.isArmed(SHIFT));
    }

    @Test
    public void lockedModifierSurvivesKeys() {
        input.lockModifier(ALT);
        input.release(input.press(number("2")));
        input.release(input.press(number("3")));
        assertTrue(input.isArmed(ALT));
        assertTrue(input.isLocked(ALT));
        assertEquals(1, host.keysDown.get(ALT).intValue());
        input.toggleModifier(ALT);
        assertTrue(host.nothingHeld());
    }

    @Test
    public void pageButtonsIgnoreArmedModifiersAndThenRestoreThem() {
        input.toggleModifier(SHIFT);
        input.release(input.press(page("Map")));
        assertEquals(Arrays.asList("+A0/0", "-A0/0", "+4D/0", "-4D/0", "+A0/0"), host.events);
        assertTrue(input.isArmed(SHIFT));
        assertEquals(1, host.keysDown.get(SHIFT).intValue());
    }

    @Test
    public void comboButtonsPressTheirOwnModifiers() {
        input.release(input.press(page("Previous enemy")));
        assertEquals(Arrays.asList("+A0/0", "+09/1", "-09/1", "-A0/0"), host.events);
    }

    @Test
    public void chatMacroTypesEnterTextEnterWithNoModifiers() {
        input.toggleModifier(SHIFT);
        host.events.clear();
        assertEquals(null, input.press(page("Sit")));
        assertEquals(Arrays.asList("-A0/0"), host.events);
        host.events.clear();
        later.runAll();
        assertEquals(Arrays.asList("+0D/0", "-0D/0", "+BF/0", "-BF/0", "+53/0", "-53/0", "+49/0", "-49/0",
                "+54/0", "-54/0", "+0D/0", "-0D/0"), host.events);
    }

    @Test
    public void channelButtonTypesItsPrefixAndDoesNotSend() {
        assertEquals(null, input.press(page("Party chat")));
        host.events.clear();
        later.runAll();
        assertEquals(Arrays.asList("+0D/0", "-0D/0", "+BF/0", "-BF/0", "+50/0", "-50/0", "+20/0", "-20/0"), host.events);
    }

    @Test
    public void chatMacroHoldsShiftAroundCapitalsAndPunctuation() {
        PadConfig config = new PadConfig();
        config.setMacro("Thanks", "Hi!");
        Slot thanks = null;
        for (Slot s : config.resolve("replies", repliesSlots(), false)) if (s.id.equals("Thanks")) thanks = s;
        input.press(thanks);
        host.events.clear();
        later.runAll();
        assertEquals(Arrays.asList("+0D/0", "-0D/0",
                "+A0/0", "+48/1", "-48/1", "-A0/0",
                "+49/0", "-49/0",
                "+A0/0", "+31/1", "-31/1", "-A0/0",
                "+0D/0", "-0D/0"), host.events);
    }

    @Test
    public void mouseButtonStaysDownUntilEveryHolderLetsGo() {
        input.toggleHold();
        input.mouseButton(ActionPadKeyMap.MOUSE_LEFT, true);
        input.mouseButton(ActionPadKeyMap.MOUSE_LEFT, false);
        assertEquals(1, host.buttonsDown.get((int) ActionPadKeyMap.MOUSE_LEFT).intValue());
        input.toggleHold();
        assertTrue(host.nothingHeld());
    }

    @Test
    public void aClickGoesDownNowAndUpLater() {
        input.click(ActionPadKeyMap.MOUSE_RIGHT);
        assertEquals(Arrays.asList("+M3"), host.events);
        later.runAll();
        assertEquals(Arrays.asList("+M3", "-M3"), host.events);
    }

    @Test
    public void releaseAllLeavesNothingHeldAndCancelsTyping() {
        input.lockModifier(SHIFT);
        input.press(number("4"));
        input.press(page("Hide UI"));
        input.toggleHold();
        input.mouseButton(ActionPadKeyMap.MOUSE_RIGHT, true);
        input.press(page("Dance"));
        input.releaseAll();
        assertTrue(host.events.toString(), host.nothingHeld());
        assertFalse(input.anyArmed());
        assertFalse(input.isHoldOn());
        later.runAll();
        assertTrue(host.nothingHeld());
    }

    /** Random touches in any order, ended at any moment: the host must never be left with a stuck key. */
    @Test
    public void randomTouchesNeverLeaveAKeyStuck() {
        List<Slot> all = new ArrayList<>(ActionPadKeyMap.NUMBERS);
        all.addAll(ActionPadKeyMap.EXTRAS);
        for (ActionPadKeyMap.Page p : ActionPadKeyMap.PAGES) all.addAll(p.slots);
        for (int seed = 0; seed < 300; seed++) {
            Random rnd = new Random(seed);
            Host h = new Host();
            Later l = new Later();
            PadInput in = new PadInput(h, l);
            List<PadInput.Press> held = new ArrayList<>();
            for (int step = 0; step < 60; step++) {
                switch (rnd.nextInt(7)) {
                    case 0: in.toggleModifier(ActionPadKeyMap.MODIFIER_KEYS[rnd.nextInt(3)]); break;
                    case 1: in.lockModifier(ActionPadKeyMap.MODIFIER_KEYS[rnd.nextInt(3)]); break;
                    case 2:
                    case 3:
                        PadInput.Press p = in.press(all.get(rnd.nextInt(all.size())));
                        if (p != null) held.add(p);
                        break;
                    case 4:
                        if (!held.isEmpty()) in.release(held.remove(rnd.nextInt(held.size())));
                        break;
                    case 5: in.mouseButton(ActionPadKeyMap.MOUSE_LEFT, rnd.nextBoolean()); break;
                    default: in.toggleHold(); break;
                }
            }
            in.releaseAll();
            l.runAll();
            assertTrue("seed " + seed + ": " + h.events, h.nothingHeld());
        }
    }
}
