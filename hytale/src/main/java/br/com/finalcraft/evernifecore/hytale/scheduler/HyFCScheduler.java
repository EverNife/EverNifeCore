package br.com.finalcraft.evernifecore.hytale.scheduler;

import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.*;

public class HyFCScheduler {

    public static HyFCScheduler INSTANCE = new HyFCScheduler();

    private final SynchronizedAction synchronizedAction = new SynchronizedAction();

    public HyFCScheduler() {

    }

    public SynchronizedAction getSynchronizedAction() {
        return synchronizedAction;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the World Thread
    // -----------------------------------------------------------------------------------------------------------------

    public void runSync(World world, Runnable runnable){
        world.execute(runnable);
    }

    public void scheduleSync(World world, Runnable runnable, long delayMillis){
        FCScheduler.getScheduler().schedule(() -> {
            world.execute(runnable);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void scheduleSyncInTicks(World world, Runnable runnable, long delayTicks){
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

        public <T> T runAndGet(World world, Callable<T> callable){
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

        public <T> T scheduleAndGet(World world, Callable<T> callable, int delayTicks){
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

        public void run(World world, Runnable runnable) {
            if (world.isInThread()) {
                runnable.run();
                return;
            }

            CompletableFuture.runAsync(() -> {
                runnable.run();
            }, world).join();
        }

        public void schedule(World world, Runnable runnable, int delayTicks) {
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
