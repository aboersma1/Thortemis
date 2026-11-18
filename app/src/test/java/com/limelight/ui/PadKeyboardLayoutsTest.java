package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PadKeyboardLayoutsTest {
    private static final List<String> MODS_AND_LAYER_KEYS = Arrays.asList("Shift", "Ctrl", "Alt", "_",
            PadKeyboardLayouts.SYMBOLS_KEY, PadKeyboardLayouts.LETTERS_KEY);

    private static boolean known(String name) {
        if (MODS_AND_LAYER_KEYS.contains(name) || PadKeyboardLayouts.namedKeyVk(name) >= 0) return true;
        if (name.length() != 1) return false;
        try {
            ActionPadKeyMap.typingPair(name.charAt(0));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Test
    public void everyKeyInEveryLayoutIsKnown() {
        for (String id : PadKeyboardLayouts.IDS) {
            for (String[] layer : PadKeyboardLayouts.layers(id)) {
                for (String row : layer) {
                    for (String token : row.split(" ")) {
                        assertTrue(id + ": " + token, known(PadKeyboardLayouts.keyName(token)));
                    }
                }
            }
        }
    }

    @Test
    public void rowsOfTheTenColumnLayoutsFillTheSameWidth() {
        for (String id : new String[]{PadKeyboardLayouts.GRID, PadKeyboardLayouts.LAYERS}) {
            for (String[] layer : PadKeyboardLayouts.layers(id)) {
                for (String row : layer) {
                    float sum = 0;
                    for (String token : row.split(" ")) sum += PadKeyboardLayouts.weight(token);
                    assertEquals(id + ": " + row, 10f, sum, 0.01f);
                }
            }
        }
    }

    @Test
    public void everyLetterAndDigitCanBeTypedInEveryLayout() {
        for (String id : PadKeyboardLayouts.IDS) {
            StringBuilder all = new StringBuilder();
            for (String[] layer : PadKeyboardLayouts.layers(id)) {
                for (String row : layer) for (String t : row.split(" ")) all.append(PadKeyboardLayouts.keyName(t)).append(' ');
            }
            String keys = " " + all;
            for (char c : "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()) {
                assertTrue(id + " lacks " + c, keys.contains(" " + c + " "));
            }
        }
    }

    @Test
    public void everyLayoutCanReachEnterBackspaceSpaceAndArrows() {
        for (String id : PadKeyboardLayouts.IDS) {
            String keys = "";
            for (String[] layer : PadKeyboardLayouts.layers(id)) for (String row : layer) keys += " " + row;
            for (String need : new String[]{"Enter", "Bksp", "Space", "Esc", "Tab", "←", "↑", "↓", "→"}) {
                assertTrue(id + " lacks " + need, (" " + keys).replaceAll(":[0-9.]+", "").contains(" " + need));
            }
        }
    }

    @Test
    public void layerSwitchKeysExistOnlyWhereTheyHaveAWhereTo() {
        String[][] layers = PadKeyboardLayouts.layers(PadKeyboardLayouts.LAYERS);
        assertEquals(2, layers.length);
        assertTrue(String.join(" ", layers[0]).contains(PadKeyboardLayouts.SYMBOLS_KEY));
        assertTrue(String.join(" ", layers[1]).contains(PadKeyboardLayouts.LETTERS_KEY));
        assertEquals(1, PadKeyboardLayouts.layers(PadKeyboardLayouts.CLASSIC).length);
        assertEquals(1, PadKeyboardLayouts.layers(PadKeyboardLayouts.GRID).length);
    }

    @Test
    public void layoutChoicesMatchTheSettingsScreen() throws Exception {
        String xml = new String(Files.readAllBytes(new File("src/main/res/values/action_pad_arrays.xml").toPath()), "UTF-8");
        for (String[] pair : new String[][]{{"action_pad_keyboard_values", "ids"}, {"action_pad_keyboard_names", "names"}}) {
            Matcher block = Pattern.compile("<string-array name=\"" + pair[0] + "\">(.*?)</string-array>", Pattern.DOTALL).matcher(xml);
            assertTrue(block.find());
            Matcher item = Pattern.compile("<item>(.*?)</item>").matcher(block.group(1));
            List<String> items = new ArrayList<>();
            while (item.find()) items.add(item.group(1));
            assertEquals(Arrays.asList(pair[1].equals("ids") ? PadKeyboardLayouts.IDS : PadKeyboardLayouts.NAMES), items);
        }
    }

    @Test
    public void slashIsOnTheFirstLayerOfEveryLayout() {
        for (String id : PadKeyboardLayouts.IDS) {
            boolean found = false;
            for (String row : PadKeyboardLayouts.layers(id)[0]) {
                for (String token : row.split(" ")) if (PadKeyboardLayouts.keyName(token).equals("/")) found = true;
            }
            org.junit.Assert.assertTrue(id + " has no / without switching layers", found);
        }
    }

    /** The tokens of one row of a layer. */
    private static String[] row(String id, int layer, int row) {
        return PadKeyboardLayouts.layers(id)[layer][row].split(" ");
    }

    @Test
    public void shiftStaysOnTheLeftOfRowFourOnEveryLayer() {
        String[][] layers = PadKeyboardLayouts.layers(PadKeyboardLayouts.LAYERS);
        for (int layer = 0; layer < layers.length; layer++) {
            String[] tokens = row(PadKeyboardLayouts.LAYERS, layer, 3);
            assertEquals("layer " + layer, "Shift", PadKeyboardLayouts.keyName(tokens[0]));
            assertEquals("layer " + layer, 1.5f, PadKeyboardLayouts.weight(tokens[0]), 0.001f);
        }
    }

    @Test
    public void theFrameKeysDoNotMoveBetweenLayers() {
        String id = PadKeyboardLayouts.LAYERS;
        for (int layer = 0; layer < 2; layer++) {
            String[] four = row(id, layer, 3), five = row(id, layer, 4);
            assertEquals("Bksp", PadKeyboardLayouts.keyName(four[four.length - 1]));
            assertEquals(1.5f, PadKeyboardLayouts.weight(four[four.length - 1]), 0.001f);
            assertEquals("Enter", PadKeyboardLayouts.keyName(five[five.length - 1]));
            assertEquals(1.5f, PadKeyboardLayouts.weight(five[five.length - 1]), 0.001f);
            assertEquals(1.5f, PadKeyboardLayouts.weight(five[0]), 0.001f);
        }
        assertEquals(PadKeyboardLayouts.SYMBOLS_KEY, PadKeyboardLayouts.keyName(row(id, 0, 4)[0]));
        assertEquals(PadKeyboardLayouts.LETTERS_KEY, PadKeyboardLayouts.keyName(row(id, 1, 4)[0]));
    }

    @Test
    public void shiftIsOnTheLeftInEveryLayout() {
        for (String id : PadKeyboardLayouts.IDS) {
            boolean found = false;
            for (String[] layer : PadKeyboardLayouts.layers(id)) {
                for (String r : layer) {
                    String[] tokens = r.split(" ");
                    for (int i = 0; i < tokens.length; i++) {
                        if (PadKeyboardLayouts.keyName(tokens[i]).equals("Shift")) {
                            found = true;
                            assertEquals(id + ": " + r, 0, i);
                        }
                    }
                }
            }
            assertTrue(id + " has no Shift", found);
        }
    }

    @Test
    public void chatPunctuationIsOnTheLetterLayer() {
        String first = String.join(" ", PadKeyboardLayouts.layers(PadKeyboardLayouts.LAYERS)[0]);
        for (String key : new String[]{"'", ",", ".", "/", "Esc", "Space", "Enter", "Bksp"}) {
            assertTrue(key, (" " + first.replaceAll(":[0-9.]+", "") + " ").contains(" " + key + " "));
        }
    }
}
