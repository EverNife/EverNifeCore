package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.SectionSchemaStep;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import br.com.finalcraft.everyconfig.binding.ConfigContext;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storages;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mechanism gate for the ConfigFactory storage bridge (InMemory backend, no Bukkit). Each case proves
 * one gap the bridge closes and pins the negative - the plain {@link JacksonJsonCodec} that fails today -
 * so the "fails without the bridge, passes with it" contract is checked, not assumed:
 *
 * <ul>
 *   <li><b>(a)</b> a platform type ({@code Pos}) taught only to the {@link ConfigFactory} survives a
 *       round-trip through the bridge but not through plain Jackson;</li>
 *   <li><b>(b)</b> a {@code ConfigLifecycle} entity's {@code postSave}/{@code postLoad} fire through the
 *       storage flow (the motivating {@code FCPlayerInventory.extraInvs} shape) - lost with plain Jackson;</li>
 *   <li><b>(c)</b> an enum with an overridden {@code toString()} serializes by {@code name()};</li>
 *   <li><b>(fast-path)</b> a plain POJO round-trips to the same object through the bridge and plain Jackson;</li>
 *   <li><b>(versioned)</b> a versioned {@link PDSection} with a {@link SectionSchemaStep} migration keeps its
 *       lifecycle across the migrated read seam - the case that needs the EveryDatabase decode-delegation fix.</li>
 * </ul>
 */
class ConfigFactoryCodecTest {

    @BeforeAll
    static void setUp() {
        // ConfigFactory's static init calls getPlatform().registerConfigTypes(); seed a no-op platform first.
        TestPlatformFixture.ensureInstalled();
        // Teach the factory a type plain Jackson cannot serialize on its own (no getters, no no-arg ctor).
        ConfigFactory.register(Pos.class).asString(Pos::serialize, Pos::parse);
    }

    // ------------------------------------------------------------------
    //  storage round-trip helper (InMemory, no Docker) - mirrors EveryDatabaseEmbeddingSmokeTest
    // ------------------------------------------------------------------

    private static <E> E roundTrip(Codec<E> codec, Class<E> type, Function<E, UUID> key, E entity) {
        EntityDescriptor<UUID, E> descriptor = EntityDescriptor
                .builder(UUID.class, type)
                .collection("bridge_test")
                .keyExtractor(key)
                .codec(codec)
                .build();
        InMemoryStorage storage = Storages.createInMemory();
        storage.init().join();
        try {
            Repository<UUID, E> repo = storage.repository(descriptor);
            repo.save(entity).join();
            Optional<E> found = repo.find(key.apply(entity)).join();
            assertTrue(found.isPresent(), "the entity must be found after save");
            return found.get();
        } finally {
            storage.close().join();
        }
    }

    // ==================================================================
    //  (a) a platform type as a field: bridge preserves it, plain Jackson does not
    // ==================================================================

    @Test
    void platformTypeAsFieldSurvivesTheBridgeButNotPlainJackson() {
        Holder holder = new Holder(UUID.randomUUID(), Pos.of(3, 7));

        // the bridge carries the ConfigFactory type authority into storage
        Holder viaBridge = roundTrip(ConfigFactoryCodec.json(Holder.class), Holder.class, h -> h.id, holder);
        assertEquals(Pos.of(3, 7), viaBridge.pos, "the registered Pos type must survive the bridge round-trip");

        // the negative: plain Jackson has no idea how to (de)serialize Pos - it must NOT reproduce it
        assertFalse(plainJacksonPreservesPos(holder),
                "plain JacksonJsonCodec must fail to round-trip a ConfigFactory-only type - else the gap is unproven");
    }

    /** True only if a plain {@link JacksonJsonCodec} round-trip reproduces {@code pos} (it must not). */
    private static boolean plainJacksonPreservesPos(Holder holder) {
        try {
            Holder out = roundTrip(new JacksonJsonCodec<>(Holder.class), Holder.class, h -> h.id, holder);
            return holder.pos.equals(out.pos);
        } catch (Throwable failedToSerializeTheOpaqueType) {
            return false;
        }
    }

    // ==================================================================
    //  (b) ConfigLifecycle through storage - the motivating extraInvs case
    // ==================================================================

