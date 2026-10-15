package br.com.finalcraft.evernifecore.minecraft.protection.worldguard;

import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.adapter.FCRegionManager;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.adapter.FCRegionResultSet;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.HashMapIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FCRegionManagerTest {

    @Test
    void getRegionsSeesARegionAddedAndRemovedAfterTheFirstCall() {
        RegionManager handle = new RegionManager(new MemoryRegionDatabase(), new HashMapIndex.Factory(), new SimpleFlagRegistry());
        handle.addRegion(new GlobalProtectedRegion("first"));
        FCRegionManager manager = new FakeRegionManager(handle);

        assertEquals(1, manager.getRegions().size());

        handle.addRegion(new GlobalProtectedRegion("second"));
        assertTrue(manager.getRegions().containsKey("second"), "a region created after the first call: " + manager.getRegions().keySet());

        handle.removeRegion("first");
        assertFalse(manager.getRegions().containsKey("first"), "a removed region is gone: " + manager.getRegions().keySet());
    }

    private static final class FakeRegionManager extends FCRegionManager {

        FakeRegionManager(RegionManager handle) {
            super(null, handle);
        }

        @Override
        protected FCWorldGuardRegion wrap(ProtectedRegion region) {
            return (FCWorldGuardRegion) Proxy.newProxyInstance(FCWorldGuardRegion.class.getClassLoader(),
                    new Class<?>[]{FCWorldGuardRegion.class}, (proxy, method, args) -> region);
        }

        @Override
        public FCRegionResultSet getApplicableRegions(Location location) {
            throw new UnsupportedOperationException();
        }
    }
}
