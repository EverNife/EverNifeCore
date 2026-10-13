package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When its owner shuts down a manager writes and lets go of its regions, and never closes the owner's storage. */
class RegionOwnerShutdownTest extends ManagedRegionFixture {

    @Test
    void closingTheOwnerWritesDropsItsRegionsAndKeepsItsStorageOpen() {
        created(manager().create("a", WORLD, box(0, 0, 9, 9)));
        created(manager().create("b", "nether", box(0, 0, 9, 9)));
        List<RegionRemovedEvent> removed = record(RegionRemovedEvent.class);

        RegionManager.closeOwnedBy(owner);

        assertEquals(2, removed.size());
        for (RegionRemovedEvent event : removed) {
            assertEquals(RegionRemovedEvent.Cause.OWNER_DISABLED, event.getCause());
        }
        assertTrue(RegionLookup.get().byOwner(owner).isEmpty());
        assertNull(RegionManager.ofIfPresent(owner));
        assertTrue(storage.isOpen());
        assertEquals(2, backend.callsTo("save"), "both regions were written before letting go");
    }

    @Test
    void discardingAnOwnerWhoseStorageIsGoneWritesNothing() {
        Queue<Runnable> held = new ArrayDeque<>();
        RegionScheduler.set((task, delayMillis) -> held.add(task));
        created(manager().create("a", WORLD, box(0, 0, 9, 9)));
        List<RegionRemovedEvent> removed = record(RegionRemovedEvent.class);

        RegionManager.discardOwnedBy(owner);
        while (!held.isEmpty()) {
            held.poll().run();
        }

        assertEquals(1, removed.size());
        assertEquals(RegionRemovedEvent.Cause.OWNER_DISABLED, removed.get(0).getCause());
        assertNull(RegionManager.ofIfPresent(owner));
        assertEquals(0, backend.callsTo("save"));
    }

    @Test
    void closingEverythingStopsTheGlobalCountAndTracker() {
        List<ECListenerWatch> before = new ArrayList<>(ECEventBus.global().getListenerWatches());
        EventSourceUsage usage = EventSourceUsage.get();
        RegionMovementTracker tracker = RegionMovementTracker.get();
        List<ECListenerWatch> opened = new ArrayList<>(ECEventBus.global().getListenerWatches());
        opened.removeAll(before);
        assertFalse(opened.isEmpty(), "the count watches who listens to region enter and leave");

        RegionManager.closeAll();

        List<ECListenerWatch> left = ECEventBus.global().getListenerWatches();
        for (ECListenerWatch watch : opened) {
            assertFalse(left.contains(watch), "still on the global bus: " + watch);
        }
        assertNotSame(usage, EventSourceUsage.get(), "the next use starts a fresh count");
        assertNotSame(tracker, RegionMovementTracker.get(), "the next use starts a fresh tracker");
    }
}
