package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Asks the regions where the victim stands about every hit, with the player behind the hit as the attacker; a denial cancels it. */
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
        Player attacker = RegionContexts.rootPlayer(event.getDamager());   //null for a monster: the flag decides what that means
        DamageContext context = new DamageContext(RegionContexts.position(victim.getLocation()), attacker == null ? null : FCBukkitUtil.adapt(attacker),
                event, RegionContexts.victimKind(victim), victim instanceof Player ? FCBukkitUtil.adapt((Player) victim) : null,
                RegionContexts.id(victim.getType()));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }
}
