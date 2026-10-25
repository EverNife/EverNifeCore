package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/** Keeps a player from landing inside a region with {@code force-fly = TRUE} ({@link EventSource#FLIGHT_TOGGLE}). */
public final class RegionFlightToggleListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying()) {
            return;
        }
        Location at = event.getPlayer().getLocation();
        if (RegionContexts.isDisabled(at.getWorld())) {
            return;
        }
        Tristate forced = RegionLookup.get().resolve(CoreFlags.FORCE_FLY, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                FCBukkitUtil.adapt(event.getPlayer()));
        if (forced == Tristate.TRUE) {
            event.setCancelled(true);
        }
    }
}
