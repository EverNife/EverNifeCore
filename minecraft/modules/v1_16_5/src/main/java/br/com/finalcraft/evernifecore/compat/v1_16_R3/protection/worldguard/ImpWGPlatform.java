package br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard;

import br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers.ImpFCRegionManager;
import br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers.ImpIFCFlagRegistry;
import br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers.ImpWorldGuardRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;

public class ImpWGPlatform extends WGPlatform {

    private final ImpIFCFlagRegistry ifcFlagRegistry = new ImpIFCFlagRegistry();

    @Override
    protected FCWorldGuardRegion createFCWorldGuardRegion(String id, BlockPos pt1, BlockPos pt2) {
        return new ImpWorldGuardRegion(null,
                new ProtectedCuboidRegion(
                        id,
                        BlockVector3.at(pt1.getX(), pt1.getY(), pt1.getZ()),
                        BlockVector3.at(pt2.getX(), pt2.getY(), pt2.getZ()
                        )
                ));
    }

    @Override
    protected FCWorldGuardRegion createFCWorldGuardRegion(String id, boolean isTransient, BlockPos pt1, BlockPos pt2) {
        return new ImpWorldGuardRegion(null,
                new ProtectedCuboidRegion(
                        id,
                        isTransient,
                        BlockVector3.at(pt1.getX(), pt1.getY(), pt1.getZ()),
                        BlockVector3.at(pt2.getX(), pt2.getY(), pt2.getZ()
                        )
                ));
    }

    @Override
    public IFCFlagRegistry getFlagRegistry() {
        return ifcFlagRegistry;
    }

    @Override
    public FCRegionManager getRegionManager(World world) {
        BukkitWorld bukkitWorld = (BukkitWorld) BukkitAdapter.adapt(world);
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return new ImpFCRegionManager(world, container.get(bukkitWorld));
    }

    @Override
    public FCWorldGuardRegion wrapRegion(World world, ProtectedRegion protectedRegion) {
        return new ImpWorldGuardRegion(world, protectedRegion);
    }
}
