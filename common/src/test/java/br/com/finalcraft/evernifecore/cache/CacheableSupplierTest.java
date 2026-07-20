package br.com.finalcraft.evernifecore.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link CacheableSupplier} shared across threads runs its wrapped supplier at most once per
 * interval: many threads racing on a cold cache still trigger a single {@code supplier.get()}.
 */
class CacheableSupplierTest {

    @Test
    void concurrentReadsInvokeTheSupplierExactlyOncePerInterval() throws InterruptedException {
        int threads = 16;
        AtomicInteger invocations = new AtomicInteger(0);
        //an interval far longer than the test window: a single refresh must serve every reader
        CacheableSupplier<Integer> cache =
                new CacheableSupplier<>(invocations::incrementAndGet, TimeUnit.MINUTES.toMillis(1));

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        fire.await(); //release all threads at once, maximizing the race on the cold cache
                        cache.getValue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "all worker threads must reach the barrier");
            fire.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "all worker threads must finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, invocations.get(),
                "the wrapped supplier must run exactly once for the whole interval, not once per thread");
        assertEquals(1, cache.getValue().intValue(), "the cached value is the single computed result");
    }
}
