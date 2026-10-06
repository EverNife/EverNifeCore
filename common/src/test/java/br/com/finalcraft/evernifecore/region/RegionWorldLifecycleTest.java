package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A world that unloads takes its transient regions with it and gets its persistent ones written. */
class RegionWorldLifecycleTest extends ManagedRegionFixture {

    @Test
    void unloadingAWorldRemovesOnlyItsTransientRegions(TestPlatform platform) {
        RegionManager manager = manager();
        RegionOptions fleeting = RegionOptions.defaults().withPersistence(Persistence.TRANSIENT);
        Region transientHere = created(manager.create("fleeting_here", "w", box(0, 0, 9, 9), fleeting));
        Region lasting = created(manager.create("lasting_here", "w", box(20, 20, 29, 29)));
        created(manager.create("fleeting_there", "elsewhere", box(0, 0, 9, 9), fleeting));
        manager.flush().join();
        List<RegionRemovedEvent> removed = record(RegionRemovedEvent.class);
        backend.resetCalls();
        lasting.addTag("changed");   //pending when the world goes

        platform.unloadWorld("w");
        manager.flush().join();

        assertEquals(1, removed.size());
        assertSame(transientHere, removed.get(0).getRegion());
        assertEquals(RegionRemovedEvent.Cause.WORLD_UNLOADED, removed.get(0).getCause());
        assertNull(manager.get("fleeting_here"));
        assertNotNull(manager.get("lasting_here"), "a persistent region stays indexed");
        assertNotNull(manager.get("fleeting_there"), "another world's transient region stays");
        assertEquals(Collections.singletonList(lasting), RegionLookup.get().at("w", 25, 50, 25));
        assertEquals(1, backend.callsTo("save"), "the persistent region of the world was written");
    }
}
