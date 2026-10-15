package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryMessage;
import br.com.finalcraft.evernifecore.region.flag.core.DenyPvpFlag;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core catalog: the static instances, the names, and how each flag breaks a tie - checked against the catalog, not against the flag. */
@ECoreTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreFlagsTest {

    private static final String CORE = "EverNifeCore";

    /** What wins a tie, straight from the catalog: TRUE for every deny flag, denied=TRUE for the hybrid, the first otherwise. */
    private static final Map<String, String> CATALOG = new HashMap<>();

    static {
        for (String name : Arrays.asList("deny-pvp", "invincible", "deny-damage-animals", "deny-block-break", "deny-block-break-mobs",
                "deny-block-break-environment", "deny-block-break-fake-players", "deny-block-place", "deny-block-place-fake-players",
                "deny-explosion", "deny-explosion-mobs", "deny-interact", "deny-interact-blocks", "deny-interact-entities", "deny-entry", "deny-exit", "deny-teleport-in", "deny-teleport-out",
                "deny-monster-spawn", "deny-animal-spawn")) {
            CATALOG.put(name, "tristate-true");
        }
        CATALOG.put("deny-entry-with-message", "denied-true");
        for (String name : Arrays.asList("greeting", "farewell", "deny-message", "teleport-point")) {
            CATALOG.put(name, "first");
        }
    }

    private ECPluginData core;
    private int iterated;

    @BeforeAll
    void registerCore() throws Exception {
        core = Plugins.fakePluginData(CORE, Files.createTempDirectory("core-flags").toFile());
        CoreFlags.registerAll(core);
    }

    @AfterAll
    void tearDown() {
        try {
            assertTrue(iterated >= 18, "the tie test must have run over the whole catalog, ran " + iterated);
            assertEquals(25, iterated, "the catalog holds 25 flags");
        } finally {
            FlagRegistry.get().reset();
            Plugins.forget(CORE);
        }
    }

    Stream<RegionFlag<?>> coreFlags() {
        return FlagRegistry.get().getByOwner(core).stream();
    }

    @Test
    void everyCoreFlagSaysWhatItDoes() {
        List<RegionFlag<?>> flags = coreFlags().collect(Collectors.toList());
        assertFalse(flags.isEmpty());
        for (RegionFlag<?> flag : flags) {
            String description = flag.getDescription();
            assertTrue(description != null && !description.trim().isEmpty(), flag.getKey().toString());
        }
    }

    @Test
    void catalogNamesAreExactlyTheCoreOnes() {
        TreeSet<String> registered = FlagRegistry.get().getByOwner(core).stream()
                .map(flag -> flag.getKey().toString()).collect(Collectors.toCollection(TreeSet::new));
        TreeSet<String> expected = CATALOG.keySet().stream().map(name -> "evernifecore:" + name).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, registered);
        assertFalse(FlagRegistry.get().find("night-vision").isPresent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coreFlags")
    @SuppressWarnings("unchecked")
    void tieResolvesAsTheCatalogSays(RegionFlag<?> flag) {
        iterated++;
        String expectation = CATALOG.get(flag.getKey().getName());
        if ("tristate-true".equals(expectation)) {
            RegionFlag<Tristate> tristate = (RegionFlag<Tristate>) flag;
            assertEquals(Tristate.TRUE, tristate.resolveTie(Arrays.asList(Tristate.FALSE, Tristate.TRUE)));
            assertEquals(Tristate.TRUE, tristate.resolveTie(Arrays.asList(Tristate.TRUE, Tristate.FALSE)));
            assertEquals(Tristate.FALSE, tristate.getDefault());
        } else if ("denied-true".equals(expectation)) {
            RegionFlag<DenyEntryMessage> hybrid = (RegionFlag<DenyEntryMessage>) flag;
            DenyEntryMessage open = new DenyEntryMessage(Tristate.FALSE, "welcome");
            DenyEntryMessage closed = new DenyEntryMessage(Tristate.TRUE, "stay out");
            assertEquals(closed, hybrid.resolveTie(Arrays.asList(open, closed)));
            assertEquals(open, hybrid.resolveTie(Arrays.asList(open, new DenyEntryMessage(Tristate.UNDEFINED, null))));
        } else if ("first".equals(expectation)) {
            List<Object> values = flag.getType().getValueType() == WorldLocPos.class
                    ? Arrays.asList(WorldLocPos.of(1, 2, 3, "a"), WorldLocPos.of(4, 5, 6, "b"))
                    : Arrays.asList("one", "two");
            assertEquals(values.get(0), ((RegionFlag<Object>) flag).resolveTie(values));
        } else {
            throw new AssertionError("Flag " + flag.getKey() + " is not in the catalog");
        }
    }

    @Test
    void onlyDenyPvpIgnoresTheBypass() {
        for (RegionFlag<?> flag : FlagRegistry.get().getByOwner(core)) {
            assertEquals(!flag.getKey().getName().equals("deny-pvp"), flag.isBypassable(), flag.getKey().toString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void denyFlagsDenyOnTrueAndTheHybridOnDenied() {
        WorldLocPos at = WorldLocPos.of(5, 5, 5, "world");
        TestFPlayerSender player = new TestFPlayerSender("player");
        DamageContext pvpHit = new DamageContext(at, Actors.player(player), null, Actors.player(new TestFPlayerSender("victim")));
        RegionFlag<Tristate> pvp = (RegionFlag<Tristate>) FlagRegistry.get().find("evernifecore:deny-pvp").get();
        assertEquals(Decision.DENY, pvp.decide(pvpHit, Tristate.TRUE));
        assertEquals(Decision.PASS, pvp.decide(pvpHit, Tristate.FALSE));
        MoveContext entering = new MoveContext(player, WorldLocPos.of(20, 5, 5, "world"), at, MoveCause.MOVE, MoveContext.Transition.ENTER,
                TestRegions.create(core, "arena", "world"), null);
        RegionFlag<DenyEntryMessage> hybrid = (RegionFlag<DenyEntryMessage>) FlagRegistry.get().find("deny-entry-with-message").get();
        assertEquals(Decision.DENY, hybrid.decide(entering, new DenyEntryMessage(Tristate.TRUE, null)));
        assertEquals(Decision.PASS, hybrid.decide(entering, new DenyEntryMessage(Tristate.UNDEFINED, "x")));
    }

    @Test
    void categoriesAndSources() {
        assertEquals(FlagCategory.SESSION, FlagRegistry.get().find("greeting").get().getCategory());
        assertEquals(FlagCategory.DATA, FlagRegistry.get().find("teleport-point").get().getCategory());
        assertEquals(FlagCategory.PROTECTION, FlagRegistry.get().find("deny-exit").get().getCategory());
        assertEquals(5, FlagRegistry.get().getBySource(EventSource.MOVE).stream()
                .filter(flag -> flag.getOwner() == core && flag.getCategory() == FlagCategory.PROTECTION).count());
    }

    @Test
    void everyCoreFlagIsAStaticInstanceRegisteredUnderTheCore() {
        List<RegionFlag<?>> all = CoreFlags.all();
        assertEquals(25, all.size());
        for (RegionFlag<?> flag : all) {
            assertTrue(flag.isRegistered(), flag.getKey().toString());
            assertSame(core, flag.getOwner(), flag.getKey().toString());
        }
        assertSame(CoreFlags.DENY_PVP, FlagRegistry.get().find("evernifecore:deny-pvp").get());
        assertSame(CoreFlags.DENY_INTERACT, CoreFlags.DENY_INTERACT_BLOCKS.getParent());
        assertSame(CoreFlags.DENY_BLOCK_BREAK, CoreFlags.DENY_BLOCK_BREAK_MOBS.getParent());
        assertSame(CoreFlags.DENY_BLOCK_PLACE, CoreFlags.DENY_BLOCK_PLACE_FAKE_PLAYERS.getParent());
        assertSame(CoreFlags.DENY_EXPLOSION, CoreFlags.DENY_EXPLOSION_MOBS.getParent());
    }

    @Test
    void aCoreFlagInstanceCanBeSwappedAndTheStoredValuesFollowTheKey() {
        DenyPvpFlag original = CoreFlags.DENY_PVP;
        Region region = TestRegions.loaded(core, "arena-swap", "world");
        try {
            region.setFlag(CoreFlags.DENY_PVP, Tristate.TRUE);

            FlagRegistry.get().unregister(CoreFlags.DENY_PVP);
            assertNull(region.getFlag(CoreFlags.DENY_PVP));
            assertTrue(region.getUnknownFlags().containsKey("evernifecore:deny-pvp"));

            CoreFlags.DENY_PVP = new DenyPvpFlag();
            FlagRegistry.get().register(core, CoreFlags.DENY_PVP);
            assertEquals(Tristate.TRUE, region.getFlag(CoreFlags.DENY_PVP));
            assertFalse(original.isRegistered());
        } finally {
            FlagRegistry.get().unregister(CoreFlags.DENY_PVP);
            CoreFlags.DENY_PVP = original;
            FlagRegistry.get().register(core, original);
            TestRegions.unloadAll();
        }
    }

    @Test
    void onlyTheMovementDenialsAndTheBlockBreakStartWithATriggerInterval() {
        List<RegionFlag<?>> all = CoreFlags.all();
        assertEquals(25, all.size());
        TreeSet<String> paced = new TreeSet<>();
        for (RegionFlag<?> flag : all) {
            Duration floor = flag.getDefaultTriggerInterval();
            if (!floor.isZero()) {
                Duration expected = flag == CoreFlags.DENY_BLOCK_BREAK ? Duration.ofSeconds(1) : Duration.ofMillis(250);
                assertEquals(expected, floor, flag.getKey().getName());
                paced.add(flag.getKey().getName());
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList("deny-entry", "deny-exit", "deny-teleport-in", "deny-teleport-out", "deny-entry-with-message",
                "deny-block-break")), paced);
    }
}
