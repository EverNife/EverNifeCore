package br.com.finalcraft.evernifecore.api.events;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.PlayerMoveListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Called when a player move from one chunk to another
 *
 * @author EverNife
 */
public class ECPlayerChangeChunkEvent extends Event implements Cancellable {

    private static boolean hasBeenRegistered = false;
    private static final HandlerList handlers = new HandlerList(){
        @Override
        public synchronized void register(RegisteredListener listener) {
            super.register(listener);
            if (hasBeenRegistered == false){
                hasBeenRegistered = true;
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        ECListener.register(EverNifeCore.instance, PlayerMoveListener.class);
                    }
                }.runTaskLater(EverNifeCore.instance, 1);
            }
        }
    };

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

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
