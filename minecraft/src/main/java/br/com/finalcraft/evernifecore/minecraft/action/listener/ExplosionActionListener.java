package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.ExplosionContext;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.ExplosionActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the regions about an entity exploding - a creeper, a TNT, a fireball - in two stages of one dispatch, with one
 * actor: the explosion as a whole ({@link EventSource#EXPLOSION}; a denial or a cancelled {@code Pre} cancels it, nothing
 * breaks and nobody is hurt), then each block it would break ({@link EventSource#BREAK}; a denied block leaves the list,
 * so an explosion on a border breaks the outside and keeps the inside). After it went off, the {@link ExplosionActionEvent}
 * Post is posted once and a {@link BreakActionEvent} Post per block it broke. Whom it hurts is asked by
 * {@link DamageActionListener}. On the server while either source is needed.
 */
public final class ExplosionActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(actorOf(event), event.getLocation(), event.blockList(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterEntityExplode(EntityExplodeEvent event) {
        exploded(actorOf(event), event.getLocation(), event.blockList(), event);
    }

    //an entity-less explosion (a bed on some servers) is the environment's
    private static Actor actorOf(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        return entity == null ? Actor.environment(McActionContexts.EXPLOSION) : McActionContexts.actor(entity);
    }

    static void explode(Actor actor, Location at, List<Block> blocks, Cancellable event) {
        if (McActionContexts.isDisabled(at.getWorld())) {
            return;
        }
        EventSourceUsage usage = EventSourceUsage.get();
        try (RegionDispatch dispatch = RegionLookup.get().dispatch()) {
            if (usage.isNeeded(EventSource.EXPLOSION) && Actions.denied(dispatch, new ExplosionContext(McActionContexts.position(at), actor,
                    event, null), ExplosionActionEvent.Pre.class, ExplosionActionEvent.Pre::new)) {
                event.setCancelled(true);
                return;
            }
            if (usage.isNeeded(EventSource.BREAK)) {
                blocks.removeIf(block -> Actions.denied(dispatch, breaking(block, actor, event), BreakActionEvent.Pre.class,
                        BreakActionEvent.Pre::new));
            }
        }
    }

    /** The Posts of an explosion that went off: the explosion once, then each block left in its list. */
    static void exploded(Actor actor, Location at, List<Block> blocks, Cancellable event) {
        if (McActionContexts.isDisabled(at.getWorld())) {
            return;
        }
        EventSourceUsage usage = EventSourceUsage.get();
        if (usage.isNeeded(EventSource.EXPLOSION)) {
            Actions.post(ExplosionActionEvent.Post.class, () -> new ExplosionActionEvent.Post(new ExplosionContext(McActionContexts.position(at),
                    actor, event, null)));
        }
        if (usage.isNeeded(EventSource.BREAK)) {
            for (Block block : new ArrayList<>(blocks)) {
                Actions.post(BreakActionEvent.Post.class, () -> new BreakActionEvent.Post(breaking(block, actor, event)));
            }
        }
    }

    private static BlockContext breaking(Block block, Actor actor, Cancellable event) {
        return new BlockContext(EventSource.BREAK, McActionContexts.position(block.getLocation()), actor, event,
                McActionContexts.key(block.getType()), null);
    }
}
