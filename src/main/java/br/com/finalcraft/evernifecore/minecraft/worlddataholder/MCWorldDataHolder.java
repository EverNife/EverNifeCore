package br.com.finalcraft.evernifecore.minecraft.worlddataholder;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MCWorldDataHolder<O extends Object> {

    private final Map<ChunkPos, MCChunkDataHolder<O>> chunkPosMap = new HashMap<>();

    public @Nullable MCChunkDataHolder<O> getChunkData(@NotNull ChunkPos chunkPos){
        return chunkPosMap.get(chunkPos);
    }

    public @NotNull MCChunkDataHolder<O> getOrCreateChunkData(@NotNull ChunkPos chunkPos){
        return chunkPosMap.computeIfAbsent(chunkPos, chunkPos1 -> new MCChunkDataHolder<>());
    }

    public @Nullable O getBlockData(@NotNull BlockPos blockPos){
        MCChunkDataHolder<O> chunkData = getChunkData(blockPos.getChunkPos());
        if (chunkData == null) return null;
        return chunkData.getBlockData(blockPos);
    }

    public @Nullable O setBlockData(@NotNull BlockPos blockPos, @Nullable O value){
        return getOrCreateChunkData(blockPos.getChunkPos()).setBlockData(blockPos, value);
    }

    public @Nullable O removeBlockData(@NotNull BlockPos blockPos){
        MCChunkDataHolder<O> chunkData = getChunkData(blockPos.getChunkPos());
        if (chunkData == null) return null;
        return chunkData.removeBlockData(blockPos);
    }

    public @Nullable Map<ChunkPos, MCChunkDataHolder<O>> getChunkPosMap() {
        return chunkPosMap;
    }

}
