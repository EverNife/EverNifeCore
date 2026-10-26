package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order every layer takes before the resolver joins them: lock > written priority > contextual > newest; set cuts. */
@ECoreTest
class FlagLayerOrderTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private RegionWorld regions;
    private Flag<String> text;
    private Flag<List<String>> lines;
    private Flag<String> buff;
    private final TestFPlayerSender alice = Senders.player("alice");

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        regions = RegionWorld.open("Layers", tempDir.resolve("layers"));
        ECPluginData owner = regions.getOwner();
        text = new Flag<String>(FlagKey.of(owner, "motto"), FlagTypes.TEXT, Collections.singleton(EventSource.NONE), null) {
        };
        lines = new Flag<List<String>>(FlagKey.of(owner, "lines"), FlagTypes.TEXT_LIST, Collections.singleton(EventSource.NONE), null) {
            @Override
            public FlagResolver<List<String>> getResolver() {
                return FlagResolvers.concat();
            }
        };
        buff = new Flag<String>(FlagKey.of(owner, "buff"), FlagTypes.TEXT, Collections.singleton(EventSource.NONE), null) {
            @Override
            public FlagScope getScope() {
                return FlagScope.ACTOR;
            }
        };
        FlagRegistry.get().register(owner, text);
        FlagRegistry.get().register(owner, lines);
        FlagRegistry.get().register(owner, buff);
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(regions.getOwner());
        regions.close();
        world.close();
    }

    private Region region(String name, int priority, int size) {
        Region region = regions.cuboid(name, WORLD, 0, 0, 0, size, 10, size);
        region.setPriority(priority);
        return region;
    }

    private <T> Resolution<T> at5(Flag<T> flag) {
        return regions.getLookup().explain(flag, WORLD, 5, 5, 5, alice);
    }

    @Test
    void aLockComesBeforeEvenPriority9999() {
        region("crypt", 0, 20).setFlag(text, FlagValue.permanent("crypt").withLock(true));
        region("altar", 50, 10).setFlag(text, FlagValue.permanent("altar").withPriority(9999));
        Resolution<String> resolution = at5(text);
        assertEquals("crypt", resolution.getValue());
        assertEquals(FlagLayer.Use.NOT_COUNTED, resolution.getLayers().get(1).getUse());
    }

    @Test
    void writtenPriority10ComesBeforeAMoreSpecificLayerWithout() {
        Region arena = region("arena", 0, 20);
        arena.getGroup("vip").add(alice.getUniqueId());
        arena.getGroup("vip").setFlag(text, "group");
        region("ring", 30, 10).setFlag(text, "ring");
        arena.setFlag(text, FlagValue.permanent("written").withPriority(10));
        Resolution<String> resolution = at5(text);
        assertEquals("written", resolution.getValue());
        assertEquals(Resolution.Reason.LOCAL_VALUE, resolution.getReason());
        assertEquals("ring", resolution.getLayers().get(1).getValue(), "then the higher region, before the group of the lower one");
    }

    @Test
    void aSetLayerCutsEveryLayerAfterIt() {
        region("inner", 10, 10).setFlag(lines, FlagValue.permanent(Collections.singletonList("say inner")).withMode(FlagMode.SET));
        region("outer", 0, 20).setFlag(lines, Collections.singletonList("say outer"));
        Resolution<List<String>> resolution = at5(lines);
        assertEquals(Collections.singletonList("say inner"), resolution.getValue());
        assertEquals(FlagLayer.Use.CUT_BY_SET, resolution.getLayers().get(1).getUse());

        regions.getLookup().byId(resolution.getLayers().get(0).getRegion().getId())
                .setFlag(lines, Collections.singletonList("say inner"));
        assertEquals(Arrays.asList("say inner", "say outer"), at5(lines).getValue(), "with add, both join");
    }

    @Test
    void twoLocksThatDisagreeKeepTheMostSpecificAndOverruleTheOther() {
        region("cathedral", 0, 20).setFlag(text, FlagValue.permanent("cathedral").withLock(true));
        region("crypt", 10, 10).setFlag(text, FlagValue.permanent("crypt").withLock(true));
        Resolution<String> resolution = at5(text);
        assertEquals("crypt", resolution.getValue());
        assertEquals(FlagLayer.Use.LOCK_OVERRULED, resolution.getLayers().get(1).getUse());
        assertEquals("cathedral", resolution.getLayers().get(1).getValue());
    }

    @Test
    void amongEqualRanksTheNewestComesFirst() {
        region("a", 0, 20).setFlag(text, "older");
        regions.advance(Duration.ofSeconds(1));
        region("b", 0, 20).setFlag(text, "newer");
        assertEquals("newer", at5(text).getValue());
    }

    @Test
    void aLockOnAHolderThatFollowsThePlayerIsRefusedTeachingPriority() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> regions.actorHolder("party", alice.getUniqueId()).setFlag(buff, FlagValue.permanent("strong").withLock(true)));
        assertTrue(refused.getMessage().contains("priority"), refused.getMessage());
    }

    @Test
    void aHolderThatFollowsThePlayerComesAfterThePlaceAndSpeaksAloneOutsideIt() {
        regions.actorHolder("party", alice.getUniqueId()).setFlag(buff, "party");
        Region spa = region("spa", 0, 20);
        spa.setFlag(buff, "spa");
        Resolution<String> inside = at5(buff);
        assertEquals("spa", inside.getValue());
        assertEquals(Resolution.Reason.ACTOR_VALUE, inside.getLayers().get(1).getReason());

        Resolution<String> outside = regions.getLookup().explain(buff, WORLD, 500, 5, 500, alice);
        assertEquals("party", outside.getValue());
        assertEquals(Resolution.Reason.ACTOR_VALUE, outside.getReason());
        assertNull(outside.getRegion());
        assertSame(outside.getLayers().get(0).getHolder(), outside.getSource());
    }
}
