package br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard;

import br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers.ImpFCRegionManager;
import br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers.ImpWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;

public class ImpWGPlatform extends WGPlatform {

    @Override
    public SimpleFlagRegistry getFlagRegistry() {
        return (SimpleFlagRegistry) WorldGuard.getInstance().getFlagRegistry();
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
