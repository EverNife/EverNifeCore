package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.SpawnActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Asks the regions about every creature the world spawns; a denial, or a cancelled {@code Pre}, cancels it. A spawn
 * from an egg is a player acting, never asked of the regions - its {@link SpawnActionEvent} pair is posted all the same,
 * with the egg as the reason, as for every other spawn.
 */
public final class SpawnActionListener implements ECListener {

    private static final TypeKey NATURAL_SPAWN = TypeKey.of("environment", "spawn");

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (McActionContexts.isDisabled(event.getEntity().getWorld())) {
            return;
        }
        boolean denied = isEgg(event)
                ? Actions.cancelled(SpawnActionEvent.Pre.class, () -> new SpawnActionEvent.Pre(context(event)))
                : Actions.denied(context(event), SpawnActionEvent.Pre.class, SpawnActionEvent.Pre::new);
        if (denied) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterSpawn(CreatureSpawnEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())) {
            Actions.post(SpawnActionEvent.Post.class, () -> new SpawnActionEvent.Post(context(event)));
        }
    }

    private static boolean isEgg(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {   //a switch, not ==: a constant a server lacks then reads as no match instead of failing to link
            case EGG:
            case SPAWNER_EGG:
            case DISPENSE_EGG:
                return true;
            default:
                return false;
        }
    }

    //the world spawns it: the environment acts, and the flags decide by the creature
    private static SpawnContext context(CreatureSpawnEvent event) {
        Entity spawned = event.getEntity();
        return new SpawnContext(McActionContexts.position(spawned.getLocation()), Actor.environment(NATURAL_SPAWN), event,
                McActionContexts.actor(spawned), event.getSpawnReason().name());
    }
}
