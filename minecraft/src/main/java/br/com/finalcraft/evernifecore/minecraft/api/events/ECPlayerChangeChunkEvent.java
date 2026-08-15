package br.com.finalcraft.evernifecore.minecraft.api.events;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Called when a player moves from one chunk to another. Produced only while somebody listens - the
 * core registers its {@code PlayerMoveEvent} listener on the first listener of this event and drops it
 * with the last, whether that listener sits on the bus or on the server.
 *
 * @author EverNife
 */
public class ECPlayerChangeChunkEvent extends ECEvent implements ECCancellable {

    public static HandlerList getHandlerList() {
        return (HandlerList) ECEvent.getHandlerListOf(ECPlayerChangeChunkEvent.class);
    }

    private final PlayerMoveEvent playerMoveEvent;
    private final Chunk from;
    private final Chunk to;

    public ECPlayerChangeChunkEvent(PlayerMoveEvent originalEvent, Chunk from, Chunk to) {
        this.from = from;
        this.to = to;
        this.playerMoveEvent = originalEvent;
    }

    /**
     * Get the Original PlayerMoveEvent
     *
     * @return The {@link PlayerMoveEvent}
     * @author EverNife
     */
    public PlayerMoveEvent getOriginalEvent() {
        return playerMoveEvent;
    }

    /**
     * The origin chunk.
     *
     * @return origin chunk
     * @author EverNife
     */
    public Chunk getFrom() {
        return from;
    }

    /**
     * The target chunk.
     *
     * @return target chunk
     * @author EverNife
     */
    public Chunk getTo() {
        return to;
    }

    @Override
    public boolean isCancelled() {
        return playerMoveEvent.isCancelled();
    }

    /**
     * Get the player from this event
     *
     * @return The player that crafted
     * @author EverNife
     */
    public Player getPlayer() {
        return playerMoveEvent.getPlayer();
    }

    @Override
    public void setCancelled(boolean cancel) {
        playerMoveEvent.setCancelled(cancel);
    }

}
