package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code join()}/{@code get()} that waits on a pending core future on the server thread logs where and how long; a
 * complete future and a wait on another thread stay quiet. The test's own thread plays the server thread.
 */
@ECoreTest
class BlockingJoinWarningTest {

    private static final String WARNING = "blocked the server thread";

    @TempDir
    Path tempDir;

    @AfterEach
    void disarm() {
        SlowSection.gate = null;
    }

    /** A section whose stored row takes as long to load as the test says: its decode waits on {@link #gate}. */
    public static class SlowSection extends PDSection {
        static volatile CountDownLatch gate;
        static volatile Thread server;

        public int visits;

        public SlowSection() {
            CountDownLatch armed = gate;
            if (armed != null && Thread.currentThread() != server) {
                await(armed);
            }
        }
    }

    @Test
    void aJoinOnAPendingSectionNamesTheCallerAndTheWait() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.localFile()).sections(SlowSection.class).boot(tempDir)) {
            UUID uuid = storedAndEvicted();
            CompletableFuture<SlowSection> pending = PlayerController.getPDSection(uuid, SlowSection.class);
            assertFalse(pending.isDone(), "the reload waits on the gate");

            List<String> logged = Logs.capture(() -> {
                releaseOnceBlocked(SlowSection.gate::countDown);
                pending.join();
            });

            String line = warningLine(logged);
            assertTrue(line.contains("join()"), line);
            assertTrue(line.contains("BlockingJoinWarningTest.lambda$aJoinOnAPendingSectionNamesTheCallerAndTheWait"), line);
            assertTrue(line.matches(".* for \\d+ ms .*"), line);
        }
    }

    @Test
    void aGetOnAPendingSectionWarnsTheSameWay() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.localFile()).sections(SlowSection.class).boot(tempDir)) {
            UUID uuid = storedAndEvicted();
            CompletableFuture<SlowSection> pending = PlayerController.getPDSection(uuid, SlowSection.class);
            assertFalse(pending.isDone(), "the reload waits on the gate");

            List<String> logged = Logs.capture(() -> {
                releaseOnceBlocked(SlowSection.gate::countDown);
                try {
                    pending.get();
                } catch (InterruptedException | ExecutionException failure) {
                    throw new AssertionError(failure);
                }
            });

            String line = warningLine(logged);
            assertTrue(line.contains("get()"), line);
            assertTrue(line.contains("BlockingJoinWarningTest.lambda$aGetOnAPendingSectionWarnsTheSameWay"), line);
        }
    }

    @Test
    void aCompleteFutureJoinsWithoutAWord() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.localFile()).sections(SlowSection.class).boot(tempDir)) {
            UUID uuid = UUID.randomUUID();
            PlayerController.handleLogin(uuid, "cached").join();
            PlayerController.getPDSection(uuid, SlowSection.class).join();
            CompletableFuture<SlowSection> cached = PlayerController.getPDSection(uuid, SlowSection.class);
            assertTrue(cached.isDone(), "a cached section answers at once");

            List<String> logged = Logs.capture(cached::join);

            assertTrue(logged.stream().noneMatch(line -> line.contains(WARNING)), String.valueOf(logged));
        }
    }

    @Test
    void aJoinOffTheServerThreadStaysQuiet() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        CompletableFuture<String> pending = ECScheduler.supplyAsyncFuture(() -> {
            await(gate);
            return "done";
        });

        List<String> logged = Logs.capture(() -> {
            Thread worker = new Thread(pending::join, "not-the-server");
            worker.start();
            waitUntilBlocked(worker);
            gate.countDown();
            try {
                worker.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException interrupted) {
                throw new AssertionError(interrupted);
            }
        });

        assertEquals("done", pending.getNow(null));
        assertTrue(logged.stream().noneMatch(line -> line.contains(WARNING)), String.valueOf(logged));
    }

    @Test
    void whatAPluginDerivesWithThenApplyWarnsToo() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.localFile()).sections(SlowSection.class).boot(tempDir)) {
            UUID uuid = storedAndEvicted();
            CompletableFuture<Integer> visits = PlayerController.getPDSection(uuid, SlowSection.class).thenApply(section -> section.visits);
            assertFalse(visits.isDone(), "the reload waits on the gate");

            List<String> logged = Logs.capture(() -> {
                releaseOnceBlocked(SlowSection.gate::countDown);
                assertEquals(7, (int) visits.join());
            });

            String line = warningLine(logged);
            assertTrue(line.contains("BlockingJoinWarningTest.lambda$whatAPluginDerivesWithThenApplyWarnsToo"), line);
        }
    }

    @Test
    void aThousandWaitsFromOnePlaceMakeOneLinePerWindowAndTheNextOneCountsThem() {
        TestClock.acquire();
        try {
            List<String> logged = Logs.capture(() -> {
                for (int i = 0; i <= 1000; i++) {
                    if (i == 1000) {
                        TestClock.advance(Duration.ofSeconds(BlockingJoinWarning.WINDOW_SECONDS));
                    }
                    CompletableFuture<Integer> pending = BlockingJoinWarning.newFuture();
                    int value = i;
                    releaseOnceBlocked(() -> pending.complete(value));
                    assertEquals(i, (int) pending.join());
                }
            });

            List<String> lines = logged.stream().filter(line -> line.contains(WARNING)).distinct().collect(Collectors.toList());
            assertEquals(2, lines.size(), "one line in the first window, one when the next opens: " + lines);
            assertFalse(lines.get(0).contains("more time"), lines.get(0));
            assertTrue(lines.get(1).contains("and 999 more time(s) from there"), lines.get(1));
        } finally {
            TestClock.release();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** A player whose stored section is out of the cache, so the next read goes to the backend and meets the armed gate. */
    private static UUID storedAndEvicted() {
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "slow").join();
        SlowSection section = PlayerController.getPDSection(uuid, SlowSection.class).join();
        section.visits = 7;
        section.forceSave().join();
        PlayerController.invalidatePDSection(SlowSection.class, uuid);
        SlowSection.server = Thread.currentThread();
        SlowSection.gate = new CountDownLatch(1);
        return uuid;
    }

    private static String warningLine(List<String> logged) {
        return logged.stream().filter(line -> line.contains(WARNING)).findFirst()
                .orElseThrow(() -> new AssertionError("no blocking warning in " + logged));
    }

    /** Runs {@code release} from another thread once the calling thread is parked - so the wait it is about to start really waits. */
    private static void releaseOnceBlocked(Runnable release) {
        Thread waiter = Thread.currentThread();
        Thread releaser = new Thread(() -> {
            waitUntilBlocked(waiter);
            release.run();
        }, "releaser");
        releaser.setDaemon(true);
        releaser.start();
    }

    private static void waitUntilBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(thread.getName() + " never blocked");
            }
            Thread.yield();
        }
    }

    private static void await(CountDownLatch gate) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never opened the gate");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