    @Test
    void lifecycleHooksFireThroughTheStorageFlow() {
        Bag bag = new Bag(UUID.randomUUID(), Arrays.asList("sword", "shield", "potion"));

        Bag viaBridge = roundTrip(ConfigFactoryCodec.json(Bag.class), Bag.class, b -> b.id, bag);
        assertEquals(Arrays.asList("sword", "shield", "potion"), viaBridge.items, "the visible field must survive");
        assertEquals(3, viaBridge.restored,
                "postSave wrote extra.count and postLoad read it back: both lifecycle hooks fired through storage");

        // the negative: plain Jackson never fires ConfigLifecycle - the hook-written state is silently lost
        Bag viaPlain = roundTrip(new JacksonJsonCodec<>(Bag.class), Bag.class, b -> b.id, bag);
        assertEquals(Arrays.asList("sword", "shield", "potion"), viaPlain.items, "the visible field still survives");
        assertEquals(0, viaPlain.restored,
                "plain JacksonJsonCodec fires no lifecycle: extra.count is never written, restored stays 0");
    }

    // ==================================================================
    //  (c) enum-by-name authority: name() wins over an overridden toString()
    // ==================================================================

    @Test
    void enumSerializesByNameNotByOverriddenToString() {
        EnumHolder holder = new EnumHolder(UUID.randomUUID(), Rank.GOLD);

        // the wire form the bridge emits carries name(), never the custom toString()
        byte[] wire = ConfigFactoryCodec.json(EnumHolder.class).encode(holder);
        String json = new String(wire, StandardCharsets.UTF_8);
        assertTrue(json.contains("GOLD"), "the enum must serialize by name(): " + json);
        assertFalse(json.contains("tier-gold"), "the overridden toString() must NOT win: " + json);

        // and it still round-trips to the same constant through storage
        EnumHolder viaBridge = roundTrip(ConfigFactoryCodec.json(EnumHolder.class), EnumHolder.class, h -> h.id, holder);
        assertEquals(Rank.GOLD, viaBridge.rank);
    }

    // ==================================================================
    //  (fast-path) a plain POJO: bridge and plain Jackson agree
    // ==================================================================

    @Test
    void plainPojoRoundTripsIdenticallyOnBothCodecs() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("b", 2);
        scores.put("a", 1);
        Plain plain = new Plain(UUID.randomUUID(), 42, Arrays.asList("x", "y"), scores);

        Plain viaBridge = roundTrip(ConfigFactoryCodec.json(Plain.class), Plain.class, p -> p.id, plain);
        Plain viaPlain = roundTrip(new JacksonJsonCodec<>(Plain.class), Plain.class, p -> p.id, plain);

