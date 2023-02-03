package br.com.finalcraft.evernifecore.worldedit.clipboard;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;

public abstract class FCBlockArrayClipboard {

    protected final BlockArrayClipboard blockArrayClipboard;
    protected transient IFCCuboidRegion region;

    public FCBlockArrayClipboard(BlockArrayClipboard blockArrayClipboard) {
        this.blockArrayClipboard = blockArrayClipboard;
    }

    public BlockArrayClipboard getHandle() {
        return blockArrayClipboard;
    }

    public abstract IFCCuboidRegion getRegion();

    public abstract LocPos getOrigin();

    public abstract void setOrigin(LocPos origin);

    public BlockPos getMinimumPoint() {
        return getRegion().getMinimumPoint();
    }

    public BlockPos getMaximumPoint() {
        return getRegion().getMaximumPoint();
    }

    public abstract BlockPos getDimensions();

    public abstract FCBaseBlock getBlock(BlockPos blockPos);
}
