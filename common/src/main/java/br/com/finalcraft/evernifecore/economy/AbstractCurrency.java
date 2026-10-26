package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * The one base every {@link Currency} extends. The public verbs are final and own the rules, in this order: a
 * negative amount is {@link EcoResponse.Reason#INVALID_AMOUNT}; an owner kind the currency does not
 * {@link #supports support} is {@link EcoResponse.Reason#UNSUPPORTED_OWNER}; an unavailable currency is
 * {@link EcoResponse.Reason#UNAVAILABLE}; zero is a successful no-op. Only past those does the implementation run,
 * and every change it reports posts exactly one {@link BalanceChangeEvent}.
 *
 * <p>An implementation writes {@link #withdraw}, {@link #deposit}, {@link #overwrite}, {@link #getBalance} and
 * {@link #supports}, each on the caller's thread without blocking on I/O; {@link #withdraw} checks and takes in one
 * step, so two racing takes never spend the same funds (an {@code everylibs} {@code Balance} does this by CAS).</p>
 */
public abstract class AbstractCurrency implements Currency {

    private final String id;
    private final FancyText displayName;
    private final NumericSpec spec;

    /**
     * @param id {@code owner:name}, the owner being the plugin that registers it
     * @throws IllegalArgumentException when the id is not {@code owner:name}
     */
    protected AbstractCurrency(String id, FancyText displayName, NumericSpec spec) {
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("A currency id is owner:name, the owner being the plugin that registers it"
                    + " (e.g. 'finaleconomy:gold'), not '" + id + "'.");
        }
        this.id = RegionId.requireOwner(id.substring(0, colon)) + ":" + RegionId.requireName("currency", id.substring(colon + 1));
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    /** Checks and takes {@code amount > 0} in one step; {@link EcoResponse.Reason#INSUFFICIENT_FUNDS} moves nothing. */
    protected abstract EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount);

    /** Adds {@code amount > 0}. */
    protected abstract EcoResponse deposit(CurrencyOwner owner, BigDecimal amount);

    /** Replaces the balance with {@code amount >= 0}. */
    protected abstract EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount);

    /** Why nothing can move while {@link #isAvailable()} is false, and what fixes it. */
    protected String describeUnavailable() {
        return "The currency " + id + " cannot move anything right now.";
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public FancyText getDisplayName() {
        return displayName;
    }

    @Override
    public NumericSpec getSpec() {
        return spec;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean has(CurrencyOwner owner, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        return supports(owner.getKind()) && isAvailable() && getBalance(owner).compareTo(amount) >= 0;
    }

    @Override
    public final EcoResponse take(CurrencyOwner owner, BigDecimal amount, Caller caller) {
        EcoResponse refused = refuse(owner, amount, caller);
        if (refused != null) {
            return refused;
        }
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, getBalance(owner)).withCurrency(this);
        }
        EcoResponse taken = withdraw(owner, amount).withCurrency(this);
        if (taken.isSuccess()) {
            changed(owner, taken.getBalance().add(amount), taken.getBalance(), BalanceChangeEvent.Cause.TAKE, caller);
        }
        return taken;
    }

    @Override
    public final EcoResponse give(CurrencyOwner owner, BigDecimal amount, Caller caller) {
        EcoResponse refused = refuse(owner, amount, caller);
        if (refused != null) {
            return refused;
        }
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, getBalance(owner)).withCurrency(this);
        }
        EcoResponse given = deposit(owner, amount).withCurrency(this);
        if (given.isSuccess()) {
            changed(owner, given.getBalance().subtract(amount), given.getBalance(), BalanceChangeEvent.Cause.GIVE, caller);
        }
        return given;
    }

    @Override
    public final EcoResponse set(CurrencyOwner owner, BigDecimal amount, Caller caller) {
        EcoResponse refused = refuse(owner, amount, caller);
        if (refused != null) {
            return refused;
        }
        BigDecimal before = getBalance(owner);
        EcoResponse set = overwrite(owner, amount).withCurrency(this);
        if (set.isSuccess() && set.getBalance().compareTo(before) != 0) {
            changed(owner, before, set.getBalance(), BalanceChangeEvent.Cause.SET, caller);
        }
        return set;
    }

    /**
     * Takes from {@code from}, then gives to {@code to}. A failed second leg gives the first back and answers with the
     * give's reason; a give-back that ALSO fails is logged SEVERE on the caller's plugin with the amount, and reaches
     * {@link TransferEvent#getRefund()}. A success answers with the payer's new balance. One {@link TransferEvent} for
     * every transfer that reached the currency.
     */
    @Override
    public final EcoResponse transfer(CurrencyOwner from, CurrencyOwner to, BigDecimal amount, Caller caller) {
        EcoResponse refused = refuse(from, amount, caller);
        if (refused == null) {
            refused = refuse(to, amount, caller);
        }
        if (refused != null) {
            return refused;
        }
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, getBalance(from)).withCurrency(this);
        }

        EcoResponse take = withdraw(from, amount).withCurrency(this);
        if (!take.isSuccess()) {
            transferred(from, to, amount, take, null, null, caller);
            return take;
        }
        changed(from, take.getBalance().add(amount), take.getBalance(), BalanceChangeEvent.Cause.TRANSFER_OUT, caller);

        EcoResponse give = deposit(to, amount).withCurrency(this);
        if (give.isSuccess()) {
            changed(to, give.getBalance().subtract(amount), give.getBalance(), BalanceChangeEvent.Cause.TRANSFER_IN, caller);
            transferred(from, to, amount, take, give, null, caller);
            return take;
        }

        EcoResponse refund = deposit(from, amount).withCurrency(this);
        if (refund.isSuccess()) {
            changed(from, refund.getBalance().subtract(amount), refund.getBalance(), BalanceChangeEvent.Cause.TRANSFER_REFUND, caller);
        } else {
            caller.getPlugin().getLog().severe("A transfer of " + amount.toPlainString() + " " + id + " from " + from + " to " + to
                    + " (" + caller + ") failed on the receiving side (" + give + ") AND could not be given back to " + from
                    + " (" + refund + "): " + from + " is short of " + amount.toPlainString() + " - give it back by hand.");
        }
        transferred(from, to, amount, take, give, refund, caller);
        return give;
    }

    /** Default: the amount at this currency's scale, then the display name - {@code 12.50 gold}. */
    @Override
    public FancyText format(BigDecimal amount) {
        return FancyText.of(spec.round(amount).toPlainString() + " " + displayName.getText());
    }

    @Override
    public Class<BigDecimal> getType() {
        return BigDecimal.class;
    }

    /** The balance of the context's owner. */
    @Override
    public BigDecimal read(ExecutionContext ctx) {
        return getBalance(walletOf(ctx));
    }

    /** Sets the balance of the context's owner, on the context's caller. */
    @Override
    public void set(ExecutionContext ctx, BigDecimal value) {
        set(walletOf(ctx), value, ctx.getCaller());
    }

    @Override
    public String toString() {
        return id;
    }

    private CurrencyOwner walletOf(ExecutionContext ctx) {
        return CurrencyOwner.of(ctx.getOwner()).orElseThrow(() -> new IllegalArgumentException("The currency " + id
                + " has no wallet for the owner " + ctx.getOwner() + ": a currency keeps wallets for players, groups or the"
                + " server (global), never for a region or a world."));
    }

    private EcoResponse refuse(CurrencyOwner owner, BigDecimal amount, Caller caller) {
        Objects.requireNonNull(owner, "Name whose wallet moves - CurrencyOwner.player(uuid), group(id) or global() - 'owner' cannot be null.");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(caller, "Every move of a currency names who made it, Caller.of(plugin, \"shop/vip\") - 'caller' cannot be null.");
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount).withCurrency(this);
        }
        if (!supports(owner.getKind())) {
            return EcoResponse.unsupportedOwner(amount, describeUnsupported(owner.getKind())).withCurrency(this);
        }
        if (!isAvailable()) {
            return EcoResponse.unavailable(amount, describeUnavailable()).withCurrency(this);
        }
        return null;
    }

    /** Why this currency has no wallet for {@code kind}, and where such a wallet lives instead. */
    protected String describeUnsupported(CurrencyOwner.Kind kind) {
        return id + " has no wallet for " + kind.name().toLowerCase(Locale.ROOT) + " owners: a " + kind.name().toLowerCase(Locale.ROOT)
                + " balance is a Currency of the plugin that keeps it - register one with Currencies.register(owner, currency)"
                + " and name it in the cost (currency: <id>).";
    }

    private void changed(CurrencyOwner owner, BigDecimal before, BigDecimal after, BalanceChangeEvent.Cause cause, Caller caller) {
        ECEventBus.global().postIfListened(BalanceChangeEvent.class, () -> new BalanceChangeEvent(this, owner, before, after, cause, caller));
    }

    private void transferred(CurrencyOwner from, CurrencyOwner to, BigDecimal amount, EcoResponse take, EcoResponse give,
                             EcoResponse refund, Caller caller) {
        ECEventBus.global().postIfListened(TransferEvent.class, () -> new TransferEvent(this, from, to, amount, take, give, refund, caller));
    }
}
