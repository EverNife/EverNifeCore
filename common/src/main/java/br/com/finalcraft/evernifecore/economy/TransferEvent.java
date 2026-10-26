package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * One {@link Currency#transfer} that reached the currency, whatever its outcome: posted once, after it, on the thread
 * that made it, and only when somebody listens.
 */
public class TransferEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(TransferEvent.class);
    }

    private final Currency currency;
    private final CurrencyOwner from;
    private final CurrencyOwner to;
    private final BigDecimal amount;
    private final EcoResponse take;
    private final EcoResponse give;
    private final EcoResponse refund;
    private final Caller caller;

    public TransferEvent(Currency currency, CurrencyOwner from, CurrencyOwner to, BigDecimal amount, EcoResponse take,
                         @Nullable EcoResponse give, @Nullable EcoResponse refund, Caller caller) {
        this.currency = currency;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.take = take;
        this.give = give;
        this.refund = refund;
        this.caller = caller;
    }

    public Currency getCurrency() {
        return currency;
    }

    public CurrencyOwner getFrom() {
        return from;
    }

    public CurrencyOwner getTo() {
        return to;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** The first leg, from the payer. */
    public EcoResponse getTake() {
        return take;
    }

    /** The second leg, to the receiver; null when the take already failed. */
    public @Nullable EcoResponse getGive() {
        return give;
    }

    /** The give-back to the payer after a failed second leg; null when there was none. */
    public @Nullable EcoResponse getRefund() {
        return refund;
    }

    public Caller getCaller() {
        return caller;
    }

    /** Both legs moved. */
    public boolean isSuccess() {
        return take.isSuccess() && give != null && give.isSuccess();
    }
}
