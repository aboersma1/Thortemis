package com.limelight.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActionPadSettingsTest {
    @Test
    public void nextSlotSkipsEmptySlotsAndWrapsRound() {
        boolean[] used = {false, true, false, true};
        assertEquals(1, ActionPadSettings.nextSlot(0, used));
        assertEquals(3, ActionPadSettings.nextSlot(1, used));
        assertEquals(1, ActionPadSettings.nextSlot(3, used));
    }

    @Test
    public void nextSlotStaysOnTheOnlyUsedSlot() {
        assertEquals(2, ActionPadSettings.nextSlot(2, new boolean[]{false, false, true, false}));
    }

    @Test
    public void nextSlotIsZeroWhenNothingIsSaved() {
        assertEquals(0, ActionPadSettings.nextSlot(1, new boolean[4]));
    }
}
