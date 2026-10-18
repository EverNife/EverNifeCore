package br.com.finalcraft.evernifecore.minecraft.integration.placeholders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link McPAPIIntegration}'s presence probe gates every expansion registration and every
 * {@code parse} call on Bukkit. It once probed for the Hytale PlaceholderAPI's main class,
 * which no Bukkit server ever loads, so it answered {@code false} with the plugin installed -
 * no error, no log, just third-party tokens passing through unresolved. It also once answered
 * {@code true} from the class alone, and a PlaceholderAPI that never enabled made {@code parse}
 * die with a {@code NoClassDefFoundError}. The test classpath carries the PlaceholderAPI jar the
 * module compiles against and no server enabled it: the class is there, the plugin is not.
 */
class McPAPIIntegrationPresenceTest {

    @Test
    void theProbeFindsTheBukkitPlaceholderAPI() {
        assertTrue(McPAPIIntegration.isApiOnClasspath());
    }

    @Test
    void aPlaceholderAPIThatIsNotEnabledIsNotPresentAndParseLeavesTheText() {
        assertFalse(McPAPIIntegration.isPresent());
        assertEquals("hello %player_name%", McPAPIIntegration.parse(null, "hello %player_name%"));
    }
}
