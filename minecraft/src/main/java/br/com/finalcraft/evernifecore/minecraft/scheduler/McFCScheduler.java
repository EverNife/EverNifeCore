package br.com.finalcraft.evernifecore.minecraft.scheduler;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class McFCScheduler {

    public static McFCScheduler INSTANCE = new McFCScheduler();

    private final SynchronizedAction synchronizedAction = new SynchronizedAction();

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

    public void runSync(Runnable runnable){
        wrapRunnable(runnable).runTask(EverNifeCoreBukkitPlugin.instance);
    }

    public void scheduleSync(Runnable runnable, long delayMillis){
        FCScheduler.getScheduler().schedule(() -> {
            wrapRunnable(runnable).runTask(EverNifeCoreBukkitPlugin.instance);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void scheduleSyncInTicks(Runnable runnable, long delayTicks){
        wrapRunnable(runnable).runTaskLater(EverNifeCoreBukkitPlugin.instance, delayTicks);
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
