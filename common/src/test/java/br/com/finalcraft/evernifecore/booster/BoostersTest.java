package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The booster engine: the stock permission source, a plugin's manager, the joins, the sweep and the multiplier a context reads. */
class BoostersTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
    }

    @AfterEach
    void close() {
        Boosters.applySettings(PermissionBoosterSource.DEFAULT_FACTORS, Collections.<String, BoosterJoin>emptyMap());
        shop.close();
        world.close();
    }

    private ExecutionContext ctx() {
        return shop.context(alex).build();
    }

    private static void assertMultiplier(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected x" + expected + ", got x" + actual);
    }

    @Test
    void aVipPermissionAndAManagerBoosterSumTheirBonusesUpToTheDeclaredCap() {
        alex.grant("vip.booster.money.1.5");
        shop.boosters().grant(Booster.on("money").bonus(0.25).forOwner(ProgressOwner.player(alex.getUniqueId()))
                .from(shop.caller("event/weekend")).build()).join();

        BoosterResult result = Boosters.resolve(ctx(), "money");
        assertMultiplier("1.75", result.getMultiplier());
        assertEquals(2, result.getParcels().size());
        assertMultiplier("1.75", ctx().getMultiplier("evernifecore:money"));
        assertTrue(result.explain().getText().contains(":permission"), result.explain().getText());

        Boosters.declareJoin(shop.getOwner(), "money", BoosterJoin.sumBonus().cap(new BigDecimal("1.6")));
        assertMultiplier("1.6", ctx().getMultiplier("money"));
    }

    @Test
    void thePermissionSourceKeepsTheLargestListedFactorAndNeverSeesAnUnlistedOne() {
        alex.grant("vip.booster.money.1.25").grant("vip.booster.money.2").grant("vip.booster.money.2.5");

        assertMultiplier("2", ctx().getMultiplier("money"));
        assertMultiplier("1", ctx().getMultiplier("exp-points"));
        assertMultiplier("1", shop.context().build().getMultiplier("money"));
    }

    @Test
    void sumOfBonusesMaxAndProductJoinTheSameBoostersDifferently() {
        List<Booster> boosters = Arrays.asList(Booster.on("money").factor(2).build(), Booster.on("money").factor(1.5).build());

        assertMultiplier("2.5", BoosterJoin.sumBonus().join(boosters));
        assertMultiplier("2", BoosterJoin.max().join(boosters));
        assertMultiplier("3", BoosterJoin.product().join(boosters));
        assertMultiplier("2.75", BoosterJoin.product().cap(new BigDecimal("2.75")).join(boosters));
        assertMultiplier("1", BoosterJoin.product().join(Collections.<Booster>emptyList()));
    }

    @Test
    void theConfigSwapsAJoinOnlyWithinWhatTheGainsOwnerAccepts() {
        Boosters.declareJoin(shop.getOwner(), "fakeshop:points", BoosterJoin.sumBonus(), BoosterJoin.max());

        Boosters.applySettings(PermissionBoosterSource.DEFAULT_FACTORS, Collections.singletonMap("fakeshop:points", BoosterJoin.max()));
        assertEquals(BoosterJoin.max(), Boosters.getJoin("fakeshop:points"));

        Boosters.applySettings(PermissionBoosterSource.DEFAULT_FACTORS, Collections.singletonMap("fakeshop:points", BoosterJoin.product()));
        assertEquals(BoosterJoin.sumBonus(), Boosters.getJoin("fakeshop:points"), "product is not accepted: the declared join stays");

        Boosters.applySettings(PermissionBoosterSource.DEFAULT_FACTORS, Collections.singletonMap("money", BoosterJoin.product()));
        assertEquals(BoosterJoin.product(), Boosters.getJoin("evernifecore:money"), "an undeclared gain takes what the config says");
    }

    @Test
    void anExpiredBoosterStopsCountingAtOnceAndTheSweepDropsItFromTheStorage() {
        BoosterManager manager = BoosterManager.of(shop.getOwner(), shop.getStorage());
        Booster granted = manager.grant(Booster.on("money").factor(2).forOwner(ProgressOwner.player(alex.getUniqueId()))
                .lasting(Duration.ofMinutes(1)).build()).join();
        assertMultiplier("2", ctx().getMultiplier("money"));

        shop.advance(Duration.ofSeconds(61));
        assertMultiplier("1", ctx().getMultiplier("money"));
        assertEquals(1, manager.getAll().size(), "nothing ran yet: only the sweep drops it");

        shop.runDue();
        assertTrue(manager.getAll().isEmpty());
        BoosterManager.closeOwnedBy(shop.getOwner());
        assertTrue(BoosterManager.of(shop.getOwner(), shop.getStorage()).getAll().isEmpty(), "gone from the storage, not only from memory: " + granted);
    }

    @Test
    void aStoredBoosterComesBackWithItsIdOwnerAndOriginWhenTheManagerReopens() {
        BoosterManager manager = BoosterManager.of(shop.getOwner(), shop.getStorage());
        Booster granted = manager.grant(Booster.on("fakeshop:points").bonus(0.5).forOwner(ProgressOwner.global())
                .from(shop.caller("store/x2")).build()).join();
        List<BoosterGrantedEvent> events = shop.recorded(BoosterGrantedEvent.class);
        manager.grant(Booster.on("money").factor(3).build()).join();
        assertEquals(1, events.size());

        BoosterManager.closeOwnedBy(shop.getOwner());
        assertMultiplier("1", ctx().getMultiplier("fakeshop:points"));
        BoosterManager reopened = BoosterManager.of(shop.getOwner(), shop.getStorage());

        assertTrue(reopened.getActive(ProgressOwner.global()).contains(granted), reopened.getAll().toString());
        assertMultiplier("1.5", ctx().getMultiplier("fakeshop:points"));
        assertTrue(reopened.revoke(granted.getId()).join());
        assertMultiplier("1", ctx().getMultiplier("fakeshop:points"));
    }

    @Test
    void aBoosterIsAFactorOrABonusAndNeverTurnsAGainIntoALoss() {
        IllegalArgumentException both = assertThrows(IllegalArgumentException.class, () -> Booster.on("money").factor(2).bonus(1).build());
        assertTrue(both.getMessage().contains("exactly one"), both.getMessage());
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> Booster.on("money").bonus(-1.5).build());
        assertTrue(negative.getMessage().contains("loss"), negative.getMessage());
    }

    @Test
    void aBoosterRoundTripsThroughAConfig() {
        Booster written = Booster.on("finaljobs:money").bonus(0.25).forOwner(ProgressOwner.world("world"))
                .until(shop.now().plus(Duration.ofHours(2))).build();
        Config config = ConfigFactory.open(tempDir.resolve("boosters.yml"));
        config.setValue("weekend", written);
        config.save();

        Booster read = ConfigFactory.open(tempDir.resolve("boosters.yml")).getValue("weekend", Booster.class);
        assertEquals(written, read);
    }
}
