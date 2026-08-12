package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.factory.ECBuiltinTypes;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.everyconfig.codec.Codec;
import br.com.finalcraft.everyconfig.codec.CodecRegistry;
import br.com.finalcraft.everyconfig.codec.jackson.*;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everyconfig.io.BackStore;
import br.com.finalcraft.everyconfig.selfdescribe.CompactElementCodec;
import br.com.finalcraft.everyconfig.selfdescribe.CompactElementResolver;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The framework's config factory + type registry. It is two things in one door:
 *
 * <ol>
 *   <li><b>The type registry</b> - the single source of truth for how the non-natively-serializable types
 *       of a platform (Bukkit's {@code ItemStack}, {@code Location}, the vector families, {@code FancyText},
 *       ...) cross to and from config. The core registers the built-ins in its {@code static} block; each
 *       platform adds its own during {@code registerConfigTypes()}; other plugins may add theirs during
 *       their own bootstrap.</li>
 *   <li><b>The open factory</b> - opens type-aware {@link Config} handles. The plain {@code open(...)}
 *       overloads mirror {@link Config#open}'s surface but bake in the shared, type-aware codec (chosen from
 *       the file extension). The {@link ECPluginData}-scoped overloads additionally resolve the file under
 *       the plugin's own data folder and seed a standard header - the only overloads that apply that policy.</li>
 * </ol>
 *
 * <p>Because EveryConfig is Jackson-first, "teaching" the library a new type is exactly one Jackson
 * serializer/deserializer pair. Register it once and the whole surface composes for free: {@code setValue},
 * {@code getValue}, {@code getList}, the type as a field of another POJO, as a map value, or as a
 * {@code @KeyIndex} element - all funnel through the one shared {@link ObjectMapper}.
 *
 * <h2>The lock + copy-on-write contract</h2>
 * A shared codec {@code copy()}-isolates its mapper at construction, so a codec's mapper is read-only for
 * life (which is exactly how Jackson wants to be used - configured once, then lock-free on the hot path).
 * Rather than FREEZE registration after the first open, a late {@link #register} is absorbed by rebuilding:
 * the codecs are invalidated under a lock and lazily rebuilt (fresh mappers carrying every registration) on
 * the next open, then swapped in atomically. So:
 * <ul>
 *   <li>registration never throws - good compatibility with plugins that register late;</li>
 *   <li>a config opened AFTER a registration sees the new type; a config already open keeps the codec it
 *       captured (use {@code Config.changeCodec} if a live config must pick up the refreshed one);</li>
 *   <li>the mapper is still built-once-then-read-only, so no per-operation locking and no fighting Jackson's
 *       serializer cache.</li>
 * </ul>
 */
public final class ConfigFactory {

    private ConfigFactory() {
    }

    private static final Object LOCK = new Object();

    /** Every registration, keyed by adapter type so a re-register of a type replaces rather than accumulates
     *  (last one wins, matching Jackson's per-type resolution); iteration order is stable (first-registration
     *  order). Drained into a fresh {@link SimpleModule} on each rebuild. */
    private static final Map<Class<?>, TypeAdapter<?>> ADAPTERS = new LinkedHashMap<>();

    /** The codecs built from the current registrations; {@code null} means "stale, rebuild on next use".
     *  Volatile so a reader sees the swapped-in instance without locking on the hot path. */
    private static volatile Codecs codecs;

    static {
        // Teach the registry the framework's built-in types before any config opens
        ECBuiltinTypes.register();
        EverNifeCore.getPlatform().registerConfigTypes();
    }

    // ---- registration (fluent; allowed at any time) ---------------------

    /** Begin registering how {@code type} crosses to/from config. Chain {@code asMap}/{@code asString}/
     *  {@code jackson}; the chosen terminal invalidates the registry so the next open rebuilds with it. */
    public static <T> TypeAdapter<T> register(final Class<T> type) {
        final TypeAdapter<T> adapter = new TypeAdapter<>(type);
        synchronized (LOCK) {
            ADAPTERS.put(type, adapter);
        }
        return adapter;
    }

    /** Mark the codecs stale so the next use rebuilds them with the latest registrations. */
    private static void invalidate() {
        synchronized (LOCK) {
            codecs = null;
        }
    }

    // ---- open: plain, type-aware (no data folder, no header) ------------

    /** Open (or create) {@code file} with the shared, type-aware codec chosen from its extension. A header is
     *  NOT seeded. For a plugin-scoped file (data folder + header), use {@link #open(ECPluginData, String)}. */
    public static Config open(final File file) {
        return Config.open(file.toPath(), codecForFile(file.getName()));
    }

    /** As {@link #open(File)}, from a {@link Path}. */
    public static Config open(final Path path) {
        return Config.open(path, codecForFile(fileNameOf(path)));
    }

    /** As {@link #open(File)}, from a file-name/path string (resolved relative to the working directory). */
    public static Config open(final String fileName) {
        return open(new File(fileName));
    }

    /** As {@link #open(Path)}, but with an explicitly supplied {@code codec} instead of the extension-inferred
     *  one - mirrors {@link Config#open(Path, Codec)}. The caller owns matching the codec to the file. */
    public static Config open(final Path path, final Codec codec) {
        return Config.open(path, codec);
    }

    /** As {@link #open(Path, Codec)}, also choosing how durably each save must land - mirrors
     *  {@link Config#open(Path, Codec, BackStore.Durability)}. */
    public static Config open(final Path path, final Codec codec, final BackStore.Durability durability) {
        return Config.open(path, codec, durability);
    }

    // ---- open: plugin-scoped (data folder + seeded header) --------------

    /**
     * Open (or create) {@code fileName} inside {@code plugin}'s own data folder, with the shared
     * type-aware codec chosen from the extension and the standard FinalCraft header seeded.
     */
    public static Config open(final ECPluginData plugin, final String fileName) {
        final File dataFolder = plugin.getMetaInfo().getDataFolder();
        dataFolder.mkdirs(); // a real plugin ensures its data folder exists before writing
        final File file = new File(dataFolder, fileName);
        final Config config = Config.open(file.toPath(), codecForFile(fileName));
        config.setHeader(standardHeader(plugin));
        return config;
    }

    /** As {@link #open(ECPluginData, String)}, with an explicitly supplied {@code codec}. */
    public static Config open(final ECPluginData plugin, final String fileName, final Codec codec) {
        final File dataFolder = plugin.getMetaInfo().getDataFolder();
        dataFolder.mkdirs();
        final File file = new File(dataFolder, fileName);
        final Config config = Config.open(file.toPath(), codec);
        config.setHeader(standardHeader(plugin));
        return config;
    }

    // ---- open: header-seeded at an EXPLICIT path (not relocated under the data folder) ------------

    /**
     * Open (or create) {@code file} at its EXACT path (NOT relocated under the plugin's data folder),
     * seeding the standard header attributed to {@code plugin}. Use this for a core file whose location
     * is the caller's to decide - e.g. {@code storage.yml}, which a test or an admin tool may point
     * anywhere - while still stamping it with the same banner every other config carries.
     *
     * <p>{@code plugin} may be {@code null} in a headless runtime that never registered an
     * {@link ECPluginData} (the unit tests): the file then opens with no header, exactly like the plain
     * {@link #open(File)}. This is the deliberate difference from {@link #open(ECPluginData, String)},
     * which relocates under the data folder and therefore requires a real plugin.</p>
     */
    public static Config open(final ECPluginData plugin, final File file) {
        final Config config = open(file);
        if (plugin != null) {
            config.setHeader(standardHeader(plugin));
        }
        return config;
    }

    /** As {@link #open(ECPluginData, File)}, from a {@link Path}. */
    public static Config open(final ECPluginData plugin, final Path path) {
        final Config config = open(path);
        if (plugin != null) {
            config.setHeader(standardHeader(plugin));
        }
        return config;
    }

    /**
     * The standard FinalCraft banner header seeded on a plugin-scoped file (the same one applied by
     * {@link #open(ECPluginData, String)}). Public so a caller that opens a core file by explicit path,
     * or that must PREPEND the banner to a file-specific header of its own, can reuse it verbatim.
     */
    public static String[] standardHeader(final ECPluginData plugin) {
        final IPluginMetaInfo meta = plugin.getMetaInfo();
        // Site used to make this http://patorjk.com/software/taag/#p=display&f=Doom&t=FinalCraft
        return new String[]{
                "-----------------------------------------------------",
                "",
                "        _____ _____              __ _       ",
                "       |  ___/  __ \\            / _(_)      ",
                "       | |__ | /  \\/ ___  _ __ | |_ _  __ _ ",
                "       |  __|| |    / _ \\| '_ \\|  _| |/ _` |",
                "       | |___| \\__/\\ (_) | | | | | | | (_| |",
                "       \\____/ \\____/\\___/|_| |_|_| |_|\\__, |",
                "                                       __/ |",
                "                                      |___/ ",
                "",
                "              EverNife's Config Manager",
                "",
                " Plugin: " + meta.getName(),
                " Author: " + meta.getAuthor(),
                "",
                "-----------------------------------------------------",
        };
    }

    // ---- in-memory + codec selection ------------------------------------

    /** A type-aware, file-less {@link Config}: it binds and coerces every registered type exactly like a real
     *  config (so {@code getValue(path, ItemStack.class)} works), but has no back-store and cannot be saved.
     *  It is the substrate a Jackson adapter uses when it must host a subtree and read it back through the
     *  path-based API rather than the streaming API. Every call hands out a fresh, empty {@link Config} over
     *  the ONE shared {@link #inMemoryCodec()}, so hosting a subtree costs a tree, not a mapper. */
    public static Config inMemory() {
        return Config.inMemory(inMemoryCodec());
    }

    /**
     * The shared, file-less codec behind {@link #inMemory()} - the same type authority the file codecs carry,
     * with no on-disk format. Built once per registration generation and handed out as one instance, because
     * a Jackson {@link ObjectMapper} is expensive to copy and EveryConfig keys its binding schema cache by
     * mapper identity: a codec built per call would pay both costs on every hosted subtree.
     *
     * <p>Reach for it directly when a subtree must be hosted with a mapper of your own (layer a module onto
     * {@code inMemoryCodec().getObjectMapper().copy()} and build one {@link InMemoryCodec} from it, once);
     * for the plain case {@link #inMemory()} is the whole story.
     */
    public static InMemoryCodec inMemoryCodec() {
        return current().inMemory;
    }

    /** A file-less, type-aware {@link ConfigSection} over a fresh in-memory {@link Config} seeded with
     *  {@code node} (empty when {@code null} or not an object). The section resolves
     *  {@code getValue(path, T)}/{@code setValue} through the same registered type adapters a real config
     *  does; it has NO back-store (no file). It is the shared substrate for hosting a raw Jackson subtree
     *  and reading it back through the path-based API - the storage bridge codec, the schema-migration
     *  adapter and {@code McConfigTypes.sectionFrom} all host their tree here. */
    public static ConfigSection inMemorySection(final JsonNode node) {
        final Config host = inMemory();
        if (node instanceof ObjectNode) {
            host.getRoot().setAll((ObjectNode) node);
        }
        return new ConfigSection(host, "");
    }

    /** A fresh Jackson {@link Module} carrying every currently-registered type adapter's serializer/
     *  deserializer pair - the same pairs that augment the EveryConfig codecs in {@link #build()}. It lets
     *  another Jackson stack (the EveryDatabase storage codec) share the exact type authority this factory
     *  owns. Rebuilt on each call so it reflects late registrations, mirroring the copy-on-write contract of
     *  {@link #codecForFile}. The compact list-element forms ({@code asCompactElement}) are deliberately NOT
     *  included - they are an EveryConfig per-codec resolver concern, not a Jackson module - so a type's
     *  solo/field form crosses to storage but its compact list-element form does not. */
    public static Module sharedTypeModule() {
        synchronized (LOCK) {
            final SimpleModule module = new SimpleModule("EverNifeConfigFactoryTypes");
            for (final TypeAdapter<?> adapter : ADAPTERS.values()) {
                adapter.contributeTo(module);
            }
            return module;
        }
    }

    /** Resolve the shared, type-aware codec for {@code fileName}'s extension, rebuilding if a registration
     *  invalidated the codecs since the last open. */
    static Codec codecForFile(final String fileName) {
        return current().byExtension.forFile(fileName);
    }

    /** The current codecs, rebuilt on first use after an invalidation. */
    private static Codecs current() {
        Codecs local = codecs;
        if (local == null) {
            synchronized (LOCK) {
                if (codecs == null) {
                    codecs = build();
                }
                local = codecs;
            }
        }
        return local;
    }

    private static String fileNameOf(final Path path) {
        final Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /** Build fresh codecs carrying every current registration. Called under {@link #LOCK}. */
    private static Codecs build() {
        final SimpleModule module = new SimpleModule("EverNifeConfigFactoryTypes");
        final Map<Class<?>, CompactElementCodec<?>> compact = new HashMap<>();

        for (final TypeAdapter<?> adapter : ADAPTERS.values()) {
            adapter.contributeTo(module);
            adapter.contributeCompact(compact);
        }

        // The compact element form is resolved per-codec (no global registry): a type registered via
        // asCompactElement writes compact ONLY as a list element, while its serializer above owns the solo form.
        final CompactElementResolver resolver = compact.isEmpty()
                ? CompactElementResolver.NONE
                : type -> compact.get(type);

        final CodecRegistry newRegistry = new CodecRegistry();

        // Start from each default codec's mapper (its full storage-safe contract + format settings) and
        // AUGMENT it with the registered types, rather than building a bare mapper that would drop the base
        // contract. Then re-wrap so the codec owns an isolated, read-only copy.
        final YamlCodec yaml = new YamlCodec(augment(new YamlCodec().getObjectMapper(), module), resolver);
        newRegistry.register(yaml);
        newRegistry.register(new TomlCodec(augment(new TomlCodec().getObjectMapper(), module), resolver));
        newRegistry.register(new JsonCodec(augment(new JsonCodec().getObjectMapper(), module), resolver));
        newRegistry.register(new JsoncCodec(augment(new JsoncCodec().getObjectMapper(), module), resolver));

        // The file-less codec shares the YAML mapper's contract: binding behaves the same, and the format
        // settings are inert with no text edge.
        return new Codecs(newRegistry, new InMemoryCodec(yaml.getObjectMapper(), resolver));
    }

    /** One registration generation's codecs, held behind a single reference so a rebuild swaps the
     *  by-extension registry and the file-less codec together instead of one at a time. */
    private static final class Codecs {
        final CodecRegistry byExtension;
        final InMemoryCodec inMemory;

        Codecs(final CodecRegistry byExtension, final InMemoryCodec inMemory) {
            this.byExtension = byExtension;
            this.inMemory = inMemory;
        }
    }

    private static ObjectMapper augment(final ObjectMapper base, final Module module) {
        final ObjectMapper copy = base.copy();
        copy.registerModule(module);
        return copy;
    }

    // ---- the fluent registration + its Jackson bridges ------------------

    /**
     * One type's registration. The three styles cover the whole space a plugin dev meets:
     *  {@link #asMap} for an object-shaped value
     *  {@link #asString} for a compact scalar
     *  {@link #jackson} as the escape hatch when the value needs the full Jackson streaming API
     *  {@link #asCompactElement} is an add-on to any of them: it gives the type a SECOND, compact
     *                            form used only when it is a list element.
     *
     *  The terminal invalidates the registry, so a late registration takes effect on the next open.
     */
    public static final class TypeAdapter<T> {

        private final Class<T> type;
        private JsonSerializer<T> serializer;
        private JsonDeserializer<T> deserializer;
        private CompactElementCodec<T> compactCodec;

        TypeAdapter(final Class<T> type) {
            this.type = type;
        }

        /** Object-shaped: the value becomes a {@code Map} (nested values recurse through the mapper, so a
         *  map that itself holds a registered type just works). */
        public TypeAdapter<T> asMap(final Function<T, Map<String, Object>> encode,
                                    final Function<Map<String, Object>, T> decode) {
            this.serializer = new MapSerializer<>(encode);
            this.deserializer = new MapDeserializer<>(decode);
            invalidate();
            return this;
        }

        /** Scalar-shaped: the value becomes a single string, stored raw (never entity-merged) and usable as
         *  a map key. */
        public TypeAdapter<T> asString(final Function<T, String> encode, final Function<String, T> decode) {
            this.serializer = new StringSerializer<>(encode);
            this.deserializer = new StringDeserializer<>(decode);
            invalidate();
            return this;
        }

        /** Full control: supply the Jackson serializer/deserializer directly. */
        public TypeAdapter<T> jackson(final JsonSerializer<T> serializer, final JsonDeserializer<T> deserializer) {
            this.serializer = serializer;
            this.deserializer = deserializer;
            invalidate();
            return this;
        }

        /**
         * Add-on: a COMPACT element form used ONLY when a value of this type is a list element on the dynamic
         * path ({@code setValue}/{@code getList}) - it is stored as a single string, while the solo/field form
         * stays whatever the serializer above produces. Read is tolerant: an element stored as an
         * object still binds through the mapper. Compose it with {@link #asMap}/{@link #jackson}, e.g.
         * {@code register(Pos.class).jackson(mapSer, mapDeser).asCompactElement(Pos::serialize, Pos::deserialize)}.
         */
        public TypeAdapter<T> asCompactElement(final Function<T, String> encode, final Function<String, T> decode) {
            this.compactCodec = new CompactElementCodec<T>() {
                @Override
                public String encode(final T value) {
                    return encode.apply(value);
                }

                @Override
                public T decode(final String text) {
                    return decode.apply(text);
                }
            };
            invalidate();
            return this;
        }

        void contributeTo(final SimpleModule module) {
            if (serializer != null) {
                module.addSerializer(type, serializer);
            }
            if (deserializer != null) {
                module.addDeserializer(type, deserializer);
            }
        }

        /** Contribute this type's compact element form (if any) to the per-codec resolver table. */
        void contributeCompact(final Map<Class<?>, CompactElementCodec<?>> sink) {
            if (compactCodec != null) {
                sink.put(type, compactCodec);
            }
        }
    }

    private static final class MapSerializer<T> extends JsonSerializer<T> {
        private final Function<T, Map<String, Object>> encode;

        MapSerializer(final Function<T, Map<String, Object>> encode) {
            this.encode = encode;
        }

        @Override
        public void serialize(final T value, final JsonGenerator gen, final SerializerProvider provider)
                throws IOException {
            gen.writeObject(encode.apply(value)); // routes back through the mapper; nested types recurse
        }
    }

    private static final class MapDeserializer<T> extends JsonDeserializer<T> {
        private static final TypeReference<Map<String, Object>> MAP_TYPE =
                new TypeReference<Map<String, Object>>() {
                };

        private final Function<Map<String, Object>, T> decode;

        MapDeserializer(final Function<Map<String, Object>, T> decode) {
            this.decode = decode;
        }

        @Override
        public T deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
            return decode.apply(parser.readValueAs(MAP_TYPE));
        }
    }

    private static final class StringSerializer<T> extends JsonSerializer<T> {
        private final Function<T, String> encode;

        StringSerializer(final Function<T, String> encode) {
            this.encode = encode;
        }

        @Override
        public void serialize(final T value, final JsonGenerator gen, final SerializerProvider provider)
                throws IOException {
            gen.writeString(encode.apply(value));
        }
    }

    private static final class StringDeserializer<T> extends JsonDeserializer<T> {
        private final Function<String, T> decode;

        StringDeserializer(final Function<String, T> decode) {
            this.decode = decode;
        }

        @Override
        public T deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
            return decode.apply(parser.getValueAsString());
        }
    }
}
