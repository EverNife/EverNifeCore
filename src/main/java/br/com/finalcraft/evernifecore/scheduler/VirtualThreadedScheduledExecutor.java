package br.com.finalcraft.evernifecore.scheduler;

import br.com.finalcraft.evernifecore.util.FCExecutorsUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A hybrid scheduler that uses a ScheduledExecutorService to handle timing,
 * and executes tasks on virtual threads to prevent blocking issues.
 *
 * Ideal for workloads where tasks may call Thread.sleep(), wait on I/O,
 * or perform blocking operations.
 *
 * This class requires Java 21 or higher to utilize virtual threads.
 * On lower Java versions, it falls back to a fixed thread pool of System core count.
 */
@Log4j2
public class VirtualThreadedScheduledExecutor implements AutoCloseable {

    private final String identifier;
    private final ScheduledThreadPoolExecutor scheduler;
    private final ExecutorService executor;

    /**
     * Creates a scheduler that uses a small number of platform threads
     * to handle scheduling, while tasks run on lightweight virtual threads.
     */
    public VirtualThreadedScheduledExecutor(String identifier) {
        this.identifier = identifier;

        // A small pool for scheduling (uses platform threads), only for scheduling stuff
        // We use half of the available processors to avoid oversubscription
        this.scheduler = new ScheduledThreadPoolExecutor(
                FCExecutorsUtil.getMinMaxThreadCountBoundedToSystemCoreCount(Runtime.getRuntime().availableProcessors() / 2).getMax(),
                new ThreadFactoryBuilder()
                        .setNameFormat(identifier + "-evernifecore-scheduler-%d")
                        .setDaemon(true)
                        .build()
        );
        this.scheduler.setMaximumPoolSize(Runtime.getRuntime().availableProcessors());
        this.scheduler.setRemoveOnCancelPolicy(true);

        // If we are not on Java 21+, we fallback to a fixed thread pool bounded to the number of CPU cores
        this.executor = FCExecutorsUtil.createVirtualExecutorIfPossible(
                this.identifier
        );
    }

    /**
     * Executes a task immediately on a virtual thread.
     *
     * @param task the task to execute
     */
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        executor.execute(task);
    }

    /**
     * Schedules a task to run after the given delay on a virtual thread.
     *
     * @param task  the task to execute
     * @param delay the delay duration before execution
     * @param unit  the time unit of the delay
     * @return a ScheduledFuture representing pending completion
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        return scheduler.schedule(() -> executor.execute(task), delay, unit);
    }

    /**
     * Schedules a task to run periodically on virtual threads.
     *
     * @param task         the task to execute
     * @param initialDelay initial delay before first run
     * @param period       period between successive executions
     * @param unit         the time unit of delay and period
     * @return a ScheduledFuture that can be used to cancel future executions
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        return scheduler.scheduleAtFixedRate(() -> executor.execute(task), initialDelay, period, unit);
    }

    /**
     * Calls shutdown() on both scheduler and executor. Each call is isolated so an exception
     * thrown while shutting one does not prevent the other from being attempted. All errors
     * are thrown and logged with the identifier.
     */
    public List<Runnable> shutdown() {
        List<Exception> errors = new ArrayList<>();
        List<Runnable> pending = new ArrayList<>();

        try {
            if (!scheduler.isShutdown() && !scheduler.isTerminated() && !scheduler.isTerminating()){
                // Fscheduuler.shutdown() will not cancel pending tasks, it just stops accepting new ones
                // So we call shutdownNow() to also retrieve pending tasks
                List<Runnable> tasks = scheduler.shutdownNow();
                if (tasks != null){
                    pending.addAll(tasks);
                }
            }
        } catch (Exception e) {
            errors.add(e);
            log.error("[EverNifeCore-VirtualThreadedScheduledExecutorService] {} - error while shutting down scheduler", identifier, e);
        }

        try {
            if (!executor.isShutdown() && !executor.isTerminated()){
                executor.shutdown();
            }
        } catch (Exception e) {
            errors.add(e);
            log.error("[EverNifeCore-VirtualThreadedScheduledExecutorService] {} - error while shutting down executor", identifier, e);
        }

        if (!errors.isEmpty()) {
            throw new VTSShutdownException("Errors occurred while shutting down VirtualThreadedScheduledExecutorService '" + identifier + "'. See logs for details.", errors);
        }

        return pending;
    }

    /**
     * Attempts to stop all actively executing tasks and halts processing of waiting tasks.
     * Returns the list of tasks that were awaiting execution from both scheduler and executor.
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> pending = new ArrayList<>();

        if (!scheduler.isShutdown() && !scheduler.isTerminated() && !scheduler.isTerminating()){
            scheduler.shutdownNow();
        }

        try {
            List<Runnable> tasks = executor.shutdownNow();
            if (tasks != null){
                pending.addAll(tasks);
            }
        } catch (Exception ex) {
            log.error("[EverNifeCore-VirtualThreadedScheduledExecutorService] {} - error while calling executor.shutdownNow()", identifier, ex);
        }

        return pending;
    }

    /**
     * Waits for the executor to terminate within the given timeout.
     * (the scheduler was already shutdownNow, so no need to wait for it)
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit must not be null");

        return executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
    }


    public boolean isShutdown() {
        return scheduler.isShutdown() || executor.isShutdown();
    }

    public boolean isTerminated() {
        return scheduler.isTerminated() && executor.isTerminated();
    }

    @Override
    public void close() {
        // Best-effort close: try graceful shutdown and if interrupted escalate
        shutdown();
        try {
            awaitTermination(1, TimeUnit.SECONDS);
            if (!isTerminated()) {
                shutdownNow();
            }
        } catch (InterruptedException ie) {
            log.warn("[EverNifeCore-VirtualThreadedScheduledExecutorService] {} - interrupted while awaiting termination during close(); invoking shutdownNow() and restoring interruption", identifier, ie);
            try {
                shutdownNow();
            } catch (Exception e) {
                log.error("[EverNifeCore-VirtualThreadedScheduledExecutorService] {} - error while invoking shutdownNow() during close", identifier, e);
            }
            Thread.currentThread().interrupt();
        }
    }

    public static class VTSShutdownException extends RuntimeException {

        private final List<Exception> errors;

        public VTSShutdownException(String message, List<Exception> errors) {
            super(message, errors.get(0));
            this.errors = errors;
        }

        public List<Exception> getErrors() {
            return errors;
        }
    }


}