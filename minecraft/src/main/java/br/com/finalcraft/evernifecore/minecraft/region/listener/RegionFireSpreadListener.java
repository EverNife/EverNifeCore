package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;

/**
 * Asks the regions about fire spreading to a block on its own ({@link EventSource#FIRE_SPREAD}), the fire as the
 * environment; a denial cancels it. The server raises both events for one spread, and each is asked.
 */
public final class RegionFireSpreadListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            decide(event.getBlock(), event);
        }
    }

    /** Only fire: grass, vines and mushrooms spread through this event too. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE) {
            decide(event.getBlock(), event);
        }
    }

    private static void decide(Block block, Cancellable event) {
        if (RegionContexts.isDisabled(block.getWorld())) {
            return;
        }
        BlockContext context = new BlockContext(EventSource.FIRE_SPREAD, RegionContexts.position(block.getLocation()),
                Actor.environment(RegionContexts.FIRE), event, RegionContexts.key(Material.FIRE), null);
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
