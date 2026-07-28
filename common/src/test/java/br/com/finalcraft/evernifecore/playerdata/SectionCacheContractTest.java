package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache contract a plugin can rely on: a section loads at login unless it opted out, a write on
 * an instance the cache already released is reported instead of vanishing, and a login that drags
 * explains itself.
 *
 * <p>Runs on H2 mem - no Docker.</p>
 */
@ECoreTest
class SectionCacheContractTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetCounters() {
        DetachedWrites.reset();
    }

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        DetachedWrites.reset();
    }

    public static class BalanceSection extends PDSection {
        public long balance;
    }

    public static class ColdSection extends PDSection {
        public long value;
    }

    // ------------------------------------------------------------------
    // ONLINE is the default: the login is what puts the cell in memory
    // ------------------------------------------------------------------

    @Test
    void aSectionRegisteredWithoutALifecycleIsLoadedByTheLoginItself() throws IOException {
        PlayerController.initialize(Storages.h2("c_default_online").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalanceSection.class, "balance").build());
        PlayerController.registerPDSectionCfg(PDSectionConfiguration
                .builder(null, ColdSection.class, "cold").lifecycle(SectionLifecycle.LAZY).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Defaulted").join();

        assertNotNull(PlayerController.getLoadedSection(uuid, BalanceSection.class),
                "no lifecycle declared means ONLINE, so the login must have resolved it");
        assertNull(PlayerController.getLoadedSection(uuid, ColdSection.class),
                "LAZY is now the opt-out and must still wait for the first effective access");
    }

    @Test
    void resolvingAnAlreadyLoadedSectionCompletesWithoutWaiting() throws IOException {
        PlayerController.initialize(Storages.h2("c_completed").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalanceSection.class, "balance").build());

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Warm").join();

        //the guarantee the ONLINE default buys: after the login, resolving is not an async round trip
        CompletableFuture<BalanceSection> resolved = playerData.getPDSection(BalanceSection.class);
        assertTrue(resolved.isDone(), "an already-cached section must resolve without going to storage");
    }

    // ------------------------------------------------------------------
    // a write on a released instance is reported, not swallowed
    // ------------------------------------------------------------------

    @Test
    void writingToASectionTheCacheAlreadyReleasedIsReported() throws IOException {
        PlayerController.initialize(Storages.h2("c_detached").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalanceSection.class, "balance").build());

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Holder").join();
        BalanceSection held = playerData.getPDSection(BalanceSection.class).join();

        held.balance = 10;
        held.markDirty();
        assertEquals(0, DetachedWrites.occurrences(), "a write on the live instance is not a detached one");

        PlayerController.get().getBinding(BalanceSection.class).getManager().evict(uuid);

        held.balance = 20;
        held.markDirty();
        assertEquals(1, DetachedWrites.occurrences(),
                "the flush persists the cached values, so this write would have vanished");

        held.balance = 30;
        held.markDirty();
        assertEquals(2, DetachedWrites.occurrences(), "every occurrence counts, even when only one prints");
    }

    @Test
    void aFreshlySeededDefaultIsNotReportedAsDetached() throws IOException {
        PlayerController.initialize(Storages.h2("c_seeded").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalanceSection.class, "balance").build());

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Seeded").join();
        BalanceSection seeded = playerData.getPDSection(BalanceSection.class).join();
        seeded.balance = 7;
        seeded.markDirty();
        PlayerController.get().flushAll().join();

        assertEquals(0, DetachedWrites.occurrences());
    }

    // ------------------------------------------------------------------
    // the slow-login breakdown
    // ------------------------------------------------------------------

    @Test
    void theSlowLoginBreakdownNamesTheSectionThePluginAndTheBackend() throws IOException {
        PlayerController.initialize(Storages.h2("c_report").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, BalanceSection.class, "balance").build());

        LoginTimings timings = LoginTimings.start(UUID.randomUUID(), "Petrus", 3);
        assertTrue(timings.isEnabled());
        long fourSecondsAgo = System.nanoTime() - 4_000_000_000L;
        timings.phase("account", fourSecondsAgo);
        timings.track(PlayerController.get().getBinding(BalanceSection.class), fourSecondsAgo,
                CompletableFuture.completedFuture(null)).join();

        List<String> report = timings.format(4_100_000_000L, false);

        String joined = String.join("\n", report);
        assertTrue(joined.contains("slow login: Petrus"), joined);
        assertTrue(joined.contains("balance"), "the section has to be named: " + joined);
        assertTrue(joined.contains("test_h2"), "and the backend it sits on: " + joined);
        assertTrue(joined.contains("slowest: balance"), joined);
        assertTrue(report.get(0).startsWith("+--"), "the frame opens on the title line");

        int columnOfTime = report.get(1).indexOf("TIME");
        assertTrue(columnOfTime > 0, "the table header is the first line inside the frame: " + report.get(1));
        assertTrue(report.get(2).length() > columnOfTime,
                "the row must reach the TIME column it was padded to: " + report.get(2));
    }

    @Test
    void theBreakdownIsOffWhenTheThresholdIs() {
        LoginTimings disabled = LoginTimings.start(UUID.randomUUID(), "Quiet", 0);
        assertFalse(disabled.isEnabled(), "0 turns the report off");
        assertSame(LoginTimings.DISABLED, disabled, "and costs no allocation per login");
    }
}
