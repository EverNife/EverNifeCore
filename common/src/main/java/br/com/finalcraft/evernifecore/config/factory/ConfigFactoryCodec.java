package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.binding.BindResult;
import br.com.finalcraft.everyconfig.binding.LoadIssue;
import br.com.finalcraft.everyconfig.binding.introspect.EveryConfigModule;
import br.com.finalcraft.everyconfig.binding.merge.LifecycleGraphWalker;
import br.com.finalcraft.everyconfig.codec.jackson.InMemoryCodec;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.codec.JacksonConfig;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.jackson.RefModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;

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
 * <h2>Two internal paths, chosen once by {@link LifecycleGraphWalker#mayContainHooks(Class, ObjectMapper)}</h2>
 * <ul>
 *   <li><b>Fast path</b> (the type's graph carries no hooks): serialize/deserialize the POJO directly
 *       through the {@code byteEdge} - a plain {@code writeValueAsBytes}/{@code readValue} that costs the
 *       same as a bare Jackson codec, only now with the platform types available.</li>
 *   <li><b>Lifecycle path</b> (the type may carry hooks): host a file-less {@link Config} on the host codec
 *       as the intermediary so the binding layer fires the hooks. On encode, {@code
 *       host.bind(type).write("")} fires {@code PRE_SAVE}/{@code POST_SAVE} (plus the nested walk) -
 *       materializing hook-written subtrees into the host tree - and the {@code byteEdge} then serializes
 *       that tree. On decode, the {@code byteEdge} parses the bytes into a tree, the host adopts it, and
 *       {@code host.bind(type).readResult("")} fires the load hooks that reconstruct manually-managed
 *       state.</li>
 * </ul>
 * The choice is judged with the byte-edge mapper, so a type that mapper writes with a serializer of its own
 * (a platform type, a {@link Ref}) counts as a leaf: it has no fields in the tree for a hook to hide under,
 * and only a type that genuinely reaches a hook pays for a host. The choice is about hooks alone - it never
 * decides how forgiving a read is, because a refused payload is retried through the host on either path
 * ({@link #decode(byte[])}).
 *
 * <h2>Type-registry staleness</h2>
 * The byte-edge and the host both capture the {@link ConfigFactory} registrations present at construction,
 * exactly like the factory's own codecs: a type registered AFTER this codec is built is not seen by it. This
 * is acceptable because a codec is built when its section binds, after the bulk of registrations have run.
 *
 * <h2>Ref-aware variants</h2>
 * The factories that take a {@link RefRegistry} layer a {@link RefModule} bound to that registry onto BOTH
 * mappers - the byte-edge and the host - so a {@link Ref} is serialized as its key and read back <b>bound</b>
 * to the registry on either path: a ref inside a persisted entity resolves an entity that lives in a manager
 * registered in the same registry, whether or not that entity also carries lifecycle hooks. A {@code null}
 * registry is the plain bridge (no ref support), and its host is the factory's shared file-less codec rather
 * than a mapper of its own.
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
    /** What a hosted operation binds through. Built once because copying a mapper is expensive and
     *  EveryConfig keys its binding schema cache by mapper identity - one per operation would pay both costs
     *  on every entity read or written. */
    private final InMemoryCodec hostCodec;

    private ConfigFactoryCodec(final Class<V> type, final ObjectMapper byteEdge, final String contentType,
                               final RefRegistry registry) {
        this.type = type;
        this.byteEdge = byteEdge;
        this.contentType = contentType;
        this.lifecycle = LifecycleGraphWalker.mayContainHooks(type, byteEdge);
        this.hostCodec = hostCodec(registry);
    }

    // ---- factories (mirror BindingResolver.defaultCodec's JSON/pretty/YAML choices) ----

    /** Compact JSON - the default for every non-file backend and for a {@code .json} file value. */
    public static <V> ConfigFactoryCodec<V> json(final Class<V> type) {
        return json(type, null);
    }

    /** Indented JSON - JSON on a file backend, where a human may open the per-entity file. */
    public static <V> ConfigFactoryCodec<V> jsonPretty(final Class<V> type) {
        return jsonPretty(type, null);
    }

    /** YAML - only a localfile/groupedfile backend configured for the YAML format. */
    public static <V> ConfigFactoryCodec<V> yaml(final Class<V> type) {
        return yaml(type, null);
    }

    /**
     * Compact JSON, ref-aware: a {@link Ref} field resolves against {@code registry} once decoded.
     * A {@code null} registry is the plain bridge (identical to {@link #json(Class)}).
     */
    public static <V> ConfigFactoryCodec<V> json(final Class<V> type, final RefRegistry registry) {
        return new ConfigFactoryCodec<>(type, mapper(new JsonMapper(), registry), "application/json", registry);
    }

    /** Indented JSON, ref-aware. See {@link #json(Class, RefRegistry)}. */
    public static <V> ConfigFactoryCodec<V> jsonPretty(final Class<V> type, final RefRegistry registry) {
        return new ConfigFactoryCodec<>(type,
                mapper(new JsonMapper(), registry).enable(SerializationFeature.INDENT_OUTPUT),
                "application/json", registry);
    }

    /** YAML, ref-aware. See {@link #json(Class, RefRegistry)}. */
    public static <V> ConfigFactoryCodec<V> yaml(final Class<V> type, final RefRegistry registry) {
        return new ConfigFactoryCodec<>(type, mapper(new YAMLMapper(), registry), "application/yaml", registry);
    }

    /**
     * Layer the storage read contract (ISO dates, unknown-key tolerance, insertion-order-preserving maps),
     * then EveryConfig's binding semantics, then the {@link ConfigFactory} platform types onto {@code base};
     * finally, when {@code registry} is non-null, a {@link RefModule} bound to it so {@code Ref} fields are
     * read back resolvable. Mutate-and-return (Jackson is configured once at construction, then read-only on
     * the hot path).
     */
    private static <M extends ObjectMapper> M mapper(final M base, final RefRegistry registry) {
        JacksonConfig.storageSafe(base);                        // EveryDatabase: ISO dates, map insertion order kept
        base.registerModule(new EveryConfigModule());           // EveryConfig: enum-by-name + @Key introspector
        base.registerModule(ConfigFactory.sharedTypeModule());  // the platform types the factory owns
        if (registry != null) {
            base.registerModule(new RefModule(registry));       // Ref <-> key, bound to the plugin's registry
        }
        return base;
    }

    /**
     * The codec a hosted read or write binds through: the factory's shared file-less codec, or - when this
     * variant is ref-aware - a private copy of it carrying the same {@link RefModule} the byte-edge got, so
     * the bind that fires the hooks also binds a {@link Ref} to {@code registry}.
     */
    private static InMemoryCodec hostCodec(final RefRegistry registry) {
        final InMemoryCodec shared = ConfigFactory.inMemoryCodec();
        if (registry == null) {
            return shared;
        }
        final ObjectMapper refAware = shared.getObjectMapper().copy();
        refAware.registerModule(new RefModule(registry));
        return new InMemoryCodec(refAware, shared.compactElementResolver());
    }

    @Override
    public byte[] encode(final V value) throws CodecException {
        try {
            if (!lifecycle) {
                return byteEdge.writeValueAsBytes(value);          // fast path: type-aware, no host
            }
            final Config host = Config.inMemory(hostCodec);
            host.bind(type).write("", value);                      // fires PRE/POST_SAVE + nested walk
            return byteEdge.writeValueAsBytes(host.getRoot());     // serialize the materialized tree
        } catch (final Exception e) {
            throw new CodecException("Failed to encode " + type.getSimpleName(), e);
        }
    }

    /**
     * Reads {@code data} back into an entity. A hook-free type is read straight off the byte-edge; a type
     * that carries hooks is read through the host, whose bind fires them. Either way, bytes the read REFUSES
     * are retried through the host, because that bind is lenient: one unreadable field then costs that field
     * (which keeps the default a fresh entity carries) instead of costing the whole entity, and says so - see
     * {@link #reportRefusedValues(List)}. Bytes not even the lenient read can take still fail.
     */
    @Override
    public V decode(final byte[] data) throws CodecException {
        if (!lifecycle) {
            try {
                return byteEdge.readValue(data, type);             // nothing to fire: read it off the wire
            } catch (final Exception refused) {
                return hostedRead(data, refused);
            }
        }
        return hostedRead(data, null);
    }

    /**
     * Reads through a hosted config: its bind fires {@code PRE_LOAD}/{@code POST_LOAD} (plus the nested walk)
     * and is lenient about a value it cannot take. {@code refused}, when present, is what a direct read of
     * the same bytes complained about, carried on the failure so a payload not even this read can take
     * reports both readings rather than only the second.
     */
    private V hostedRead(final byte[] data, final Exception refused) throws CodecException {
        try {
            final JsonNode node = byteEdge.readTree(data);
            final Config host = Config.inMemory(hostCodec);
            if (node instanceof ObjectNode) {
                host.getRoot().setAll((ObjectNode) node);
            }
            final BindResult<V> bound = host.bind(type).readResult("");
            reportRefusedValues(bound.issues());
            return bound.value();
        } catch (final Exception e) {
            final CodecException failure = new CodecException("Failed to decode " + type.getSimpleName(), e);
            if (refused != null) {
                failure.addSuppressed(refused);
            }
            throw failure;
        }
    }

    /**
     * Says out loud what the lenient bind kept to itself. A value the bind refuses is replaced by the
     * default a fresh entity carries and the load goes on, which would otherwise be indistinguishable from
     * bytes that never held the value at all, right up until the next save writes the defaults over what is
     * stored.
     */
    private void reportRefusedValues(final List<LoadIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        EverNifeCore.getLog().warning("Reading a " + type.getSimpleName() + " out of storage refused "
                + issues.size() + " of its values, which now hold what a brand new one holds: " + issues
                + ". Storage still has the real ones; the next save of this entity is what replaces them "
                + "with these, so fix what each issue names before it saves again.");
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
