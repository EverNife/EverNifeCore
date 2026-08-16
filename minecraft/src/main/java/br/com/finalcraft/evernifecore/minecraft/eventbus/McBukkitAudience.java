package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.List;

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
     * Whether the list Bukkit registers a listener of {@code eventType} into has anyone in it - the
     * event's own list when it declares {@code getHandlerList}, the family list otherwise, resolved
     * exactly as the plugin manager resolves it. Registrations into an EC list push a refresh of the
     * listener watches through {@link ECHandlerList}, so a watch follows a Bukkit listener as it does
     * a bus subscriber.
     */
    @Override
    public boolean hasListeners(Class<? extends IECEvent> eventType) {
        return McHandlerLists.registrationListOf(eventType).getRegisteredListeners().length > 0;
    }

    /**
     * {@code plugin listenerClass @PRIORITY}, one per registration in the list a listener of {@code
     * eventType} lands in - which, for an event that declares no list of its own, is the family list
     * and so names the listeners of its siblings too, exactly as {@link #hasListeners(Class)} counts them.
     */
    @Override
    public List<String> describeListeners(Class<? extends IECEvent> eventType) {
        List<String> lines = new ArrayList<>();
        for (RegisteredListener registered : McHandlerLists.registrationListOf(eventType).getRegisteredListeners()) {
            lines.add(registered.getPlugin().getName() + " " + registered.getListener().getClass().getName()
                    + " @" + registered.getPriority());
        }
        return lines;
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
        EverNifeCore.getLog().severe("[ECEventBus] {} was built {}"
                        + " but posted {}, so this post reached the bus subscribers and NO Bukkit "
                        + "listener. Build and post the event on the same thread; an event that is always "
                        + "asynchronous says so through the ECEvent(true) constructor. The stack below is the "
                        + "producer.",
                event.getEventName(), built, posted, new Throwable("Posted " + event.getEventName() + " " + posted));
    }

}
