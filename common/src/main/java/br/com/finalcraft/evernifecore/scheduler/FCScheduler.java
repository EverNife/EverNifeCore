package br.com.finalcraft.evernifecore.scheduler;

import br.com.finalcraft.evernifecore.hytale.scheduler.HyFCScheduler;
import br.com.finalcraft.evernifecore.minecraft.scheduler.McFCScheduler;
import br.com.finalcraft.everylibs.executors.scheduler.VirtualThreadedScheduledExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FCScheduler {

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

    public static void scheduleAsync(Runnable runnable, long delayMillis){
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

    public static HyFCScheduler getHytaleScheduler() {
        return HyFCScheduler.INSTANCE;
    }

    public static McFCScheduler getMinecraftScheduler() {
        return McFCScheduler.INSTANCE;
    }

}
