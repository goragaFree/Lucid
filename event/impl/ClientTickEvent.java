package dev.lucid.event.impl;

import dev.lucid.event.Event;

/** START/END fire even in menus; check player/level before accessing the world. */
public final class ClientTickEvent extends Event {
    public enum Phase { START, END }
    private final Phase phase;

    public ClientTickEvent(Phase phase) { this.phase = phase; }
    public Phase getPhase() { return phase; }
}
