package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.everyconfig.config.Config;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An optional location - a spawn point nobody set yet - reads as {@code null} instead of taking the
 * plugin's enable down, whether the key is missing or the file holds an empty map for it.
 */
class McLocationCodecTest {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;

    @BeforeEach
    void setup() {
        world = Platforms.lenient().install().withPluginExtractor(
                Plugins.fake("McLocationCodec_" + UNIQUE_SUFFIX.incrementAndGet(), tempDir.toFile()));
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);
        ItemWorld.registerConfigTypes();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private Config open() {
        return ConfigFactory.open(tempDir.resolve("locations.yml"));
    }

    @Test
    void anAbsentLocationReadsAsNull() {
        assertNull(open().getValue("Spawn", Location.class));
    }

    @Test
    void anEmptyMapReadsAsNull() {
        Config config = open();
        config.setValue("Spawn", Collections.emptyMap());
        config.save();

        assertNull(open().getValue("Spawn", Location.class));
    }
}
