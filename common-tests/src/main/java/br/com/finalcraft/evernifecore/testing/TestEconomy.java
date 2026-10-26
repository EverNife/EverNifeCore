package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An economy bridge that lives in a map: what {@code Currencies.money()} runs on in a test.
 *
 * <p>It keeps what a bridge owes - a {@code set} to the balance an account already has succeeds and a
 * withdraw never leaves a negative balance; the amount rules are the {@code money} currency's, which calls
 * this only with a positive (or, for {@code set}, non-negative) amount.</p>
 *
 * <p>Build it through {@link Economies}.</p>
 */
public final class TestEconomy implements IEconomyProvider {

    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<UUID, BigDecimal>();

    TestEconomy() {
    }

    void seed(UUID playerUUID, BigDecimal amount) {
        balances.put(playerUUID, amount);
    }

    @Override
    public BigDecimal getBalance(UUID playerUUID) {
        BigDecimal balance = balances.get(playerUUID);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
        return getBalance(playerUUID).compareTo(amount) >= 0;
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        return EcoResponse.success(amount, store(playerUUID, getBalance(playerUUID).add(amount)));
    }

    @Override
    public EcoResponse take(UUID playerUUID, BigDecimal amount) {
        BigDecimal current = getBalance(playerUUID);
        if (current.compareTo(amount) < 0) {
            return EcoResponse.insufficientFunds(amount, current);
        }
        return EcoResponse.success(amount, store(playerUUID, current.subtract(amount)));
    }

    @Override
    public EcoResponse set(UUID playerUUID, BigDecimal amount) {
        return EcoResponse.success(amount, store(playerUUID, amount));
    }

    @Override
    public String format(BigDecimal amount) {
        return "$" + amount.toPlainString();
    }

    @Override
    public Object getHandle() {
        return balances;
    }

    private BigDecimal store(UUID playerUUID, BigDecimal balance) {
        balances.put(playerUUID, balance);
        return balance;
    }

}
