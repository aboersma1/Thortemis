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
}
