package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.SpawnContext;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyMonsterSpawnFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyPvpFlag;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            super(FlagKey.of(owner, name), EnumSet.of(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
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

    private ECPluginData owner;
    private RegionLookup lookup;
    private Region region;
    private ManualClock clock;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        lookup = TestRegions.newLookup();
        region = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, region);
        clock = TestRegions.manualClock();
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(owner);
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
    }

    private CountingFlag registered(String name) {
        CountingFlag flag = new CountingFlag(owner, name);
        FlagRegistry.get().register(flag);
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
    void aZeroIntervalFiresEveryTime() {
        CountingFlag flag = registered("unpaced");
        flag.getSettings().setTriggerInterval(Duration.ZERO);
        region.setFlag(flag, Tristate.TRUE);
        TestFPlayerSender player = new TestFPlayerSender("breaker");

        lookup.decide(breakAt(5, player));
        lookup.decide(breakAt(5, player));

        assertEquals(2, flag.applied.get());
    }

    @Test
    void anActionWithNoPlayerIsPacedAsOne() {
        CountingFlag flag = registered("machines");
        region.setFlag(flag, Tristate.TRUE);
        BlockContext noPlayer = new BlockContext(EventSource.BREAK, WorldLocPos.of(5, 5, 5, WORLD), null, null, "minecraft:stone", null, false);

        assertEquals(Decision.DENY, lookup.decide(noPlayer));
        lookup.decide(noPlayer);

        assertEquals(1, flag.applied.get(), "every action with no player shares one pace");
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
        DenyPvpFlag denyPvp = new DenyPvpFlag(owner);
        FlagRegistry.get().register(denyPvp);
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
        DenyMonsterSpawnFlag denyMonsters = new DenyMonsterSpawnFlag(owner);
        FlagRegistry.get().register(denyMonsters);
        region.setFlag(denyMonsters, Tristate.TRUE);
        WorldLocPos at = WorldLocPos.of(5, 5, 5, WORLD);

        assertEquals(Decision.PASS, lookup.decide(new SpawnContext(at, null, SpawnContext.Kind.ANIMAL, "minecraft:cow", "NATURAL")));
        assertEquals(Decision.DENY, lookup.decide(new SpawnContext(at, null, SpawnContext.Kind.MONSTER, "minecraft:zombie", "NATURAL")));
    }

    @Test
    void aContextOfAnotherKindPassesInsteadOfThrowing() {
        DenyPvpFlag denyPvp = new DenyPvpFlag(owner);

        assertEquals(Decision.PASS, denyPvp.decide(breakAt(5, new TestFPlayerSender("breaker")), Tristate.TRUE));
    }
}
