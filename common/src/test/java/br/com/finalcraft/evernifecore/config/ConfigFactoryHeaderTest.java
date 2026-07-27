package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explicit-path, header-seeded {@code open} overloads: a file opened at a caller-chosen path
 * (as {@code storage.yml} and the legacy migration metadata are) still carries the same FinalCraft
 * banner every plugin-scoped config gets, while a {@code null} plugin (a headless runtime with no
 * {@link ECPluginData} registered) seeds none.
 */
@ECoreTest
class ConfigFactoryHeaderTest {

    private static final String PLUGIN_NAME = "HeaderTestPlugin";
    private static final String PLUGIN_AUTHOR = "Petrus";
    /** A stable line of the banner {@link ConfigFactory#standardHeader} emits. */
    private static final String BANNER_MARKER = "EverNife's Config Manager";


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        //the ECPluginData cache is static and keyed by name: drop it so a stale one pointing at a
        //@TempDir that no longer exists cannot reach the next test in this JVM
        ECPluginManager.removePluginData(PLUGIN_NAME);
    }

    @Test
    void openWithAPluginSeedsTheStandardBannerAtAnExplicitPath() throws IOException {
        ECPluginData plugin = fakePluginData();
        File file = tempDir.resolve("with-header.yml").toFile();

        Config config = ConfigFactory.open(plugin, file);
        config.setValue("some-key", "some-value");
        config.save();

        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains(BANNER_MARKER), "the banner must be seeded: " + raw);
        assertTrue(raw.contains("Plugin: " + PLUGIN_NAME), "the banner must name the plugin: " + raw);
        assertTrue(raw.contains("Author: " + PLUGIN_AUTHOR), "the banner must name the author: " + raw);
        //the file is written where the caller asked, NOT relocated under the plugin data folder
        assertTrue(file.getParentFile().equals(tempDir.toFile()), "the file must stay at the explicit path");
    }

    @Test
    void openWithANullPluginSeedsNoHeader() throws IOException {
        File file = tempDir.resolve("no-header.yml").toFile();

        Config config = ConfigFactory.open((ECPluginData) null, file);
        config.setValue("some-key", "some-value");
        config.save();

        String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse(raw.contains(BANNER_MARKER),
                "a headless runtime (no ECPluginData) must not seed a banner: " + raw);
    }

    // ------------------------------------------------------------------
    // fixture: a real ECPluginData, built the way production does (through the extractor)
    // ------------------------------------------------------------------

    private ECPluginData fakePluginData() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    /** Stands in for the platform's plugin object (a JavaPlugin on Bukkit); only its identity matters. */
    public static final class FakePlugin {
    }


}
