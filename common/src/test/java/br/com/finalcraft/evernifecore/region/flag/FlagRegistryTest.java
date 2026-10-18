package br.com.finalcraft.evernifecore.region.flag;

import java.util.Set;
import java.util.EnumSet;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
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
import java.util.Collections;
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
        return new TristateFlag(FlagKey.of(flagOwner, name), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
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
        FlagRegistry.get().register(flagOwner, flag);

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
        List<String> logged = Logs.capture(() -> FlagRegistry.get().register(flagOwner, flag));

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
        FlagRegistry.get().register(flagOwner, flag);
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
        FlagRegistry.get().register(flagOwner, back);
        assertEquals(Tristate.FALSE, region.getFlag(back));
        assertTrue(region.getUnknownFlags().isEmpty());
    }

    @Test
    void registrationRules() {
        TristateFlag flag = newFlag("deny-once");
        FlagRegistry.get().register(flagOwner, flag);
        FlagRegistry.get().register(flagOwner, flag);
        assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(flagOwner, newFlag("deny-once")));

        assertSame(flag, FlagRegistry.get().get(FlagKey.parse("FlagRegistryFlagOwner:deny-once")));
        assertSame(flag, FlagRegistry.get().find("deny-once").get());
        assertSame(flag, FlagRegistry.get().find("flagregistryflagowner:deny-once").get());
        assertFalse(FlagRegistry.get().find("nobody:deny-once").isPresent());
        assertTrue(FlagRegistry.get().getBySource(EventSource.DAMAGE).contains(flag));
        assertFalse(FlagRegistry.get().getBySource(EventSource.MOVE).contains(flag));
        assertEquals(1, FlagRegistry.get().getByOwner(flagOwner).size());
        assertTrue(FlagRegistry.get().getByOwner(regionOwner).isEmpty());
    }

    @Test
    void aFlagRegisteredByAnotherPluginThanItsKeyNamesIsRefused() {
        TristateFlag flag = new TristateFlag(FlagKey.of(regionOwner, "deny-owned"), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(flagOwner, flag));

        assertTrue(refused.getMessage().contains("FlagKey.of(\"flagregistryflagowner\""), refused.getMessage());
        assertFalse(flag.isRegistered());
        assertNull(FlagRegistry.get().get(flag.getKey()));
    }

    @Test
    void unregisteringOneFlagPutsItsValuesBackToRaw() {
        Region region = TestRegions.loaded(regionOwner, "spawn", "world");
        TristateFlag leaving = newFlag("deny-leaving");
        TristateFlag staying = newFlag("deny-staying");
        FlagRegistry.get().register(flagOwner, leaving);
        FlagRegistry.get().register(flagOwner, staying);
        region.setFlag(leaving, Tristate.TRUE);
        region.setFlag(staying, Tristate.TRUE);

        assertTrue(FlagRegistry.get().unregister(leaving));

        assertFalse(leaving.isRegistered());
        assertNull(FlagRegistry.get().get(leaving.getKey()));
        assertSame(staying, FlagRegistry.get().get(staying.getKey()));
        assertEquals(Tristate.TRUE, region.getFlag(staying));
        assertFalse(region.getUnknownFlags().isEmpty());
        assertEquals("true", region.getUnknownFlags().get("flagregistryflagowner:deny-leaving").get("value").asText());
        assertFalse(FlagRegistry.get().unregister(leaving), "an instance that is not registered is not unregistered again");
    }

    @Test
    void aFlagKnowsWhetherItIsRegisteredAndWhoOwnsIt() {
        TristateFlag flag = newFlag("deny-known");
        assertFalse(flag.isRegistered());
        IllegalStateException ownerless = assertThrows(IllegalStateException.class, flag::getOwner);
        assertTrue(ownerless.getMessage().contains("register(owner, flag)"), ownerless.getMessage());

        FlagRegistry.get().register(flagOwner, flag);
        assertTrue(flag.isRegistered());
        assertSame(flagOwner, flag.getOwner());

        FlagRegistry.get().unregisterAll(flagOwner);
        assertFalse(flag.isRegistered());
        assertThrows(IllegalStateException.class, flag::getOwner);

        FlagRegistry.get().register(flagOwner, flag);
        FlagRegistry.get().reset();
        assertFalse(flag.isRegistered(), "emptying the registry unbinds what it held");
    }

    @Test
    void theSameFlagRegisteredAgainUnderAnotherOwnerIsRefused() {
        TristateFlag flag = newFlag("deny-twice");
        FlagRegistry.get().register(flagOwner, flag);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(regionOwner, flag));

        assertTrue(refused.getMessage().contains("is registered by FlagRegistryFlagOwner: unregister it there"), refused.getMessage());
        assertSame(flagOwner, flag.getOwner());
    }

    @Test
    void aFlagNeedingACapabilityThePlatformLacksIsReportedOnceAndOnlyThat() {
        TristateFlag feeds = new TristateFlag(FlagKey.of(flagOwner, "feeds"), Collections.singleton(EventSource.MOVE), Tristate.FALSE, Tristate.TRUE) {
            @Override
            public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
                return EnumSet.of(IPlayerStateAccess.Capability.FOOD, IPlayerStateAccess.Capability.HEALTH);
            }
        };
        TristateFlag plain = newFlag("plain");
        FlagRegistry.get().register(flagOwner, feeds);
        FlagRegistry.get().register(flagOwner, plain);
        assertNull(FlagRegistry.get().describeWithoutEffect(feeds), "every capability is there until a platform says otherwise");
        try {
            List<String> logged = Logs.capture(() -> FlagRegistry.get().setMissingCapabilities(EnumSet.of(IPlayerStateAccess.Capability.FOOD,
                    IPlayerStateAccess.Capability.WEATHER)));

            String line = FlagRegistry.get().describeWithoutEffect(feeds);
            assertEquals("Flag flagregistryflagowner:feeds has no effect on this platform: it changes [FOOD] on a player, which this"
                    + " server cannot. A region may hold it; nothing will apply it here.", line);
            assertNull(FlagRegistry.get().describeWithoutEffect(plain));
            assertTrue(logged.stream().anyMatch(logLine -> logLine.contains("flagregistryflagowner:feeds has no effect")), logged.toString());
            assertTrue(logged.stream().noneMatch(logLine -> logLine.contains("flagregistryflagowner:plain")), logged.toString());
        } finally {
            FlagRegistry.get().setMissingCapabilities(Collections.emptySet());
        }
        assertNull(FlagRegistry.get().describeWithoutEffect(feeds));
    }
}
