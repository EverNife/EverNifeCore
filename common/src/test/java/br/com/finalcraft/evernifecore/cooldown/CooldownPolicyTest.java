package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldownsLocal;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The policy vocabulary of the {@link Cooldown} handle, over a memory route and the real clock. */
@ECoreTest
class CooldownPolicyTest {

    private static Cooldown counter(String id, RepeatPolicy policy) {
        return new Cooldown(id, new CooldownEntry(), policy, null);
    }

    @Test
    void tryUseOfSixteenWithFifteenLeftTakesNothing() {
        Cooldown shop = counter("shop", RepeatPolicy.limited(16));
        assertTrue(shop.tryUse());
        assertEquals(15, shop.getUsesLeft());

        assertFalse(shop.tryUse(16), "all or nothing: sixteen do not fit in fifteen");
        assertEquals(15, shop.getUsesLeft(), "and nothing was taken");
        assertTrue(shop.tryUse(15));
        assertEquals(0, shop.getUsesLeft());
        assertFalse(shop.canUse());
    }

    @Test
    void seedThenGetLastUseAtAnswersThatInstant() {
        Cooldown kit = counter("kit", RepeatPolicy.unlimited().cooldown(Duration.ofHours(1)));
        assertFalse(kit.getLastUseAt().isPresent(), "never used");

        Instant lastUse = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        kit.seed(lastUse);

        assertEquals(lastUse, kit.getLastUseAt().get());
        assertTrue(kit.isInCooldown(), "the gap counts from the seeded use");
        assertFalse(kit.canUse());
    }

    @Test
    void seedCountsOneUseInTheWindowOfThatInstant() {
        Cooldown daily = counter("daily", RepeatPolicy.limited(2).window(Window.daily(LocalTime.MIDNIGHT, ZoneOffset.UTC)));
        daily.seed(Instant.now());

        assertEquals(1, daily.getUsesLeft(), "the seeded use counts in today's window");
        assertTrue(daily.tryUse());
        assertFalse(daily.canUse(), "two of two used today");
    }

    @Test
    void seedInAnOlderWindowCountsNothingNow() {
        Cooldown daily = counter("daily", RepeatPolicy.limited(2).window(Window.daily(LocalTime.MIDNIGHT, ZoneOffset.UTC)));
        assertTrue(daily.tryUse());

        daily.seed(Instant.now().minus(3, ChronoUnit.DAYS));

        assertEquals(1, daily.getUsesLeft(), "a use three days ago neither counts today nor erases today's");
        Cooldown fresh = counter("fresh", RepeatPolicy.limited(2).window(Window.daily(LocalTime.MIDNIGHT, ZoneOffset.UTC)));
        fresh.seed(Instant.now().minus(3, ChronoUnit.DAYS));
        assertEquals(2, fresh.getUsesLeft());
    }

    @Test
    void anInMemoryCooldownIsNeverListedNorWritten() {
        Cooldown throttle = Cooldown.inMemory("parser-throttle");
        //a route to Cooldowns.yml would need the core's config, which no unit test opens: reaching it would throw
        throttle.setPersist(true).startWith(60);

        assertTrue(throttle.isInCooldown());
        assertFalse(Cooldowns.getMapOfCooldowns().containsKey("parser-throttle"), "viewserver never lists it");
        assertFalse(Cooldowns.getMapOfCooldowns().containsValue(throttle));
    }

    @Test
    void giveBackReturnsTheUseAndClosesTheGap() {
        Cooldown chest = counter("chest", RepeatPolicy.limited(3).cooldown(Duration.ofMinutes(10)));
        assertTrue(chest.tryUse());
        assertFalse(chest.canUse(), "the gap holds the next use back");

        chest.giveBack();

        assertEquals(3, chest.getUsesLeft());
        assertTrue(chest.canUse());
    }

    @Test
    void aLimitWithoutAWindowNeverFreesAndExplainsIt() {
        Cooldown once = counter("starter", RepeatPolicy.once());
        assertTrue(once.tryUse());

        assertFalse(once.tryUse());
        assertEquals(ChronoUnit.FOREVER.getDuration(), once.getRemaining());
        assertEquals("§c1 of 1 used.", once.explain(Locale.ENGLISH).getText());
        assertEquals(Duration.ZERO, counter("fresh", RepeatPolicy.once()).getRemaining());
    }

