package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerData<O extends Object> {

    private final Map<String, WorldData<O>> worldNameMap = new HashMap<>();

    public @Nullable WorldData<O> getWorldData(@NotNull String worldName){
        return worldNameMap.get(worldName);
    }

    public @NotNull WorldData<O> getOrCreateWorldData(@NotNull String worldName){
        return worldNameMap.computeIfAbsent(worldName, s -> new WorldData<>());
    }

    public @Nullable O getBlockData(@NotNull Location location){
        return this.getBlockData(location.getWorld().getName(), BlockPos.from(location));
    }

    public @Nullable O getBlockData(@NotNull String worldName, @NotNull BlockPos blockPos){
        WorldData<O> worldData = getWorldData(worldName);
        return worldData == null ? null : worldData.getBlockData(blockPos);
    }

    public @Nullable O setBlockData(@NotNull Location location, @NotNull O value){
        return this.setBlockData(location.getWorld().getName(), BlockPos.from(location), value);
    }

    public @Nullable O setBlockData(@NotNull String worldName, @NotNull BlockPos blockPos, @NotNull O value){
        return this.getOrCreateWorldData(worldName)
                .setBlockData(blockPos, value);
    }

    public @Nullable O removeBlockData(@NotNull Location location){
        return this.removeBlockData(location.getWorld().getName(), BlockPos.from(location));
    }

    public @Nullable O removeBlockData(@NotNull String worldName, @NotNull BlockPos blockPos){
        WorldData<O> worldData = getWorldData(worldName);
        return worldData == null ? null : worldData.removeBlockData(blockPos);
    }

    public @NotNull Map<String, WorldData<O>> getWorldNameMap() {
        return worldNameMap;
    }

    public @NotNull Collection<WorldData<O>> getAllWorldData(){
        return worldNameMap.size() == 0 ? Collections.EMPTY_LIST : worldNameMap.values();
    }

}
