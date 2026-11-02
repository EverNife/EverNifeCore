package br.com.finalcraft.evernifecore.testing.minecraft.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

/** A chest world leaves the core registry as it found it. */
class BukkitScreenWorldCoreTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aCoreRegisteredBeforeTheWorldIsStillRegisteredAfterItCloses() {
        ECoreTestWorld outer = Platforms.lenient().install();
        try {
            ECPluginData before = Plugins.fakeCore(tempDir.resolve("core").toFile());

            BukkitScreenWorld.install(tempDir.resolve("chest")).close();

            assertSame(before, ECPluginManager.getECPluginsMap().get(Plugins.CORE));
        } finally {
            Plugins.forget(Plugins.CORE);
            outer.close();
        }
    }

}
