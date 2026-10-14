package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.ExplosionContext;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Asks the regions about an entity exploding - a creeper, a TNT, a fireball - in two stages of one dispatch, with one
 * actor: the explosion as a whole ({@link EventSource#EXPLOSION}; a denial cancels it, nothing breaks and nobody is
 * hurt), then each block it would break ({@link EventSource#BREAK}; a denied block leaves the list, so an explosion on a
 * border breaks the outside and keeps the inside). Whom it hurts is asked by {@link RegionDamageListener}. On the server
 * while either source is needed.
 */
public final class RegionExplosionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        //an entity-less explosion (a bed on some servers) is the environment's
        explode(entity == null ? Actor.environment(RegionContexts.EXPLOSION) : RegionContexts.actor(entity), event.getLocation(),
                event.blockList(), event);
    }

    static void explode(Actor actor, Location at, List<Block> blocks, Cancellable event) {
        if (RegionContexts.isDisabled(at.getWorld())) {
            return;
        }
        EventSourceUsage usage = EventSourceUsage.get();
        try (RegionDispatch dispatch = RegionLookup.get().dispatch()) {
            if (usage.isNeeded(EventSource.EXPLOSION)
                    && dispatch.decide(new ExplosionContext(RegionContexts.position(at), actor, event, null)) == Decision.DENY) {
                event.setCancelled(true);
                return;
            }
            if (usage.isNeeded(EventSource.BREAK)) {
                blocks.removeIf(block -> dispatch.decide(new BlockContext(EventSource.BREAK, RegionContexts.position(block.getLocation()), actor,
                        event, RegionContexts.key(block.getType()), null)) == Decision.DENY);
            }
        }
    }
}
