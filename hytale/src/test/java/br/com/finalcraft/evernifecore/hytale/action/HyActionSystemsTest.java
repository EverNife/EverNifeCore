package br.com.finalcraft.evernifecore.hytale.action;

import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import java.util.TreeSet;
import java.util.Set;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.event.InteractNpcActionEvent;
import br.com.finalcraft.evernifecore.flag.core.ActorDenyFlag;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.testing.Logs;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.lang.reflect.Field;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.EnvironmentBreakBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.CraftActionEvent;
import br.com.finalcraft.evernifecore.action.event.ExploreActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.action.event.RespawnActionEvent;
import br.com.finalcraft.evernifecore.hytale.region.HyRegionMoveSystem;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.event.events.ecs.RespawnEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import org.joml.Vector3d;
import java.util.UUID;
import java.util.function.Supplier;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.Region;
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
class HyActionSystemsTest {

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
        return new BlockContext(EventSource.BREAK, HyActionContexts.position(WORLD, new Vector3i(x, y, z)), actor, event,
                HyActionContexts.key("Rock_Stone"), null);
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

        boolean denied = HyActionSystems.judge(EventSource.BREAK, WORLD, event, () -> {
            built.incrementAndGet();
            return breakBy(HyActionContexts.actor(miner, null, null, null), 5, 5, 5, event);
        });

