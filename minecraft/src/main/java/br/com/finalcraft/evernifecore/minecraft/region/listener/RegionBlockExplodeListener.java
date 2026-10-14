package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;

/**
 * {@link RegionExplosionListener}'s stages for a block exploding - a bed in the Nether, a respawn anchor - with the
 * environment as the actor. Its own class because {@link BlockExplodeEvent} arrived in 1.8: on 1.7.10 it is never
 * registered.
 */
public final class RegionBlockExplodeListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        RegionExplosionListener.explode(Actor.environment(RegionContexts.EXPLOSION), event.getBlock().getLocation(), event.blockList(), event);
    }
}
