package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registering at any time: raw values come alive, unreadable ones stay raw, and leaving puts values back to raw. */
@ECoreTest
class FlagRegistryTest {

    private static final String FLAG_OWNER = "FlagRegistryFlagOwner";
    private static final String REGION_OWNER = "FlagRegistryRegionOwner";

    @TempDir
    Path tempDir;

    private ECPluginData flagOwner;
    private ECPluginData regionOwner;

    @BeforeEach
    void setUp() {
        flagOwner = Plugins.fakePluginData(FLAG_OWNER, tempDir.resolve("flags").toFile());
        regionOwner = Plugins.fakePluginData(REGION_OWNER, tempDir.resolve("regions").toFile());
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().reset();
        TestRegions.unloadAll();
        Plugins.forget(FLAG_OWNER);
        Plugins.forget(REGION_OWNER);
    }

    private TristateFlag newFlag(String name) {
        return new TristateFlag(FlagKey.of(flagOwner, name), EnumSet.of(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
    }

    private static ObjectNode raw(JsonNode value) {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.set("value", value);
        return raw;
    }

    @Test
    void lateRegistrationRelinksTheRawValueOfALoadedRegion() {
        Region region = TestRegions.loaded(regionOwner, "spawn", "world");
        region.getFlagStore().putRaw("flagregistryflagowner:deny-late", raw(JsonNodeFactory.instance.textNode("TRUE")));
        assertTrue(region.getUnknownFlags().containsKey("flagregistryflagowner:deny-late"));

        TristateFlag flag = newFlag("deny-late");
        FlagRegistry.get().register(flag);

        assertEquals(Tristate.TRUE, region.getFlag(flag));
        assertTrue(region.getUnknownFlags().isEmpty());
    }

    @Test
    void unreadableRawValueStaysRawAndIsLoggedOnceForAllRegions() {
        Region first = TestRegions.loaded(regionOwner, "first", "world");
        Region second = TestRegions.loaded(regionOwner, "second", "world");
        JsonNode bad = raw(JsonNodeFactory.instance.textNode("maybe"));
        first.getFlagStore().putRaw("flagregistryflagowner:deny-bad", bad);
        second.getFlagStore().putRaw("flagregistryflagowner:deny-bad", bad);

        TristateFlag flag = newFlag("deny-bad");
        List<String> logged = Logs.capture(() -> FlagRegistry.get().register(flag));

        assertEquals(bad, first.getUnknownFlags().get("flagregistryflagowner:deny-bad"));
        assertEquals(bad, second.getUnknownFlags().get("flagregistryflagowner:deny-bad"));
        assertNull(first.getFlag(flag));
        assertTrue(logged.stream().anyMatch(line -> line.contains("flagregistryflagowner:deny-bad") && line.contains("2 region(s)")
                && line.contains("flagregistryregionowner:first") && line.contains("flagregistryregionowner:second")),
                "one warning names every region whose value did not read: " + logged);
    }

    @Test
    void unregisterAllTurnsValuesBackToRawAndARegisterAgainBringsThemBack() {
        Region region = TestRegions.loaded(regionOwner, "spawn", "world");
        TristateFlag flag = newFlag("deny-leaving");
        FlagRegistry.get().register(flag);
        region.setFlag(flag, Tristate.TRUE);
        region.setFlag(flag, Tristate.FALSE, Duration.ofHours(1));

        FlagRegistry.get().unregisterAll(flagOwner);

        assertNull(FlagRegistry.get().get(flag.getKey()));
        assertTrue(region.getFlags().isEmpty());
        JsonNode kept = region.getUnknownFlags().get("flagregistryflagowner:deny-leaving");
        assertEquals("false", kept.get("value").asText());
        assertEquals("true", kept.get("permanent").asText());
        assertTrue(kept.has("expiresAt"));

        TristateFlag back = newFlag("deny-leaving");
        FlagRegistry.get().register(back);
        assertEquals(Tristate.FALSE, region.getFlag(back));
        assertTrue(region.getUnknownFlags().isEmpty());
    }

    @Test
    void registrationRules() {
        TristateFlag flag = newFlag("deny-once");
        FlagRegistry.get().register(flag);
        FlagRegistry.get().register(flag);
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(newFlag("deny-once")));

        assertSame(flag, FlagRegistry.get().get(FlagKey.parse("FlagRegistryFlagOwner:deny-once")));
        assertSame(flag, FlagRegistry.get().find("deny-once").get());
        assertSame(flag, FlagRegistry.get().find("flagregistryflagowner:deny-once").get());
        assertFalse(FlagRegistry.get().find("nobody:deny-once").isPresent());
        assertTrue(FlagRegistry.get().getBySource(EventSource.DAMAGE).contains(flag));
        assertFalse(FlagRegistry.get().getBySource(EventSource.MOVE).contains(flag));
        assertEquals(1, FlagRegistry.get().getByOwner(flagOwner).size());
        assertTrue(FlagRegistry.get().getByOwner(regionOwner).isEmpty());
    }
}
