package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import java.util.TreeSet;
import java.util.Set;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.ItemContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision every Hytale region System makes - the source gate first, then the regions - and the actors it hands
 * them: a player as itself, an NPC by its attitude, an explosion's block with nobody behind it as the environment.
 */
@ECoreTest
class HyRegionSystemsTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld server;
    private RegionWorld regions;
    private ECPluginData core;
    private final TestFPlayerSender miner = new TestFPlayerSender("miner");

    /** Never denies; keeps every context it is asked about. */
    static final class CapturingFlag extends TristateFlag {
        final List<ActionContext> asked = new ArrayList<>();

        CapturingFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "capturing-break"), Collections.singleton(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            asked.add(context);
            return Decision.PASS;
        }
    }

    @BeforeEach
    void open() {
        server = Platforms.lenient().install();
        regions = RegionWorld.open("HyRegionOwner", tempDir.resolve("regions"));
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        FlagRegistry.get().register(core, CoreFlags.DENY_BLOCK_BREAK);
    }

    @AfterEach
    void close() {
        try {
            regions.close();
            FlagRegistry.get().unregisterAll(regions.getOwner());
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        } finally {
            server.close();
        }
    }

    private Region vault(TristateFlag flag) {
        Region region = regions.cuboid("vault", WORLD, 0, 0, 0, 10, 10, 10);
        region.setFlag(flag, Tristate.TRUE);
        return region;
    }

    private BlockContext breakBy(Actor actor, int x, int y, int z, Object event) {
        return new BlockContext(EventSource.BREAK, HyRegionContexts.position(WORLD, new Vector3i(x, y, z)), actor, event,
                HyRegionContexts.key("Rock_Stone"), null);
    }

    private static BreakBlockEvent breakEvent(int x, int y, int z) {
        return new BreakBlockEvent(null, new Vector3i(x, y, z), null);
    }

    @Test
    void withTheSourceUnneededTheSystemReturnsWithoutBuildingOrDeciding() {
        regions.cuboid("plain", WORLD, 0, 0, 0, 10, 10, 10);   //a region, but no flag that needs BREAK
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.BREAK));
        AtomicInteger built = new AtomicInteger();
        BreakBlockEvent event = breakEvent(5, 5, 5);

        boolean denied = HyRegionSystems.judge(EventSource.BREAK, WORLD, event, () -> {
            built.incrementAndGet();
            return breakBy(HyRegionContexts.actor(miner, null, null, null), 5, 5, 5, event);
        });

        assertFalse(denied);
        assertFalse(event.isCancelled());
        assertEquals(0, built.get(), "the context was built for a source no region needs");
    }

    @Test
    void aBreakInsideDenyBlockBreakIsCancelledAndOutsideItHappens() {
        vault(CoreFlags.DENY_BLOCK_BREAK);
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.BREAK));
        Actor player = HyRegionContexts.actor(miner, null, null, null);

        BreakBlockEvent inside = breakEvent(5, 5, 5);
        BreakBlockEvent outside = breakEvent(50, 5, 5);
        assertTrue(HyRegionSystems.judge(EventSource.BREAK, WORLD, inside, () -> breakBy(player, 5, 5, 5, inside)));
        assertTrue(inside.isCancelled());
        assertFalse(HyRegionSystems.judge(EventSource.BREAK, WORLD, outside, () -> breakBy(player, 50, 5, 5, outside)));
        assertFalse(outside.isCancelled(), "outside the region the break happens");
    }

    @Test
    void aDisabledWorldIsNeverAsked() {
        vault(CoreFlags.DENY_BLOCK_BREAK);
        List<String> before = ECSettings.REGIONS_DISABLED_WORLDS;
        ECSettings.REGIONS_DISABLED_WORLDS = Collections.singletonList(WORLD);
        try {
            BreakBlockEvent event = breakEvent(5, 5, 5);
            assertFalse(HyRegionSystems.judge(EventSource.BREAK, WORLD, event,
                    () -> breakBy(HyRegionContexts.actor(miner, null, null, null), 5, 5, 5, event)));
            assertFalse(event.isCancelled());
        } finally {
            ECSettings.REGIONS_DISABLED_WORLDS = before;
        }
    }

    @Test
    void anAlreadyCancelledEventIsLeftAlone() {
        CapturingFlag capturing = new CapturingFlag(regions.getOwner());
        FlagRegistry.get().register(regions.getOwner(), capturing);
        vault(capturing);
        BreakBlockEvent event = breakEvent(5, 5, 5);
        event.setCancelled(true);

        HyRegionSystems.judge(EventSource.BREAK, WORLD, event, () -> breakBy(HyRegionContexts.actor(miner, null, null, null), 5, 5, 5, event));

        assertTrue(capturing.asked.isEmpty(), "another plugin already said no; the regions have nothing to add");
    }

    @Test
    void aPlayerActsAsItselfWithItselfAsTheRoot() {
        Actor actor = HyRegionContexts.actor(miner, null, null, null);

        assertSame(Actor.Type.ENTITY, actor.getType());
        assertSame(ECEntityType.PLAYER, actor.getEntityType());
        assertSame(miner, actor.getRootPlayer());
        assertEquals(Actor.getPlayerKey(), actor.getKey());
    }

    @Test
    void anNpcIsTypedByItsAttitudeAndHasNoPlayerBehindIt() {
        Actor brute = HyRegionContexts.actor(null, "Trork_Brute", Attitude.HOSTILE, null);
        assertSame(ECEntityType.MONSTER, brute.getEntityType());
        assertNull(brute.getRootPlayer());
        assertEquals(TypeKey.of("hytale", "Trork_Brute"), brute.getKey());

        assertSame(ECEntityType.ANIMAL, HyRegionContexts.actor(null, "Sheep", Attitude.NEUTRAL, null).getEntityType());
        assertSame(ECEntityType.ANIMAL, HyRegionContexts.actor(null, "Kweebec", Attitude.FRIENDLY, null).getEntityType());
        assertSame(ECEntityType.NPC, HyRegionContexts.actor(null, "Quest_Giver", null, null).getEntityType(),
                "a role with no attitude is a scripted NPC");
        assertSame(ECEntityType.OTHER, HyRegionContexts.actor(null, null, null, null).getEntityType());
    }

    @Test
    void anExplosionsBlockIsBrokenByTheEnvironment() {
        CapturingFlag capturing = new CapturingFlag(regions.getOwner());
        FlagRegistry.get().register(regions.getOwner(), capturing);
        vault(capturing);

        HyRegionSystems.explode(WORLD, new DamageBlockEvent(null, new Vector3i(5, 5, 5), null, 0f, 1f));

        Actor actor = capturing.asked.get(0).getActor();
        assertSame(Actor.Type.ENVIRONMENT, actor.getType());
        assertEquals(HyRegionContexts.EXPLOSION, actor.getKey());
        assertNull(actor.getRootPlayer());
    }

    @Test
    void anExplosionKeepsTheInsideOfDenyBlockBreakAndBreaksTheOutside() {
        vault(CoreFlags.DENY_BLOCK_BREAK);

        DamageBlockEvent inside = new DamageBlockEvent(null, new Vector3i(5, 5, 5), null, 0f, 1f);
        DamageBlockEvent outside = new DamageBlockEvent(null, new Vector3i(50, 5, 5), null, 0f, 1f);
        HyRegionSystems.explode(WORLD, inside);
        HyRegionSystems.explode(WORLD, outside);

        assertTrue(inside.isCancelled(), "the block inside deny-block-break survives the explosion");
        assertFalse(outside.isCancelled(), "the block outside breaks");
    }

    @Test
    void anAssetIdIsTheKeyAsTheServerSpellsIt() {
        assertEquals(TypeKey.of("hytale", "Rock_Stone"), HyRegionContexts.key("Rock_Stone"));
        assertEquals(TypeKey.of("mymod", "Crystal"), HyRegionContexts.key("mymod:Crystal"), "a namespace the server gives is kept");
        assertEquals(TypeKey.of("hytale", "Unknown"), HyRegionContexts.key((BlockType) null));
        assertNull(HyRegionContexts.itemKey(null), "an empty hand holds nothing");
    }

    @Test
    void anItemEventWithItsSourceUnneededReturnsBeforeReadingTheEntity() {
        regions.cuboid("plain", WORLD, 0, 0, 0, 10, 10, 10);
        DropItemEvent.Drop drop = new DropItemEvent.Drop(null, 0f);
        InteractivelyPickupItemEvent pickup = new InteractivelyPickupItemEvent(null);

        //a null entity and store: anything past the gate would throw
        assertFalse(HyRegionSystems.onItem(EventSource.ITEM_DROP, null, null, drop, null));
        assertFalse(HyRegionSystems.onItem(EventSource.ITEM_PICKUP, null, null, pickup, null));
        assertFalse(drop.isCancelled());
        assertFalse(pickup.isCancelled());
    }

    @Test
    void anItemDroppedOrPickedUpInsideItsDenyFlagIsCancelled() {
        FlagRegistry.get().register(core, CoreFlags.DENY_ITEM_DROP);
        FlagRegistry.get().register(core, CoreFlags.DENY_ITEM_PICKUP);
        Region vault = vault(CoreFlags.DENY_ITEM_DROP);
        vault.setFlag(CoreFlags.DENY_ITEM_PICKUP, Tristate.TRUE);
        Actor player = HyRegionContexts.actor(miner, null, null, null);
        TypeKey stick = HyRegionContexts.key("Ingredient_Stick");
        DropItemEvent.Drop drop = new DropItemEvent.Drop(null, 0f);
        InteractivelyPickupItemEvent pickup = new InteractivelyPickupItemEvent(null);

        assertTrue(HyRegionSystems.judge(EventSource.ITEM_DROP, WORLD, drop, () -> new ItemContext(EventSource.ITEM_DROP,
                HyRegionContexts.position(WORLD, new Vector3i(5, 5, 5)), player, drop, stick)));
        assertTrue(HyRegionSystems.judge(EventSource.ITEM_PICKUP, WORLD, pickup, () -> new ItemContext(EventSource.ITEM_PICKUP,
                HyRegionContexts.position(WORLD, new Vector3i(5, 5, 5)), player, pickup, stick)));
        assertTrue(drop.isCancelled());
        assertTrue(pickup.isCancelled());
    }

    @Test
    void theNewFlagsOnTheSpawnDamageAndPlaceSystemsLeaveBeforeBuildingWhileUnneeded() {
        regions.cuboid("plain", WORLD, 0, 0, 0, 10, 10, 10);
        for (TristateFlag flag : Arrays.asList(CoreFlags.DENY_SPAWN, CoreFlags.DENY_SPAWN_MONSTER, CoreFlags.DENY_SPAWN_ANIMAL,
                CoreFlags.DENY_DAMAGE_FROM_MOBS, CoreFlags.DENY_BLOCK_PLACE, CoreFlags.DENY_BLOCK_PLACE_ENVIRONMENT)) {
            FlagRegistry.get().register(core, flag);
        }
        AtomicInteger built = new AtomicInteger();

        assertFalse(HyRegionSystems.spawnDenied(WORLD, () -> {
            built.incrementAndGet();
            return null;
        }));
        for (EventSource source : Arrays.asList(EventSource.DAMAGE, EventSource.PLACE)) {
            BreakBlockEvent event = breakEvent(5, 5, 5);
            assertFalse(HyRegionSystems.judge(source, WORLD, event, () -> {
                built.incrementAndGet();
                return null;
            }));
        }
        assertEquals(0, built.get(), "a context was built for a source no region needs");

        Region den = vault(CoreFlags.DENY_SPAWN_MONSTER);
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.SPAWN), "deny-spawn-monster asks for SPAWN");
        den.setFlag(CoreFlags.DENY_DAMAGE_FROM_MOBS, Tristate.TRUE);
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.DAMAGE));
    }

    @Test
    void theBootWarnsOncePerFlagWithoutEffectOnHytaleAndNeverForOneWithASystem() {
        for (RegionFlag<?> flag : CoreFlags.all()) {
            if (!flag.isRegistered()) {
                FlagRegistry.get().register(core, flag);
            }
        }
        try {
            FlagRegistry.get().setServedSources(HyRegionSystems.SERVED_SOURCES);
            FlagRegistry.get().setMissingCapabilities(HyPlatform.PLAYER_STATE.getUnsupported());
            List<String> lines = new ArrayList<>();
            Set<String> lackingCapability = new TreeSet<>();
            for (RegionFlag<?> flag : CoreFlags.all()) {
                String line = FlagRegistry.get().describeWithoutEffect(flag);
                boolean served = HyRegionSystems.SERVED_SOURCES.containsAll(flag.getNeeds()) || flag.getNeeds().equals(Collections.singleton(EventSource.NONE));
                boolean capable = FlagRegistry.get().getMissingCapabilities(flag).isEmpty();
                if (line == null) {
                    assertTrue(served && capable, flag.getKey() + " has no effect here and must say so");
                    continue;
                }
                assertFalse(served && capable, flag.getKey() + " has its effect here and must stay silent: " + line);
                if (served) {
                    lackingCapability.add(flag.getKey().getName());
                    assertTrue(line.contains("which this server cannot"), line);
                }
                assertTrue(line.startsWith("Flag " + flag.getKey() + " has no effect"), line);
                lines.add(line);
            }
            assertFalse(lines.isEmpty());
            for (String key : Arrays.asList("spawn-point", "deny-vehicle", "deny-vehicle-enter", "deny-fire-spread", "deny-fluid-flow",
                    "deny-explosion")) {
                assertTrue(lines.stream().anyMatch(line -> line.startsWith("Flag evernifecore:" + key + " ")), key + " in " + lines);
            }
            assertEquals(new TreeSet<>(Arrays.asList("weather-lock", "feed", "hunger-over-time")), lackingCapability,
                    "Hytale has no player weather it can give back and no food");
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("Flag evernifecore:force-fly ") && line.contains("flight-toggle")),
                    "force-fly cannot keep a player from landing here: " + lines);
        } finally {
            FlagRegistry.get().setServedSources(null);
            FlagRegistry.get().setMissingCapabilities(Collections.emptyList());
        }
    }
}
