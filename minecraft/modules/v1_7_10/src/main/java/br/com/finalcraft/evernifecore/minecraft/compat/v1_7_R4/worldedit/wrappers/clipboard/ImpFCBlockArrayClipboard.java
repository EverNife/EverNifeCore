package br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.clipboard;

import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.block.ImpFCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.minecraft.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.registry.WorldData;
import jakarta.annotation.Nullable;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;

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
        Vector origin = blockArrayClipboard.getOrigin();
        return new LocPos(origin.getX(), origin.getY(), origin.getZ());
    }

    @Override
    public void setOrigin(LocPos origin) {
        blockArrayClipboard.setOrigin(new Vector(origin.getX(), origin.getY(), origin.getZ()));
    }

    @Override
    public BlockPos getDimensions() {
        Vector vector = blockArrayClipboard.getDimensions();
        return new BlockPos(vector.getX(), vector.getY(), vector.getZ());
    }

    @Override
    public FCBaseBlock getBlock(BlockPos blockPos) {
        return new ImpFCBaseBlock(
                blockArrayClipboard.getBlock(new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
        );
    }

    @Override
    public void clearNbtData() {
        for (BlockVector at : blockArrayClipboard.getRegion()) {
            BaseBlock block = blockArrayClipboard.getBlock(at);
            if (block.hasNbtData()) {
                try {
                    blockArrayClipboard.setBlock(at, new BaseBlock(block.getId(), block.getData()));
                } catch (WorldEditException e) {
                    throw new IllegalStateException("WorldEdit refused to rewrite " + at + " of its own clipboard: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public EditSession paste(World world, BlockPos to, boolean pasteAir, boolean copyEntities, @Nullable Transform transform) {
        com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
        WorldData worldData = weWorld.getWorldData();
        //WorldEdit 6 always copies the source's entities; a source without them is how a paste leaves them out
        Clipboard source = copyEntities ? getHandle() : new EntitylessClipboard(getHandle());
        ClipboardHolder holder = new ClipboardHolder(source, worldData);
        if (transform != null) {
            holder.setTransform(transform);
        }
        EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1);
        try {
            Operations.completeLegacy(holder.createPaste(editSession, worldData)
                    .to(new Vector(to.getX(), to.getY(), to.getZ()))
                    .ignoreAirBlocks(!pasteAir)
                    .build());
        } catch (MaxChangedBlocksException e) {
            throw new IllegalStateException("WorldEdit refused to paste at " + to + " in " + world.getName() + ": " + e.getMessage(), e);
        } finally {
            editSession.flushQueue();
        }
        return editSession;
    }

    private static final class EntitylessClipboard extends AbstractDelegateExtent implements Clipboard {

        private final Clipboard clipboard;

        private EntitylessClipboard(Clipboard clipboard) {
            super(clipboard);
            this.clipboard = clipboard;
        }

        @Override public Region getRegion() { return clipboard.getRegion(); }
        @Override public Vector getDimensions() { return clipboard.getDimensions(); }
        @Override public Vector getOrigin() { return clipboard.getOrigin(); }
        @Override public void setOrigin(Vector origin) { clipboard.setOrigin(origin); }
        @Override public List<? extends Entity> getEntities(Region region) { return Collections.emptyList(); }
        @Override public List<? extends Entity> getEntities() { return Collections.emptyList(); }
    }
}
