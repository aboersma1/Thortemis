package com.limelight.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Small line icons for the Interface and Utility buttons, drawn in code (no image files), used when the
 * player picks Icons instead of Text. Each is drawn on a 24 by 24 grid and scaled to fit.
 */
final class PadIcons {
    private static final Set<String> IDS = new HashSet<>(Arrays.asList(
            "Character", "Spellbook", "Talents", "Quest Log", "Map", "Social", "Guild", "Group Finder",
            "Achievements", "Professions", "Bags", "System",
            "Sheathe weapons", "Auto-run", "Walk / run", "Sit / stand", "Hide UI", "Enemy nameplates",
            "Friendly nameplates", "Chat page up", "Chat page down", "Screenshot"));

    private PadIcons() {
    }

    /** Whether the button with this stable id (its default label) has an icon. */
    static boolean has(String id) {
        return IDS.contains(id);
    }

    static Drawable make(String id, int sizePx, int color) {
        Icon icon = new Icon(id, color);
        icon.setBounds(0, 0, sizePx, sizePx);
        return icon;
    }

    private static final class Icon extends Drawable {
        private final String id;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();

        Icon(String id, int color) {
            this.id = id;
            line.setColor(color);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            fill.setColor(color);
            fill.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.save();
            c.translate(b.left, b.top);
            c.scale(b.width() / 24f, b.height() / 24f);
            paint(c);
            c.restore();
        }

        private void ring(Canvas c, float cx, float cy, float r) {
            c.drawCircle(cx, cy, r, line);
        }

        private void arc(Canvas c, float l, float t, float r, float b, float start, float sweep) {
            box.set(l, t, r, b);
            c.drawArc(box, start, sweep, false, line);
        }

        private void rrect(Canvas c, float l, float t, float r, float b, float rad, Paint p) {
            box.set(l, t, r, b);
            c.drawRoundRect(box, rad, rad, p);
        }

        private void poly(Canvas c, boolean close, float... pts) {
            path.reset();
            path.moveTo(pts[0], pts[1]);
            for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
            if (close) path.close();
            c.drawPath(path, line);
        }

