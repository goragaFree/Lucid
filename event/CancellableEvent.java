package dev.lucid.event;

/** Cancellation skips ordinary listeners and, for input, the vanilla handler. */
public abstract class CancellableEvent extends Event {
    private boolean cancelled;

    public final boolean isCancelled() { return cancelled; }
    public final void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public final void cancel() { cancelled = true; }
}
