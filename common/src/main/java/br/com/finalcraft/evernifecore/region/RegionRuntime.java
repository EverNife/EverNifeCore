package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;

/**
 * Internal: the process-wide pieces of the region system that hang on the global event bus - the event-source count,
 * the movement tracker and the sweeper - and the one way to stop them all. Each starts again on its next use.
 */
public final class RegionRuntime {

    private RegionRuntime() {
    }

    /** Stops the global event-source count and movement tracker and forgets the sweeper's running state. */
    public static void shutdown() {
        EventSourceUsage.shutdown();
        RegionMovementTracker.shutdown();
        RegionSweeper.reset();
    }
}
