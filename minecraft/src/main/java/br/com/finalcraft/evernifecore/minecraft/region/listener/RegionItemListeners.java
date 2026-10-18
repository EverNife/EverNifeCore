package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.ItemContext;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * The item listeners, one class per event so that each registers alone: {@link EventSource#ITEM_DROP} is a player
 * throwing an item, {@link EventSource#ITEM_PICKUP} anyone picking one up - asked where the item lies, a denial cancels it.
 * The pickup event fires every tick for every item next to someone, which is why it is a source of its own.
 */
public final class RegionItemListeners {

    private RegionItemListeners() {
    }

    static void decide(EventSource source, Item item, Actor actor, Cancellable event) {
        if (RegionContexts.isDisabled(item.getWorld())) {
            return;
        }
        ItemContext context = new ItemContext(source, RegionContexts.position(item.getLocation()), actor, event,
                RegionContexts.key(item.getItemStack()));
        if (RegionLookup.get().decide(context) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    public static final class Drop implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            decide(EventSource.ITEM_DROP, event.getItemDrop(), RegionContexts.actor(event.getPlayer()), event);
        }
    }

    /** From 1.12, where any living entity - a player, a zombie, an allay - picks up through one event. */
    public static final class Pickup implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onPickup(EntityPickupItemEvent event) {
            decide(EventSource.ITEM_PICKUP, event.getItem(), RegionContexts.actor(event.getEntity()), event);
        }
    }

    /** Before 1.12, where only a player's pickup has an event. */
    public static final class LegacyPickup implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }

        @SuppressWarnings("deprecation")   //the only pickup event before 1.12
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onPickup(PlayerPickupItemEvent event) {
            decide(EventSource.ITEM_PICKUP, event.getItem(), RegionContexts.actor(event.getPlayer()), event);
        }
    }
}
