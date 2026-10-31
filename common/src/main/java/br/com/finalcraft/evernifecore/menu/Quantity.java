package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How many units a click trades: a fixed number, a stack, all that fits (or all held), or asked of the player. Immutable. */
public final class Quantity {

    private enum Kind { UNITS, STACK, ALL, ASK }

    public static final Quantity ONE = new Quantity(Kind.UNITS, 1);
    /** The goods' {@link Goods#getStackSize()}. */
    public static final Quantity STACK = new Quantity(Kind.STACK, 0);
    /**
     * BUY: the smallest of the room for the goods, what each price line's balance affords and the uses the limit has
     * left. SELL: the units held, within the limit.
     */
    public static final Quantity ALL_THAT_FITS = new Quantity(Kind.ALL, 0);
    /** The SELL reading of {@link #ALL_THAT_FITS}. */
    public static final Quantity ALL_HELD = ALL_THAT_FITS;
    /** A number the player types, 1 to {@link #ALL_THAT_FITS}; resolved by {@link Transactions#trade}. */
    public static final Quantity ASK = new Quantity(Kind.ASK, 0);

    private final Kind kind;
    private final int units;

    private Quantity(Kind kind, int units) {
        this.kind = kind;
        this.units = units;
    }

    /** @throws IllegalArgumentException when {@code units} is less than 1 */
    public static Quantity of(int units) {
        if (units < 1) {
            throw new IllegalArgumentException("A quantity of " + units + " trades nothing: write 1 or more.");
        }
        return units == 1 ? ONE : new Quantity(Kind.UNITS, units);
    }

    public boolean isAsk() {
        return kind == Kind.ASK;
    }

    /**
     * The units this quantity stands for, for {@code offer} in {@code ctx}; 0 when nothing can be traded.
     *
     * @throws IllegalStateException for {@link #ASK}, which only {@link Transactions#trade} resolves
     */
    public int resolve(ExecutionContext ctx, Offer offer) {
        switch (kind) {
            case UNITS:
                return units;
            case STACK:
                return offer.getGoods().getStackSize();
            case ALL:
                return allThatFits(ctx, offer);
            default:
                throw new IllegalStateException("Quantity.ASK is the player's answer and cannot be read from a context: trade it with"
                        + " Transactions.trade(ctx, offer, Quantity.ASK), or pass Quantity.of(n).");
        }
    }

    private static int allThatFits(ExecutionContext ctx, Offer offer) {
        Goods goods = offer.getGoods();
        long most = offer.getType() == Offer.Type.SELL ? goods.count(ctx) : goods.roomFor(ctx);
        if (offer.getType() == Offer.Type.BUY) {
            Quote unit = offer.getPrice().quote(ctx, 1);
            for (PriceLine line : unit.getLines()) {
                BigDecimal each = unit.getTotal(line.getId());
                if (each.signum() > 0) {
                    most = Math.min(most, line.available(ctx).divide(each, 0, RoundingMode.FLOOR).longValue());
                }
            }
        }
        Cooldown counter = offer.getCounter(ctx);
        if (counter != null) {
            most = Math.min(most, counter.getUsesLeft());
        }
        return (int) Math.max(0, Math.min(most, Integer.MAX_VALUE));
    }

    @Override
    public String toString() {
        return kind == Kind.UNITS ? String.valueOf(units) : kind.name();
    }
}
