package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.math.BigDecimal;

/**
 * One line of a {@link Price}: an amount of a currency, or a count of an item, per unit traded. A BUY takes each line
 * through the gate as a {@link Cost}; a SELL pays the player each line, which only a currency line can do.
 */
public interface PriceLine {

    /** The currency's id, or {@code item:<matcher>}; unique within a price. */
    String getId();

    /** The amount per unit for {@code ctx}, read now - a variable price reads its variable; safe without a player. */
    BigDecimal unitAmount(ExecutionContext ctx);

    /** How much of this line the context's owner has to spend: a balance, a count of items. */
    BigDecimal available(ExecutionContext ctx);

    /** {@code total} in the line's own words: "250 coins", "10 x minecraft:iron_ingot". */
    FancyText describe(ExecutionContext ctx, BigDecimal total);

    /** {@code total} of this line as a cost the gate takes, and refunds when a later one does not come out. */
    Cost asCost(BigDecimal total);

    /** Whether the line can be paid TO the player - only a currency can. */
    boolean canPay();

    /**
     * Pays {@code total} to the context's owner.
     *
     * @throws UnsupportedOperationException on a line that {@link #canPay() cannot pay}
     */
    EcoResponse pay(ExecutionContext ctx, BigDecimal total);
}
