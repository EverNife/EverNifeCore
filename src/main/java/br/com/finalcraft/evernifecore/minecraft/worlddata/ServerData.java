package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServerData<O extends Object> {

    private final Map<String, WorldData<O>> worldDataMap = new LinkedHashMap<>();

    public Map<String, WorldData<O>> getWorldDataMap() {
        return worldDataMap;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Map Manipulators
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable WorldData<O> setWorldData(String worldName, WorldData<O> worldData){
        return worldDataMap.put(worldName, worldData);
    }

    public @Nullable WorldData<O> getWorldData(String worldName){
        return worldDataMap.get(worldName);
    }

    public WorldData<O> getOrCreateWorldData(String worldName){
        return worldDataMap.computeIfAbsent(worldName, s -> new WorldData<>(this, worldName));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Utility Methods to prevent code replication
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable O setBlockData(String worldName, BlockPos blockPos, @Nullable O value){
        WorldData<O> worldData = value == null
                ? getWorldData(worldName) //we are removing a value
                : getOrCreateWorldData(worldName);

        if (worldData == null){//This can be null only when 'value' is null as well, so lets early return
            return null;
        }

        return worldData.setBlockData(blockPos, value);
    }

    public @Nullable O setBlockData(Location location, @Nullable O value){
        return this.setBlockData(location.getWorld().getName(), BlockPos.from(location), value);
    }

    public @Nullable O getBlockData(String worldName, BlockPos blockPos){
        WorldData<O> worldData = getWorldData(worldName);
        return worldData == null ? null : worldData.getBlockData(blockPos);
    }

    public @Nullable O getBlockData(Location location){
        return this.getBlockData(location.getWorld().getName(), BlockPos.from(location));
    }

}
