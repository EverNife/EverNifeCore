package br.com.finalcraft.evernifecore.protection.worldguard.adapter;

import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.Collection;
import java.util.List;

public class FCRegionResultSet {

    private final World world;
    private final RegionResultSet regionResultSet;

    private transient List<FCWorldGuardRegion> fcWorldGuardRegions;//Populated on demand

    public FCRegionResultSet(World world, RegionResultSet regionResultSet) {
        this.world = world;
        this.regionResultSet = regionResultSet;
    }

    public boolean testState(RegionAssociable regionAssociable, StateFlag... stateFlags) {
        return regionResultSet.testState(regionAssociable, stateFlags);
    }

    public StateFlag.State queryState(RegionAssociable regionAssociable, StateFlag... stateFlags) {
        return regionResultSet.queryState(regionAssociable, stateFlags);
    }

    public <V> V queryValue(RegionAssociable regionAssociable, Flag<V> flag) {
        return regionResultSet.queryValue(regionAssociable, flag);
    }

//    MapFlags are not present on WorldGuard 6.1, if i ever need this function, i will create a workaround for this

//    public <V, K> V queryMapValue(RegionAssociable regionAssociable, MapFlag<K, V> mapFlag, K k) {
//        return regionResultSet.queryMapValue(regionAssociable, mapFlag, k);
//    }
//
//    public <V, K> V queryMapValue(RegionAssociable regionAssociable, MapFlag<K, V> mapFlag, K k, Flag<V> flag) {
//        return regionResultSet.queryMapValue(regionAssociable, mapFlag, k, flag);
//    }

    public <V> Collection<V> queryAllValues(RegionAssociable regionAssociable, Flag<V> flag) {
        return regionResultSet.queryAllValues(regionAssociable, flag);
    }

    public boolean isOwnerOfAll(OfflinePlayer player) {
        return regionResultSet.isOwnerOfAll(WGPlatform.getInstance().wrapPlayer(player));
    }

    public boolean isMemberOfAll(OfflinePlayer player) {
        return regionResultSet.isMemberOfAll(WGPlatform.getInstance().wrapPlayer(player));
    }

    public int size() {
        return regionResultSet.size();
    }

    public boolean isEmpty(){
        return this.size() == 0;
    }

    public boolean contains(String regionID){
        return this.getRegion(regionID) != null;
    }

    public FCWorldGuardRegion getRegion(String regionID){
        for (FCWorldGuardRegion region : getRegions()) {
            if (region.getId().equalsIgnoreCase(regionID)){
                return region;
            }
        }
        return null;
    }

    public List<FCWorldGuardRegion> getRegions() {
        if (fcWorldGuardRegions == null){
            fcWorldGuardRegions = regionResultSet.getRegions().stream()
                    .map(region -> WGPlatform.getInstance().wrapRegion(world, region))
                    .collect(ImmutableList.toImmutableList());
        }
        return fcWorldGuardRegions;
    }

}
