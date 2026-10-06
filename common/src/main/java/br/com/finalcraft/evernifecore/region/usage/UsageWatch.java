package br.com.finalcraft.evernifecore.region.usage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/**
 * One plugin following whether an {@link EventSource} is needed - see {@link EventSourceUsage#watch}. It was born
 * thinking the source unneeded, so a source already needed runs {@code onNeeded} at once.
 */
public final class UsageWatch {

    private final EventSourceUsage usage;
    private final ECPluginData owner;
    private final EventSource source;
    private final Runnable onNeeded;
    private final Runnable onIdle;
    private boolean state;
    private volatile boolean active = true;

    UsageWatch(EventSourceUsage usage, ECPluginData owner, EventSource source, Runnable onNeeded, Runnable onIdle) {
        this.usage = usage;
        this.owner = owner;
        this.source = source;
        this.onNeeded = onNeeded;
        this.onIdle = onIdle;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public EventSource getSource() {
        return source;
    }

    /** Whether, as far as this watch's callbacks went, the source is needed. */
    public synchronized boolean isNeeded() {
        return state;
    }

    public boolean isActive() {
        return active;
    }

    /** No callback runs after this. */
    public void stop() {
        active = false;
        usage.forget(this);
    }

    /** Moves to {@code needed}; the callback that transition owes, or null when there was no transition. */
    Runnable become(boolean needed) {
        synchronized (this) {
            if (!active || state == needed) {
                return null;
            }
            state = needed;
            return needed ? onNeeded : onIdle;
        }
    }
}
