package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A folder an admin fills with one file per thing, and the default file a plugin ships in its jar. */
@ECoreTest
class ConfigFactoryFolderTest {

    private static final String PLUGIN_NAME = "FolderTestPlugin";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        ECPluginManager.removePluginData(PLUGIN_NAME);
    }

    private ECPluginData plugin() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    /** Stands in for the platform's plugin object; its class loader is the one holding the test assets. */
    public static final class FakePlugin {
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> idsOf(List<Config> configs) {
        return configs.stream().map(config -> config.getString("id")).collect(Collectors.toList());
    }

    @Test
    void aFlatReadTakesTheFolderAndARecursiveOneTheSubFoldersToo() throws IOException {
        Path crates = tempDir.resolve("crates");
        write(crates.resolve("b.yml"), "id: b\n");
        write(crates.resolve("a.yaml"), "id: a\n");
        write(crates.resolve("notes.txt"), "not a config\n");
        write(crates.resolve("tier2").resolve("c.yml"), "id: c\n");

        assertEquals(Arrays.asList("a", "b"), idsOf(ConfigFactory.openAll(plugin(), crates.toFile(), false)));
        assertEquals(Arrays.asList("a", "b", "c"), idsOf(ConfigFactory.openAll(plugin(), crates.toFile(), true)),
                "path order, sub-folder files included");
    }

    @Test
    void aFolderThatDoesNotExistYetHoldsNoFilesAndAFileIsRefused() throws IOException {
        assertTrue(ConfigFactory.openAll(plugin(), tempDir.resolve("nothing-here").toFile(), true).isEmpty());

        Path file = tempDir.resolve("single.yml");
        write(file, "id: x\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigFactory.openAll(plugin(), file.toFile(), true));
    }

    @Test
    void anAssetIsCopiedOnceAndNeverOverAnExistingFile() throws IOException {
        File target = tempDir.resolve("data").resolve("crates").resolve("example.yml").toFile();

        ConfigFactory.copyAsset(plugin(), "config-assets/example-crate.yml", target);
        assertEquals("Example", ConfigFactory.open(target).getString("Settings.crate_id"));

        write(target.toPath(), "Settings:\n  crate_id: Edited\n");
        ConfigFactory.copyAsset(plugin(), "config-assets/example-crate.yml", target);
        assertEquals("Edited", ConfigFactory.open(target).getString("Settings.crate_id"),
                "the admin owns the file once it exists");
    }

    @Test
    void anAssetTheJarDoesNotHaveIsNamedInTheRefusal() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> ConfigFactory.copyAsset(plugin(), "config-assets/missing.yml", tempDir.resolve("m.yml").toFile()));
        assertTrue(refusal.getMessage().contains("config-assets/missing.yml"), refusal.getMessage());
    }
}
