package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.FluidContext;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * Asks the regions about water and lava flowing into a block ({@link EventSource#FLUID}), the liquid as the environment
 * under its own key; a denial cancels the flow. {@link BlockFromToEvent} is the hottest event a server raises: this
 * listener is on the server only while a region holds a flag that needs {@code FLUID}.
 */
public final class RegionFluidListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (!from.isLiquid() || RegionContexts.isDisabled(from.getWorld())) {   //a dragon egg teleporting raises it too
            return;
        }
        FluidContext context = new FluidContext(RegionContexts.position(event.getToBlock().getLocation()),
                Actor.environment(RegionContexts.key(from.getType())), event, RegionContexts.position(from.getLocation()));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
