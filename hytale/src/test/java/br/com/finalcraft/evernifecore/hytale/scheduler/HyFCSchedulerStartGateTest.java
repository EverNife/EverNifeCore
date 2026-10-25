package br.com.finalcraft.evernifecore.hytale.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hytale's stand-in for "once every plugin has enabled": a main-thread task asked before the start phase waits for
 * the flush, and one asked after it runs at once - on a background thread either way.
 */
class HyFCSchedulerStartGateTest {

    @Test
    void aTaskAskedBeforeTheStartPhaseWaitsForTheFlushAndRunsOffTheCaller() throws Exception {
        HyFCScheduler scheduler = new HyFCScheduler(); //a fresh gate; INSTANCE may have been flushed by another test
        AtomicReference<Thread> ranOn = new AtomicReference<>();

        CompletableFuture<String> later = scheduler.onMainThreadLater(() -> {
            ranOn.set(Thread.currentThread());
            return "ran";
        });

        assertThrows(TimeoutException.class, () -> later.get(200, TimeUnit.MILLISECONDS),
                "nothing runs before the server reached its start phase");
        assertNull(ranOn.get());

        scheduler.flushPendingMainThreadTasks();

        assertEquals("ran", later.get(5, TimeUnit.SECONDS));
        assertNotSame(Thread.currentThread(), ranOn.get(), "the task runs on a background thread, not the caller's");
    }

    @Test
    void afterTheFlushATaskRunsWithoutWaitingForAnother() throws Exception {
        HyFCScheduler scheduler = new HyFCScheduler();
        scheduler.flushPendingMainThreadTasks();

        assertEquals("now", scheduler.onMainThread(() -> "now").get(5, TimeUnit.SECONDS));
    }

}
