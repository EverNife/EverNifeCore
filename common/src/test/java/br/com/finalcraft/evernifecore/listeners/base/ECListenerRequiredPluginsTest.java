package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A listener that needs another plugin is registered only while that plugin runs - installed is not enough. */
class ECListenerRequiredPluginsTest {

    private static final String PLUGIN_NAME = "RequiredPluginsTest";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData data;

    @BeforeEach
    void setup() {
        world = Platforms.lenient().pluginsLoaded("Vault").pluginsDisabled("WorldGuard").install();
        data = Plugins.fakePluginData(PLUGIN_NAME, tempDir.toFile());
        EverNifeCore.instance.onLoaderInstantiate(data);
    }

    @AfterEach
    void teardown() {
        ECListener.unregisterAll(data);
        Plugins.forget(PLUGIN_NAME);
        world.close();
    }

    static final class Requiring implements ECListener {
        private final String plugin;

        Requiring(String plugin) {
            this.plugin = plugin;
        }

        @Override
        public String[] requiredPlugins() {
            return new String[]{plugin};
        }

        @Override
        public boolean silentRegistration() {
            return true;
        }
    }

    @Test
    void anInstalledButDisabledPluginDoesNotSatisfyTheRequirement() {
        assertFalse(ECListener.register(data, new Requiring("WorldGuard")));
    }

    @Test
    void aRunningPluginDoes() {
        assertTrue(ECListener.register(data, new Requiring("Vault")));
    }
}
