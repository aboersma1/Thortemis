package com.limelight.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WhatsNewTest {
    @Test
    public void showsOnFirstRunAndAfterAnUpdate() {
        assertTrue(WhatsNew.isUnseen(null, "1.0.0"));
        assertTrue(WhatsNew.isUnseen("1.0.0", "1.1.0"));
    }

    @Test
    public void doesNotShowTwiceForTheSameVersion() {
        assertFalse(WhatsNew.isUnseen("1.0.0", "1.0.0"));
    }
}
