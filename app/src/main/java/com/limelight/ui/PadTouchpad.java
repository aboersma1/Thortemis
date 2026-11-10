package com.limelight.ui;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
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

        addView(new Surface(), kit.weighted(this, 1f));

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
}
