package com.limelight.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The letter layer of the keyboard when swipe typing is on. A touch that starts on a letter and then travels
 * is taken over from the keys, drawn as a trail, and on lift turned into a word by {@link PadSwipe}. A touch
 * that does not travel is left to the keys, which type the letter.
 */
final class PadSwipeLayer extends LinearLayout {
    interface Listener {
        void onWord(String word);
    }

    private final PadKit kit;
    private final String[] layer;
    private final Listener listener;
    private final List<float[]> points = new ArrayList<>();
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float startDistance;
    private boolean tracking;
    private boolean swiping;
    private float downX, downY;
    private Map<Character, float[]> centers;
    private int centersWidth, centersHeight;

    PadSwipeLayer(PadKit kit, String[] layer, Listener listener) {
        super(kit.context);
        this.kit = kit;
        this.layer = layer;
        this.listener = listener;
        setOrientation(VERTICAL);
        setMotionEventSplittingEnabled(true);
        setWillNotDraw(false);
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeWidth(kit.dp(7));
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setStrokeJoin(Paint.Join.ROUND);
        trail.setColor(kit.theme.accent);
        trail.setAlpha(190);
        startDistance = kit.dp(18);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                reset();
                String key = PadKeyboardLayouts.keyAt(layer, getWidth(), getHeight(), ev.getX(), ev.getY());
                tracking = PadSwipeDictionary.get() != null && key != null && key.length() == 1
                        && key.charAt(0) >= 'a' && key.charAt(0) <= 'z';
                if (tracking) {
                    downX = ev.getX();
                    downY = ev.getY();
                    points.add(new float[]{downX, downY});
                }
                return false;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger means two-thumb typing, not a swipe.
                reset();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking || ev.getPointerCount() != 1) return false;
                addPoints(ev);
                if (Math.hypot(ev.getX() - downX, ev.getY() - downY) > startDistance) {
                    tracking = false;
                    swiping = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!swiping) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                addPoints(ev);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                addPoints(ev);
                finish();
                reset();
                return true;
            case MotionEvent.ACTION_CANCEL:
                reset();
                return true;
            default:
                return true;
        }
    }

    private void addPoints(MotionEvent ev) {
        for (int i = 0; i < ev.getHistorySize(); i++) points.add(new float[]{ev.getHistoricalX(i), ev.getHistoricalY(i)});
        points.add(new float[]{ev.getX(), ev.getY()});
    }

    private void finish() {
        PadSwipe.Dictionary dictionary = PadSwipeDictionary.get();
        if (dictionary == null || getWidth() == 0 || getHeight() == 0) return;
        if (centers == null || centersWidth != getWidth() || centersHeight != getHeight()) {
            centers = PadKeyboardLayouts.letterCenters(layer, getWidth(), getHeight());
            centersWidth = getWidth();
            centersHeight = getHeight();
        }
        List<String> words = PadSwipe.decode(points, centers, getHeight() / (float) layer.length, dictionary, 1);
        if (words.isEmpty()) return;
        if (kit.settings.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        listener.onWord(words.get(0));
    }

    private void reset() {
        boolean wasSwiping = swiping;
        tracking = false;
        swiping = false;
        points.clear();
        if (wasSwiping) invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!swiping || points.size() < 2) return;
        Path path = new Path();
        path.moveTo(points.get(0)[0], points.get(0)[1]);
        for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i)[0], points.get(i)[1]);
        canvas.drawPath(path, trail);
    }
}
