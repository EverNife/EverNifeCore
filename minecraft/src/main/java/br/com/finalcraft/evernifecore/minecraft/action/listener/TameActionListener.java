package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.TameActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTameEvent;

/**
 * An animal tamed ({@link EventSource#TAME}), asked where it stands; the actor is the tamer. A denial, or a cancelled
 * {@code Pre}, leaves it wild; one that went through posts its {@link TameActionEvent} Post.
 */
public final class TameActionListener implements ECListener {

    /** The tamer when the server names none that is an entity (a plugin taming for an offline player). */
    public static final TypeKey NO_TAMER = TypeKey.of("environment", "tame");

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())
                && Actions.denied(context(event), TameActionEvent.Pre.class, TameActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterTame(EntityTameEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())) {
            Actions.post(TameActionEvent.Post.class, () -> new TameActionEvent.Post(context(event)));
        }
    }

    private static EntityContext context(EntityTameEvent event) {
        LivingEntity animal = event.getEntity();
        AnimalTamer tamer = event.getOwner();
        Actor actor = tamer instanceof Entity ? McActionContexts.actor((Entity) tamer) : Actor.environment(NO_TAMER);
        return new EntityContext(EventSource.TAME, McActionContexts.position(animal.getLocation()), actor, event, McActionContexts.actor(animal), null);
    }
}
