package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A composite price: lines of any registered currencies, and - for a BUY - of items, each scaled by the quantity.
 * {@code Price.of(money, 250).and(gold, 2)}. Immutable: {@link #and} returns a new price.
 */
public final class Price {

    private static final Price FREE = new Price(Collections.<PriceLine>emptyList());

    private final List<PriceLine> lines;

    private Price(List<PriceLine> lines) {
        this.lines = Collections.unmodifiableList(lines);
    }

    public static Price free() {
        return FREE;
    }

    public static Price of(Currency currency, BigDecimal unitAmount) {
        return FREE.and(currency, unitAmount);
    }

    public static Price of(Currency currency, double unitAmount) {
        return of(currency, BigDecimal.valueOf(unitAmount));
    }

    /** A price read from {@code unitAmount} at every quote - a dynamic value, a stock price. */
    public static Price of(Currency currency, Variable<BigDecimal> unitAmount) {
        return FREE.and(currency, unitAmount);
    }

    public Price and(Currency currency, BigDecimal unitAmount) {
        Objects.requireNonNull(unitAmount, "unitAmount");
        if (unitAmount.signum() < 0) {
            throw new IllegalArgumentException("A price of " + unitAmount.toPlainString() + " " + currency.getId() + " is negative: write zero"
                    + " or more. Paying the player is a SELL offer.");
        }
        return with(new CurrencyLine(currency, ctx -> unitAmount, unitAmount.toPlainString()));
    }

    public Price and(Currency currency, double unitAmount) {
        return and(currency, BigDecimal.valueOf(unitAmount));
    }

    public Price and(Currency currency, Variable<BigDecimal> unitAmount) {
        Objects.requireNonNull(unitAmount, "unitAmount");
        return with(new CurrencyLine(currency, unitAmount::read, "{" + unitAmount.getId() + "}"));
    }

    /** {@code unitCount} items {@code matcher} accepts, per unit - item for item; a SELL offer refuses it. */
    public Price andItem(ItemMatcher matcher, int unitCount) {
        if (unitCount < 1) {
            throw new IllegalArgumentException("An item price line takes at least 1 item per unit, not " + unitCount + ".");
        }
        return with(new ItemLine(matcher, unitCount));
    }

    private Price with(PriceLine line) {
        for (PriceLine existing : lines) {
            if (existing.getId().equalsIgnoreCase(line.getId())) {
                throw new IllegalArgumentException("The price already has a line " + line.getId() + ": write one line per currency or"
                        + " item, with the whole amount on it.");
            }
        }
        List<PriceLine> all = new ArrayList<>(lines);
        all.add(line);
        return new Price(all);
    }

    public List<PriceLine> getLines() {
        return lines;
    }

    /** The totals for {@code quantity} units, every line read now; nothing taken. */
    public Quote quote(ExecutionContext ctx, int quantity) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal units = BigDecimal.valueOf(quantity);
        for (PriceLine line : lines) {
            totals.put(line.getId(), line.unitAmount(ctx).multiply(units));
        }
        return new Quote(lines, totals, null);
    }

    @Override
    public String toString() {
        List<String> ids = new ArrayList<>();
        for (PriceLine line : lines) {
            ids.add(line.toString());
        }
        return ids.isEmpty() ? "free" : String.join(" + ", ids);
    }

    private static Optional<CurrencyOwner> wallet(ExecutionContext ctx) {
        return CurrencyOwner.of(ctx.getOwner());
    }

    private interface Amount {
        BigDecimal read(ExecutionContext ctx);
    }

    private static final class CurrencyLine implements PriceLine {
        private final Currency currency;
        private final Amount unitAmount;
        private final String written;

        CurrencyLine(Currency currency, Amount unitAmount, String written) {
            this.currency = Objects.requireNonNull(currency, "currency");
            this.unitAmount = unitAmount;
            this.written = written;
        }

        @Override
        public String getId() {
            return currency.getId();
        }

        @Override
        public BigDecimal unitAmount(ExecutionContext ctx) {
            BigDecimal amount = unitAmount.read(ctx);
            return amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
        }

        @Override
        public BigDecimal available(ExecutionContext ctx) {
            return wallet(ctx).map(currency::getBalance).orElse(BigDecimal.ZERO);
        }

        @Override
        public FancyText describe(ExecutionContext ctx, BigDecimal total) {
            return currency.format(total);
        }

        @Override
        public Cost asCost(BigDecimal total) {
            return new Cost() {
                @Override
                public ConditionResult test(ExecutionContext ctx) {
                    Optional<CurrencyOwner> owner = wallet(ctx);
                    if (!owner.isPresent()) {
                        return ConditionResult.fail(FancyText.of(currency.getId() + " has no wallet for " + ctx.getOwner() + "."));
                    }
                    return ConditionResult.of(currency.has(owner.get(), total),
                            FancyText.of("Not enough " + currency.getId() + ": " + currency.format(total).toPlainText() + " needed, "
                                    + currency.format(currency.getBalance(owner.get())).toPlainText() + " held."));
                }

                @Override
                public boolean take(ExecutionContext ctx) {
                    Optional<CurrencyOwner> owner = wallet(ctx);
                    return owner.isPresent() && currency.take(owner.get(), total, ctx.getCaller()).isSuccess();
                }

                @Override
                public void refund(ExecutionContext ctx) {
                    EcoResponse given = currency.give(wallet(ctx).orElseThrow(() -> new IllegalStateException(
                            currency.getId() + " has no wallet for " + ctx.getOwner())), total, ctx.getCaller());
                    if (!given.isSuccess()) {
                        throw new IllegalStateException(currency.getId() + " refused the refund: " + given);
                    }
                }

                @Override
                public FancyText describe(ExecutionContext ctx) {
                    return currency.format(total);
                }

                @Override
                public String toString() {
                    return "price " + total.toPlainString() + " " + currency.getId();
                }
            };
        }

        @Override
        public boolean canPay() {
            return true;
        }

        @Override
        public EcoResponse pay(ExecutionContext ctx, BigDecimal total) {
            return wallet(ctx).map(owner -> currency.give(owner, total, ctx.getCaller()))
                    .orElseGet(() -> EcoResponse.unsupportedOwner(total, currency.getId() + " has no wallet for " + ctx.getOwner() + "."));
        }

        @Override
        public String toString() {
            return written + " " + currency.getId();
        }
    }

    static final class ItemLine implements PriceLine {
        private final ItemMatcher matcher;
        private final int unitCount;

        ItemLine(ItemMatcher matcher, int unitCount) {
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.unitCount = unitCount;
        }

        @Override
        public String getId() {
            return "item:" + matcher.getMaterialId();
        }

        @Override
        public BigDecimal unitAmount(ExecutionContext ctx) {
            return BigDecimal.valueOf(unitCount);
        }

        @Override
        public BigDecimal available(ExecutionContext ctx) {
            Optional<InventoryDestination> inventory = DeliveryDestinations.inventory();
            return ctx.getPlayer() == null || !inventory.isPresent() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(inventory.get().count(ctx.getPlayer(), matcher));
        }

        @Override
        public FancyText describe(ExecutionContext ctx, BigDecimal total) {
            return FancyText.of(total.toPlainString() + " x " + matcher.getMaterialId());
        }

        @Override
        public Cost asCost(BigDecimal total) {
            int amount = total.intValueExact();
            return new Cost() {
                @Override
                public ConditionResult test(ExecutionContext ctx) {
                    int held = available(ctx).intValue();
                    return ConditionResult.of(held >= amount, FancyText.of("Not enough " + matcher + ": " + held + " of " + amount + "."));
                }

                @Override
                public boolean take(ExecutionContext ctx) {
                    FPlayer player = ctx.getPlayer();
                    return player != null && DeliveryDestinations.requireInventory().take(player, matcher, amount);
                }

                /** Hands the items back; what no longer fits waits as a pending delivery. */
                @Override
                public void refund(ExecutionContext ctx) {
                    ItemSpec rest = DeliveryDestinations.requireInventory().deliverItem(ctx, matcher.toItem(amount));
                    if (rest != null && rest.getAmount() > 0) {
                        Deliveries.get().keepFor(ctx, DeliveryPolicy.KEEP_SELF_CONTAINED, null, PendingDestination.giveItemNode(rest, null));
                    }
                }

                @Override
                public FancyText describe(ExecutionContext ctx) {
                    return ItemLine.this.describe(ctx, total);
                }

                @Override
                public String toString() {
                    return "price " + amount + " x " + matcher;
                }
            };
        }

        @Override
        public boolean canPay() {
            return false;
        }

        @Override
        public EcoResponse pay(ExecutionContext ctx, BigDecimal total) {
            throw new UnsupportedOperationException("The item line " + getId() + " cannot pay a player: only a currency line can.");
        }

        @Override
        public String toString() {
            return unitCount + " x " + matcher;
        }
    }
}
