package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.prompt.Prompt;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Runs an {@link Offer}. Every verb runs on the caller's thread (a click, a command), without blocking I/O, in one fixed
 * order: the offer's gate checked (every failure) -> the limit -> room or units held -> {@link TransactionPreEvent} ->
 * the price and the fixed costs taken by one {@link Gate#pay} (refunded in reverse when one does not come out) -> the
 * goods handed over or taken -> the limit counted -> the owner's {@link AuditLog} -> {@link TransactionEvent}. A
 * refusal before the pre-event posts nothing. The texts for the player are the caller's: {@link TransactionResult#describe}.
 */
public final class Transactions {

    private Transactions() {
    }

    /** What {@link #buy}/{@link #sell} would answer now - every check and the totals - with nothing taken and nothing posted. */
    public static TransactionResult quote(ExecutionContext ctx, Offer offer, int quantity) {
        requirePositive(quantity, offer);
        Quote quote = offer.getPrice().quote(ctx, quantity);
        TransactionResult refused = precheck(ctx, offer, quantity, quote);
        if (refused != null) {
            return refused;
        }
        if (offer.getType() == Offer.Type.BUY) {
            Map<Cost, PriceLine> lines = new IdentityHashMap<>();
            GateResult checked = payingGate(offer, quote, lines).check(ctx);
            if (!checked.isPassed()) {
                return deny(offer, quantity, quote, checked, lines);
            }
        }
        return TransactionResult.success(offer, quantity, quote, null, null);
    }

    /** The player pays the price and the gate, and gets the goods. */
    public static TransactionResult buy(ExecutionContext ctx, Offer offer, int quantity) {
        requireType(offer, Offer.Type.BUY, "buy");
        return run(ctx, offer, quantity);
    }

    /** The player pays the gate's fixed costs and hands over the goods, and is paid the price. */
    public static TransactionResult sell(ExecutionContext ctx, Offer offer, int quantity) {
        requireType(offer, Offer.Type.SELL, "sell");
        return run(ctx, offer, quantity);
    }

    /**
     * Resolves {@code quantity} and buys or sells. {@link Quantity#ASK} asks the player through the screen's prompt
     * channel and completes with {@code CANCELLED} when the prompt goes unanswered, or when the context has no player or
     * no screen to ask through; every other quantity completes at once.
     */
    public static CompletableFuture<TransactionResult> trade(ExecutionContext ctx, Offer offer, Quantity quantity) {
        if (!quantity.isAsk()) {
            int units = quantity.resolve(ctx, offer);
            if (units < 1 && offer.getType() == Offer.Type.BUY && offer.getGoods().roomFor(ctx) < 1) {
                return CompletableFuture.completedFuture(TransactionResult.failed(TransactionResult.Failure.NO_SPACE, offer, 0, null,
                        FancyText.of("There is no room for " + offer.getGoods().describe(ctx, 1).toLegacyString() + ".")));
            }
            //nothing fits for another reason: one unit makes the real refusal (funds, limit, items) speak
            return CompletableFuture.completedFuture(run(ctx, offer, Math.max(1, units)));
        }
        FPlayer player = ctx.getPlayer();
        ScreenView view = ctx.getAttribute(ScreenView.class.getName(), ScreenView.class);
        if (player == null || view == null) {
            String why = "Quantity.ASK needs a player to ask: this context has " + (player == null ? "none" : "no screen to ask through")
                    + " (caller " + ctx.getCaller() + "). Pass Quantity.of(n) from code, or trade from a click.";
            ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, why);
            return CompletableFuture.completedFuture(TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, 0, null, FancyText.of(why)));
        }
        int most = Math.max(1, Quantity.ALL_THAT_FITS.resolve(ctx, offer));
        String question = "How many? 1 to " + most + " (" + offer.getGoods().describe(ctx, 1).toLegacyString() + ")";
        CompletableFuture<TransactionResult> traded = new CompletableFuture<>();
        view.ask(Prompt.of(question).parse(input -> {
            int units;
            try {
                units = Integer.parseInt(input.trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException("'" + input + "' is not a whole number: type one from 1 to " + most + ".");
            }
            if (units < 1 || units > most) {
                throw new IllegalArgumentException(units + " is out of reach: type one from 1 to " + most + ".");
            }
            return units;
        })).whenComplete((units, unanswered) -> {
            if (unanswered != null) {
                traded.complete(TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, 0, null, FancyText.of("No quantity was given.")));
            } else {
                traded.complete(run(ctx, offer, units));
            }
        });
        return traded;
    }

    /**
     * Sells every unit of the main inventory {@code catalog} prices, one {@link #sell} per offer it matched (each posting
     * its own {@link TransactionEvent}).
     */
    public static SellAllResult sellAll(ExecutionContext ctx, OfferCatalog catalog) {
        FPlayer player = ctx.getPlayer();
        List<TransactionResult> results = new ArrayList<>();
        if (player == null) {
            return new SellAllResult(results);
        }
        Map<Offer, Boolean> matched = new LinkedHashMap<>();
        for (ItemSpec stack : DeliveryDestinations.requireInventory().getContents(player)) {
            Offer offer = catalog.sellOfferFor(ctx, stack);
            if (offer != null && !offer.isReadOnly()) {
                matched.put(offer, Boolean.TRUE);
            }
        }
        for (Offer offer : matched.keySet()) {
            int held = offer.getGoods().count(ctx);
            if (held > 0) {
                results.add(sell(ctx, offer, held));
            }
        }
        return new SellAllResult(results);
    }

    /** Sells the stack in the player's hand ({@link HandScope#HELD_STACK}) or every unit its offer matches. */
    public static TransactionResult sellHand(ExecutionContext ctx, OfferCatalog catalog, HandScope scope) {
        FPlayer player = ctx.getPlayer();
        ItemSpec held = player == null ? null : DeliveryDestinations.requireInventory().getHeld(player);
        if (held == null) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, null, 0, null, FancyText.of("There is nothing in your hand to sell."));
        }
        Offer offer = catalog.sellOfferFor(ctx, held);
        if (offer == null) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, null, 0, null,
                    FancyText.of("Nobody here buys " + (held.getDisplayName().isEmpty() ? held.getMaterialId() : held.getDisplayName()) + "."));
        }
        int units = offer.getGoods().count(ctx);
        if (scope == HandScope.HELD_STACK && offer.getGoods() instanceof ItemGoods) {
            //the units of the held stack; taking by the matcher takes items that are worth the same, wherever they lie
            units = Math.min(units, held.getAmount() / ((ItemGoods) offer.getGoods()).getItem().getAmount());
        }
        return sell(ctx, offer, Math.max(1, units));
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static TransactionResult run(ExecutionContext ctx, Offer offer, int quantity) {
        requirePositive(quantity, offer);
        Quote asked = offer.getPrice().quote(ctx, quantity);
        TransactionResult refused = precheck(ctx, offer, quantity, asked);
        if (refused != null) {
            return refused;
        }
        TransactionPreEvent pre = ECEventBus.global().postIfListened(TransactionPreEvent.class,
                () -> new TransactionPreEvent(ctx, offer, quantity, asked.editable()));
        Quote quote = pre != null ? pre.getQuote() : asked.editable();
        int units = pre != null ? pre.getQuantity() : quantity;
        if (pre != null && pre.isCancelled()) {
            return TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, units, quote,
                    FancyText.of("The trade was called off."));
        }
        TransactionResult result = offer.getType() == Offer.Type.BUY ? takeAndGive(ctx, offer, units, quote) : takeAndPay(ctx, offer, units, quote);
        if (!result.isSuccess()) {
            return result;
        }
        Cooldown counter = offer.getCounter(ctx);
        if (counter != null) {
            counter.tryUse(units);
        }
        audit(ctx, result);
        ECEventBus.global().postIfListened(TransactionEvent.class, () -> new TransactionEvent(ctx, result));
        return result;
    }

    /** The checks that refuse before the pre-event: read-only, the gate, the limit, room or units held. */
    private static @Nullable TransactionResult precheck(ExecutionContext ctx, Offer offer, int quantity, Quote quote) {
        if (offer.isReadOnly()) {
            return TransactionResult.failed(TransactionResult.Failure.GATE_DENIED, offer, quantity, quote,
                    FancyText.of("This offer is a catalog line: it shows a price and trades nothing."));
        }
        if (offer.getType() == Offer.Type.SELL && !offer.getGoods().canBeSold()) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, offer, quantity, quote,
                    FancyText.of(offer.getGoods().describe(ctx, quantity).toLegacyString() + " cannot be sold."));
        }
        GateResult gate = offer.getGate().check(ctx);
        if (!gate.isPassed()) {
            return TransactionResult.denied(TransactionResult.Failure.GATE_DENIED, offer, quantity, quote, gate, null);
        }
        Cooldown counter = offer.getCounter(ctx);
        if (counter != null && (counter.getUsesLeft() < quantity || !counter.canUse())) {
            return TransactionResult.failed(TransactionResult.Failure.LIMIT_REACHED, offer, quantity, quote, counter.explain(ctx.getLocale()));
        }
        if (offer.getType() == Offer.Type.BUY) {
            if (offer.getNoSpacePolicy() == NoSpacePolicy.REFUSE && offer.getGoods().roomFor(ctx) < quantity) {
                return TransactionResult.failed(TransactionResult.Failure.NO_SPACE, offer, quantity, quote,
                        FancyText.of("There is no room for " + offer.getGoods().describe(ctx, quantity).toLegacyString() + "."));
            }
        } else if (offer.getGoods().count(ctx) < quantity) {
            return TransactionResult.failed(TransactionResult.Failure.NO_ITEMS, offer, quantity, quote,
                    FancyText.of("You do not have " + offer.getGoods().describe(ctx, quantity).toLegacyString() + "."));
        }
        return null;
    }

    private static TransactionResult takeAndGive(ExecutionContext ctx, Offer offer, int units, Quote quote) {
        Map<Cost, PriceLine> lines = new IdentityHashMap<>();
        Gate paying = payingGate(offer, quote, lines);
        GateResult paid = paying.pay(ctx);
        if (!paid.isPassed()) {
            return deny(offer, units, quote, paid, lines);
        }
        CompletableFuture<Delivery> delivery;
        try {
            delivery = offer.getGoods().give(ctx, units, offer.getDestination());
        } catch (RuntimeException failure) {
            refundInReverse(ctx, paying.getCosts(), "the goods could not be handed over (" + failure.getMessage() + ")");
            throw new IllegalStateException("The trade of " + offer + " for " + ctx.getCaller() + " could not hand the goods over, and what"
                    + " was charged went back: " + failure.getMessage(), failure);
        }
        return TransactionResult.success(offer, units, quote, paid, delivery);
    }

    private static TransactionResult takeAndPay(ExecutionContext ctx, Offer offer, int units, Quote quote) {
        GateResult paid = offer.getGate().pay(ctx);
        if (!paid.isPassed()) {
            return TransactionResult.denied(TransactionResult.Failure.GATE_DENIED, offer, units, quote, paid, null);
        }
        try {
            offer.getGoods().take(ctx, units);
        } catch (RuntimeException fewer) {
            refundInReverse(ctx, offer.getGate().getCosts(), "the goods could not be taken");
            return TransactionResult.failed(TransactionResult.Failure.NO_ITEMS, offer, units, quote, FancyText.of(fewer.getMessage()));
        }
        List<PriceLine> paidLines = new ArrayList<>();
        for (PriceLine line : quote.getLines()) {
            BigDecimal total = quote.getTotal(line.getId()).multiply(ctx.getMultiplier(line.getId()));
            quote.setTotal(line.getId(), total);
            EcoResponse response = line.pay(ctx, total);
            if (!response.isSuccess()) {
                undoSale(ctx, offer, units, quote, paidLines);
                refundInReverse(ctx, offer.getGate().getCosts(), "a price line could not be paid");
                return TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, units, quote,
                        FancyText.of(line.describe(ctx, total).toLegacyString() + " could not be paid (" + response.getReason()
                                + "); the goods went back."));
            }
            paidLines.add(line);
        }
        return TransactionResult.success(offer, units, quote, paid, null);
    }

    /** A sale whose payment failed: the goods go back and the lines already paid are taken back. */
    private static void undoSale(ExecutionContext ctx, Offer offer, int units, Quote quote, List<PriceLine> paidLines) {
        offer.getGoods().give(ctx, units, null);
        for (int i = paidLines.size() - 1; i >= 0; i--) {
            PriceLine line = paidLines.get(i);
            Cost back = line.asCost(quote.getTotal(line.getId()));
            if (!back.take(ctx)) {
                lost(ctx, back, "was paid for a sale that failed and could not be taken back");
            }
        }
    }

    /** The offer's gate with the price lines appended as costs, each remembered by the line it came from. */
    private static Gate payingGate(Offer offer, Quote quote, Map<Cost, PriceLine> lines) {
        List<Cost> costs = new ArrayList<>(offer.getGate().getCosts());
        for (PriceLine line : quote.getLines()) {
            Cost cost = line.asCost(quote.getTotal(line.getId()));
            lines.put(cost, line);
            costs.add(cost);
        }
        return Gate.of(offer.getGate().getConditions(), costs);
    }

    private static TransactionResult deny(Offer offer, int units, Quote quote, GateResult result, Map<Cost, PriceLine> lines) {
        for (ConditionResult failure : result.getFailures()) {
            PriceLine missing = lines.get(failure.getCondition());
            if (missing != null) {
                return TransactionResult.denied(TransactionResult.Failure.INSUFFICIENT_FUNDS, offer, units, quote, result, missing);
            }
        }
        return TransactionResult.denied(TransactionResult.Failure.GATE_DENIED, offer, units, quote, result, null);
    }

    private static void refundInReverse(ExecutionContext ctx, List<Cost> costs, String why) {
        for (int i = costs.size() - 1; i >= 0; i--) {
            Cost cost = costs.get(i);
            try {
                cost.refund(ctx);
            } catch (RuntimeException failure) {
                lost(ctx, cost, "was taken and its refund failed after " + why + " (" + failure + ")");
            }
        }
    }

    /** Money or items that went missing are never silent: an audit entry and a severe line naming who and how much. */
    private static void lost(ExecutionContext ctx, Cost cost, String what) {
        FPlayer player = ctx.getPlayer();
        String text = cost.describe(ctx).toPlainText() + " of " + (player == null ? ctx.getOwner().toString()
                : player.getName() + " (uuid " + player.getUniqueId() + ")") + " " + what + " - give it back by hand.";
        AuditLog.ofIfPresent(ctx.getCaller().getPlugin()).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.REFUND_FAILED, ctx.getCaller())
                .owner(ctx.getOwner()).player(player == null ? null : player.getUniqueId()).text(text).build()));
        ctx.getCaller().getPlugin().getLog().severe("[" + ctx.getCaller() + "] " + text);
    }

    private static void audit(ExecutionContext ctx, TransactionResult result) {
        FPlayer player = ctx.getPlayer();
        Quote quote = result.getQuote();
        BigDecimal amount = quote.getLines().isEmpty() ? null : quote.getTotal(quote.getLines().get(0).getId());
        AuditLog.ofIfPresent(ctx.getCaller().getPlugin()).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.TRANSACTION, ctx.getCaller())
                .owner(ctx.getOwner())
                .player(player == null ? null : player.getUniqueId())
                .amount(amount)
                .subject(quote.getLines().isEmpty() ? null : quote.getLines().get(0).getId())
                .text(result.describe(ctx).toPlainText())
                .build()));
    }

    private static void requirePositive(int quantity, Offer offer) {
        if (quantity < 1) {
            throw new IllegalArgumentException("A trade of " + quantity + " units of " + offer + " trades nothing: pass 1 or more, or a"
                    + " Quantity to Transactions.trade.");
        }
    }

    private static void requireType(Offer offer, Offer.Type type, String verb) {
        if (offer.getType() != type) {
            throw new IllegalArgumentException("Transactions." + verb + " was given a " + offer.getType() + " offer (" + offer + "): call"
                    + " Transactions." + (type == Offer.Type.BUY ? "sell" : "buy") + ", or trade(ctx, offer, quantity), which picks by the offer.");
        }
    }
}
