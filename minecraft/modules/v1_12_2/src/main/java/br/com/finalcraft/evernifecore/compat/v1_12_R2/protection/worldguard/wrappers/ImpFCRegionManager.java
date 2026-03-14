package br.com.finalcraft.evernifecore.compat.v1_12_R2.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionResultSet;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Location;
import org.bukkit.World;

public class ImpFCRegionManager extends FCRegionManager {

    public ImpFCRegionManager(World world, RegionManager regionManager) {
        super(world, regionManager);
    }

    @Override
    public FCRegionResultSet getApplicableRegions(Location location) {
        return new FCRegionResultSet(this.world, (RegionResultSet) this.getRegionManager().getApplicableRegions(location));
    }

}
