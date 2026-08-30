package br.com.finalcraft.evernifecore.minecraft.integration.placeholders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link McPAPIIntegration}'s presence probe gates every expansion registration and every
 * {@code parse} call on Bukkit. It once probed for the Hytale PlaceholderAPI's main class,
 * which no Bukkit server ever loads, so it answered {@code false} with the plugin installed -
 * no error, no log, just third-party tokens passing through unresolved. The test classpath
 * carries the same PlaceholderAPI jar the module compiles against, so a probe that names the
 * wrong class fails here instead of on a live server.
 */
class McPAPIIntegrationPresenceTest {

    @Test
    void theProbeFindsTheBukkitPlaceholderAPI() {
        assertTrue(McPAPIIntegration.isPresent());
    }
}
