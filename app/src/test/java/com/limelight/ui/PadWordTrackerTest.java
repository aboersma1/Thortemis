package com.limelight.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PadWordTrackerTest {
    private static PadWordTracker typed(String text, boolean... shift) {
        PadWordTracker tracker = new PadWordTracker();
        for (int i = 0; i < text.length(); i++) tracker.letter(text.charAt(i), i < shift.length && shift[i]);
        return tracker;
    }

    @Test
    public void followsTheLettersTyped() {
        assertEquals("hel", typed("hel").prefix());
    }

    @Test
    public void anApostropheBelongsToTheWord() {
        assertEquals("don'", typed("don'").prefix());
    }

    @Test
    public void backspaceTakesOffTheLastLetterAndIsSafeWhenEmpty() {
        PadWordTracker tracker = typed("hey");
        tracker.backspace();
        assertEquals("he", tracker.prefix());
        tracker.reset();
        tracker.backspace();
        assertEquals("", tracker.prefix());
    }

    @Test
    public void resetStartsAgain() {
        PadWordTracker tracker = typed("hello");
        tracker.reset();
        assertEquals("", tracker.prefix());
        assertEquals(PadInput.Caps.NONE, tracker.caps());
    }

    @Test
    public void capitalisationIsRememberedForCompletions() {
        assertEquals(PadInput.Caps.NONE, typed("he").caps());
        assertEquals(PadInput.Caps.FIRST, typed("he", true).caps());
        assertEquals(PadInput.Caps.FIRST, typed("h", true).caps());
        assertEquals(PadInput.Caps.ALL, typed("he", true, true).caps());
        assertEquals(PadInput.Caps.FIRST, typed("hE", true, false).caps());
        assertEquals(PadInput.Caps.NONE, typed("hE", false, true).caps());
    }

    @Test
    public void backspaceKeepsCapitalisationInStepWithTheLetters() {
        PadWordTracker tracker = typed("hey", true, false, false);
        tracker.backspace();
        tracker.backspace();
        assertEquals(PadInput.Caps.FIRST, tracker.caps());
        tracker.backspace();
        assertEquals(PadInput.Caps.NONE, tracker.caps());
    }
}
