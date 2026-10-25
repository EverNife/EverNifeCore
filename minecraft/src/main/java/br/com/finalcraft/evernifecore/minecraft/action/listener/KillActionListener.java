package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Locale;

/**
 * Posts the {@link KillActionEvent} Post of every death ({@link EventSource#KILL}; a player's death is an
 * {@code EntityDeathEvent} too). A death cannot be cancelled, so there is no {@code Pre} and the regions are not asked.
 * The killer is the last entity that hit the victim - with the player behind a pet or an arrow as root - else the
 * player the server credits, else the environment named by the cause of the last damage ({@code environment:fall}).
 */
public final class KillActionListener implements ECListener {

    /** The killer of a death with no damage recorded. */
    public static final TypeKey UNKNOWN_DEATH = TypeKey.of("environment", "death");

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!McActionContexts.isDisabled(victim.getWorld())) {
            Actions.post(KillActionEvent.Post.class, () -> new KillActionEvent.Post(new DamageContext(EventSource.KILL,
                    McActionContexts.position(victim.getLocation()), killer(victim), event, McActionContexts.actor(victim))));
        }
    }

    static Actor killer(LivingEntity victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent) {
            return McActionContexts.actor(((EntityDamageByEntityEvent) last).getDamager());
        }
        Player credited = victim.getKiller();
        if (credited != null) {
            return McActionContexts.actor(credited);
        }
        return last == null || last.getCause() == null ? Actor.environment(UNKNOWN_DEATH)
                : Actor.environment(TypeKey.of("environment", last.getCause().name().toLowerCase(Locale.ROOT)));
    }
}
