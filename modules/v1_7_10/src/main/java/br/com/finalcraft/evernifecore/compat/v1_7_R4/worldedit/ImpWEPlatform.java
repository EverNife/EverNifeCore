package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit;

import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.clipboard.ImpFCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.clipboard.format.IFCClipboardFormat_Schematic;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.clipboard.format.IFCClipboardManager;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;

public class ImpWEPlatform extends WEPlatform {

    public ImpWEPlatform() {
        super(new IFCClipboardManager(
                new IFCClipboardFormat_Schematic(),
                null),
                null);

    }

    @Override
    public IFCCuboidRegion createCuboidRegion(BlockPos pos1, BlockPos pos2) {
        return new ImpIFCCuboidRegion(new CuboidRegion(
                new Vector(pos1.getX(), pos1.getY(), pos1.getZ()),
                new Vector(pos2.getX(), pos2.getY(), pos2.getZ())
        ));
    }

    @Override
    public FCBlockArrayClipboard createBlockArrayClipboard(IFCCuboidRegion region) {
        return new ImpFCBlockArrayClipboard(
                new BlockArrayClipboard(
                        region.getHandle()
                )
        );
    }

    public void setBlock(World world, BlockPos blockPos, FCBaseBlock blockBase, boolean placeFast) throws WorldEditException {
        world.setBlock(new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                (BaseBlock) blockBase.getHandle(),
                placeFast
        );
    }
}
