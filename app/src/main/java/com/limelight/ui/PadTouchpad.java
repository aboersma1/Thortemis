package com.limelight.ui;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Mouse tab: a drag surface above Left, Hold and Right buttons. One finger moves the host's cursor,
 * a quick tap clicks left, a quick two-finger tap clicks right.
 */
final class PadTouchpad extends LinearLayout {
    private static final long TAP_MAX_MS = 250;
    private static final float TAP_SLOP_DP = 8;

    private final PadKit kit;

    PadTouchpad(PadKit kit) {
        super(kit.context);
        this.kit = kit;
        setOrientation(VERTICAL);
        setMotionEventSplittingEnabled(true);

        LinearLayout pad = kit.box(HORIZONTAL);
        pad.addView(new Surface(), kit.weighted(pad, 1f));
        if (kit.settings.scrollStrip) {
            pad.addView(new ScrollStrip(), kit.settings.scrollLeft ? 0 : 1, new LayoutParams(kit.dp(36), LayoutParams.MATCH_PARENT));
        }
        addView(pad, kit.weighted(this, 1f));

        LinearLayout row = kit.box(HORIZONTAL);
        row.addView(clickButton("Left", ActionPadKeyMap.MOUSE_LEFT), kit.weighted(row, 1f));
        row.addView(holdButton(), kit.weighted(row, 1f));
        row.addView(clickButton("Right", ActionPadKeyMap.MOUSE_RIGHT), kit.weighted(row, 1f));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, kit.dp(64)));
    }

    private TextView clickButton(String label, final byte button) {
        return kit.pressButton(label, 15, kit.theme.face, kit.theme.border, kit.theme.accent, new PadKit.PressHandler() {
            @Override
            public void down() {
                kit.input.mouseButton(button, true);
            }

            @Override
            public void up(boolean cancelled) {
                kit.input.mouseButton(button, false);
            }
        });
    }

    /** Toggles the left button held down, for dragging windows and items without keeping a finger on Left. */
    private TextView holdButton() {
        final TextView view = kit.tapButton("Hold", 15, kit.input::toggleHold);
        kit.addRefresher(() -> {
            boolean on = kit.input.isHoldOn();
            view.setBackground(on ? kit.rect(kit.theme.accent, kit.theme.accent) : kit.rect(kit.theme.face, kit.theme.border));
            view.setTextColor(on ? kit.theme.bg : kit.theme.text);
        });
        return view;
    }

    private final class Surface extends TextView {
        private float lastX, lastY, downX, downY, remX, remY;
        private long downTime;
        private int maxPointers;
        private boolean moved;
        private boolean resync;

        Surface() {
            super(kit.context);
            setText("Touchpad");
            setGravity(Gravity.CENTER);
            setTextColor(kit.theme.border);
            GradientDrawable d = kit.rect(kit.theme.bg, kit.theme.border);
            if (!kit.theme.flat) d.setColors(new int[]{kit.theme.bg, kit.theme.bgTop});
            setBackground(d);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = e.getEventTime();
                    downX = lastX = e.getX();
                    downY = lastY = e.getY();
                    remX = remY = 0;
                    moved = false;
                    resync = false;
                    maxPointers = 1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    maxPointers = Math.max(maxPointers, e.getPointerCount());
                    resync = true;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    resync = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() == 1) drag(e);
                    break;
                case MotionEvent.ACTION_UP:
                    boolean quick = e.getEventTime() - downTime <= TAP_MAX_MS;
                    if (!moved && quick && kit.settings.tapToClick) {
                        kit.input.click(maxPointers >= 2 ? ActionPadKeyMap.MOUSE_RIGHT : ActionPadKeyMap.MOUSE_LEFT);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        private void drag(MotionEvent e) {
            if (resync) {
                lastX = e.getX();
                lastY = e.getY();
                resync = false;
                return;
            }
            if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > kit.dp(TAP_SLOP_DP)) moved = true;
            if (moved) {
                float scale = kit.settings.touchSpeed / kit.dp(1);
                remX += (e.getX() - lastX) * scale;
                remY += (e.getY() - lastY) * scale;
                int ix = (int) remX, iy = (int) remY;
                remX -= ix;
                remY -= iy;
                if (ix != 0 || iy != 0) {
                    kit.input.moveMouse((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, ix)),
                            (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, iy)));
                }
            }
            lastX = e.getX();
            lastY = e.getY();
        }
    }

    /** A thin strip beside the touchpad. Sliding a finger along it turns the wheel; it never moves the cursor. */
    private final class ScrollStrip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastY, carry;
        private boolean touching;

        ScrollStrip() {
            super(kit.context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // A faint line with arrows at both ends, brighter while a finger is on it.
            paint.setColor(kit.theme.border);
            paint.setAlpha(touching ? 0xC0 : 0x70);
            float cx = getWidth() / 2f, inset = kit.dp(22), arrow = kit.dp(5);
            canvas.drawRoundRect(cx - kit.dp(1.5f), inset, cx + kit.dp(1.5f), getHeight() - inset, kit.dp(1.5f), kit.dp(1.5f), paint);
            for (int end = 0; end < 2; end++) {
                float tip = end == 0 ? kit.dp(8) : getHeight() - kit.dp(8);
                float base = tip + (end == 0 ? 1 : -1) * arrow * 1.4f;
                Path p = new Path();
                p.moveTo(cx, tip);
                p.lineTo(cx - arrow, base);
                p.lineTo(cx + arrow, base);
                p.close();
                canvas.drawPath(p, paint);
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    lastY = e.getY();
                    carry = 0;
                    touching = true;
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    carry += e.getY() - lastY;
                    lastY = e.getY();
                    float step = kit.dp(kit.settings.scrollDp);
                    // Dragging down scrolls the page down (wheel notches are negative), unless inverted.
                    byte dir = (byte) (kit.settings.scrollInvert ? 1 : -1);
                    for (; carry >= step; carry -= step) kit.input.scroll(dir);
                    for (; carry <= -step; carry += step) kit.input.scroll((byte) -dir);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touching = false;
                    invalidate();
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
