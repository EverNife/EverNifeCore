package br.com.finalcraft.evernifecore.minecraft.protection.worldguard.adapter;

import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.WGPlatform;
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

    /** A snapshot of the world's regions at call time, wrapped; regions added or removed later show on the next call. */
    public Map<String, FCWorldGuardRegion> getRegions(){
        Map<String, FCWorldGuardRegion> regionMap = new HashMap<>();
        for (Map.Entry<String, ProtectedRegion> entry : this.regionManager.getRegions().entrySet()) {
            regionMap.put(entry.getKey(), wrap(entry.getValue()));
        }
        return regionMap;
    }

    public @Nullable FCWorldGuardRegion getRegion(String regionID){
        ProtectedRegion protectedRegion = this.regionManager.getRegion(regionID);
        return protectedRegion == null ? null : wrap(protectedRegion);
    }

    protected FCWorldGuardRegion wrap(ProtectedRegion protectedRegion){
        return WGPlatform.getInstance().wrapRegion(world, protectedRegion);
    }

    public abstract FCRegionResultSet getApplicableRegions(Location location);

    public FCRegionResultSet getApplicableRegions(FCWorldGuardRegion region){
        return new FCRegionResultSet(this.world, (RegionResultSet) this.getRegionManager().getApplicableRegions(region.getProtectedRegion()));
    }

}
