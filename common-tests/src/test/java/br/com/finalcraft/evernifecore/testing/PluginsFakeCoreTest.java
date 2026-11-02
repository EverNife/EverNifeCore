package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A fake core built over a core someone else registered leaves that registration where it was. */
class PluginsFakeCoreTest {

    @TempDir
    Path tempDir;

    @Test
    void forgettingAFakeCoreHandsBackTheCoreThatWasRegistered() {
        try (ECoreTestWorld world = Platforms.lenient().install()) {
            //registered the way a BukkitScreenWorld registers its core: not through Plugins
            world.withPluginExtractor(Plugins.fake(Plugins.CORE, tempDir.resolve("registered").toFile()));
            ECPluginData registered = ECPluginManager.getOrCreateECorePluginData(new Object());
            try {
                Plugins.fakeCore(tempDir.resolve("fake").toFile());
                Plugins.forget(Plugins.CORE);

                assertSame(registered, ECPluginManager.getECPluginsMap().get(Plugins.CORE));
            } finally {
                ECPluginManager.removePluginData(Plugins.CORE);
            }
        }
    }

    @Test
    void aFakeCoreBuiltOnAnEmptyRegistryIsForgotten() {
        try (ECoreTestWorld world = Platforms.lenient().install()) {
            Plugins.fakeCore(tempDir.resolve("fake").toFile());
            Plugins.forget(Plugins.CORE);

            assertNull(ECPluginManager.getECPluginsMap().get(Plugins.CORE));
        }
    }

}
