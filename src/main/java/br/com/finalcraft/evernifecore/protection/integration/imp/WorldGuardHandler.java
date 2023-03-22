package br.com.finalcraft.evernifecore.protection.integration.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
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

import java.util.Arrays;
import java.util.stream.Collectors;

public class WorldGuardHandler implements ProtectionHandler {

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return this.queryState(player, location, WGFlags.PASSTHROUGH, WGFlags.BUILD, WGFlags.BLOCK_PLACE);//If any of these are TRUE, then we can build
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        return this.queryState(player, location, WGFlags.PASSTHROUGH, WGFlags.BUILD, WGFlags.BLOCK_BREAK);//If any of these are TRUE, then we can break
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        return this.queryState(player, location, WGFlags.PASSTHROUGH, WGFlags.BUILD, WGFlags.INTERACT);//If any of these are TRUE, then we can interact
    }

    @Override
    public boolean canAttack(Player player, Entity entity) {
        if (entity instanceof Player) {
            return this.queryState(player, entity.getLocation(), WGFlags.PVP);
        }
        if (entity instanceof Animals || entity instanceof Villager) {
            return this.queryState(player, entity.getLocation(), WGFlags.PASSTHROUGH, WGFlags.DAMAGE_ANIMALS);
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
        return regions.testState(localPlayer, WGFlags.PASSTHROUGH, WGFlags.BUILD, WGFlags.BLOCK_PLACE);
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        FCRegionResultSet regions = getRegionsAtSelection(world, cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);
        return regions.testState(localPlayer, WGFlags.PASSTHROUGH, WGFlags.BUILD, WGFlags.BLOCK_BREAK);
    }

    protected boolean queryState(Player player, Location location, StateFlag... flags) {
        FCRegionResultSet regions = this.getRegionsAtSelection(location);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        StateFlag.State state = regions.queryState(localPlayer, flags);

        EverNifeCore.getLog().info("Regions [%s] queryState: <%s> result: %s", regions.getRegions().stream()
                .map(region -> region.getId()).collect(Collectors.joining(", ")), Arrays.toString(flags), state
        );

        if (state != null && state == StateFlag.State.ALLOW){
            return true;
        }

        return false;
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
