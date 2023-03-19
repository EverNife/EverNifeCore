package br.com.finalcraft.evernifecore.worldedit.clipboard;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.transform.Transform;
import org.bukkit.World;

import javax.annotation.Nullable;

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

    public EditSession paste(World world, BlockPos to){
        return paste(world, to, false, true, null);
    }

    public EditSession paste(World world, BlockPos to, boolean allowUndo, boolean pasteAir, @Nullable Transform transform) {
        paste(world, to, allowUndo, pasteAir, true, transform);
        return null;
    }

    public abstract void paste(World world, BlockPos to, boolean allowUndo, boolean pasteAir, boolean copyEntities, @Nullable Transform transform);
}
