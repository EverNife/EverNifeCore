package br.com.finalcraft.evernifecore.minecraft.integration.placeholders;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A plugin's PlaceholderAPI expansions leave with the plugin: its disable runs every undo recorded for it,
 * once, and a PlaceholderAPI that cannot unregister (an old build on 1.7.10) costs a warning, not the rest.
 */
@ECoreTest
class McPAPIIntegrationDisableTest {

    @Test
    void theOwnersDisableUnregistersEachOfItsExpansionsOnce() {
        List<String> undone = new ArrayList<>();
        McPAPIIntegration.unregisterOnDisable("Owner", () -> undone.add("owner_a"));
        McPAPIIntegration.unregisterOnDisable("Owner", () -> undone.add("owner_b"));
        McPAPIIntegration.unregisterOnDisable("Bystander", () -> undone.add("bystander"));

        McPAPIIntegration.unregisterAll("Owner");
        McPAPIIntegration.unregisterAll("Owner");

        assertEquals(Arrays.asList("owner_a", "owner_b"), undone, "only the owner's expansions, and only once");

        McPAPIIntegration.unregisterAll("Bystander");
        assertEquals(Arrays.asList("owner_a", "owner_b", "bystander"), undone);
    }

    @Test
    void anUnregisterThePlaceholderAPIBuildLacksDoesNotStopTheOthers() {
        List<String> undone = new ArrayList<>();
        McPAPIIntegration.unregisterOnDisable("Legacy", () -> {
            throw new NoSuchMethodError("me.clip.placeholderapi.PlaceholderAPI.unregisterPlaceholderHook");
        });
        McPAPIIntegration.unregisterOnDisable("Legacy", () -> undone.add("second"));

        McPAPIIntegration.unregisterAll("Legacy");

        assertEquals(Collections.singletonList("second"), undone);
    }

    @Test
    void aPluginThatRegisteredNothingIsANoOp() {
        McPAPIIntegration.unregisterAll("NeverRegistered");
    }
}
