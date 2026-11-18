package com.limelight.ui;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The swipe typing word list, read from the app's assets once and kept. Reading takes a moment, so it is done on
 * a background thread; until it is done {@link #get} is null and swiping does nothing (taps still type).
 */
final class PadSwipeDictionary {
    private static volatile PadSwipe.Dictionary loaded;
    private static boolean loading;

    private PadSwipeDictionary() {}

    /** The word list, or null while it is still being read (or if it could not be read). */
    static PadSwipe.Dictionary get() {
        return loaded;
    }

    /** Starts reading the word list, if that has not been done. */
    static synchronized void load(Context context) {
        if (loaded != null || loading) return;
        loading = true;
        final AssetManager assets = context.getApplicationContext().getAssets();
        new Thread(() -> {
            try {
                loaded = PadSwipe.Dictionary.parse(read(assets, "swipe_words.txt"), read(assets, "swipe_wow.txt"));
            } catch (IOException e) {
                // Swiping stays off; the keyboard still types.
            } finally {
                synchronized (PadSwipeDictionary.class) {
                    loading = false;
                }
            }
        }, "swipe-dictionary").start();
    }

    private static String read(AssetManager assets, String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
