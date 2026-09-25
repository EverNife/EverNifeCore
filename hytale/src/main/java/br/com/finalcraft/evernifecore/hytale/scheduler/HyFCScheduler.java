package br.com.finalcraft.evernifecore.hytale.scheduler;

import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

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
    //  The tick clock
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * How many times a second the universe's default world ticks - what a tick count means on this server, since
     * a Hytale world's tick rate is configurable and not the 20 of Minecraft. {@link TickingThread#TPS} (30), the
     * server's default rate, while there is no default world to ask.
     */
    public int getMainWorldTps() {
        Universe universe = Universe.get();
        return tpsOf(universe == null ? null : universe.getDefaultWorld());
    }

    /** The tick rate {@code world} is set to run at, or {@link TickingThread#TPS} (30) when there is no world to ask. */
    public static int tpsOf(World world) {
        int tps = world == null ? 0 : world.getTps();
        return tps > 0 ? tps : TickingThread.TPS;
    }

    /** How long {@code ticks} last on a clock running at {@code tps}, in milliseconds. */
    public static long ticksToMillis(long ticks, int tps) {
        return ticks * 1000L / tps;
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

    /** Runs {@code runnable} on {@code world}'s thread after {@code delayTicks} of that world's own ticks. */
    public void scheduleSyncInTicks(World world, Runnable runnable, long delayTicks){
        long delayMillis = ticksToMillis(delayTicks, tpsOf(world));
        FCScheduler.runAsync(() -> {
            try {
                Thread.sleep(delayMillis);
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

                long delayMillis = ticksToMillis(delayTicks, tpsOf(world));
                FCScheduler.runAsync(() -> {
                    try {
                        Thread.sleep(delayMillis);
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
