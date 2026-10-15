package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.TestRegions.ManualAgenda;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.event.RegionLoadFailedEvent;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.util.retry.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A load under a policy gives up when the policy says so, and nothing is created until the regions are known. */
class RegionLoadPolicyTest extends ManagedRegionFixture {

    private static final IllegalStateException DOWN = new IllegalStateException("backend unreachable");

    private ManualAgenda agenda;
    private ManualClock clock;

    @BeforeEach
    void storeOneRegion() {
        RegionManager writer = manager();
        created(writer.create("arena", WORLD, box(0, 0, 9, 9)));
        writer.flush().join();
        writer.close().join();
        backend.resetCalls();
        agenda = new ManualAgenda().install();
        clock = TestRegions.manualClock();
    }

    /** Runs the agenda until nothing is queued, moving the clock by each retry's wait. */
    private void runUntilIdle(Duration retryWait) {
        for (int round = 0; agenda.size() > 0 && round < 50; round++) {
            agenda.runQueued();
            clock.advance(retryWait);
        }
    }

    @Test
    void itGivesUpAtTheAttemptCeiling() {
        List<RegionLoadFailedEvent> failures = record(RegionLoadFailedEvent.class);
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();

        CompletableFuture<Void> load = manager.load(RetryPolicy.attempts(3).every(Duration.ofSeconds(2)));
        runUntilIdle(Duration.ofSeconds(2));

        CompletionException failed = assertThrows(CompletionException.class, load::join);
        assertEquals(3, backend.callsTo("scanAll"), "exactly the three tries of the policy");
        assertEquals(3, failures.size(), "every failed try is evented");
        String message = failed.getCause().getMessage();
        assertTrue(message.contains("3 tries"), message);
        assertTrue(message.contains("backend unreachable"), message);
        assertSame(DOWN, failed.getCause().getCause());
        assertFalse(manager.isLoaded());
    }

    @Test
    void aRetryThatSucceedsCompletesTheLoad() {
        backend.failNextAll(DOWN);
        RegionManager manager = unloadedManager();

        CompletableFuture<Void> load = manager.load(RetryPolicy.attempts(3).every(Duration.ofSeconds(2)));
        agenda.runQueued();
        assertFalse(load.isDone(), "one failed try of three is not the end");
        agenda.runQueued();

        load.join();
        assertEquals(2, backend.callsTo("scanAll"));
        assertTrue(manager.isLoaded());
        assertNotNull(manager.get("arena"));
    }

    @Test
    void itGivesUpAtTheTimeCeilingByTheRegionClock() {
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();

        CompletableFuture<Void> load = manager.load(RetryPolicy.forever(Duration.ofSeconds(10)).giveUpAfter(Duration.ofSeconds(25)));
        runUntilIdle(Duration.ofSeconds(10));

        assertThrows(CompletionException.class, load::join);
        assertEquals(3, backend.callsTo("scanAll"), "tries at 0, 10 and 20 s; the one at 30 s would pass 25 s");
        assertEquals(Long.valueOf(10_000), agenda.getDelays().get(agenda.getDelays().size() - 1));
    }

    @Test
    void afterGivingUpNothingRetriesUntilANewLoad(TestPlatform platform) {
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();
        CompletableFuture<Void> load = manager.load(RetryPolicy.attempts(2));
        runUntilIdle(Duration.ZERO);
        assertThrows(CompletionException.class, load::join);
        int reads = backend.callsTo("scanAll");

        platform.loadWorld(WORLD);
        assertEquals(0, agenda.size(), "a world load does not revive a load that gave up");
        assertEquals(reads, backend.callsTo("scanAll"));

        backend.stopFailingAll();
        CompletableFuture<Void> again = manager.load(RetryPolicy.attempts(2));
        agenda.runQueued();
        again.join();
        assertNotNull(manager.get("arena"));
    }

    @Test
    void aWorldLoadIsOneMoreTryOfThePolicy(TestPlatform platform) {
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();
        CompletableFuture<Void> load = manager.load(RetryPolicy.attempts(2).every(Duration.ofMinutes(1)));
        agenda.runQueued();

        platform.loadWorld(WORLD);
        agenda.runQueued();   //the timed retry and the world's: only the newer one reads

        assertEquals(2, backend.callsTo("scanAll"));
        assertThrows(CompletionException.class, load::join, "the world's try was the second and last");
    }

