package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.storage.ECNetworkStorage;
import br.com.finalcraft.evernifecore.storage.ECStorageRegistries;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a {@code Ref} may point once the network backend is reachable.
 *
 * <p>A plugin's network entity and its PDSections share one registry, so a ref crosses between them in
 * both directions. The framework's own rows bind to the GLOBAL registry instead: resolution walks child
 * to parent and never back down, which is what keeps a framework row from depending on a plugin being
 * installed. And a plugin that stores ONLY on the network still has to be swept when it disables -
 * nothing else would ever release its type registrations.</p>
 */
@ECoreTest
class NetworkStorageRefWiringTest {

    private static final String PLUGIN_NAME = "RefWiringPlugin";
    private static final String NETWORK_BACKEND = "net_main";

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
        ECPluginManager.removePluginData(PLUGIN_NAME);
    }

    // ------------------------------------------------------------------
    // a plugin's own graph: network entity <-> PDSection, both ways
    // ------------------------------------------------------------------

    @Test
    void aRefCrossesBetweenANetworkEntityAndAPdSectionOfTheSamePlugin() {
        ECPluginData plugin = fakePlugin();
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(plugin, ProfileSection.class, "profile").build());
        boot("ref_cross");

        ECNetworkStorage network = ECNetworkStorage.of(plugin);
        RefRegistry shared = network.refRegistry();
        EntityDescriptor<UUID, Bank> banks = EntityDescriptor.builder(UUID.class, Bank.class)
                .collection("crossing_banks")
                .keyExtractor(bank -> bank.id)
                .codec(network.defaultCodec(Bank.class))
                .build();
        CachingManager<UUID, Bank> bankManager = network.manager(banks, CachePolicy.always());

        UUID uuid = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Petrus").join();
        ProfileSection profile = PlayerController.getPDSection(uuid, ProfileSection.class).join();

        Bank bank = new Bank(bankId, 300);
        bank.owner = shared.ref(uuid, ProfileSection.class);
        bankManager.saveAndCache(bank).join();

        profile.bank = shared.ref(bankId, Bank.class);
        profile.markDirty();
        PlayerController.get().flushAll().join();

        //both sides re-read from the backend: an unbound ref decodes fine and then resolves nothing
        bankManager.clearCache();
        Optional<ProfileSection> owner = bankManager.refresh(bankId).join().owner.resolve().join();
        assertTrue(owner.isPresent(), "a ref inside a network entity must resolve the plugin's PDSection");
        assertEquals(uuid, owner.get().getStorageKey());

        CachingManager<UUID, ProfileSection> sectionManager =
                PlayerController.get().getBinding(ProfileSection.class).getManager();
        sectionManager.clearCache();
        Optional<Bank> reachedBank = sectionManager.refresh(uuid).join().bank.resolve().join();
        assertTrue(reachedBank.isPresent(), "and a ref inside the PDSection must reach back into the network entity");
        assertEquals(300, reachedBank.get().balance);
    }

    // ------------------------------------------------------------------
    // a plugin whose only storage is the network facade
    // ------------------------------------------------------------------

    @Test
    void aPluginThatOnlyUsesTheNetworkIsStillSweptOnUnregister() {
        boot("ref_sweep");
        ECPluginData plugin = fakePlugin();

        ECNetworkStorage network = ECNetworkStorage.of(plugin);
        network.manager(EntityDescriptor.builder(UUID.class, Bank.class)
                .collection("swept_banks")
                .keyExtractor(bank -> bank.id)
                .codec(new JacksonJsonCodec<>(Bank.class))
                .build(), CachePolicy.always());
        assertTrue(ECStorageRegistries.of(plugin).isRegistered(Bank.class));

        PlayerController.unregisterPDSections(plugin);

        //this plugin owns no section at all, so an early return over "nothing registered" would skip the
        //registry drop and leave its types - and through them its classloader - alive with nothing to free them
        assertFalse(ECStorageRegistries.of(plugin).isRegistered(Bank.class),
                "unregistering a section-less plugin must still drop its per-plugin registry");
    }

    // ------------------------------------------------------------------
    // the framework's own rows resolve upwards, into the global registry
    // ------------------------------------------------------------------

    @Test
    void theAccountAndCooldownCodecsBindARefToTheGlobalRegistry() throws IOException {
        ECPluginData plugin = fakePlugin();
        boot("ref_global");
        RefRegistry global = PlayerController.get().registries().global();

        //an entity published in the global registry - the only place a framework row is allowed to reach
        Storage side = PlayerController.get().registry().getDefaultBackend();
        EntityDescriptor<UUID, Landmark> landmarks = EntityDescriptor.builder(UUID.class, Landmark.class)
                .collection("global_landmarks")
                .keyExtractor(landmark -> landmark.id)
                .codec(new JacksonJsonCodec<>(Landmark.class))
                .build();
        UUID landmarkId = UUID.randomUUID();
        global.manager(landmarks, side, CachePolicy.always())
                .saveAndCache(new Landmark(landmarkId, "Spawn")).join();

        //and one that lives in a PLUGIN's child registry, which nothing above it may reach
        UUID bankId = UUID.randomUUID();
        ECNetworkStorage.of(plugin).manager(EntityDescriptor.builder(UUID.class, Bank.class)
                .collection("private_banks")
                .keyExtractor(bank -> bank.id)
                .codec(new JacksonJsonCodec<>(Bank.class))
                .build(), CachePolicy.always()).saveAndCache(new Bank(bankId, 10)).join();

        //Account and ServerCooldownRow declare no Ref field of their own, so the wiring is observed on the
        //codec the framework actually reads those rows through: it is the codec that binds a decoded ref,
        //and before it was built ref-aware a Ref in either row resolved to nothing at all
        for (String collection : new String[]{Accounts.COLLECTION, ServerCooldowns.DEFAULT_COLLECTION}) {
            Optional<Landmark> resolved = refThroughCodecOf(collection, landmarkId, Landmark.class)
                    .resolve().join();
            assertTrue(resolved.isPresent(), "the codec of '" + collection + "' must bind a decoded ref to"
                    + " the global registry, where the framework's own managers live");
            assertEquals("Spawn", resolved.get().name);

            //upwards only: resolution walks child to parent, so a framework row never depends on a
            //plugin being installed - it fails loudly instead of quietly reaching into one
            CompletionException unreachable = assertThrows(CompletionException.class,
                    () -> refThroughCodecOf(collection, bankId, Bank.class).resolve().join());
            assertTrue(unreachable.getCause().getMessage().contains(Bank.class.getName()),
                    unreachable.getCause().getMessage());
        }

        //what is being asserted above is the WIRING, not Jackson's ability to read a key: the plain
        //bridge - the codec these rows would get from the registry-less overload - cannot read a Ref at all
        BackendDefinition definition = PlayerController.get().storageConfig()
                .getBackend(NETWORK_BACKEND).orElseThrow(AssertionError::new);
        ObjectMapper plain = ((ObjectMapperAware) BindingResolver.defaultCodec(definition, Account.class))
                .objectMapper();
        assertThrows(JsonProcessingException.class, () -> plain.readValue("\"" + landmarkId + "\"",
                plain.getTypeFactory().constructParametricType(Ref.class, UUID.class, Landmark.class)));
    }

    /** A ref decoded through the very codec the framework reads {@code collection} with. */
    private <V> Ref<UUID, V> refThroughCodecOf(String collection, UUID key, Class<V> type) throws IOException {
        EntityDescriptor<?, ?> descriptor = PlayerController.get().registry()
                .getClaimedDescriptor(NETWORK_BACKEND, collection);
        ObjectMapper mapper = ((ObjectMapperAware) descriptor.codec()).objectMapper();
        JavaType refType = mapper.getTypeFactory().constructParametricType(Ref.class, UUID.class, type);
        return mapper.readValue("\"" + key + "\"", refType);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A network backend of its own, so the network family is not mixed with the PlayerData one. */
    private File boot(String dbName) {
        File storageYml = Storages.h2(dbName)
                .extraBackend(NETWORK_BACKEND, "    type: h2",
                        "    url: \"jdbc:h2:mem:" + dbName + "_net;DB_CLOSE_DELAY=-1\"")
                .networkBackendId(NETWORK_BACKEND)
                .writeTo(tempDir);
        PlayerController.initialize(storageYml);
        return storageYml;
    }

    private ECPluginData fakePlugin() {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(PLUGIN_NAME, tempDir.resolve(PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(new FakePlugin());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** Stands in for the platform's plugin object; only its identity matters. */
    public static final class FakePlugin {
    }

    /** A plugin's network-wide entity, pointing back at the per-player section that owns it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Bank {
        public UUID id;
        public int balance;
        public Ref<UUID, ProfileSection> owner;

        public Bank() {
        }

        Bank(UUID id, int balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    /** A per-player section pointing into the plugin's network collection.
     *  Inherits {@code @JsonAutoDetectFieldsOnly} from PDSection - a field-visibility override here would
     *  re-enable getter detection and hit the delegating getters. */
    public static class ProfileSection extends PDSection {
        public Ref<UUID, Bank> bank;
    }

    /** An entity published in the global registry, reachable from a framework row and from nothing below it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Landmark {
        public UUID id;
        public String name;

        public Landmark() {
        }

        Landmark(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
