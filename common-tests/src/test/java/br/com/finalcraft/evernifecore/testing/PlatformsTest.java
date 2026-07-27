package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.ECBaseProvider;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two properties the whole engine rests on: a strict double refuses in a way that teaches, and
 * an installed world always puts back what it found.
 */
class PlatformsTest {

    private static ECBaseProvider providers() {
        return EverNifeCore.getProviders().getBaseProvider();
    }

    @Test
    void strictRefusesAndTheMessageNamesTheMethodAndTheFix() {
        TestPlatform platform = Platforms.strict().build();

        UnsupportedOperationException thrown =
                assertThrows(UnsupportedOperationException.class, platform::getChatAdapter);

        assertTrue(thrown.getMessage().contains("getChatAdapter"),
                "the message has to name the refusing method: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Platforms.strict().chatAdapter("),
                "the message has to name the call that configures it: " + thrown.getMessage());
    }

    @Test
    void aStrictPlatformStillAnswersWhatItWasTaught() {
        TestPlatform platform = Platforms.strict()
                .platformProviderId("test")
                .pluginsLoaded("Vault", "WorldEdit")
                .build();

        assertEquals("test", platform.getPlatformProviderId());
        assertTrue(platform.isPluginLoaded("Vault"));
        assertFalse(platform.isPluginLoaded("AuthMe"));
        assertThrows(UnsupportedOperationException.class, platform::isPAPIPresent);
    }

    @Test
    void lenientAnswersEverythingWithTheOldNoOpDefaults() {
        TestPlatform platform = Platforms.lenient().build();

        assertEquals("test", platform.getPlatformProviderId());
        assertTrue(platform.getOnlinePlayers().isEmpty());
        assertFalse(platform.isPluginLoaded("anything"));
        assertFalse(platform.isPAPIPresent());
        assertFalse(platform.serverSupportsActionBar());
        assertNull(platform.getChatAdapter());
        assertNull(platform.getVecAdapter());
        assertEquals("§atext", platform.parse(null, "§atext"));
        assertNotNull(platform.createLogAdapterFor(null));
    }

    @Test
    void lenientRunsMainThreadTasksInPlaceSoABootSequenceIsDeterministic() {
        TestPlatform platform = Platforms.lenient().build();
        StringBuilder ran = new StringBuilder();

        platform.runOnMainThread(() -> ran.append("ran"));

        assertEquals("ran", ran.toString());
        assertEquals("value", platform.runOnMainThread(() -> "value").join());
    }

    @Test
    void shutdownIsRecordedInsteadOfKillingTheTestJvm() {
        TestPlatform platform = Platforms.lenient().build();

        platform.shutdown("storage is unreachable");

        assertEquals(1, platform.getShutdownReasons().size());
        assertEquals("storage is unreachable", platform.getShutdownReasons().get(0));
    }

    @Test
    void closingTheWorldRestoresThePlatformThatWasThereBefore() {
        IPlatform original = Platforms.lenient().build();
        providers().register(IPlatform.class, original);

        try (ECoreTestWorld world = Platforms.strict().install()) {
            assertSame(world.platform(), providers().provide(IPlatform.class));
        }

        assertSame(original, providers().provide(IPlatform.class),
                "the world has to put back exactly what it found");
        providers().unregister(IPlatform.class);
    }

    @Test
    void closingTheWorldLeavesNothingBehindWhenThereWasNothingBefore() {
        providers().unregister(IPlatform.class);

        try (ECoreTestWorld world = Platforms.lenient().install()) {
            assertNotNull(providers().provideOrNull(IPlatform.class));
        }

        assertNull(providers().provideOrNull(IPlatform.class),
                "an install into an empty registry has to leave it empty again");
    }

    @Test
    void capturingCommandsRecordsRegistrationInsteadOfRefusingIt() {
        TestPlatform platform = Platforms.commandCapture().build();

        assertNotNull(platform.getChatAdapter());
        assertTrue(platform.registrationOrder().isEmpty());
        assertNull(platform.getCaptured("nothing-registered-yet"));
    }
}