    @Test
    void aSecondLoadFollowsTheOneInProgress() {
        RegionManager manager = unloadedManager();

        CompletableFuture<Void> first = manager.load();
        CompletableFuture<Void> second = manager.load();
        agenda.runQueued();

        first.join();
        second.join();
        assertEquals(1, backend.callsTo("scanAll"));
    }

    @Test
    void whenLoadedWaitsForTheFirstLoadThatWorks() {
        backend.failNextAll(DOWN);
        RegionManager manager = unloadedManager();
        CompletableFuture<Void> whenLoaded = manager.whenLoaded();
        assertFalse(manager.isLoaded());

        CompletableFuture<Void> load = manager.load();
        agenda.runQueued();
        assertThrows(CompletionException.class, load::join, "load() answers its first try");
        assertFalse(whenLoaded.isDone(), "a failed try is not the end of waiting");

        agenda.runQueued();
        whenLoaded.join();
        assertTrue(manager.isLoaded());
        assertTrue(manager.whenLoaded().isDone(), "asked after the load, it is already complete");
    }

    @Test
    void aManagerThatStoresNothingStartsLoaded() {
        RegionManager memoryOnly = new RegionManager(owner, null);

        assertTrue(memoryOnly.isLoaded());
        assertTrue(memoryOnly.whenLoaded().isDone());
    }

    @Test
    void closingBeforeTheLoadLeavesNoFuturePending() {
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();
        CompletableFuture<Void> whenLoaded = manager.whenLoaded();
        CompletableFuture<Void> load = manager.load(RetryPolicy.forever(Duration.ofSeconds(30)));
        agenda.runQueued();

        manager.close();

        CompletionException closed = assertThrows(CompletionException.class, whenLoaded::join);
        assertInstanceOf(IllegalStateException.class, closed.getCause());
        assertTrue(closed.getCause().getMessage().contains("closed before its regions loaded"), closed.getCause().getMessage());
        assertThrows(CompletionException.class, load::join);
    }

    // ------------------------------------------------------------------
    //  Create before the load
    // ------------------------------------------------------------------

    private static void assertRefusedBeforeTheLoad(Executable create) {
        IllegalStateException refused = assertThrows(IllegalStateException.class, create);
        assertTrue(refused.getMessage().contains("whenLoaded"), refused.getMessage());
    }

    @Test
    void creatingAStoredNameBeforeTheLoadIsRefusedAndTheStoredOneSurvives() {
        RegionManager manager = unloadedManager();

        assertRefusedBeforeTheLoad(() -> manager.create("arena", WORLD, box(50, 50, 60, 60)));

        CompletableFuture<Void> load = manager.load();
        agenda.runQueued();
        load.join();
        assertEquals(box(0, 0, 9, 9), manager.get("arena").getShape(), "the stored arena is what loaded");
    }

    @Test
    void creatingOverAStoredRegionBeforeTheLoadIsRefused() {
        RegionManager manager = unloadedManager();

        assertRefusedBeforeTheLoad(() -> manager.create("overlapping", WORLD, box(5, 5, 15, 15)));
        assertRefusedBeforeTheLoad(() -> manager.create("fleeting", WORLD, box(5, 5, 15, 15),
                RegionOptions.defaults().withPersistence(Persistence.TRANSIENT)));
    }

    @Test
    void creatingWhileTheLoadFailsIsRefused() {
        backend.failEveryAll(DOWN);
        RegionManager manager = unloadedManager();
        CompletableFuture<Void> load = manager.load();
        agenda.runQueued();
        assertThrows(CompletionException.class, load::join);

        assertRefusedBeforeTheLoad(() -> manager.create("elsewhere", WORLD, box(100, 100, 110, 110)));

        backend.stopFailingAll();
        agenda.runQueued();
        assertTrue(manager.isLoaded());
        created(manager.create("elsewhere", WORLD, box(100, 100, 110, 110)));
    }
}
