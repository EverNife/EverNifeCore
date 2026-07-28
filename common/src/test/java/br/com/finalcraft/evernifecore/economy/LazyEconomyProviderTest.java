package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.TestEconomy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lazy resolution an economy plugin that enables after EverNifeCore depends on - and the reason the
 * console no longer fills up: the diagnostic is logged once, from the boot hook, never per call.
 */
public class LazyEconomyProviderTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @Test
    void anEconomyThatShowsUpLaterIsPickedUp() {
        ScriptedLazyEconomy lazy = new ScriptedLazyEconomy();

        assertFalse(lazy.isAvailable());
        assertThrows(IllegalStateException.class, () -> lazy.getBalance(PLAYER_UUID));

        lazy.economy = Economies.inMemory().balance(PLAYER_UUID, 70).build();

        assertTrue(lazy.isAvailable());
        assertEquals(0, lazy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("70")));
    }

    @Test
    void onceResolvedItIsNotResolvedAgain() {
        ScriptedLazyEconomy lazy = new ScriptedLazyEconomy();
        lazy.economy = Economies.inMemory().balance(PLAYER_UUID, 70).build();

        lazy.getBalance(PLAYER_UUID);
        int resolvesAfterFirstUse = lazy.resolveCalls;
        lazy.economy = null; //an economy plugin disabling at runtime does not invalidate the resolved one

        assertEquals(0, lazy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("70")));
        assertEquals(resolvesAfterFirstUse, lazy.resolveCalls);
    }

    @Test
    void theDiagnosticIsLoggedOncePerBootAndNeverPerCall() {
        ScriptedLazyEconomy lazy = new ScriptedLazyEconomy();

        lazy.warmUp();
        assertThrows(IllegalStateException.class, () -> lazy.getBalance(PLAYER_UUID));
        assertThrows(IllegalStateException.class, () -> lazy.give(PLAYER_UUID, BigDecimal.TEN));

        assertEquals(1, lazy.warnings, "the exception is what repeats, not EverNifeCore's own warning");
    }

    @Test
    void anEconomyAlreadyUpAtBootIsNotComplainedAbout() {
        ScriptedLazyEconomy lazy = new ScriptedLazyEconomy();
        lazy.economy = Economies.inMemory().build();

        lazy.warmUp();

        assertEquals(0, lazy.warnings);
    }

    private static final class ScriptedLazyEconomy extends LazyEconomyProvider {

        private TestEconomy economy;
        private int resolveCalls;
        private int warnings;

        @Override
        protected IEconomyProvider resolve() {
            resolveCalls++;
            return economy;
        }

        @Override
        protected void logMissingEconomy() {
            warnings++;
        }
    }

}
