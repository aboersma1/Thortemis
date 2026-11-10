package com.limelight.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.limelight.BuildConfig;
import com.limelight.R;

/** The "What's new" dialog: shown once after each update, and any time from Settings. */
public final class WhatsNew {
    private static final String KEY_SEEN_VERSION = "whats_new_seen_version";

    private WhatsNew() {}

    /** True when the player has not yet seen the notes for {@code current}. */
    static boolean isUnseen(String lastSeen, String current) {
        return !current.equals(lastSeen);
    }

    /** Shows the notes if this version's have not been seen, then remembers that they were. */
    public static void showOnce(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (isUnseen(prefs.getString(KEY_SEEN_VERSION, null), BuildConfig.VERSION_NAME)) {
            prefs.edit().putString(KEY_SEEN_VERSION, BuildConfig.VERSION_NAME).apply();
            show(activity);
        }
    }

    public static void show(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
                .setMessage(R.string.whats_new_body)
                .setPositiveButton(R.string.whats_new_ok, null)
                .show();
    }
}
