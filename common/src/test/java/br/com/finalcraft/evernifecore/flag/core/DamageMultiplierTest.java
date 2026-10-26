package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.ProtectionFlagGate;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code damage-multiplier}: the layers multiply within the settings' bounds, and the factor rides the hit for the listener to apply. */
@ECoreTest
class DamageMultiplierTest {

    private static final String WORLD = "world";
    private static final WorldLocPos AT = WorldLocPos.of(5.5, 5, 5.5, WORLD);

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private TestFPlayerSender alice;
    private TestFPlayerSender bob;
    private Region arena;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 5.5, 5, 5.5);
        bob = Senders.player("bob").at(WORLD, 6.5, 5, 5.5);
        world = Platforms.lenient().onlinePlayers(Arrays.<FPlayer>asList(alice, bob)).install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Arenas", tempDir.resolve("arenas"));
        arena = regions.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    /** Bob hits Alice where she stands. */
    private DamageContext bobHitsAlice() {
        return new DamageContext(EventSource.DAMAGE, AT, Actors.player(bob), null, Actors.player(alice));
    }

    @Test
    void theRegionsTwoAndThePartysThreeMultiplyToSix() {
        arena.setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        regions.actorHolder("party", alice.getUniqueId()).setFlag(CoreFlags.DAMAGE_MULTIPLIER, 3.0);

        assertEquals(Double.valueOf(6.0), regions.getLookup().resolve(CoreFlags.DAMAGE_MULTIPLIER, alice));
        assertEquals("product-capped", regions.explain(CoreFlags.DAMAGE_MULTIPLIER, alice).getResolver().getId());
    }

    @Test
    void theCapOfTheSettingsInEffectBoundsTheProduct() {
        arena.setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        regions.actorHolder("party", alice.getUniqueId()).setFlag(CoreFlags.DAMAGE_MULTIPLIER, 3.0);
        DamageMultiplierSettings capped = new DamageMultiplierSettings();
        capped.setCap(4);
        arena.setFlagSettings(CoreFlags.DAMAGE_MULTIPLIER, capped);

        assertEquals(Double.valueOf(4.0), regions.getLookup().resolve(CoreFlags.DAMAGE_MULTIPLIER, alice));
    }

    @Test
    void theFactorRidesTheHitAndTheFlagNeverDenies() {
        arena.setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        DamageContext hit = bobHitsAlice();

        assertEquals(Decision.PASS, ProtectionFlagGate.decide(regions.getLookup(), hit));
        assertEquals(2.0, hit.getDamageMultiplier());
        CoreFlags.DAMAGE_MULTIPLIER.decide(hit, 5.0);
        assertEquals(2.0, hit.getDamageMultiplier(), "asked again about the same hit, the flag keeps its first factor");
    }

    @Test
    void anInvinciblePlayerIsDeniedSoNothingIsMultiplied() {
        arena.setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        arena.setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE);

        assertEquals(Decision.DENY, ProtectionFlagGate.decide(regions.getLookup(), bobHitsAlice()));
        assertEquals(Collections.singleton("damage:player-victim"), CoreFlags.DAMAGE_MULTIPLIER.getChannels());
    }
}
