package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PadTabOptionsTest {
    @Test
    public void defaultsAreTheStockNamesAndTheNumberBlock() {
        PadTabOptions o = new PadTabOptions();
        assertEquals("Utility", o.name("utility", "Utility"));
        assertEquals(PadTabOptions.NUMBERS, o.side("utility"));
        assertEquals("", o.serialize());
    }

    @Test
    public void renamesAndSidesSurviveASaveAndLoad() {
        PadTabOptions o = new PadTabOptions();
        o.setName("interface", "  Panels  ");
        o.setSide("interface", "utility");
        o.setSide("chat", PadTabOptions.HIDDEN);
        PadTabOptions back = PadTabOptions.parse(o.serialize());
        assertEquals("Panels", back.name("interface", "Interface"));
        assertEquals("utility", back.side("interface"));
        assertEquals(PadTabOptions.HIDDEN, back.side("chat"));
    }

    @Test
    public void namesAreShortAndHaveNoControlCharacters() {
        PadTabOptions o = new PadTabOptions();
        o.setName("utility", "a\tb\nc this name is far too long");
        String n = o.name("utility", "x");
        assertTrue(n.length() <= PadTabOptions.MAX_NAME);
        assertFalse(n.contains("\t") || n.contains("\n"));
        assertEquals(o.serialize(), PadTabOptions.parse(o.serialize()).serialize());
    }

    @Test
    public void clearingBothChoicesForgetsTheTab() {
        PadTabOptions o = new PadTabOptions();
        o.setName("chat", "Talk");
        o.setSide("chat", "interface");
        o.setName("chat", "");
        o.setSide("chat", PadTabOptions.NUMBERS);
        assertEquals("", o.serialize());
    }

    @Test
    public void theTargetTabAlwaysKeepsTheNumbersButCanBeRenamed() {
        PadTabOptions o = new PadTabOptions();
        o.setSide("target", PadTabOptions.HIDDEN);
        assertEquals(PadTabOptions.NUMBERS, o.side("target"));
        o.setName("target", "Party");
        assertEquals("Party", o.name("target", "Target"));
        assertFalse(PadTabOptions.canChooseSide("target"));
    }

    @Test
    public void fullScreenTabsHaveNoRightSide() {
        for (String id : new String[]{"replies", "mouse", "keyboard"}) assertFalse(id, PadTabOptions.canChooseSide(id));
        for (String id : new String[]{"interface", "utility", "chat", "fkeys"}) assertTrue(id, PadTabOptions.canChooseSide(id));
    }

    @Test
    public void aTabCannotShowItselfAndUnknownChoicesFallBackToNumbers() {
        assertFalse(PadTabOptions.sideChoices("utility").contains("utility"));
        assertTrue(PadTabOptions.sideChoices("utility").contains("interface"));
        assertFalse(PadTabOptions.sideChoices("utility").contains("replies"));
        PadTabOptions o = new PadTabOptions();
        o.setSide("utility", "utility");
        o.setSide("chat", "nonsense");
        assertEquals(PadTabOptions.NUMBERS, o.side("utility"));
        assertEquals(PadTabOptions.NUMBERS, o.side("chat"));
    }

    @Test
    public void junkInSavedTextIsIgnored() {
        PadTabOptions o = PadTabOptions.parse("nope\tx\ty\n\nchat\tTalk\thidden\nbroken line");
        assertEquals("Talk", o.name("chat", "Chat"));
        assertEquals(PadTabOptions.HIDDEN, o.side("chat"));
        assertEquals("Chat", o.name("nope", "Chat"));
    }
}
