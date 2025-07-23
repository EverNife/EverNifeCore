package br.com.finalcraft.evernifecore.scheduler;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.*;

public class FCScheduler {

    private static final ThreadPoolExecutor scheduler = new ThreadPoolExecutor(
            5,
            Math.min(5, Runtime.getRuntime().availableProcessors()),
            1000L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder()
                    .setNameFormat("evernifecore-fcscheduler-%d")
                    .setDaemon(true)
                    .build()
    );

    public static ThreadPoolExecutor getScheduler() {
        return scheduler;
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

    public static void runSync(Runnable runnable){
        wrapRunnable(runnable).runTask(EverNifeCore.instance);
    }

    public static void scheduleSync(Runnable runnable, long delayMillis){
        scheduler.schedule(() -> {
            wrapRunnable(runnable).runTask(EverNifeCore.instance);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void scheduleSyncInTicks(Runnable runnable, long delayTicks){
        wrapRunnable(runnable).runTaskLater(EverNifeCore.instance, delayTicks);
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

    public static void scheduleAsyncInTicks(Runnable runnable, long delayTicks){
        wrapRunnable(runnable).runTaskLaterAsynchronously(EverNifeCore.instance, delayTicks);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Actions to be Executed on the Main Thread and be Returned to the Parallel Thread
    // -----------------------------------------------------------------------------------------------------------------

    public static class SynchronizedAction {

        public static <T> T runAndGet(Callable<T> callable){
            if (FCBukkitUtil.isMainThread()) throw new RejectedExecutionException("You cannot schedule a SynchronizedAction on the Main Thread!");
            try {
                FutureTask<T> futureTask = new FutureTask(callable);
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        futureTask.run();
                    }
                }.runTask(EverNifeCore.instance);
                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public static <T> T scheduleAndGet(Callable<T> callable, int delayTicks){
            if (FCBukkitUtil.isMainThread()) throw new RejectedExecutionException("You cannot schedule a SynchronizedAction on the Main Thread!");
            try {
                FutureTask<T> futureTask = new FutureTask(callable);
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        futureTask.run();
                    }
                }.runTaskLater(EverNifeCore.instance, delayTicks);
                return futureTask.get();
            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }

        public static void run(Runnable runnable) {
            runAndGet(() -> {
                runnable.run();
                return null;
            });
        }

        public static void schedule(Runnable runnable, int delayTicks) {
            scheduleAndGet(() -> {
                runnable.run();
                return null;
            }, delayTicks);
        }

    }
}
