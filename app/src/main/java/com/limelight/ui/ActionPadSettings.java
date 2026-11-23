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
    /** Which tabs the saved tab list knew about. 2 added Replies, which a list saved before it should still show. */
    private static final String KEY_TABS_VERSION = "action_pad_tabs_version";
    private static final int TABS_VERSION = 2;
    private static final String KEY_THEME = "list_action_pad_theme";
    private static final String KEY_DIM = "list_action_pad_dim";
    private static final String KEY_SAVER = "list_action_pad_saver";
    private static final String KEY_SIDE_BY_SIDE = "checkbox_action_pad_side_by_side";
    private static final String KEY_SWAP_SIDES = "checkbox_action_pad_swap_sides";
    private static final String KEY_TABS_BOTTOM = "checkbox_action_pad_tabs_bottom";
    private static final String KEY_HAPTICS = "checkbox_action_pad_haptics";
    private static final String KEY_LABEL_SIZE = "list_action_pad_label_size";
    private static final String KEY_TOUCH_SPEED = "list_action_pad_touch_speed";
    private static final String KEY_TAP_CLICK = "checkbox_action_pad_tap_click";
    private static final String KEY_KEYBOARD = "list_action_pad_keyboard_layout";
    private static final String KEY_SWIPE = "checkbox_action_pad_swipe_typing_beta";
    private static final String KEY_SCROLL = "checkbox_action_pad_scroll";
    private static final String KEY_SCROLL_LEFT = "checkbox_action_pad_scroll_left";
    private static final String KEY_SCROLL_INVERT = "checkbox_action_pad_scroll_invert";
    private static final String KEY_SCROLL_SPEED = "list_action_pad_scroll_speed";
    private static final String KEY_EXTRAS = "checkbox_action_pad_extras";
    private static final String KEY_EXTRAS_COUNT = "list_action_pad_extras_count";
    private static final String KEY_CONFIG = "action_pad_config";
    private static final String KEY_PROFILE = "action_pad_profile_";
    private static final String KEY_ACTIVE_PROFILE = "action_pad_active_profile";

    public static final int PROFILE_SLOTS = 3;
    /** The idle times the player can pick, in seconds; 0 means never dim. Matches action_pad_dim_values. */
    public static final int[] DIM_SECONDS = {3, 5, 10, 20, 60, 0};
    /** Idle times for the screen saver, which blacks the pad out completely; 0 means never. */
    public static final int[] SAVER_SECONDS = {0, 60, 120, 180};
    /** The F-keys page is hidden unless the player turns it on. */
    private static final String HIDDEN_BY_DEFAULT = "fkeys";

    /** Ids of the tabs to show. Never empty. */
    public Set<String> shownTabs;
    public ActionPadTheme theme;
    /** Idle time before the pad dims, or 0 for never. */
    public long dimMs;
    /** Idle time before the screen saver blacks the pad out; 0 = never. */
    public long saverMs;
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
    /** The scroll strip beside the touchpad, which side it is on, and which way a downward drag scrolls. */
    /** The id of the keyboard tab's layout, see {@link PadKeyboardLayouts}. */
    public String keyboardLayout;
    /** Swipe typing (beta), off unless the player turns it on in Edit. */
    public boolean swipeTyping;
    public final boolean scrollStrip;
    public final boolean scrollLeft;
    public final boolean scrollInvert;
    /** Finger travel, in dp, for one wheel notch. */
    public final float scrollDp;
    public final boolean extrasOn;
    public final int extrasCount;
    /** The player's button changes. The pad edits this and saves it with {@link #saveConfig}. */
    public final PadConfig config;

    private final SharedPreferences prefs;

    private ActionPadSettings(SharedPreferences p) {
        prefs = p;
        Set<String> tabs = new LinkedHashSet<>(p.getStringSet(KEY_TABS, defaultTabs()));
        if (p.contains(KEY_TABS) && p.getInt(KEY_TABS_VERSION, 1) < TABS_VERSION) {
            tabs.add(ActionPadKeyMap.REPLIES_TAB_ID);
            p.edit().putStringSet(KEY_TABS, new LinkedHashSet<>(tabs)).putInt(KEY_TABS_VERSION, TABS_VERSION).apply();
        }
        tabs.retainAll(ActionPadKeyMap.tabIds());
        if (tabs.isEmpty()) tabs.addAll(ActionPadKeyMap.tabIds());
        shownTabs = tabs;
        theme = ActionPadTheme.byId(p.getString(KEY_THEME, ActionPadTheme.DEFAULT_ID));
        dimMs = number(p, KEY_DIM, 10) * 1000L;
        saverMs = number(p, KEY_SAVER, 0) * 1000L;
        sideBySide = p.getBoolean(KEY_SIDE_BY_SIDE, false);
        swapSides = p.getBoolean(KEY_SWAP_SIDES, false);
        tabsBottom = p.getBoolean(KEY_TABS_BOTTOM, false);
        haptics = p.getBoolean(KEY_HAPTICS, true);
        labelScale = number(p, KEY_LABEL_SIZE, 100) / 100f;
        touchSpeed = number(p, KEY_TOUCH_SPEED, 22) / 10f;
        tapToClick = p.getBoolean(KEY_TAP_CLICK, true);
        keyboardLayout = p.getString(KEY_KEYBOARD, PadKeyboardLayouts.DEFAULT);
        swipeTyping = p.getBoolean(KEY_SWIPE, false);
        scrollStrip = p.getBoolean(KEY_SCROLL, true);
        scrollLeft = p.getBoolean(KEY_SCROLL_LEFT, false);
        scrollInvert = p.getBoolean(KEY_SCROLL_INVERT, false);
        scrollDp = 100f / Math.max(1, Math.min(number(p, KEY_SCROLL_SPEED, 5), 10));
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

    public void saveTheme(ActionPadTheme chosen) {
        theme = chosen;
        prefs.edit().putString(KEY_THEME, chosen.id).apply();
    }

    public void saveSwipeTyping(boolean on) {
        swipeTyping = on;
        prefs.edit().putBoolean(KEY_SWIPE, on).apply();
    }

    public void saveKeyboardLayout(String id) {
        keyboardLayout = id;
        prefs.edit().putString(KEY_KEYBOARD, id).apply();
    }

    /** Turns one tab on or off. The last tab on stays on. */
    public void toggleTab(String id) {
        shownTabs = ActionPadKeyMap.toggledTabs(shownTabs, id);
        prefs.edit().putStringSet(KEY_TABS, new LinkedHashSet<>(shownTabs)).putInt(KEY_TABS_VERSION, TABS_VERSION).apply();
    }

    public void saveDimSeconds(int seconds) {
        dimMs = seconds * 1000L;
        prefs.edit().putString(KEY_DIM, String.valueOf(seconds)).apply();
    }

    public void saveSaverSeconds(int seconds) {
        saverMs = seconds * 1000L;
        prefs.edit().putString(KEY_SAVER, String.valueOf(seconds)).apply();
    }

    public void saveConfig() {
        prefs.edit().putString(KEY_CONFIG, config.serialize()).apply();
    }

    public boolean hasProfile(int slot) {
        return prefs.contains(KEY_PROFILE + slot);
    }

    public void saveProfile(int slot) {
        prefs.edit().putString(KEY_PROFILE + slot, config.serialize()).putInt(KEY_ACTIVE_PROFILE, slot).apply();
    }

    /** The profile last saved or loaded, or 0 if none. */
    public int activeProfile() {
        return prefs.getInt(KEY_ACTIVE_PROFILE, 0);
    }

    /** True when at least one profile has been saved. */
    public boolean hasAnyProfile() {
        return nextSlot(0, savedSlots()) != 0;
    }

    /** Loads the next saved profile after the active one (wrapping round), and returns its slot, or 0 if none. */
    public int loadNextProfile() {
        int slot = nextSlot(activeProfile(), savedSlots());
        if (slot != 0) loadProfile(slot);
        return slot;
    }

    private boolean[] savedSlots() {
        boolean[] used = new boolean[PROFILE_SLOTS + 1];
        for (int i = 1; i <= PROFILE_SLOTS; i++) used[i] = hasProfile(i);
        return used;
    }

    /** The first used slot after {@code active}, wrapping round ({@code used[0]} is ignored); 0 if none is used. */
    static int nextSlot(int active, boolean[] used) {
        int slots = used.length - 1;
        for (int step = 1; step <= slots; step++) {
            int slot = (active + step - 1) % slots + 1;
            if (used[slot]) return slot;
        }
        return 0;
    }

    /** Replaces the current changes with the ones saved in {@code slot} (and saves them). */
    public void loadProfile(int slot) {
        PadConfig saved = PadConfig.parse(prefs.getString(KEY_PROFILE + slot, ""));
        config.clear();
        config.replaceWith(saved);
        prefs.edit().putInt(KEY_ACTIVE_PROFILE, slot).apply();
        saveConfig();
    }
}
