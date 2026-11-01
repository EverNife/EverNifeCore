package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.storage.Persistence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A manager opened without storage: transient regions only, and it shares the owner's slot with a stored one. */
class RegionManagerTransientTest extends ManagedRegionFixture {

    private static final RegionOptions TRANSIENT = RegionOptions.defaults().withPersistence(Persistence.TRANSIENT);

    @Test
    void aManagerWithoutStorageIsLoadedAndCreatesTransientRegions() {
        RegionManager manager = RegionManager.of(owner);

        assertNull(manager.getStorage());
        assertTrue(manager.isLoaded());
        CreationResult result = manager.create("reach-the-tower", WORLD, box(0, 0, 10, 10), TRANSIENT);
        assertTrue(result.isCreated(), result.getRefusal());
        assertEquals(Persistence.TRANSIENT, result.getRegion().getPersistence());
    }

    @Test
    void aPersistentRegionIsARefusalThatSaysHowToStoreIt() {
        RegionManager manager = RegionManager.of(owner);

        CreationResult result = manager.create("reach-the-tower", WORLD, box(0, 0, 10, 10));

        assertFalse(result.isCreated());
        assertTrue(result.getRefusal().contains("TRANSIENT"), result.getRefusal());
        assertTrue(result.getRefusal().contains("RegionManager.of(owner, storage)"), result.getRefusal());
    }

    @Test
    void openingOnAStorageOverAManagerWithoutStorageThrowsUntilItCloses() {
        RegionManager.of(owner);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> RegionManager.of(owner, storage));

        assertTrue(refused.getMessage().contains("close()"), refused.getMessage());
    }

    @Test
    void openingWithoutStorageOverAStoredManagerReturnsIt() {
        RegionManager stored = RegionManager.of(owner, storage);

        assertSame(stored, RegionManager.of(owner));
    }

    @Test
    void closeOwnedByClosesTheManagerWithoutStorage() {
        RegionManager manager = RegionManager.of(owner);

        RegionManager.closeOwnedBy(owner);

        assertTrue(manager.isClosed());
        assertNull(RegionManager.ofIfPresent(owner));
    }
}
