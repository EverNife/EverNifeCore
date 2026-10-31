package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.hytale.scheduler.HyFCScheduler;
import br.com.finalcraft.evernifecore.screen.Cancellable;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The thread every screen on Hytale lives on. Hytale has no main thread - each world ticks on its own - so screens get
 * one of their own, serial, and a tick is one tick of the default world. Pages hop from here to the viewer's world
 * thread to reach the server, and back here with what the player did.
 */
final class HyScreenScheduler implements ScreenScheduler {

    static final HyScreenScheduler INSTANCE = new HyScreenScheduler();

    private volatile Thread thread;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread created = new Thread(task, "EverNifeCore Screens");
        created.setDaemon(true);
        thread = created;
        return created;
    });

    private HyScreenScheduler() {

    }

    @Override
    public Cancellable later(long ticks, Runnable task) {
        ScheduledFuture<?> handle = executor.schedule(guarded(task), millisOf(ticks), TimeUnit.MILLISECONDS);
        return () -> handle.cancel(false);
    }

    @Override
    public Cancellable repeat(long ticks, Runnable task) {
        long period = Math.max(1L, millisOf(ticks));
        ScheduledFuture<?> handle = executor.scheduleAtFixedRate(guarded(task), period, period, TimeUnit.MILLISECONDS);
        return () -> handle.cancel(false);
    }

    @Override
    public boolean isRenderThread() {
        return Thread.currentThread() == thread;
    }

    private static long millisOf(long ticks) {
        return HyFCScheduler.ticksToMillis(Math.max(0L, ticks), HyFCScheduler.INSTANCE.getMainWorldTps());
    }

    //a throw would cancel a repeating task for good and kill a one-shot silently
    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable broken) {
                EverNifeCore.getLog().severe("A screen task failed on the Hytale screen thread", broken);
            }
        };
    }

}
