package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.economy.LazyEconomyProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builds the economy a test runs against, and hands it to {@link ECoreTestWorld#withEconomy}.
 *
 * <pre>{@code
 * try (ECoreTestWorld world = Platforms.lenient().install()
 *         .withEconomy(Economies.inMemory().balance(uuid, 500).build())) {
 *     ...
 * }
 * }</pre>
 *
 * <p>{@link #absent()} is the other world worth testing: a provider IS registered, but there is no
 * economy behind it - what a server with Vault and no economy plugin looks like. Every call throws,
 * which is the contract, and {@code FCEcoUtil.isEcoAvailable()} is the way to see it coming.</p>
 */
public final class Economies {

    private final TestEconomy economy = new TestEconomy();

    private Economies() {
    }

    /** An economy that keeps balances in a map and obeys every rule of the contract. */
    public static Economies inMemory() {
        return new Economies();
    }

    /** A registered provider with nothing behind it: {@code isAvailable()} is false and every call throws. */
    public static IEconomyProvider absent() {
        return new AbsentEconomy();
    }

    public Economies balance(UUID playerUUID, double amount) {
        return balance(playerUUID, BigDecimal.valueOf(amount));
    }

    public Economies balance(UUID playerUUID, BigDecimal amount) {
        economy.seed(playerUUID, amount);
        return this;
    }

    public TestEconomy build() {
        return economy;
    }

    private static final class AbsentEconomy extends LazyEconomyProvider {

        @Override
        protected IEconomyProvider resolve() {
            return null;
        }

        @Override
        protected void logMissingEconomy() {
            //A test asserts on the exception, not on the console.
        }
    }

}
