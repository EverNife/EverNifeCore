package br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard;

import br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers.ImpFCRegionManager;
import br.com.finalcraft.evernifecore.compat.v1_7_R4.protection.worldguard.wrappers.ImpWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;

public class ImpWGPlatform extends WGPlatform {

    @Override
    public SimpleFlagRegistry getFlagRegistry() {
        return (SimpleFlagRegistry) WorldGuardPlugin.inst().getFlagRegistry();
    }

    @Override
    public FCRegionManager getRegionManager(World world) {
        return new ImpFCRegionManager(world, WorldGuardPlugin.inst().getRegionManager(world));
    }

    @Override
    public FCWorldGuardRegion wrapRegion(World world, ProtectedRegion protectedRegion) {
        return new ImpWorldGuardRegion(world, protectedRegion);
    }

}
