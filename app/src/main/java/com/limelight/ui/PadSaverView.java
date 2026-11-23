package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.View;

/**
 * The screen saver: solid black, with (if the player chose it) the Thortemis mark drifting around and
 * changing colour at each wall, like the old DVD player screensavers. The mark is our own wordmark,
 * not Blizzard's or WoW Forever's logo.
 */
final class PadSaverView extends View {
    private static final int[] COLORS = {0xFF4FC3F7, 0xFFFFB74D, 0xFF81C784, 0xFFBA68C8, 0xFFE57373, 0xFFFFF176};
    private static final String WORD = "THORTEMIS";

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private boolean logo;
    private boolean running;
    private PadBounce bounce;
    private int color;
    private long last;
    private float markW, markH;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long nanos) {
            if (!running) return;
            if (bounce != null) {
                float dt = last == 0 ? 0f : (nanos - last) / 1e9f;
                if (bounce.step(dt)) color = (color + 1) % COLORS.length;
                invalidate();
            }
            last = nanos;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    PadSaverView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF000000);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(20 * density);
        text.setTextAlign(Paint.Align.CENTER);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2.5f * density);
        markW = text.measureText(WORD) + 28 * density;
        markH = 44 * density;
    }

    /** Starts or stops the animation. {@code withLogo} false is a plain black screen. */
    void run(boolean on, boolean withLogo) {
        logo = withLogo;
        running = on && withLogo;
        Choreographer.getInstance().removeFrameCallback(frame);
        last = 0;
        if (running) {
            bounce = null;
            ensureBounce();
            Choreographer.getInstance().postFrameCallback(frame);
        }
        invalidate();
    }

    private void ensureBounce() {
        if (bounce == null && getWidth() > 0 && getHeight() > 0) {
            float x = (float) Math.random() * Math.max(1, getWidth() - markW);
            float y = (float) Math.random() * Math.max(1, getHeight() - markH);
            bounce = new PadBounce(getWidth(), getHeight(), markW, markH, 55 * density, x, y);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounce = null;
        if (running) ensureBounce();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!logo) return;
        ensureBounce();
        if (bounce == null) return;
        int c = COLORS[color];
        edge.setColor(c);
        text.setColor(c);
        box.set(bounce.x, bounce.y, bounce.x + markW, bounce.y + markH);
        canvas.drawRoundRect(box, 10 * density, 10 * density, edge);
        float baseline = box.centerY() - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(WORD, box.centerX(), baseline, text);
    }
}
