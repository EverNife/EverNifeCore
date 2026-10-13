package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenyFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractBlocksFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractEntitiesFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyInteractFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyMonsterSpawnFlag;
import br.com.finalcraft.evernifecore.region.flag.core.DenyPvpFlag;
import br.com.finalcraft.evernifecore.region.flag.core.InteractSettings;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One protected action asked of the flags where it happens: first denial wins, consequences at their pace. */
@ECoreTest
class RegionLookupDecideTest {

    private static final String OWNER = "RegionDecideOwner";
    private static final String WORLD = "world";

    /** Denies on TRUE and counts what it was asked. */
    private static final class CountingFlag extends TristateFlag {
        final AtomicInteger decided = new AtomicInteger();
        final AtomicInteger applied = new AtomicInteger();

        CountingFlag(ECPluginData owner, String name) {
            this(owner, name, EventSource.BREAK);
        }

        CountingFlag(ECPluginData owner, String name, EventSource source) {
            super(FlagKey.of(owner, name), Collections.singleton(source), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            decided.incrementAndGet();
            return value == Tristate.TRUE ? Decision.DENY : Decision.PASS;
        }

        @Override
        public void onApplied(ActionContext context, Tristate value) {
            applied.incrementAndGet();
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData core;
    private ECPluginData owner;
    private RegionLookup lookup;
    private Region region;
    private ManualClock clock;

    @BeforeEach
    void setUp() {
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        lookup = TestRegions.newLookup();
        region = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, region);
        clock = TestRegions.manualClock();
    }

    @AfterEach
    void tearDown() {
        TestRegions.unindex(RegionLookup.get(), region);
        FlagRegistry.get().unregisterAll(owner);
        FlagRegistry.get().unregisterAll(core);
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
        Plugins.forget(Plugins.CORE);
    }

    private CountingFlag registered(String name) {
        CountingFlag flag = new CountingFlag(owner, name);
        FlagRegistry.get().register(owner, flag);
        return flag;
    }

    private static BlockContext breakAt(double x, TestFPlayerSender player) {
        return new BlockContext(EventSource.BREAK, WorldLocPos.of(x, 5, 5, WORLD), player, null, "minecraft:stone", null, false);
    }

    @Test
    void theFirstDenialWinsAndTheFlagsAfterItAreNotAsked() {
        CountingFlag first = registered("first");
        CountingFlag second = registered("second");
        region.setFlag(first, Tristate.TRUE);
        region.setFlag(second, Tristate.TRUE);

        assertEquals(Decision.DENY, lookup.decide(breakAt(5, new TestFPlayerSender("breaker"))));

        assertEquals(1, first.decided.get() + second.decided.get(), "the denial of one flag ends the question");
    }

    @Test
    void onAppliedRunsOncePerIntervalAndAgainAfterIt() {
        CountingFlag flag = registered("paced");
        flag.getSettings().setTriggerInterval(Duration.ofSeconds(2));
        region.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        lookup.decide(breakAt(5, player));
        lookup.decide(breakAt(5, player));
        assertEquals(1, flag.applied.get(), "twice inside the 2 s window fires once");

        clock.advance(Duration.ofSeconds(2));
        lookup.decide(breakAt(5, player));
        assertEquals(2, flag.applied.get());
    }

    @Test
    void theDefaultIntervalFiresOnEveryDenial() {
        CountingFlag flag = registered("unpaced");
        region.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        lookup.decide(breakAt(5, player));
        lookup.decide(breakAt(5, player));

        assertEquals(2, flag.applied.get());
    }

    @Test
    void anActionWithNoPlayerIsPacedAsOne() {
        CountingFlag flag = registered("machines");
        flag.getSettings().setTriggerInterval(Duration.ofSeconds(2));
        region.setFlag(flag, Tristate.TRUE);
        BlockContext noPlayer = new BlockContext(EventSource.BREAK, WorldLocPos.of(5, 5, 5, WORLD), null, null, "minecraft:stone", null, false);

        assertEquals(Decision.DENY, lookup.decide(noPlayer));
        lookup.decide(noPlayer);

        assertEquals(1, flag.applied.get(), "every action with no player shares one pace");
    }

    @Test
    void aHitWithNoPlayerBehindItIsPacedPerVictim() {
        CountingFlag flag = new CountingFlag(owner, "mob-hits", EventSource.DAMAGE);
        FlagRegistry.get().register(owner, flag);
        flag.getSettings().setTriggerInterval(Duration.ofSeconds(2));
        region.setFlag(flag, Tristate.TRUE);
        WorldLocPos at = WorldLocPos.of(5, 5, 5, WORLD);
        TestFPlayerSender victimA = new TestFPlayerSender("victimA");
        TestFPlayerSender victimB = new TestFPlayerSender("victimB");

        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.PLAYER, victimA, "minecraft:player"));
        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.PLAYER, victimA, "minecraft:player"));
        assertEquals(1, flag.applied.get(), "one victim, one pace");

        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.PLAYER, victimB, "minecraft:player"));
        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.PLAYER, victimB, "minecraft:player"));
        assertEquals(2, flag.applied.get(), "another victim has a pace of its own");

        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.ANIMAL, null, "minecraft:cow"));
        lookup.decide(new DamageContext(at, null, null, DamageContext.Kind.ANIMAL, null, "minecraft:cow"));
        assertEquals(3, flag.applied.get(), "no player on either side shares one pace");
    }

    @Test
    void aPointOutsideEveryRegionPassesWithoutAskingAnyFlag() {
        CountingFlag flag = registered("outside");
        region.setFlag(flag, Tristate.TRUE);

        assertEquals(Decision.PASS, lookup.decide(breakAt(50, new TestFPlayerSender("far"))));

        assertEquals(0, flag.decided.get());
    }

    @Test
    void denyPvpSpeaksAboutPlayersOnly() {
        DenyPvpFlag denyPvp = new DenyPvpFlag();
        FlagRegistry.get().register(core, denyPvp);
        region.setFlag(denyPvp, Tristate.TRUE);
        TestFPlayerSender attacker = new TestFPlayerSender("attacker");
        WorldLocPos victimAt = WorldLocPos.of(5, 5, 5, WORLD);

        assertEquals(Decision.PASS, lookup.decide(new DamageContext(victimAt, attacker, null, DamageContext.Kind.ANIMAL, null, "minecraft:cow")));
        assertEquals(Decision.DENY, lookup.decide(new DamageContext(victimAt, attacker, null, DamageContext.Kind.PLAYER,
                new TestFPlayerSender("victim"), "minecraft:player")));
        assertEquals(Decision.PASS, lookup.decide(new DamageContext(victimAt, null, null, DamageContext.Kind.PLAYER,
                new TestFPlayerSender("victim"), "minecraft:player")), "a zombie hitting a player is no PvP");
    }

    @Test
    void denyMonsterSpawnLetsAnimalsSpawn() {
        DenyMonsterSpawnFlag denyMonsters = new DenyMonsterSpawnFlag();
        FlagRegistry.get().register(core, denyMonsters);
        region.setFlag(denyMonsters, Tristate.TRUE);
        WorldLocPos at = WorldLocPos.of(5, 5, 5, WORLD);

        assertEquals(Decision.PASS, lookup.decide(new SpawnContext(at, null, SpawnContext.Kind.ANIMAL, "minecraft:cow", "NATURAL")));
        assertEquals(Decision.DENY, lookup.decide(new SpawnContext(at, null, SpawnContext.Kind.MONSTER, "minecraft:zombie", "NATURAL")));
    }

    @Test
    void aContextOfAnotherKindPassesInsteadOfThrowing() {
        DenyPvpFlag denyPvp = new DenyPvpFlag();

        assertEquals(Decision.PASS, denyPvp.decide(breakAt(5, new TestFPlayerSender("breaker")), Tristate.TRUE));
    }

    // ------------------------------------------------------------------
    // deny-message: what a DenyFlag tells the player it denied
    // ------------------------------------------------------------------

    /** Counts the effect a DenyFlag runs after a denial. */
    private static final class CountingDenyFlag extends DenyFlag {
        final AtomicInteger effects = new AtomicInteger();

        CountingDenyFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "counted-deny"), EventSource.BREAK);
        }

        @Override
        public void onApplied(ActionContext context, Tristate value) {
            effects.incrementAndGet();
        }
    }

    /** The region also in the global lookup, where a DenyFlag reads the deny-message at the point of the action. */
    private void sayingDenyMessage(String message) {
        FlagRegistry.get().register(core, CoreFlags.DENY_MESSAGE);
        TestRegions.index(RegionLookup.get(), region);
        region.setFlag(CoreFlags.DENY_MESSAGE, message);
    }

    @Test
    void aDeniedActionTellsThePlayerTheRegionsDenyMessage() {
        FlagRegistry.get().register(core, CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        sayingDenyMessage("stay out");
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        assertEquals(Decision.DENY, lookup.decide(breakAt(5, player)));

        assertEquals(Arrays.asList("stay out"), player.getMessages());
    }

    @Test
    void anAllowedActionSendsNoDenyMessage() {
        FlagRegistry.get().register(core, CoreFlags.DENY_BLOCK_BREAK);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.FALSE);
        sayingDenyMessage("stay out");
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        assertEquals(Decision.PASS, lookup.decide(breakAt(5, player)));

        assertTrue(player.getMessages().isEmpty(), player.getMessages().toString());
    }

    @Test
    void anInteractLetThroughByItsSettingsSendsNoDenyMessage() {
        Interact interact = new Interact(core);
        region.setFlag(interact.any, Tristate.TRUE);
        InteractSettings settings = new InteractSettings();
        settings.setExcept(Arrays.asList("minecraft:chest"));
        region.setFlagSettings(interact.any, settings);
        sayingDenyMessage("hands off");
        TestFPlayerSender player = new TestFPlayerSender("user");

        assertEquals(Decision.PASS, lookup.decide(use("minecraft:chest", player)));
        assertTrue(player.getMessages().isEmpty(), "TRUE with the chest excepted is no denial: " + player.getMessages());

        assertEquals(Decision.DENY, lookup.decide(use("minecraft:furnace", player)));
        assertEquals(Arrays.asList("hands off"), player.getMessages());
    }

    @Test
    void aDenyFlagsEffectRunsOnlyAfterADenial() {
        CountingDenyFlag flag = new CountingDenyFlag(owner);
        FlagRegistry.get().register(owner, flag);
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        region.setFlag(flag, Tristate.TRUE);
        assertEquals(Decision.DENY, lookup.decide(breakAt(5, player)));
        assertEquals(1, flag.effects.get());

        region.setFlag(flag, Tristate.FALSE);
        assertEquals(Decision.PASS, lookup.decide(breakAt(5, player)));
        assertEquals(1, flag.effects.get(), "FALSE applied, but denied nothing");
    }

    // ------------------------------------------------------------------
    // a plugin's own action on its own event source, decided like a core one
    // ------------------------------------------------------------------

    /** The hit of a duel: an action only the duel plugin raises. */
    private static final class DuelHitContext extends ActionContext {
        DuelHitContext(EventSource source, WorldLocPos at, FPlayer hitter) {
            super(source, at, hitter, null);
        }
    }

    private static final class DenyDuelHitFlag extends DenyFlag {
        DenyDuelHitFlag(ECPluginData owner, EventSource source) {
            super(FlagKey.of(owner, "deny-duel-hit"), source);
        }

        @Override
        public boolean applies(ActionContext context) {
            return context instanceof DuelHitContext;
        }
    }

    @Test
    void aPluginFlagOnItsOwnSourceDeniesItsOwnContext() {
        EventSource duelHit = EventSource.register(owner, "duel-hit");
        try {
            DenyDuelHitFlag denyDuelHit = new DenyDuelHitFlag(owner, duelHit);
            FlagRegistry.get().register(owner, denyDuelHit);
            CountingFlag breakFlag = registered("break-alongside");
            region.setFlag(denyDuelHit, Tristate.TRUE);
            region.setFlag(breakFlag, Tristate.TRUE);
            TestFPlayerSender hitter = new TestFPlayerSender("hitter");

            assertEquals(Decision.DENY, lookup.decide(new DuelHitContext(duelHit, WorldLocPos.of(5, 5, 5, WORLD), hitter)));
            assertEquals(Decision.PASS, lookup.decide(new DuelHitContext(duelHit, WorldLocPos.of(50, 5, 5, WORLD), hitter)));

            assertEquals(0, breakFlag.decided.get(), "a flag of another source is not asked about a duel hit");
        } finally {
            EventSource.unregisterAll(owner);
        }
    }

    // ------------------------------------------------------------------
    // deny-interact and its refinements: the most specific flag defined decides, no listener combines them
    // ------------------------------------------------------------------

    private static final class Interact {
        final DenyInteractFlag any;
        final DenyInteractBlocksFlag blocks;
        final DenyInteractEntitiesFlag entities;

        Interact(ECPluginData core) {
            any = new DenyInteractFlag();
            blocks = new DenyInteractBlocksFlag(any);
            entities = new DenyInteractEntitiesFlag(any);
            FlagRegistry.get().register(core, any);
            FlagRegistry.get().register(core, blocks);
            FlagRegistry.get().register(core, entities);
        }
    }

    private static BlockContext use(String blockId, TestFPlayerSender player) {
        return new BlockContext(EventSource.INTERACT, WorldLocPos.of(5, 5, 5, WORLD), player, null, blockId, null, false);
    }

    private static EntityContext useEntity(String typeId, TestFPlayerSender player) {
        return new EntityContext(WorldLocPos.of(5, 5, 5, WORLD), player, null, typeId, null);
    }

    @Test
    void denyInteractCoversAnEntityThroughItsUndefinedRefinement() {
        Interact interact = new Interact(core);
        region.setFlag(interact.any, Tristate.TRUE);

        assertEquals(Decision.DENY, lookup.decide(useEntity("minecraft:villager", new TestFPlayerSender("trader"))));
    }

    @Test
    void theMostSpecificDefinedFlagDecides() {
        Interact interact = new Interact(core);
        region.setFlag(interact.any, Tristate.TRUE);
        region.setFlag(interact.blocks, Tristate.FALSE);
        TestFPlayerSender player = new TestFPlayerSender("user");

        assertEquals(Decision.PASS, lookup.decide(use("minecraft:chest", player)), "blocks = FALSE speaks for blocks");
        assertEquals(Decision.DENY, lookup.decide(useEntity("minecraft:villager", player)), "entities still read deny-interact");
    }

    @Test
    void denyInteractBlocksSaysNothingAboutEntities() {
        Interact interact = new Interact(core);
        region.setFlag(interact.blocks, Tristate.TRUE);

        assertEquals(Decision.PASS, lookup.decide(useEntity("minecraft:villager", new TestFPlayerSender("trader"))));
    }

    @Test
    void denyInteractEntitiesSaysNothingAboutBlocks() {
        Interact interact = new Interact(core);
        region.setFlag(interact.entities, Tristate.TRUE);

        assertEquals(Decision.PASS, lookup.decide(use("minecraft:chest", new TestFPlayerSender("user"))));
    }

    @Test
    void onlyNarrowsTheDenialToTheListedIds() {
        Interact interact = new Interact(core);
        region.setFlag(interact.blocks, Tristate.TRUE);
        InteractSettings settings = new InteractSettings();
        settings.setOnly(Arrays.asList("minecraft:*chest*"));
        region.setFlagSettings(interact.blocks, settings);
        TestFPlayerSender player = new TestFPlayerSender("user");

        assertEquals(Decision.DENY, lookup.decide(use("minecraft:chest", player)));
        assertEquals(Decision.DENY, lookup.decide(use("minecraft:trapped_chest", player)));
        assertEquals(Decision.PASS, lookup.decide(use("minecraft:furnace", player)));
    }

    @Test
    void exceptLeavesTheListedIdsFree() {
        Interact interact = new Interact(core);
        region.setFlag(interact.any, Tristate.TRUE);
        InteractSettings settings = new InteractSettings();
        settings.setExcept(Arrays.asList("minecraft:chest"));
        region.setFlagSettings(interact.any, settings);
        TestFPlayerSender player = new TestFPlayerSender("user");

        assertEquals(Decision.PASS, lookup.decide(use("minecraft:chest", player)), "the parent's settings travel with its value");
        assertEquals(Decision.DENY, lookup.decide(use("minecraft:furnace", player)));
    }

    @Test
    void aBypassOfTheRefinementLetsThePlayerThroughWhateverTheParentSays() {
        Interact interact = new Interact(core);
        region.setFlag(interact.any, Tristate.TRUE);
        TestFPlayerSender bypassing = new TestFPlayerSender("admin")
                .grant("evernifecore.region.bypass." + interact.blocks.getKey().getOwner() + ".deny-interact-blocks");

        assertEquals(Decision.PASS, lookup.decide(use("minecraft:chest", bypassing)));
        assertEquals(Decision.DENY, lookup.decide(use("minecraft:chest", new TestFPlayerSender("user"))));
    }
}
