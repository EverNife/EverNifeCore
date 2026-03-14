package br.com.finalcraft.evernifecore.worldedit.region;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.LocPos;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.RegionOperationException;

public interface IFCCuboidRegion {

    public CuboidRegion getHandle();

    BlockPos getMinimumPoint();

    BlockPos getMaximumPoint();

    LocPos getCenter();

    int getArea();

    int getWidth();

    int getHeight();

    int getLength();

    void expand(BlockPos... pos) throws RegionOperationException;

    void contract(BlockPos... pos) throws RegionOperationException;

    void shift(BlockPos blockPos) throws RegionOperationException;

    boolean contains(BlockPos blockPos);

}
