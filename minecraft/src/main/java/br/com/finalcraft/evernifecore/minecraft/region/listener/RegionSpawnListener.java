package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Asks the regions about every creature the world spawns; a denial cancels it. A spawn from an egg is a player acting, never asked. */
public final class RegionSpawnListener implements ECListener {

    private static final TypeKey NATURAL_SPAWN = TypeKey.of("environment", "spawn");

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Entity spawned = event.getEntity();
        if (RegionContexts.isDisabled(spawned.getWorld())) {
            return;
        }
        switch (event.getSpawnReason()) {   //a switch, not ==: a constant a server lacks then reads as no match instead of failing to link
            case EGG:
            case SPAWNER_EGG:
            case DISPENSE_EGG:
                return;
            default:
                break;
        }
        //the world spawns it: the environment acts, and the flags decide by the creature
        SpawnContext context = new SpawnContext(RegionContexts.position(spawned.getLocation()), Actor.environment(NATURAL_SPAWN), event,
                RegionContexts.actor(spawned), event.getSpawnReason().name());
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
