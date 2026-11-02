package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.ConsumeActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * An item eaten or drunk ({@link EventSource#CONSUME}), asked where the player stands. A denial, or a cancelled
 * {@code Pre}, keeps the item; one that went through posts its {@link ConsumeActionEvent} Post.
 */
public final class ConsumeActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!McActionContexts.isDisabled(event.getPlayer().getWorld())
                && Actions.denied(context(event), ConsumeActionEvent.Pre.class, ConsumeActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterConsume(PlayerItemConsumeEvent event) {
        if (!McActionContexts.isDisabled(event.getPlayer().getWorld())) {
            Actions.post(ConsumeActionEvent.Post.class, () -> new ConsumeActionEvent.Post(context(event)));
        }
    }

    private static ItemContext context(PlayerItemConsumeEvent event) {
        return new ItemContext(EventSource.CONSUME, McActionContexts.position(event.getPlayer().getLocation()),
                McActionContexts.actor(event.getPlayer()), event, McActionContexts.key(event.getItem()), 1, null,
                McActionContexts.item(event.getItem()));
    }
}
