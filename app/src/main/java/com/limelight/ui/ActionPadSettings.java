package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The action pad's settings, read from the app's default preferences when the pad is built, and where
 * the player's button changes ({@link PadConfig}) and saved profiles are kept.
 */
public final class ActionPadSettings {
    private static final String KEY_TABS = "multi_action_pad_tabs";
    private static final String KEY_THEME = "list_action_pad_theme";
    private static final String KEY_DIM = "list_action_pad_dim";
    private static final String KEY_SIDE_BY_SIDE = "checkbox_action_pad_side_by_side";
    private static final String KEY_SWAP_SIDES = "checkbox_action_pad_swap_sides";
    private static final String KEY_TABS_BOTTOM = "checkbox_action_pad_tabs_bottom";
    private static final String KEY_HAPTICS = "checkbox_action_pad_haptics";
    private static final String KEY_LABEL_SIZE = "list_action_pad_label_size";
    private static final String KEY_TOUCH_SPEED = "list_action_pad_touch_speed";
    private static final String KEY_TAP_CLICK = "checkbox_action_pad_tap_click";
    private static final String KEY_EXTRAS = "checkbox_action_pad_extras";
    private static final String KEY_EXTRAS_COUNT = "list_action_pad_extras_count";
    private static final String KEY_CONFIG = "action_pad_config";
    private static final String KEY_PROFILE = "action_pad_profile_";

    public static final int PROFILE_SLOTS = 3;
    /** The F-keys page is hidden unless the player turns it on. */
    private static final String HIDDEN_BY_DEFAULT = "fkeys";

    /** Ids of the tabs to show. Never empty. */
    public final Set<String> shownTabs;
    public final ActionPadTheme theme;
    /** Idle time before the pad dims, or 0 for never. */
    public final long dimMs;
    /** Interface, Target, Utility and the other key pages at once, instead of one tab each. */
    public final boolean sideBySide;
    public final boolean swapSides;
    public final boolean tabsBottom;
    public final boolean haptics;
    /** Multiplier for button text. */
    public final float labelScale;
    /** Host pixels per dp of finger travel on the touchpad. */
    public final float touchSpeed;
    public final boolean tapToClick;
    public final boolean extrasOn;
    public final int extrasCount;
    /** The player's button changes. The pad edits this and saves it with {@link #saveConfig}. */
    public final PadConfig config;

    private final SharedPreferences prefs;

    private ActionPadSettings(SharedPreferences p) {
        prefs = p;
        Set<String> tabs = new LinkedHashSet<>(p.getStringSet(KEY_TABS, defaultTabs()));
        tabs.retainAll(ActionPadKeyMap.tabIds());
        if (tabs.isEmpty()) tabs.addAll(ActionPadKeyMap.tabIds());
        shownTabs = tabs;
        theme = ActionPadTheme.byId(p.getString(KEY_THEME, ActionPadTheme.DEFAULT_ID));
        dimMs = number(p, KEY_DIM, 10) * 1000L;
        sideBySide = p.getBoolean(KEY_SIDE_BY_SIDE, false);
        swapSides = p.getBoolean(KEY_SWAP_SIDES, false);
        tabsBottom = p.getBoolean(KEY_TABS_BOTTOM, false);
        haptics = p.getBoolean(KEY_HAPTICS, true);
        labelScale = number(p, KEY_LABEL_SIZE, 100) / 100f;
        touchSpeed = number(p, KEY_TOUCH_SPEED, 22) / 10f;
        tapToClick = p.getBoolean(KEY_TAP_CLICK, true);
        extrasOn = p.getBoolean(KEY_EXTRAS, false);
        extrasCount = Math.max(1, Math.min(number(p, KEY_EXTRAS_COUNT, 5), ActionPadKeyMap.EXTRAS.size()));
        config = PadConfig.parse(p.getString(KEY_CONFIG, ""));
    }

    public static ActionPadSettings load(Context context) {
        return new ActionPadSettings(PreferenceManager.getDefaultSharedPreferences(context));
    }

    /** Every tab but F-Keys. */
    public static Set<String> defaultTabs() {
        Set<String> tabs = new LinkedHashSet<>(ActionPadKeyMap.tabIds());
        tabs.remove(HIDDEN_BY_DEFAULT);
        return tabs;
    }

    private static int number(SharedPreferences p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getString(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // --- Changes and profiles ---

    public void saveConfig() {
        prefs.edit().putString(KEY_CONFIG, config.serialize()).apply();
    }

    public boolean hasProfile(int slot) {
        return prefs.contains(KEY_PROFILE + slot);
    }

    public void saveProfile(int slot) {
        prefs.edit().putString(KEY_PROFILE + slot, config.serialize()).apply();
    }

    /** Replaces the current changes with the ones saved in {@code slot} (and saves them). */
    public void loadProfile(int slot) {
        PadConfig saved = PadConfig.parse(prefs.getString(KEY_PROFILE + slot, ""));
        config.clear();
        config.replaceWith(saved);
        saveConfig();
    }
}
