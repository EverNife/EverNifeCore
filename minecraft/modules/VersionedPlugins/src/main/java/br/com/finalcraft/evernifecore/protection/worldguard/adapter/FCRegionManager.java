package br.com.finalcraft.evernifecore.protection.worldguard.adapter;

import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public abstract class FCRegionManager {

    protected final World world;
    protected final RegionManager regionManager;

    private transient Map<String, FCWorldGuardRegion> regionMap; //Populated on demmand

    public FCRegionManager(World world, RegionManager regionManager) {
        this.world = world;
        this.regionManager = regionManager;
    }

    public World getWorld() {
        return world;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public Map<String, FCWorldGuardRegion> getRegions(){
        if (regionMap != null) return regionMap;
        regionMap = new HashMap<>();
        for (Map.Entry<String, ProtectedRegion> entry : this.regionManager.getRegions().entrySet()) {
            regionMap.put(entry.getKey(), WGPlatform.getInstance().wrapRegion(this.world, entry.getValue()));
        }
        return regionMap;
    }

    public @Nullable FCWorldGuardRegion getRegion(String regionID){
        ProtectedRegion protectedRegion = this.regionManager.getRegion(regionID);
        return protectedRegion == null ? null : WGPlatform.getInstance().wrapRegion(world, protectedRegion);
    }

    public abstract FCRegionResultSet getApplicableRegions(Location location);

    public FCRegionResultSet getApplicableRegions(FCWorldGuardRegion region){
        return new FCRegionResultSet(this.world, (RegionResultSet) this.getRegionManager().getApplicableRegions(region.getProtectedRegion()));
    }

}
