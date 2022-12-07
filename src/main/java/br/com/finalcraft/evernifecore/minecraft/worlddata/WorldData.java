package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WorldData<O extends Object> {

    private final ServerData<O> serverData;
    private final String worldName;
    private final Map<ChunkPos, ChunkData<O>> chunkDataMap = new LinkedHashMap<>();

    public WorldData(ServerData<O> serverData, String worldName) {
        this.serverData = serverData;
        this.worldName = worldName;
    }

    public ServerData<O> getServerData() {
        return serverData;
    }

    public @NotNull String getWorldName() {
        return worldName;
    }

    public @Nullable Map<ChunkPos, ChunkData<O>> getChunkDataMap() {
        return chunkDataMap;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Map Manipulators
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable ChunkData<O> setChunkData(ChunkPos chunkPos, ChunkData<O> chunkData){
        return chunkDataMap.put(chunkPos, chunkData);
    }

    public @Nullable ChunkData<O> getChunkData(ChunkPos chunkPos){
        return chunkDataMap.get(chunkPos);
    }

    public @NotNull ChunkData<O> getOrCreateChunkData(ChunkPos chunkPos){
        return chunkDataMap.computeIfAbsent(chunkPos, c -> new ChunkData<>(this, chunkPos));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Utility Methods to prevent code replication
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable O setBlockData(BlockPos blockPos, @Nullable O value){
        ChunkPos chunkPos = blockPos.getChunkPos();
        ChunkData<O> chunkData = value == null
                ? getChunkData(chunkPos) //we are removing a value
                : getOrCreateChunkData(chunkPos);

        if (chunkData == null && value == null){
            return null;
        }

        return chunkData.setBlockData(blockPos, value);
    }

    public @Nullable O getBlockData(@NotNull BlockPos blockPos){
        ChunkData<O> chunkData = getChunkData(blockPos.getChunkPos());
        return chunkData == null ? null : chunkData.getBlockData(blockPos);
    }

}
