package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;

import java.math.BigDecimal;

/**
 * A wallet of a {@link Currency} changed: exactly one per change, posted after it, on the thread that made it, and only
 * when somebody listens. A transfer posts one for each leg that moved (and one for a give-back).
 */
public class BalanceChangeEvent extends ECEvent implements IECEvent {

    public enum Cause { GIVE, TAKE, SET, TRANSFER_OUT, TRANSFER_IN, TRANSFER_REFUND }

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(BalanceChangeEvent.class);
    }

    private final Currency currency;
    private final CurrencyOwner owner;
    private final BigDecimal before;
    private final BigDecimal after;
    private final Cause cause;
    private final Caller caller;

    public BalanceChangeEvent(Currency currency, CurrencyOwner owner, BigDecimal before, BigDecimal after, Cause cause, Caller caller) {
        this.currency = currency;
        this.owner = owner;
        this.before = before;
        this.after = after;
        this.cause = cause;
        this.caller = caller;
    }

    public Currency getCurrency() {
        return currency;
    }

    public CurrencyOwner getOwner() {
        return owner;
    }

    public BigDecimal getBefore() {
        return before;
    }

    public BigDecimal getAfter() {
        return after;
    }

    /** {@code after - before}: negative for a take. */
    public BigDecimal getDelta() {
        return after.subtract(before);
    }

    public Cause getCause() {
        return cause;
    }

    public Caller getCaller() {
        return caller;
    }
}
