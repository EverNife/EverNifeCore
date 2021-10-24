package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.*;

public class FCScheduller {

    private static final ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(5);

    public static ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }

    public static void runSync(Runnable runnable){
        BukkitRunnable bukkitRunnable = runnable instanceof BukkitRunnable ? (BukkitRunnable) runnable : new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        };
        bukkitRunnable.runTask(EverNifeCore.instance);
    }

    public static void scheduleSync(Runnable runnable, long delayMillis){
        BukkitRunnable bukkitRunnable = runnable instanceof BukkitRunnable ? (BukkitRunnable) runnable : new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        };
        scheduler.schedule(() -> {
            bukkitRunnable.runTask(EverNifeCore.instance);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void runAssync(Runnable runnable){
        scheduler.submit(runnable);
    }

    public static void scheduleAssync(Runnable runnable, long delayMillis){
        scheduler.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static class SynchronizedAction {

        public static <T> T runAndGet(Callable<T> callable){
            if (FCBukkitUtil.isMainThread()) throw new RejectedExecutionException("You cannot schedule a SynchronizedAction on the Main Thread!");
            try {
                if (FCBukkitUtil.isMainThread()) {
                    return callable.call();
                } else {
                    FutureTask<T> futureTask = new FutureTask(callable);
                    new BukkitRunnable(){
                        @Override
                        public void run() {
                            futureTask.run();
                        }
                    }.runTask(EverNifeCore.instance);
                    return futureTask.get();
                }
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
