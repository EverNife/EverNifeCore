package br.com.finalcraft.evernifecore.hytale.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.server.core.HytaleServer;

import java.util.function.Supplier;

/**
 * The Hytale server as an audience of the event bus. An {@link ECEvent} IS a Hytale event here, so a
 * plugin's own {@code registerGlobal} consumer hears it once the bus mirrors it.
 */
public class HyHytaleAudience implements ECNativeAudience {

    /** The name the bus keys this audience by - re-registering under it replaces, never duplicates. */
    public static final String NAME = "hytale";

    //resolved per call, never cached: the audience is built during setup, and the server that owns
    //the bus is asked for it only when there is something to mirror
    private final Supplier<IEventBus> eventBus;

    public HyHytaleAudience() {
        this(() -> HytaleServer.get().getEventBus());
    }

    /** An audience over a given bus, for a caller that has one without a server around it. */
    HyHytaleAudience(Supplier<IEventBus> eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean hasListeners(IECEvent event) {
        IEventBus bus = eventBus.get();
        for (Class<?> level = event.getClass(); isMirroredLevel(level); level = level.getSuperclass()) {
            if (dispatcherFor(bus, level).hasListener()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispatch(IECEvent event) {
        //Hytale dispatches by EXACT class, so climbing the chain here is what keeps "subscribe to the
        //base, hear the family" true on this platform. The chains are two or three levels long and
        //each one is gated on its own.
        IEventBus bus = eventBus.get();
        for (Class<?> level = event.getClass(); isMirroredLevel(level); level = level.getSuperclass()) {
            try {
                IEventDispatcher dispatcher = dispatcherFor(bus, level);
                if (dispatcher.hasListener()) {
                    dispatcher.dispatch((IEvent) event);
                }
            } catch (Throwable t) {
                EverNifeCore.getLog().severe("[ECEventBus] Mirroring " + event.getClass().getName()
                        + " into the hytale listeners of " + level.getName() + " failed; the levels "
                        + "above it in the chain still run.", t);
            }
        }
    }

    /**
     * The dispatcher {@code dispatchFor} hands back is the only one whose {@code hasListener()} can be
     * trusted: {@link IEventDispatcher} defaults that method to {@code true}, and the answer that
     * matters is the no-op's {@code false}. Asking creates the class's registry, which is the price of
     * a gate that tells the truth.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IEventDispatcher dispatcherFor(IEventBus bus, Class<?> level) {
        return bus.dispatchFor((Class) level);
    }

    /**
     * Every class from the event down to - and not including - the platoverride base. {@link ECEvent}
     * is where the family stops being a family: a consumer registered on it would be asking for every
     * EC event there is.
     */
    private static boolean isMirroredLevel(Class<?> type) {
        return type != null && ECEvent.class.isAssignableFrom(type) && type != ECEvent.class;
    }

}
