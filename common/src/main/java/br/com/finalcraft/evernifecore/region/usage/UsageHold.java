package br.com.finalcraft.evernifecore.region.usage;

import br.com.finalcraft.evernifecore.region.flag.EventSource;

import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps an {@link EventSource} needed until released - see {@link EventSourceUsage#acquire}. */
public final class UsageHold {

    private final EventSourceUsage usage;
    private final EventSource source;
    private final AtomicBoolean released = new AtomicBoolean();

    UsageHold(EventSourceUsage usage, EventSource source) {
        this.usage = usage;
        this.source = source;
    }

    public EventSource getSource() {
        return source;
    }

    /** Lets go; a second call does nothing. */
    public void release() {
        if (released.compareAndSet(false, true)) {
            usage.release(source);
        }
    }
}
