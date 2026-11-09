package com.limelight.ui;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

/**
 * Shows {@link ExternalActionPadView} full screen on a second display (the Thor's bottom screen) while
 * the stream plays on the main one. The pad sends its keys through the stream, so it needs no focus and
 * no extra activity.
 */
public class ActionPadPresentation extends Presentation {
    private final ExternalActionPadView.KeySink sink;
    private ExternalActionPadView pad;

    public ActionPadPresentation(Context outerContext, Display display, ExternalActionPadView.KeySink sink) {
        super(outerContext, display);
        this.sink = sink;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pad = new ExternalActionPadView(getContext(), sink);
        setContentView(pad);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Lets go of every key and modifier the pad is holding. */
    public void releaseAll() {
        if (pad != null) {
            pad.releaseAll();
        }
    }
}
