package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PadSwipeTest {
    private static final float W = 1200, H = 600;
    private static final String[] LAYER = PadKeyboardLayouts.layers(PadKeyboardLayouts.LAYERS)[0];
    private static final Map<Character, float[]> CENTERS = PadKeyboardLayouts.letterCenters(LAYER, W, H);
    private static final float ROW = H / LAYER.length;
    private static PadSwipe.Dictionary dictionary;
    private static List<String> common;

    @BeforeClass
    public static void load() throws Exception {
        String main = read("src/main/assets/swipe_words.txt");
        dictionary = PadSwipe.Dictionary.parse(main, read("src/main/assets/swipe_wow.txt"));
        common = Arrays.asList(main.split("\n"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    /** A finger path through the letters of {@code word}, each key hit {@code sloppy} rows off, with 5 touch points per leg. */
    private static List<float[]> swipe(String word, float sloppy, Random random) {
        String letters = PadSwipe.collapse(word.replace("'", ""));
        List<float[]> hits = new ArrayList<>();
        for (char c : letters.toCharArray()) {
            float[] centre = CENTERS.get(c);
            hits.add(new float[]{centre[0] + (float) random.nextGaussian() * sloppy * ROW, centre[1] + (float) random.nextGaussian() * sloppy * ROW});
        }
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < hits.size(); i++) {
            for (int k = 0; k < 5; k++) {
                float t = k / 5f;
                out.add(new float[]{hits.get(i)[0] + (hits.get(i + 1)[0] - hits.get(i)[0]) * t, hits.get(i)[1] + (hits.get(i + 1)[1] - hits.get(i)[1]) * t});
            }
        }
        out.add(hits.get(hits.size() - 1));
        return out;
    }

    private static List<String> decode(List<float[]> path, int max) {
        return PadSwipe.decode(path, CENTERS, ROW, dictionary, max);
    }

    @Test
    public void collapseCutsRunsOfOneLetter() {
        assertEquals("helo", PadSwipe.collapse("hello"));
        assertEquals("to", PadSwipe.collapse("too"));
    }

    @Test
    public void aCleanSwipeFindsItsWord() {
        for (String word : new String[]{"hello", "thanks", "please", "healer", "ready", "wait"}) {
            assertEquals(word, decode(swipe(word, 0f, new Random(1)), 1).get(0));
        }
    }

    @Test
    public void contractionsAreTypedWithTheirApostrophe() {
        assertEquals("don't", decode(swipe("dont", 0f, new Random(1)), 1).get(0));
        assertEquals("i'm", decode(swipe("im", 0f, new Random(1)), 1).get(0));
    }

    @Test
    public void gameWordsAreInTheDictionary() {
        assertEquals("mana", decode(swipe("mana", 0f, new Random(2)), 1).get(0));
        assertEquals("rez", decode(swipe("rez", 0f, new Random(2)), 1).get(0));
        assertEquals("aggro", decode(swipe("aggro", 0f, new Random(2)), 1).get(0));
    }

    @Test
    public void aTapOrWobbleIsNotASwipe() {
        List<float[]> wobble = new ArrayList<>();
        wobble.add(new float[]{100, 100});
        wobble.add(new float[]{110, 105});
        assertTrue(decode(wobble, 3).isEmpty());
        assertTrue(decode(new ArrayList<float[]>(), 3).isEmpty());
    }

    @Test
    public void thePathMustStartAndEndNearTheFirstAndLastLetter() {
        List<float[]> path = swipe("hello", 0f, new Random(1));
        float[] start = path.get(0), end = path.get(path.size() - 1);
        for (String word : decode(path, 5)) {
            String letters = PadSwipe.collapse(word.replace("'", ""));
            assertTrue(word, PadSwipe.distance(start, CENTERS.get(letters.charAt(0))) <= PadSwipe.EDGE_ROWS * ROW);
            assertTrue(word, PadSwipe.distance(end, CENTERS.get(letters.charAt(letters.length() - 1))) <= PadSwipe.EDGE_ROWS * ROW);
        }
    }

    @Test
    public void sloppySwipesOfCommonWordsStillMostlyWork() {
        Random random = new Random(42);
        int tried = 0, top1 = 0, top3 = 0;
        for (int i = 0; i < 2000 && tried < 400; i += 5) {
            String word = common.get(i);
            if (!word.matches("[a-z]{3,}") || PadSwipe.collapse(word).length() < 3) continue;
            tried++;
            List<String> got = decode(swipe(word, 0.22f, random), 3);
            if (!got.isEmpty() && got.get(0).equals(word)) top1++;
            if (got.contains(word)) top3++;
        }
        System.out.println("swipe accuracy: top1 " + top1 + "/" + tried + ", top3 " + top3 + "/" + tried);
        assertTrue("top3 " + top3 + "/" + tried, top3 >= tried * 0.85);
        assertTrue("top1 " + top1 + "/" + tried, top1 >= tried * 0.6);
    }

    @Test
    public void decodingTheWholeDictionaryIsFast() {
        List<float[]> path = swipe("everyone", 0.1f, new Random(3));
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) decode(path, 3);
        long ms = (System.nanoTime() - start) / 5_000_000;
        System.out.println("swipe decode ms: " + ms);
        assertTrue("decode took " + ms + " ms", ms < 150);
    }

    @Test
    public void everyDictionaryWordHasKeysOnTheLetterLayer() {
        for (PadSwipe.Entry entry : dictionary.entries) {
            for (char c : entry.letters.toCharArray()) assertTrue(entry.typed, CENTERS.containsKey(c));
        }
    }

    @Test
    public void completionsStartWithThePrefixCommonestFirst() {
        List<String> words = dictionary.complete("he", 3);
        assertEquals(3, words.size());
        int lastRank = -1;
        for (String word : words) {
            assertTrue(word, word.startsWith("he"));
            int rank = -1;
            for (PadSwipe.Entry entry : dictionary.entries) if (entry.typed.equals(word)) rank = entry.rank;
            assertTrue(word + " out of order", rank >= lastRank);
            lastRank = rank;
        }
    }

    @Test
    public void completionsCanFinishAContraction() {
        assertTrue(dictionary.complete("don'", 3).contains("don't"));
        assertTrue(dictionary.complete("don", 20).contains("don't"));
    }

    @Test
    public void completionsAreEmptyForNothingOrNoMatch() {
        assertTrue(dictionary.complete("", 3).isEmpty());
        assertTrue(dictionary.complete("zzzq", 3).isEmpty());
    }

    @Test
    public void gameWordsCompleteToo() {
        assertTrue(dictionary.complete("agg", 5).contains("aggro"));
    }
}
