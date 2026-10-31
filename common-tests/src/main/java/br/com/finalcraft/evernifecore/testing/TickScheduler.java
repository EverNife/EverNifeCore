package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.screen.Cancellable;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ScreenScheduler} whose clock only moves when a test says so ({@link #advanceTicks(long)}). The test thread is
 * the render thread: a task due now runs inside the call that advanced to it, in the order it was scheduled.
 */
public final class TickScheduler implements ScreenScheduler {

    private final List<Task> tasks = new ArrayList<>();
    private long now = 0L;

    public synchronized long getCurrentTick() {
        return now;
    }

    /** Moves the clock {@code ticks} forward, one tick at a time, running what falls due on each. */
    public void advanceTicks(long ticks) {
        for (long i = 0; i < ticks; i++) {
            List<Task> due = new ArrayList<>();
            synchronized (this) {
                now++;
                for (Task task : new ArrayList<>(tasks)) {
                    if (!task.cancelled && task.dueAt <= now) {
                        due.add(task);
                        if (task.period > 0) {
                            task.dueAt = now + task.period;
                        } else {
                            tasks.remove(task);
                        }
                    }
                }
            }
            for (Task task : due) {
                if (!task.cancelled) {
                    task.body.run();
                }
            }
        }
    }

    /** Tasks still scheduled - a view that closed cleanly leaves none of its own. */
    public synchronized int getActiveTaskCount() {
        int active = 0;
        for (Task task : tasks) {
            if (!task.cancelled) {
                active++;
            }
        }
        return active;
    }

    @Override
    public synchronized Cancellable later(long ticks, Runnable task) {
        return add(new Task(now + Math.max(1L, ticks), 0L, task));
    }

    @Override
    public synchronized Cancellable repeat(long ticks, Runnable task) {
        long period = Math.max(1L, ticks);
        return add(new Task(now + period, period, task));
    }

    @Override
    public boolean isRenderThread() {
        return true;
    }

    private Cancellable add(Task task) {
        tasks.add(task);
        return () -> {
            synchronized (TickScheduler.this) {
                task.cancelled = true;
                tasks.remove(task);
            }
        };
    }

    private static final class Task {

        private long dueAt;
        private final long period;
        private final Runnable body;
        private volatile boolean cancelled;

        private Task(long dueAt, long period, Runnable body) {
            this.dueAt = dueAt;
            this.period = period;
            this.body = body;
        }

    }

}
