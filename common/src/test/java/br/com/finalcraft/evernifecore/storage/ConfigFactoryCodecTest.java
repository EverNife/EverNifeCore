package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.SectionSchemaStep;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.everyconfig.binding.ConfigContext;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everyconfig.binding.merge.LifecycleGraphWalker;
import br.com.finalcraft.everyconfig.codec.jackson.InMemoryCodec;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 *   <li><b>(refused)</b> a value the read cannot take costs that field, not the entity, and is said out loud;</li>
 *   <li><b>(host)</b> a hosted operation binds through one shared codec, never one built per entity;</li>
 *   <li><b>(versioned)</b> a versioned {@link PDSection} with a {@link SectionSchemaStep} migration keeps its
 *       lifecycle across the migrated read seam - the case that needs the EveryDatabase decode-delegation fix.</li>
 * </ul>
 */
@ECoreTest
class ConfigFactoryCodecTest {

    @BeforeAll
    static void setUp() {
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
    //  map-of-sequence ordering: a FancyFormatter's numbered children ("1".."N") keep insertion order
    //  through the storage codec even past 10 - the case a lexicographic key sort would scramble
    // ==================================================================

    @Test
    void numberedMapFieldKeepsInsertionOrderThroughStorageBeyondTen() {
        // A plain Map field is serialized by Jackson's MapSerializer, which honours the storage codec's
        // map-ordering setting. With numbered-string keys inserted 1..12, a key sort would emit "10" before
        // "2" and corrupt the sequence. This pins that the storage bytes keep insertion order.
        NumberedMapHolder holder = new NumberedMapHolder(UUID.randomUUID());
        List<String> expectedKeys = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            holder.steps.put(String.valueOf(i), "v" + i);
            expectedKeys.add(String.valueOf(i));
        }

        NumberedMapHolder viaBridge = roundTrip(
                ConfigFactoryCodec.json(NumberedMapHolder.class), NumberedMapHolder.class, h -> h.id, holder);

        // decode fills the LinkedHashMap in on-disk order, so its key iteration IS the persisted order
        assertEquals(expectedKeys, new ArrayList<>(viaBridge.steps.keySet()),
                "a numbered-key Map must keep insertion order through storage (a key sort puts \"10\" before \"2\")");
    }

    @Test
    void fancyFormatterKeepsChildOrderThroughStorageBeyondTenSegments() {
        // FancyTextConfigCodec serializes a formatter's children as a map keyed "1".."N". A FancyText-bearing
        // holder takes ConfigFactoryCodec's lifecycle path, which materializes the value into a JsonNode tree
        // before writing; a JsonNode's fields are immune to a map key-sort (it is not a java.util.Map), so a
        // formatter survived even the old sorting default. This end-to-end guardrail pins that the chat-segment
        // sequence round-trips in order past 10 children.
        int childCount = 12;
        FancyFormatter formatter = new FancyFormatter();
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= childCount; i++) {
            String segment = "seg" + i;
            formatter.append(new FancySegment(segment));
            expected.add(segment);
        }

        FancyHolder viaBridge = roundTrip(
                ConfigFactoryCodec.json(FancyHolder.class), FancyHolder.class, h -> h.id,
                new FancyHolder(UUID.randomUUID(), formatter));

