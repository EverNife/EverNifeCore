package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import jakarta.annotation.Nullable;

/**
 * A pending {@link Delivery} was kept, handed out, dropped by its expiry, purged by an admin, or - for a delayed
 * effect whose player left - discarded because the core cannot keep what another plugin's effect needs. Posted only
 * when somebody listens, on the thread that did it.
 */
public class DeliveryEvent extends ECEvent implements IECEvent {

    public enum Kind { ENQUEUED, DELIVERED, EXPIRED, PURGED, DISCARDED }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(DeliveryEvent.class);
    }

    private final Kind kind;
    private final Delivery delivery;
    private final @Nullable String by;

    public DeliveryEvent(Kind kind, Delivery delivery, @Nullable String by) {
        this.kind = kind;
        this.delivery = delivery;
        this.by = by;
    }

    public Kind getKind() {
        return kind;
    }

    /** The delivery as it was when this happened; a DISCARDED one holds what was thrown away. */
    public Delivery getDelivery() {
        return delivery;
    }

    /** Who purged it ({@code Caller} text), null for every other kind. */
    public @Nullable String getBy() {
        return by;
    }
}
