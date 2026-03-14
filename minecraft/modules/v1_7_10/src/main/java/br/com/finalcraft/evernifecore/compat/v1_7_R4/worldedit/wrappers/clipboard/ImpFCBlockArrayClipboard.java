package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.clipboard;

import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.block.ImpFCBaseBlock;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.Location;
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
    public void paste(World world, BlockPos to, boolean allowUndo, boolean pasteAir, boolean copyEntities, Transform transform) {
        com.sk89q.worldedit.world.World bukkitWorld = new BukkitWorld(world);
        Extent extent = bukkitWorld;

        BlockVector toVector = new BlockVector(to.getX(), to.getY(), to.getZ());

        final Vector origin = getHandle().getOrigin();

        // To must be relative to the clipboard origin ( player location - clipboard origin ) (as the locations supplied are relative to the world origin)
        final int relx = toVector.getBlockX() - origin.getBlockX();
        final int rely = toVector.getBlockY() - origin.getBlockY();
        final int relz = toVector.getBlockZ() - origin.getBlockZ();

        for (BlockVector pos : getHandle().getRegion()) {
            BaseBlock block = getHandle().getBlock(pos);
            int xx = (int) pos.getX() + relx;
            int yy = (int) pos.getY() + rely;
            int zz = (int) pos.getZ() + relz;

            if (!pasteAir && block.getId() <= 0) {
                continue;
            }
            try {
                extent.setBlock(new BlockVector(xx, yy, zz), block);
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
                if (entity.getState() != null && entity.getState().getTypeId().equals("minecraft:player")) {
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
