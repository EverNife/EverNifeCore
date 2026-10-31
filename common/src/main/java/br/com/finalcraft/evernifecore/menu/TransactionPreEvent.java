package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

/**
 * A trade passed its gate and limit and is about to take: cancel it ({@code CANCELLED}, nothing taken), change the
 * quantity, or change the totals through {@link #getQuote()} - a tax, a discount. Changing the quantity does not
 * recompute the totals. Posted only when somebody listens, on the trading thread.
 */
public class TransactionPreEvent extends ECEvent implements IECEvent, ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(TransactionPreEvent.class);
    }

    private final ExecutionContext context;
    private final Offer offer;
    private final Quote quote;
    private volatile int quantity;
    private volatile boolean cancelled;

    public TransactionPreEvent(ExecutionContext context, Offer offer, int quantity, Quote quote) {
        this.context = context;
        this.offer = offer;
        this.quantity = quantity;
        this.quote = quote;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public Offer getOffer() {
        return offer;
    }

    public int getQuantity() {
        return quantity;
    }

    /** @throws IllegalArgumentException when {@code quantity} is less than 1 - cancel the event to trade nothing */
    public void setQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("A trade of " + quantity + " units trades nothing: cancel the TransactionPreEvent instead.");
        }
        this.quantity = quantity;
    }

    /** The totals this trade will charge or pay, editable; {@code getQuote().getOriginal()} is what the offer asked. */
    public Quote getQuote() {
        return quote;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
