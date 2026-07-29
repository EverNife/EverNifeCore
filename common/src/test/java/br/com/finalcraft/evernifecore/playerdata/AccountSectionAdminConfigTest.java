package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin's side of an AccountSection: the generated storage.yml entry, the collection it can be
 * moved to, and the freshness policy that bounds how stale another instance's write may look here.
 *
 * <p>Runs on H2 mem - no Docker.</p>
 */
@ECoreTest
class AccountSectionAdminConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class TrophiesSection extends AccountSection<TrophiesSection> {
        public Set<String> earned = new LinkedHashSet<>();

        @Override
        public TrophiesSection merge(List<TrophiesSection> others) {
            TrophiesSection merged = new TrophiesSection();
            merged.earned.addAll(this.earned);
            for (TrophiesSection other : others) {
                merged.earned.addAll(other.earned);
            }
            return merged;
        }
    }

    private File writeStorageYml(String dbName, String... extraLines) throws IOException {
        return writeStorageYml(dbName, 60, extraLines);
    }

    private File writeStorageYml(String dbName, int accountIdleGraceSeconds, String... extraLines) throws IOException {
        StringBuilder yml = new StringBuilder(String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "  idle-grace-seconds: " + accountIdleGraceSeconds,
                ""));
        for (String line : extraLines) {
            yml.append(line).append('\n');
        }
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void registerTrophies() {
        PlayerController.registerAccountSectionCfg(AccountSectionConfiguration
                .builder(null, TrophiesSection.class, "trophies")
                .description("Trophies shared by every linked identity")
                .build());
    }

    @Test
    void registeringGeneratesTheEntryTheAdminCanFind() throws IOException {
        File storageYml = writeStorageYml("acs_admin_entry");
        PlayerController.initialize(storageYml);
        registerTrophies();

        String raw = new String(Files.readAllBytes(storageYml.toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("accountsections:"), raw);
        assertTrue(raw.contains("trophies:"), raw);
        assertTrue(raw.contains("acs_unknownplugin_trophies"), raw);
        assertTrue(raw.contains("Trophies shared by every linked identity"),
                "the description is the only place the admin learns what this section holds:\n" + raw);
        //author first, plugin last - the same order both families now write
        assertTrue(raw.contains("AccountSection created by ") && raw.contains(" on the Plugin: "), raw);
    }

    @Test
    void theAdminCollectionWinsOverTheDerivedOne() throws IOException {
        File storageYml = writeStorageYml("acs_admin_collection",
                "accountsections:",
                "  unknownplugin:",
                "    trophies:",
                "      collection: renamed_trophies");
        PlayerController.initialize(storageYml);
        registerTrophies();

        assertEquals("renamed_trophies",
                PlayerController.get().accountEngine().getBinding(TrophiesSection.class).getCollection());
    }

    @Test
    void theAdminCanBoundHowStaleAnotherInstancesWriteLooks() throws IOException {
        File storageYml = writeStorageYml("acs_admin_ttl",
                "accountsections:",
                "  unknownplugin:",
                "    trophies:",
                "      cache: { policy: TTL, ttlSeconds: 30 }");
        PlayerController.initialize(storageYml);
        registerTrophies();

        CachePolicy policy = PlayerController.get().accountEngine()
                .getBinding(TrophiesSection.class).getManager().defaultPolicy();
        assertTrue(policy instanceof CachePolicy.TtlPolicy, String.valueOf(policy));
        assertEquals(30, ((CachePolicy.TtlPolicy) policy).getTtl().getSeconds());
    }

    @Test
    void noCacheIsRefusedBecauseTheCachedRowIsWhatGetsPersisted() throws IOException {
        File storageYml = writeStorageYml("acs_admin_nocache",
                "accountsections:",
                "  unknownplugin:",
                "    trophies:",
                "      cache: { policy: NOCACHE }");
        PlayerController.initialize(storageYml);

        StorageConfigException error = assertThrows(StorageConfigException.class, this::registerTrophies);
        assertTrue(error.getMessage().contains("NOCACHE"), error.getMessage());
    }

    @Test
    void aRowReadWithNobodyOnlineIsReleasedByTheSweep() throws Exception {
        File storageYml = writeStorageYml("acs_admin_sweep", 0);
        PlayerController.initialize(storageYml);
        registerTrophies();

        //an offline/aggregate read: no member of this account is online here, so no quit will ever come
        UUID accountId = UUID.randomUUID();
        PlayerController.getAccountSectionByAccountId(accountId, TrophiesSection.class).join();
        assertEquals(1, PlayerController.get().accountEngine().getBinding(TrophiesSection.class)
                .getManager().cachedSize());

        //two sweeps: the first one starts the clock for a key nothing had seen before
        PlayerController.get().lifecycleEngine().sweepIdleSections();
        PlayerController.get().lifecycleEngine().sweepIdleSections();

        assertEquals(0, PlayerController.get().accountEngine().getBinding(TrophiesSection.class)
                .getManager().cachedSize(),
                "an account row nobody is using must not stay resident forever");
    }
}
