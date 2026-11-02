package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A {@link ContributionRequirement} of a {@link Currency}, read from and taken out of the depositing player's own
 * wallet. Consuming unless {@link #check()}. Immutable; called on the deposit's thread.
 */
public final class CurrencyRequirement implements ContributionRequirement<Currency> {

    private final Currency currency;
    private final BigDecimal target;
    private final boolean consuming;

    private CurrencyRequirement(Currency currency, BigDecimal target, boolean consuming) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.target = Objects.requireNonNull(target, "target");
        this.consuming = consuming;
    }

    /** Collects {@code target} of {@code currency}, taken from each depositor. */
    public static CurrencyRequirement of(Currency currency, BigDecimal target) {
        return new CurrencyRequirement(currency, target, true);
    }

    /** This requirement counting what the depositor holds, taking nothing. */
    public CurrencyRequirement check() {
        return new CurrencyRequirement(currency, target, false);
    }

    @Override
    public Currency getSubject() {
        return currency;
    }

    @Override
    public BigDecimal getTarget() {
        return target;
    }

    @Override
    public boolean isConsuming() {
        return consuming;
    }

    @Override
    public BigDecimal countHeld(ExecutionContext ctx) {
        return currency.getBalance(wallet(ctx));
    }

    @Override
    public BigDecimal take(ExecutionContext ctx, BigDecimal upTo) {
        CurrencyOwner wallet = wallet(ctx);
        BigDecimal amount = currency.getBalance(wallet).min(upTo);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        EcoResponse taken = currency.take(wallet, amount, ctx.getCaller());
        return taken.isSuccess() ? amount : BigDecimal.ZERO;
    }

    @Override
    public void giveBack(ExecutionContext ctx, BigDecimal amount) {
        EcoResponse given = currency.give(wallet(ctx), amount, ctx.getCaller());
        if (!given.isSuccess()) {
            throw new IllegalStateException("Giving " + amount.toPlainString() + " " + currency.getId() + " back failed: " + given);
        }
    }

    private static CurrencyOwner wallet(ExecutionContext ctx) {
        FPlayer player = Contribution.depositor(ctx);
        return CurrencyOwner.player(player.getUniqueId());
    }

    @Override
    public String toString() {
        return (consuming ? "" : "check ") + target.toPlainString() + " " + currency.getId();
    }
}
