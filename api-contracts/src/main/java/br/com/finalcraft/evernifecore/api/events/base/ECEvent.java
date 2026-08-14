package br.com.finalcraft.evernifecore.api.events.base;

/**
 * The base of every EC event that is meant to be seen by the platforms. Each platform ships the real
 * class under this same name: on Bukkit it IS an {@code org.bukkit.event.Event}, on Hytale an
 * {@code IEvent<Void>}. Extending it is the whole statement that the event may be mirrored into the
 * native buses - an event that must stay inside EverNifeCore implements {@code IECEvent} and stops
 * there, and no audience can ever see it.
 *
 * <p>A subclass must not declare {@code isAsynchronous}, {@code getEventName}, {@code getHandlers},
 * {@code getHandlerList} or {@code callEvent}: on Bukkit the real base is {@code Event}, where those
 * are its plumbing and {@code isAsynchronous} is final - redeclaring one is a VerifyError at
 * classload. Cancellation goes through {@code ECCancellable}.</p>
 *
 * <p>This stub deliberately does NOT implement {@code IECEvent}: that interface lives in
 * {@code common}, which already depends on this module, and the reverse dependency is a build cycle.
 * An event declared in a platform-agnostic module therefore says both -
 * {@code extends ECEvent implements IECEvent}.</p>
 */
public abstract class ECEvent {

    /** Takes the async flag from the thread that builds the event, on a platform that has one. */
    protected ECEvent() {
    }

    /** For an event that is asynchronous by nature, whichever thread happens to build it. */
    protected ECEvent(boolean async) {
    }

}
