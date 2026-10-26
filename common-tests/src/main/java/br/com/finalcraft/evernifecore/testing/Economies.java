package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.economy.LazyEconomyProvider;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
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
 * economy behind it - what a server with Vault and no economy plugin looks like. {@code Currencies.money()}
 * then answers {@code UNAVAILABLE} without throwing, and {@code FCEcoUtil.isEcoAvailable()} is false.</p>
 *
 * <p>{@link #currency(String)} is the other piece: a plugin's own {@link TestCurrency}, registered with
 * {@link ECoreTestWorld#withCurrency}.</p>
 */
public final class Economies {

    private final TestEconomy economy = new TestEconomy();

    private Economies() {
    }

    /** An economy that keeps balances in a map and obeys every rule of the contract. */
    public static Economies inMemory() {
        return new Economies();
    }

    /**
     * A plugin's currency {@code id} ({@code owner:name}) in memory, with wallets for every owner kind, scale 2.
     */
    public static TestCurrency currency(String id) {
        return new TestCurrency(id, NumericSpec.MONEY, EnumSet.allOf(CurrencyOwner.Kind.class));
    }

    /** Like {@link #currency(String)}, with wallets only for {@code kinds} - a clan vault is {@code GROUP} only. */
    public static TestCurrency currency(String id, CurrencyOwner.Kind... kinds) {
        return new TestCurrency(id, NumericSpec.MONEY, EnumSet.copyOf(Arrays.asList(kinds)));
    }

    /** A registered provider with nothing behind it: {@code isAvailable()} is false and every direct call throws. */
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
