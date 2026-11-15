package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.PlatformNotConfiguredException;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The booster engine: a domain's permission source, a plugin's manager, the joins, the sweep and the multiplier a domain resolves. */
class BoostersTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private BoosterDomain gold;
    private BoosterDomain points;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        gold = BoosterDomain.of(shop.getOwner(), "gold").permission("fakeshop.booster.gold", 1.25, 1.5, 2).register();
        points = BoosterDomain.of(shop.getOwner(), "points").register();
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private ExecutionContext ctx() {
        return shop.context(alex).build();
    }

    private BigDecimal multiplier(BoosterDomain domain) {
        return domain.resolve(ctx()).getMultiplier();
    }

    private static void assertMultiplier(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected x" + expected + ", got x" + actual);
    }

    @Test
    void anUntaughtPlatformRefusalReachesTheCallerWhileABrokenSourceIsSkipped() {
        Boosters.addSource(shop.getOwner(), new BoosterSource() {
            @Override
            public String getId() {
                return "fakeshop:broken";
            }

            @Override
            public Collection<Booster> boostersFor(ExecutionContext ctx, BoosterDomain domain) {
                throw new IllegalStateException("broken on purpose");
            }
        });
        assertMultiplier("1", multiplier(points));

        //this strict world never taught getOfflinePermissions; an offline player's permission booster asks it
        PlatformNotConfiguredException refused = assertThrows(PlatformNotConfiguredException.class, () -> gold.resolve(UUID.randomUUID()));
        assertTrue(refused.getMessage().contains("Platforms.strict().offlinePermissions()"), refused.getMessage());
    }

    @Test
    void aPermissionAndAManagerBoosterSumTheirBonusesUpToTheDeclaredCap() {
        BoosterDomain capped = BoosterDomain.of(shop.getOwner(), "capped").join(BoosterJoin.sumBonus().cap(new BigDecimal("1.6")))
                .permission("fakeshop.booster.capped", 1.5).register();
        alex.grant("fakeshop.booster.gold.1.5").grant("fakeshop.booster.capped.1.5");
        for (BoosterDomain domain : new BoosterDomain[]{gold, capped}) {
            shop.boosters().grant(Booster.on(domain).bonus(0.25).forOwner(ProgressOwner.player(alex.getUniqueId()))
                    .from(shop.caller("event/weekend")).build()).join();
        }

        BoosterResult result = gold.resolve(ctx());
        assertMultiplier("1.75", result.getMultiplier());
        assertEquals(2, result.getParcels().size());
        assertTrue(result.explain().getText().contains(":permission"), result.explain().getText());
        assertMultiplier("1.6", multiplier(capped));
    }

    @Test
    void thePermissionSourceKeepsTheLargestListedFactorAndNeverSeesAnUnlistedOne() {
        alex.grant("fakeshop.booster.gold.1.25").grant("fakeshop.booster.gold.2").grant("fakeshop.booster.gold.2.5");

        assertMultiplier("2", multiplier(gold));
        assertMultiplier("1", multiplier(points));
        assertMultiplier("1", gold.resolve(shop.context().build()).getMultiplier());
    }

    @Test
    void anOpWithoutTheNodeGetsNoBoosterAndOneWithItGetsTheFactorTheNodeNames() {
        alex.op(true);
        assertMultiplier("1", multiplier(gold));

        alex.grant("fakeshop.booster.gold.1.5");
        assertMultiplier("1.5", multiplier(gold));
    }

    @Test
    void sumOfBonusesMaxAndProductJoinTheSameBoostersDifferently() {
        List<Booster> boosters = Arrays.asList(Booster.on(gold).factor(2).build(), Booster.on(gold).factor(1.5).build());

        assertMultiplier("2.5", BoosterJoin.sumBonus().join(boosters));
        assertMultiplier("2", BoosterJoin.max().join(boosters));
        assertMultiplier("3", BoosterJoin.product().join(boosters));
        assertMultiplier("2.75", BoosterJoin.product().cap(new BigDecimal("2.75")).join(boosters));
        assertMultiplier("1", BoosterJoin.product().join(Collections.<Booster>emptyList()));
    }

    @Test
    void anExpiredBoosterStopsCountingAtOnceAndTheSweepDropsItFromTheStorage() {
        BoosterManager manager = BoosterManager.of(shop.getOwner(), shop.getStorage());
        Booster granted = manager.grant(Booster.on(gold).factor(2).forOwner(ProgressOwner.player(alex.getUniqueId()))
                .lasting(Duration.ofMinutes(1)).build()).join();
        assertMultiplier("2", multiplier(gold));

        shop.advance(Duration.ofSeconds(61));
        assertMultiplier("1", multiplier(gold));
        assertEquals(1, manager.getAll().size(), "nothing ran yet: only the sweep drops it");

        shop.runDue();
        assertTrue(manager.getAll().isEmpty());
        BoosterManager.closeOwnedBy(shop.getOwner());
        assertTrue(BoosterManager.of(shop.getOwner(), shop.getStorage()).getAll().isEmpty(), "gone from the storage, not only from memory: " + granted);
    }

    @Test
    void aStoredBoosterComesBackWithItsIdOwnerAndOriginWhenTheManagerReopens() {
        BoosterManager manager = BoosterManager.of(shop.getOwner(), shop.getStorage());
        Booster granted = manager.grant(Booster.on(points).bonus(0.5).forOwner(ProgressOwner.global())
                .from(shop.caller("store/x2")).build()).join();
        List<BoosterGrantedEvent> events = shop.recorded(BoosterGrantedEvent.class);
        manager.grant(Booster.on(gold).factor(3).build()).join();
        assertEquals(1, events.size());

        BoosterManager.closeOwnedBy(shop.getOwner());
        assertMultiplier("1", multiplier(points));
        BoosterManager reopened = BoosterManager.of(shop.getOwner(), shop.getStorage());

        assertTrue(reopened.getActive(ProgressOwner.global()).contains(granted), reopened.getAll().toString());
        assertMultiplier("1.5", multiplier(points));
        assertTrue(reopened.revoke(granted.getId()).join());
        assertMultiplier("1", multiplier(points));
    }

    @Test
    void aBoosterIsAFactorOrABonusAndNeverTurnsAGainIntoALoss() {
        IllegalArgumentException both = assertThrows(IllegalArgumentException.class, () -> Booster.on(gold).factor(2).bonus(1).build());
        assertTrue(both.getMessage().contains("exactly one"), both.getMessage());
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> Booster.on(gold).bonus(-1.5).build());
        assertTrue(negative.getMessage().contains("loss"), negative.getMessage());
    }

    @Test
    void aBoosterRoundTripsThroughAConfigAndABareIdIsRefused() throws Exception {
        Booster written = Booster.on(points).bonus(0.25).forOwner(ProgressOwner.world("world"))
                .until(shop.now().plus(Duration.ofHours(2))).build();
        Config config = ConfigFactory.open(tempDir.resolve("boosters.yml"));
        config.setValue("weekend", written);
        config.save();

        Booster read = ConfigFactory.open(tempDir.resolve("boosters.yml")).getValue("weekend", Booster.class);
        assertEquals(written, read);
        assertEquals("fakeshop:points", read.getDomainId());

        Files.write(tempDir.resolve("bare.yml"), "weekend:\n  on: money\n  factor: 2\n".getBytes(StandardCharsets.UTF_8));
        Exception bare = assertThrows(Exception.class, () -> ConfigFactory.open(tempDir.resolve("bare.yml")).getValue("weekend", Booster.class));
        assertTrue(String.valueOf(rootCause(bare).getMessage()).contains("<plugin>:<domain>"), String.valueOf(rootCause(bare)));
    }

    private static Throwable rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
