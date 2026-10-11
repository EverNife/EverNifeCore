package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code Regions:} keys of the core config: their shipped defaults as written, and the keys named for a unit dropped. */
@ECoreTest
class ECSettingsRegionsTest {

    @TempDir
    Path dir;

    private List<String> disabledWorlds;
    private int hugeChunks;
    private Duration idleTime;
    private Duration sweeperInterval;
    private Duration viewTime;
    private Duration selectionIdleTime;
    private boolean generateFiles;

    @BeforeEach
    void remember() {
        disabledWorlds = ECSettings.REGIONS_DISABLED_WORLDS;
        hugeChunks = ECSettings.REGIONS_HUGE_CHUNKS;
        idleTime = ECSettings.REGIONS_LISTENER_IDLE_TIME;
        sweeperInterval = ECSettings.REGIONS_SWEEPER_INTERVAL;
        viewTime = ECSettings.REGIONS_VISUALIZER_VIEW_TIME;
        selectionIdleTime = ECSettings.REGIONS_SELECTION_IDLE_TIME;
        generateFiles = ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES;
    }

    @AfterEach
    void restore() {
        ECSettings.REGIONS_DISABLED_WORLDS = disabledWorlds;
        ECSettings.REGIONS_HUGE_CHUNKS = hugeChunks;
        ECSettings.REGIONS_LISTENER_IDLE_TIME = idleTime;
        ECSettings.REGIONS_SWEEPER_INTERVAL = sweeperInterval;
        ECSettings.REGIONS_VISUALIZER_VIEW_TIME = viewTime;
        ECSettings.REGIONS_SELECTION_IDLE_TIME = selectionIdleTime;
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = generateFiles;
    }

    private Config load(Path file) {
        Config config = ConfigFactory.open(file);
        ECSettings.loadRegionSettings(config);
        config.save();
        return ConfigFactory.open(file);
    }

    @Test
    void anEmptyConfigGetsTheReadableDefaults() throws Exception {
        Path file = dir.resolve("config.yml");
        Config written = load(file);

        assertEquals("3m", written.getString("Regions.listener-idle-time"));
        assertEquals("1s", written.getString("Regions.sweeper-interval"));
        assertEquals("10s", written.getString("Regions.visualizer.view-time"));
        assertEquals("5m", written.getString("Regions.selection.idle-time"));
        assertEquals(Boolean.FALSE, written.getValue("Regions.generate-global-settings-configuration-files"));
        assertEquals(Duration.ofMinutes(3), ECSettings.REGIONS_LISTENER_IDLE_TIME);
        assertEquals(Duration.ofSeconds(1), ECSettings.REGIONS_SWEEPER_INTERVAL);
        assertFalse(ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES);

        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Regions:") || text.contains("\nRegions:"), text);
        assertTrue(text.contains("  listener-idle-time: 3m"), text);
        assertTrue(text.contains("  generate-global-settings-configuration-files: false"), text);
    }

    @Test
    void aTimeKeyNamedForItsUnitIsDroppedUnread() throws Exception {
        Path file = dir.resolve("config.yml");
        StringBuilder before = new StringBuilder("Regions:\n");
        for (String unit : new String[]{"ticks", "seconds", "minutes"}) {
            before.append("  sweeper-interval-").append(unit).append(": 7\n");
        }
        before.append("  visualizer:\n    view-seconds: 30\n");
        Files.write(file, before.toString().getBytes(StandardCharsets.UTF_8));

        Config written = load(file);

        for (String unit : new String[]{"ticks", "seconds", "minutes"}) {
            assertFalse(written.contains("Regions.sweeper-interval-" + unit), unit);
        }
        assertFalse(written.contains("Regions.visualizer.view-seconds"));
        assertEquals(Duration.ofSeconds(1), ECSettings.REGIONS_SWEEPER_INTERVAL, "the dropped value is not carried over");
        assertEquals(Duration.ofSeconds(10), ECSettings.REGIONS_VISUALIZER_VIEW_TIME);
    }

    @Test
    void anEditedTimeReadsInEitherForm() throws Exception {
        Path file = dir.resolve("config.yml");
        Files.write(file, "Regions:\n  sweeper-interval: 1s 500ms\n  listener-idle-time: PT10M\n".getBytes(StandardCharsets.UTF_8));

        load(file);

        assertEquals(Duration.ofMillis(1500), ECSettings.REGIONS_SWEEPER_INTERVAL);
        assertEquals(Duration.ofMinutes(10), ECSettings.REGIONS_LISTENER_IDLE_TIME);
    }
}
