package br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.minecraft.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.transform.Transform;
import jakarta.annotation.Nullable;
import org.bukkit.World;

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

    /** {@link #paste(World, BlockPos, boolean, boolean, Transform)} with air and entities, untransformed. */
    public EditSession paste(World world, BlockPos to){
        return paste(world, to, true, true, null);
    }

    /**
     * Pastes this clipboard with its origin at {@code to}, through WorldEdit's own copy, in one go on the calling thread.
     *
     * @param transform applied around the clipboard origin (a rotation, a flip); null pastes it as it is
     * @return the finished session - {@code session.undo(otherSession)} puts back what the paste replaced
     */
    public abstract EditSession paste(World world, BlockPos to, boolean pasteAir, boolean copyEntities, @Nullable Transform transform);
}
