package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code Settings.Waypoint.refresh-ticks}, the one waypoint key the core keeps: one clock redraws every waypoint. */
@ECoreTest
class ECSettingsWaypointTest {

    @TempDir
    Path dir;

    @AfterEach
    void restoreTheShippedDefaults() {
        ECSettings.loadWaypointSettings(ConfigFactory.open(dir.resolve("defaults.yml")));
    }

    @Test
    void anEmptyFileRefreshesEveryHalfSecond() {
        ECSettings.loadWaypointSettings(ConfigFactory.open(dir.resolve("config.yml")));

        assertEquals(10, ECSettings.WAYPOINT_REFRESH_TICKS);
    }
}
