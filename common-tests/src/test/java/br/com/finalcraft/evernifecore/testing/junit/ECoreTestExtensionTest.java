package br.com.finalcraft.evernifecore.testing.junit;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertTrue(platform.shutdownReasons().contains("a reason"));
    }
}
