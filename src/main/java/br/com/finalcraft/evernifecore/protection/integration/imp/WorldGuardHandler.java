package br.com.finalcraft.evernifecore.protection.integration.imp;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGFlags;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionResultSet;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

public class WorldGuardHandler implements ProtectionHandler {

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return this.check(player, location, WGFlags.BUILD); //Myabe we could check for BLOCK_PALCE as well ?
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        return this.check(player, location, WGFlags.BUILD); //Myabe we could check for BLOCK_BREAK as well ?
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        return this.check(player, location, WGFlags.INTERACT);
    }

    @Override
    public boolean canAttack(Player player, Entity entity) {
        if (entity instanceof Player) {
            return this.check(player, entity.getLocation(), WGFlags.PVP);
        }
        if (entity instanceof Animals || entity instanceof Villager) {
            return this.check(player, entity.getLocation(), WGFlags.DAMAGE_ANIMALS);
        }
        return true;
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {

        return canBreakOnRegion(
                player,
                location.getWorld(),
                CuboidSelection.of(
                        BlockPos.from(location).add(-range, location.getBlockY(), -range),
                        BlockPos.from(location).add(range, 255 - location.getBlockY(), range)
                )
        );

    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        FCRegionResultSet regions = getRegionsAtSelection(world, cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);
        return regions.testState(localPlayer, WGFlags.BUILD); //Myabe we could check for BLOCK_PLACE as well ?
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        FCRegionResultSet regions = getRegionsAtSelection(world, cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);
        return regions.testState(localPlayer, WGFlags.BUILD); //Myabe we could check for BLOCK_BREAK as well ?
    }

    protected boolean check(Player player, Location location, StateFlag flag) {
        FCRegionResultSet regions = this.getRegionsAtSelection(location);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        StateFlag.State state = regions.queryState(localPlayer, flag);

        if (state != null && state != StateFlag.State.ALLOW){
            return false;
        }

        return true;
    }

    protected FCRegionResultSet getRegionsAtSelection(Location location) {
        return WGPlatform.getInstance().getRegionManager(location.getWorld()).getApplicableRegions(location);
    }

    protected FCRegionResultSet getRegionsAtSelection(World world, CuboidSelection cuboidSelection) {
        FCWorldGuardRegion region = FCWorldGuardRegion.of("EC-WorldGuardHandler-Tester", cuboidSelection);

        return WGPlatform.getInstance()
                .getRegionManager(world)
                .getApplicableRegions(region);
    }

}
