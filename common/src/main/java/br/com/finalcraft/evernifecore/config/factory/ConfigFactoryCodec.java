package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.binding.introspect.EveryConfigModule;
import br.com.finalcraft.everyconfig.binding.merge.LifecycleGraphWalker;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.codec.JacksonConfig;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * A storage {@link Codec} that gives the {@link ConfigFactory} type authority - the platform types it
 * teaches and the {@code ConfigLifecycle} hooks EveryConfig fires - over the EveryDatabase storage flow, so
 * a type behaves identically whether it lands in a {@code Config} or in a persisted entity. It bridges the
 * two otherwise-parallel Jackson stacks: EveryConfig (the {@code ConfigFactory} registry + binding layer)
 * and EveryDatabase (the byte-oriented storage codec).
 *
 * <h2>Byte-edge mapper</h2>
 * Every variant owns a {@code byteEdge} {@link ObjectMapper} built from scratch for its wire format
 * ({@link JsonMapper} for JSON, {@link YAMLMapper} for YAML) and layered with, in order:
 * <ol>
 *   <li>{@link JacksonConfig#storageSafe(ObjectMapper)} - the EveryDatabase read contract (ISO-8601 dates,
 *       tolerance of unknown keys); it preserves a map's insertion order, so a sequence-carrying map
 *       (numbered segments, ordered slots) survives a round-trip;</li>
 *   <li>{@link EveryConfigModule} - EveryConfig binding semantics (enum-by-name, {@code @Key} naming);</li>
 *   <li>{@link ConfigFactory#sharedTypeModule()} - the platform type serializers/deserializers.</li>
 * </ol>
 * The byte-edge is what {@link #objectMapper()} exposes (satisfying {@link ObjectMapperAware}, which
 * {@code EntitySchemaMigratingCodec.wrap} and {@code IndexValueExtractor} require).
 *
 * <p><b>Why the byte-edge is built from scratch and never borrowed from {@link ConfigFactory#inMemory()}:</b>
 * an in-memory config's own mapper is a {@code YAMLMapper} (the in-memory codec starts from a {@code .yml}
 * codec), so reusing it for a JSON variant would emit YAML labelled {@code application/json} and break the
 * SQL/Mongo/InMemory backends that parse the payload as JSON. Only the lifecycle host below may be that
 * YAML-based config, because there it merely builds the intermediate tree (format-independent); the wire
 * bytes are always produced by the {@code byteEdge}.
 *
 * <h2>Two internal paths, chosen once by {@link LifecycleGraphWalker#mayContainHooks(Class)}</h2>
 * <ul>
 *   <li><b>Fast path</b> (the type's graph carries no hooks): serialize/deserialize the POJO directly
 *       through the {@code byteEdge} - a plain {@code writeValueAsBytes}/{@code readValue} that costs the
 *       same as a bare Jackson codec, only now with the platform types available.</li>
 *   <li><b>Lifecycle path</b> (the type may carry hooks): host a {@link ConfigFactory#inMemory()} config as
 *       the intermediary so the binding layer fires the hooks. On encode, {@code host.bind(type).write("")}
 *       fires {@code PRE_SAVE}/{@code POST_SAVE} (plus the nested walk) - materializing hook-written subtrees
 *       into the host tree - and the {@code byteEdge} then serializes that tree. On decode, the {@code
 *       byteEdge} parses the bytes into a tree, the host adopts it, and {@code host.bind(type).read("")}
 *       fires the load hooks that reconstruct manually-managed state.</li>
 * </ul>
 *
 * <h2>Type-registry staleness</h2>
 * The byte-edge captures the {@link ConfigFactory} registrations present at construction, exactly like the
 * factory's own codecs: a type registered AFTER this codec is built is not seen by it. This is acceptable
 * because a codec is built when its section binds, after the bulk of registrations have run.
 *
 * @param <V> the entity type
 */
public final class ConfigFactoryCodec<V> implements Codec<V>, ObjectMapperAware {

    private final Class<V> type;
    /** Type-aware, storage-safe, format-specific (JSON or YAML); the wire edge and the {@link #objectMapper()}. */
    private final ObjectMapper byteEdge;
    /** {@code "application/json"} or {@code "application/yaml"}. */
    private final String contentType;
    /** Captured once: does this type's graph carry lifecycle hooks (else the fast path is taken)? */
    private final boolean lifecycle;

    private ConfigFactoryCodec(final Class<V> type, final ObjectMapper byteEdge, final String contentType) {
        this.type = type;
        this.byteEdge = byteEdge;
        this.contentType = contentType;
        this.lifecycle = LifecycleGraphWalker.mayContainHooks(type);
    }

    // ---- factories (mirror BindingResolver.defaultCodec's JSON/pretty/YAML choices) ----

    /** Compact JSON - the default for every non-file backend and for a {@code .json} file value. */
    public static <V> ConfigFactoryCodec<V> json(final Class<V> type) {
        return new ConfigFactoryCodec<>(type, mapper(new JsonMapper()), "application/json");
    }

    /** Indented JSON - JSON on a file backend, where a human may open the per-entity file. */
    public static <V> ConfigFactoryCodec<V> jsonPretty(final Class<V> type) {
        return new ConfigFactoryCodec<>(type,
                mapper(new JsonMapper()).enable(SerializationFeature.INDENT_OUTPUT), "application/json");
    }

    /** YAML - only a localfile/groupedfile backend configured for the YAML format. */
    public static <V> ConfigFactoryCodec<V> yaml(final Class<V> type) {
        return new ConfigFactoryCodec<>(type, mapper(new YAMLMapper()), "application/yaml");
    }

    /**
     * Layer the storage read contract (ISO dates, unknown-key tolerance, insertion-order-preserving maps),
     * then EveryConfig's binding semantics, then the {@link ConfigFactory} platform types onto {@code base}.
     * Mutate-and-return (Jackson is configured once at construction, then read-only on the hot path).
     */
    private static <M extends ObjectMapper> M mapper(final M base) {
        JacksonConfig.storageSafe(base);                        // EveryDatabase: ISO dates, map insertion order kept
        base.registerModule(new EveryConfigModule());           // EveryConfig: enum-by-name + @Key introspector
        base.registerModule(ConfigFactory.sharedTypeModule());  // the platform types the factory owns
        return base;
    }

    @Override
    public byte[] encode(final V value) throws CodecException {
        try {
            if (!lifecycle) {
                return byteEdge.writeValueAsBytes(value);          // fast path: type-aware, no host
            }
            final Config host = ConfigFactory.inMemory();
            host.bind(type).write("", value);                      // fires PRE/POST_SAVE + nested walk
            return byteEdge.writeValueAsBytes(host.getRoot());     // serialize the materialized tree
        } catch (final Exception e) {
            throw new CodecException("Failed to encode " + type.getSimpleName(), e);
        }
    }

    @Override
    public V decode(final byte[] data) throws CodecException {
        try {
            if (!lifecycle) {
                return byteEdge.readValue(data, type);             // fast path
            }
            final JsonNode node = byteEdge.readTree(data);
            final Config host = ConfigFactory.inMemory();
            if (node instanceof ObjectNode) {
                host.getRoot().setAll((ObjectNode) node);
            }
            return host.bind(type).read("");                       // fires PRE/POST_LOAD + nested walk
        } catch (final Exception e) {
            throw new CodecException("Failed to decode " + type.getSimpleName(), e);
        }
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public ObjectMapper objectMapper() {
        return byteEdge;
    }
}
