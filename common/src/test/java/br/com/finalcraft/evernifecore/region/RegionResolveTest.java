package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Resolution.Step;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryMessage;
import br.com.finalcraft.evernifecore.region.flag.stock.StockFlags;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldShape;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effective value of a flag at a point for a subject: bands, parents, groups, ties, bypass and default. */
@ECoreTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegionResolveTest {

    private static final String OWNER = "RegionResolveOwner";
    private static final String CORE = "EverNifeCore";
    private static final String WORLD = "world";

    /** Counts the ties it is asked to break. */
    private static final class CountingFlag extends TristateFlag {
        int ties;

        CountingFlag(ECPluginData owner, String name, boolean bypassable) {
            super(FlagKey.of(owner, name), EnumSet.of(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
            this.bypassable = bypassable;
        }

        private final boolean bypassable;

        @Override
        public boolean isBypassable() {
            return bypassable;
        }

        @Override
        public Tristate resolveTie(List<Tristate> values) {
            ties++;
            return super.resolveTie(values);
        }
    }

    /** A player standing somewhere: the engine's player has no location. */
    private static final class LocatedPlayer extends TestFPlayerSender {
        private final FLocation location;

        LocatedPlayer(String name, double x, double y, double z) {
            super(name);
            this.location = new FLocation(WorldLocPos.of(x, y, z, WORLD), null);
        }

        @Override
        public FLocation getLocation() {
            return location;
        }
    }

    private ECPluginData owner;
    private ECPluginData core;
    private RegionGridOptions previousGrid;
    private RegionIndex index;
    private RegionLookup lookup;
    private CountingFlag flag;
    private CountingFlag strict;
    private final List<UUID> players = new ArrayList<>();
    private int stockIterated;

    @BeforeAll
    void registerStock() throws Exception {
        core = Plugins.fakePluginData(CORE, Files.createTempDirectory("resolve-stock").toFile());
        StockFlags.registerAll(core);
    }

    @AfterAll
    void unregisterStock() {
        try {
            assertTrue(stockIterated >= 18, "the stock tie test must have run over the whole catalog, ran " + stockIterated);
        } finally {
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(CORE);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        previousGrid = RegionGridOptions.getCurrent();
        RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT_LEGACY);
        owner = Plugins.fakePluginData(OWNER, Files.createTempDirectory("resolve").toFile());
        index = new RegionIndex();
        lookup = new RegionLookup(index);
        flag = new CountingFlag(owner, "deny-thing", true);
        strict = new CountingFlag(owner, "deny-strict", false);
        FlagRegistry.get().register(owner, flag);
        FlagRegistry.get().register(owner, strict);
    }

    @AfterEach
    void tearDown() {
        for (UUID player : players) {
            RegionLookup.get().forgetSession(player);
        }
        players.clear();
        FlagRegistry.get().unregisterAll(owner);
        Plugins.forget(OWNER);
        RegionGridOptions.setCurrent(previousGrid);
    }

    private TestFPlayerSender player(String name) {
        TestFPlayerSender player = Senders.player(name);
        players.add(player.getUniqueId());
        return player;
    }

    private static CuboidShape box(int max) {
        return CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(max, max, max));
    }

    private Region region(String name, int priority, Shape shape, @Nullable Region parent) {
        return region(owner, name, priority, shape, parent);
    }

    private Region region(ECPluginData by, String name, int priority, Shape shape, @Nullable Region parent) {
        Region region = TestRegions.create(by, name, WORLD, shape);
        region.setPriority(priority);
        region.setParent(parent);
        index.add(region);
        return region;
    }

    /** resolve and explain at (5, 5, 5) agree with {@code expected}, and explain shows its work. */
    private <T> void assertResolves(@Nullable T expected, RegionFlag<T> which, @Nullable FPlayer subject) {
        assertEquals(expected, lookup.resolve(which, WORLD, 5, 5, 5, subject));
        Resolution<T> resolution = lookup.explain(which, WORLD, 5, 5, 5, subject);
        assertEquals(expected, resolution.getValue(), "explain must agree with resolve: " + resolution);
        if (!lookup.at(WORLD, 5, 5, 5).isEmpty()) {
            assertFalse(resolution.getSteps().isEmpty(), "explain must show its steps");
        }
    }

    @Test
    void higherBandWinsEvenWithTheLosingValue() {
        region("high", 10, box(20), null).setFlag(flag, Tristate.FALSE);
        region("low", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        assertResolves(Tristate.FALSE, flag, null);
        assertEquals(0, flag.ties);
    }

    @Test
    void distinctValuesInOneBandGoToTheFlagsTie() {
        region("a", 0, box(20), null).setFlag(flag, Tristate.FALSE);
        region("b", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        assertEquals(Tristate.TRUE, lookup.resolve(flag, WORLD, 5, 5, 5, null));
        assertEquals(1, flag.ties);
        Resolution<Tristate> resolution = lookup.explain(flag, WORLD, 5, 5, 5, null);
        assertEquals(Tristate.TRUE, resolution.getValue());
        assertEquals("b", resolution.getSource().getId().getName());
        assertTrue(resolution.getSteps().stream().anyMatch(step -> step.getReason() == Step.Reason.TIE));
    }

    @Test
    void equalValuesInOneBandDoNotAskForATie() {
        region("a", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        region("b", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        assertResolves(Tristate.TRUE, flag, null);
        assertEquals(0, flag.ties);
    }

    @Test
    void parentDoesNotCompeteInItsOwnBand() {
        Region parent = region("parent", 100, box(100), null);
        parent.setFlag(flag, Tristate.TRUE);
        region("child", 0, box(10), parent);
        region("neighbour", 50, box(20), null).setFlag(flag, Tristate.FALSE);
        assertResolves(Tristate.FALSE, flag, null);
        Resolution<Tristate> resolution = lookup.explain(flag, WORLD, 5, 5, 5, null);
        assertTrue(resolution.getSteps().stream().anyMatch(step -> step.getReason() == Step.Reason.SKIPPED_ANCESTOR
                && step.getRegion() == parent), resolution.toString());
    }

    @Test
    void childWithoutValueInheritsFromItsParent() {
        Region parent = region("parent", 100, box(100), null);
        parent.setFlag(flag, Tristate.TRUE);
        Region child = region("child", 0, box(10), parent);
        assertResolves(Tristate.TRUE, flag, null);
        assertSame(parent, lookup.explain(flag, WORLD, 5, 5, 5, null).getSource());
        assertEquals(Tristate.TRUE, lookup.resolve(flag, child, null));
    }

    @Test
    void worldRegionCompetesWithItsOwnPriority() {
        Region wilderness = region("wilderness", 5, WorldShape.INSTANCE, null);
        wilderness.setFlag(flag, Tristate.TRUE);
        Region spawn = region("spawn", 0, box(20), null);
        spawn.setFlag(flag, Tristate.FALSE);
        assertResolves(Tristate.TRUE, flag, null);
        wilderness.setPriority(-1);
        assertResolves(Tristate.FALSE, flag, null);
    }

    @Test
    void livePriorityCountsWithoutRepublishing() {
        Region a = region("a", 10, box(20), null);
        a.setFlag(flag, Tristate.FALSE);
        Region b = region("b", 0, box(20), null);
        b.setFlag(flag, Tristate.TRUE);
        assertResolves(Tristate.FALSE, flag, null);
        b.setPriority(20);
        assertResolves(Tristate.TRUE, flag, null);
    }

    @Test
    void nothingDefinedGivesTheDefault() {
        region("empty", 0, box(20), null);
        assertResolves(Tristate.FALSE, flag, null);
        assertEquals(Tristate.UNDEFINED, lookup.tristate(new TristateFlag(FlagKey.of(owner, "no-default"), EnumSet.of(EventSource.DAMAGE),
                Tristate.UNDEFINED, Tristate.TRUE), WORLD, 5, 5, 5, null));
        assertEquals(Tristate.FALSE, lookup.tristate(flag, WORLD, 500, 5, 500, null));
        assertFalse(lookup.isTrue(flag, WORLD, 5, 5, 5, null));
    }

    @Test
    void bypassGivesTheDefaultUnlessTheFlagRefusesIt() {
        region("arena", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        lookup.byId(RegionId.of(OWNER, "arena")).setFlag(strict, Tristate.TRUE);
        TestFPlayerSender all = player("all").grant("evernifecore.region.bypass.*");
        TestFPlayerSender one = player("one").grant("evernifecore.region.bypass.regionresolveowner.deny-thing");
        TestFPlayerSender none = player("none");

        assertResolves(Tristate.FALSE, flag, all);
        assertResolves(Tristate.FALSE, flag, one);
        assertResolves(Tristate.TRUE, flag, none);
        assertResolves(Tristate.TRUE, strict, all);
        assertTrue(lookup.isTrue(flag, WORLD, 5, 5, 5, null), "no subject, no bypass");
        assertEquals(Step.Reason.BYPASS, lookup.explain(flag, WORLD, 5, 5, 5, all).getSteps().get(0).getReason());
        assertEquals(Tristate.FALSE, lookup.resolve(flag, lookup.byId(RegionId.of(OWNER, "arena")), all));
    }

    @Test
    void groupOverridesTheRegionOnlyForItsMembers() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender fighter = player("fighter");
        TestFPlayerSender spectator = player("spectator");
        AccessGroup fighters = arena.getGroup("fighters");
        fighters.addMember(fighter.getUniqueId());
        fighters.setFlag(flag, Tristate.FALSE);

        assertResolves(Tristate.FALSE, flag, fighter);
        assertResolves(Tristate.TRUE, flag, spectator);
        assertResolves(Tristate.TRUE, flag, null);
        Step groupStep = lookup.explain(flag, WORLD, 5, 5, 5, fighter).getSteps().get(0);
        assertEquals(Step.Reason.GROUP_VALUE, groupStep.getReason());
        assertSame(fighters, groupStep.getGroup());
    }

    @Test
    void highestGroupWinsAndEqualGroupsTie() {
        Region arena = region("arena", 0, box(20), null);
        TestFPlayerSender alice = player("alice");
        AccessGroup low = arena.getGroup("low");
        AccessGroup high = arena.getGroup("high");
        low.addMember(alice.getUniqueId());
        high.addMember(alice.getUniqueId());
        low.setFlag(flag, Tristate.TRUE);
        high.setFlag(flag, Tristate.FALSE);
        high.setPriority(5);
        assertResolves(Tristate.FALSE, flag, alice);
        assertEquals(0, flag.ties);

        high.setPriority(0);
        assertResolves(Tristate.TRUE, flag, alice);
        assertTrue(flag.ties > 0);
    }

    @Test
    void groupThatDoesNotDefineTheFlagFallsBackToTheRegion() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender alice = player("alice");
        arena.getGroup("fighters").addMember(alice.getUniqueId());
        assertResolves(Tristate.TRUE, flag, alice);
    }

    @Test
    void resolvesAtThePlayersLocation() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        LocatedPlayer inside = new LocatedPlayer("inside", 5.5, 5, 5.5);
        LocatedPlayer outside = new LocatedPlayer("outside", 500, 5, 500);
        players.add(inside.getUniqueId());
        players.add(outside.getUniqueId());
        assertEquals(Arrays.asList(arena), lookup.at(inside));
        assertEquals(Arrays.asList(arena), lookup.at(inside.getLocation()));
        assertTrue(lookup.at(outside).isEmpty());
        assertTrue(lookup.at(player("nowhere")).isEmpty());
        assertEquals(Tristate.TRUE, lookup.resolve(flag, inside));
        assertEquals(Tristate.FALSE, lookup.resolve(flag, outside));
    }

    Stream<RegionFlag<?>> stockFlags() {
        return FlagRegistry.get().getByOwner(core).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stockFlags")
    @SuppressWarnings("unchecked")
    void stockFlagTieResolvesThroughTheLookup(RegionFlag<?> stock) {
        stockIterated++;
        Region a = region(core, "a-" + stock.getKey().getName(), 0, box(20), null);
        Region b = region(core, "b-" + stock.getKey().getName(), 0, box(20), null);
        Class<?> valueType = stock.getType().getValueType();
        Object first;
        Object second;
        Object expected;
        if (valueType == Tristate.class) {
            first = Tristate.FALSE;
            second = Tristate.TRUE;
            expected = Tristate.TRUE;
        } else if (valueType == DenyEntryMessage.class) {
            first = new DenyEntryMessage(Tristate.FALSE, "welcome");
            second = new DenyEntryMessage(Tristate.TRUE, "stay out");
            expected = second;
        } else if (valueType == WorldLocPos.class) {
            first = WorldLocPos.of(1, 2, 3, WORLD);
            second = WorldLocPos.of(4, 5, 6, WORLD);
            expected = first;
        } else {
            first = "first";
            second = "second";
            expected = first;
        }
        RegionFlag<Object> typed = (RegionFlag<Object>) stock;
        a.setFlag(typed, first);
        b.setFlag(typed, second);
        assertEquals(expected, lookup.resolve(typed, WORLD, 5, 5, 5, null), stock.getKey().toString());
    }
}
