package com.limelight.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swipe typing: turns the path a finger drew across the letter keys into the most likely word.
 *
 * Every word in the dictionary has an ideal path, a line through the centres of its letters. The finger's
 * path and each ideal path are cut into the same number of evenly spaced points, and the words are ranked by
 * how far apart those points are, with a small bonus for common words. Only words that start near where the
 * finger landed and end near where it lifted are tried. Distances are measured in key rows, so the result does
 * not depend on the screen size.
 *
 * Pure Java, no Android dependencies.
 */
final class PadSwipe {
    /** Points each path is cut into. */
    static final int SAMPLES = 32;
    /** How far, in rows, the first and last letter may be from where the finger landed and lifted. */
    static final float EDGE_ROWS = 1.4f;
    /** A path shorter than this many rows is a tap or a wobble, not a swipe. */
    static final float MIN_LENGTH_ROWS = 0.8f;
    /** How much a rare word costs: this times the log of its rank, in rows. */
    static final float RARITY_WEIGHT = 0.08f;
    /** Game words rank like a word this common. */
    static final int EXTRA_RANK = 150;

    private PadSwipe() {}

    /** One word that can be swiped: what it types, the letters its path goes through, and how common it is. */
    static final class Entry {
        final String typed;
        final String letters;
        final int rank;

        Entry(String typed, String letters, int rank) {
            this.typed = typed;
            this.letters = letters;
            this.rank = rank;
        }
    }

    /** The words to choose from. */
    static final class Dictionary {
        final List<Entry> entries;

        private Dictionary(List<Entry> entries) {
            this.entries = entries;
        }

        /**
         * Reads word lists, one word per line, commonest first. Words are lower-case letters, with at most an
         * apostrophe. {@code extra} are game words, which rank like {@link #EXTRA_RANK}. When two words need
         * the same swipe (dont and don't), the commoner one is kept, so a contraction is typed properly.
         */
        static Dictionary parse(String main, String extra) {
            Map<String, Entry> best = new HashMap<>();
            add(best, main, -1);
            add(best, extra, EXTRA_RANK);
            return new Dictionary(new ArrayList<>(best.values()));
        }

        private static void add(Map<String, Entry> best, String text, int fixedRank) {
            if (text == null) return;
            int rank = 0;
            for (String line : text.split("\n")) {
                String word = line.trim();
                if (word.isEmpty()) continue;
                int thisRank = fixedRank >= 0 ? fixedRank + rank / 4 : rank;
                rank++;
                if (!word.matches("[a-z]+('[a-z]+)?")) continue;
                String letters = word.replace("'", "");
                if (collapse(letters).length() < 2) continue;
                Entry old = best.get(letters);
                if (old == null || thisRank < old.rank) best.put(letters, new Entry(word, letters, thisRank));
            }
        }
    }

    /** The word with each run of one letter cut to one: hello becomes helo, as a finger passes over a key once. */
    static String collapse(String letters) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < letters.length(); i++) {
            if (i == 0 || letters.charAt(i) != letters.charAt(i - 1)) sb.append(letters.charAt(i));
        }
        return sb.toString();
    }

    /**
     * The best {@code max} words for a finger path, best first. {@code points} are the {x, y} positions of the
     * finger in pixels, {@code centers} where each letter key is, in the same pixels, and {@code row} the height
     * of a key row in pixels. Empty when the path is too short or no word fits.
     */
    static List<String> decode(List<float[]> points, Map<Character, float[]> centers, float row, Dictionary dictionary, int max) {
        List<String> out = new ArrayList<>();
        if (points.size() < 2 || max < 1) return out;
        if (length(points) < MIN_LENGTH_ROWS * row) return out;
        float[][] user = resample(points, SAMPLES);
        float[] start = user[0], end = user[SAMPLES - 1];
        float edge = EDGE_ROWS * row;

        List<Entry> found = new ArrayList<>();
        List<Float> costs = new ArrayList<>();
        for (Entry entry : dictionary.entries) {
            String path = collapse(entry.letters);
            float[] first = centers.get(path.charAt(0)), last = centers.get(path.charAt(path.length() - 1));
            if (first == null || last == null) continue;
            if (distance(start, first) > edge || distance(end, last) > edge) continue;
            List<float[]> line = new ArrayList<>();
            boolean complete = true;
            for (int i = 0; i < path.length(); i++) {
                float[] c = centers.get(path.charAt(i));
                if (c == null) {
                    complete = false;
                    break;
                }
                line.add(c);
            }
            if (!complete) continue;
            float[][] ideal = resample(line, SAMPLES);
            float sum = 0;
            for (int i = 0; i < SAMPLES; i++) sum += distance(user[i], ideal[i]);
            float cost = sum / SAMPLES / row + RARITY_WEIGHT * (float) Math.log(1 + entry.rank / 100.0);
            insert(found, costs, entry, cost, max);
        }
        for (Entry entry : found) out.add(entry.typed);
        return out;
    }

    /** Keeps {@code found} sorted by cost, at most {@code max} long. */
    private static void insert(List<Entry> found, List<Float> costs, Entry entry, float cost, int max) {
        int at = costs.size();
        while (at > 0 && costs.get(at - 1) > cost) at--;
        if (at >= max) return;
        found.add(at, entry);
        costs.add(at, cost);
        if (found.size() > max) {
            found.remove(max);
            costs.remove(max);
        }
    }

    static float length(List<float[]> points) {
        float total = 0;
        for (int i = 1; i < points.size(); i++) total += distance(points.get(i - 1), points.get(i));
        return total;
    }

    static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** {@code n} points spaced evenly along the path, the first and last being its ends. */
    static float[][] resample(List<float[]> points, int n) {
        float[][] out = new float[n][];
        float total = length(points);
        if (total == 0) {
            for (int i = 0; i < n; i++) out[i] = points.get(0).clone();
            return out;
        }
        float step = total / (n - 1);
        out[0] = points.get(0).clone();
        int seg = 1;
        float done = 0;
        for (int i = 1; i < n - 1; i++) {
            float target = i * step;
            while (seg < points.size() - 1 && done + distance(points.get(seg - 1), points.get(seg)) < target) {
                done += distance(points.get(seg - 1), points.get(seg));
                seg++;
            }
            float[] a = points.get(seg - 1), b = points.get(seg);
            float len = distance(a, b);
            float t = len == 0 ? 0 : Math.max(0, Math.min(1, (target - done) / len));
            out[i] = new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
        }
        out[n - 1] = points.get(points.size() - 1).clone();
        return out;
    }
}
