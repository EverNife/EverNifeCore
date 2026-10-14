package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Asks the regions where the victim stands about every hit, with the damager as the attacker - an exploding creeper or
 * TNT included, so an explosion's damage is asked with the explosion's actor - and about a block explosion's damage (a
 * bed, a respawn anchor), with the environment as the attacker; a denial cancels it.
 */
public final class RegionDamageListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (RegionContexts.isDisabled(victim.getWorld())) {
            return;
        }
        //the damager acts, with the player behind it as root - none for a monster: the flag decides what that means
        DamageContext context = new DamageContext(RegionContexts.position(victim.getLocation()), RegionContexts.actor(event.getDamager()),
                event, RegionContexts.actor(victim));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageByBlockEvent event) {
        Entity victim = event.getEntity();
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || RegionContexts.isDisabled(victim.getWorld())) {
            return;
        }
        DamageContext context = new DamageContext(RegionContexts.position(victim.getLocation()), Actor.environment(RegionContexts.EXPLOSION),
                event, RegionContexts.actor(victim));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
