package br.com.finalcraft.evernifecore.api.events.base;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The Bukkit face of {@link IECEvent}: an EC event that extends this IS a Bukkit event, so a plain
 * {@code @EventHandler} listener can hear it once the bus mirrors it into the server.
 */
public abstract class ECEvent extends Event implements IECEvent {

    //ONE shared HandlerList for every EC event: an event declared in a platform-agnostic module
    //cannot give itself a static getHandlerList(), which is what Bukkit looks for up the hierarchy.
    //Bukkit's generated executor filters by the handler's parameter type, so a listener still only
    //hears its own event; the cost is that "does anyone listen?" can only be answered for the whole
    //family at once.
    private static final HandlerList handlers = new HandlerList();

    protected ECEvent() {
        //snapshot of the thread that built it - the producer's contract is to build and post on the
        //same thread, and Bukkit refuses a sync event posted off the main thread
        super(!Bukkit.isPrimaryThread());
    }

    protected ECEvent(boolean async) {
        super(async);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
