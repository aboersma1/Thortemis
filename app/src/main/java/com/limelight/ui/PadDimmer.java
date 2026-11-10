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
    private final long idleMs;
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

    /** {@code idleMs} of 0 means never dim. */
    PadDimmer(View view, long idleMs) {
        this.view = view;
        this.idleMs = idleMs;
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
        brighten();
        if (enabled && idleMs > 0) handler.postDelayed(check, idleMs);
    }

    void stop() {
        handler.removeCallbacks(check);
        brighten();
    }

    /** Call for every touch. The touch itself is not consumed. */
    void onTouch(MotionEvent event) {
        int action = event.getActionMasked();
        lastTouch = SystemClock.uptimeMillis();
        touching = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
        if (dimmed) start();
    }

    private void brighten() {
        if (dimmed) {
            dimmed = false;
            view.animate().cancel();
            view.setAlpha(1f);
        }
    }
}
