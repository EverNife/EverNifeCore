package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        world.withCorePluginData(ECPluginManager.getOrCreateECorePluginData(new Object()));
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

    private Config written(String yaml) throws Exception {
        Files.write(tempDir.resolve("locations.yml"), yaml.getBytes(StandardCharsets.UTF_8));
        return open();
    }

    @Test
    void aLocationWithoutYFailsNamingY() throws Exception {
        Config config = written("Spawn:\n  worldName: world\n  x: 10\n  z: 3\n");

        Throwable thrown;
        try (ItemWorld ignored = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            thrown = assertThrows(Throwable.class, () -> config.getValue("Spawn", Location.class));
        }

        assertFalse(thrown instanceof NullPointerException, "a missing coordinate reads as a bare NullPointerException");
        assertTrue(messagesOf(thrown).contains("'y'"), messagesOf(thrown));
    }

    @Test
    void aLocationWithoutYawAndPitchFacesZero() throws Exception {
        try (ItemWorld ignored = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            Location spawn = written("Spawn:\n  worldName: world\n  x: 10\n  y: 64\n  z: 3\n").getValue("Spawn", Location.class);

            assertEquals(64, spawn.getY());
            assertEquals(0f, spawn.getYaw());
            assertEquals(0f, spawn.getPitch());
        }
    }

    private static String messagesOf(Throwable thrown) {
        StringBuilder all = new StringBuilder();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            all.append(cause).append(" | ");
        }
        return all.toString();
    }

    @Test
    void anEmptyMapReadsAsNull() {
        Config config = open();
        config.setValue("Spawn", Collections.emptyMap());
        config.save();

        assertNull(open().getValue("Spawn", Location.class));
    }
}
