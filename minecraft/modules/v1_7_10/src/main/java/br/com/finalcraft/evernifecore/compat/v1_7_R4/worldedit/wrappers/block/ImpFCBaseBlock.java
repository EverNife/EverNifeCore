package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.block;

import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockType;

public class ImpFCBaseBlock extends FCBaseBlock {

    private final BaseBlock baseBlock;

    public ImpFCBaseBlock(BaseBlock baseBlock) {
        this.baseBlock = baseBlock;
    }

    @Override
    public Object getHandle() {
        return baseBlock;
    }

    @Override
    public CompoundTag getNbtData() {
        return baseBlock.getNbtData();
    }

    @Override
    public int getLegacyId() {
        return baseBlock.getType();
    }

    @Override
    public int getLegacyMetadata() {
        return baseBlock.getData();
    }

    @Override
    public boolean shouldPlaceFinal() {
        return BlockType.shouldPlaceFinal(baseBlock.getType());
    }

    @Override
    public boolean shouldPlaceLast() {
        return BlockType.shouldPlaceLast(baseBlock.getType());
    }
}
