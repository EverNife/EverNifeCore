package br.com.finalcraft.evernifecore.time;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ECScheduler#runAsyncFuture(Runnable)} - a background-thread building block (the same one
 * {@code HyFCScheduler.onMainThreadLater} builds on): it runs the task off the caller thread and reports
 * the outcome through the returned future - and the main-thread hop, which refuses when no platform installed one.
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
}
