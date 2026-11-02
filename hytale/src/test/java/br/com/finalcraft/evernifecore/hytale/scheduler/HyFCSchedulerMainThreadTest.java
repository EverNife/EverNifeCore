package br.com.finalcraft.evernifecore.hytale.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The core's Hytale main thread is ONE thread: tasks never overlap, run in the order asked, and nest in place. */
class HyFCSchedulerMainThreadTest {

    private static HyFCScheduler startedScheduler() {
        HyFCScheduler scheduler = new HyFCScheduler();
        scheduler.flushPendingMainThreadTasks();
        return scheduler;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void twoMainThreadTasksNeverOverlap() throws Exception {
        HyFCScheduler scheduler = startedScheduler();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        List<CompletableFuture<Void>> all = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            all.add(scheduler.onMainThreadLater(() -> {
                if (running.incrementAndGet() > 1) {
                    overlaps.incrementAndGet();
                }
                sleep(30);
                running.decrementAndGet();
                return null;
            }));
        }

        CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        assertEquals(0, overlaps.get(), "main-thread tasks run one at a time");
    }

    @Test
    void tasksRunInTheOrderAsked() throws Exception {
        HyFCScheduler scheduler = startedScheduler();
        List<Integer> ran = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> all = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            int index = i;
            all.add(scheduler.onMainThreadLater(() -> {
                sleep((8 - index) * 10L); //the first asked is the slowest: only a queue keeps it first
                ran.add(index);
                return null;
            }));
        }

        CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        assertEquals(IntStream.range(0, 8).boxed().collect(Collectors.toList()), ran, "tasks run in the order asked");
    }

    @Test
    void onMainThreadFromTheMainThreadRunsInPlace() throws Exception {
        HyFCScheduler scheduler = startedScheduler();
        AtomicReference<Thread> outer = new AtomicReference<>();
        AtomicReference<Thread> inner = new AtomicReference<>();

        scheduler.onMainThreadLater(() -> {
            outer.set(Thread.currentThread());
            CompletableFuture<Void> nested = scheduler.onMainThread(() -> {
                inner.set(Thread.currentThread());
                return null;
            });
            try {
                nested.get(2, TimeUnit.SECONDS); //in place: already complete; queued behind us it would time out
            } catch (Exception ignored) {
            }
            return null;
        }).get(5, TimeUnit.SECONDS);

        assertSame(outer.get(), inner.get(), "onMainThread on the main thread runs in place");
    }

}
