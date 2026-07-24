package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser names are matched case-insensitively, and two names that differ only in case are rejected
 * where the mistake is - at registration - instead of producing an order-dependent winner at
 * lookup time.
 */
public class PlaceholderProviderCaseContractTest {

    @Test
    void aParserResolvesRegardlessOfHowTheTokenIsCased() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("player", o -> "Steve");

        assertEquals("Steve", replacer.apply("%player%", new Object()));
        assertEquals("Steve", replacer.apply("%PLAYER%", new Object()));
        assertEquals("Steve", replacer.apply("%PlAyEr%", new Object()));
    }

    @Test
    void registeringTwoNamesThatDifferOnlyInCaseThrowsAtRegistration() {
        PlaceholderProvider<Object> provider = new PlaceholderProvider<>().addParser("player", o -> "first");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> provider.addParser("Player", o -> "second"));

        assertTrue(thrown.getMessage().contains("Player") && thrown.getMessage().contains("player"),
                "the failure must name both colliding registrations: " + thrown.getMessage());
        assertEquals("first", provider.parse(new Object(), "player"),
                "the rejected registration must not have replaced the existing parser");
    }

    @Test
    void registeringTheSameNameTwiceStillOverridesSilently() {
        PlaceholderProvider<Object> provider = new PlaceholderProvider<Object>()
                .addParser("player", o -> "first")
                .addParser("player", o -> "second");

        assertEquals("second", provider.parse(new Object(), "player"));
    }
}
