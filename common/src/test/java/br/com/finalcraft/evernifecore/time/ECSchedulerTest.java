package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.testing.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ECScheduler#runAsyncFuture(Runnable)} - a background-thread building block (the same one
 * {@code HyFCScheduler.onMainThreadLater} builds on): it runs the task off the caller thread and reports
 * the outcome through the returned future - the repeated task on the test clock, and the main-thread hop, which refuses
 * when no platform installed one.
 */
class ECSchedulerTest {

    @Test
    void completesOnceTheTaskHasRun() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);

        ECScheduler.runAsyncFuture(() -> ran.set(true)).get(5, TimeUnit.SECONDS);

        assertTrue(ran.get(), "the future must not complete before the task has run");
    }

    @Test
    void completesExceptionallyWhenTheTaskThrows() {
        CompletableFuture<Void> future = ECScheduler.runAsyncFuture(() -> {
            throw new IllegalStateException("boom"); //expected: the stack trace print below is intentional
        });

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }

    @Test
    void withoutAnInstalledMainThreadTheHopRefusesAndSaysWhoInstallsOne() {
        ECScheduler.MainThread previous = ECScheduler.getMainThread();
        ECScheduler.setMainThread(null);
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> ECScheduler.onMainThread(() -> { }));
            assertTrue(refused.getMessage().contains("ECScheduler.setMainThread"), refused.getMessage());
            assertThrows(IllegalStateException.class, () -> ECScheduler.onMainThreadLater(() -> { }));
        } finally {
            ECScheduler.setMainThread(previous);
        }
    }

    @Test
    void aRepeatingTaskRunsOncePerPeriodUntilCancelled() {
        TestClock.acquire();
        try {
            AtomicInteger runs = new AtomicInteger();
            ECScheduler.RepeatingTask task = ECScheduler.repeatAsync(runs::incrementAndGet, 1000, 500);

            TestClock.advance(Duration.ofMillis(999));
            TestClock.runDue();
            assertEquals(0, runs.get(), "nothing before the first delay");
            TestClock.advance(Duration.ofMillis(1));
            TestClock.runDue();
            for (int i = 0; i < 4; i++) {
                TestClock.advance(Duration.ofMillis(500));
                TestClock.runDue();
            }
            assertEquals(5, runs.get(), "the first run after the delay, then one per period");

            task.cancel();
            for (int i = 0; i < 3; i++) {
                TestClock.advance(Duration.ofMillis(500));
                TestClock.runDue();
            }
            assertEquals(5, runs.get(), "no run after cancel");
            assertEquals(0, TestClock.getPendingTasks(), "a cancelled task leaves nothing on the agenda");
        } finally {
            TestClock.release();
        }
    }

    @Test
    void aRunThatThrowsDoesNotStopTheNextOne() {
        TestClock.acquire();
        try {
            AtomicInteger runs = new AtomicInteger();
            ECScheduler.RepeatingTask task = ECScheduler.repeatAsync(() -> {
                if (runs.incrementAndGet() == 1) {
                    throw new IllegalStateException("boom"); //expected: the stack trace print is intentional
                }
            }, 0, 100);

            TestClock.runDue();
            TestClock.advance(Duration.ofMillis(100));
            TestClock.runDue();

            assertEquals(2, runs.get(), "the run after the one that threw still came");
            task.cancel();
        } finally {
            TestClock.release();
        }
    }
}
