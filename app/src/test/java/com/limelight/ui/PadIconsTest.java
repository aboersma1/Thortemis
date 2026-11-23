package com.limelight.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PadIconsTest {
    @Test
    public void everyInterfaceAndUtilityButtonHasAnIcon() {
        for (ActionPadKeyMap.Page page : ActionPadKeyMap.PAGES) {
            if (!page.id.equals("interface") && !page.id.equals("utility")) continue;
            for (ActionPadKeyMap.Slot slot : page.slots) assertTrue(slot.id, PadIcons.has(slot.id));
        }
    }

    @Test
    public void otherButtonsKeepTheirWords() {
        assertFalse(PadIcons.has("Party chat"));
        assertFalse(PadIcons.has("Hello"));
    }
}
