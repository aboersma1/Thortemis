package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.LinkedHashSet;
import java.util.Set;

/** The action pad's user settings, read from the app's default preferences when the pad is built. */
public final class ActionPadSettings {
    public static final String KEY_TABS = "multi_action_pad_tabs";
    public static final String KEY_THEME = "list_action_pad_theme";
    public static final String KEY_DIM = "list_action_pad_dim";

    public static final String DEFAULT_DIM_SECONDS = "10";
    /** The F-keys page is hidden unless the user turns it on. */
    private static final String HIDDEN_BY_DEFAULT = "fkeys";

    /** Ids of the tabs to show. Never empty. */
    public final Set<String> shownTabs;
    public final ActionPadTheme theme;
    /** Idle time before the pad dims, or 0 for never. */
    public final long dimMs;

    private ActionPadSettings(Set<String> shownTabs, ActionPadTheme theme, long dimMs) {
        this.shownTabs = shownTabs;
        this.theme = theme;
        this.dimMs = dimMs;
    }

    public static Set<String> defaultTabs() {
        Set<String> tabs = new LinkedHashSet<>(ActionPadKeyMap.tabIds());
        tabs.remove(HIDDEN_BY_DEFAULT);
        return tabs;
    }

    public static ActionPadSettings load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> tabs = new LinkedHashSet<>(prefs.getStringSet(KEY_TABS, defaultTabs()));
        tabs.retainAll(ActionPadKeyMap.tabIds());
        if (tabs.isEmpty()) tabs.addAll(ActionPadKeyMap.tabIds());
        long dimMs;
        try {
            dimMs = Long.parseLong(prefs.getString(KEY_DIM, DEFAULT_DIM_SECONDS)) * 1000L;
        } catch (NumberFormatException e) {
            dimMs = Long.parseLong(DEFAULT_DIM_SECONDS) * 1000L;
        }
        return new ActionPadSettings(tabs, ActionPadTheme.byId(prefs.getString(KEY_THEME, ActionPadTheme.DEFAULT_ID)), dimMs);
    }
}
