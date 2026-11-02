package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.action.event.PickupActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The item listeners, one class per event so that each registers alone: {@link EventSource#ITEM_DROP} is a player
 * throwing an item, {@link EventSource#ITEM_PICKUP} anyone picking one up - asked where the item lies; a denial, or a
 * cancelled {@code Pre}, cancels it, and one that went through posts its {@link DropActionEvent} / {@link PickupActionEvent}
 * Post. The pickup event fires every tick for every item next to someone, which is why it is a source of its own.
 */
public final class ItemActionListeners {

    private ItemActionListeners() {
    }

    static ItemContext context(EventSource source, Item item, Actor actor, Cancellable event) {
        ItemStack stack = item.getItemStack();
        return new ItemContext(source, McActionContexts.position(item.getLocation()), actor, event, McActionContexts.key(stack),
                stack.getAmount(), item.getUniqueId(), McActionContexts.item(stack));
    }

    static void pickup(Item item, Actor actor, Cancellable event) {
        if (!McActionContexts.isDisabled(item.getWorld()) && Actions.denied(context(EventSource.ITEM_PICKUP, item, actor, event),
                PickupActionEvent.Pre.class, PickupActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    static void pickedUp(Item item, Actor actor, Cancellable event) {
        if (!McActionContexts.isDisabled(item.getWorld())) {
            Actions.post(PickupActionEvent.Post.class, () -> new PickupActionEvent.Post(context(EventSource.ITEM_PICKUP, item, actor, event)));
        }
    }

    public static final class Drop implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            Item item = event.getItemDrop();
            if (!McActionContexts.isDisabled(item.getWorld()) && Actions.denied(context(EventSource.ITEM_DROP, item,
                    McActionContexts.actor(event.getPlayer()), event), DropActionEvent.Pre.class, DropActionEvent.Pre::new)) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterDrop(PlayerDropItemEvent event) {
            Item item = event.getItemDrop();
            if (!McActionContexts.isDisabled(item.getWorld())) {
                Actions.post(DropActionEvent.Post.class, () -> new DropActionEvent.Post(context(EventSource.ITEM_DROP, item,
                        McActionContexts.actor(event.getPlayer()), event)));
            }
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
            pickup(event.getItem(), McActionContexts.actor(event.getEntity()), event);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterPickup(EntityPickupItemEvent event) {
            pickedUp(event.getItem(), McActionContexts.actor(event.getEntity()), event);
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
            pickup(event.getItem(), McActionContexts.actor(event.getPlayer()), event);
        }

        @SuppressWarnings("deprecation")
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterPickup(PlayerPickupItemEvent event) {
            pickedUp(event.getItem(), McActionContexts.actor(event.getPlayer()), event);
        }
    }
}
