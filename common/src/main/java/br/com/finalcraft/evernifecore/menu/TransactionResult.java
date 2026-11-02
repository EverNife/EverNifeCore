package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/** What {@link Transactions} answered: success, or the one failure that stopped the trade, with its text. Immutable. */
public final class TransactionResult {

    @FCLocale(lang = LocaleType.EN_US, text = "Bought ${goods}")
    @FCLocale(lang = LocaleType.PT_BR, text = "Comprou ${goods}")
    private static LocaleMessage BOUGHT;

    @FCLocale(lang = LocaleType.EN_US, text = "Sold ${goods}")
    @FCLocale(lang = LocaleType.PT_BR, text = "Vendeu ${goods}")
    private static LocaleMessage SOLD;

    @FCLocale(lang = LocaleType.EN_US, text = " for ${price}")
    @FCLocale(lang = LocaleType.PT_BR, text = " por ${price}")
    private static LocaleMessage FOR_PRICE;

    @FCLocale(lang = LocaleType.EN_US, text = " - what did not fit waits in your deliveries")
    @FCLocale(lang = LocaleType.PT_BR, text = " - o que não coube espera nas suas entregas")
    private static LocaleMessage WAITS_IN_DELIVERIES;

    public enum Failure {
        NONE,
        /** The offer's gate refused (every failure in {@link #getGateResult()}), or the offer is read-only. */
        GATE_DENIED,
        /** The limit has fewer uses left than the quantity, for this owner in this window. */
        LIMIT_REACHED,
        /** A price line could not be taken - {@link #getMissingLine()} names it. */
        INSUFFICIENT_FUNDS,
        /** A SELL of more units than are held. */
        NO_ITEMS,
        /** A BUY with {@link NoSpacePolicy#REFUSE} and no room. */
        NO_SPACE,
        /** A SELL of goods nobody holds, or of an item the catalog does not buy. */
        NOT_SELLABLE,
        /** A {@link TransactionPreEvent} listener, the quantity prompt or the confirmation called it off. */
        CANCELLED,
        /** A BUY whose goods threw when handed over: what was charged went back, and the plugin's log has the exception. */
        NOT_DELIVERED
    }

    private final Failure failure;
    private final @Nullable Offer offer;
    private final int quantity;
    private final @Nullable Quote quote;
    private final @Nullable GateResult gateResult;
    private final @Nullable PriceLine missingLine;
    private final @Nullable CompletableFuture<Delivery> delivery;
    private final @Nullable FancyText reason;

    private TransactionResult(Failure failure, @Nullable Offer offer, int quantity, @Nullable Quote quote, @Nullable GateResult gateResult,
                              @Nullable PriceLine missingLine, @Nullable CompletableFuture<Delivery> delivery, @Nullable FancyText reason) {
        this.failure = failure;
        this.offer = offer;
        this.quantity = quantity;
        this.quote = quote;
        this.gateResult = gateResult;
        this.missingLine = missingLine;
        this.delivery = delivery;
        this.reason = reason;
    }

    static TransactionResult success(Offer offer, int quantity, Quote quote, @Nullable GateResult paid, @Nullable CompletableFuture<Delivery> delivery) {
        return new TransactionResult(Failure.NONE, offer, quantity, quote, paid, null, delivery, null);
    }

    static TransactionResult failed(Failure failure, @Nullable Offer offer, int quantity, @Nullable Quote quote, FancyText reason) {
        return new TransactionResult(failure, offer, quantity, quote, null, null, null, reason);
    }

    static TransactionResult denied(Failure failure, Offer offer, int quantity, @Nullable Quote quote, GateResult gate, @Nullable PriceLine missing) {
        return new TransactionResult(failure, offer, quantity, quote, gate, missing, null, gate.explain());
    }

    public boolean isSuccess() {
        return failure == Failure.NONE;
    }

    public Failure getFailure() {
        return failure;
    }

    /** The offer traded; null only for a {@link Failure#NOT_SELLABLE} item the catalog does not buy. */
    public @Nullable Offer getOffer() {
        return offer;
    }

    public int getQuantity() {
        return quantity;
    }

    /** The totals charged or paid (after the pre-event; {@code getQuote().getOriginal()} before it); null when it stopped before quoting. */
    public @Nullable Quote getQuote() {
        return quote;
    }

    /** The gate's answer - every failure on a denial; null when no gate was asked. */
    public @Nullable GateResult getGateResult() {
        return gateResult;
    }

    /** The price line that could not be taken, on {@link Failure#INSUFFICIENT_FUNDS}. */
    public @Nullable PriceLine getMissingLine() {
        return missingLine;
    }

    /** The pending delivery being written for the goods that did not fit; null when all of them were handed over. */
    public @Nullable CompletableFuture<Delivery> getDelivery() {
        return delivery;
    }

    /**
     * "Bought 3x Diamond for 750 coins", or why it failed, for the viewer - in {@code ctx}'s language; a failure's text
     * was written in the language of the context that traded.
     */
    public FancyText describe(ExecutionContext ctx) {
        if (!isSuccess()) {
            return reason != null ? reason : FancyText.of(failure.name());
        }
        String goods = offer.getGoods().describe(ctx, quantity).toLegacyString();
        String text = Transactions.text(offer.getType() == Offer.Type.BUY ? BOUGHT : SOLD, ctx, "goods", goods).getText();
        if (!quote.isFree()) {
            text += Transactions.text(FOR_PRICE, ctx, "price", quote.describe(ctx).toLegacyString()).getText();
        }
        if (delivery != null) {
            text += Transactions.text(WAITS_IN_DELIVERIES, ctx).getText();
        }
        return FancyText.of(text + ".");
    }

    @Override
    public String toString() {
        return "TransactionResult{" + failure + ", " + quantity + " of " + offer + (reason == null ? "" : ", " + reason.toPlainText()) + "}";
    }
}
