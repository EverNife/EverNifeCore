package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.hytale.scheduler.HyFCScheduler;
import br.com.finalcraft.evernifecore.minecraft.scheduler.McFCScheduler;
import br.com.finalcraft.everylibs.executors.scheduler.VirtualThreadedScheduledExecutor;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The one place core code leaves the caller's thread: a shared executor for parallel work, the delayed and repeated paths
 * ({@link #scheduleAsync}, {@link #repeatAsync}) that a test can take over through {@link #set(Backend)}, and the hop onto the platform's
 * main thread ({@link #onMainThread}, {@link #onMainThreadLater}). Any thread.
 */
public final class ECScheduler {

    /** Replaces only the delayed path ({@link #scheduleAsync}, and {@link #repeatAsync} over it); a test runs that agenda when it says so. */
    public interface Backend {
        void scheduleAsync(Runnable task, long delayMillis);
    }

    /**
     * The platform's main thread, installed by the platform module through {@link #setMainThread}. Bukkit: the server
     * thread. Hytale, which ticks each world on its own thread: the core's own main thread, one for the server, tasks
     * one at a time in the order asked, from the start phase on. Either way a task that blocks holds the queue.
     */
    public interface MainThread {
        /** Runs the task on the main thread: in place when the caller already is it, queued otherwise. */
        <T> CompletableFuture<T> onMainThread(Supplier<T> task);

        /** Runs the task on the main thread, never in place. */
        <T> CompletableFuture<T> onMainThreadLater(Supplier<T> task);
    }

    /**
     * A Scheduler that uses Virtual Threads to execute the tasks.
     * This allows for non-blocking scheduling of tasks that may perform blocking operations.
     * Ideal for workloads where tasks may call Thread.sleep(), wait on I/O, or perform blocking operations.
     * <p>
     * This class requires Java 21 or higher to utilize virtual threads.
     * On lower Java versions, it falls back to a fixed thread pool of System Core Count.
     *
     * If you are on a Java version lower than 21, this will use a normal Thread Pool instead of Virtual Threads,
     * bounded to the number of CPU cores available on the system.
     *
     * See: {@link VirtualThreadedScheduledExecutor}
     */
    private static final VirtualThreadedScheduledExecutor scheduler = new VirtualThreadedScheduledExecutor("fcscheduler");

    private static final Backend DEFAULT = ECScheduler::scheduleOnExecutor;

    private static volatile Backend backend = DEFAULT;

    private static volatile @Nullable MainThread mainThread;

    private ECScheduler() {
    }

    public static VirtualThreadedScheduledExecutor getScheduler() {
        return scheduler;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on a Parallel Thread
    // -----------------------------------------------------------------------------------------------------------------

    public static void runAsync(Runnable runnable){
        scheduler.execute(() -> {
            try {
                runnable.run();
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        });
    }

    /** Runs the task after the delay, through the installed {@link Backend} (the executor unless a test set one). */
    public static void scheduleAsync(Runnable runnable, long delayMillis){
        backend.scheduleAsync(runnable, delayMillis);
    }

    /**
     * Runs the task after {@code delayMillis}, then again {@code periodMillis} after each run ends, until the returned
     * handle is cancelled. A run that throws is printed and the next one still comes. Each run goes through the installed
     * {@link Backend}, so a test clock drives it like any {@link #scheduleAsync}. Any thread.
     */
    public static RepeatingTask repeatAsync(Runnable runnable, long delayMillis, long periodMillis) {
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("A repeating task needs a period above zero, not " + periodMillis
                    + " ms: for a single run use scheduleAsync.");
        }
        RepeatingTask handle = new RepeatingTask();
        Runnable[] run = new Runnable[1];
        run[0] = () -> {
            if (handle.isCancelled()) {
                return;
            }
            try {
                runnable.run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            if (!handle.isCancelled()) {
                backend.scheduleAsync(run[0], periodMillis);
            }
        };
        backend.scheduleAsync(run[0], delayMillis);
        return handle;
    }

    /** The handle of a {@link #repeatAsync} task. */
    public static final class RepeatingTask {

        private volatile boolean cancelled;

        private RepeatingTask() {
        }

        /** Stops the task: no run starts after this call; one already running finishes. Idempotent. */
        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** Replaces the backend of {@link #scheduleAsync}; {@code null} goes back to the executor. Tests only. */
    public static void set(@Nullable Backend newBackend) {
        backend = newBackend == null ? DEFAULT : newBackend;
    }

    private static void scheduleOnExecutor(Runnable runnable, long delayMillis){
        scheduler.schedule(() -> {
            try {
                runnable.run();
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs the task on a parallel (virtual) thread and returns a future that completes when it
     * finishes - or completes exceptionally if it throws. The throwable is also printed, so a failure
     * stays visible even when the caller ignores the future.
     */
    public static CompletableFuture<Void> runAsyncFuture(Runnable runnable){
        return supplyAsyncFuture(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Runs the supplier on a parallel (virtual) thread and returns a future that completes with its
     * result - or completes exceptionally if it throws. The throwable is also printed, so a failure
     * stays visible even when the caller ignores the future.
     */
    public static <T> CompletableFuture<T> supplyAsyncFuture(Supplier<T> supplier){
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.execute(() -> {
            try {
                future.complete(supplier.get());
            }catch (Throwable throwable){
                throwable.printStackTrace();
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the Main Thread
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Runs the task on the main thread: in place, before this call returns, when the caller already is it; queued
     * like {@link #onMainThreadLater(Runnable)} otherwise. The future completes with the task's outcome; a throwable
     * is also printed. Any thread.
     *
     * @throws IllegalStateException when no platform installed its main thread
     */
    public static CompletableFuture<Void> onMainThread(Runnable task) {
        return onMainThread(asSupplier(task));
    }

    /** The value-returning form of {@link #onMainThread(Runnable)}: same thread contract. */
    public static <T> CompletableFuture<T> onMainThread(Supplier<T> task) {
        return requireMainThread().onMainThread(task);
    }

    /**
     * Runs the task on the main thread, never in place. Asked during enable, it runs once every plugin has enabled
     * (Bukkit: the first tick; Hytale: the start phase, after every plugin's setup() and the world loads). Asked later,
     * it runs at the next opportunity, which is not a promise of a later tick: on Bukkit a task queued by a running
     * scheduler task may run in that same tick, and on Hytale it runs next on the core's main thread, after the tasks
     * already queued. To pace work one piece per tick use {@code IPlatform.runOnWorldThreadNextTick}. The task holds
     * the main thread until it returns (on Bukkit, the tick), so never join this future from the main thread. Any
     * thread.
     *
     * @throws IllegalStateException when no platform installed its main thread
     */
    public static CompletableFuture<Void> onMainThreadLater(Runnable task) {
        return onMainThreadLater(asSupplier(task));
    }

    /** The value-returning form of {@link #onMainThreadLater(Runnable)}: same thread contract. */
    public static <T> CompletableFuture<T> onMainThreadLater(Supplier<T> task) {
        return requireMainThread().onMainThreadLater(task);
    }

    /** Installs the platform's main thread; each platform calls it before any bootstrap hook. Null removes it. */
    public static void setMainThread(@Nullable MainThread newMainThread) {
        mainThread = newMainThread;
    }

    /** The installed main thread, or {@code null} before a platform installed one. */
    public static @Nullable MainThread getMainThread() {
        return mainThread;
    }

    private static MainThread requireMainThread() {
        MainThread installed = mainThread;
        if (installed == null) {
            throw new IllegalStateException("No platform installed its main thread, so ECScheduler has nowhere to run this"
                    + " task - the platform module calls ECScheduler.setMainThread(...) before any bootstrap hook; a test"
                    + " gets one from Platforms.lenient() or Platforms.strict().mainThreadInline().");
        }
        return installed;
    }

    private static Supplier<Void> asSupplier(Runnable task) {
        return () -> {
            task.run();
            return null;
        };
    }

    public static HyFCScheduler getHytaleScheduler() {
        return HyFCScheduler.INSTANCE;
    }

    public static McFCScheduler getMinecraftScheduler() {
        return McFCScheduler.INSTANCE;
    }

}
