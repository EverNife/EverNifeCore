package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * The outcome of a {@link Currency} verb - or of an {@link IEconomyProvider} bridge call, before the
 * {@code money} currency stamps itself on it. Immutable.
 */
public final class EcoResponse {

    public enum Reason {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        /** A negative amount, which no verb has a defined meaning for; the implementation is never called. */
        INVALID_AMOUNT,
        PROVIDER_ERROR,
        /** The currency keeps no wallet for that kind of owner ({@link Currency#supports}); nothing was called. */
        UNSUPPORTED_OWNER,
        /** The currency cannot move anything right now - {@code money} with no economy plugin behind it. */
        UNAVAILABLE
    }

    private final Reason reason;
    private final BigDecimal amount;
    private final BigDecimal balance;
    private final String detail;
    private final Currency currency;

    private EcoResponse(Reason reason, BigDecimal amount, BigDecimal balance, String detail, Currency currency) {
        this.reason = reason;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.detail = detail;
        this.currency = currency;
    }

    public static EcoResponse success(BigDecimal amount, BigDecimal newBalance) {
        return new EcoResponse(Reason.SUCCESS, amount, newBalance, null, null);
    }

    public static EcoResponse insufficientFunds(BigDecimal amount, BigDecimal currentBalance) {
        return new EcoResponse(Reason.INSUFFICIENT_FUNDS, amount, currentBalance, null, null);
    }

    public static EcoResponse invalidAmount(BigDecimal amount) {
        return new EcoResponse(Reason.INVALID_AMOUNT, amount, BigDecimal.ZERO, "amount must not be negative: " + amount, null);
    }

    public static EcoResponse providerError(BigDecimal amount, BigDecimal currentBalance, String detail) {
        return new EcoResponse(Reason.PROVIDER_ERROR, amount, currentBalance, detail, null);
    }

    /** @param detail what the caller should do instead - name the currency that does have that wallet */
    public static EcoResponse unsupportedOwner(BigDecimal amount, String detail) {
        return new EcoResponse(Reason.UNSUPPORTED_OWNER, amount, BigDecimal.ZERO, detail, null);
    }

    /** @param detail why nothing can move and how to fix it */
    public static EcoResponse unavailable(BigDecimal amount, String detail) {
        return new EcoResponse(Reason.UNAVAILABLE, amount, BigDecimal.ZERO, detail, null);
    }

    /** This response answered by {@code currency}; a response that already names one keeps it. */
    EcoResponse withCurrency(Currency currency) {
        return this.currency != null ? this : new EcoResponse(reason, amount, balance, detail, currency);
    }

    public boolean isSuccess() {
        return reason == Reason.SUCCESS;
    }

    public Reason getReason() {
        return reason;
    }

    /** The amount the call asked for. Never null. */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * The balance after the call, or the current one when the call failed. Never null; it is
     * {@link BigDecimal#ZERO} when the call never reached the implementation ({@link Reason#INVALID_AMOUNT},
     * {@link Reason#UNSUPPORTED_OWNER}, {@link Reason#UNAVAILABLE}).
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /** The provider's error text, or the teaching text of a refusal; null on success and lack of funds. */
    public @Nullable String getDetail() {
        return detail;
    }

    /** The currency that answered; null only for a bare {@link IEconomyProvider} call. */
    public @Nullable Currency getCurrency() {
        return currency;
    }

    /**
     * Sends the localized "not enough money" message when this response failed for lack of funds,
     * using the balance this response already carries - no second read, and no window between
     * checking and charging. The amounts are rendered by the currency that refused.
     *
     * <p>Only {@link Reason#INSUFFICIENT_FUNDS} produces a message: a provider error or a negative
     * amount is not the player's problem to read about.</p>
     *
     * @return {@link #isSuccess()}, so a charge reads as one line:
     *         {@code if (!FCEcoUtil.ecoTake(player, 500).warnIfInsufficient(player)) return;}
     */
    public boolean warnIfInsufficient(FPlayer player) {
        if (reason == Reason.INSUFFICIENT_FUNDS) {
            FCMessageUtil.ecoNotEnough(player, currency != null ? currency : Currencies.money(), balance, amount);
        }
        return isSuccess();
    }

    @Override
    public String toString() {
        return "EcoResponse{" + reason + ", amount=" + amount + ", balance=" + balance
                + (currency != null ? ", currency=" + currency.getId() : "")
                + (detail != null ? ", detail=" + detail : "") + "}";
    }
}
