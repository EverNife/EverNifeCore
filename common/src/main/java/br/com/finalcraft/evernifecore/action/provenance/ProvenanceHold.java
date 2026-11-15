package br.com.finalcraft.evernifecore.action.provenance;

import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the provenance record recording until closed, or until its plugin leaves; closing twice is closing once. */
public final class ProvenanceHold implements AutoCloseable {

    private final ProvenanceTracker tracker;
    private final AtomicBoolean open = new AtomicBoolean(true);

    ProvenanceHold(ProvenanceTracker tracker) {
        this.tracker = tracker;
    }

    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            tracker.release(this);
        }
    }
}
