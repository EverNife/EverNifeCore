package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin's file that cannot be parsed is moved aside and opened empty; the plugin's log has to say so. */
@ECoreTest
class ConfigFactoryUnreadableTest {

    private static final String PLUGIN = "UnreadableConfigPlugin";
    private static final String WARNING = "could not be read (broken YAML)";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        Plugins.forget(PLUGIN);
    }

    private ECPluginData pluginWith(String yaml) throws Exception {
        Path folder = tempDir.resolve(PLUGIN);
        Files.createDirectories(folder);
        Files.write(folder.resolve("config.yml"), yaml.getBytes(StandardCharsets.UTF_8));
        return Plugins.fakePluginData(PLUGIN, folder.toFile());
    }

    @Test
    void aBrokenFileWarnsNamingItAndItsBackup() throws Exception {
        ECPluginData plugin = pluginWith("settings: [unclosed\n  - : :\n");

        List<String> logged = Logs.capture(() -> ConfigFactory.open(plugin, "config.yml"));

        assertTrue(logged.stream().anyMatch(line -> line.contains(WARNING) && line.contains("config.yml.bak")), logged.toString());
    }

    @Test
    void aGoodFileWarnsNothing() throws Exception {
        ECPluginData plugin = pluginWith("settings:\n  enabled: true\n");

        List<String> logged = Logs.capture(() -> ConfigFactory.open(plugin, "config.yml"));

        assertFalse(logged.stream().anyMatch(line -> line.contains(WARNING)), logged.toString());
    }
}
