package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The fake core a page world builds is the world's own: closing it hands back the core that was there before. */
class HyPageWorldTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void closingThePageWorldHandsBackThePreviousCore() {
        ECPluginData before = EverNifeCore.getEcPluginData();

        HyPageWorld.open(tempDir).close();

        assertSame(before, EverNifeCore.getEcPluginData());
        assertFalse(ECPluginManager.getECPluginsMap().containsKey(Plugins.CORE), "the fake core leaves the registry");
    }
}
