package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.flag.FlagMode;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.Resolution;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.AmountEveryFlag;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestActorHolder;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
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

/**
 * The owner's cases for amounts over time joined across holders: a party's heal adds to the region's, heal and damage of
 * one interval are one net change, a written priority with {@code set} or a {@code lock} replaces what is under it.
 * Each tick runs the ticking flags as the region ticker does, after the clock moved one second.
 */
@ECoreTest
class AmountEveryNetTest {

    private static final String WORLD = "world";
    private static final WorldLocPos INSIDE = WorldLocPos.of(5.5, 5, 5.5, WORLD);

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private TestPlayerState.State state;
    private TestFPlayerSender alice;
    private TestActorHolder party;
    private Region spa;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 5.5, 5, 5.5);
        world = Platforms.lenient().onlinePlayers(Collections.<FPlayer>singletonList(alice)).install();
        state = world.platform().getTestPlayerState().of(alice);
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Spas", tempDir.resolve("spas"));
        party = regions.actorHolder("party", alice.getUniqueId());
        spa = regions.cuboid("spa", WORLD, 0, 0, 0, 10, 10, 10);
    }

    @AfterEach
    void close() {
        RegionMovementTracker.get().onQuit(alice);
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    private static List<AmountEvery> every(int amount, int seconds) {
        return Collections.singletonList(AmountEvery.of(amount, Duration.ofSeconds(seconds)));
    }

    private void join(double health, double maxHealth) {
        state.health = health;
        state.maxHealth = maxHealth;
        RegionMovementTracker.get().onJoin(alice, INSIDE);
    }

    /** One second later, every ticking amount flag that resolves for alice ticks once - as the region ticker runs them. */
    private void tick() {
        regions.advance(Duration.ofSeconds(1));
        RegionPlayerSession session = RegionLookup.get().getSession(alice);
        for (AmountEveryFlag flag : Arrays.asList(CoreFlags.HEAL, CoreFlags.FEED, CoreFlags.DAMAGE_OVER_TIME, CoreFlags.HUNGER_OVER_TIME)) {
            Resolution<List<AmountEvery>> resolution = RegionLookup.get().resolution(flag, session.getCurrent(), alice);
            if (resolution.getSource() != null) {
                flag.onTick(new SessionContext(alice, resolution.getRegion(), session), resolution.getValue());
            }
        }
    }

    @Test
    void aPartyHealOf2AndARegionHealOf10Give12PerTick() {
        party.setFlag(CoreFlags.HEAL, every(2, 1));
        spa.setFlag(CoreFlags.HEAL, every(10, 1));
        join(50, 100);
        tick();
        assertEquals(62.0, state.health);
        tick();
        assertEquals(74.0, state.health);
    }

    @Test
    void invincibleFalseDefinedInTheRegionDropsThePartysInvincibleTrueSoDamagePasses() {
        party.setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE);
        spa.setFlag(CoreFlags.DAMAGE_OVER_TIME, every(4, 1));
        join(20, 20);
        tick();
        assertEquals(20.0, state.health, "the party's invincible blocks the damage");

        spa.setFlag(CoreFlags.INVINCIBLE, Tristate.FALSE);
        assertEquals(Tristate.FALSE, RegionLookup.get().resolve(CoreFlags.INVINCIBLE, alice), "a defined FALSE is an answer");
        tick();
        assertEquals(16.0, state.health, "the damage passes");
    }

    @Test
    void heal100AndDamage2OfOneIntervalAreOneNetChangeOf98() {
        spa.setFlag(CoreFlags.HEAL, every(100, 1));
        spa.setFlag(CoreFlags.DAMAGE_OVER_TIME, every(2, 1));
        join(10, 200);
        tick();
        assertEquals(108.0, state.health);

        state.health = 100;
        state.maxHealth = 150;
        tick();
        assertEquals(150.0, state.health, "one change of +98 capped at full; a heal then a damage would end at 148");
    }

    @Test
    void aLegendaryItemHeal50WithPriority10AndSetReplacesTheRegionsSeven() {
        party.setFlag(CoreFlags.HEAL, FlagValue.permanent(every(50, 1)).withPriority(10).withMode(FlagMode.SET));
        spa.setFlag(CoreFlags.HEAL, every(7, 1));
        join(100, 200);
        tick();
        assertEquals(150.0, state.health);
    }

    @Test
    void aCryptHealOf0WithSetAndLockSilencesAPartySevenWithPriority9999() {
        spa.setFlag(CoreFlags.HEAL, FlagValue.permanent(every(0, 1)).withMode(FlagMode.SET).withLock(true));
        party.setFlag(CoreFlags.HEAL, FlagValue.permanent(every(7, 1)).withPriority(9999));
        join(50, 100);
        assertEquals(Collections.singletonList(AmountEvery.of(0, Duration.ofSeconds(1))), RegionLookup.get().resolve(CoreFlags.HEAL, alice));
        tick();
        tick();
        assertEquals(50.0, state.health);
    }

    @Test
    void sevenEverySecondAndSevenEveryTwoRunEachOnItsOwnTick() {
        party.setFlag(CoreFlags.HEAL, every(7, 1));
        spa.setFlag(CoreFlags.HEAL, every(7, 2));
        join(10, 100);
        tick();
        assertEquals(17.0, state.health, "only the 1s entry is due");
        tick();
        assertEquals(31.0, state.health, "both are due: 7 + 7");
    }
}
