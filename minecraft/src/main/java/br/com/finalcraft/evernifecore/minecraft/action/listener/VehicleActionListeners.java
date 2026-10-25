package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.VehicleContext;
import br.com.finalcraft.evernifecore.action.event.VehicleActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The vehicle listeners ({@link EventSource#VEHICLE}), one class per era of the place event so that each registers
 * alone: breaking and entering have had their events since 1.7.10; placing has {@link EntityPlaceEvent} from 1.13 and,
 * before it, only the click of a minecart or boat item. A denial, or a cancelled {@code Pre}, cancels the event; one that
 * went through posts its {@link VehicleActionEvent} Post.
 */
public final class VehicleActionListeners {

    /** Who breaks or places a vehicle when no entity does: a cactus, lava, a dispenser. */
    static final TypeKey NO_ENTITY = TypeKey.of("environment", "vehicle");

    private VehicleActionListeners() {
    }

    static boolean denied(Location at, Actor actor, Object event, VehicleContext.Action action, @Nullable Actor vehicle) {
        if (McActionContexts.isDisabled(at.getWorld())) {
            return false;
        }
        return Actions.denied(new VehicleContext(McActionContexts.position(at), actor, event, action, vehicle), VehicleActionEvent.Pre.class,
                VehicleActionEvent.Pre::new);
    }

    /** The Post; the actors are built only when somebody listens. */
    static void post(Location at, Supplier<Actor> actor, Object event, VehicleContext.Action action,
                     Supplier<Actor> vehicle) {
        if (!McActionContexts.isDisabled(at.getWorld())) {
            Actions.post(VehicleActionEvent.Post.class, () -> new VehicleActionEvent.Post(new VehicleContext(McActionContexts.position(at),
                    actor.get(), event, action, vehicle.get())));
        }
    }

    /** Breaking and entering, in every era. */
    public static final class BreakAndEnter implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDestroy(VehicleDestroyEvent event) {
            Vehicle vehicle = event.getVehicle();
            if (denied(vehicle.getLocation(), destroyer(event), event, VehicleContext.Action.BREAK, McActionContexts.actor(vehicle))) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterDestroy(VehicleDestroyEvent event) {
            Vehicle vehicle = event.getVehicle();
            post(vehicle.getLocation(), () -> destroyer(event), event, VehicleContext.Action.BREAK, () -> McActionContexts.actor(vehicle));
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onEnter(VehicleEnterEvent event) {
            Vehicle vehicle = event.getVehicle();
            if (denied(vehicle.getLocation(), McActionContexts.actor(event.getEntered()), event, VehicleContext.Action.ENTER,
                    McActionContexts.actor(vehicle))) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterEnter(VehicleEnterEvent event) {
            Vehicle vehicle = event.getVehicle();
            post(vehicle.getLocation(), () -> McActionContexts.actor(event.getEntered()), event, VehicleContext.Action.ENTER,
                    () -> McActionContexts.actor(vehicle));
        }

        private static Actor destroyer(VehicleDestroyEvent event) {
            Entity attacker = event.getAttacker();
            return attacker != null ? McActionContexts.actor(attacker) : Actor.environment(NO_ENTITY);
        }
    }

    /** Placing from 1.13: the event names the vehicle being placed and who places it - nobody, for a dispenser. */
    public static final class Place implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onPlace(EntityPlaceEvent event) {
            Entity placed = event.getEntity();
            if (placed instanceof Vehicle   //armor stands and end crystals come through this event too
                    && denied(placed.getLocation(), placer(event), event, VehicleContext.Action.PLACE, McActionContexts.actor(placed))) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void afterPlace(EntityPlaceEvent event) {
            Entity placed = event.getEntity();
            if (placed instanceof Vehicle) {
                post(placed.getLocation(), () -> placer(event), event, VehicleContext.Action.PLACE, () -> McActionContexts.actor(placed));
            }
        }

        private static Actor placer(EntityPlaceEvent event) {
            return event.getPlayer() != null ? McActionContexts.actor(event.getPlayer()) : Actor.environment(NO_ENTITY);
        }
    }

    /**
     * Placing before 1.13: a right click with a minecart or a boat, asked at the clicked block - or where the player
     * stands, for a boat put on water through the air. A denial takes the item's use away and leaves the click's use of
     * the block alone; a click whose item use survived posts the Post, with no vehicle yet.
     */
    public static final class LegacyPlace implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onClick(PlayerInteractEvent event) {
            if (isVehiclePlace(event) && denied(at(event), McActionContexts.actor(event.getPlayer()), event, VehicleContext.Action.PLACE, null)) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void afterClick(PlayerInteractEvent event) {
            if (isVehiclePlace(event)) {
                post(at(event), () -> McActionContexts.actor(event.getPlayer()), event, VehicleContext.Action.PLACE, () -> null);
            }
        }

        private static boolean isVehiclePlace(PlayerInteractEvent event) {
            return (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)
                    && event.useItemInHand() != Event.Result.DENY && isVehicleItem(event.getItem());
        }

        private static Location at(PlayerInteractEvent event) {
            return event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : event.getPlayer().getLocation();
        }

        static boolean isVehicleItem(@Nullable ItemStack item) {
            if (item == null) {
                return false;
            }
            String name = item.getType().name().toUpperCase(Locale.ROOT);
            return name.endsWith("MINECART") || name.contains("BOAT") || name.endsWith("_RAFT");
        }
    }
}
