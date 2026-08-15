package br.com.finalcraft.evernifecore.api.events.base;

/**
 * The base of every EC event that is meant to be seen by the platforms. Each platform ships the real
 * class under this same name: on Bukkit it IS an {@code org.bukkit.event.Event}, on Hytale an
 * {@code IEvent<Void>}. Extending it is the whole statement that the event may be mirrored into the
 * native buses - an event that must stay inside EverNifeCore implements {@code IECEvent} and stops
 * there, and no audience can ever see it.
 *
 * <p>A subclass must not declare {@code isAsynchronous}, {@code getEventName}, {@code getHandlers} or
 * {@code callEvent}: on Bukkit the real base is {@code Event}, where those are its plumbing and
 * {@code isAsynchronous} is final - redeclaring one is a VerifyError at classload. Cancellation goes
 * through {@code ECCancellable}.</p>
 *
 * <p>{@code getHandlerList} is the one Bukkit member an event MAY declare, and should: Bukkit finds the
 * list a native listener registers into by looking, up the hierarchy, for a static method of that
 * name, so an event that declares one gets a list of its own - and with it a native gate that answers
 * for that event alone, instead of the family-wide fallback every undeclared EC event shares. It is
 * declared once, on the class native listeners will subscribe to (a subclass that declares its own
 * leaves the family: a native listener on the parent stops hearing it), and it reads:</p>
 *
 * <pre>{@code
 * public static Object getHandlerList() {
 *     return ECEvent.getHandlerListOf(ShopPurchaseEvent.class);
 * }
 * }</pre>
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

    /**
     * Bukkit-only plumbing an event may name without importing Bukkit: the native handler list of
     * {@code eventType}, created on first ask and shared by every subclass that does not declare a
     * {@code getHandlerList} of its own. Typed {@code Object} because the list's class is not visible
     * here; on Bukkit the value IS an {@code org.bukkit.event.HandlerList}, on any other platform it is
     * {@code null} and nothing ever calls the {@code getHandlerList} that returns it.
     */
    public static Object getHandlerListOf(Class<? extends ECEvent> eventType) {
        return null;
    }

}
