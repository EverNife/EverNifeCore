package br.com.finalcraft.evernifecore.worlddata;

import br.com.finalcraft.evernifecore.api.hytale.math.vector.BlockPos;

public class BlockMetaData<O extends Object> {

    protected final BlockPos blockPos;
    protected final ChunkData<O> chunkData;
    protected final O value;

    public BlockMetaData(BlockPos blockPos, ChunkData<O> chunkData, O value) {
        this.blockPos = blockPos;
        this.chunkData = chunkData;
        this.value = value;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public ChunkData<O> getChunkData() {
        return chunkData;
    }

    public O getValue() {
        return value;
    }

    public void setRecentChanged(){
        this.getChunkData().getWorldData().getServerData().onBlockMetaSet(this);
    }

    public void remove(){
        this.getChunkData().setBlockData(this.blockPos, null);
    }

}
