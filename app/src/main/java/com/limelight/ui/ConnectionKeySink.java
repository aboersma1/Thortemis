package com.limelight.ui;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/** Sends the action pad's keys and mouse moves through the stream, if there is one right now. */
public final class ConnectionKeySink implements ExternalActionPadView.KeySink {
    /** The current connection, or null when no stream is running. */
    public interface Source {
        NvConnection get();
    }

    private final Source source;

    public ConnectionKeySink(Source source) {
        this.source = source;
    }

    @Override
    public void sendKey(short vk, boolean down, byte modifiers) {
        NvConnection conn = source.get();
        if (conn != null) {
            conn.sendKeyboardInput(vk, down ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP, modifiers, (byte) 0);
        }
    }

    @Override
    public void sendMouseMove(short dx, short dy) {
        NvConnection conn = source.get();
        if (conn != null) {
            conn.sendMouseMove(dx, dy);
        }
    }

    @Override
    public void sendMouseButton(byte button, boolean down) {
        NvConnection conn = source.get();
        if (conn != null) {
            if (down) {
                conn.sendMouseButtonDown(button);
            } else {
                conn.sendMouseButtonUp(button);
            }
        }
    }
}