        assertTrue(viaBridge.message instanceof FancyFormatter, "a formatter field must read back as a FancyFormatter");
        List<String> actual = new ArrayList<>();
        for (FancyText child : ((FancyFormatter) viaBridge.message).getFancyTextList()) {
            // a piece with no text carries no ordering information, so only the meaningful ones are compared
            if (child.getText() != null && !child.getText().isEmpty()) {
                actual.add(child.getText());
            }
        }
        assertEquals(expected, actual,
                "the >= 10 formatter segments must keep insertion order through the storage round-trip");
    }

    // ==================================================================
    //  what a refused payload costs: the field, not the entity - on either path
    // ==================================================================

    @Test
    void aValueTheReadRefusesCostsThatFieldInsteadOfTheWholeEntity() {
        // Holder carries no hooks, so it is read straight off the wire - and a field that read cannot take
        // must still not take the entity down with it: the payload is retried through the lenient bind,
        // which keeps that field's default and says what it dropped.
        UUID id = UUID.randomUUID();
        byte[] unreadable = ("{\"id\":\"" + id + "\",\"pos\":\"not-a-position\"}")
                .getBytes(StandardCharsets.UTF_8);

        ConfigFactoryCodec<Holder> bridge = ConfigFactoryCodec.json(Holder.class);
        assertFalse(LifecycleGraphWalker.mayContainHooks(Holder.class, bridge.objectMapper()),
                "the subject has to be a type the bridge reads directly - else this proves nothing");

        AtomicReference<Holder> read = new AtomicReference<>();
        List<String> logged = Logs.capture(() -> read.set(bridge.decode(unreadable)));

        assertEquals(id, read.get().id, "the rest of the entity loaded");
        assertNull(read.get().pos, "the refused field holds what a brand new entity holds");
        assertTrue(logged.toString().contains("pos"), "the report names the field that was lost: " + logged);
        assertTrue(logged.toString().contains("next save"),
                "and what the loss costs if nothing is done: " + logged);
    }

    // ==================================================================
    //  cost of the lifecycle path: one host codec for the codec's whole life, not one per operation
    // ==================================================================

    @Test
    void everyLifecycleOperationHostsItsTreeOnTheOneSharedCodec() {
        // A host codec carries a Jackson mapper, which is expensive to copy and which EveryConfig keys its
        // binding schema cache by: building one per operation would pay both on every entity read or written.
        HostSpy first = new HostSpy(UUID.randomUUID());
        HostSpy second = new HostSpy(UUID.randomUUID());

        Codec<HostSpy> bridge = ConfigFactoryCodec.json(HostSpy.class);
        bridge.encode(first);
        bridge.encode(second);

        assertNotNull(first.host, "the lifecycle path ran, so the hook was handed a host config");
        assertSame(first.host, second.host, "two operations of one bridge host on the same codec");
        assertSame(ConfigFactory.inMemory().getCodec(), first.host,
                "and it is the factory's shared file-less codec: inMemory() hands out a tree, not a mapper");
    }

    // ==================================================================
    //  versioned + migration + lifecycle: the bridge codec must delegate its decode so the
    //  lifecycle hooks still fire when a payload is read through the migration seam
    // ==================================================================

    @Test
    void versionedSectionKeepsLifecycleAcrossTheMigratedReadSeam() throws Exception {
        // the migration step is expressed as a SectionSchemaStep over the rich, type-aware ConfigSection:
        // read 'label' and derive 'grade' from it.
        SectionSchemaStep step = section -> {
            String label = section.getValue("label", String.class);
            section.setValue("grade", label == null ? 0 : label.length());
        };
        // register the chain through the real builder adapter (SectionSchemaStep -> EntitySchemaStep)
        List<EntitySchemaMigrations.Step> chain = PDSectionConfiguration
                .builder(null, VersionedBag.class, "versionedbag")
                .migration(1, step)
                .build()
                .getMigrations();
        EntitySchemaMigrations.registerChain(VersionedBag.class, chain);
        try {
            UUID key = UUID.randomUUID();
            // a stale row written by an older build: schema v1, no 'grade' yet
            byte[] v1 = ("{\"schemaVersion\":1,\"uuid\":\"" + key + "\",\"label\":\"hello\"}")
                    .getBytes(StandardCharsets.UTF_8);

            // GREEN: the bridge is the inner codec, so lifecycle survives the migrated decode path
            Codec<VersionedBag> bridge = EntitySchemaMigratingCodec.wrap(
                    VersionedBag.class, ConfigFactoryCodec.json(VersionedBag.class), "uuid");
            VersionedBag migrated = bridge.decode(v1);
            assertEquals(2, migrated.getSchemaVersion(), "the payload was upcast to the current version");
            assertEquals(5, migrated.grade, "the SectionSchemaStep migration ran (grade = label.length())");
            assertEquals(5, migrated.restored,
                    "postLoad fired on the MIGRATED payload - the decode delegated to the bridge inner codec");

            // NEGATIVE: a plain inner codec migrates the tree the same way, but fires no lifecycle, so the
            // hook-populated state is lost even though the migration itself ran. This isolates that the BRIDGE
            // (not the migration wrapper) is what carries lifecycle through the migrated seam.
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

    /** Reports, from inside a hook, which codec the host config it was handed runs on. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class HostSpy implements ConfigLifecycle {
        public UUID id;
        @JsonIgnore
        public transient InMemoryCodec host;

        public HostSpy() {
        }

        HostSpy(UUID id) {
            this.id = id;
        }

        @Override
        public void postSave(ConfigContext context) {
            this.host = (InMemoryCodec) context.section().getConfig().getCodec();
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

    /** A holder with a plain numbered-key {@link LinkedHashMap} field - the direct MapSerializer path a
     *  storage-side key sort would scramble. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class NumberedMapHolder {
        public UUID id;
        public LinkedHashMap<String, String> steps = new LinkedHashMap<>();

        public NumberedMapHolder() {
        }

        NumberedMapHolder(UUID id) {
            this.id = id;
        }
    }

    /** A holder whose payload is a {@link FancyText}, declared as the base type so a {@link FancyFormatter}
     *  still reads back as one through the centrally-registered codec. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class FancyHolder {
        public UUID id;
        public FancyText message;

        public FancyHolder() {
        }

        FancyHolder(UUID id, FancyText message) {
            this.id = id;
            this.message = message;
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
