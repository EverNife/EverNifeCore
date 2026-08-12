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
import br.com.finalcraft.everyconfig.binding.merge.LifecycleGraphWalker;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end-to-end contract for a {@code Ref} living inside a PDSection: the section's default codec is
 * ref-aware and bound to the plugin's shared {@link RefRegistry}, so a {@code Ref} persisted in a section
 * resolves an entity that lives in an {@link ECStorage} whose manager is registered in the SAME registry.
 *
 * <ul>
 *   <li><b>scenario</b> - the user's request: a {@code ProfileSection} with a {@code Ref<UUID, Guild>},
 *       resolved through {@link BindingResolver} without a custom codec, re-read from the backend after a
 *       cache clear, resolves the guild;</li>
 *   <li><b>codec unit</b> - {@link ConfigFactoryCodec#json(Class, RefRegistry)} round-trips a ref and binds
 *       it to the registry, solo and inside a collection;</li>
 *   <li><b>composition</b> - a section that carries refs AND implements {@code ConfigLifecycle} gets both:
 *       the refs come back bound and the hooks fire, on the codec and through the storage flow;</li>
 *   <li><b>cost</b> - a ref-bearing type with no hooks stays on the codec's fast path, because the
 *       ref-aware mapper proves it hook-free;</li>
 *   <li><b>no regression</b> - a ConfigFactory platform type serializes byte-identically with and without a
 *       registry (the ref module never touches a non-ref field).</li>
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

    @Test
    void refsInsideACollectionRoundTripAndBind() {
        RefRegistry reg = new RefRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registerGuilds(reg, new Guild(first, "Gamma"), new Guild(second, "Delta"));

        //List<Ref<..>> hides the Ref one level down: the field's own class is java.util.List, so a graph
        //walk that stops at the field type reads the element as unresolved and rejects the whole type
        Codec<RefListHolder> codec = ConfigFactoryCodec.json(RefListHolder.class, reg);
        RefListHolder holder = new RefListHolder(Arrays.asList(reg.ref(first, Guild.class),
                reg.ref(second, Guild.class)));

        RefListHolder out = codec.decode(codec.encode(holder));
        assertEquals(2, out.guildRefs.size());
        assertEquals(first, out.guildRefs.get(0).key(), "each element serialized as its key");
        assertEquals("Gamma", out.guildRefs.get(0).resolve().join().get().name);
        assertEquals("Delta", out.guildRefs.get(1).resolve().join().get().name);
    }

    // ==================================================================
    //  composition: refs and lifecycle hooks in the SAME type
    // ==================================================================

    @Test
    void aLifecycleTypeCarryingRefsWritesBareKeysBindsThemAndFiresItsHooks() {
        RefRegistry reg = new RefRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registerGuilds(reg, new Guild(first, "Zeta"), new Guild(second, "Eta"));

        Codec<LifecycleRefListHolder> codec = ConfigFactoryCodec.json(LifecycleRefListHolder.class, reg);
        LifecycleRefListHolder holder = new LifecycleRefListHolder(
                Arrays.asList(reg.ref(first, Guild.class), reg.ref(second, Guild.class)));

        byte[] wire = codec.encode(holder);
        String json = new String(wire, StandardCharsets.UTF_8);
        assertTrue(json.contains("[\"" + first + "\",\"" + second + "\"]"),
                "each ref must be written as its bare key, never as an embedded entity: " + json);
        assertTrue(holder.saved, "postSave fired on the entity being written");

        LifecycleRefListHolder out = codec.decode(wire);
        assertTrue(out.loaded, "postLoad fired on the entity being read");
        assertEquals(2, out.guildRefs.size());
        assertEquals("Zeta", out.guildRefs.get(0).resolve().join().get().name,
                "a ref decoded on the hook-firing path is bound to the registry all the same");
        assertEquals("Eta", out.guildRefs.get(1).resolve().join().get().name);
    }

    @Test
    void aLifecycleSectionCarryingRefsRegistersResolvesAndFiresItsHooks() throws IOException {
        RefRegistry reg = new RefRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registerGuilds(reg, new Guild(first, "Theta"), new Guild(second, "Iota"));

        StorageContext ctx = memoryStorage();
        PDSectionBinding<TrackerSection> binding = assertDoesNotThrow(() -> BindingResolver.resolve("PluginY",
                        PDSectionConfiguration.builder(null, TrackerSection.class, "trackersection").build(),
                        ctx.parsed, ctx.registry, reg),
                "a section that carries refs AND implements ConfigLifecycle must resolve, not be refused");
        CachingManager<UUID, TrackerSection> tracker = binding.getManager();

        UUID pid = UUID.randomUUID();
        TrackerSection section = new TrackerSection();
        section.setStorageKey(pid);
        section.visitedGuilds = new ArrayList<>(Arrays.asList(reg.ref(first, Guild.class),
                reg.ref(second, Guild.class)));
        tracker.saveAndCache(section).join();

        tracker.clearCache();
        TrackerSection reread = tracker.refresh(pid).join();

        assertEquals(2, reread.visitedGuilds.size());
        assertEquals("Theta", reread.visitedGuilds.get(0).resolve().join().get().name,
                "the refs stored by a lifecycle section still resolve after a re-read from the backend");
        assertEquals(2, reread.visitedCount,
                "postSave wrote extra.visited and postLoad read it back: the hooks fired through storage");
    }

    // ==================================================================
    //  cost: refs alone never push a type off the fast path
    // ==================================================================

    @Test
    void aRefBearingTypeWithoutHooksStaysOnTheFastPath() {
        ConfigFactoryCodec<RefListHolder> codec = ConfigFactoryCodec.json(RefListHolder.class, new RefRegistry());

        assertFalse(LifecycleGraphWalker.mayContainHooks(RefListHolder.class, codec.objectMapper()),
                "the ref-aware mapper writes a Ref with a serializer of its own, so the holder is proved "
                        + "hook-free and no operation of it ever hosts a config");
        assertTrue(LifecycleGraphWalker.mayContainHooks(RefListHolder.class),
                "without that mapper the same type cannot be proved: a Ref's erased key field is an Object");
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
    //  helpers
    // ==================================================================

    private static void registerGuild(RefRegistry reg, UUID gid, String name) {
        registerGuilds(reg, new Guild(gid, name));
    }

    /** One manager per registry - a second {@code register} for the same type is refused by design. */
    private static void registerGuilds(RefRegistry reg, Guild... guilds) {
        ECStorage storage = ECStorage.open(BackendDefinition.memory()).join();
        EntityDescriptor<UUID, Guild> desc = EntityDescriptor.builder(UUID.class, Guild.class)
                .collection("guilds")
                .keyExtractor(g -> g.id)
                .codec(storage.defaultCodec(Guild.class))
                .build();
        CachingManager<UUID, Guild> manager = storage.manager(desc, CacheOptions.of(CachePolicy.always()), reg);
        for (Guild guild : guilds) {
            manager.saveAndCache(guild).join();
        }
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
                "network:",
                "  storage-backend-id: main_storage",
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

    /** Refs held one level down, inside a collection - the shape a PDSection uses to list what it touched. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class RefListHolder {
        public List<Ref<UUID, Guild>> guildRefs;

        public RefListHolder() {
        }

        RefListHolder(List<Ref<UUID, Guild>> guildRefs) {
            this.guildRefs = guildRefs;
        }
    }

    /** Refs plus hooks in one type: the codec must bind the refs AND fire the lifecycle. The two flags are
     *  kept out of the payload so they report only what fired on this instance. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class LifecycleRefListHolder implements ConfigLifecycle {
        public List<Ref<UUID, Guild>> guildRefs;
        @JsonIgnore
        public transient boolean saved = false;
        @JsonIgnore
        public transient boolean loaded = false;

        public LifecycleRefListHolder() {
        }

        LifecycleRefListHolder(List<Ref<UUID, Guild>> guildRefs) {
            this.guildRefs = guildRefs;
        }

        @Override
        public void postSave(ConfigContext context) {
            this.saved = true;
        }

        @Override
        public void postLoad(ConfigContext context) {
            this.loaded = true;
        }
    }

    /** The same composition as a real per-player section: refs to resolve, plus a hook that completes state
     *  the payload does not carry on its own. */
    public static class TrackerSection extends PDSection implements ConfigLifecycle {
        public List<Ref<UUID, Guild>> visitedGuilds = new ArrayList<>();
        @JsonIgnore
        public transient int visitedCount = -1;

        /** Stamps the storage key directly (a detached test instance has no attached PlayerData). */
        void setStorageKey(UUID id) {
            this.uuid = id;   // inherited protected key field
        }

        @Override
        public void postSave(ConfigContext context) {
            context.section().setValue("extra.visited", visitedGuilds.size());
        }

        @Override
        public void postLoad(ConfigContext context) {
            this.visitedCount = context.section().getInt("extra.visited", -1);
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

}
