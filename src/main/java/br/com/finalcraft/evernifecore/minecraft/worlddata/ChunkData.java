package br.com.finalcraft.evernifecore.minecraft.worlddata;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChunkData<O extends Object> {

    private final Map<BlockPos, O> blockPosMap = new HashMap<>();

    public @Nullable O getBlockData(@Nullable BlockPos blockPos){
        return blockPosMap.get(blockPos);
    }

    public @Nullable O setBlockData(@NotNull BlockPos blockPos, @NotNull O value){
        return blockPosMap.put(blockPos, value);
    }

    public @Nullable O removeBlockData(@NotNull BlockPos blockPos){
        return blockPosMap.remove(blockPos);
    }

    public @NotNull Map<BlockPos, O> getBlockPosMap() {
        return blockPosMap;
    }

    public @NotNull Collection<O> getAllBlockData(){
        return blockPosMap.size() == 0 ? Collections.EMPTY_LIST : blockPosMap.values();
    }

}
