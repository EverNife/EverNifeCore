package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.BreedActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;

/**
 * Two animals breeding ({@link EventSource#BREED}), asked where the newborn appears; the context's entity is the newborn,
 * the actor whoever fed the parents. A denial, or a cancelled {@code Pre}, cancels the breeding; one that went through
 * posts its {@link BreedActionEvent} Post. {@code EntityBreedEvent} exists from 1.12 on: registered only there.
 */
public final class BreedActionListener implements ECListener {

    /** The breeder when nobody fed the parents (a villager, a plugin). */
    public static final TypeKey NO_BREEDER = TypeKey.of("environment", "breed");

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())
                && Actions.denied(context(event), BreedActionEvent.Pre.class, BreedActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreed(EntityBreedEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())) {
            Actions.post(BreedActionEvent.Post.class, () -> new BreedActionEvent.Post(context(event)));
        }
    }

    private static EntityContext context(EntityBreedEvent event) {
        LivingEntity child = event.getEntity();
        LivingEntity breeder = event.getBreeder();
        return new EntityContext(EventSource.BREED, McActionContexts.position(child.getLocation()),
                breeder == null ? Actor.environment(NO_BREEDER) : McActionContexts.actor(breeder), event, McActionContexts.actor(child));
    }
}
