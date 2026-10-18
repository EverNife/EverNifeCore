package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.VehicleContext;
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

/**
 * The vehicle listeners ({@link EventSource#VEHICLE}), one class per era of the place event so that each registers
 * alone: breaking and entering have had their events since 1.7.10; placing has {@link EntityPlaceEvent} from 1.13 and,
 * before it, only the click of a minecart or boat item. A denial cancels the event.
 */
public final class RegionVehicleListeners {

    /** Who breaks or places a vehicle when no entity does: a cactus, lava, a dispenser. */
    static final TypeKey NO_ENTITY = TypeKey.of("environment", "vehicle");

    private RegionVehicleListeners() {
    }

    static boolean denied(Location at, Actor actor, Object event, VehicleContext.Action action, @Nullable Actor vehicle) {
        if (RegionContexts.isDisabled(at.getWorld())) {
            return false;
        }
        return RegionLookup.get().decide(new VehicleContext(RegionContexts.position(at), actor, event, action, vehicle)) == Decision.DENY;
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
            Entity attacker = event.getAttacker();
            Actor actor = attacker != null ? RegionContexts.actor(attacker) : Actor.environment(NO_ENTITY);
            if (denied(vehicle.getLocation(), actor, event, VehicleContext.Action.BREAK, RegionContexts.actor(vehicle))) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onEnter(VehicleEnterEvent event) {
            Vehicle vehicle = event.getVehicle();
            if (denied(vehicle.getLocation(), RegionContexts.actor(event.getEntered()), event, VehicleContext.Action.ENTER,
                    RegionContexts.actor(vehicle))) {
                event.setCancelled(true);
            }
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
            if (!(placed instanceof Vehicle)) {
                return;   //armor stands and end crystals come through this event too
            }
            Actor actor = event.getPlayer() != null ? RegionContexts.actor(event.getPlayer()) : Actor.environment(NO_ENTITY);
            if (denied(placed.getLocation(), actor, event, VehicleContext.Action.PLACE, RegionContexts.actor(placed))) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Placing before 1.13: a right click with a minecart or a boat, asked at the clicked block - or where the player
     * stands, for a boat put on water through the air. A denial takes the item's use away and leaves the click's use of
     * the block alone.
     */
    public static final class LegacyPlace implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onClick(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR
                    || event.useItemInHand() == Event.Result.DENY || !isVehicleItem(event.getItem())) {
                return;
            }
            Location at = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : event.getPlayer().getLocation();
            if (denied(at, RegionContexts.actor(event.getPlayer()), event, VehicleContext.Action.PLACE, null)) {
                event.setUseItemInHand(Event.Result.DENY);
            }
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
