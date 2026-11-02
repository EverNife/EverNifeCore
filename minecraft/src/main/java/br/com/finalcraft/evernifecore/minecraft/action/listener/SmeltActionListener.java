package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.SmeltActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.FurnaceExtractEvent;

/**
 * Posts the {@link SmeltActionEvent} Post of a player taking items out of a furnace ({@link EventSource#SMELT}), asked
 * where the furnace stands. The extraction cannot be cancelled on any Bukkit era ({@code FurnaceExtractEvent} is no
 * {@code Cancellable}): no {@code Pre}, and the regions are not asked.
 */
public final class SmeltActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterExtract(FurnaceExtractEvent event) {
        if (event.getItemAmount() > 0 && !McActionContexts.isDisabled(event.getBlock().getWorld())) {
            Actions.post(SmeltActionEvent.Post.class, () -> new SmeltActionEvent.Post(new ItemContext(EventSource.SMELT,
                    McActionContexts.position(event.getBlock().getLocation()), McActionContexts.actor(event.getPlayer()), event,
                    McActionContexts.key(event.getItemType()), event.getItemAmount(), null, null)));
        }
    }
}
