package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * The Bukkit server as an audience of the event bus. An {@link ECEvent} IS a Bukkit event here, so
 * mirroring is a single {@code callEvent} and a plain {@code @EventHandler} listener hears it.
 */
public class McBukkitAudience implements ECNativeAudience {

    /** The name the bus keys this audience by - re-registering under it replaces, never duplicates. */
    public static final String NAME = "bukkit";

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Whether ANY EC event has a Bukkit listener. Every EC event shares one HandlerList - an event
     * declared away from Bukkit cannot give itself a {@code static getHandlerList()} - so this can
     * only answer for the whole family: it opens for an event nobody listens to as long as a sibling
     * has a listener. What that costs is one {@code callEvent} whose executors filter by type anyway.
     */
    @Override
    public boolean hasListeners(IECEvent event) {
        return ECEvent.getHandlerList().getRegisteredListeners().length > 0;
    }

    @Override
    public void dispatch(IECEvent event) {
        //The bus only mirrors an ECEvent, and on this platform that base extends org.bukkit.event.Event.
        Event bukkitEvent = (Event) event;

        boolean offMain = !Bukkit.isPrimaryThread();
        if (bukkitEvent.isAsynchronous() != offMain) {
            logThreadMismatch(bukkitEvent, offMain);
            return;
        }

        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }

    /**
     * Skipping the native phase turns a floor hazard into something an operator can act on: a sync
     * event fired off the main thread does not throw on the old servers, it silently serializes every
     * caller on the plugin manager's global monitor.
     */
    private static void logThreadMismatch(Event event, boolean offMain) {
        String built = event.isAsynchronous() ? "asynchronous" : "synchronous";
        String posted = offMain ? "off the main thread" : "on the main thread";
        EverNifeCore.getLog().severe("[ECEventBus] " + event.getEventName() + " was built " + built
                + " but posted " + posted + ", so this post reached the bus subscribers and NO Bukkit "
                + "listener. Build and post the event on the same thread; an event that is always "
                + "asynchronous says so through the ECEvent(true) constructor. The stack below is the "
                + "producer.", new Throwable("Posted " + event.getEventName() + " " + posted));
    }

}
