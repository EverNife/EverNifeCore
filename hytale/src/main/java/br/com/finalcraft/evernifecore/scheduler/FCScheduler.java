package br.com.finalcraft.evernifecore.scheduler;

import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.*;

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

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the World Thread
    // -----------------------------------------------------------------------------------------------------------------

    public static void runSync(World world, Runnable runnable){
        world.execute(runnable);
    }

    public static void scheduleSync(World world, Runnable runnable, long delayMillis){
        scheduler.schedule(() -> {
            world.execute(runnable);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void scheduleSyncInTicks(World world, Runnable runnable, long delayTicks){
        FCScheduler.runAsync(() -> {
            try {
                Thread.sleep(delayTicks * 50); //TODO Make this respect ticks rather than just wait some random calculated value
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            CompletableFuture.runAsync(() -> {
                runnable.run();
            }, world);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the Main Thread and be Returned to the Parallel Thread
    // -----------------------------------------------------------------------------------------------------------------

    public static class SynchronizedAction {

        public static <T> T runAndGet(World world, Callable<T> callable){
            if (world.isInThread()) {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                FutureTask<T> futureTask = new FutureTask(callable);
                CompletableFuture.runAsync(() -> {
                    futureTask.run();
                }, world);
                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public static <T> T scheduleAndGet(World world, Callable<T> callable, int delayTicks){
            if (world.isInThread()) {
                throw new RejectedExecutionException("You cannot schedule a SynchronizedAction on the World's [" + world.getName() + "] Own Thread!");
            }

            try {
                FutureTask<T> futureTask = new FutureTask(callable);

                FCScheduler.runAsync(() -> {
                    try {
                        Thread.sleep(delayTicks * 50); //TODO Make this respect ticks rather than just wait some random calculated value
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    CompletableFuture.runAsync(() -> {
                        futureTask.run();
                    }, world);
                });

                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public static void run(World world, Runnable runnable) {
            if (world.isInThread()) {
                runnable.run();
                return;
            }

            CompletableFuture.runAsync(() -> {
                runnable.run();
            }, world).join();
        }

        public static void schedule(World world, Runnable runnable, int delayTicks) {
            scheduleAndGet(
                    world,
                    () -> {
                        runnable.run();
                        return null;
                    },
                    delayTicks
            );
        }

    }
}
