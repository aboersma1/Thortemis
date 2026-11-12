package com.limelight.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionPadSettingsTest {
    @Test
    public void nextSlotSkipsEmptySlotsAndWrapsRound() {
        boolean[] used = {false, true, false, true};
        assertEquals(1, ActionPadSettings.nextSlot(0, used));
        assertEquals(3, ActionPadSettings.nextSlot(1, used));
        assertEquals(1, ActionPadSettings.nextSlot(3, used));
    }

    @Test
    public void nextSlotStaysOnTheOnlyUsedSlot() {
        assertEquals(2, ActionPadSettings.nextSlot(2, new boolean[]{false, false, true, false}));
    }

    @Test
    public void nextSlotIsZeroWhenNothingIsSaved() {
        assertEquals(0, ActionPadSettings.nextSlot(1, new boolean[4]));
    }

    @Test
    public void fadeChoicesMatchTheSettingsScreen() throws Exception {
        String xml = new String(Files.readAllBytes(new File("src/main/res/values/action_pad_arrays.xml").toPath()), "UTF-8");
        Matcher block = Pattern.compile("<string-array name=\"action_pad_dim_values\">(.*?)</string-array>", Pattern.DOTALL).matcher(xml);
        org.junit.Assert.assertTrue(block.find());
        Matcher item = Pattern.compile("<item>(.*?)</item>").matcher(block.group(1));
        List<Integer> values = new ArrayList<>();
        while (item.find()) values.add(Integer.parseInt(item.group(1)));
        List<Integer> expected = new ArrayList<>();
        for (int seconds : ActionPadSettings.DIM_SECONDS) expected.add(seconds);
        assertEquals(expected, values);
    }
}
