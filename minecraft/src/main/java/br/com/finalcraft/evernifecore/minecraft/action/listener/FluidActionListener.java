package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.FluidContext;
import br.com.finalcraft.evernifecore.action.event.FluidActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * Asks the regions about water and lava flowing into a block ({@link EventSource#FLUID}), the liquid as the environment
 * under its own key; a denial, or a cancelled {@code Pre}, cancels the flow, and a flow that went through posts its
 * {@link FluidActionEvent} Post. {@link BlockFromToEvent} is the hottest event a server raises: this listener is on the
 * server only while a region holds a flag that needs {@code FLUID}, or somebody listens to its action event.
 */
public final class FluidActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isAsked(event) && Actions.denied(context(event), FluidActionEvent.Pre.class, FluidActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterFlow(BlockFromToEvent event) {
        if (isAsked(event)) {
            Actions.post(FluidActionEvent.Post.class, () -> new FluidActionEvent.Post(context(event)));
        }
    }

    private static boolean isAsked(BlockFromToEvent event) {
        Block from = event.getBlock();
        return from.isLiquid() && !McActionContexts.isDisabled(from.getWorld());   //a dragon egg teleporting raises it too
    }

    private static FluidContext context(BlockFromToEvent event) {
        Block from = event.getBlock();
        return new FluidContext(McActionContexts.position(event.getToBlock().getLocation()),
                Actor.environment(McActionContexts.key(from.getType())), event, McActionContexts.position(from.getLocation()));
    }
}
