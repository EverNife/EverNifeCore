package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

import java.math.BigDecimal;

/**
 * The outcome of an economy mutation.
 */
public final class EcoResponse {

    public enum Reason {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        /** A negative amount, which no verb has a defined meaning for; the provider is never called. */
        INVALID_AMOUNT,
        PROVIDER_ERROR
    }

    private final Reason reason;
    private final BigDecimal amount;
    private final BigDecimal balance;
    private final String detail;

    private EcoResponse(Reason reason, BigDecimal amount, BigDecimal balance, String detail) {
        this.reason = reason;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.detail = detail;
    }

    public static EcoResponse success(BigDecimal amount, BigDecimal newBalance) {
        return new EcoResponse(Reason.SUCCESS, amount, newBalance, null);
    }

    public static EcoResponse insufficientFunds(BigDecimal amount, BigDecimal currentBalance) {
        return new EcoResponse(Reason.INSUFFICIENT_FUNDS, amount, currentBalance, null);
    }

    public static EcoResponse invalidAmount(BigDecimal amount) {
        return new EcoResponse(Reason.INVALID_AMOUNT, amount, BigDecimal.ZERO, "amount must not be negative: " + amount);
    }

    public static EcoResponse providerError(BigDecimal amount, BigDecimal currentBalance, String detail) {
        return new EcoResponse(Reason.PROVIDER_ERROR, amount, currentBalance, detail);
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
     * {@link BigDecimal#ZERO} when the call never reached the provider ({@link Reason#INVALID_AMOUNT}).
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /** The provider's own error text on {@link Reason#PROVIDER_ERROR}, null otherwise. */
    public String getDetail() {
        return detail;
    }

    /**
     * Sends the localized "not enough money" message when this response failed for lack of funds,
     * using the balance this response already carries - no second read, and no window between
     * checking and charging.
     *
     * <p>Only {@link Reason#INSUFFICIENT_FUNDS} produces a message: a provider error or a negative
     * amount is not the player's problem to read about.</p>
     *
     * @return {@link #isSuccess()}, so a charge reads as one line:
     *         {@code if (!FCEcoUtil.ecoTake(player, 500).warnIfInsufficient(player)) return;}
     */
    public boolean warnIfInsufficient(FPlayer player) {
        if (reason == Reason.INSUFFICIENT_FUNDS) {
            FCMessageUtil.ecoNotEnough(player, balance, amount);
        }
        return isSuccess();
    }

    @Override
    public String toString() {
        return "EcoResponse{" + reason + ", amount=" + amount + ", balance=" + balance
                + (detail != null ? ", detail=" + detail : "") + "}";
    }
}