        // a hook-free, custom-type-free POJO takes the bridge's fast path: same object as plain Jackson
        // (any byte difference would be only Map-entry ordering, which equals() ignores)
        assertEquals(plain, viaBridge, "the fast path must not change a plain POJO");
        assertEquals(viaBridge, viaPlain, "the fast path must agree with the bare Jackson codec");
    }

    // ==================================================================
    //  (versioned + migration + lifecycle) - the case that needs the F1 decode-delegation fix
    // ==================================================================

    @Test
    void versionedSectionKeepsLifecycleAcrossTheMigratedReadSeam() throws Exception {
        // the migration step is expressed as a SectionSchemaStep over the rich, type-aware ConfigSection
        // (the F3 surface): read 'label' and derive 'grade' from it.
        SectionSchemaStep step = section -> {
            String label = section.getValue("label", String.class);
            section.setValue("grade", label == null ? 0 : label.length());
        };
        // register the chain through the real F3 builder adapter (SectionSchemaStep -> EntitySchemaStep)
        List<EntitySchemaMigrations.Step> chain = PDSectionConfiguration
                .builder(null, VersionedBag.class)
                .migration(1, step)
                .build()
                .getMigrations();
        EntitySchemaMigrations.registerChain(VersionedBag.class, chain);
        try {
            UUID key = UUID.randomUUID();
            // a stale row written by an older build: schema v1, no 'grade' yet
            byte[] v1 = ("{\"schemaVersion\":1,\"uuid\":\"" + key + "\",\"label\":\"hello\"}")
                    .getBytes(StandardCharsets.UTF_8);

            // GREEN: the bridge is the inner codec, so lifecycle survives the migrated decode path (F1 fix)
            Codec<VersionedBag> bridge = EntitySchemaMigratingCodec.wrap(
                    VersionedBag.class, ConfigFactoryCodec.json(VersionedBag.class), "uuid");
            VersionedBag migrated = bridge.decode(v1);
            assertEquals(2, migrated.getSchemaVersion(), "the payload was upcast to the current version");
            assertEquals(5, migrated.grade, "the SectionSchemaStep migration ran (grade = label.length())");
            assertEquals(5, migrated.restored,
                    "postLoad fired on the MIGRATED payload - the decode delegated to the bridge inner codec");

            // NEGATIVE: a plain inner codec migrates the tree the same way, but fires no lifecycle, so the
            // hook-populated state is lost even though the migration itself ran. This isolates that the BRIDGE
            // (not the migration wrapper) is what carries lifecycle through the migrated seam. The stricter
            // "red without the F1 fix" - where even the bridge inner would be bypassed - is covered by F1's own
            // verification (the pre-fix jar is not republished), so it is not re-faked here.
            Codec<VersionedBag> plainInner = EntitySchemaMigratingCodec.wrap(
                    VersionedBag.class, new JacksonJsonCodec<>(VersionedBag.class), "uuid");
            VersionedBag plainMigrated = plainInner.decode(v1);
            assertEquals(5, plainMigrated.grade, "the migration still runs - it lives in the wrapper, not the inner codec");
            assertEquals(0, plainMigrated.restored,
                    "a plain inner codec fires no lifecycle: postLoad never runs, so the hook state is lost");
        } finally {
            EntitySchemaMigrations.clear(VersionedBag.class);
        }
    }

    // ==================================================================
    //  test fixtures
    // ==================================================================

    /** A value type taught to the {@link ConfigFactory} as a string, opaque to plain Jackson (no getters,
     *  no no-arg constructor - Jackson can neither serialize nor instantiate it on its own). */
    static final class Pos {
        private final int x;
        private final int y;

        private Pos(int x, int y) {
            this.x = x;
            this.y = y;
        }

        static Pos of(int x, int y) {
            return new Pos(x, y);
        }

        String serialize() {
            return x + ":" + y;
        }

        static Pos parse(String text) {
            String[] parts = text.split(":");
            return new Pos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pos)) return false;
            Pos pos = (Pos) o;
            return x == pos.x && y == pos.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Holder {
        public UUID id;
        public Pos pos;

        public Holder() {
        }

        Holder(UUID id, Pos pos) {
            this.id = id;
            this.pos = pos;
        }
    }

    /** The analogue of {@code FCPlayerInventory}: a visible field bound normally, plus a {@code @JsonIgnore}
     *  field that only exists because {@code postSave}/{@code postLoad} write and read the tree. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Bag implements ConfigLifecycle {
        public UUID id;
        public List<String> items = new ArrayList<>();
        @JsonIgnore
        public transient int restored = 0;

        public Bag() {
        }

        Bag(UUID id, List<String> items) {
            this.id = id;
            this.items = new ArrayList<>(items);
        }

        @Override
        public void postSave(ConfigContext context) {
            context.section().setValue("extra.count", items.size());
        }

        @Override
        public void postLoad(ConfigContext context) {
            this.restored = context.section().getInt("extra.count", 0);
        }
    }

    /** An enum whose {@code toString()} differs from {@code name()} - the bridge must still emit {@code name()}. */
    enum Rank {
        BRONZE,
        GOLD;

        @Override
        public String toString() {
            return "tier-" + name().toLowerCase();
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class EnumHolder {
        public UUID id;
        public Rank rank;

        public EnumHolder() {
        }

        EnumHolder(UUID id, Rank rank) {
            this.id = id;
            this.rank = rank;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Plain {
        public UUID id;
        public int n;
        public List<String> tags = new ArrayList<>();
        public Map<String, Integer> scores = new LinkedHashMap<>();

        public Plain() {
        }

        Plain(UUID id, int n, List<String> tags, Map<String, Integer> scores) {
            this.id = id;
            this.n = n;
            this.tags = new ArrayList<>(tags);
            this.scores = new LinkedHashMap<>(scores);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Plain)) return false;
            Plain plain = (Plain) o;
            return n == plain.n && Objects.equals(id, plain.id)
                    && Objects.equals(tags, plain.tags) && Objects.equals(scores, plain.scores);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, n, tags, scores);
        }
    }

    /** A versioned per-player section (via {@link PDSection}) that also carries a {@link ConfigLifecycle}
     *  hook, so the migrated-read seam has to preserve BOTH the upcast and the lifecycle. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class VersionedBag extends PDSection implements ConfigLifecycle {
        public String label;
        public int grade;
        @JsonIgnore
        public transient int restored = 0;

        public VersionedBag() {
        }

        @Override
        public void postLoad(ConfigContext context) {
            // reads the field the migration added, so a non-zero value proves the hook ran AFTER the upcast
            this.restored = context.section().getInt("grade", 0);
        }
    }
}
