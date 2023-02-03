package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.region;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.Vector;
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
        Vector vector = region.getMinimumPoint();
        return new BlockPos(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    @Override
    public BlockPos getMaximumPoint() {
        Vector vector = region.getMaximumPoint();
        return new BlockPos(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    @Override
    public LocPos getCenter() {
        Vector vector = region.getCenter();
        return new LocPos(vector.getX(), vector.getY(), vector.getZ());
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
        Vector[] blockVectors = Arrays.stream(pos)
                .map(blockPos ->new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                .toArray(Vector[]::new);
        region.expand(blockVectors);
    }

    @Override
    public void contract(BlockPos... pos) throws RegionOperationException {
        Vector[] blockVectors = Arrays.stream(pos)
                .map(blockPos ->new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                .toArray(Vector[]::new);
        region.contract(blockVectors);
    }

    @Override
    public void shift(BlockPos blockPos) throws RegionOperationException {
        region.shift(new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

    @Override
    public boolean contains(BlockPos blockPos) {
        return region.contains(new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

}
