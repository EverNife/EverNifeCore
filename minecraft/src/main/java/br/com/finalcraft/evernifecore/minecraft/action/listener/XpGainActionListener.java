package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.XpContext;
import br.com.finalcraft.evernifecore.action.event.XpGainActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerExpChangeEvent;

/**
 * Posts the {@link XpGainActionEvent} Post of experience a player gains ({@link EventSource#XP_GAIN}); a change of
 * nothing, or a loss, is no gain. The event is no {@code Cancellable} - a plugin may change the amount, not refuse it -
 * so there is no {@code Pre}, and the amount posted is the one left after every other listener.
 */
public final class XpGainActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0 && !McActionContexts.isDisabled(event.getPlayer().getWorld())) {
            Actions.post(XpGainActionEvent.Post.class, () -> new XpGainActionEvent.Post(new XpContext(
                    McActionContexts.position(event.getPlayer().getLocation()), McActionContexts.actor(event.getPlayer()), event,
                    event.getAmount())));
        }
    }
}
