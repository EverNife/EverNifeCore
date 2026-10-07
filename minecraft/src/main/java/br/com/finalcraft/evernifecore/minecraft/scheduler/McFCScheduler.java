package br.com.finalcraft.evernifecore.minecraft.scheduler;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class McFCScheduler {

    public static McFCScheduler INSTANCE = new McFCScheduler();

    private final SynchronizedAction synchronizedAction = new SynchronizedAction();

    private List<Runnable> heldUntilTheCoreEnables = new ArrayList<>(); //null once released

    public McFCScheduler() {

    }

    public SynchronizedAction getSynchronizedAction() {
        return synchronizedAction;
    }

    public static BukkitRunnable wrapRunnable(Runnable runnable){
        return runnable instanceof BukkitRunnable ? (BukkitRunnable) runnable : new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                }catch (Throwable throwable){
                    throwable.printStackTrace();
                }
            }
        };
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the Main Thread
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Runs {@code runnable} on the main thread on the next tick, owned by EverNifeCore.
     *
     * <p>A plugin that enables before EverNifeCore ({@code loadbefore}) may already call this while the
     * core is loaded but not yet enabled, when Bukkit would refuse the task. Such a task is held and
     * scheduled at the end of the core's enable, so it still runs on the first tick - after the
     * first-tick work the core itself scheduled during its enable.</p>
     */
    public void runSync(Runnable runnable){
        EverNifeCoreBukkitPlugin core = EverNifeCoreBukkitPlugin.instance;
        synchronized (this) {
            if (heldUntilTheCoreEnables != null && core != null && !core.isEnabled()) {
                heldUntilTheCoreEnables.add(runnable);
                return;
            }
        }
        wrapRunnable(runnable).runTask(core);
    }

    /** Schedules what {@link #runSync(Runnable)} held before the core enabled. Called once, by the core's enable. */
    public void releaseTasksHeldUntilTheCoreEnables(){
        List<Runnable> held;
        synchronized (this) {
            held = heldUntilTheCoreEnables;
            heldUntilTheCoreEnables = null;
        }
        if (held != null) {
            held.forEach(this::runSync);
        }
    }

    public void scheduleSync(Runnable runnable, long delayMillis){
        FCScheduler.getScheduler().schedule(() -> {
            wrapRunnable(runnable).runTask(EverNifeCoreBukkitPlugin.instance);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public BukkitTask scheduleSyncInTicks(Runnable runnable, long delayTicks){
        return wrapRunnable(runnable).runTaskLater(EverNifeCoreBukkitPlugin.instance, delayTicks);
    }

    /**
     * Runs {@code runnable} on the main thread every {@code periodTicks}, first after
     * {@code delayTicks}. Cancel through the returned task; nothing else stops it.
     */
    public BukkitTask repeatSyncInTicks(Runnable runnable, long delayTicks, long periodTicks){
        return wrapRunnable(runnable).runTaskTimer(EverNifeCoreBukkitPlugin.instance, delayTicks, periodTicks);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the Main Thread and be Returned to the Parallel Thread
    // -----------------------------------------------------------------------------------------------------------------

    public static class SynchronizedAction {

        public <T> T runAndGet(Callable<T> callable){
            if (FCBukkitUtil.isMainThread()) {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                FutureTask<T> futureTask = new FutureTask(callable);
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        futureTask.run();
                    }
                }.runTask(EverNifeCoreBukkitPlugin.instance);
                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public <T> T scheduleAndGet(Callable<T> callable, int delayTicks){
            if (FCBukkitUtil.isMainThread()) {
                throw new RejectedExecutionException("You cannot schedule a SynchronizedAction on the Main Thread!");
            }

            try {
                FutureTask<T> futureTask = new FutureTask(callable);
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        futureTask.run();
                    }
                }.runTaskLater(EverNifeCoreBukkitPlugin.instance, delayTicks);
                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public void run(Runnable runnable) {
            if (FCBukkitUtil.isMainThread()) {
                runnable.run();
                return;
            }

            runAndGet(() -> {
                runnable.run();
                return null;
            });
        }

        public void schedule(Runnable runnable, int delayTicks) {
            scheduleAndGet(() -> {
                runnable.run();
                return null;
            }, delayTicks);
        }

    }

}
