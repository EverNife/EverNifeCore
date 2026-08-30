package br.com.finalcraft.evernifecore.hytale.integration.placeholders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HyPAPIIntegration}'s presence probe gates every expansion registration and every
 * {@code parse} call on Hytale. Its Bukkit sibling once probed for the other platform's
 * PlaceholderAPI main class and silently answered {@code false} on every server; this pins
 * the Hytale probe to the PlaceholderAPI jar this module actually compiles against, so the
 * same mix-up fails here instead of on a live server.
 */
class HyPAPIIntegrationPresenceTest {

    @Test
    void theProbeFindsTheHytalePlaceholderAPI() {
        assertTrue(HyPAPIIntegration.isPresent());
    }
}
