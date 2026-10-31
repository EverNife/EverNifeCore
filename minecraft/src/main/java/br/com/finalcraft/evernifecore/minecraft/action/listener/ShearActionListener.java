package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ShearActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * An animal sheared by a player ({@link EventSource#SHEAR}), asked where it stands. A denial, or a cancelled
 * {@code Pre}, leaves it as it was; one that went through posts its {@link ShearActionEvent} Post.
 */
public final class ShearActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())
                && Actions.denied(context(event), ShearActionEvent.Pre.class, ShearActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterShear(PlayerShearEntityEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())) {
            Actions.post(ShearActionEvent.Post.class, () -> new ShearActionEvent.Post(context(event)));
        }
    }

    private static EntityContext context(PlayerShearEntityEvent event) {
        return new EntityContext(EventSource.SHEAR, McActionContexts.position(event.getEntity().getLocation()),
                McActionContexts.actor(event.getPlayer()), event, McActionContexts.actor(event.getEntity()), null);
    }
}
