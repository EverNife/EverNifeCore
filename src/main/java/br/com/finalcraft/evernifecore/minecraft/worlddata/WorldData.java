package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldData<O extends Object> {

    protected final ServerData<O> serverData; //father
    protected final String worldName;
    protected final Map<ChunkPos, ChunkData<O>> chunkDataMap = new LinkedHashMap<>();

    public WorldData(ServerData<O> serverData, String worldName) {
        this.serverData = serverData;
        this.worldName = worldName;
    }

    public ServerData<O> getServerData() {
        return serverData;
    }

    public String getWorldName() {
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

    public ChunkData<O> getOrCreateChunkData(ChunkPos chunkPos){
        return chunkDataMap.computeIfAbsent(chunkPos, c -> new ChunkData<>(this, chunkPos));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Utility Methods to prevent code replication
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable BlockMetaData<O> setBlockData(BlockPos blockPos, @Nullable O value){
        ChunkPos chunkPos = blockPos.getChunkPos();
        ChunkData<O> chunkData = value == null
                ? getChunkData(chunkPos) //we are removing a value
                : getOrCreateChunkData(chunkPos);

        if (chunkData == null){//This can be null only when 'value' is null as well, so lets early return
            return null;
        }

        return chunkData.setBlockData(blockPos, value);
    }

    public @Nullable BlockMetaData<O> getBlockMetaData(BlockPos blockPos){
        ChunkData<O> chunkData = getChunkData(blockPos.getChunkPos());
        return chunkData == null ? null : chunkData.getBlockMetaData(blockPos);
    }

    public @Nullable O getBlockData(BlockPos blockPos){
        BlockMetaData<O> blockMetaData = getBlockMetaData(blockPos);
        return blockMetaData == null ? null : blockMetaData.getValue();
    }

    public List<BlockMetaData<O>> getAllBlockData(){
        List<BlockMetaData<O>> allBlockData = new ArrayList<>();
        for (ChunkData<O> value : chunkDataMap.values()) {
            allBlockData.addAll(value.getAllBlockData());
        }
        return allBlockData;
    }

}
