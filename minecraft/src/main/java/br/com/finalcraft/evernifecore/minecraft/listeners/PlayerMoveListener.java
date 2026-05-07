package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECPlayerChangeChunkEvent;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Note: This listener is registered only once
 * and on Demand by the Event:
 *
 * @see ECPlayerChangeChunkEvent
 */
public class PlayerMoveListener implements ECListener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo().getChunk();
        if (from != to){
            EverNifeCoreBukkitPlugin.instance.getServer().getPluginManager().callEvent(new ECPlayerChangeChunkEvent(event, from, to));
        }
    }

}
