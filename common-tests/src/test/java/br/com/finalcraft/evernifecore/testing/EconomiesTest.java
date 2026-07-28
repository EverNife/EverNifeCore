package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine's own economy, checked against the engine's own conformance suite: it proves the double
 * obeys the contract AND that the suite can tell when something does not.
 */
public class EconomiesTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void inMemoryEconomyConformsToTheReadOnlyChecks() {
        List<String> failures = EconomyConformance.check(Economies.inMemory().build());

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void inMemoryEconomyConformsToTheMutatingChecks() {
        List<String> failures = EconomyConformance.checkMutating(Economies.inMemory().build(), PLAYER);

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void theMutatingChecksPutTheOriginalBalanceBack() {
        TestEconomy economy = Economies.inMemory().balance(PLAYER, 42).build();

        EconomyConformance.checkMutating(economy, PLAYER);

        assertEquals(0, economy.getBalance(PLAYER).compareTo(new BigDecimal("42")));
    }

    @Test
    void aSeededBalanceIsWhatTheProviderAnswers() {
        TestEconomy economy = Economies.inMemory().balance(PLAYER, 250.5).build();

        assertEquals(0, economy.getBalance(PLAYER).compareTo(new BigDecimal("250.5")));
        assertTrue(economy.hasEnough(PLAYER, new BigDecimal("250.5")));
        assertFalse(economy.hasEnough(PLAYER, new BigDecimal("250.6")));
    }

    @Test
    void anAbsentEconomyIsUnavailableAndRefusesEveryCall() {
        IEconomyProvider absent = Economies.absent();

        assertFalse(absent.isAvailable());
        assertThrows(IllegalStateException.class, () -> absent.getBalance(PLAYER));
        assertThrows(IllegalStateException.class, () -> absent.give(PLAYER, BigDecimal.TEN));
        assertThrows(IllegalStateException.class, () -> absent.format(BigDecimal.TEN));
    }

    @Test
    void theConformanceSuiteCatchesAProviderThatBreaksTheContract() {
        List<String> failures = EconomyConformance.checkMutating(new NoOpSetIsFailure(), PLAYER);

        assertFalse(failures.isEmpty(), "a provider that reports a no-op set as a failure has to be caught");
    }

    /**
     * Reports the old Vault v1 behaviour - setting the balance the account already holds comes back as a
     * failure - and is otherwise honest, so the suite has to catch that one rule and nothing else.
     */
    private static final class NoOpSetIsFailure implements IEconomyProvider {

        private final TestEconomy delegate = Economies.inMemory().build();

        @Override
        public BigDecimal getBalance(UUID playerUUID) {
            return delegate.getBalance(playerUUID);
        }

        @Override
        public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
            return delegate.hasEnough(playerUUID, amount);
        }

        @Override
        public EcoResponse give(UUID playerUUID, BigDecimal amount) {
            return delegate.give(playerUUID, amount);
        }

        @Override
        public EcoResponse take(UUID playerUUID, BigDecimal amount) {
            return delegate.take(playerUUID, amount);
        }

        @Override
        public EcoResponse set(UUID playerUUID, BigDecimal amount) {
            if (amount.signum() >= 0 && delegate.getBalance(playerUUID).compareTo(amount) == 0) {
                return EcoResponse.providerError(amount, amount, "nothing to move");
            }
            return delegate.set(playerUUID, amount);
        }

        @Override
        public Object getHandle() {
            return delegate.getHandle();
        }
    }

}
