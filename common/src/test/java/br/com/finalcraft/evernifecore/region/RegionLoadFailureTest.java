package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.TestRegions.ManualAgenda;
import br.com.finalcraft.evernifecore.region.event.RegionLoadFailedEvent;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A load that fails leaves the owner with no regions, says so, and tries again until it works. */
class RegionLoadFailureTest extends ManagedRegionFixture {

    private ManualAgenda agenda;

    @BeforeEach
    void storeOneRegion() {
        RegionManager writer = manager();
        created(writer.create("stored", WORLD, box(0, 0, 9, 9)));
        writer.flush().join();
        writer.close().join();
        agenda = new ManualAgenda().install();
    }

    @Test
    void aFailedLoadIsEventedFailsOpenAndIsRetried() {
        List<RegionLoadFailedEvent> failures = record(RegionLoadFailedEvent.class);
        RegionManager.setRetryDelayMillis(10);
        backend.failNextAll(new IllegalStateException("backend unreachable"));
        RegionManager manager = manager();

        CompletableFuture<Void> load = manager.load();
        agenda.runQueued();
        CompletionException failed = assertThrows(CompletionException.class, load::join);

        assertEquals("backend unreachable", failed.getCause().getMessage());
        assertEquals(1, failures.size());
        assertSame(owner, failures.get(0).getOwner());
        assertNull(failures.get(0).getWorld(), "the owner's whole load failed, not one world's");
        assertEquals(Long.valueOf(10), agenda.getDelays().get(agenda.getDelays().size() - 1), "the retry waits the retry delay");
        assertNull(manager.get("stored"));

        agenda.runQueued();
        assertNotNull(manager.get("stored"), "the retry loads what the failed load could not");
    }

    @Test
    void untilTheRetryNothingOfTheOwnerIsIndexed() {
        backend.failNextAll(new IllegalStateException("backend unreachable"));

        CompletableFuture<Void> load = manager().load();
        agenda.runQueued();

        assertThrows(CompletionException.class, load::join);
        assertTrue(RegionLookup.get().byOwner(owner).isEmpty(), "fail-open: a region that did not load denies nothing");
    }

    @Test
    void aWorldLoadRetriesAFailedLoad(TestPlatform platform) {
        backend.failNextAll(new IllegalStateException("backend unreachable"));
        RegionManager manager = manager();
        CompletableFuture<Void> load = manager.load();
        agenda.runQueued();
        assertThrows(CompletionException.class, load::join);
        agenda.discardQueued();   //the timed retry: only the world load may bring the regions back here

        platform.loadWorld(WORLD);
        agenda.runQueued();

        assertNotNull(manager.get("stored"));
    }
}
