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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class WorldGuardHandler implements ProtectionHandler {

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(location);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowBuild', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(localPlayer, WGFlags.BLOCK_PLACE); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(location);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowBreak', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(localPlayer, WGFlags.BLOCK_BREAK); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(location);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowInteract', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(localPlayer, WGFlags.INTERACT); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canAttack(Player player, Entity victim) {
        if (player.hasPermission("worldguard.region.bypass." + victim.getWorld().getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(victim.getLocation());
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowInteract', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(
                localPlayer,
                (victim instanceof Player)
                        ? WGFlags.PVP
                        : WGFlags.DAMAGE_ANIMALS
        ); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName())){
            return true;
        }

        CuboidSelection cuboidSelection = CuboidSelection.of(BlockPos.from(location)).expand(range).expandVert();
        FCRegionResultSet regions = this.getRegionsAtSelection(location.getWorld(), cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowBreak', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryState(localPlayer, WGFlags.BLOCK_BREAK, WGFlags.BLOCK_PLACE); //the null value for these flags implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        if (player.hasPermission("worldguard.region.bypass." + world.getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(world, cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowBuild', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(localPlayer, WGFlags.BLOCK_PLACE); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        if (player.hasPermission("worldguard.region.bypass." + world.getName())){
            return true;
        }

        FCRegionResultSet regions = this.getRegionsAtSelection(world, cuboidSelection);
        LocalPlayer localPlayer = WGPlatform.getInstance().wrapPlayer(player);

        //Get the default value for the location! [PASSTHROUGH and BUILD] defaults to 'allowBreak', empty region list (__global__) means true as well
        boolean allowByDefault = regions.queryValue(localPlayer, WGFlags.PASSTHROUGH) == StateFlag.State.DENY || regions.queryState(localPlayer, WGFlags.BUILD) == StateFlag.State.ALLOW;

        StateFlag.State state = regions.queryValue(localPlayer, WGFlags.BLOCK_BREAK); //the null value for this flag implies 'allow'

        if (state == StateFlag.State.DENY){
            return false;
        }

        return allowByDefault;
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
