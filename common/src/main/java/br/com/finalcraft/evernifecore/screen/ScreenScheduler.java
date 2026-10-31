package br.com.finalcraft.evernifecore.screen;

/**
 * Time in ticks on the thread a renderer draws on - the main thread on Bukkit. Every task a view schedules goes through
 * the scheduler of the renderer that opened it, so a pass, a watch and a close all land where writing is allowed.
 */
public interface ScreenScheduler {

    /** Runs {@code task} once, {@code ticks} from now, on the renderer thread. */
    Cancellable later(long ticks, Runnable task);

    /** Runs {@code task} every {@code ticks}, the first time {@code ticks} from now, on the renderer thread. */
    Cancellable repeat(long ticks, Runnable task);

    /** Whether the calling thread is the renderer thread - a write from anywhere else is refused. */
    boolean isRenderThread();

    /** Runs {@code task} on the renderer thread: now when already there, on the next tick otherwise. */
    default void run(Runnable task) {
        if (isRenderThread()) {
            task.run();
        } else {
            later(0L, task);
        }
    }

}
