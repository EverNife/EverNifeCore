package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An economy that lives in a map: the oracle a test asserts against.
 *
 * <p>It implements the contract's rules literally - negative amounts are refused before anything moves,
 * a {@code set} to the balance an account already has succeeds, and a withdraw never leaves a negative
 * balance - so a test that fails against it is a test that would fail against a real economy.</p>
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
        if (amount.signum() <= 0) {
            return true;
        }
        return getBalance(playerUUID).compareTo(amount) >= 0;
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }
        return EcoResponse.success(amount, store(playerUUID, getBalance(playerUUID).add(amount)));
    }

    @Override
    public EcoResponse take(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        BigDecimal current = getBalance(playerUUID);
        if (current.compareTo(amount) < 0) {
            return EcoResponse.insufficientFunds(amount, current);
        }
        return EcoResponse.success(amount, store(playerUUID, current.subtract(amount)));
    }

    @Override
    public EcoResponse set(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }
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
