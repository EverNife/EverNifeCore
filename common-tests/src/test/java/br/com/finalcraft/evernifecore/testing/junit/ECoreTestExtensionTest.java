package br.com.finalcraft.evernifecore.testing.junit;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the annotation installs a world and hands it to the test that asks for it. */
@ECoreTest
class ECoreTestExtensionTest {

    @Test
    void theAnnotatedClassRunsWithAnInstalledPlatform(ECoreTestWorld world) {
        assertSame(world.platform(), EverNifeCore.getProviders().getBaseProvider().provide(IPlatform.class));
    }

    @Test
    void aTestCanTakeThePlatformDirectlyToAssertOnWhatItCaptured(TestPlatform platform) {
        platform.shutdown("a reason");

        assertNotNull(platform.getPlatformProviderId());
        assertTrue(platform.getShutdownReasons().contains("a reason"));
    }

    @Test
    void aWatchOrAudienceLeftOnTheGlobalBusFailsTheCloseByName() {
        ECoreTestWorld world = Platforms.lenient().install();
        ECListenerWatch planted = ECEventBus.global().watchListeners(() -> {
        }, null, PlantedEvent.class);
        ECEventBus.global().addNativeAudience(new RecordingAudience("planted-audience"));
        try {
            AssertionError failure = assertThrows(AssertionError.class, world::close);

            assertTrue(failure.getMessage().contains("[PlantedEvent]"), failure.getMessage());
            assertTrue(failure.getMessage().contains("planted-audience"), failure.getMessage());
        } finally {
            planted.stop();
            ECEventBus.global().removeNativeAudience("planted-audience");
        }
    }

    static class PlantedEvent implements IECEvent {
    }
}
