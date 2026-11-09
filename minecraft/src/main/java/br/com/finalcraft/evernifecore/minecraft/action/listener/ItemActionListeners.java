package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.action.event.PickupActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The item listeners, one class per event so that each registers alone: {@link EventSource#ITEM_DROP} is a player
 * throwing an item, {@link EventSource#ITEM_PICKUP} anyone picking one up - asked where the item lies; a denial, or a
 * cancelled {@code Pre}, cancels it, and one that went through posts its {@link DropActionEvent} / {@link PickupActionEvent}
 * Post. A drop let through that a flag marked ({@link ItemContext#isItemDestroyed()}) never enters the world: it posts
 * its Post, empties the entity and cancels the event, all at {@code LOW}, so a plugin at {@code NORMAL} or later reads a
 * cancelled drop of air. Any other drop keeps its owner ({@link ItemContext#getItemOwner()}) for the pickups, hoppers and
 * merges of that item. The pickup event fires every tick for every item next to someone, which is why it is a source of its own.
 */
public final class ItemActionListeners {

    // reason-detre: in memory, so a restart or a chunk unload frees the item; Item#setOwner would survive both, but vanilla then
    // refuses by itself, outside the region and past the bypass, and it exists only on the newer eras
    private static final Map<Item, UUID> OWNERS = Collections.synchronizedMap(new WeakHashMap<>()); //an entity gone from the world frees its key

    private ItemActionListeners() {
    }

    static ItemContext context(EventSource source, Item item, Actor actor, Cancellable event) {
        ItemStack stack = item.getItemStack();
        ItemContext context = new ItemContext(source, McActionContexts.position(item.getLocation()), actor, event, McActionContexts.key(stack),
                stack.getAmount(), item.getUniqueId(), McActionContexts.item(stack));
        context.setItemOwner(OWNERS.get(item));
        return context;
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

        private final Set<Item> destroyed = Collections.newSetFromMap(new WeakHashMap<>()); //a cancelled entity never spawned: nothing else holds it

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            Item item = event.getItemDrop();
            if (McActionContexts.isDisabled(item.getWorld())) {
                return;
            }
            ItemContext context = context(EventSource.ITEM_DROP, item, McActionContexts.actor(event.getPlayer()), event);
            if (Actions.denied(context, DropActionEvent.Pre.class, DropActionEvent.Pre::new)) {
                event.setCancelled(true);
                return;
            }
            if (context.isItemDestroyed()) {
                destroyed.add(item);
                postDropped(event); //while the entity still holds the item the Post describes
                item.setItemStack(new ItemStack(Material.AIR)); //the server gives a cancelled drop's stack back to the player: nothing comes back
                event.setCancelled(true);
                return;
            }
            if (context.getItemOwner() != null) {
                OWNERS.put(item, context.getItemOwner());
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterDrop(PlayerDropItemEvent event) {
            Item item = event.getItemDrop();
            //one onDrop destroyed reaches here only if another plugin uncancelled it: its Post already went out
            if (!destroyed.remove(item) && !McActionContexts.isDisabled(item.getWorld())) {
                postDropped(event);
            }
        }

        private static void postDropped(PlayerDropItemEvent event) {
            Actions.post(DropActionEvent.Post.class, () -> new DropActionEvent.Post(context(EventSource.ITEM_DROP, event.getItemDrop(),
                    McActionContexts.actor(event.getPlayer()), event)));
        }
    }

    /** Keeps two piles of different owners apart, from 1.8; before that the server merges them with no event, and the pile left keeps its own owner. */
    public static final class Merge implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onMerge(ItemMergeEvent event) {
            if (!Objects.equals(OWNERS.get(event.getEntity()), OWNERS.get(event.getTarget()))) {
                event.setCancelled(true);
            }
        }
    }

    /** A hopper or a hopper minecart pulling an item off the ground; it has no action event, only the regions are asked. */
    public static final class HopperPickup implements ECListener {

        private static final TypeKey HOPPER = TypeKey.of("environment", "hopper");

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onPickup(InventoryPickupItemEvent event) {
            Item item = event.getItem();
            if (!McActionContexts.isDisabled(item.getWorld())
                    && Actions.denied(context(EventSource.ITEM_HOPPER_PICKUP, item, Actor.environment(HOPPER), event))) {
                event.setCancelled(true);
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
