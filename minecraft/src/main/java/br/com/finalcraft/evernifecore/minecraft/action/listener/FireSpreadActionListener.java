package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.FireSpreadActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;

/**
 * Asks the regions about fire spreading to a block on its own ({@link EventSource#FIRE_SPREAD}), the fire as the
 * environment; a denial, or a cancelled {@code Pre}, cancels it, and a spread that went through posts its
 * {@link FireSpreadActionEvent} Post. The server raises both events for one spread, and each is asked and posted.
 */
public final class FireSpreadActionListener implements ECListener {

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            post(event.getBlock(), event);
        }
    }

    /** Only fire: grass, vines and mushrooms spread through this event too. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE) {
            decide(event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE) {
            post(event.getBlock(), event);
        }
    }

    private static void decide(Block block, Cancellable event) {
        if (!McActionContexts.isDisabled(block.getWorld())
                && Actions.denied(context(block, event), FireSpreadActionEvent.Pre.class, FireSpreadActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    private static void post(Block block, Cancellable event) {
        if (!McActionContexts.isDisabled(block.getWorld())) {
            Actions.post(FireSpreadActionEvent.Post.class, () -> new FireSpreadActionEvent.Post(context(block, event)));
        }
    }

    private static BlockContext context(Block block, Cancellable event) {
        return new BlockContext(EventSource.FIRE_SPREAD, McActionContexts.position(block.getLocation()),
                Actor.environment(McActionContexts.FIRE), event, McActionContexts.key(Material.FIRE), null);
    }
}
