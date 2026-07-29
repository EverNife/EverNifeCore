package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.storage.ECNetworkStorage;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving the whole network family to another backend.
 *
 * <p>The unit is the family: what travels is what is CLAIMED on the source, so a collection a plugin
 * declared at runtime goes with the framework's without anyone maintaining a list. The two ways this
 * could lie to an admin are what these pin down - a transfer that reports success over a collection it
 * left behind, and a failed one that has already rewritten the binding.</p>
 */
@ECoreTest
class NetworkTransferTest {

    private static final String PLUGIN_NAME = "TransferPlugin";
    private static final String SOURCE = "net_source";
    private static final String TARGET = "net_target";
    private static final String BANK_COLLECTION = "plugin_banks";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        ECPluginManager.removePluginData(PLUGIN_NAME);
    }

    // ------------------------------------------------------------------
    // the happy path: everything claimed travels, and the choice is persisted
    // ------------------------------------------------------------------

    @Test
    void everyClaimedCollectionTravelsIncludingAPluginsOwn() {
        File storageYml = boot("net_move_ok");
        ECPluginData plugin = fakePlugin();

        UUID bankId = UUID.randomUUID();
        ECNetworkStorage.of(plugin).manager(bankDescriptor(), CachePolicy.always())
                .saveAndCache(new Bank(bankId, 900)).join();

        UUID owner = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        PlayerController.handleLogin(owner, "Owner").join();
        PlayerController.handleLogin(alt, "Alt").join();
        Account fused = Accounts.get().link(owner, alt).join();

        NetworkTransferReport report = PlayerController.get().transferNetwork(TARGET).join();

        assertTrue(report.success(), "the transfer must succeed: " + report.notCopyable());
        assertTrue(report.moved().contains(Accounts.COLLECTION), report.moved().toString());
        assertTrue(report.moved().contains(ServerCooldowns.DEFAULT_COLLECTION), report.moved().toString());
        assertTrue(report.moved().contains(BANK_COLLECTION),
                "a collection a plugin claimed through ECNetworkStorage must travel with the framework's: "
                        + report.moved());
        assertTrue(report.notCopyable().isEmpty(), report.notCopyable().toString());

        //the admin's choice is what the next boot reads, so it goes into the file
        assertEquals(TARGET, ConfigFactory.open(storageYml).getString("network.storage-backend-id"));
        assertEquals(TARGET, PlayerController.get().storageConfig().getNetworkBackendName());

        //and the rows are readable on the target - the family really moved, it was not just re-pointed
        assertEquals(fused.getAccountId(), Accounts.get().account(alt).join().getAccountId(),
                "the alias row must be readable on the target backend");
        Optional<Bank> moved = ECNetworkStorage.of(plugin)
                .manager(bankDescriptor(), CachePolicy.always()).resolve(bankId).join();
        assertTrue(moved.isPresent(), "the plugin's rows must be readable on the target backend");
        assertEquals(900, moved.get().balance);
    }

    // ------------------------------------------------------------------
    // a failure leaves the source serving and the target as it found it
    // ------------------------------------------------------------------

    @Test
    void aFailedTransferKeepsTheBindingAndReleasesTheClaimsItCreated() {
        File storageYml = boot("net_move_fail");
        ECPluginData plugin = fakePlugin();
        ECNetworkStorage.of(plugin).manager(bankDescriptor(), CachePolicy.always())
                .saveAndCache(new Bank(UUID.randomUUID(), 900)).join();

        //the copy refuses a target collection that already holds rows, which is how a half-finished
        //transfer is forced here without breaking a backend
        PlayerController.get().registry().get(TARGET).repository(bankDescriptor())
                .save(new Bank(UUID.randomUUID(), 1)).join();

        NetworkTransferReport report = PlayerController.get().transferNetwork(TARGET).join();

        assertFalse(report.success(), "a non-empty target collection must abort the transfer");
        assertFalse(report.reports().isEmpty(),
                "the transfer must have failed PART-WAY, on a copy it attempted - not before starting");
        assertEquals(SOURCE, ConfigFactory.open(storageYml).getString("network.storage-backend-id"),
                "a failed transfer must not rewrite the binding - the family stays where it is");
        assertEquals(SOURCE, PlayerController.get().storageConfig().getNetworkBackendName());
        assertTrue(PlayerController.get().registry().getClaims(TARGET).isEmpty(),
                "a claim this transfer reserved on the target must be given back, or a retry would collide"
                        + " with the leftovers of the attempt that failed: "
                        + PlayerController.get().registry().getClaims(TARGET));
    }

    // ------------------------------------------------------------------
    // a claim nothing can copy is named, never skipped in silence
    // ------------------------------------------------------------------

    @Test
    void aClaimWithoutADescriptorIsNamedByThePreviewAndByTheReport() {
        boot("net_move_preview");
        //the 3-arg claim records no descriptor, so nothing knows how to read those rows back
        PlayerController.get().registry().claimCollection(SOURCE, "legacy_rows", "SomePlugin:legacy");

        Map<String, String> preview = PlayerController.networkTransferPreview();
        assertEquals("SomePlugin:legacy", preview.get("legacy_rows"),
                "the preview prints before anything is copied, so it has to list it: " + preview);

        NetworkTransferReport report = PlayerController.get().transferNetwork(TARGET).join();

        assertTrue(report.success(), report.notCopyable().toString());
        assertFalse(report.moved().contains("legacy_rows"), report.moved().toString());
        assertTrue(report.notCopyable().stream().anyMatch(entry ->
                        entry.contains("legacy_rows") && entry.contains("SomePlugin:legacy")),
                "reading 'transferred everything' over a collection left behind is the failure worth"
                        + " preventing here: " + report.notCopyable());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Two network-capable backends, the family starting on the first one. */
    private File boot(String dbName) {
        File storageYml = Storages.h2(dbName)
                .extraBackend(SOURCE, "    type: h2",
                        "    url: \"jdbc:h2:mem:" + dbName + "_src;DB_CLOSE_DELAY=-1\"")
                .extraBackend(TARGET, "    type: h2",
                        "    url: \"jdbc:h2:mem:" + dbName + "_dst;DB_CLOSE_DELAY=-1\"")
                .networkBackendId(SOURCE)
                .writeTo(tempDir);
        PlayerController.initialize(storageYml);
        return storageYml;
    }

    private ECPluginData fakePlugin() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    private static EntityDescriptor<UUID, Bank> bankDescriptor() {
        return EntityDescriptor.builder(UUID.class, Bank.class)
                .collection(BANK_COLLECTION)
                .keyExtractor(bank -> bank.id)
                .codec(new JacksonJsonCodec<>(Bank.class))
                .build();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** Stands in for the platform's plugin object; only its identity matters. */
    public static final class FakePlugin {
    }

    /** A plugin's network-wide entity, so the family under test is not only the framework's. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Bank {
        public UUID id;
        public int balance;

        public Bank() {
        }

        Bank(UUID id, int balance) {
            this.id = id;
            this.balance = balance;
        }
    }
}
