package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.api.common.player.IOfflinePermissions;
import br.com.finalcraft.evernifecore.config.plugin.BoosterSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A booster domain: one plugin's gain, declared in code, boosted only by what names it. */
class BoosterDomainTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld duels;
    private ECPluginData jobs;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout()
                .offlinePermissions(IOfflinePermissions.NONE).install();
        duels = LogicWorld.open("Duels", tempDir.resolve("duels"));
        jobs = Plugins.fakePluginData("Jobs", tempDir.resolve("jobs").toFile());
        alex = duels.player("Alex");
    }

    @AfterEach
    void close() {
        Plugins.forget("Jobs");
        duels.close();
        world.close();
    }

    private static void assertMultiplier(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected x" + expected + ", got x" + actual);
    }

    private BigDecimal multiplierOf(BoosterDomain domain) {
        return domain.resolve(duels.context(alex).build()).getMultiplier();
    }

    @Test
    void theIdIsThePluginAndTheNameLowercase() {
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "Win").register();

        assertEquals("duels:win", win.getId());
        assertTrue(Boosters.findDomain("Duels:WIN").isPresent());
        assertEquals(Collections.singletonList(win), Boosters.getDomains(duels.getOwner()));
        assertTrue(win.resolve(duels.context(alex).build()).explain().getText().startsWith("duels:win x1"));
    }

    @Test
    void thePermissionOfADomainBoostsItAndNoOtherDomainOfAnyPlugin() {
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "win").permission("duels.booster.win", 2).register();
        BoosterDomain kill = BoosterDomain.of(duels.getOwner(), "kill").permission("duels.booster.kill", 2).register();
        BoosterDomain pay = BoosterDomain.of(jobs, "pay").permission("jobs.booster.pay", 2).register();
        BoosterDomain silent = BoosterDomain.of(jobs, "silent").register();

        alex.grant("duels.booster.win.2");

        assertMultiplier("2", multiplierOf(win));
        assertMultiplier("2", win.resolve(alex.getUniqueId()).getMultiplier());
        assertMultiplier("1", multiplierOf(kill));
        assertMultiplier("1", multiplierOf(pay));
        assertMultiplier("1", multiplierOf(silent));
    }

    @Test
    void aGlobalVipNodeBoostsNoDomain() {
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "win").permission("duels.booster.win", 2).register();
        BoosterDomain money = BoosterDomain.of(jobs, "money").register();
        //the node of the removed global source, built so no source line spells it
        String global = String.join(".", "vip", "booster");
        alex.grant(global + ".money.2").grant(global + ".win.2").grant(global + ".duels:win.2").grant(global + ".jobs:money.2");

        assertMultiplier("1", multiplierOf(win));
        assertMultiplier("1", multiplierOf(money));
    }

    @Test
    void theBuildersJoinIsTheOneTheDomainResolvesBy() {
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "win").register();
        BoosterDomain kill = BoosterDomain.of(duels.getOwner(), "kill").join(BoosterJoin.max()).register();
        BoosterDomain streak = BoosterDomain.of(duels.getOwner(), "streak").join(BoosterJoin.product().cap(new BigDecimal("2.5"))).register();
        BoosterManager boosters = duels.boosters();
        for (BoosterDomain domain : new BoosterDomain[]{win, kill, streak}) {
            boosters.grant(Booster.on(domain).factor(2).forOwner(ProgressOwner.player(alex.getUniqueId())).build()).join();
            boosters.grant(Booster.on(domain).factor(1.5).build()).join();
        }

        assertEquals(BoosterJoin.sumBonus(), win.getJoin(), "sum-bonus when the builder names none");
        assertMultiplier("2.5", multiplierOf(win));
        assertMultiplier("2", multiplierOf(kill));
        assertMultiplier("2.5", multiplierOf(streak)); //product 3, capped
        assertEquals(BoosterJoin.max(), kill.resolve(duels.context(alex).build()).getJoin());
    }

    @Test
    void aDomainLeavesWithThePluginThatDeclaredIt() {
        ECPluginData arena = Plugins.fakePluginData("Arena", tempDir.resolve("arena").toFile());
        BoosterDomain.of(arena, "capture").register();
        assertTrue(Boosters.findDomain("arena:capture").isPresent());

        Plugins.forget("Arena");

        assertFalse(Boosters.findDomain("arena:capture").isPresent());
        assertTrue(Boosters.getDomains(arena).isEmpty());
        BoosterDomain again = BoosterDomain.of(Plugins.fakePluginData("Arena", tempDir.resolve("arena").toFile()), "capture").register();
        assertEquals("arena:capture", again.getId(), "a reloaded plugin declares it again");
        Plugins.forget("Arena");
    }

    @Test
    void applyToCountMakesTheBoostedCountWholeByTheRoundingAsked() {
        BoosterDomain planks = BoosterDomain.of(duels.getOwner(), "planks").register();
        BoosterResult twice = new BoosterResult(planks, new BigDecimal("2"), Collections.<Booster>emptyList(), BoosterJoin.sumBonus(), Collections.<String>emptyList());
        for (CountRounding rounding : CountRounding.values()) {
            assertEquals(8, twice.applyToCount(4, rounding, () -> 0.0), rounding.name());
            assertEquals(8, twice.applyToCount(4, rounding, () -> 0.99), rounding.name());
        }

        BoosterResult half = new BoosterResult(planks, new BigDecimal("1.5"), Collections.<Booster>emptyList(), BoosterJoin.sumBonus(), Collections.<String>emptyList());
        assertEquals(4, half.applyToCount(3, CountRounding.FLOOR));
        assertEquals(5, half.applyToCount(3, CountRounding.ROUND));
        assertEquals(5, half.applyToCount(3, CountRounding.CHANCE, () -> 0.49), "below the fraction: the extra one comes");
        assertEquals(4, half.applyToCount(3, CountRounding.CHANCE, () -> 0.5), "at the fraction: it does not");
        assertEquals(0, half.applyToCount(0, CountRounding.CHANCE, () -> 0.0));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> half.applyToCount(-1, CountRounding.FLOOR));
        assertTrue(negative.getMessage().contains("0 or more"), negative.getMessage());
    }

    @Test
    void registeringTheSameIdTwiceIsRefusedWithTheWayOut() {
        BoosterDomain.of(duels.getOwner(), "win").register();

        IllegalStateException twice = assertThrows(IllegalStateException.class, () -> BoosterDomain.of(duels.getOwner(), "WIN").register());
        assertTrue(twice.getMessage().contains("already registered by Duels"), twice.getMessage());
        assertTrue(twice.getMessage().contains("Boosters.findDomain(\"duels:win\")"), twice.getMessage());
    }

    @Test
    void thePermissionGrantsOnlyTheFactorsTheDomainDeclared() {
        BoosterDomain planks = BoosterDomain.of(duels.getOwner(), "woodcutter").permission("duels.booster.woodcutter", 2, 4, 6).register();
        assertEquals(Arrays.asList(new BigDecimal("2.0"), new BigDecimal("4.0"), new BigDecimal("6.0")), planks.getPermissionFactors());

        alex.grant("duels.booster.woodcutter.3");
        assertMultiplier("1", multiplierOf(planks));
        alex.grant("duels.booster.woodcutter.4");
        assertMultiplier("4", multiplierOf(planks));
        alex.grant("duels.booster.woodcutter.6.0");
        assertMultiplier("6", multiplierOf(planks));

        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> BoosterDomain.of(duels.getOwner(), "kill").permission("duels.booster.kill").register());
        assertTrue(none.getMessage().contains("permission(\"duels.booster.kill\", 2, 4, 6)"), none.getMessage());
        assertFalse(Boosters.findDomain("duels:kill").isPresent(), "a refused domain is not registered");
    }

    @Test
    void anOfflineOwnerGetsTheBoostersTheManagerGranted() {
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "win").permission("duels.booster.win", 2).register();
        UUID offline = UUID.randomUUID();
        duels.boosters().grant(Booster.on(win).factor(2).forOwner(ProgressOwner.player(offline)).build()).join();
        duels.boosters().grant(Booster.on(win).bonus(0.5).build()).join();
        duels.boosters().grant(Booster.on(win).factor(3).forOwner(ProgressOwner.player(alex.getUniqueId())).build()).join();

        BoosterResult result = win.resolve(offline);

        assertMultiplier("2.5", result.getMultiplier());
        assertEquals(2, result.getParcels().size(), result.toString());
    }

    @Test
    void theFactorsComeFromTheDomainsEntryOfTheBoosterBlock() {
        BoosterSettings block = new BoosterSettings().addDomain("win", 2, 4).addDomain("kill");
        BoosterDomain win = BoosterDomain.of(duels.getOwner(), "win").permission("duels.booster.win", block).register();
        BoosterDomain kill = BoosterDomain.of(duels.getOwner(), "kill").permission("duels.booster.kill", block).register();
        BoosterDomain absent = BoosterDomain.of(duels.getOwner(), "loss").permission("duels.booster.loss", block).register();

        assertEquals(Arrays.asList(new BigDecimal("2.0"), new BigDecimal("4.0")), win.getPermissionFactors());
        assertEquals("duels.booster.win", win.getPermissionPrefix());
        alex.grant("duels.booster.win.4");
        assertMultiplier("4", multiplierOf(win));

        assertTrue(kill.getPermissionFactors().isEmpty());
        assertEquals(null, kill.getPermissionPrefix());
        assertEquals(null, absent.getPermissionPrefix());
    }
}
