package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PadBounceTest {
    @Test
    public void staysInsideTheScreenForAMinuteOfFrames() {
        PadBounce b = new PadBounce(800, 400, 120, 40, 90, 10, 10);
        int bounces = 0;
        for (int i = 0; i < 60 * 60; i++) {
            if (b.step(1 / 60f)) bounces++;
            assertTrue(b.x >= 0 && b.x <= 680);
            assertTrue(b.y >= 0 && b.y <= 360);
        }
        assertTrue(bounces > 10);
    }

    @Test
    public void bouncingTurnsTheDirectionAround() {
        PadBounce b = new PadBounce(200, 200, 100, 100, 100, 99, 0);
        assertTrue(b.step(0.1f));
        assertTrue(b.vx < 0);
        assertEquals(100f, b.vy, 0.001f);
    }

    @Test
    public void aLongPauseDoesNotJumpAcrossTheScreen() {
        PadBounce b = new PadBounce(1000, 1000, 10, 10, 100, 0, 0);
        b.step(30f);
        assertEquals(10f, b.x, 0.001f);
    }

    @Test
    public void aBoxBiggerThanTheScreenJustSitsAtTheCorner() {
        PadBounce b = new PadBounce(50, 50, 100, 100, 100, 5, 5);
        for (int i = 0; i < 100; i++) b.step(0.05f);
        assertEquals(0f, b.x, 0.001f);
        assertEquals(0f, b.y, 0.001f);
    }
}
