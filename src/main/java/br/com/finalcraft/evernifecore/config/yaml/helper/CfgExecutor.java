package br.com.finalcraft.evernifecore.config.yaml.helper;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;
import java.util.logging.Logger;

public class CfgExecutor {

    private static final Logger logger = Logger.getLogger("CfgExecutor");

    private static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(10);

    public static ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }

    public static ExecutorService EXECUTOR_SERVICE;
    static {
        createExecutorService();
    }

    public static void createExecutorService(){
        EXECUTOR_SERVICE = new ThreadPoolExecutor(5, Integer.MAX_VALUE,
                1000L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue(),
                new ThreadFactoryBuilder()
                        .setNameFormat("evernifecore-asyncsave-pool-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    public static ExecutorService getExecutorService() {
        return EXECUTOR_SERVICE;
    }

    public static synchronized void shutdownExecutor(){
        if (EXECUTOR_SERVICE != null && !EXECUTOR_SERVICE.isShutdown() && !EXECUTOR_SERVICE.isTerminated()){
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
