package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChunkData<O extends Object> {

    private final WorldData<O> worldData; //father
    private final ChunkPos chunkPos;
    private final Map<BlockPos, O> posDataMap = new LinkedHashMap<>();

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

    public Map<BlockPos, O> getPosDataMap() {
        return posDataMap;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Map Manipulators
    // -----------------------------------------------------------------------------------------------------------------

    public @Nullable O setBlockData(BlockPos blockPos, @Nullable O value){
        if (value == null){
            return posDataMap.remove(blockPos);
        }else {
            return posDataMap.put(blockPos, value);
        }
    }

    public @Nullable O getBlockData(BlockPos blockPos){
        return posDataMap.get(blockPos);
    }

}
