package com.limelight.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Fades a view to near black after it has been left alone for a while, to spare an OLED screen. Any touch
 * brings it back; a finger held down never counts as idle.
 */
final class PadDimmer {
    private static final float DIM_ALPHA = 0.18f;

    private final View view;
    private long idleMs;
    private long sleepMs;
    private Runnable onSleep = () -> { };
    private Runnable onWake = () -> { };
    private boolean asleep = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean enabled = true;
    private boolean dimmed = false;
    private boolean touching = false;
    private long lastTouch = SystemClock.uptimeMillis();

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            long remaining = idleMs - (SystemClock.uptimeMillis() - lastTouch);
            if (touching || remaining > 0) {
                handler.postDelayed(this, touching ? idleMs : remaining);
                return;
            }
            dimmed = true;
            view.animate().alpha(DIM_ALPHA).setDuration(700).start();
        }
    };

    private final Runnable sleepCheck = new Runnable() {
        @Override
        public void run() {
            long remaining = sleepMs - (SystemClock.uptimeMillis() - lastTouch);
            if (touching || remaining > 0) {
                handler.postDelayed(this, touching ? sleepMs : remaining);
                return;
            }
            asleep = true;
            onSleep.run();
        }
    };

    /** {@code idleMs} of 0 means never dim. */
    PadDimmer(View view, long idleMs) {
        this.view = view;
        this.idleMs = idleMs;
    }

    /** Changes how long the view is left alone before it dims, and restarts the countdown. */
    void setIdleMs(long idleMs) {
        if (idleMs == this.idleMs) return;
        this.idleMs = idleMs;
        start();
    }

    /** The screen saver: after {@code sleepMs} idle (0 = never) {@code sleep} runs, and {@code wake} on the next touch. */
    void setSaver(long sleepMs, Runnable sleep, Runnable wake) {
        this.onSleep = sleep;
        this.onWake = wake;
        if (sleepMs == this.sleepMs) return;
        this.sleepMs = sleepMs;
        start();
    }

    /** True while the saver is showing. */
    boolean isAsleep() {
        return asleep;
    }

    /** Off when the host screen already dims itself. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        start();
    }

    /** Brightens the view and starts the idle countdown again. */
    void start() {
        lastTouch = SystemClock.uptimeMillis();
        handler.removeCallbacks(check);
        handler.removeCallbacks(sleepCheck);
        brighten();
        if (enabled && idleMs > 0) handler.postDelayed(check, idleMs);
        if (enabled && sleepMs > 0) handler.postDelayed(sleepCheck, sleepMs);
    }

    void stop() {
        handler.removeCallbacks(check);
        handler.removeCallbacks(sleepCheck);
        brighten();
    }

    /** Call for every touch. The touch itself is not consumed. */
    void onTouch(MotionEvent event) {
        int action = event.getActionMasked();
        lastTouch = SystemClock.uptimeMillis();
        touching = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
        if (dimmed || asleep) start();
    }

    private void brighten() {
        if (asleep) {
            asleep = false;
            onWake.run();
        }
        if (dimmed) {
            dimmed = false;
            view.animate().cancel();
            view.setAlpha(1f);
        }
    }
}
