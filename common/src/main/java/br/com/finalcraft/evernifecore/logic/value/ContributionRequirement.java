package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.math.BigDecimal;

/**
 * What a {@link Contribution} collects and how much: a currency, an item. A consuming requirement takes from the
 * depositing player; a checking one only counts what they hold. Called on the deposit's thread, never while the
 * contribution is locked, so an implementation may wait for the player's thread.
 */
public interface ContributionRequirement<T> {

    /** What is collected: the {@code Currency}, the item matcher. */
    T getSubject();

    /** The total that completes the contribution. */
    BigDecimal getTarget();

    boolean isConsuming();

    /** How much of the subject the context's player holds now. */
    BigDecimal countHeld(ExecutionContext ctx);

    /** Takes up to {@code upTo} from the context's player (consuming only); what actually left, zero when nothing did. */
    BigDecimal take(ExecutionContext ctx, BigDecimal upTo);

    /**
     * Gives {@code amount} back to the context's player (consuming only): what {@link #take} took past the target
     * because another deposit filled it first.
     *
     * @throws RuntimeException when it could not be given back; the contribution audits it and logs it severe
     */
    void giveBack(ExecutionContext ctx, BigDecimal amount);
}
