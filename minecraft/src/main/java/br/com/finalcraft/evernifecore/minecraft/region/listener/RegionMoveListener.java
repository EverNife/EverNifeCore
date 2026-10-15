package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.world.PassengerAccess;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.MoveVerdict;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

/**
 * Hands every player's move across a block to the {@link RegionMovementTracker} and applies its verdict: a denied step
 * is put back with {@code setTo}, a denied teleport is cancelled, a denied vehicle ride sends the whole vehicle back.
 * Join, quit, respawn and world change only reconcile the session, reading the state the other plugins left.
 */
public final class RegionMoveListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || RegionContexts.isDisabled(to.getWorld()) || sameBlock(from, to)) {
            return;   //a head turn is most of the traffic; the tracker is only asked on a block change
        }
        MoveVerdict verdict = RegionMovementTracker.get().onMove(FCBukkitUtil.adapt(event.getPlayer()),
                RegionContexts.position(from), RegionContexts.position(to), MoveCause.MOVE, event);
        if (!verdict.isAllowed()) {
            event.setTo(RegionContexts.toLocation(verdict.getRevertTo(), to));   //not setCancelled: no teleport event, no re-entry
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || RegionContexts.isDisabled(to.getWorld())) {
            return;
        }
        MoveVerdict verdict = RegionMovementTracker.get().onMove(FCBukkitUtil.adapt(event.getPlayer()),
                RegionContexts.position(event.getFrom()), RegionContexts.position(to), MoveCause.TELEPORT, event);
        if (!verdict.isAllowed()) {
            event.setCancelled(true);   //a teleport "to where you already stand" would report success to whoever teleported
        }
    }

    @EventHandler(priority = EventPriority.HIGH)   //VehicleMoveEvent is not cancellable
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        Location to = event.getTo();
        if (RegionContexts.isDisabled(to.getWorld()) || sameBlock(event.getFrom(), to)) {
            return;
        }
        List<Entity> passengers = passengersOf(vehicle);
        for (Entity passenger : passengers) {
            if (!(passenger instanceof Player)) {
                continue;
            }
            MoveVerdict verdict = RegionMovementTracker.get().onMove(FCBukkitUtil.adapt((Player) passenger),
                    RegionContexts.position(event.getFrom()), RegionContexts.position(to), MoveCause.VEHICLE, event);
            if (!verdict.isAllowed()) {
                Location back = RegionContexts.toLocation(verdict.getRevertTo(), to);
                vehicle.eject();   //the whole vehicle goes back, so every passenger leaves it
                vehicle.setVelocity(new Vector());
                vehicle.teleport(back);
                for (Entity rider : passengers) {
                    rider.teleport(back);
                }
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")   //getPassenger is the only form below 1.11; from there a vehicle may carry any number
    private static List<Entity> passengersOf(Vehicle vehicle) {
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_11_R1)) {
            return PassengerAccess.of(vehicle);
        }
        Entity passenger = vehicle.getPassenger();
        return passenger == null ? Collections.<Entity>emptyList() : Collections.singletonList(passenger);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!RegionContexts.isDisabled(player.getWorld())) {
            MoveVerdict verdict = RegionMovementTracker.get().onJoin(FCBukkitUtil.adapt(player), RegionContexts.position(player.getLocation()));
            if (!verdict.isAllowed()) {
                player.teleport(RegionContexts.toLocation(verdict.getRevertTo(), player.getLocation()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        //the core's quit event also ends the session, but only for a player with PlayerData; a second call does nothing
        RegionMovementTracker.get().onQuit(FCBukkitUtil.adapt(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Location to = event.getRespawnLocation();
        if (!RegionContexts.isDisabled(to.getWorld())) {   //the player still stands where they died: that is the from
            RegionMovementTracker.get().onMove(FCBukkitUtil.adapt(event.getPlayer()),
                    RegionContexts.position(event.getPlayer().getLocation()), RegionContexts.position(to), MoveCause.RESPAWN, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Location now = event.getPlayer().getLocation();
        if (!RegionContexts.isDisabled(now.getWorld())) {
            WorldLocPos from = WorldLocPos.of(now.getX(), now.getY(), now.getZ(), event.getFrom().getName());
            RegionMovementTracker.get().onMove(FCBukkitUtil.adapt(event.getPlayer()), from, RegionContexts.position(now), MoveCause.WORLD_CHANGE, event);
        }
    }

    /**
     * Everyone online is inside wherever they stand, unasked, unless a region there expels them: run when this listener
     * arrives on a server that already has players, so the first step of someone standing in a {@code deny-entry} region
     * is not an entry. Their greetings play again, with cause {@code JOIN}.
     */
    static void seedOnlinePlayers() {
        RegionMovementTracker tracker = RegionMovementTracker.get();
        for (FPlayer player : EverNifeCore.getPlatform().getOnlinePlayers()) {
            FLocation at = player.getLocation();
            if (at != null && !ECSettings.REGIONS_DISABLED_WORLDS.contains(at.getWorldName())) {
                MoveVerdict verdict = tracker.onJoin(player, at.getWorldLocPos());
                if (!verdict.isAllowed()) {
                    player.teleportTo(new FLocation(verdict.getRevertTo(), null));
                }
            }
        }
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld().equals(to.getWorld());
    }
}
