package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import br.com.finalcraft.everyconfig.config.Config;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's per-context blocks bound as sections of a plugin's own config.yml, the way a plugin's settings bean holds them. */
@ECoreTest
class PluginSettingsBindingTest {

    /** A plugin's settings bean: each block is a field under its standard section name. */
    public static class PluginSettings {
        @Key(NotifySettings.SECTION)
        @JsonProperty
        NotifySettings notify = new NotifySettings();
        @Key(WaypointSettings.SECTION)
        @JsonProperty
        WaypointSettings waypoints = new WaypointSettings();
        @Key(RegionSettings.SECTION)
        @JsonProperty
        RegionSettings regions = new RegionSettings();
        @Key(ProvenanceSettings.SECTION)
        @JsonProperty
        ProvenanceSettings provenance = new ProvenanceSettings();
        @Key(CooldownSettings.SECTION)
        @JsonProperty
        CooldownSettings cooldowns = new CooldownSettings();
        @Key(BoosterSettings.SECTION)
        @JsonProperty
        BoosterSettings boosters = new BoosterSettings().addDomain("woodcutter", 2, 4, 6);
    }

    /** A plugin that wants one key more extends the core's block. */
    public static class ClaimRegionSettings extends RegionSettings {
        @Key("foreign-access")
        @Comment("Whether a player may open containers in a claim that is not theirs.")
        @JsonProperty
        private boolean foreignAccess = false;

        @JsonIgnore
        public boolean isForeignAccess() {
            return foreignAccess;
        }
    }

    public static class ClaimSettings {
        @Key(RegionSettings.SECTION)
        @JsonProperty
        ClaimRegionSettings regions = new ClaimRegionSettings();
    }

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.yml");
    }

    private String text() throws Exception {
        return new String(Files.readAllBytes(file()), StandardCharsets.UTF_8);
    }

    private void write(String yaml) throws Exception {
        Files.write(file(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T bind(T defaults) {
        Config config = ConfigFactory.open(file());
        T bound = config.getOrMergeValue("", defaults);
        config.save();
        return bound;
    }

    @Test
    void anAbsentSectionIsSeededWithTheDefaultsAndTheirComments() throws Exception {
        PluginSettings settings = bind(new PluginSettings());

        String text = text();
        for (String expected : new String[]{
                "notify:", "chains:", "progress:", "- title+sound", "durations:", "complete: 5s",
                "waypoints:", "- particle-path", "particle-path-length: 16",
                "regions:", "disabled-worlds: []", "flag-settings-files: false", "view-time: 10s", "live-preview: false", "idle-time: 5m",
                "provenance:", "player-placed: 30d", "record-from-boot: false",
                "cooldowns:", "retention-days:", "retention-overrides:",
                "boosters:", "woodcutter:", "permission-factors:",
                "# How long an outline asked for by a 'view' command stays on screen.",
                "# Each booster domain of this plugin, by name. permission-factors: the multipliers a permission grants"}) {
            assertTrue(text.contains(expected), "expected '" + expected + "' in:\n" + text);
        }
        assertEquals(Duration.ofSeconds(10), settings.regions.getViewTime());
        assertEquals(Arrays.asList(2.0, 4.0, 6.0), settings.boosters.getDomain("woodcutter").getPermissionFactors());
        assertNull(settings.cooldowns.getRetentionDays());
    }

    @Test
    void aValueInTheFileWinsOverTheDefault() throws Exception {
        write("regions:\n"
                + "  disabled-worlds: [arena]\n"
                + "  view-time: 30s\n"
                + "  selection:\n"
                + "    idle-time: 1m\n"
                + "waypoints:\n"
                + "  particle-path-length: 8\n"
                + "cooldowns:\n"
                + "  retention-days: 7\n"
                + "boosters:\n"
                + "  domains:\n"
                + "    woodcutter:\n"
                + "      permission-factors: [3.0]\n");

        PluginSettings settings = bind(new PluginSettings());

        assertEquals(Collections.singletonList("arena"), settings.regions.getDisabledWorlds());
        assertEquals(Duration.ofSeconds(30), settings.regions.getViewTime());
        assertEquals(Duration.ofMinutes(1), settings.regions.getSelection().getIdleTime());
        assertFalse(settings.regions.getSelection().isLivePreview());
        assertEquals(8, settings.waypoints.getParticlePathLength());
        assertEquals(Integer.valueOf(7), settings.cooldowns.getRetentionDays());
        assertEquals(Collections.singletonList(3.0), settings.boosters.getDomain("woodcutter").getPermissionFactors());
        assertEquals(Duration.ofDays(30), settings.provenance.getRetention().getPlayerPlaced());
        String text = text();
        assertTrue(text.contains("view-time: 30s"), text);
        assertTrue(text.contains("record-from-boot: false"), text);
    }

    @Test
    void aSubclassWithANewKeyReadsAndSeedsBothLevels() throws Exception {
        ClaimSettings seeded = bind(new ClaimSettings());
        String text = text();
        assertTrue(text.contains("foreign-access: false"), text);
        assertTrue(text.contains("# Whether a player may open containers"), text);
        assertTrue(text.contains("view-time: 10s"), text);
        assertTrue(text.contains("disabled-worlds: []"), text);
        assertFalse(seeded.regions.isForeignAccess());

        write(text.replace("foreign-access: false", "foreign-access: true").replace("view-time: 10s", "view-time: 20s"));
        ClaimSettings read = bind(new ClaimSettings());

        assertTrue(read.regions.isForeignAccess());
        assertEquals(Duration.ofSeconds(20), read.regions.getViewTime());
    }
}
