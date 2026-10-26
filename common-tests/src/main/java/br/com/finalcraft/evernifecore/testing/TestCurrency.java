package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.AbstractCurrency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.everylibs.util.numberwrapper.Balance;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A plugin's own currency in a map of {@link Balance}s, one per {@link CurrencyOwner}: what a points plugin or a clan
 * vault looks like to the core. It keeps wallets for the kinds it was built with (every kind by default) and counts
 * the takes that went through. Build it through {@link Economies#currency}. Any thread.
 */
public final class TestCurrency extends AbstractCurrency {

    private final Map<CurrencyOwner, Balance> wallets = new ConcurrentHashMap<>();
    private final Set<CurrencyOwner.Kind> kinds;
    private final AtomicInteger takes = new AtomicInteger();

    TestCurrency(String id, NumericSpec spec, Set<CurrencyOwner.Kind> kinds) {
        super(id, FancyText.of(id.substring(id.indexOf(':') + 1)), spec);
        this.kinds = EnumSet.copyOf(kinds);
    }

    /** Seeds {@code owner}'s wallet without an event or a caller. */
    public TestCurrency balance(CurrencyOwner owner, double amount) {
        return balance(owner, BigDecimal.valueOf(amount));
    }

    public TestCurrency balance(CurrencyOwner owner, BigDecimal amount) {
        wallet(owner).set(amount);
        return this;
    }

    /** How many takes moved funds - a refused one does not count. */
    public int getTakes() {
        return takes.get();
    }

    @Override
    public boolean supports(CurrencyOwner.Kind kind) {
        return kinds.contains(kind);
    }

    @Override
    public BigDecimal getBalance(CurrencyOwner owner) {
        Balance balance = wallets.get(owner);
        return balance != null ? balance.get() : BigDecimal.ZERO;
    }

    @Override
    protected EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
        Balance balance = wallet(owner);
        if (!balance.tryTake(amount)) {
            return EcoResponse.insufficientFunds(amount, balance.get());
        }
        takes.incrementAndGet();
        return EcoResponse.success(amount, balance.get());
    }

    @Override
    protected EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
        return EcoResponse.success(amount, wallet(owner).give(amount));
    }

    @Override
    protected EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
        return EcoResponse.success(amount, wallet(owner).set(amount));
    }

    private Balance wallet(CurrencyOwner owner) {
        return wallets.computeIfAbsent(owner, key -> Balance.of(getSpec()));
    }
}
