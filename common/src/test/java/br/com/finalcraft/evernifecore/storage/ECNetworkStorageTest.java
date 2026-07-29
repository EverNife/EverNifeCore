package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.PlayerDataAdminConfig;
import br.com.finalcraft.evernifecore.storage.config.SectionFamily;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract of a plugin's handle onto the shared network backend.
 *
 * <p>Four properties carry the whole design, and each is a failure that would only surface in
 * production: the facade captures no storage (so it survives the reload that rebuilds the network one),
 * its claims are keyed by plugin NAME (so a re-enabled plugin does not collide with itself), those
 * claims are what makes such a collection visible to an admin at all, and {@code release()} gives back
 * exactly what this handle registered - never a sibling PDSection's registration.</p>
 */
@ECoreTest
class ECNetworkStorageTest {

    private static final String PLUGIN_A = "NetPluginA";
    private static final String PLUGIN_B = "NetPluginB";
    private static final String NETWORK_BACKEND = "net_main";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        //the ECPluginData cache is static and keyed by name: a stale one would point at a @TempDir
        //that no longer exists and reach the next test in this JVM
        ECPluginManager.removePluginData(PLUGIN_A);
        ECPluginManager.removePluginData(PLUGIN_B);
    }

    // ------------------------------------------------------------------
    // the claims are the only place a network collection is visible
    // ------------------------------------------------------------------

    @Test
    void everyFrameworkFamilyOnTheNetworkBackendIsClaimedAndListed() {
        boot("net_claims");

        Map<String, String> claims = PlayerController.get().registry().getClaims(NETWORK_BACKEND);

        assertEquals("EverNifeCore:Accounts", claims.get(Accounts.COLLECTION),
                "the account registry must be claimed, or a plugin could take the name: " + claims);
        assertEquals("EverNifeCore:ServerCooldowns", claims.get(ServerCooldowns.DEFAULT_COLLECTION), claims.toString());
        String accountSection = BindingResolver.collectionName(SectionFamily.ACCOUNT.getCollectionPrefix(),
                PlayerController.pluginNameOf(EverNifeCore.getEcPluginData()), "cooldowns");
        assertTrue(claims.containsKey(accountSection),
                "the account-wide cooldown section travels with the family, so it must be claimed too: " + claims);

        //the base PlayerData sits on the default backend: a transfer enumerating THIS backend's claims
        //must not drag it along
        assertFalse(claims.containsKey(PlayerDataAdminConfig.DEFAULT_COLLECTION), claims.toString());
    }

    @Test
    void theClaimsSnapshotCannotBeMutatedByItsReader() {
        boot("net_claims_immutable");

        Map<String, String> claims = PlayerController.get().registry().getClaims(NETWORK_BACKEND);

        //a caller that could edit the snapshot would be editing who owns what, from outside the claim check
        assertThrows(UnsupportedOperationException.class, () -> claims.put("hijacked", "someone"));
    }

    // ------------------------------------------------------------------
    // reachable only once the storage layer is up
    // ------------------------------------------------------------------

    @Test
    void reachingTheNetworkBeforeTheBootstrapIsRefused() {
        //no controller at all - the state a plugin's static initializer would find
        PlayerDataWorld.tearDown();
        ECPluginData plugin = fakePlugin(PLUGIN_A);

        StorageUnavailableException refused = assertThrows(StorageUnavailableException.class,
                () -> ECNetworkStorage.of(plugin));
        assertTrue(refused.getMessage().contains("not available yet"), refused.getMessage());
        //it must point at WHEN to call instead, not just report the absence
        assertTrue(refused.getMessage().contains("static initializer"), refused.getMessage());
    }

    // ------------------------------------------------------------------
    // claims: one owner per collection, owner derived from the plugin name
    // ------------------------------------------------------------------

    @Test
    void aSecondPluginOnTheSameCollectionIsRefusedAndNamesTheOwner() {
        boot("net_collision");
        EntityDescriptor<UUID, Bank> banks = bankDescriptor("contested_banks");

        ECNetworkStorage.of(fakePlugin(PLUGIN_A)).manager(banks, CachePolicy.always());

        ECNetworkStorage second = ECNetworkStorage.of(fakePlugin(PLUGIN_B));
        StorageConfigException refused = assertThrows(StorageConfigException.class,
                () -> second.manager(banks, CachePolicy.always()));
        assertTrue(refused.getMessage().contains(PLUGIN_B), refused.getMessage());
        assertTrue(refused.getMessage().contains("contested_banks"), refused.getMessage());
        //naming the current owner is the whole value of the refusal - "taken" alone is a dead end
        assertTrue(refused.getMessage().contains("plugin:" + PLUGIN_A), refused.getMessage());
    }

    @Test
    void aFreshPluginDataOfTheSameNameReclaimsItsOwnCollection() {
        File storageYml = boot("net_reclaim");
        EntityDescriptor<UUID, Bank> banks = bankDescriptor("reclaim_banks");

        ECPluginData first = fakePlugin(PLUGIN_A);
        ECNetworkStorage.of(first).manager(banks, CachePolicy.always());

        //a plugin re-enabled at runtime comes back as a DIFFERENT ECPluginData under the same name;
        //an owner keyed by instance would make it collide with the claim it left behind
        ECPluginManager.removePluginData(PLUGIN_A);
        ECPluginData reEnabled = fakePlugin(PLUGIN_A);
        assertNotSame(first, reEnabled);

        ECNetworkStorage after = ECNetworkStorage.of(reEnabled);
        assertDoesNotThrow(() -> after.manager(banks, CachePolicy.always()));
        assertEquals("plugin:" + PLUGIN_A,
                PlayerController.get().registry().getCollectionOwner(NETWORK_BACKEND, "reclaim_banks"));

        //and the same holds across a core reload, which rebuilds the registry the claims live in
        PlayerController.initialize(storageYml);
        assertDoesNotThrow(() -> after.manager(banks, CachePolicy.always()));
        assertEquals("plugin:" + PLUGIN_A,
                PlayerController.get().registry().getCollectionOwner(NETWORK_BACKEND, "reclaim_banks"));
    }

    // ------------------------------------------------------------------
    // captures nothing: the reason this is a facade and not an ECStorage
    // ------------------------------------------------------------------

    @Test
    void aFacadeTakenBeforeAReloadStillServesTheLiveStorage() {
        File storageYml = boot("net_survives_reload");
        EntityDescriptor<UUID, Bank> banks = bankDescriptor("surviving_banks");

        ECNetworkStorage network = ECNetworkStorage.of(fakePlugin(PLUGIN_A));
        UUID bankId = UUID.randomUUID();
        network.manager(banks, CachePolicy.always()).saveAndCache(new Bank(bankId, 500)).join();

        //the reload rebuilds the network storage and CLOSES the one the facade was born with
        PlayerController.initialize(storageYml);

        Storage live = PlayerController.get().registry().get(network.backendName());
        assertSame(live, network.storage(), "the facade must resolve the live storage, never a captured one");

        CachingManager<UUID, Bank> reDerived = network.manager(banks, CachePolicy.always());
        assertSame(live, reDerived.storage(), "a manager handed out after the reload belongs to the live storage");
        assertTrue(reDerived.resolve(bankId).join().isPresent(),
                "and it reads what was written before the reload - the handle still works end to end");
    }

    // ------------------------------------------------------------------
    // release(): gives back this handle's registrations, and nothing else
    // ------------------------------------------------------------------

    @Test
    void releaseDropsOnlyTheTypesThisHandleRegistered() {
        ECPluginData plugin = fakePlugin(PLUGIN_A);
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(plugin, ProfileSection.class, "profile").build());
        boot("net_release_types");

        ECNetworkStorage network = ECNetworkStorage.of(plugin);
        network.manager(bankDescriptor("released_banks"), CachePolicy.always());
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Petrus").join();
        PlayerController.getPDSection(uuid, ProfileSection.class).join();

        RefRegistry shared = ECStorageRegistries.of(plugin);
        assertTrue(shared.isRegistered(Bank.class));
        assertTrue(shared.isRegistered(ProfileSection.class));

        network.release();

        assertFalse(shared.isRegistered(Bank.class), "release must give back what this handle registered");
        assertTrue(shared.isRegistered(ProfileSection.class),
                "the registry is SHARED with the plugin's PDSections - releasing it wholesale would unwire them");
        assertDoesNotThrow(() -> PlayerController.getPDSection(uuid, ProfileSection.class).join(),
                "the plugin's sections keep resolving after its network handle is released");
    }

    @Test
    void releaseFreesTheClaimForAnotherOwner() {
        boot("net_release_claim");
        EntityDescriptor<UUID, Bank> banks = bankDescriptor("handover_banks");

        ECNetworkStorage first = ECNetworkStorage.of(fakePlugin(PLUGIN_A));
        first.manager(banks, CachePolicy.always());

        first.release();
        assertNull(PlayerController.get().registry()
                .getCollectionOwner(NETWORK_BACKEND, "handover_banks"));

        //a plugin disabled and never re-enabled must not leave the name locked against everyone else
        ECNetworkStorage second = ECNetworkStorage.of(fakePlugin(PLUGIN_B));
        assertDoesNotThrow(() -> second.manager(banks, CachePolicy.always()));
        assertEquals("plugin:" + PLUGIN_B, PlayerController.get().registry()
                .getCollectionOwner(NETWORK_BACKEND, "handover_banks"));
    }

    // ------------------------------------------------------------------
    // an ECStorage entity's default codec is ref-aware too
    // ------------------------------------------------------------------

    @Test
    void aRefInsideAnEcStorageEntityResolvesThroughThatHandlesRegistry() {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        RefRegistry registry = storage.refRegistry();

        EntityDescriptor<UUID, Bank> banks = EntityDescriptor.builder(UUID.class, Bank.class)
                .collection("handle_banks")
                .keyExtractor(bank -> bank.id)
                .codec(storage.defaultCodec(Bank.class))
                .build();
        UUID bankId = UUID.randomUUID();
        storage.manager(banks, CacheOptions.of(CachePolicy.always()))
                .saveAndCache(new Bank(bankId, 700)).join();

        EntityDescriptor<UUID, Vault> vaults = EntityDescriptor.builder(UUID.class, Vault.class)
                .collection("handle_vaults")
                .keyExtractor(vault -> vault.id)
                .codec(storage.defaultCodec(Vault.class))
                .build();
        CachingManager<UUID, Vault> vaultManager =
                storage.manager(vaults, CacheOptions.of(CachePolicy.always()));
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault(vaultId);
        vault.bank = registry.ref(bankId, Bank.class);
        vaultManager.saveAndCache(vault).join();

        //re-read from the backend: only a codec that BOUND the ref on decode can resolve it
        vaultManager.clearCache();
        Vault reread = vaultManager.refresh(vaultId).join();

        Optional<Bank> resolved = reread.bank.resolve().join();
        assertTrue(resolved.isPresent(), "the ref decoded from the backend must be bound to the handle's registry");
        assertEquals(700, resolved.get().balance);

        storage.close().join();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A network backend of its own, so what is claimed on it is the network family and nothing else. */
    private File boot(String dbName) {
        File storageYml = Storages.h2(dbName)
                .extraBackend(NETWORK_BACKEND, "    type: h2",
                        "    url: \"jdbc:h2:mem:" + dbName + "_net;DB_CLOSE_DELAY=-1\"")
                .networkBackendId(NETWORK_BACKEND)
                .writeTo(tempDir);
        PlayerController.initialize(storageYml);
        return storageYml;
    }

    private ECPluginData fakePlugin(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    private static EntityDescriptor<UUID, Bank> bankDescriptor(String collection) {
        return EntityDescriptor.builder(UUID.class, Bank.class)
                .collection(collection)
                .keyExtractor(bank -> bank.id)
                .codec(new JacksonJsonCodec<>(Bank.class))
                .build();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** Stands in for the platform's plugin object (a JavaPlugin on Bukkit); only its identity matters. */
    public static final class FakePlugin {
    }

    /** A plugin's network-wide entity: the guild bank of the class javadoc. */
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

    /** An entity that points at another one living in the same handle. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Vault {
        public UUID id;
        public Ref<UUID, Bank> bank;

        public Vault() {
        }

        Vault(UUID id) {
            this.id = id;
        }
    }

    /** A per-player section of the same plugin, so release() has a sibling registration to spare. */
    public static class ProfileSection extends PDSection {
        public int visits;
    }
}
