package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.DamageActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Asks the regions where the victim stands about every hit, with the damager as the attacker - an exploding creeper or
 * TNT included, so an explosion's damage is asked with the explosion's actor - and about a block explosion's damage (a
 * bed, a respawn anchor), with the environment as the attacker; a denial, or a cancelled {@code Pre}, cancels it. Each
 * hit then posts its {@link DamageActionEvent} pair.
 */
public final class DamageActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())
                && Actions.denied(context(event), DamageActionEvent.Pre.class, DamageActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDamage(EntityDamageByEntityEvent event) {
        if (!McActionContexts.isDisabled(event.getEntity().getWorld())) {
            Actions.post(DamageActionEvent.Post.class, () -> new DamageActionEvent.Post(context(event)));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageByBlockEvent event) {
        DamageContext context = context(event);
        if (context != null && Actions.denied(context, DamageActionEvent.Pre.class, DamageActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBlockExplosionDamage(EntityDamageByBlockEvent event) {
        if (isAsked(event)) {
            Actions.post(DamageActionEvent.Post.class, () -> new DamageActionEvent.Post(context(event)));
        }
    }

    //the damager acts, with the player behind it as root - none for a monster: the flag decides what that means
    private static DamageContext context(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        return new DamageContext(EventSource.DAMAGE, McActionContexts.position(victim.getLocation()), McActionContexts.actor(event.getDamager()), event,
                McActionContexts.actor(victim));
    }

    private static boolean isAsked(EntityDamageByBlockEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && !McActionContexts.isDisabled(event.getEntity().getWorld());
    }

    /** Null for block damage other than an explosion's, or in a disabled world. */
    private static @Nullable DamageContext context(EntityDamageByBlockEvent event) {
        if (!isAsked(event)) {
            return null;
        }
        Entity victim = event.getEntity();
        return new DamageContext(EventSource.DAMAGE, McActionContexts.position(victim.getLocation()), Actor.environment(McActionContexts.EXPLOSION), event,
                McActionContexts.actor(victim));
    }
}