    @Test
    void aBareCooldownExplainsTheWaitLeft() {
        Cooldown kit = counter("kit", RepeatPolicy.unlimited());
        kit.startWith(300);

        assertTrue(kit.explain(Locale.ENGLISH).getText().startsWith("§cWait "));
        assertTrue(kit.explain(new Locale("pt", "BR")).getText().startsWith("§cEspere mais "));
        assertTrue(kit.getRemaining().compareTo(Duration.ofSeconds(290)) > 0);
    }

    @Test
    void startCountsAUseAgainstALimitAndTryUseOfZeroTeaches() {
        Cooldown daily = counter("daily", RepeatPolicy.limited(2));
        daily.startWith(0);

        assertEquals(1, daily.getUsesLeft(), "startWith is a use whose answer is ignored");
        assertThrows(IllegalArgumentException.class, () -> daily.tryUse(0));
    }

    @Test
    void theConfigFormCarriesUsesAndWindowAndAnOldFileReadsNone() {
        CooldownEntry entry = new CooldownEntry(1000L, 0L, 1000L, true, 2, 500L, 9000L);
        Map<String, Object> map = Cooldowns.toConfigMap(new Cooldown("daily", entry, RepeatPolicy.unlimited(), null));

        Cooldown reread = Cooldowns.fromConfigMap(map);
        assertEquals(2, reread.getEntry().getUses());
        assertEquals(500L, reread.getEntry().getWindowStart());
        assertEquals(9000L, reread.getEntry().getWindowEnd());

        map.remove("uses");
        map.remove("windowStart");
        map.remove("windowEnd");
        assertEquals(0, Cooldowns.fromConfigMap(map).getEntry().getUses(), "a file written before uses existed reads 0");
    }

    @Test
    void theLegacyPlayerImportReadsNoUses(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("legacy.yml");
        Files.write(file, "Cooldown:\n  kit:\n    timeStart: 1000\n    timeDuration: 5000\n".getBytes(StandardCharsets.UTF_8));
        Config legacy = ConfigFactory.open(file.toFile());
        PlayerCooldownsLocal section = PlayerCooldownsLocal.fromLegacyYaml(legacy.getConfigSection("Cooldown"));

        CooldownEntry kit = section.getPersistedCooldowns().get("kit");
        assertEquals(1000L, kit.getTimeStart());
        assertEquals(0, kit.getUses());
        assertEquals(0L, kit.getWindowStart());
    }

    @Test
    void theRetentionKeepsALifetimeCountAndDropsAWindowedOneAfterItsWindow() {
        long now = System.currentTimeMillis();
        long retention = TimeUnit.DAYS.toMillis(30);
        long ancient = now - TimeUnit.DAYS.toMillis(400);

        CooldownEntry lifetime = new CooldownEntry(ancient, 0L, ancient, true, 1, 0L, 0L);
        assertFalse(CooldownRetention.isExpired(lifetime, now, retention), "dropping it would hand the use back");

        CooldownEntry windowed = new CooldownEntry(ancient, 0L, ancient, true, 3, ancient, ancient + TimeUnit.DAYS.toMillis(1));
        assertTrue(CooldownRetention.isExpired(windowed, now, retention));

        CooldownEntry openWindow = new CooldownEntry(now, 0L, now, true, 3, now, now + TimeUnit.DAYS.toMillis(1));
        assertFalse(CooldownRetention.isExpired(openWindow, now + TimeUnit.DAYS.toMillis(20), retention));
    }

    @Test
    void latestBreaksAFullTieByTheLaterWindowThenTheMoreUses() {
        CooldownEntry older = new CooldownEntry(100L, 0L, 500L, true, 3, 100L, 200L);
        CooldownEntry newer = new CooldownEntry(100L, 0L, 500L, true, 1, 300L, 400L);
        assertEquals(newer, CooldownEntry.latest(older, newer));
        assertEquals(newer, CooldownEntry.latest(newer, older));

        CooldownEntry fewer = new CooldownEntry(100L, 0L, 500L, true, 1, 100L, 200L);
        assertEquals(older, CooldownEntry.latest(fewer, older));
        assertEquals(older, CooldownEntry.latest(older, fewer));
    }
}
