package br.com.finalcraft.evernifecore.compat.v1_16_R3.worldedit.wrappers.region;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.RegionOperationException;

import java.util.Arrays;

public class ImpIFCCuboidRegion implements IFCCuboidRegion {

    private final CuboidRegion region;

    public ImpIFCCuboidRegion(CuboidRegion region) {
        this.region = region;
    }

    @Override
    public CuboidRegion getHandle() {
        return region;
    }

    @Override
    public BlockPos getMinimumPoint() {
        BlockVector3 vector3 = region.getMinimumPoint();
        return new BlockPos(vector3.getBlockX(), vector3.getBlockY(), vector3.getBlockZ());
    }

    @Override
    public BlockPos getMaximumPoint() {
        BlockVector3 vector3 = region.getMaximumPoint();
        return new BlockPos(vector3.getBlockX(), vector3.getBlockY(), vector3.getBlockZ());
    }

    @Override
    public LocPos getCenter() {
        Vector3 vector3 = region.getCenter();
        return new LocPos(vector3.getX(), vector3.getY(), vector3.getZ());
    }

    @Override
    public int getArea() {
        return region.getArea();
    }

    @Override
    public int getWidth() {
        return region.getWidth();
    }

    @Override
    public int getHeight() {
        return region.getHeight();
    }

    @Override
    public int getLength() {
        return region.getLength();
    }

    @Override
    public void expand(BlockPos... pos) throws RegionOperationException {
        BlockVector3[] blockVector3s = Arrays.stream(pos)
                .map(blockPos -> BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                .toArray(BlockVector3[]::new);
        region.expand(blockVector3s);
    }

    @Override
    public void contract(BlockPos... pos) throws RegionOperationException {
        BlockVector3[] blockVector3s = Arrays.stream(pos)
                .map(blockPos -> BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                .toArray(BlockVector3[]::new);
        region.contract(blockVector3s);
    }

    @Override
    public void shift(BlockPos blockPos) throws RegionOperationException {
        region.shift(BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

    @Override
    public boolean contains(BlockPos blockPos) {
        return region.contains(BlockVector3.at(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

}
