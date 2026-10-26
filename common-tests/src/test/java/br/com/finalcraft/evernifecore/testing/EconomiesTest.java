package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.AbstractCurrency;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine's own economy, checked against the engine's own conformance suite: it proves the doubles obey the
 * contract AND that the suite can tell when something does not.
 */
@ECoreTest
public class EconomiesTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final CurrencyOwner WALLET = CurrencyOwner.player(PLAYER);

    @TempDir
    Path tempDir;

    private Caller caller;

    @BeforeEach
    void openBank() {
        caller = Caller.of(Plugins.fakePluginData("FakeBank", tempDir.toFile()), "economies-test");
    }

    @AfterEach
    void forgetBank() {
        Plugins.forget("FakeBank");
    }

    @Test
    void aTestCurrencyConformsToTheReadOnlyChecks() {
        List<String> failures = EconomyConformance.check(Economies.currency("fakebank:gold"));

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void aTestCurrencyConformsToTheMutatingChecks() {
        List<String> failures = EconomyConformance.checkMutating(Economies.currency("fakebank:gold", CurrencyOwner.Kind.GROUP),
                CurrencyOwner.group("fakebank:vault"), caller);

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void moneyOverTheInMemoryEconomyConformsToTheMutatingChecksAndPutsTheBalanceBack(ECoreTestWorld world) {
        TestEconomy economy = Economies.inMemory().balance(PLAYER, 42).build();
        world.withEconomy(economy);

        List<String> failures = EconomyConformance.checkMutating(Currencies.money(), WALLET, caller);

        assertEquals(Collections.<String>emptyList(), failures);
        assertEquals(0, economy.getBalance(PLAYER).compareTo(new BigDecimal("42")));
    }

    @Test
    void aSeededWalletIsWhatTheCurrencyAnswers() {
        TestCurrency gold = Economies.currency("fakebank:gold").balance(WALLET, 250.5);

        assertEquals(0, gold.getBalance(WALLET).compareTo(new BigDecimal("250.5")));
        assertTrue(gold.has(WALLET, new BigDecimal("250.5")));
        assertFalse(gold.has(WALLET, new BigDecimal("250.6")));
    }

    @Test
    void anAbsentEconomyThrowsOnADirectCallAndMakesMoneyUnavailable(ECoreTestWorld world) {
        IEconomyProvider absent = Economies.absent();
        world.withEconomy(absent);

        assertFalse(absent.isAvailable());
        assertThrows(IllegalStateException.class, () -> absent.getBalance(PLAYER));
        assertThrows(IllegalStateException.class, () -> absent.give(PLAYER, BigDecimal.TEN));
        assertEquals(Collections.<String>emptyList(), EconomyConformance.checkMutating(Currencies.money(), WALLET, caller));
        assertEquals(EcoResponse.Reason.UNAVAILABLE, Currencies.money().give(WALLET, BigDecimal.TEN, caller).getReason());
    }

    @Test
    void theConformanceSuiteCatchesACurrencyThatBreaksTheContract() {
        List<String> failures = EconomyConformance.checkMutating(new NoOpSetIsFailure(), WALLET, caller);

        assertFalse(failures.isEmpty(), "a currency that reports a no-op set as a failure has to be caught");
        assertTrue(failures.stream().anyMatch(line -> line.contains("set to the balance the wallet already has")), failures.toString());
    }

    /**
     * Reports the old Vault v1 behaviour - setting the balance the wallet already holds comes back as a failure - and
     * is otherwise honest, so the suite has to catch that one rule.
     */
    private static final class NoOpSetIsFailure extends AbstractCurrency {

        private final Map<CurrencyOwner, BigDecimal> wallets = new ConcurrentHashMap<>();

        NoOpSetIsFailure() {
            super("fakebank:broken", FancyText.of("broken"), NumericSpec.MONEY);
        }

        @Override
        public boolean supports(CurrencyOwner.Kind kind) {
            return true;
        }

        @Override
        public BigDecimal getBalance(CurrencyOwner owner) {
            return wallets.getOrDefault(owner, BigDecimal.ZERO);
        }

        @Override
        protected EcoResponse withdraw(CurrencyOwner owner, BigDecimal amount) {
            BigDecimal current = getBalance(owner);
            if (current.compareTo(amount) < 0) {
                return EcoResponse.insufficientFunds(amount, current);
            }
            return overwriteTo(owner, amount, current.subtract(amount));
        }

        @Override
        protected EcoResponse deposit(CurrencyOwner owner, BigDecimal amount) {
            return overwriteTo(owner, amount, getBalance(owner).add(amount));
        }

        @Override
        protected EcoResponse overwrite(CurrencyOwner owner, BigDecimal amount) {
            if (getBalance(owner).compareTo(amount) == 0) {
                return EcoResponse.providerError(amount, amount, "nothing to move");
            }
            return overwriteTo(owner, amount, amount);
        }

        private EcoResponse overwriteTo(CurrencyOwner owner, BigDecimal amount, BigDecimal balance) {
            wallets.put(owner, balance);
            return EcoResponse.success(amount, balance);
        }
    }
}
