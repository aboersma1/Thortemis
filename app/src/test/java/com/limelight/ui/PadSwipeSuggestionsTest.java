package com.limelight.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class PadSwipeSuggestionsTest {
    private static PadSwipeSuggestions three() {
        return new PadSwipeSuggestions(Arrays.asList("hello", "help", "hell"));
    }

    @Test
    public void theBestWordIsTypedAndTheOthersAreOffered() {
        PadSwipeSuggestions s = three();
        assertEquals("hello", s.typed());
        assertEquals(Arrays.asList("help", "hell"), s.alternatives());
        assertEquals(6, s.eraseCount());
    }

    @Test
    public void pickingOneSwapsItInAndOffersTheOldOneBack() {
        PadSwipeSuggestions s = three();
        s.choose("help");
        assertEquals("help", s.typed());
        assertEquals(Arrays.asList("hello", "hell"), s.alternatives());
        assertEquals(5, s.eraseCount());
        s.choose("hello");
        assertEquals(Arrays.asList("help", "hell"), s.alternatives());
    }

    @Test
    public void aWordThatIsNotACandidateIsIgnored() {
        PadSwipeSuggestions s = three();
        s.choose("zebra");
        assertEquals("hello", s.typed());
    }

    @Test
    public void aSingleCandidateHasNoAlternatives() {
        assertEquals(0, new PadSwipeSuggestions(Arrays.asList("hello")).alternatives().size());
    }
}
