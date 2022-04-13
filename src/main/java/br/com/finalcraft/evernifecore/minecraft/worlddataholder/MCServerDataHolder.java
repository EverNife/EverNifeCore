package br.com.finalcraft.evernifecore.minecraft.worlddataholder;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MCServerDataHolder<O extends Object> {

    private final Map<String, MCWorldDataHolder<O>> worldNameMap = new HashMap<>();

    public @Nullable MCWorldDataHolder<O> getWorldData(@NotNull String worldName){
        return worldNameMap.get(worldName);
    }

    public @NotNull MCWorldDataHolder<O> getOrCreateWorldData(@NotNull String worldName){
        return worldNameMap.computeIfAbsent(worldName, s -> new MCWorldDataHolder<>());
    }

    public @Nullable O getBlockData(@NotNull Location location){
        return this.getBlockData(location.getWorld().getName(), BlockPos.from(location));
    }

    public @Nullable O getBlockData(@NotNull String worldName, @NotNull BlockPos blockPos){
        MCWorldDataHolder<O> worldData = getWorldData(worldName);
        if (worldData == null) return null;
        return worldData.getBlockData(blockPos);
    }

    public void setBlockData(@NotNull Location location, @NotNull O value){
        this.setBlockData(location.getWorld().getName(), BlockPos.from(location), value);
    }

    public void setBlockData(@NotNull String worldName, @NotNull BlockPos blockPos, @NotNull O value){
        this.getOrCreateWorldData(worldName)
                .setBlockData(blockPos, value);
    }

    public @NotNull Map<String, MCWorldDataHolder<O>> getWorldNameMap() {
        return worldNameMap;
    }

}
