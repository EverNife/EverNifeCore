package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChunkData<O extends Object> {

    protected final WorldData<O> worldData; //father
    protected final ChunkPos chunkPos;
    protected final Map<BlockPos, BlockMetaData<O>> posDataMap = new LinkedHashMap<>();

    public ChunkData(@Nullable WorldData<O> worldData, ChunkPos chunkPos) {
        this.worldData = worldData;
        this.chunkPos = chunkPos;
    }

    public @Nullable WorldData getWorldData() {
        return worldData;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public Map<BlockPos, BlockMetaData<O>> getPosDataMap() {
        return posDataMap;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Map Manipulators
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable BlockMetaData<O> setBlockData(BlockPos blockPos, @Nullable O value){
        BlockMetaData<O> newValue = null;
        BlockMetaData<O> previousBlockMeta = value == null
                ? posDataMap.remove(blockPos)
                : posDataMap.put(blockPos, newValue = new BlockMetaData<>(blockPos, this, value));

        if (previousBlockMeta != null && this.worldData != null && this.worldData.serverData != null){
            this.getWorldData().getServerData().onBlockMetaRemove(previousBlockMeta);
        }

        if (newValue != null && this.worldData != null && this.worldData.serverData != null){
            this.getWorldData().getServerData().onBlockMetaSet(newValue);
        }

        return previousBlockMeta;
    }

    public @Nullable BlockMetaData<O> getBlockMetaData(BlockPos blockPos){
        return posDataMap.get(blockPos);
    }

    public @Nullable O getBlockData(BlockPos blockPos){
        BlockMetaData<O> blockMetaData = getBlockMetaData(blockPos);
        return blockMetaData == null ? null : blockMetaData.getValue();
    }

    public List<BlockMetaData<O>> getAllBlockData(){
        List<BlockMetaData<O>> allBlockData = new ArrayList<>();
        allBlockData.addAll(this.posDataMap.values());
        return allBlockData;
    }

}
