package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Forge side of a hybrid Bukkit+Forge server as an audience of the event bus.
 *
 * <p>The gate is the hybrid detection {@link ForgeListener} already does, and it is the only one:
 * a second probe could disagree with the first. A plain Spigot/Paper server answers false there and
 * never loads a Forge class through here, which is what makes this audience free to register
 * unconditionally.</p>
 *
 * <p><b>Nothing crosses yet.</b> A Forge bus takes a subclass of its era's {@code Event}
 * ({@code IEventBus.post(Event)}), and a subclass is a compiled class - reflection can call a bus,
 * it cannot become an event. Each Forge era declares that base under a different name, so the route
 * out is one compiled carrier per era, not one reflective call. Until a carrier exists this audience
 * has no listeners to report, and a hybrid server hears once that it is registered and idle.</p>
 */
public class McForgeAudience implements ECNativeAudience {

    /** The name the bus keys this audience by - re-registering under it replaces, never duplicates. */
    public static final String NAME = "forge";

    //Answered once per instance: the detection builds the inbound adapter as a side effect, and a
    //server whose adapter cannot initialize would otherwise pay for - and log - that failure on
    //every single post.
    private volatile Boolean hybrid;
    private final AtomicBoolean idleWarned = new AtomicBoolean();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Always {@code false}: an audience that cannot deliver has no listeners, whatever the Forge side
     * of this server registered. Answering the hybrid flag instead would hold every listener watch open
     * and build every gated event on exactly the hybrid servers this project runs on, for a bus nothing
     * reaches. The first question on a hybrid is where the idle notice is logged, once.
     */
    @Override
    public boolean hasListeners(Class<? extends IECEvent> eventType) {
        Boolean answer = hybrid;
        if (answer == null) {
            answer = detectHybrid();
            hybrid = answer;
        }
        if (answer && idleWarned.compareAndSet(false, true)) {
            EverNifeCore.getLog().warning("[ECEventBus] This server has a Forge side and the '{}' audience"
                    + " is registered, but no event can reach the Forge bus yet: posting one needs a"
                    + " compiled carrier extending this era's Forge Event class, and none is built."
                    + " Bukkit listeners are unaffected. This is said once.", NAME);
        }
        return false;
    }

    /** Unreachable while {@link #hasListeners(Class)} answers false; kept a no-op so a caller that bypasses the gate loses nothing. */
    @Override
    public void dispatch(IECEvent event) {
    }

    /**
     * The detection swallows its own failure the way {@link #dispatch(IECEvent)} has to: a hybrid
     * whose adapter refuses to initialize is a server where nothing can be mirrored, not a reason to
     * break whoever posted the event.
     */
    private static boolean detectHybrid() {
        try {
            return ForgeListener.isAvailable();
        } catch (Throwable t) {
            EverNifeCore.getLog().severe("[ECEventBus] The forge audience could not tell whether this"
                    + " server has a Forge side, so it stays silent for the rest of this run.", t);
            return false;
        }
    }

}
