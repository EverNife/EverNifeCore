package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestActorHolder;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The envelope of a stored value: what storage writes, and how a value written before the envelope existed reads. */
@ECoreTest
class FlagStoreEnvelopeTest {

    private static final String OWNER = "EnvelopeOwner";

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private TristateFlag flag;
    private TristateFlag actorFlag;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        flag = new TristateFlag(FlagKey.of(owner, "deny-thing"), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        actorFlag = new TristateFlag(FlagKey.of(owner, "buff-thing"), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE) {
            @Override
            public FlagScope getScope() {
                return FlagScope.ACTOR;
            }
        };
        FlagRegistry.get().register(owner, flag);
        FlagRegistry.get().register(owner, actorFlag);
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(owner);
        Plugins.forget(OWNER);
    }

    private Region region(String name) {
        return TestRegions.create(owner, name, "world");
    }

    @Test
    void oldFormatWithOnlyTheValueReadsAsAddPriorityZeroUnlockedAndOldest() {
        Region region = region("old");
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.set("value", FlagTypes.TRISTATE.toJson(Tristate.TRUE));
        region.getFlagStore().putRaw(flag.getKey().toString(), raw);

        FlagValue<Tristate> read = region.getFlagValue(flag);
        assertEquals(Tristate.TRUE, read.getValue());
        assertEquals(FlagMode.ADD, read.getMode());
        assertEquals(0, read.getPriority());
        assertFalse(read.isLock());
        assertEquals(Instant.EPOCH, read.getSince(), "a value from before the envelope is the oldest of all");
    }

    @Test
    void theEnvelopeRoundTripsThroughTheRawForm() {
        Region written = region("written");
        FlagValue<Tristate> value = FlagValue.permanent(Tristate.TRUE).withMode(FlagMode.SET).withPriority(10).withLock(true)
                .withSince(Instant.parse("2026-10-24T18:41:00Z"));
        written.setFlag(flag, value);
        JsonNode raw = written.getFlagStore().toRaw().get(flag.getKey().toString());
        assertEquals("set", raw.get("mode").asText());
        assertEquals(10, raw.get("priority").asInt());
        assertTrue(raw.get("lock").asBoolean());
        assertEquals("2026-10-24T18:41:00Z", raw.get("since").asText());

        Region read = region("read");
        read.getFlagStore().putRaw(flag.getKey().toString(), raw);
        assertEquals(value, read.getFlagValue(flag));
    }

    @Test
    void theDefaultEnvelopeWritesOnlyTheValueAndSince() {
        Region region = region("plain");
        region.setFlag(flag, Tristate.TRUE);
        JsonNode raw = region.getFlagStore().toRaw().get(flag.getKey().toString());
        List<String> keys = new ArrayList<>();
        raw.fieldNames().forEachRemaining(keys::add);
        assertEquals(Arrays.asList("value", "since"), keys);
    }

    @Test
    void aTemporaryLayerCarriesTheEnvelopeAndKeepsThePermanentValueUnderIt() {
        Region region = region("timed");
        region.setFlag(flag, Tristate.FALSE);
        Instant deadline = Instant.now().plusSeconds(3600);
        region.setFlag(flag, FlagValue.until(Tristate.TRUE, deadline).withMode(FlagMode.SET));
        JsonNode raw = region.getFlagStore().toRaw().get(flag.getKey().toString());
        assertEquals(deadline.toString(), raw.get("expiresAt").asText());
        assertEquals("set", raw.get("mode").asText());
        assertEquals(FlagTypes.TRISTATE.toJson(Tristate.FALSE), raw.get("permanent"));

        Region read = region("timed-read");
        read.getFlagStore().putRaw(flag.getKey().toString(), raw);
        assertEquals(FlagMode.SET, read.getFlagValue(flag).getMode());
        assertEquals(deadline, read.getFlagValue(flag).getExpiresAt());
    }

    @Test
    void aHolderThatFollowsThePlayerRefusesAPlaceFlagAndALock() {
        TestActorHolder party = new TestActorHolder("party-abc");
        IllegalArgumentException place = assertThrows(IllegalArgumentException.class, () -> party.setFlag(flag, Tristate.TRUE));
        assertTrue(place.getMessage().contains("PLACE flag") && place.getMessage().contains("party-abc"), place.getMessage());
        party.setFlag(actorFlag, Tristate.TRUE);
        assertEquals(Tristate.TRUE, party.getFlagValue(actorFlag).getValue(), "an ACTOR flag is welcome");
    }
}
