package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import jakarta.annotation.Nullable;

/**
 * Internal: the one place region code schedules work off the caller's thread - writes, load retries, the sweeper,
 * the session tick, the idle delay of event sources. A test swaps the backend to run that work when it says so.
 */
public final class RegionScheduler {

    public interface Backend {
        void scheduleAsync(Runnable task, long delayMillis);
    }

    private static final Backend DEFAULT = FCScheduler::scheduleAsync;

    private static volatile Backend current = DEFAULT;

    private RegionScheduler() {
    }

    public static void scheduleAsync(Runnable task, long delayMillis) {
        current.scheduleAsync(task, delayMillis);
    }

    /** Replaces the backend; {@code null} goes back to the {@code FCScheduler} one. */
    public static void set(@Nullable Backend backend) {
        current = backend == null ? DEFAULT : backend;
    }
}
