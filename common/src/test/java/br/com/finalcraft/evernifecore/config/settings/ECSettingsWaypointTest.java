package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code Settings.Waypoint} keys: the shipped defaults, an admin's list, and a channel name nobody has refused at boot. */
@ECoreTest
class ECSettingsWaypointTest {

    @TempDir
    Path dir;

    @AfterEach
    void restoreTheShippedDefaults() {
        ECSettings.loadWaypointSettings(ConfigFactory.open(dir.resolve("defaults.yml")));
    }

    @Test
    void anEmptyFileGetsCompassAndParticlePathEveryHalfSecond() {
        ECSettings.loadWaypointSettings(ConfigFactory.open(dir.resolve("config.yml")));

        assertEquals(Arrays.asList("compass", "particle-path"), ECSettings.WAYPOINT_CHANNELS);
        assertEquals(10, ECSettings.WAYPOINT_REFRESH_TICKS);
        assertEquals(16, ECSettings.WAYPOINT_PARTICLE_PATH_LENGTH);
    }

    @Test
    void anAdminListAndAPluginChannelAreTaken() {
        Config config = ConfigFactory.open(dir.resolve("config.yml"));
        config.setValue("Settings.Waypoint.channels", Arrays.asList("hologram", "finalquests:marker"));

        ECSettings.loadWaypointSettings(config);

        assertEquals(Arrays.asList("hologram", "finalquests:marker"), ECSettings.WAYPOINT_CHANNELS);
    }

    @Test
    void aMisspelledChannelFailsTheBootNamingTheKeyAndTheChannels() {
        Config config = ConfigFactory.open(dir.resolve("config.yml"));
        config.setValue("Settings.Waypoint.channels", Collections.singletonList("compas"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> ECSettings.loadWaypointSettings(config));

        assertTrue(refused.getMessage().contains("Settings.Waypoint.channels"), refused.getMessage());
        assertTrue(refused.getMessage().contains("'compas'") && refused.getMessage().contains("particle-path"), refused.getMessage());
    }
}
