package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A region's own flag values: set, read, remove, and the temporary layer over the permanent one. */
@ECoreTest
class RegionFlagValuesTest {

    private static final String OWNER = "RegionFlagValuesOwner";

    @TempDir
    Path tempDir;

    private ManualClock clock;
    private TristateFlag flag;
    private Region region;

    @BeforeEach
    void setUp() {
        ECPluginData owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        clock = TestRegions.manualClock();
        flag = new TristateFlag(FlagKey.of(owner, "deny-test"), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(owner, flag);
        region = TestRegions.create(owner, "arena", "world");
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().reset();
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
    }

    @Test
    void setReadAndRemove() {
        assertNull(region.getFlag(flag));
        region.setFlag(flag, Tristate.TRUE);
        assertEquals(Tristate.TRUE, region.getFlag(flag));
        assertNull(region.getFlagValue(flag).getExpiresAt());
        assertTrue(region.removeFlag(flag));
        assertNull(region.getFlag(flag));
        assertFalse(region.removeFlag(flag));
    }

    @Test
    void getFlagsIsAReadOnlySnapshot() {
        region.setFlag(flag, Tristate.TRUE);
        Map<FlagKey, FlagValue<?>> flags = region.getFlags();
        assertEquals(Tristate.TRUE, flags.get(flag.getKey()).getValue());
        assertThrows(UnsupportedOperationException.class, () -> flags.remove(flag.getKey()));
        assertThrows(UnsupportedOperationException.class, () -> region.getUnknownFlags().clear());
    }

    @Test
    void expiredTemporaryValueReadsAsUnsetAndThePermanentComesBack() {
        region.setFlag(flag, Tristate.TRUE);
        region.setFlag(flag, Tristate.FALSE, Duration.ofMinutes(15));
        assertEquals(Tristate.FALSE, region.getFlag(flag));
        assertEquals(Duration.ofMinutes(15), region.getFlagValue(flag).getRemaining());

        clock.advance(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(5), region.getFlagValue(flag).getRemaining());

        clock.advance(Duration.ofMinutes(5));
        assertEquals(Tristate.TRUE, region.getFlag(flag), "the permanent value is back once the temporary one expires");
        assertNull(region.getFlagValue(flag).getExpiresAt());
    }

    @Test
    void expiredTemporaryValueWithoutPermanentIsAbsent() {
        region.setFlag(flag, Tristate.TRUE, Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(30));
        assertNull(region.getFlag(flag));
        assertNull(region.getFlagValue(flag));
        assertFalse(region.getFlags().containsKey(flag.getKey()));
    }

    @Test
    void permanentSetDropsTheTemporaryLayer() {
        region.setFlag(flag, Tristate.TRUE, Duration.ofMinutes(1));
        region.setFlag(flag, Tristate.FALSE);
        clock.advance(Duration.ofMinutes(2));
        assertEquals(Tristate.FALSE, region.getFlag(flag));
    }

    @Test
    void refusesUnregisteredFlagAndNonPositiveDuration() {
        TristateFlag stranger = new TristateFlag(FlagKey.of(flag.getOwner(), "not-registered"), Collections.singleton(EventSource.BREAK),
                Tristate.FALSE, Tristate.TRUE);
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> region.setFlag(stranger, Tristate.TRUE));
        assertTrue(refused.getMessage().contains("FlagRegistry.get().register"), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> region.setFlag(flag, Tristate.TRUE, Duration.ZERO));
    }

    @Test
    void aParsedKeyBuildsAFlagOnlyThePluginItNamesRegistersAndATristateNeedsDefaultAndWinner() {
        TristateFlag someones = new TristateFlag(FlagKey.parse("someone:flag"), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        assertFalse(someones.isRegistered());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> FlagRegistry.get().register(flag.getOwner(), someones));
        assertTrue(refused.getMessage().contains("FlagKey.of(\"regionflagvaluesowner\", \"flag\")"), refused.getMessage());
        assertFalse(someones.isRegistered());
        assertThrows(NullPointerException.class, () -> new TristateFlag(FlagKey.of(flag.getOwner(), "x"), Collections.singleton(EventSource.BREAK),
                Tristate.FALSE, null));
        assertThrows(IllegalArgumentException.class, () -> FlagKey.of(flag.getOwner(), "has space"));
        assertEquals("regionflagvaluesowner:deny-test", flag.getKey().toString());
        assertEquals(flag.getKey(), FlagKey.parse("RegionFlagValuesOwner:Deny-Test"));
    }
}
