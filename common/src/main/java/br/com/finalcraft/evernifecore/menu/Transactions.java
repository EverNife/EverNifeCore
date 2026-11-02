package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
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
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
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
 * order: the offer's gate checked (every failure) -> the limit -> room or units held -> {@link TransactionPreEvent} (a
 * quantity it changed is checked again) -> the price and the fixed costs taken by one {@link Gate#pay} (refunded in
 * reverse when one does not come out) -> the goods handed over or taken -> the limit counted -> the owner's {@link AuditLog} -> {@link TransactionEvent}. A
 * refusal before the pre-event posts nothing. The texts for the player ({@link TransactionResult#describe}, the quantity
 * prompt) are the core's locale messages, in {@link ExecutionContext#getLocale()}.
 */
public final class Transactions {

    @FCLocale(lang = LocaleType.EN_US, text = "There is no room for ${goods}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Não há espaço para ${goods}.")
    private static LocaleMessage NO_ROOM;

    @FCLocale(lang = LocaleType.EN_US, text = "You do not have ${goods}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Você não tem ${goods}.")
    private static LocaleMessage NOT_HELD;

    @FCLocale(lang = LocaleType.EN_US, text = "${goods} cannot be sold.")
    @FCLocale(lang = LocaleType.PT_BR, text = "${goods} não pode ser vendido.")
    private static LocaleMessage CANNOT_BE_SOLD;

    @FCLocale(lang = LocaleType.EN_US, text = "This offer is a catalog line: it shows a price and trades nothing.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Esta oferta é uma linha de catálogo: mostra um preço e não troca nada.")
    private static LocaleMessage CATALOG_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = "The trade was called off.")
    @FCLocale(lang = LocaleType.PT_BR, text = "A troca foi cancelada.")
    private static LocaleMessage CALLED_OFF;

    @FCLocale(lang = LocaleType.EN_US, text = "There is nothing in your hand to sell.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Não há nada na sua mão para vender.")
    private static LocaleMessage NOTHING_IN_HAND;

    @FCLocale(lang = LocaleType.EN_US, text = "Nobody here buys ${item}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Ninguém aqui compra ${item}.")
    private static LocaleMessage NOBODY_BUYS;

    @FCLocale(lang = LocaleType.EN_US, text = "${goods} could not be handed over; what you paid went back.")
    @FCLocale(lang = LocaleType.PT_BR, text = "${goods} não pôde ser entregue; o que você pagou voltou.")
    private static LocaleMessage NOT_DELIVERED;

    @FCLocale(lang = LocaleType.EN_US, text = "${line} could not be paid (${reason}); the goods went back.")
    @FCLocale(lang = LocaleType.PT_BR, text = "${line} não pôde ser pago (${reason}); os itens voltaram.")
    private static LocaleMessage NOT_PAID;

    @FCLocale(lang = LocaleType.EN_US, text = "How many? 1 to ${most} (${goods}), or ${cancel}")
    @FCLocale(lang = LocaleType.PT_BR, text = "Quantos? De 1 a ${most} (${goods}), ou ${cancel}")
    private static LocaleMessage HOW_MANY;

    @FCLocale(lang = LocaleType.EN_US, text = "'${input}' is not a whole number: type one from 1 to ${most}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "'${input}' não é um número inteiro: digite um de 1 a ${most}.")
    private static LocaleMessage NOT_A_NUMBER;

    @FCLocale(lang = LocaleType.EN_US, text = "${units} is out of reach: type one from 1 to ${most}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "${units} está fora do alcance: digite um de 1 a ${most}.")
    private static LocaleMessage OUT_OF_REACH;

    @FCLocale(lang = LocaleType.EN_US, text = "No quantity was given.")
    @FCLocale(lang = LocaleType.PT_BR, text = "Nenhuma quantidade foi informada.")
    private static LocaleMessage NO_QUANTITY;

    //what a text reads when nothing loaded the core locales above (the boot does, in ConfigManager.initialize)
    private static final String NOT_LOADED = "[transaction text not loaded]";

    private Transactions() {
    }

    /**
     * {@code message} in the context's language (the core's own when it has none), each {@code ${key}} of the
     * {@code key, value} pairs replaced; a visible marker when the core's locales were never loaded.
     */
    static FancyText text(@Nullable LocaleMessage message, ExecutionContext ctx, Object... keysAndValues) {
        if (message == null) {
            return FancyText.of(NOT_LOADED);
        }
        FancyText localized = message.getFancyText(LocaleType.normalize(ctx.getLocale().toString()));
        String text = (localized != null ? localized : message.getDefaultFancyText()).getText();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            text = text.replace("${" + keysAndValues[i] + "}", String.valueOf(keysAndValues[i + 1]));
        }
        return FancyText.of(text);
    }

    private static String goods(ExecutionContext ctx, Offer offer, int quantity) {
        return offer.getGoods().describe(ctx, quantity).toLegacyString();
    }

    /**
     * What {@link #buy}/{@link #sell} would answer now - every check and the totals - with nothing taken and nothing posted.
     * A limit whose {@link LimitScope#findOwner owner} the context lacks (a catalog priced without a player) is not checked.
     */
    public static TransactionResult quote(ExecutionContext ctx, Offer offer, int quantity) {
        requirePositive(quantity, offer);
        Quote quote = offer.getPrice().quote(ctx, quantity);
        TransactionResult refused = precheck(ctx, offer, quantity, quote, true);
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
                        text(NO_ROOM, ctx, "goods", goods(ctx, offer, 1))));
            }
            //nothing fits for another reason: one unit makes the real refusal (funds, limit, items) speak
            return CompletableFuture.completedFuture(run(ctx, offer, Math.max(1, units)));
        }
        TransactionResult cannotAsk = cannotAsk(ctx, offer);
        if (cannotAsk != null) {
            return CompletableFuture.completedFuture(cannotAsk);
        }
        return askQuantity(ctx, offer).handle((units, unanswered) -> unanswered != null ? noQuantity(ctx, offer) : run(ctx, offer, units));
    }

    /** {@code CANCELLED} (audited) when {@code ctx} has no player or no screen to ask a quantity through; null when it can ask. */
    static @Nullable TransactionResult cannotAsk(ExecutionContext ctx, Offer offer) {
        FPlayer player = ctx.getPlayer();
        if (player != null && ctx.getAttribute(ScreenView.class.getName(), ScreenView.class) != null) {
            return null;
        }
        String why = "Quantity.ASK needs a player to ask: this context has " + (player == null ? "none" : "no screen to ask through")
                + " (caller " + ctx.getCaller() + "). Pass Quantity.of(n) from code, or trade from a click.";
        ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, why);
        return TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, 0, null, FancyText.of(why));
    }

    /**
     * Asks the player, through the context's screen, how many units of {@code offer} to trade - 1 to all that fits; fails
     * when the prompt goes unanswered. Only for a context {@link #cannotAsk} accepts.
     */
    static CompletableFuture<Integer> askQuantity(ExecutionContext ctx, Offer offer) {
        ScreenView view = ctx.getAttribute(ScreenView.class.getName(), ScreenView.class);
        int most = Math.max(1, Quantity.ALL_THAT_FITS.resolve(ctx, offer));
        String question = text(HOW_MANY, ctx, "most", most, "goods", goods(ctx, offer, 1), "cancel", "cancel").getText();
        return view.ask(PromptSpec.of(question).parse(input -> {
            int units;
            try {
                units = Integer.parseInt(input.trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException(text(NOT_A_NUMBER, ctx, "input", input, "most", most).getText());
            }
            if (units < 1 || units > most) {
                throw new IllegalArgumentException(text(OUT_OF_REACH, ctx, "units", units, "most", most).getText());
            }
            return units;
        }).cancelWord("cancel"));
    }

    /** What a quantity prompt that went unanswered answers. */
    static TransactionResult noQuantity(ExecutionContext ctx, Offer offer) {
        return TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, 0, null, text(NO_QUANTITY, ctx));
    }

    /**
     * Sells every unit of the main inventory {@code catalog} prices, up to each offer's limit, one {@link #sell} per offer
     * it matched (each posting its own {@link TransactionEvent}).
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
                results.add(sell(ctx, offer, withinLimit(ctx, offer, held)));
            }
        }
        return new SellAllResult(results);
    }

    /**
     * Sells the stack in the player's hand ({@link HandScope#HELD_STACK}) or every unit its offer matches, up to the
     * offer's limit. A hand holding less than one unit of the offer answers {@code NO_ITEMS} and takes nothing.
     */
    public static TransactionResult sellHand(ExecutionContext ctx, OfferCatalog catalog, HandScope scope) {
        FPlayer player = ctx.getPlayer();
        ItemSpec held = player == null ? null : DeliveryDestinations.requireInventory().getHeld(player);
        if (held == null) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, null, 0, null, text(NOTHING_IN_HAND, ctx));
        }
        Offer offer = catalog.sellOfferFor(ctx, held);
        if (offer == null) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, null, 0, null,
                    text(NOBODY_BUYS, ctx, "item", held.getDisplayName().isEmpty() ? held.getMaterialId() : held.getDisplayName()));
        }
        int units = offer.getGoods().count(ctx);
        if (scope == HandScope.HELD_STACK && offer.getGoods() instanceof ItemGoods) {
            //the units of the held stack; taking by the matcher takes items that are worth the same, wherever they lie
            units = Math.min(units, held.getAmount() / ((ItemGoods) offer.getGoods()).getItem().getAmount());
            if (units < 1) {
                return TransactionResult.failed(TransactionResult.Failure.NO_ITEMS, offer, 1, null, text(NOT_HELD, ctx, "goods", goods(ctx, offer, 1)));
            }
        }
        return sell(ctx, offer, withinLimit(ctx, offer, Math.max(1, units)));
    }

    /** {@code units} cut to what the offer's limit still allows; 1 when nothing is left, so the refusal names the limit. */
    private static int withinLimit(ExecutionContext ctx, Offer offer, int units) {
        Cooldown counter = offer.getCounter(ctx);
        return counter == null ? units : Math.max(1, Math.min(units, counter.getUsesLeft()));
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static TransactionResult run(ExecutionContext ctx, Offer offer, int quantity) {
        requirePositive(quantity, offer);
        Quote asked = offer.getPrice().quote(ctx, quantity);
        TransactionResult refused = precheck(ctx, offer, quantity, asked, false);
        if (refused != null) {
            return refused;
        }
        TransactionPreEvent pre = ECEventBus.global().postIfListened(TransactionPreEvent.class,
                () -> new TransactionPreEvent(ctx, offer, quantity, asked.editable()));
        Quote quote = pre != null ? pre.getQuote() : asked.editable();
        int units = pre != null ? pre.getQuantity() : quantity;
        if (pre != null && pre.isCancelled()) {
            return TransactionResult.failed(TransactionResult.Failure.CANCELLED, offer, units, quote, text(CALLED_OFF, ctx));
        }
        if (units != quantity) {
            //the limit, the room and the items were measured for the asked quantity, not the listener's
            TransactionResult refusedNow = precheck(ctx, offer, units, quote, false);
            if (refusedNow != null) {
                return refusedNow;
            }
        }
        TransactionResult result = offer.getType() == Offer.Type.BUY ? takeAndGive(ctx, offer, units, quote) : takeAndPay(ctx, offer, units, quote);
        if (!result.isSuccess()) {
            return result;
        }
        Cooldown counter = offer.getCounter(ctx);
        if (counter != null && !counter.tryUse(units)) {
            uncounted(ctx, offer, units);
        }
        audit(ctx, result);
        ECEventBus.global().postIfListened(TransactionEvent.class, () -> new TransactionEvent(ctx, result));
        return result;
    }

    /** The checks that refuse before the pre-event: read-only, the gate, the limit, room or units held. */
    private static @Nullable TransactionResult precheck(ExecutionContext ctx, Offer offer, int quantity, Quote quote, boolean quoting) {
        if (offer.isReadOnly()) {
            return TransactionResult.failed(TransactionResult.Failure.GATE_DENIED, offer, quantity, quote, text(CATALOG_LINE, ctx));
        }
        if (offer.getType() == Offer.Type.SELL && !offer.getGoods().canBeSold()) {
            return TransactionResult.failed(TransactionResult.Failure.NOT_SELLABLE, offer, quantity, quote,
                    text(CANNOT_BE_SOLD, ctx, "goods", goods(ctx, offer, quantity)));
        }
        GateResult gate = offer.getGate().check(ctx);
        if (!gate.isPassed()) {
            return TransactionResult.denied(TransactionResult.Failure.GATE_DENIED, offer, quantity, quote, gate, null);
        }
        //a quote without the limit's owner prices the offer; a trade asks ownerOf, which says how to build the context
        Cooldown counter = quoting && offer.getLimitScope().findOwner(ctx) == null ? null : offer.getCounter(ctx);
        if (counter != null && (counter.getUsesLeft() < quantity || !counter.canUse())) {
            return TransactionResult.failed(TransactionResult.Failure.LIMIT_REACHED, offer, quantity, quote, counter.explain(ctx.getLocale()));
        }
        if (offer.getType() == Offer.Type.BUY) {
            if (offer.getNoSpacePolicy() == NoSpacePolicy.REFUSE && offer.getGoods().roomFor(ctx) < quantity) {
                return TransactionResult.failed(TransactionResult.Failure.NO_SPACE, offer, quantity, quote,
                        text(NO_ROOM, ctx, "goods", goods(ctx, offer, quantity)));
            }
        } else if (offer.getGoods().count(ctx) < quantity) {
            return TransactionResult.failed(TransactionResult.Failure.NO_ITEMS, offer, quantity, quote,
                    text(NOT_HELD, ctx, "goods", goods(ctx, offer, quantity)));
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
            ctx.getCaller().getPlugin().getLog().severe("[" + ctx.getCaller() + "] The trade of " + offer + " could not hand the goods over,"
                    + " and what was charged went back. Fix the goods' give: {}", failure.toString(), failure);
            return TransactionResult.failed(TransactionResult.Failure.NOT_DELIVERED, offer, units, quote,
                    text(NOT_DELIVERED, ctx, "goods", goods(ctx, offer, units)));
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
                        text(NOT_PAID, ctx, "line", line.describe(ctx, total).toLegacyString(), "reason", response.getReason()));
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

    /** A trade that went through and its limit could not count (used up in between): audited and logged, never silent. */
    private static void uncounted(ExecutionContext ctx, Offer offer, int units) {
        FPlayer player = ctx.getPlayer();
        String text = "The trade of " + units + " units of " + offer + " for " + (player == null ? ctx.getOwner().toString() : player.getName())
                + " went through and its limit could not count it, so those units are not on the limit - check the counter by hand.";
        ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, text);
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
