package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        RegionAgenda.set((task, delayMillis) -> held.add(task));
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
}
