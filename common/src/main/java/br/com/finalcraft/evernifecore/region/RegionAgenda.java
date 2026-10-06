package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import jakarta.annotation.Nullable;

/**
 * Internal: the one place region code schedules work off the caller's thread - writes, load retries, the sweeper,
 * the session tick, the idle delay of event sources. A test swaps it to run that work when it says so.
 */
public final class RegionAgenda {

    public interface Scheduler {
        void scheduleAsync(Runnable task, long delayMillis);
    }

    private static final Scheduler DEFAULT = FCScheduler::scheduleAsync;

    private static volatile Scheduler current = DEFAULT;

    private RegionAgenda() {
    }

    public static void scheduleAsync(Runnable task, long delayMillis) {
        current.scheduleAsync(task, delayMillis);
    }

    /** Replaces the scheduler; {@code null} goes back to the {@code FCScheduler} one. */
    public static void set(@Nullable Scheduler scheduler) {
        current = scheduler == null ? DEFAULT : scheduler;
    }
}
