package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagLayer;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.Resolution;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.flag.core.DenyEntryMessage;
import br.com.finalcraft.evernifecore.flag.core.DenySettings;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.group.Group;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effective value of a flag at a point for a subject, joined over layers: bands, parents, groups, bypass and default. */
@ECoreTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegionResolveTest {

    private static final String OWNER = "RegionResolveOwner";
    private static final String CORE = "EverNifeCore";
    private static final String WORLD = "world";

    private static final class DenyThing extends TristateFlag {
        private final boolean bypassable;

        DenyThing(ECPluginData owner, String name, boolean bypassable) {
            super(FlagKey.of(owner, name), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
            this.bypassable = bypassable;
        }

        @Override
        public boolean isBypassable() {
            return bypassable;
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
    private DenyThing flag;
    private DenyThing strict;
    private final List<UUID> players = new ArrayList<>();
    private int stockIterated;

    @BeforeAll
    void registerStock() throws Exception {
        core = Plugins.fakePluginData(CORE, Files.createTempDirectory("resolve-stock").toFile());
        CoreFlags.registerAll(core);
    }

    @AfterAll
    void unregisterStock() {
        try {
            assertTrue(stockIterated >= 18, "the stock test must have run over the whole catalog, ran " + stockIterated);
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
        flag = new DenyThing(owner, "deny-thing", true);
        strict = new DenyThing(owner, "deny-strict", false);
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

    /** resolve and explain at (5, 5, 5) agree with {@code expected}, and explain shows its layers. */
    private <T> Resolution<T> assertResolves(@Nullable T expected, Flag<T> which, @Nullable FPlayer subject) {
        assertEquals(expected, lookup.resolve(which, WORLD, 5, 5, 5, subject));
        Resolution<T> resolution = lookup.explain(which, WORLD, 5, 5, 5, subject);
        assertEquals(expected, resolution.getValue(), "explain must agree with resolve: " + resolution);
        if (resolution.getReason() != Resolution.Reason.DEFAULT && resolution.getReason() != Resolution.Reason.BYPASS) {
            assertFalse(resolution.getLayers().isEmpty(), "explain must show its layers");
        }
        return resolution;
    }

    @Test
    void higherBandWinsEvenWithTheLosingValue() {
        region("high", 10, box(20), null).setFlag(flag, Tristate.FALSE);
        region("low", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        Resolution<Tristate> resolution = assertResolves(Tristate.FALSE, flag, null);
        assertEquals(FlagLayer.Use.NOT_COUNTED, resolution.getLayers().get(1).getUse(), "the lower band is seen, not counted");
    }

    @Test
    void equallySpecificRegionsPreferTheFlagsWinner() {
        region("a", 0, box(20), null).setFlag(flag, Tristate.FALSE);
        region("b", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        Resolution<Tristate> resolution = assertResolves(Tristate.TRUE, flag, null);
        assertEquals("b", ((Region) resolution.getSource()).getId().getName());
        assertEquals("prefer-among-equals", resolution.getResolver().getId());
        assertEquals(1, resolution.getCountedLayers().size());
        assertEquals(2, resolution.getLayers().size());
    }

    @Test
    void equalValuesInOneBandResolveToThatValue() {
        region("a", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        region("b", 0, box(20), null).setFlag(flag, Tristate.TRUE);
        assertResolves(Tristate.TRUE, flag, null);
    }

    @Test
    void parentDoesNotCompeteBesideItsChild() {
        Region parent = region("parent", 100, box(100), null);
        parent.setFlag(flag, Tristate.TRUE);
        Region child = region("child", 0, box(10), parent);
        child.setFlag(flag, Tristate.FALSE);
        region("neighbour", 50, box(20), null).setFlag(flag, Tristate.FALSE);
        Resolution<Tristate> resolution = assertResolves(Tristate.FALSE, flag, null);
        assertTrue(resolution.getLayers().stream().noneMatch(layer -> layer.getHolder() == parent), resolution.toString());
    }

    @Test
    void childWithoutValueInheritsFromItsParent() {
        Region parent = region("parent", 100, box(100), null);
        parent.setFlag(flag, Tristate.TRUE);
        Region child = region("child", 0, box(10), parent);
        Resolution<Tristate> resolution = assertResolves(Tristate.TRUE, flag, null);
        assertSame(parent, resolution.getSource());
        assertSame(parent, resolution.getRegion());
        assertEquals(Resolution.Reason.INHERITED, resolution.getReason());
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
        Resolution<Tristate> resolution = assertResolves(Tristate.FALSE, flag, null);
        assertEquals(Resolution.Reason.DEFAULT, resolution.getReason());
        assertTrue(resolution.getLayers().isEmpty());
        assertEquals(Tristate.UNDEFINED, lookup.tristate(new TristateFlag(FlagKey.of(owner, "no-default"), Collections.singleton(EventSource.DAMAGE),
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
        Resolution<Tristate> bypassed = lookup.explain(flag, WORLD, 5, 5, 5, all);
        assertEquals(Resolution.Reason.BYPASS, bypassed.getReason());
        assertTrue(bypassed.getLayers().isEmpty());
        assertEquals(Tristate.FALSE, lookup.resolve(flag, lookup.byId(RegionId.of(OWNER, "arena")), all));
    }

    @Test
    void theExtraBypassNodeOfTheSettingsLiftsABypassableFlagOnly() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        arena.setFlag(CoreFlags.DENY_PVP, Tristate.TRUE);
        FlagSettings extra = new FlagSettings();
        extra.setBypassPermission("server.staff");
        arena.setFlagSettings(flag, extra);
        DenySettings pvpExtra = new DenySettings();
        pvpExtra.setBypassPermission("server.staff");
        arena.setFlagSettings(CoreFlags.DENY_PVP, pvpExtra);
        TestFPlayerSender staff = player("staff").grant("server.staff");
        TestFPlayerSender standard = player("standard").grant("evernifecore.region.bypass.regionresolveowner.deny-thing");
        TestFPlayerSender none = player("none");

        assertResolves(Tristate.FALSE, flag, staff);
        assertEquals(Resolution.Reason.BYPASS, lookup.explain(flag, arena, staff).getReason());
        assertResolves(Tristate.TRUE, flag, none);
        assertResolves(Tristate.FALSE, flag, standard);
        assertResolves(Tristate.TRUE, CoreFlags.DENY_PVP, staff);
        assertEquals(Tristate.TRUE, lookup.resolve(CoreFlags.DENY_PVP, arena, staff), "deny-pvp holds for everyone");
    }

    @Test
    void groupOverridesTheRegionOnlyForItsMembers() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender fighter = player("fighter");
        TestFPlayerSender spectator = player("spectator");
        Group fighters = arena.getGroup("fighters");
        fighters.add(fighter.getUniqueId());
        fighters.setFlag(flag, Tristate.FALSE);

        Resolution<Tristate> resolution = assertResolves(Tristate.FALSE, flag, fighter);
        assertResolves(Tristate.TRUE, flag, spectator);
        assertResolves(Tristate.TRUE, flag, null);
        FlagLayer<Tristate> first = resolution.getLayers().get(0);
        assertEquals(Resolution.Reason.GROUP_VALUE, first.getReason());
        assertSame(fighters, first.getHolder());
        assertSame(arena, first.getRegion());
        assertEquals(FlagLayer.Use.NOT_COUNTED, resolution.getLayers().get(1).getUse(), "the region's own value is seen under the group's");
    }

    @Test
    void highestGroupComesFirstAndEqualGroupsPreferTheWinner() {
        Region arena = region("arena", 0, box(20), null);
        TestFPlayerSender alice = player("alice");
        Group low = arena.getGroup("low");
        Group high = arena.getGroup("high");
        low.add(alice.getUniqueId());
        high.add(alice.getUniqueId());
        low.setFlag(flag, Tristate.TRUE);
        high.setFlag(flag, Tristate.FALSE);
        high.setPriority(5);
        assertResolves(Tristate.FALSE, flag, alice);

        high.setPriority(0);
        assertResolves(Tristate.TRUE, flag, alice);
    }

    @Test
    void groupThatDoesNotDefineTheFlagFallsBackToTheRegion() {
        Region arena = region("arena", 0, box(20), null);
        arena.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender alice = player("alice");
        arena.getGroup("fighters").add(alice.getUniqueId());
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

    @Test
    void aJoiningFlagCountsEveryBand() {
        region("high", 10, box(20), null).setFlag(CoreFlags.HEAL, Collections.singletonList(AmountEvery.of(2, Duration.ofSeconds(1))));
        region("low", 0, box(20), null).setFlag(CoreFlags.HEAL, Collections.singletonList(AmountEvery.of(10, Duration.ofSeconds(1))));
        Resolution<List<AmountEvery>> resolution = assertResolves(Collections.singletonList(AmountEvery.of(12, Duration.ofSeconds(1))),
                CoreFlags.HEAL, null);
        assertEquals(2, resolution.getCountedLayers().size());
        assertEquals("sum-by-interval", resolution.getResolver().getId());
    }

    /** Sets {@code value} as if written {@code second} seconds after the epoch. */
    private static <T> void put(Region region, Flag<T> flag, T value, long second) {
        region.setFlag(flag, FlagValue.permanent(value).withSince(Instant.EPOCH.plusSeconds(second)));
    }

    Stream<Flag<?>> stockFlags() {
        return FlagRegistry.get().getByOwner(core).stream();
    }

    /**
     * Two equally specific regions, {@code b} written after {@code a}: a {@code prefer-among-equals} flag keeps the value
     * that won the old tie, a first-defined one takes the newest, a joining one counts both.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("stockFlags")
    @SuppressWarnings("unchecked")
    void stockFlagResolvesTwoEqualRegions(Flag<?> stock) {
        stockIterated++;
        Region a = region(core, "a-" + stock.getKey().getName(), 0, box(20), null);
        Region b = region(core, "b-" + stock.getKey().getName(), 0, box(20), null);
        Class<?> valueType = stock.getType().getValueType();
        Object first;
        Object second;
        if (valueType == Tristate.class) {
            first = Tristate.FALSE;
            second = Tristate.TRUE;
        } else if (valueType == DenyEntryMessage.class) {
            first = new DenyEntryMessage(Tristate.TRUE, "stay out");
            second = new DenyEntryMessage(Tristate.FALSE, "welcome");
        } else {
            List<Object> values = TestRegions.twoValues(stock);
            first = values.get(0);
            second = values.get(1);
        }
        Flag<Object> typed = (Flag<Object>) stock;
        put(a, typed, first, 1);
        put(b, typed, second, 2);
        Resolution<Object> resolution = lookup.explain(typed, WORLD, 5, 5, 5, null);
        Object value = resolution.getValue();
        String name = stock.getKey().toString();
        if (!stock.accepts(a)) {   //a flag only a group carries: the regions' values are kept and ignored
            assertEquals(stock.getDefault(), value, name);
            return;
        }
        switch (resolution.getResolver().getId()) {
            case "prefer-among-equals":
                Object expected = valueType == DenyEntryMessage.class ? first
                        : stock == CoreFlags.ALLOW_FLY ? Tristate.FALSE : Tristate.TRUE; //allow-fly: the grounding wins
                assertEquals(expected, value, name);
                break;
            case "first-defined":
                assertEquals(second, value, name + ": the newest of two equals comes first");
                break;
            case "concat":
                List<Object> joined = new ArrayList<>((List<Object>) second);
                joined.addAll((List<Object>) first);
                assertEquals(joined, value, name);
                break;
            default:
                assertNotNull(value, name);
                assertEquals(2, resolution.getCountedLayers().size(), name + " joins both layers");
        }
    }
}
