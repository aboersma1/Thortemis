package com.limelight.ui;

/**
 * A box bouncing around a rectangle, like the old DVD player screensavers. Plain Java so it can be tested.
 * The box never leaves the rectangle; {@link #step} says whether it just hit a wall.
 */
final class PadBounce {
    float x;
    float y;
    float vx;
    float vy;
    private final float maxX;
    private final float maxY;

    /** {@code speed} is pixels per second along each axis. The box starts at (x, y), moving down and right. */
    PadBounce(float boundsW, float boundsH, float w, float h, float speed, float startX, float startY) {
        maxX = Math.max(0f, boundsW - w);
        maxY = Math.max(0f, boundsH - h);
        x = Math.max(0f, Math.min(startX, maxX));
        y = Math.max(0f, Math.min(startY, maxY));
        vx = speed;
        vy = speed;
    }

    /** Moves for {@code dtSec}. True if it bounced off a wall during this step. */
    boolean step(float dtSec) {
        // A long pause (the app was in the background) must not make it jump across the screen.
        float dt = Math.max(0f, Math.min(dtSec, 0.1f));
        boolean bounced = false;
        x += vx * dt;
        y += vy * dt;
        if (x < 0f) { x = -x; vx = Math.abs(vx); bounced = true; }
        if (x > maxX) { x = 2 * maxX - x; vx = -Math.abs(vx); bounced = true; }
        if (y < 0f) { y = -y; vy = Math.abs(vy); bounced = true; }
        if (y > maxY) { y = 2 * maxY - y; vy = -Math.abs(vy); bounced = true; }
        x = Math.max(0f, Math.min(x, maxX));
        y = Math.max(0f, Math.min(y, maxY));
        return bounced;
    }
}
