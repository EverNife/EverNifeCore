package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import org.bukkit.Location;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ServerData<O extends Object> {

    protected final Map<String, WorldData<O>> worldDataMap = new LinkedHashMap<>();

    public Map<String, WorldData<O>> getWorldDataMap() {
        return worldDataMap;
    }

    public abstract void onBlockMetaSet(BlockMetaData<O> blockMetaData);
    public abstract void onBlockMetaRemove(BlockMetaData<O> blockMetaData);

    // -----------------------------------------------------------------------------------------------------------------
    //  Map Manipulators
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable WorldData<O> setWorldData(String worldName, WorldData<O> worldData){
        return worldDataMap.put(worldName, worldData);
    }

    public @Nullable WorldData<O> getWorldData(String worldName){
        return worldDataMap.get(worldName);
    }

    public synchronized WorldData<O> getOrCreateWorldData(String worldName){
        return worldDataMap.computeIfAbsent(worldName, s -> new WorldData<>(this, worldName));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Utility Methods to prevent code replication
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable BlockMetaData<O> setBlockData(String worldName, BlockPos blockPos, @Nullable O value){
        WorldData<O> worldData = value == null
                ? getWorldData(worldName) //we are removing a value
                : getOrCreateWorldData(worldName);

        if (worldData == null){//This can be null only when 'value' is null as well, so lets early return
            return null;
        }

        return worldData.setBlockData(blockPos, value);
    }

    public @Nullable BlockMetaData<O> setBlockData(Location location, @Nullable O value){
        return this.setBlockData(location.getWorld().getName(), BlockPos.from(location), value);
    }

    public @Nullable BlockMetaData<O> getBlockMetaData(String worldName, BlockPos blockPos){
        WorldData<O> worldData = getWorldData(worldName);
        return worldData == null ? null : worldData.getBlockMetaData(blockPos);
    }

    public @Nullable O getBlockData(String worldName, BlockPos blockPos){
        BlockMetaData<O> blockMetaData = getBlockMetaData(worldName, blockPos);
        return blockMetaData == null ? null : blockMetaData.getValue();
    }

    public @Nullable BlockMetaData<O> getBlockMetaData(Location location){
        return this.getBlockMetaData(location.getWorld().getName(), BlockPos.from(location));
    }

    public @Nullable O getBlockData(Location location){
        return this.getBlockData(location.getWorld().getName(), BlockPos.from(location));
    }

    public List<BlockMetaData<O>> getAllBlockMetaData(){
        List<BlockMetaData<O>> allBlockData = new ArrayList<>();
        for (WorldData<O> worldData : worldDataMap.values()) {
            allBlockData.addAll(worldData.getAllBlockData());
        }
        return allBlockData;
    }

}
