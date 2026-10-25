package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.event.FlightToggleActionEvent;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * Keeps a player from landing inside a region with {@code force-fly = TRUE} ({@link EventSource#FLIGHT_TOGGLE}), then
 * posts the {@link FlightToggleActionEvent} pair of every toggle: a cancelled {@code Pre} keeps the player as they were.
 */
public final class FlightToggleActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Location at = event.getPlayer().getLocation();
        if (McActionContexts.isDisabled(at.getWorld())) {
            return;
        }
        if (!event.isFlying() && RegionLookup.get().resolve(CoreFlags.FORCE_FLY, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                FCBukkitUtil.adapt(event.getPlayer())) == Tristate.TRUE) {
            event.setCancelled(true);
            return;
        }
        if (Actions.cancelled(FlightToggleActionEvent.Pre.class, () -> new FlightToggleActionEvent.Pre(context(event)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterToggleFlight(PlayerToggleFlightEvent event) {
        if (!McActionContexts.isDisabled(event.getPlayer().getWorld())) {
            Actions.post(FlightToggleActionEvent.Post.class, () -> new FlightToggleActionEvent.Post(context(event)));
        }
    }

    private static SimpleContext context(PlayerToggleFlightEvent event) {
        return new SimpleContext(EventSource.FLIGHT_TOGGLE, McActionContexts.position(event.getPlayer().getLocation()),
                McActionContexts.actor(event.getPlayer()), event);
    }
}
