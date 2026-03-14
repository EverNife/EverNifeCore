package br.com.finalcraft.evernifecore._misc.thread;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WhyScheduledThreadPoolExecutorSucksTest {

    /**
     * Spoiler, it sucks because it creates the Threads at the moment you schedule the task.
     *
     * Lets say you want to execute a task in 2 hours from now.
     *
     * At the moment you shedule the task the new Thread is created, and this new thread
     * will only die in 2 hours from now.
     *
     * @throws InterruptedException
     */
    @Test
    @SneakyThrows
    public void whyScheduledThreadPoolExecutor() {

        if (true) {
            return; // Disabled test to avoid execution during normal test runs
        }

        ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(200);

        Runnable task = () -> {
            System.out.println("\nExecuting task at " + System.currentTimeMillis() + " by thread " + Thread.currentThread().getName());
            try {
                Thread.sleep(1000); // Simulate task taking time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        System.out.println("\n\nAt ScheduledThreadPoolExecutor Creation:");
        System.out.println("Pool size: " + scheduler.getPoolSize());
        System.out.println("Active count: " + scheduler.getActiveCount());
        System.out.println("Task count: " + scheduler.getTaskCount());
        System.out.println("Completed task count: " + scheduler.getCompletedTaskCount());

        System.out.println("\n\n System Thread.getAllStackTraces() :: isAlive(): " + Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive()).count());
        System.out.println("\n");

        // Schedule a task to run 2 hours later
        for (int i = 0; i < 1000; i++) {
            scheduler.schedule(task, 3600 + i * 2, TimeUnit.SECONDS);
        }

        // Schedule a task to run 5 seconds later
        scheduler.schedule(task, 1, TimeUnit.SECONDS);

        // Monitor the thread pool immediately after scheduling tasks
        System.out.println("Immediately after scheduling 1001 tasks:");
        System.out.println("Pool size: " + scheduler.getPoolSize());
        System.out.println("Active count: " + scheduler.getActiveCount());
        System.out.println("Task count: " + scheduler.getTaskCount());
        System.out.println("Completed task count: " + scheduler.getCompletedTaskCount());

        // Wait for 10 seconds to allow the 5-second task to execute
        Thread.sleep(2000);

        // Monitor the thread pool after some time
        System.out.println("\nAfter 5 seconds (executed 1 task):");
        System.out.println("Pool size: " + scheduler.getPoolSize());
        System.out.println("Active count: " + scheduler.getActiveCount());
        System.out.println("Task count: " + scheduler.getTaskCount());
        System.out.println("Completed task count: " + scheduler.getCompletedTaskCount());


        System.out.println("\n\n System Thread.getAllStackTraces() :: isAlive(): " + Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive()).count());

        // Shutdown the executor after tasks completion
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        System.out.println("\n\n Threads Alive: " + Thread.getAllStackTraces().keySet().size());
        System.out.println("\n\n Threads Alive: " + Thread.getAllStackTraces().keySet());

        Thread.sleep(2000);

        System.out.println("\n\n System Thread.getAllStackTraces() :: isAlive(): " + Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive()).count());
    }

}
