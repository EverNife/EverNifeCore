package br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.clipboard;

import br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.block.ImpFCBaseBlock;
import br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BaseBlock;
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
    public void paste(World world, BlockPos to, boolean allowUndo, boolean pasteAir, boolean copyEntities, @Nullable Transform transform) {
        BukkitWorld bukkitWorld = (BukkitWorld) BukkitAdapter.adapt(world);
        Extent extent = bukkitWorld;

        BlockVector3 toVector = BlockVector3.at(to.getX(), to.getY(), to.getZ());

        final BlockVector3 origin = getHandle().getOrigin();

        // To must be relative to the clipboard origin ( player location - clipboard origin ) (as the locations supplied are relative to the world origin)
        final int relx = toVector.getBlockX() - origin.getBlockX();
        final int rely = toVector.getBlockY() - origin.getBlockY();
        final int relz = toVector.getBlockZ() - origin.getBlockZ();

        for (BlockVector3 pos : getHandle().getRegion()) {
            BaseBlock block = getHandle().getFullBlock(pos);
            int xx = pos.getX() + relx;
            int yy = pos.getY() + rely;
            int zz = pos.getZ() + relz;

            if (!pasteAir && block.getBlockType().getMaterial().isAir()) {
                continue;
            }
            try {
                extent.setBlock(BlockVector3.at(xx, yy, zz), block);
            } catch (WorldEditException e) {
                System.out.println("[EverNifeCore-SchematicPlacer] Error pasting block at " + xx + ", " + yy + ", " + zz);
                e.printStackTrace();
            }
        }
        // Entity offset is the paste location subtract the clipboard origin (entity's location is already relative to the world origin)
        final int entityOffsetX = toVector.getBlockX() - origin.getBlockX();
        final int entityOffsetY = toVector.getBlockY() - origin.getBlockY();
        final int entityOffsetZ = toVector.getBlockZ() - origin.getBlockZ();
        // entities
        if (copyEntities) {
            for (Entity entity : this.getHandle().getEntities()) {
                // skip players on pasting schematic
                if (entity.getState() != null && entity.getState().getType().getId()
                        .equals("minecraft:player")) {
                    continue;
                }
                Location pos = entity.getLocation();
                Location newPos = new Location(pos.getExtent(), pos.getX() + entityOffsetX,
                        pos.getY() + entityOffsetY, pos.getZ() + entityOffsetZ, pos.getYaw(),
                        pos.getPitch()
                );
                extent.createEntity(newPos, entity.getState());
            }
        }
    }
}
