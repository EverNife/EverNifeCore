package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectModifiers;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import br.com.finalcraft.evernifecore.logic.reward.Rewards;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The stock cost and effect {@code money}: a cost takes an amount of a currency from the context owner's wallet (and
 * gives it back on a refund); an effect gives it. Neither resolves a booster: the effect multiplies only by the
 * multiplier a boosted reward payment froze on its receipt ({@link Rewards#MULTIPLIER_ATTRIBUTE}), so anywhere else it
 * pays what it says. Both resolve the currency when they run, so a currency registered later is found.
 */
final class StockEconomy {

    private static final String DEFAULT_CURRENCY = "money";
    /** The currency ids already warned about as missing: the log tells each absence once, not once per payment. */
    private static final Set<String> TOLD_MISSING = ConcurrentHashMap.newKeySet();

    private StockEconomy() {
    }

    static void register(ECPluginData core) {
        Costs.register(core, TypeDescriptor.<Cost>of("evernifecore:money")
                .describe("Takes an amount of a currency from the owner's wallet; gives it back when a later cost does not come out.")
                .param(ParamSpec.required("amount", ParamSpec.Kind.STRING, "The amount: a number, or a {variable}."))
                .param(ParamSpec.optional("currency", ParamSpec.Kind.ID, DEFAULT_CURRENCY, "The currency, e.g. money or finaleconomy:gold."))
                .factory(params -> new MoneyCost(params.getString("amount"), params.getString("currency")))
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:money")
                .describe("Gives an amount of a currency to the owner's wallet.")
                .param(ParamSpec.required("amount", ParamSpec.Kind.STRING, "The amount: a number, or a {variable}."))
                .param(ParamSpec.optional("currency", ParamSpec.Kind.ID, DEFAULT_CURRENCY, "The currency, e.g. money or finaleconomy:gold."))
                .factory(params -> new MoneyEffect(params.getString("amount"), params.getString("currency"), BigDecimal.ONE))
                .build());
    }

    /**
     * The currency {@code written} names once its {@code {variables}} are resolved for {@code ctx}, {@code money}
     * answering even before its registration; null when no such currency is registered right now.
     */
    static @Nullable Currency currency(ExecutionContext ctx, String written) {
        String id = ctx.resolve(written);
        if (DEFAULT_CURRENCY.equals(id) || ("evernifecore:" + DEFAULT_CURRENCY).equals(id)) {
            return Currencies.money();
        }
        Optional<Currency> found = Currencies.find(id);
        if (found.isPresent()) {
            TOLD_MISSING.remove(id); //back again: a later absence is worth a new warning
        }
        return found.orElse(null);
    }

    /** Why {@code id} cannot pay now: no currency of that id or alias is registered. */
    static String missing(String id) {
        return "No currency '" + id + "' is registered right now (registered: " + Currencies.getAll().stream().map(Currency::getId)
                .collect(Collectors.toList()) + "): the plugin that registers it, with Currencies.register(owner, currency), is not"
                + " enabled. Enable it, or name another currency; a short name works only when one currency has it.";
    }

    /**
     * The amount written, its {@code {variables}} resolved.
     *
     * @throws IllegalStateException naming the text when it does not read as a number
     */
    static BigDecimal amount(ExecutionContext ctx, String written) {
        String resolved = ctx.resolve(written).trim();
        try {
            return new BigDecimal(resolved);
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException("The amount '" + written + "' reads as '" + resolved + "', which is not a number: write a number"
                    + " like 250, or a {variable} that holds one.");
        }
    }

    /** Why the context owner has no wallet of {@code currency}, or null when it has one and the currency can move. */
    static @Nullable String noWallet(Currency currency, ExecutionContext ctx) {
        Optional<CurrencyOwner> owner = CurrencyOwner.of(ctx.getOwner());
        if (!owner.isPresent() || !currency.supports(owner.get().getKind())) {
            return currency.getId() + " has no wallet for " + ctx.getOwner().getKind() + ": " + ctx.getOwner()
                    + " cannot pay or be paid in it. Name a currency that keeps wallets for it, or run the logic for a player.";
        }
        if (!currency.isAvailable()) {
            return "No economy on this server: install Vault and an economy plugin (FinalEconomy works), or name another currency.";
        }
        return null;
    }

    private static final class MoneyCost implements Cost {
        private final String amount;
        private final String currencyId;

        MoneyCost(String amount, String currencyId) {
            this.amount = amount;
            this.currencyId = currencyId;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            Currency currency = currency(ctx, currencyId);
            if (currency == null) {
                return ConditionResult.fail(FancyText.of(missing(ctx.resolve(currencyId))));
            }
            String noWallet = noWallet(currency, ctx);
            if (noWallet != null) {
                return ConditionResult.fail(FancyText.of(noWallet));
            }
            CurrencyOwner owner = CurrencyOwner.of(ctx.getOwner()).get();
            BigDecimal needed = amount(ctx, amount);
            if (currency.has(owner, needed)) {
                return ConditionResult.pass();
            }
            return ConditionResult.fail(notEnough(ctx, currency, currency.getBalance(owner), needed));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            Currency currency = currency(ctx, currencyId);
            Optional<CurrencyOwner> owner = CurrencyOwner.of(ctx.getOwner());
            return currency != null && owner.isPresent() && currency.take(owner.get(), amount(ctx, amount), ctx.getCaller()).isSuccess();
        }

        @Override
        public void refund(ExecutionContext ctx) {
            Currency currency = currency(ctx, currencyId);
            if (currency == null) {
                throw new IllegalStateException("The refund of " + describe(ctx).getText() + " to " + ctx.getOwner() + " failed: "
                        + missing(ctx.resolve(currencyId)));
            }
            EcoResponse given = currency.give(CurrencyOwner.of(ctx.getOwner()).get(), amount(ctx, amount), ctx.getCaller());
            if (!given.isSuccess()) {
                throw new IllegalStateException("The refund of " + describe(ctx).getText() + " to " + ctx.getOwner() + " failed: " + given);
            }
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return format(ctx, currencyId, amount(ctx, amount));
        }

        @Override
        public String toString() {
            return "money(" + amount + " " + currencyId + ")";
        }
    }

    /** {@code amount} in the currency {@code written} names; the bare id when it is not registered right now. */
    private static FancyText format(ExecutionContext ctx, String written, BigDecimal amount) {
        Currency currency = currency(ctx, written);
        return currency != null ? currency.format(amount) : FancyText.of(amount.toPlainString() + " " + ctx.resolve(written));
    }

    private static FancyText notEnough(ExecutionContext ctx, Currency currency, BigDecimal balance, BigDecimal needed) {
        if (FCMessageUtil.ECO_NOT_ENOUGHT != null && FCMessageUtil.ECO_NOT_ENOUGHT.isDefined()) {
            //a message's placeholders resolve only when it is sent; a failure text is kept, so it is filled here
            return FancyText.of(FCMessageUtil.ECO_NOT_ENOUGHT.getFancyText(ctx.getPlayer()).getText()
                    .replace("${current_money}", currency.format(balance).getText())
                    .replace("${needed_money}", currency.format(needed).getText()));
        }
        return FancyText.of("Not enough " + currency.getDisplayName().getText() + ": " + currency.format(balance).getText() + " of "
                + currency.format(needed).getText() + ".");
    }

    private static final class MoneyEffect implements Effect {
        private final String amount;
        private final String currencyId;
        private final BigDecimal share;

        MoneyEffect(String amount, String currencyId, BigDecimal share) {
            this.amount = amount;
            this.currencyId = currencyId;
            this.share = share;
        }

        @Override
        public boolean acceptsDestination() {
            return true;
        }

        /**
         * A currency that is not registered, or a wallet that is missing or cannot move, is audited and logged, never
         * thrown: the other effects still apply, and a claim keeps the node pending. A
         * {@code to:} that names no registered destination keeps the amount as a pending delivery.
         */
        @Override
        public void apply(ExecutionContext ctx) {
            Currency currency = currency(ctx, currencyId);
            BigDecimal given = given(ctx);
            if (currency == null) {
                String id = ctx.resolve(currencyId);
                String what = "money " + given.toPlainString() + " " + id + " to " + ctx.getOwner();
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, what + " not given: " + missing(id), given, null);
                if (TOLD_MISSING.add(id)) {
                    EverNifeCore.getLog().warning("[" + ctx.getCaller() + "] " + what + " was not given: " + missing(id));
                }
                return;
            }
            String to = ctx.getAttribute(EffectModifiers.DESTINATION_ATTRIBUTE, String.class);
            if (to != null) {
                Optional<DeliveryDestination> destination = DeliveryDestinations.find(to);
                if (!destination.isPresent()) {
                    ObjectNode kept = (ObjectNode) PendingDestination.moneyNode(currency, given);
                    kept.put("to", to);
                    Deliveries.get().keepOrAudit(ctx, null, kept);
                    return;
                }
                EcoResponse response = destination.get().deliverAmount(ctx, currency, given);
                if (!response.isSuccess()) {
                    notGiven(ctx, currency, given, to + " refused it: " + response);
                }
                return;
            }
            String noWallet = noWallet(currency, ctx);
            EcoResponse response = noWallet != null ? null : currency.give(CurrencyOwner.of(ctx.getOwner()).get(), given, ctx.getCaller());
            if (response == null || !response.isSuccess()) {
                notGiven(ctx, currency, given, noWallet != null ? noWallet : String.valueOf(response));
            }
        }

        private static void notGiven(ExecutionContext ctx, Currency currency, BigDecimal given, String why) {
            ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, "money " + currency.format(given).getText() + " to " + ctx.getOwner()
                    + " not given: " + why, given, null);
            EverNifeCore.getLog().warning("[" + ctx.getCaller() + "] money " + currency.format(given).getText() + " to " + ctx.getOwner()
                    + " was not given: " + why);
        }

        /** Null when the amount reads as no number here: applying it would fail and audit that. */
        @Override
        public @Nullable BigDecimal getAmount(ExecutionContext ctx) {
            try {
                return given(ctx);
            } catch (IllegalStateException notANumber) {
                return null;
            }
        }

        /** The amount times the share, times the reward payment's frozen multiplier when there is one. */
        private BigDecimal given(ExecutionContext ctx) {
            BigDecimal given = amount(ctx, amount).multiply(share);
            BigDecimal multiplier = ctx.getAttribute(Rewards.MULTIPLIER_ATTRIBUTE, BigDecimal.class);
            return multiplier == null ? given : given.multiply(multiplier);
        }

        @Override
        public Effect withShare(BigDecimal newShare) {
            return new MoneyEffect(amount, currencyId, share.multiply(newShare));
        }

        @Override
        public String toString() {
            return "money(" + amount + " " + currencyId
                    + (share.compareTo(BigDecimal.ONE) == 0 ? ")" : " x " + share + ")");
        }
    }
}
