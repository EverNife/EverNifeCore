package br.com.finalcraft.evernifecore.hytale.scheduler;

import br.com.finalcraft.evernifecore.time.ECScheduler;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class HyFCScheduler implements ECScheduler.MainThread {

    public static HyFCScheduler INSTANCE = new HyFCScheduler();

    private final SynchronizedAction synchronizedAction = new SynchronizedAction();

    //main-thread tasks asked before the start phase (every plugin set up, all worlds loaded) wait on a gate here;
    //flushPendingMainThreadTasks() opens them once, then tasks run at once.
    private static final CompletableFuture<Void> READY_GATE = CompletableFuture.completedFuture(null);
    private final Object startGateLock = new Object();
    private boolean serverStarted = false;
    private final List<CompletableFuture<Void>> pendingStartGates = new ArrayList<>();

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
    //  The main thread ECScheduler hops to
    // -----------------------------------------------------------------------------------------------------------------

    /** The same as {@link #onMainThreadLater(Supplier)}: there is no main thread here to test the caller against. */
    @Override
    public <T> CompletableFuture<T> onMainThread(Supplier<T> task) {
        return onMainThreadLater(task);
    }

    /**
     * Runs the task on a background thread - Hytale has no single main thread (schedulers are per-world) - once the
     * start phase came: asked before it, the task waits for {@link #flushPendingMainThreadTasks()}; asked after, it
     * runs right away. A throwable is printed and fails the future.
     */
    @Override
    public <T> CompletableFuture<T> onMainThreadLater(Supplier<T> task) {
        CompletableFuture<Void> gate;
        synchronized (startGateLock) {
            if (serverStarted) {
                gate = READY_GATE;
            } else {
                gate = new CompletableFuture<>();
                pendingStartGates.add(gate);
            }
        }
        return gate.thenCompose(ignored -> ECScheduler.supplyAsyncFuture(task));
    }

    /**
     * Releases every task {@link #onMainThreadLater(Supplier)} buffered before the start phase - called from
     * {@code ECHytalePlugin.start()}, which the server runs once every plugin has finished {@code setup()} and all
     * worlds are loaded. Idempotent: repeated (or per-plugin) calls find nothing left to release.
     */
    public void flushPendingMainThreadTasks() {
        List<CompletableFuture<Void>> gates;
        synchronized (startGateLock) {
            if (serverStarted) {
                return;
            }
            serverStarted = true;
            gates = new ArrayList<>(pendingStartGates);
            pendingStartGates.clear();
        }
        gates.forEach(gate -> gate.complete(null));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the World Thread
    // -----------------------------------------------------------------------------------------------------------------

    public void runSync(World world, Runnable runnable){
        world.execute(runnable);
    }

    public void scheduleSync(World world, Runnable runnable, long delayMillis){
        ECScheduler.getScheduler().schedule(() -> {
            world.execute(runnable);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /** Runs {@code runnable} on {@code world}'s thread after {@code delayTicks} of that world's own ticks. */
    public void scheduleSyncInTicks(World world, Runnable runnable, long delayTicks){
        long delayMillis = ticksToMillis(delayTicks, tpsOf(world));
        ECScheduler.runAsync(() -> {
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
                ECScheduler.runAsync(() -> {
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
