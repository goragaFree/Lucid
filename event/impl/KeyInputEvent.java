package dev.lucid.event.impl;

import dev.lucid.event.CancellableEvent;

/** Raw GLFW action (press/repeat/release), before vanilla and module binds. */
public final class KeyInputEvent extends CancellableEvent {
    private final long window;
    private final int key;
    private final int action;

    public KeyInputEvent(long window, int key, int action) {
        this.window = window;
        this.key = key;
        this.action = action;
    }

    public long getWindow() { return window; }
    public int getKey() { return key; }
    public int getAction() { return action; }
}