        private void paint(Canvas c) {
            switch (id) {
                case "Character":
                    ring(c, 12, 8, 3.5f);
                    arc(c, 5, 14, 19, 28, 180, 180);
                    break;
                case "Spellbook":
                    poly(c, true, 3, 6, 12, 8, 21, 6, 21, 19, 12, 21, 3, 19);
                    poly(c, false, 12, 8, 12, 21);
                    break;
                case "Talents":
                    poly(c, false, 12, 5, 6, 13, 12, 20, 18, 13, 12, 5);
                    for (float[] n : new float[][]{{12, 5}, {6, 13}, {18, 13}, {12, 20}}) c.drawCircle(n[0], n[1], 2.4f, fill);
                    break;
                case "Quest Log":
                    rrect(c, 5, 3, 19, 21, 3, line);
                    poly(c, false, 8, 8, 16, 8);
                    poly(c, false, 8, 12, 16, 12);
                    poly(c, false, 8, 16, 13, 16);
                    break;
                case "Map":
                    poly(c, true, 3, 6, 9, 4, 15, 6, 21, 4, 21, 18, 15, 20, 9, 18, 3, 20);
                    poly(c, false, 9, 4, 9, 18);
                    poly(c, false, 15, 6, 15, 20);
                    break;
                case "Social":
                    ring(c, 9, 8, 3);
                    arc(c, 3, 14, 15, 26, 180, 180);
                    ring(c, 17, 9, 2.5f);
                    arc(c, 14, 14, 21, 21, 180, 180);
                    break;
                case "Guild":
                    path.reset();
                    path.moveTo(12, 3);
                    path.lineTo(20, 6);
                    path.lineTo(20, 12);
                    path.quadTo(20, 18, 12, 21);
                    path.quadTo(4, 18, 4, 12);
                    path.lineTo(4, 6);
                    path.close();
                    c.drawPath(path, line);
                    break;
                case "Group Finder":
                    ring(c, 10, 10, 6);
                    poly(c, false, 14.5f, 14.5f, 20, 20);
                    break;
                case "Achievements":
                    path.reset();
                    path.moveTo(7, 4);
                    path.lineTo(17, 4);
                    path.lineTo(17, 10);
                    path.quadTo(17, 15, 12, 15);
                    path.quadTo(7, 15, 7, 10);
                    path.close();
                    c.drawPath(path, line);
                    poly(c, false, 12, 15, 12, 19);
                    poly(c, false, 8, 20, 16, 20);
                    path.reset();
                    path.moveTo(7, 6);
                    path.lineTo(4, 6);
                    path.quadTo(4, 10, 8, 11);
                    path.moveTo(17, 6);
                    path.lineTo(20, 6);
                    path.quadTo(20, 10, 16, 11);
                    c.drawPath(path, line);
                    break;
                case "Professions":
                    poly(c, false, 5, 19, 13, 11);
                    line.setStrokeWidth(4.5f);
                    poly(c, false, 11, 6, 18, 13);
                    line.setStrokeWidth(2f);
                    break;
                case "Bags":
                    rrect(c, 5, 9, 19, 21, 4, line);
                    arc(c, 8, 3, 16, 12, 180, 180);
                    poly(c, false, 5, 14, 19, 14);
                    break;
                case "System":
                    ring(c, 12, 12, 4);
                    ring(c, 12, 12, 7);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                        poly(c, false, 12 + 7 * cs, 12 + 7 * sn, 12 + 10 * cs, 12 + 10 * sn);
                    }
                    break;
                case "Sheathe weapons":
                    poly(c, false, 6, 18, 18, 6);
                    poly(c, false, 7, 11, 13, 17);
                    c.drawCircle(4.5f, 19.5f, 1.8f, fill);
                    break;
                case "Auto-run":
                    poly(c, false, 5, 6, 11, 12, 5, 18);
                    poly(c, false, 13, 6, 19, 12, 13, 18);
                    break;
                case "Walk / run":
                    c.drawOval(new RectF(5, 4, 11, 12), fill);
                    c.drawOval(new RectF(13, 12, 19, 20), fill);
                    break;
                case "Sit / stand":
                    poly(c, false, 8, 4, 8, 20);
                    poly(c, false, 8, 13, 17, 13, 17, 20);
                    break;
                case "Hide UI":
                    path.reset();
                    path.moveTo(2, 12);
                    path.quadTo(12, 3, 22, 12);
                    path.quadTo(12, 21, 2, 12);
                    c.drawPath(path, line);
                    ring(c, 12, 12, 3);
                    poly(c, false, 4, 20, 20, 4);
                    break;
                case "Enemy nameplates":
                    rrect(c, 4, 6, 20, 12, 2, fill);
                    poly(c, false, 8, 17, 16, 17);
                    break;
                case "Friendly nameplates":
                    rrect(c, 4, 6, 20, 12, 2, line);
                    rrect(c, 4, 6, 12, 12, 2, fill);
                    poly(c, false, 8, 17, 16, 17);
                    break;
                case "Chat page up":
                    poly(c, false, 6, 15, 12, 9, 18, 15);
                    poly(c, false, 5, 20, 19, 20);
                    break;
                case "Chat page down":
                    poly(c, false, 6, 9, 12, 15, 18, 9);
                    poly(c, false, 5, 4, 19, 4);
                    break;
                case "Screenshot":
                    rrect(c, 3, 7, 21, 20, 3, line);
                    ring(c, 12, 13.5f, 3.5f);
                    poly(c, false, 8, 7, 9.5f, 4.5f, 14.5f, 4.5f, 16, 7);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            line.setAlpha(alpha);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            line.setColorFilter(filter);
            fill.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return getBounds().width();
        }

        @Override
        public int getIntrinsicHeight() {
            return getBounds().height();
        }
    }
}
