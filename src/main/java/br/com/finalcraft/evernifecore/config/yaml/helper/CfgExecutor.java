package br.com.finalcraft.evernifecore.config.yaml.helper;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;
import java.util.logging.Logger;

public class CfgExecutor {

    private static final Logger logger = Logger.getLogger("CfgExecutor");

    private static final ScheduledThreadPoolExecutor SCHEDULER =
            new ScheduledThreadPoolExecutor(Math.max(5, Runtime.getRuntime().availableProcessors()),
                    new ThreadFactoryBuilder()
                            .setNameFormat("evernifecore-cfgexecutor-caching-%d")
                            .setDaemon(true)
                            .build()
            );

    private static final ExecutorService EXECUTOR_SERVICE =
            new ThreadPoolExecutor(5, Math.max(5, Runtime.getRuntime().availableProcessors()),
                    1000L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    new ThreadFactoryBuilder()
                            .setNameFormat("evernifecore-asyncsave-pool-%d")
                            .setDaemon(true)
                            .build()
            );

    public static ScheduledThreadPoolExecutor getScheduler() {
        return SCHEDULER;
    }

    public static ExecutorService getExecutorService() {
        return EXECUTOR_SERVICE;
    }

    public static synchronized void shutdownExecutorAndScheduler(){

        if (!SCHEDULER.isShutdown() && !SCHEDULER.isTerminated()){
            try {
                SCHEDULER.shutdown();
                boolean success = SCHEDULER.awaitTermination(5, TimeUnit.SECONDS); //Give some time for the scheduler to finish
                if (!success){
                    // No need ot care about the scheduler shutdown, it will be closed by the JVM
                }
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }

        if (!EXECUTOR_SERVICE.isShutdown() && !EXECUTOR_SERVICE.isTerminated()){
            try {
                EXECUTOR_SERVICE.shutdown();
                boolean success = EXECUTOR_SERVICE.awaitTermination(30, TimeUnit.SECONDS);
                if (!success){
                    logger.warning("Failed to close ConfigHelper Scheduler, TimeOut of 30 seconds Reached, this is really bad! Terminating all of them now!");
                    EXECUTOR_SERVICE.shutdownNow();
                }
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }else {
            throw new IllegalStateException("Tried to stop the ConfigHelper Scheduler while it was not running at all!");
        }
    }
}
