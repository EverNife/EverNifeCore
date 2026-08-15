package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECPlayerChangeChunkEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Produces {@link ECPlayerChangeChunkEvent}. Registered with the server only while somebody listens
 * to that event - {@link ECListener#registerWhileListened} - so the hot {@code PlayerMoveEvent} costs nothing on
 * a server where nobody asked.
 */
public class PlayerMoveListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        //comes and goes with the listeners of what it produces; logging each turn would be noise
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        //chunk coordinates are arithmetic on the location; getChunk() is a chunk-map lookup plus an
        //allocation, so it only runs on an actual crossing - and only for a listener, inside the supplier
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)
                && from.getWorld().equals(to.getWorld())) {
            return;
        }
        ECEventBus.global().postIfListened(ECPlayerChangeChunkEvent.class,
                () -> new ECPlayerChangeChunkEvent(event, from.getChunk(), to.getChunk()));
    }

}
