package br.com.finalcraft.evernifecore.minecraft.compat.v1_16_R3.worldedit.wrappers.clipboard;

import br.com.finalcraft.evernifecore.minecraft.compat.v1_16_R3.worldedit.wrappers.block.ImpFCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.compat.v1_16_R3.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.minecraft.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import jakarta.annotation.Nullable;
import org.bukkit.World;

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
        BlockVector3 vector3 = blockArrayClipboard.getDimensions();
        return new BlockPos(vector3.getX(), vector3.getY(), vector3.getZ());
    }

    @Override
    public FCBaseBlock getBlock(BlockPos blockPos) {
        return new ImpFCBaseBlock(
                blockArrayClipboard.getFullBlock(BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
        );
    }

    @Override
    public EditSession paste(World world, BlockPos to, boolean pasteAir, boolean copyEntities, @Nullable Transform transform) {
        ClipboardHolder holder = new ClipboardHolder(getHandle());
        if (transform != null) {
            holder.setTransform(transform);
        }
        EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1);
        try {
            Operations.complete(holder.createPaste(editSession)
                    .to(BlockVector3.at(to.getX(), to.getY(), to.getZ()))
                    .ignoreAirBlocks(!pasteAir)
                    .copyEntities(copyEntities)
                    .build());
        } catch (WorldEditException e) {
            throw new IllegalStateException("WorldEdit refused to paste at " + to + " in " + world.getName() + ": " + e.getMessage(), e);
        } finally {
            editSession.close();
        }
        return editSession;
    }
}
