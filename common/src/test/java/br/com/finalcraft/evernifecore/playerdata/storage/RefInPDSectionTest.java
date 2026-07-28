package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everyconfig.binding.ConfigContext;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end-to-end contract for a {@code Ref} living inside a PDSection: the section's default codec is now
 * ref-aware and bound to the plugin's shared {@link RefRegistry}, so a {@code Ref} persisted in a section
 * resolves an entity that lives in an {@link ECStorage} whose manager is registered in the SAME registry.
 *
 * <ul>
 *   <li><b>scenario</b> - the user's request: a {@code ProfileSection} with a {@code Ref<UUID, Guild>},
 *       resolved through {@link BindingResolver} without a custom codec, re-read from the backend after a
 *       cache clear, resolves the guild;</li>
 *   <li><b>codec unit</b> - {@link ConfigFactoryCodec#json(Class, RefRegistry)} round-trips a ref and binds
 *       it to the registry;</li>
 *   <li><b>no regression</b> - a ConfigFactory platform type serializes byte-identically with and without a
 *       registry (the ref module never touches a non-ref field);</li>
 *   <li><b>fail-fast</b> - a {@code ConfigLifecycle} type carrying a {@code Ref} is rejected, never silently
 *       decoded to an unbound ref.</li>
 * </ul>
 */
@ECoreTest
class RefInPDSectionTest {

    @BeforeAll
    static void setUp() {
        // a platform type the factory owns, opaque to plain Jackson - used by the no-regression case
        ConfigFactory.register(Coord.class).asString(Coord::serialize, Coord::parse);
    }

    // ==================================================================
    //  scenario: a Ref inside a PDSection resolves an entity in another ECStorage of the same plugin
    // ==================================================================

    @Test
    void refInsidePdSectionResolvesEntityFromAnotherStorage() throws IOException, InterruptedException {
        RefRegistry reg = new RefRegistry();

        // a separate plugin-owned storage holding the Guild entity, its manager registered in the SAME reg
        ECStorage guildStorage = ECStorage.open(BackendDefinition.memory()).join();
        EntityDescriptor<UUID, Guild> guildDesc = EntityDescriptor.builder(UUID.class, Guild.class)
                .collection("guilds")
                .keyExtractor(g -> g.id)
                .codec(guildStorage.defaultCodec(Guild.class))
                .build();
        CachingManager<UUID, Guild> guilds =
                guildStorage.manager(guildDesc, CacheOptions.of(CachePolicy.always()), reg);
        UUID gid = UUID.randomUUID();
        guilds.saveAndCache(new Guild(gid, "Alpha")).join();

        // resolve the section binding with reg as the plugin's shared registry - no custom codec passed,
        // so the automatic default codec must already be ref-aware
        StorageContext ctx = memoryStorage();
        PDSectionBinding<ProfileSection> binding = BindingResolver.resolve("PluginX",
                PDSectionConfiguration.builder(null, ProfileSection.class, "profilesection").build(),
                ctx.parsed, ctx.registry, reg);
        CachingManager<UUID, ProfileSection> profiles = binding.getManager();

        // write a profile whose guildRef points into the guild storage, then wipe the cache and re-read
        UUID pid = UUID.randomUUID();
        ProfileSection profile = new ProfileSection();
        profile.setStorageKey(pid);
        profile.guildRef = reg.ref(gid, Guild.class);
        profiles.saveAndCache(profile).join();

        profiles.clearCache();
        ProfileSection reread = profiles.refresh(pid).join();

        Optional<Guild> resolved = reread.guildRef.resolve().join();
        assertTrue(resolved.isPresent(), "the ref decoded from the backend must be bound to reg and resolve");
        assertEquals(gid, resolved.get().id);
        assertEquals("Alpha", resolved.get().name, "the ref resolves the guild stored in the other ECStorage");

        guildStorage.close().join();
    }

    // ==================================================================
    //  codec unit: json(type, registry) round-trips and binds a Ref
    // ==================================================================

    @Test
    void refAwareCodecRoundTripsAndBinds() {
        RefRegistry reg = new RefRegistry();
        UUID gid = UUID.randomUUID();
        registerGuild(reg, gid, "Beta");

        Codec<RefHolder> codec = ConfigFactoryCodec.json(RefHolder.class, reg);
        RefHolder holder = new RefHolder(UUID.randomUUID(), reg.ref(gid, Guild.class));

        RefHolder out = codec.decode(codec.encode(holder));
        assertEquals(gid, out.guildRef.key(), "the ref serialized as its key and read the key back");
        Optional<Guild> resolved = out.guildRef.resolve().join();
        assertTrue(resolved.isPresent(), "the decoded ref must be bound to reg");
        assertEquals("Beta", resolved.get().name);
    }

    // ==================================================================
    //  no regression: a platform type serializes identically with and without a registry
    // ==================================================================

    @Test
    void platformTypeSerializesIdenticallyWithAndWithoutRegistry() {
        CoordHolder holder = new CoordHolder(UUID.randomUUID(), Coord.of(4, 9));

        byte[] plain = ConfigFactoryCodec.json(CoordHolder.class).encode(holder);
        byte[] withRegistry = ConfigFactoryCodec.json(CoordHolder.class, new RefRegistry()).encode(holder);

        assertArrayEquals(plain, withRegistry,
                "the RefModule must not change a type that carries no Ref - the bridge output is unchanged");

        // and the platform type still survives the ref-aware round-trip (the bridge did not regress)
        CoordHolder out = ConfigFactoryCodec.json(CoordHolder.class, new RefRegistry())
                .decode(withRegistry);
        assertEquals(Coord.of(4, 9), out.coord, "the registered Coord type still round-trips through the bridge");
    }

    // ==================================================================
    //  fail-fast (D1): a ConfigLifecycle type carrying a Ref is rejected, not silently unbound
    // ==================================================================

    @Test
    void lifecycleTypeWithRefFailsFast() {
        RefRegistry reg = new RefRegistry();
        CodecException error = assertThrows(CodecException.class,
                () -> ConfigFactoryCodec.json(LifecycleWithRef.class, reg),
                "a ConfigLifecycle type with a Ref field must be rejected, never decode to an unbound ref");
        assertTrue(error.getMessage().contains("ConfigLifecycle"), error.getMessage());
        assertTrue(error.getMessage().contains(LifecycleWithRef.class.getName()), error.getMessage());

        // the plain bridge (no registry) is unaffected: it never claimed to bind a ref
        assertDoesNotThrow(() -> ConfigFactoryCodec.json(LifecycleWithRef.class),
                "without a registry the plain bridge is unchanged");

        // and the fail-fast is SCOPED: a ConfigLifecycle type WITHOUT a ref still builds ref-aware
        assertDoesNotThrow(() -> ConfigFactoryCodec.json(LifecycleNoRef.class, reg),
                "a lifecycle type with no Ref field must not be rejected");
    }

    // ==================================================================
    //  helpers
    // ==================================================================

    private static void registerGuild(RefRegistry reg, UUID gid, String name) {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        EntityDescriptor<UUID, Guild> desc = EntityDescriptor.builder(UUID.class, Guild.class)
                .collection("guilds")
                .keyExtractor(g -> g.id)
                .codec(storage.defaultCodec(Guild.class))
                .build();
        CachingManager<UUID, Guild> guilds = storage.manager(desc, CacheOptions.of(CachePolicy.always()), reg);
        guilds.saveAndCache(new Guild(gid, name)).join();
    }

    private static final class StorageContext {
        final ParsedStorageConfig parsed;
        final StorageRegistry registry;

        StorageContext(ParsedStorageConfig parsed, StorageRegistry registry) {
            this.parsed = parsed;
            this.registry = registry;
        }
    }

    private StorageContext memoryStorage() throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  main_storage: { enabled: true, type: memory }",
                "default-backend: main_storage",
                "");
        File file = Files.createTempFile("ref_storage_", ".yml").toFile();
        file.deleteOnExit();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        StorageRegistry registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());
        return new StorageContext(parsed, registry);
    }

    // ==================================================================
    //  fixtures
    // ==================================================================

    /** The entity the ref points at - a plain POJO in its own collection. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Guild {
        public UUID id;
        public String name;

        public Guild() {
        }

        Guild(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** A per-player section holding a ref into the guild collection - no custom codec, no lifecycle.
     *  Inherits {@code @JsonAutoDetectFieldsOnly} from PDSection (fields ANY, getters NONE) - adding a
     *  field-visibility override here would re-enable getter detection and hit the delegating getters. */
    public static class ProfileSection extends PDSection {
        public Ref<UUID, Guild> guildRef;

        /** Stamps the storage key directly (a detached test instance has no attached PlayerData). */
        void setStorageKey(UUID id) {
            this.uuid = id;   // inherited protected key field
        }
    }

    /** A plain (non-section) holder of a ref, for the codec-level round-trip. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class RefHolder {
        public UUID id;
        public Ref<UUID, Guild> guildRef;

        public RefHolder() {
        }

        RefHolder(UUID id, Ref<UUID, Guild> guildRef) {
            this.id = id;
            this.guildRef = guildRef;
        }
    }

    /** A holder of a ConfigFactory platform type but NO ref - the no-regression subject. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class CoordHolder {
        public UUID id;
        public Coord coord;

        public CoordHolder() {
        }

        CoordHolder(UUID id, Coord coord) {
            this.id = id;
            this.coord = coord;
        }
    }

    /** A value type taught to the {@link ConfigFactory} as a string, opaque to plain Jackson. */
    static final class Coord {
        private final int x;
        private final int y;

        private Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        static Coord of(int x, int y) {
            return new Coord(x, y);
        }

        String serialize() {
            return x + ":" + y;
        }

        static Coord parse(String text) {
            String[] parts = text.split(":");
            return new Coord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Coord)) return false;
            Coord coord = (Coord) o;
            return x == coord.x && y == coord.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    /** A ConfigLifecycle type that also carries a Ref - the D1 fail-fast subject. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class LifecycleWithRef implements ConfigLifecycle {
        public UUID id;
        public Ref<UUID, Guild> guildRef;

        public LifecycleWithRef() {
        }

        @Override
        public void postLoad(ConfigContext context) {
            // presence of a hook forces the lifecycle path, where the ref cannot be bound
        }
    }

    /** A ConfigLifecycle type with NO Ref - must still build ref-aware (fail-fast is scoped). */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class LifecycleNoRef implements ConfigLifecycle {
        public UUID id;
        public String note;

        public LifecycleNoRef() {
        }

        @Override
        public void postLoad(ConfigContext context) {
        }
    }
}
