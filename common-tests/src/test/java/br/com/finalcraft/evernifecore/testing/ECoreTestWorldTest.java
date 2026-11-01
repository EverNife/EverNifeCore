package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The core's own plugin data is global state: a world lends one and hands back whatever was there before. */
class ECoreTestWorldTest {

    @TempDir
    Path tempDir;

    private ECPluginData before;

    @BeforeEach
    void rememberTheCore() {
        before = EverNifeCore.getEcPluginData();
    }

    @AfterEach
    void restoreTheCore() {
        EverNifeCore.instance.onLoaderInstantiate(before);
    }

    @Test
    void closeHandsBackTheCorePluginData() {
        EverNifeCore.instance.onLoaderInstantiate(null);

        ECoreTestWorld world = Platforms.lenient().install();
        assertNotNull(EverNifeCore.getEcPluginData(), "a world gives the core a plugin data when nobody did");
        world.close();

        assertNull(EverNifeCore.getEcPluginData(), "the core had none before the world, and has none after it");
    }

    @Test
    void aPluginDataLentToTheCoreGoesBackOnClose() {
        try (ECoreTestWorld outer = Platforms.lenient().install()) {
            ECPluginData original = Plugins.fakeCore(tempDir.resolve("original").toFile());
            Plugins.forget(Plugins.CORE);
            outer.withCorePluginData(original);

            ECoreTestWorld world = Platforms.lenient().install();
            ECPluginData lent = Plugins.fakePluginData("LentCore", tempDir.resolve("lent").toFile());
            world.withCorePluginData(lent);
            assertSame(lent, EverNifeCore.getEcPluginData());
            Plugins.forget("LentCore");
            world.close();

            assertSame(original, EverNifeCore.getEcPluginData(), "the inner world hands back the outer world's core");
        }
    }
}
