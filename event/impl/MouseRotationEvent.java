package dev.lucid.event.impl;

import dev.lucid.event.CancellableEvent;

/**
 * Mouse deltas immediately before LocalPlayer.turn, AFTER vanilla sensitivity,
 * smoothing and inversion. These are turn-input units, NOT degrees or absolute
 * yaw/pitch. Vanilla still applies its own scaling and pitch clamp.
 * Cancelling skips only this mouse-driven turn, not cursor movement or buttons.
 */
public final class MouseRotationEvent extends CancellableEvent {
    private double cursorDeltaX;
    private double cursorDeltaY;

    public MouseRotationEvent(double cursorDeltaX, double cursorDeltaY) {
        setCursorDeltaX(cursorDeltaX);
        setCursorDeltaY(cursorDeltaY);
    }

    public double getCursorDeltaX() { return cursorDeltaX; }
    public double getCursorDeltaY() { return cursorDeltaY; }

    public void setCursorDeltaX(double cursorDeltaX) {
        if (!Double.isFinite(cursorDeltaX)) {
            throw new IllegalArgumentException("cursorDeltaX must be finite");
        }
        this.cursorDeltaX = cursorDeltaX;
    }

    public void setCursorDeltaY(double cursorDeltaY) {
        if (!Double.isFinite(cursorDeltaY)) {
            throw new IllegalArgumentException("cursorDeltaY must be finite");
        }
        this.cursorDeltaY = cursorDeltaY;
    }
}
