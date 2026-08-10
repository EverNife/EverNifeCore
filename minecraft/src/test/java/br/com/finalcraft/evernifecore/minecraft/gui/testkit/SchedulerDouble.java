package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiScheduler;
import br.com.finalcraft.evernifecore.minecraft.testkit.Doubles;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * A server clock a test moves by hand. {@link #advanceTicks(long)} is the only thing that makes time
 * pass, so a gui that says "once a second" can be proven to fire exactly five times in a hundred
 * ticks instead of being slept on and hoped about.
 *
 * <p>A task scheduled while a tick is running belongs to a later tick, never to the one in flight -
 * the same rule a real server follows, and the reason a task that reschedules itself cannot spin.</p>
 *
 * <p>It answers both interfaces the framework can reach it through: {@link GuiScheduler}, for a view
 * built with it directly, and {@link #asBukkitScheduler()}, for the production path that goes
 * {@code BukkitGuiScheduler -> McFCScheduler -> Bukkit.getScheduler()}.</p>
 *
 * <p>Every method is synchronized. Scheduling is how anything off the main thread - a chat answer, a
 * timeout, a database callback - hands work back to it, so a queue only the test thread could touch
 * would drop those or corrupt itself instead of exercising the hop.</p>
 */
public final class SchedulerDouble implements GuiScheduler {

    private final List<Task> tasks = new ArrayList<>();

    private long currentTick = 0L;
    private int nextTaskId = 1;
    private BukkitScheduler bukkitFace;

    // -----------------------------------------------------------------------------------------------------------------
    //  The clock
    // -----------------------------------------------------------------------------------------------------------------

    public synchronized long getCurrentTick() {
        return currentTick;
    }

    /** Runs one tick at a time, so a task scheduled inside a tick waits for the next one. */
    public synchronized void advanceTicks(long ticks) {
        for (long i = 0; i < ticks; i++) {
            currentTick++;
            for (Task task : new ArrayList<>(tasks)) {
                if (task.cancelled || task.dueTick > currentTick) {
                    continue;
                }
                if (task.periodTicks > 0) {
                    task.dueTick = currentTick + task.periodTicks;
                } else {
                    tasks.remove(task);
                }
                task.body.run();
            }
        }
    }

    /** Everything still scheduled: one-shots not yet due and repetitions not yet cancelled. */
    public synchronized int getActiveTaskCount() {
        int count = 0;
        for (Task task : tasks) {
            if (!task.cancelled) {
                count++;
            }
        }
        return count;
    }

    public synchronized int getPeriodicTaskCount() {
        int count = 0;
        for (Task task : tasks) {
            if (!task.cancelled && task.periodTicks > 0) {
                count++;
            }
        }
        return count;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  GuiScheduler
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public synchronized Cancellable later(long ticks, Runnable task) {
        return schedule(ticks, 0L, task)::cancel;
    }

    @Override
    public synchronized Cancellable repeat(long ticks, Runnable task) {
        return schedule(ticks, ticks, task)::cancel;
    }

    private synchronized Task schedule(long delayTicks, long periodTicks, Runnable body) {
        Task task = new Task(nextTaskId++, currentTick + Math.max(1L, delayTicks), periodTicks, body);
        tasks.add(task);
        return task;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The platform's face of the same clock
    // -----------------------------------------------------------------------------------------------------------------

    public synchronized BukkitScheduler asBukkitScheduler() {
        if (bukkitFace == null) {
            bukkitFace = Doubles.of(BukkitScheduler.class)
                    .on("runTask", args -> taskFace(schedule(1L, 0L, (Runnable) args[1])))
                    .on("runTaskLater", args -> taskFace(schedule((Long) args[2], 0L, (Runnable) args[1])))
                    .on("runTaskTimer", args -> taskFace(schedule((Long) args[2], (Long) args[3], (Runnable) args[1])))
                    .on("cancelTask", args -> {
                        cancelById((Integer) args[0]);
                        return null;
                    })
                    .on("runTaskAsynchronously", args -> offThisClock("runTaskAsynchronously"))
                    .on("runTaskLaterAsynchronously", args -> offThisClock("runTaskLaterAsynchronously"))
                    .on("runTaskTimerAsynchronously", args -> offThisClock("runTaskTimerAsynchronously"))
                    .build();
        }
        return bukkitFace;
    }

    /** This clock is a tick counter a test advances by hand; there is no other thread to hand work to. */
    private static BukkitTask offThisClock(String method) {
        throw new UnsupportedOperationException("BukkitScheduler#" + method + " has no double: this "
                + "scheduler is a tick counter the test advances by hand, so work handed to another "
                + "thread would simply never run. Schedule it on this clock, or drive the code under "
                + "test from the thread it is meant to run on.");
    }

    private BukkitTask taskFace(Task task) {
        return Doubles.of(BukkitTask.class)
                .on("getTaskId", args -> task.id)
                .on("isCancelled", args -> task.cancelled)
                .on("cancel", args -> {
                    task.cancel();
                    return null;
                })
                .build();
    }

    private synchronized void cancelById(int id) {
        for (Task task : new ArrayList<>(tasks)) {
            if (task.id == id) {
                task.cancel();
            }
        }
    }

    private final class Task {

        private final int id;
        private final long periodTicks;
        private final Runnable body;

        private long dueTick;
        private boolean cancelled = false;

        private Task(int id, long dueTick, long periodTicks, Runnable body) {
            this.id = id;
            this.dueTick = dueTick;
            this.periodTicks = periodTicks;
            this.body = body;
        }

        private void cancel() {
            synchronized (SchedulerDouble.this) {
                cancelled = true;
                tasks.remove(this);
            }
        }

    }

}
