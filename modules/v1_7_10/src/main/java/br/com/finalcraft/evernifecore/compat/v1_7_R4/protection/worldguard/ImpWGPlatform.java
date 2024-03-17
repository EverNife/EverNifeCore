package br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard;

import br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers.ImpFCRegionManager;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers.ImpIFCFlagRegistry;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers.ImpWorldGuardRegion;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class ImpWGPlatform extends WGPlatform {

    private final ImpIFCFlagRegistry ifcFlagRegistry = new ImpIFCFlagRegistry();

    @Override
    public FCWorldGuardRegion createFCWorldGuardRegion(String id, BlockPos pt1, BlockPos pt2) {
        return new ImpWorldGuardRegion(null,
                new ProtectedCuboidRegion(
                        id,
                        new BlockVector(pt1.getX(), pt1.getY(), pt1.getZ()),
                        new BlockVector(pt2.getX(), pt2.getY(), pt2.getZ()
                        )
                ));
    }

    @Override
    public FCWorldGuardRegion createFCWorldGuardRegion(String id, boolean isTransient, BlockPos pt1, BlockPos pt2) {
        return new ImpWorldGuardRegion(null,
                new ProtectedCuboidRegion(
                        id,
                        //isTransient, //There is no such thing of 'transient' on 1.7.10
                        new BlockVector(pt1.getX(), pt1.getY(), pt1.getZ()),
                        new BlockVector(pt2.getX(), pt2.getY(), pt2.getZ()
                        )
                ));
    }

    @Override
    public IFCFlagRegistry getFlagRegistry() {
        return ifcFlagRegistry;
    }

    @Override
    public FCRegionManager getRegionManager(World world) {
        return new ImpFCRegionManager(world, WorldGuardPlugin.inst().getRegionManager(world));
    }

    @Override
    public FCWorldGuardRegion wrapRegion(World world, ProtectedRegion protectedRegion) {
        return new ImpWorldGuardRegion(world, protectedRegion);
    }

    @Override
    public void registerFlag(@NotNull Flag<?> flag, @NotNull Plugin plugin) {
        getFlagRegistry().register(flag);
        plugin.getLogger().info("[EverNifeCore -> WorldGuard] Custom Flag registered: " + flag.getName());
        schedulWorldGuardReload();
    }
}
