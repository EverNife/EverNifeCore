package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.scheduler.VirtualThreadedScheduledExecutor;

import java.util.concurrent.*;
import java.util.logging.Logger;

public class CfgExecutor {

    private static final Logger logger = Logger.getLogger("CfgExecutor");

    private static VirtualThreadedScheduledExecutor SCHEDULER = new VirtualThreadedScheduledExecutor("config-caching");

    public static VirtualThreadedScheduledExecutor getScheduler() {
        return SCHEDULER;
    }

    public static synchronized void shutdownExecutorAndScheduler(){
        if (!SCHEDULER.isShutdown() && !SCHEDULER.isTerminated()){
            try {
                SCHEDULER.shutdown();
                boolean success = SCHEDULER.awaitTermination(30, TimeUnit.SECONDS); //Give some time for the scheduler to finish
                if (!success){
                    logger.warning("Failed to close ConfigHelper Scheduler, TimeOut of 30 seconds Reached, this is really bad! Terminating all of them now!");
                    SCHEDULER.shutdownNow();
                }
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        } else {
            throw new IllegalStateException("Tried to stop the ConfigHelper Scheduler while it was not running at all!");
        }
    }
}