        assertFalse(denied);
        assertFalse(event.isCancelled());
        assertEquals(0, built.get(), "the context was built for a source no region needs");
    }

    @Test
    void aBreakInsideDenyBlockBreakIsCancelledAndOutsideItHappens() {
        vault(CoreFlags.DENY_BLOCK_BREAK);
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.BREAK));
        Actor player = HyActionContexts.actor(miner, null, null, null);

        BreakBlockEvent inside = breakEvent(5, 5, 5);
        BreakBlockEvent outside = breakEvent(50, 5, 5);
        assertTrue(HyActionSystems.judge(EventSource.BREAK, WORLD, inside, () -> breakBy(player, 5, 5, 5, inside)));
        assertTrue(inside.isCancelled());
        assertFalse(HyActionSystems.judge(EventSource.BREAK, WORLD, outside, () -> breakBy(player, 50, 5, 5, outside)));
        assertFalse(outside.isCancelled(), "outside the region the break happens");
    }

    @Test
    void aPostIsPostedOnlyWithASubscriber() {
        AtomicInteger built = new AtomicInteger();
        BreakBlockEvent event = breakEvent(50, 5, 5);
        List<BreakActionEvent.Post> posts = new ArrayList<>();
        ECEventSubscription<BreakActionEvent.Post> subscription = ECEventBus.global().subscribe(BreakActionEvent.Post.class, posts::add);
        try {
            assertTrue(EventSourceUsage.get().isNeeded(EventSource.BREAK), "the subscriber alone needs BREAK, with no region");

            assertFalse(HyActionSystems.judge(EventSource.BREAK, WORLD, event, () -> {
                built.incrementAndGet();
                return breakBy(HyActionContexts.actor(miner, null, null, null), 50, 5, 5, event);
            }));

            assertEquals(1, posts.size());
            assertEquals(2, built.get(), "one context for the regions and the Pre, one for the Post");
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void aCancelledPreCancelsTheEcsEvent() {
        BreakBlockEvent event = breakEvent(50, 5, 5);
        ECEventSubscription<BreakActionEvent.Pre> veto = ECEventBus.global().subscribe(BreakActionEvent.Pre.class,
                pre -> pre.setCancelled(true));
        try {
            assertTrue(HyActionSystems.judge(EventSource.BREAK, WORLD, event,
                    () -> breakBy(HyActionContexts.actor(miner, null, null, null), 50, 5, 5, event)));
            assertTrue(event.isCancelled(), "no region denied it: the subscriber of the Pre did");
        } finally {
            veto.unsubscribe();
        }
    }

    @Test
    void aHitOnABlockPostsNoBreak() {
        DamageBlockEvent hit = new DamageBlockEvent(null, new Vector3i(50, 5, 5), null, 0f, 1f);
        List<BreakActionEvent.Post> posts = new ArrayList<>();
        ECEventSubscription<BreakActionEvent.Post> subscription = ECEventBus.global().subscribe(BreakActionEvent.Post.class, posts::add);
        try {
            assertFalse(HyActionSystems.explode(WORLD, hit));
            assertTrue(posts.isEmpty(), "damage on the way to a break is not the break");
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    void aDisabledWorldIsNeverAsked() {
        vault(CoreFlags.DENY_BLOCK_BREAK);
        List<String> before = ECSettings.REGIONS_DISABLED_WORLDS;
        ECSettings.REGIONS_DISABLED_WORLDS = Collections.singletonList(WORLD);
        try {
            BreakBlockEvent event = breakEvent(5, 5, 5);
            assertFalse(HyActionSystems.judge(EventSource.BREAK, WORLD, event,
                    () -> breakBy(HyActionContexts.actor(miner, null, null, null), 5, 5, 5, event)));
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

        HyActionSystems.judge(EventSource.BREAK, WORLD, event, () -> breakBy(HyActionContexts.actor(miner, null, null, null), 5, 5, 5, event));

        assertTrue(capturing.asked.isEmpty(), "another plugin already said no; the regions have nothing to add");
    }

    @Test
    void aPlayerActsAsItselfWithItselfAsTheRoot() {
        Actor actor = HyActionContexts.actor(miner, null, null, null);

        assertSame(Actor.Type.ENTITY, actor.getType());
        assertSame(ECEntityType.PLAYER, actor.getEntityType());
        assertSame(miner, actor.getRootPlayer());
        assertEquals(Actor.getPlayerKey(), actor.getKey());
    }

    @Test
    void anNpcIsTypedByItsAttitudeAndHasNoPlayerBehindIt() {
        Actor brute = HyActionContexts.actor(null, "Trork_Brute", Attitude.HOSTILE, null);
        assertSame(ECEntityType.MONSTER, brute.getEntityType());
        assertNull(brute.getRootPlayer());
        assertEquals(TypeKey.of("hytale", "Trork_Brute"), brute.getKey());

        assertSame(ECEntityType.ANIMAL, HyActionContexts.actor(null, "Sheep", Attitude.NEUTRAL, null).getEntityType());
        assertSame(ECEntityType.ANIMAL, HyActionContexts.actor(null, "Kweebec", Attitude.FRIENDLY, null).getEntityType());
        assertSame(ECEntityType.NPC, HyActionContexts.actor(null, "Quest_Giver", null, null).getEntityType(),
                "a role with no attitude is a scripted NPC");
        assertSame(ECEntityType.OTHER, HyActionContexts.actor(null, null, null, null).getEntityType());
    }

    @Test
    void anExplosionsBlockIsBrokenByTheEnvironment() {
        CapturingFlag capturing = new CapturingFlag(regions.getOwner());
        FlagRegistry.get().register(regions.getOwner(), capturing);
        vault(capturing);

        HyActionSystems.explode(WORLD, new DamageBlockEvent(null, new Vector3i(5, 5, 5), null, 0f, 1f));

        Actor actor = capturing.asked.get(0).getActor();
        assertSame(Actor.Type.ENVIRONMENT, actor.getType());
        assertEquals(HyActionContexts.EXPLOSION, actor.getKey());
        assertNull(actor.getRootPlayer());
    }

    @Test
    void anExplosionKeepsTheInsideOfDenyBlockBreakAndBreaksTheOutside() {
        vault(CoreFlags.DENY_BLOCK_BREAK);

        DamageBlockEvent inside = new DamageBlockEvent(null, new Vector3i(5, 5, 5), null, 0f, 1f);
        DamageBlockEvent outside = new DamageBlockEvent(null, new Vector3i(50, 5, 5), null, 0f, 1f);
        HyActionSystems.explode(WORLD, inside);
        HyActionSystems.explode(WORLD, outside);

        assertTrue(inside.isCancelled(), "the block inside deny-block-break survives the explosion");
        assertFalse(outside.isCancelled(), "the block outside breaks");
    }

    @Test
    void anAssetIdIsTheKeyAsTheServerSpellsIt() {
        assertEquals(TypeKey.of("hytale", "Rock_Stone"), HyActionContexts.key("Rock_Stone"));
        assertEquals(TypeKey.of("mymod", "Crystal"), HyActionContexts.key("mymod:Crystal"), "a namespace the server gives is kept");
        assertEquals(TypeKey.of("hytale", "Unknown"), HyActionContexts.key((BlockType) null));
        assertNull(HyActionContexts.itemKey(null), "an empty hand holds nothing");
    }

    @Test
    void anItemEventWithItsSourceUnneededReturnsBeforeReadingTheEntity() {
        regions.cuboid("plain", WORLD, 0, 0, 0, 10, 10, 10);
        DropItemEvent.Drop drop = new DropItemEvent.Drop(null, 0f);
        InteractivelyPickupItemEvent pickup = new InteractivelyPickupItemEvent(null);

        //a null entity and store: anything past the gate would throw
        assertFalse(HyActionSystems.onItem(EventSource.ITEM_DROP, null, null, drop, null));
        assertFalse(HyActionSystems.onItem(EventSource.ITEM_PICKUP, null, null, pickup, null));
        assertFalse(drop.isCancelled());
        assertFalse(pickup.isCancelled());
    }

    @Test
    void anItemDroppedOrPickedUpInsideItsDenyFlagIsCancelled() {
        FlagRegistry.get().register(core, CoreFlags.DENY_ITEM_DROP);
        FlagRegistry.get().register(core, CoreFlags.DENY_ITEM_PICKUP);
        Region vault = vault(CoreFlags.DENY_ITEM_DROP);
        vault.setFlag(CoreFlags.DENY_ITEM_PICKUP, Tristate.TRUE);
        Actor player = HyActionContexts.actor(miner, null, null, null);
        TypeKey stick = HyActionContexts.key("Ingredient_Stick");
        DropItemEvent.Drop drop = new DropItemEvent.Drop(null, 0f);
        InteractivelyPickupItemEvent pickup = new InteractivelyPickupItemEvent(null);

        assertTrue(HyActionSystems.judge(EventSource.ITEM_DROP, WORLD, drop, () -> new ItemContext(EventSource.ITEM_DROP,
                HyActionContexts.position(WORLD, new Vector3i(5, 5, 5)), player, drop, stick, 1, null)));
        assertTrue(HyActionSystems.judge(EventSource.ITEM_PICKUP, WORLD, pickup, () -> new ItemContext(EventSource.ITEM_PICKUP,
                HyActionContexts.position(WORLD, new Vector3i(5, 5, 5)), player, pickup, stick, 1, null)));
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

        assertFalse(HyActionSystems.spawnDenied(WORLD, () -> {
            built.incrementAndGet();
            return null;
        }));
        for (EventSource source : Arrays.asList(EventSource.DAMAGE, EventSource.PLACE)) {
            BreakBlockEvent event = breakEvent(5, 5, 5);
            assertFalse(HyActionSystems.judge(source, WORLD, event, () -> {
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
        for (Flag<?> flag : CoreFlags.all()) {
            if (!flag.isRegistered()) {
                FlagRegistry.get().register(core, flag);
            }
        }
        try {
            HyActionSystems.declareSupport("Hytale");
            List<String> lines = new ArrayList<>();
            Set<String> lackingCapability = new TreeSet<>();
            for (Flag<?> flag : CoreFlags.all()) {
                String line = FlagRegistry.get().describeWithoutEffect(flag);
                boolean served = HyActionSystems.SERVED_SOURCES.containsAll(flag.getNeeds()) || flag.getNeeds().equals(Collections.singleton(EventSource.NONE));
                boolean partly = !served && flag.getNeeds().stream().anyMatch(HyActionSystems.SERVED_SOURCES::contains);
                boolean capable = FlagRegistry.get().getMissingCapabilities(flag).isEmpty();
                boolean neverMatches = flag instanceof ActorDenyFlag && ((ActorDenyFlag) flag).getActorEntries().equals(Collections.singletonList("fake-player"));
                if (line == null) {
                    assertTrue(served && capable && !neverMatches, flag.getKey() + " cannot act in full here and must say so");
                    continue;
                }
                if (neverMatches) {
                    assertTrue(line.contains(" never matches "), line);
                    continue;
                }
                assertFalse(served && capable, flag.getKey() + " has its effect here and must stay silent: " + line);
                if (served) {
                    lackingCapability.add(flag.getKey().getName());
                    assertTrue(line.contains("which this server cannot"), line);
                }
                assertTrue(line.startsWith("Flag " + flag.getKey() + (partly ? " acts in part" : " has no effect")), line);
                lines.add(line);
            }
            assertFalse(lines.isEmpty());
            for (String key : Arrays.asList("deny-vehicle", "deny-vehicle-enter", "deny-fire-spread", "deny-fluid-flow",
                    "deny-explosion")) {
                assertTrue(lines.stream().anyMatch(line -> line.startsWith("Flag evernifecore:" + key + " ")), key + " in " + lines);
            }
            assertEquals(new TreeSet<>(Arrays.asList("weather-lock", "feed", "hunger-over-time")), lackingCapability,
                    "Hytale has no player weather it can give back and no food");
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("Flag evernifecore:force-fly ") && line.contains("flight-toggle")),
                    "force-fly cannot keep a player from landing here: " + lines);
        } finally {
            ActionSupport.declare("an undeclared platform", null, null, true, null);
            FlagRegistry.get().setMissingCapabilities(Collections.emptyList());
        }
    }

    @Test
    void hytaleRaisesKillCraftRespawnExploreAndCommandAndNoSourceItHasNoEventFor() {
        assertTrue(HyActionSystems.SERVED_SOURCES.containsAll(Arrays.asList(EventSource.KILL, EventSource.CRAFT, EventSource.RESPAWN,
                EventSource.EXPLORE, EventSource.COMMAND)));
        for (EventSource none : Arrays.asList(EventSource.SMELT, EventSource.CONSUME, EventSource.XP_GAIN, EventSource.ENCHANT,
                EventSource.TAME, EventSource.BREED, EventSource.SHEAR)) {
            assertFalse(HyActionSystems.SERVED_SOURCES.contains(none), none + " has no event on Hytale");
        }
        assertTrue(HyActionSystems.PRE_SOURCES.containsAll(Arrays.asList(EventSource.CRAFT, EventSource.RESPAWN, EventSource.COMMAND)),
                "the game asks the craft and the respawn before them; the command framework asks before its commands");
        assertFalse(HyActionSystems.PRE_SOURCES.contains(EventSource.KILL), "a death cannot be cancelled");
        assertFalse(HyActionSystems.PRE_SOURCES.contains(EventSource.EXPLORE), "the step is seen after it happened");
    }

    @Test
    void aCraftCountsEveryItemOfEveryCraftAndACancelledPreCancelsIt() {
        Actor crafter = HyActionContexts.actor(miner, null, null, null);
        MaterialQuantity pickaxes = new MaterialQuantity("Tool_Pickaxe_Crude", null, null, 2, null);
        CraftRecipeEvent.Pre pre = new CraftRecipeEvent.Pre(null, 3);
        List<CraftActionEvent.Post> posts = new ArrayList<>();
        ECEventSubscription<CraftActionEvent.Pre> veto = ECEventBus.global().subscribe(CraftActionEvent.Pre.class, event -> event.setCancelled(true));
        ECEventSubscription<CraftActionEvent.Post> post = ECEventBus.global().subscribe(CraftActionEvent.Post.class, posts::add);
        try {
            assertTrue(HyActionSystems.judge(EventSource.CRAFT, WORLD, pre,
                    () -> HyActionSystems.crafted(WORLD, new Vector3d(5, 5, 5), crafter, pre, pickaxes, 3), false));
            assertTrue(pre.isCancelled(), "the game checks the Pre before it takes the inputs");
            assertTrue(posts.isEmpty(), "the Post waits for the game's after-craft event");

            HyActionSystems.posted(EventSource.CRAFT, WORLD, () -> HyActionSystems.crafted(WORLD, new Vector3d(5, 5, 5), crafter, null, pickaxes, 3));
            assertEquals(1, posts.size());
            assertEquals(6, posts.get(0).getContext().getAmount(), "two pickaxes a craft, three crafts");
            assertEquals(HyActionContexts.key("Tool_Pickaxe_Crude"), posts.get(0).getContext().getItemKey());
        } finally {
            veto.unsubscribe();
            post.unsubscribe();
        }
    }

    @Test
    void aKillIsBuiltAndPostedOnlyWhileSomebodyListens() {
        AtomicInteger built = new AtomicInteger();
        Supplier<DamageContext> kill = () -> {
            built.incrementAndGet();
            return new DamageContext(EventSource.KILL, HyActionContexts.position(WORLD, new Vector3d(5, 5, 5)),
                    HyActionContexts.actor(miner, null, null, null), null, Actor.environment(HyActionSystems.UNKNOWN_DEATH));
        };
        HyActionSystems.posted(EventSource.KILL, WORLD, kill);
        assertEquals(0, built.get(), "nobody needs KILL");

        List<KillActionEvent.Post> posts = new ArrayList<>();
        ECEventSubscription<KillActionEvent.Post> post = ECEventBus.global().subscribe(KillActionEvent.Post.class, posts::add);
        try {
            HyActionSystems.posted(EventSource.KILL, WORLD, kill);
            assertEquals(1, posts.size());
            assertSame(miner, posts.get(0).getPlayer());
        } finally {
            post.unsubscribe();
        }
    }

    @Test
    void aCancelledRespawnPreKeepsThePlayerDead() {
        RespawnEvent event = new RespawnEvent();
        ECEventSubscription<RespawnActionEvent.Pre> veto = ECEventBus.global().subscribe(RespawnActionEvent.Pre.class,
                pre -> pre.setCancelled(true));
        try {
            assertTrue(HyActionSystems.judge(EventSource.RESPAWN, WORLD, event, () -> new SimpleContext(EventSource.RESPAWN,
                    HyActionContexts.position(WORLD, new Vector3d(5, 5, 5)), HyActionContexts.actor(miner, null, null, null), event)));
            assertTrue(event.isCancelled(), "the game respawns nobody whose RespawnEvent was cancelled");
        } finally {
            veto.unsubscribe();
        }
    }

    @Test
    void aStepIntoAnotherChunkIsExploredAndTheFirstSightingOrATeleportIsNot() {
        HyRegionMoveSystem system = new HyRegionMoveSystem(RegionMovementTracker::get);
        UUID uuid = UUID.randomUUID();
        Supplier<Actor> actor = () -> HyActionContexts.actor(miner, null, null, null);
        List<ExploreActionEvent.Post> steps = new ArrayList<>();
        ECEventSubscription<ExploreActionEvent.Post> post = ECEventBus.global().subscribe(ExploreActionEvent.Post.class, steps::add);
        try {
            system.explore(uuid, actor, WORLD, new Vector3d(1, 5, 1), false);
            system.explore(uuid, actor, WORLD, new Vector3d(2, 5, 1), false);
            assertTrue(steps.isEmpty(), "the first sighting and a step inside the chunk");

            system.explore(uuid, actor, WORLD, new Vector3d(200, 5, 1), true);
            assertTrue(steps.isEmpty(), "a teleport is no exploring");

            system.explore(uuid, actor, WORLD, new Vector3d(1000, 5, 1), false);
            assertEquals(1, steps.size());
            assertEquals(ChunkPos.fromBlock(200, 1), steps.get(0).getContext().getFrom());
            assertEquals(ChunkPos.fromBlock(1000, 1), steps.get(0).getContext().getTo());
        } finally {
            post.unsubscribe();
        }
    }

    /** A hit by nobody on {@code miner}, standing at {@code x}. */
    private DamageContext hitAt(int x, Damage damage) {
        return new DamageContext(EventSource.DAMAGE, HyActionContexts.position(WORLD, new Vector3d(x, 5, 5)),
                Actor.environment(HyActionSystems.UNKNOWN_DEATH), damage, HyActionContexts.actor(miner, null, null, null));
    }

    @Test
    void aHitLetThroughInsideADamageMultiplierIsScaledAndOutsideItIsNot() {
        FlagRegistry.get().register(core, CoreFlags.INVINCIBLE);
        FlagRegistry.get().register(core, CoreFlags.DAMAGE_MULTIPLIER);
        regions.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10).setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        Damage inside = new Damage(Damage.NULL_SOURCE, 0, 5f);
        Damage outside = new Damage(Damage.NULL_SOURCE, 0, 5f);

        assertFalse(HyActionSystems.judgeDamage(WORLD, inside, () -> hitAt(5, inside)));
        assertFalse(HyActionSystems.judgeDamage(WORLD, outside, () -> hitAt(50, outside)));

        assertEquals(10f, inside.getAmount(), "the region doubles the hit");
        assertEquals(5f, outside.getAmount(), "outside the region the hit is untouched");
    }

    @Test
    void anEnvironmentBreakReachesTheJournalAndThePost() {
        FlagRegistry.get().register(core, CoreFlags.TRACK_CHANGES);
        Region garden = regions.cuboid("garden", WORLD, 0, 0, 0, 10, 10, 10);
        garden.getManager().getTerrain().track(garden);
        List<BreakActionEvent.Post> posts = new ArrayList<>();
        ECEventSubscription<BreakActionEvent.Post> post = ECEventBus.global().subscribe(BreakActionEvent.Post.class, posts::add);
        try {
            HyActionSystems.environmentBreak(WORLD, new EnvironmentBreakBlockEvent(new Vector3i(5, 5, 5), null));
            HyActionSystems.environmentBreak(WORLD, new EnvironmentBreakBlockEvent(new Vector3i(50, 5, 5), null));

            assertEquals(1, garden.getManager().getTerrain().getJournalSize(garden), "only the break inside the tracking region is kept");
            assertEquals(2, posts.size(), "every break the world made is posted");
            Actor actor = posts.get(0).getContext().getActor();
            assertSame(Actor.Type.ENVIRONMENT, actor.getType());
            assertEquals(HyActionContexts.ENVIRONMENT_BREAK, actor.getKey());
            assertTrue(actor.getKey().toString().startsWith("environment:"), actor.getKey().toString());
        } finally {
            post.unsubscribe();
        }
    }

    @Test
    void respawnInsideASpawnPointRegionTeleportsThere() {
        FlagRegistry.get().register(core, CoreFlags.SPAWN_POINT);
        WorldLocPos camp = WorldLocPos.of(100.5, 64, -20.5, WORLD);
        regions.cuboid("camp", WORLD, 0, 0, 0, 10, 10, 10).setFlag(CoreFlags.SPAWN_POINT, camp);

        WorldLocPos point = HyActionSystems.spawnPointOf(WORLD, new Vector3d(5, 5, 5), miner);

        assertEquals(camp, point, "died inside the camp: back to its spawn-point");
        Teleport teleport = HyRegionMoveSystem.teleport(point, null, new Rotation3f());
        assertEquals(new Vector3d(100.5, 64, -20.5), teleport.getPosition());
        assertNull(HyActionSystems.spawnPointOf(WORLD, new Vector3d(50, 5, 5), miner), "died where no spawn-point is defined: no teleport");
    }

    @Test
    void bootNamesTheFakePlayerFlagsThatNeverMatch() {
        for (Flag<?> flag : CoreFlags.all()) {
            if (!flag.isRegistered()) {
                FlagRegistry.get().register(core, flag);
            }
        }
        try {
            List<String> logged = Logs.capture(() -> HyActionSystems.declareSupport("Hytale"));

            for (String key : Arrays.asList("deny-block-break-fake-players", "deny-block-place-fake-players")) {
                assertTrue(logged.stream().anyMatch(line -> line.contains("Flag evernifecore:" + key + " never matches")), key + " in " + logged);
            }
            assertTrue(logged.stream().anyMatch(line -> line.contains("Flag evernifecore:track-changes acts in part") && line.contains("explosion")),
                    "track-changes keeps breaks and places here, never an explosion: " + logged);
        } finally {
            ActionSupport.declare("an undeclared platform", null, null, true, null);
            FlagRegistry.get().setMissingCapabilities(Collections.emptyList());
        }
    }

    /** {@code miner} hitting a quest giver with his hand: what the DAMAGE System builds for it. */
    private DamageContext punch(Damage damage) {
        return new DamageContext(EventSource.DAMAGE, HyActionContexts.position(WORLD, new Vector3d(5, 5, 5)), HyActionContexts.actor(miner, null, null, null),
                damage, HyActionContexts.actor(null, "Quest_Giver", null, null, NpcRef.of("hytale", "giver")));
    }

    @Test
    void aPunchOnAnNpcIsItsLeftClickAndAUseItsRightOne() {
        assertSame(EntityContext.ClickType.RIGHT, HyActionSystems.npcClickOf(InteractionType.Use));
        assertSame(EntityContext.ClickType.LEFT, HyActionSystems.npcClickOf(InteractionType.Primary));
        assertNull(HyActionSystems.npcClickOf(InteractionType.Secondary));

        List<InteractNpcActionEvent.Post> clicks = new ArrayList<>();
        ECEventSubscription<InteractNpcActionEvent.Post> post = ECEventBus.global().subscribe(InteractNpcActionEvent.Post.class, clicks::add);
        try {
            Damage damage = new Damage(Damage.NULL_SOURCE, 0, 1f);
            assertFalse(HyActionSystems.punchNpc(WORLD, damage, () -> punch(damage)));

            assertEquals(1, clicks.size());
            assertSame(EntityContext.ClickType.LEFT, clicks.get(0).getContext().getClickType());
            assertEquals(NpcRef.of("hytale", "giver"), clicks.get(0).getContext().getEntity().getNpc());
            assertSame(miner, clicks.get(0).getPlayer());
        } finally {
            post.unsubscribe();
        }
    }

    @Test
    void aCancelledPunchPreCancelsTheHitAndAHitByNobodyIsNoClick() {
        ECEventSubscription<InteractNpcActionEvent.Pre> veto = ECEventBus.global().subscribe(InteractNpcActionEvent.Pre.class, pre -> pre.setCancelled(true));
        try {
            Damage punched = new Damage(Damage.NULL_SOURCE, 0, 1f);
            assertTrue(HyActionSystems.punchNpc(WORLD, punched, () -> punch(punched)));
            assertTrue(punched.isCancelled(), "a denied left click lands no hit");

            Damage fell = new Damage(Damage.NULL_SOURCE, 0, 1f);
            assertFalse(HyActionSystems.punchNpc(WORLD, fell, () -> hitAt(5, fell)), "the victim is no NPC and nobody hit him");
            assertFalse(fell.isCancelled());
        } finally {
            veto.unsubscribe();
        }
    }

    private static InteractionChainStartEvent chainStart(InteractionChainData data) {
        return new InteractionChainStartEvent(new InteractionChain(InteractionType.Secondary, InteractionContext.withoutEntity(), data, new RootInteraction(), null, false));
    }

    @Test
    void anInteractionChainStartNamesTheBlockTheClientClickedNotTheMetaTheServerFillsLater() {
        InteractionChainData data = new InteractionChainData();
        data.blockPosition = new BlockPosition(3, 4, 5);
        InteractionChainStartEvent event = chainStart(data);

        assertNull(event.getContext().getTargetBlock(), "the server fills the target meta only after the event");
        assertEquals(new Vector3i(3, 4, 5), HyActionSystems.chainTargetBlock(event));
    }

    @Test
    void aChainStartedWithNoTargetNamesNeitherABlockNorAnEntity() {
        InteractionChainStartEvent event = chainStart(new InteractionChainData());   //entityId -1, no block: a chain the server started

        assertNull(HyActionSystems.chainTargetBlock(event));
        assertNull(HyActionSystems.chainTargetEntity(event, null), "a negative id never reaches the entity store");
    }

    @Test
    void anEntityIdTheWorldNoLongerKnowsIsNoTarget() {
        InteractionChainData data = new InteractionChainData();
        data.entityId = 42;
        data.blockPosition = new BlockPosition(3, 4, 5);
        InteractionChainStartEvent event = chainStart(data);

        assertNull(HyActionSystems.chainTargetEntity(event, new EntityStore(null)), "the entity left between the click and the server");
        assertEquals(new Vector3i(3, 4, 5), HyActionSystems.chainTargetBlock(event));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEntityIdStillMappedToARemovedEntityIsNoTarget() throws ReflectiveOperationException {
        EntityStore entities = new EntityStore(null);
        Ref<EntityStore> removed = new Ref<>(null);   //no index: what a Ref is once its entity left the store
        assertFalse(removed.isValid());
        Field byNetworkId = EntityStore.class.getDeclaredField("networkIdToRef");
        byNetworkId.setAccessible(true);
        ((Int2ReferenceMap<Ref<EntityStore>>) byNetworkId.get(entities)).put(42, removed);
        InteractionChainData data = new InteractionChainData();
        data.entityId = 42;
        InteractionChainStartEvent event = chainStart(data);

        assertSame(removed, entities.getRefFromNetworkId(42), "the store still answers the id");
        assertNull(HyActionSystems.chainTargetEntity(event, entities), "a removed entity is no target");
    }
}
