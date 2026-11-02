package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PluginsTest {

    @TempDir
    Path tempDir;

    @Test
    void fakeOtherKeepsTheInstalledExtractor() {
        IECPluginExtractor worldExtractor = Plugins.fake("World", tempDir.resolve("world").toFile());
        try (ECoreTestWorld ignored = Platforms.lenient().install().withPluginExtractor(worldExtractor)) {
            try {
                ECPluginData other = Plugins.fakeOther("FinalPoints", tempDir.resolve("points").toFile());

                assertEquals("FinalPoints", other.getMetaInfo().getName());
                assertSame(worldExtractor, EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class));
                assertEquals("World", ECPluginManager.getOrCreateECorePluginData(new Object()).getMetaInfo().getName());
            } finally {
                Plugins.forget("FinalPoints");
                Plugins.forget("World");
            }
        }
    }
}
