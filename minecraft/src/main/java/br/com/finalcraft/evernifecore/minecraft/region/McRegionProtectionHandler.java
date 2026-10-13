package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.math.game.selection.CuboidSelection;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionContexts;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * EverNifeCore's own regions answering whoever already asks {@code ProtectionAll}: each check is the action the region
 * flags would decide, with no event behind it. An area is sampled at its center and its eight corners. A world in
 * {@code Regions.disabled-worlds} allows everything.
 */
public final class McRegionProtectionHandler implements ProtectionHandler {

    @Override
    public String getName() {
        return "EverNifeCore";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return allowsBlock(EventSource.PLACE, player, location);
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        return allowsBlock(EventSource.BREAK, player, location);
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        return allowsBlock(EventSource.INTERACT, player, location);
    }

    @Override
    public boolean canAttack(Player player, Entity victim) {
        if (RegionContexts.isDisabled(victim.getWorld())) {
            return true;
        }
        return allows(new DamageContext(RegionContexts.position(victim.getLocation()), FCBukkitUtil.adapt(player), null,
                RegionContexts.victimKind(victim), victim instanceof Player ? FCBukkitUtil.adapt((Player) victim) : null,
                RegionContexts.id(victim.getType())));
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        return allowsArea(EventSource.PLACE, player, location.getWorld(),
                location.getX() - range, location.getY() - range, location.getZ() - range,
                location.getX() + range, location.getY() + range, location.getZ() + range);
    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        return allowsSelection(EventSource.PLACE, player, world, cuboidSelection);
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        return allowsSelection(EventSource.BREAK, player, world, cuboidSelection);
    }

    private boolean allowsSelection(EventSource source, Player player, World world, CuboidSelection selection) {
        BlockPos min = selection.getMinium();
        BlockPos max = selection.getMaximum();
        return allowsArea(source, player, world, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    private boolean allowsArea(EventSource source, Player player, World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (RegionContexts.isDisabled(world)) {
            return true;
        }
        if (!allowsBlock(source, player, new Location(world, (x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2))) {
            return false;
        }
        for (double x : new double[]{x1, x2}) {
            for (double y : new double[]{y1, y2}) {
                for (double z : new double[]{z1, z2}) {
                    if (!allowsBlock(source, player, new Location(world, x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean allowsBlock(EventSource source, Player player, Location at) {
        if (RegionContexts.isDisabled(at.getWorld())) {
            return true;
        }
        return allows(new BlockContext(source, RegionContexts.position(at), FCBukkitUtil.adapt(player), null,
                RegionContexts.id(at.getBlock().getType()), RegionContexts.itemInHandId(player), FCBukkitUtil.isFakePlayer(player)));
    }

    private static boolean allows(ActionContext context) {
        return RegionLookup.get().decide(context) != Decision.DENY;
    }
}
