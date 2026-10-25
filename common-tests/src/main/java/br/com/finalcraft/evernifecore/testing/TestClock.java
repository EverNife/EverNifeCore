package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The clock and the scheduler every test world shares ({@link RegionWorld}, {@link LogicWorld}): installed on
 * {@link ECClock} and {@link ECScheduler} by the first world that opens, put back by the last that closes. The clock
 * only moves on {@link #advance}; a scheduled task only runs on {@link #runDue}. Two worlds open together see the same
 * hour and one {@code runDue} runs both agendas.
 */
public final class TestClock {

    private static final Object LOCK = new Object();
    private static Shared shared;

    private TestClock() {
    }

    /** Installs the shared clock and scheduler when nobody holds them yet; every call is matched by one {@link #release()}. */
    public static void acquire() {
        synchronized (LOCK) {
            if (shared == null) {
                Shared created = new Shared();
                shared = created;
                ECClock.setClock(created.clock);
                ECScheduler.set((task, delayMillis) -> {
                    synchronized (created.pending) {
                        created.pending.add(new Scheduled(task, created.clock.now.plusMillis(Math.max(0, delayMillis))));
                    }
                });
            }
            shared.users++;
        }
    }

    /** The last release closes every region manager while the scheduler is still this one, then restores the real clock and scheduler. */
    public static void release() {
        synchronized (LOCK) {
            if (--shared.users == 0) {
                //stopped while the scheduler is still this one: whatever they schedule on the way out never reaches a real thread
                RegionManager.closeAll();
                ECScheduler.set(null);
                ECClock.setClock(null);
                shared = null;
            }
        }
    }

    /** What {@link ECClock#now()} answers - fixed until {@link #advance(Duration)}. */
    public static Instant now() {
        return require().clock.instant();
    }

    /** Moves the clock forward; nothing runs until {@link #runDue()}. */
    public static void advance(Duration by) {
        Shared current = require();
        current.clock.now = current.clock.now.plus(by);
    }

    /**
     * Runs every scheduled task whose delay has passed by the clock, once. A task they schedule waits for the next call,
     * even with no delay - so a self-rescheduling task runs once per call.
     *
     * @return how many tasks ran
     */
    public static int runDue() {
        Shared current = require();
        List<Scheduled> due = new ArrayList<>();
        synchronized (current.pending) {
            Instant now = current.clock.now;
            for (Iterator<Scheduled> it = current.pending.iterator(); it.hasNext(); ) {
                Scheduled scheduled = it.next();
                if (!scheduled.dueAt.isAfter(now)) {
                    due.add(scheduled);
                    it.remove();
                }
            }
        }
        for (Scheduled scheduled : due) {
            scheduled.task.run();
        }
        return due.size();
    }

    /** How many tasks wait on the scheduler, due or not. */
    public static int getPendingTasks() {
        Shared current = require();
        synchronized (current.pending) {
            return current.pending.size();
        }
    }

    private static Shared require() {
        Shared current = shared;
        if (current == null) {
            throw new IllegalStateException("No test world holds the clock: open a RegionWorld or a LogicWorld first.");
        }
        return current;
    }

    private static final class Shared {
        final MovableClock clock = new MovableClock(Instant.parse("2026-10-06T12:00:00Z"));
        final List<Scheduled> pending = new ArrayList<>();
        int users;
    }

    private static final class Scheduled {
        final Runnable task;
        final Instant dueAt;

        Scheduled(Runnable task, Instant dueAt) {
            this.task = task;
            this.dueAt = dueAt;
        }
    }

    private static final class MovableClock extends Clock {
        volatile Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
