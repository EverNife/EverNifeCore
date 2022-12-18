package br.com.finalcraft.evernifecore.compat.v1_12_R2.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;

public class ImpWorldGuardRegion implements FCWorldGuardRegion {

    private final World world;
    private final ProtectedRegion protectedRegion;

    public ImpWorldGuardRegion(World world, ProtectedRegion protectedRegion) {
        this.world = world;
        this.protectedRegion = protectedRegion;
    }

    @Override
    public ProtectedRegion getProtectedRegion() {
        return protectedRegion;
    }

    @Override
    public boolean contains(Location location) {
        return this.protectedRegion.contains(new Vector(location.getX(), location.getY(), location.getZ()));
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public Location getMaximumPoint() {
        return new Location(this.world,
                getProtectedRegion().getMaximumPoint().getBlockX(),
                getProtectedRegion().getMaximumPoint().getBlockY(),
                getProtectedRegion().getMaximumPoint().getBlockZ()
        );
    }

    @Override
    public Location getMinimumPoint() {
        return new Location(this.world,
                getProtectedRegion().getMinimumPoint().getBlockX(),
                getProtectedRegion().getMinimumPoint().getBlockY(),
                getProtectedRegion().getMinimumPoint().getBlockZ()
        );
    }
}
