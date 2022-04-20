package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WorldData<O extends Object> {

    private final Map<ChunkPos, ChunkData<O>> chunkPosMap = new HashMap<>();

    public @Nullable ChunkData<O> getChunkData(@NotNull ChunkPos chunkPos){
        return chunkPosMap.get(chunkPos);
    }

    public @NotNull ChunkData<O> getOrCreateChunkData(@NotNull ChunkPos chunkPos){
        return chunkPosMap.computeIfAbsent(chunkPos, chunkPos1 -> new ChunkData<>());
    }

    public @Nullable O getBlockData(@NotNull BlockPos blockPos){
        ChunkData<O> chunkData = getChunkData(blockPos.getChunkPos());
        return chunkData == null ? null : chunkData.getBlockData(blockPos);
    }

    public @Nullable O setBlockData(@NotNull BlockPos blockPos, @Nullable O value){
        return getOrCreateChunkData(blockPos.getChunkPos()).setBlockData(blockPos, value);
    }

    public @Nullable O removeBlockData(@NotNull BlockPos blockPos){
        ChunkData<O> chunkData = getChunkData(blockPos.getChunkPos());
        return chunkData == null ? null : chunkData.removeBlockData(blockPos);
    }

    public @Nullable Map<ChunkPos, ChunkData<O>> getChunkPosMap() {
        return chunkPosMap;
    }

    public @NotNull Collection<ChunkData<O>> getAllChunkData(){
        return chunkPosMap.size() == 0 ? Collections.EMPTY_LIST : chunkPosMap.values();
    }

}
