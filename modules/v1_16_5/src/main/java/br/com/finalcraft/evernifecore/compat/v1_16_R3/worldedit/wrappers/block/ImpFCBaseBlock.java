package br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.block;

import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.extent.reorder.MultiStageReorder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

public class ImpFCBaseBlock extends FCBaseBlock {

    private static final Map<BlockType, MultiStageReorder.PlacementPriority> priorityMap;
    static {
        try {
            Field field_priorityMap = MultiStageReorder.class.getDeclaredField("priorityMap");
            field_priorityMap.setAccessible(true);
            priorityMap = (Map<BlockType, MultiStageReorder.PlacementPriority>) field_priorityMap.get(null);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private final BaseBlock baseBlock;

    public ImpFCBaseBlock(BaseBlock baseBlock) {
        this.baseBlock = baseBlock;
    }

    @Override
    public Object getHandle() {
        return baseBlock;
    }

    @Nullable
    @Override
    public CompoundTag getNbtData() {
        return baseBlock.getNbtData();
    }

    @Override
    public int getLegacyId() {
        return baseBlock.getBlockType().getLegacyId();
    }

    @Override
    public int getLegacyMetadata() {
        return baseBlock.getBlockType().getLegacyData();
    }

    @Override
    public boolean shouldPlaceFinal() {
        return priorityMap.get(baseBlock.getBlockType()) == MultiStageReorder.PlacementPriority.FINAL;
    }

    @Override
    public boolean shouldPlaceLast() {
        MultiStageReorder.PlacementPriority priority = priorityMap.get(baseBlock.getBlockType());
        return priority == MultiStageReorder.PlacementPriority.LATE || priority == MultiStageReorder.PlacementPriority.LAST;
    }
}
