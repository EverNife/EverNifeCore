package br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.clipboard;

import br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.block.ImpFCBaseBlock;
import br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

public class ImpFCBlockArrayClipboard extends FCBlockArrayClipboard {

    public ImpFCBlockArrayClipboard(BlockArrayClipboard blockArrayClipboard) {
        super(blockArrayClipboard);
    }

    @Override
    public IFCCuboidRegion getRegion() {
        if (this.region == null){
            this.region = new ImpIFCCuboidRegion((CuboidRegion) blockArrayClipboard.getRegion());
        }
        return region;
    }

    @Override
    public LocPos getOrigin() {
        BlockVector3 origin = blockArrayClipboard.getOrigin();
        return new LocPos(origin.getX(), origin.getY(), origin.getZ());
    }

    @Override
    public void setOrigin(LocPos origin) {
        blockArrayClipboard.setOrigin(BlockVector3.at(origin.getX(), origin.getY(), origin.getZ()));
    }

    @Override
    public BlockPos getDimensions() {
        BlockVector3 origin = blockArrayClipboard.getDimensions();
        return new BlockPos(origin.getX(), origin.getY(), origin.getZ());
    }

    @Override
    public FCBaseBlock getBlock(BlockPos blockPos) {
        return new ImpFCBaseBlock(
                blockArrayClipboard.getFullBlock(BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
        );
    }
}
