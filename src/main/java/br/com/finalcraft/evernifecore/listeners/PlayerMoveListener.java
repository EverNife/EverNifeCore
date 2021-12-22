package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.ECPlayerChangeChunkEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements ECListener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo().getChunk();
        if (from != to){
            EverNifeCore.instance.getServer().getPluginManager().callEvent(new ECPlayerChangeChunkEvent(event, from, to));
        }
    }

}
