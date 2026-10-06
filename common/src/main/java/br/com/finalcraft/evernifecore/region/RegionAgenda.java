package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import jakarta.annotation.Nullable;

/**
 * The one place region code schedules work off the caller's thread: flushes, load retries, the sweeper. A test
 * swaps it to run that work when it says so.
 */
final class RegionAgenda {

    interface Scheduler {
        void scheduleAsync(Runnable task, long delayMillis);
    }

    private static final Scheduler DEFAULT = FCScheduler::scheduleAsync;

    private static volatile Scheduler current = DEFAULT;

    private RegionAgenda() {
    }

    static void scheduleAsync(Runnable task, long delayMillis) {
        current.scheduleAsync(task, delayMillis);
    }

    /** Replaces the scheduler; {@code null} goes back to the {@code FCScheduler} one. */
    static void set(@Nullable Scheduler scheduler) {
        current = scheduler == null ? DEFAULT : scheduler;
    }
}
