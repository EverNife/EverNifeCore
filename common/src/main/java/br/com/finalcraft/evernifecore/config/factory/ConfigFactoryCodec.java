package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.binding.BindResult;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everyconfig.binding.LoadIssue;
import br.com.finalcraft.everyconfig.binding.introspect.EveryConfigModule;
import br.com.finalcraft.everyconfig.binding.merge.LifecycleGraphWalker;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.CodecException;
import br.com.finalcraft.everydatabase.codec.JacksonConfig;
import br.com.finalcraft.everydatabase.codec.ObjectMapperAware;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.jackson.RefModule;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *       byteEdge} parses the bytes into a tree, the host adopts it, and {@code
 *       host.bind(type).readResult("")} fires the load hooks that reconstruct manually-managed state and
 *       answers what the bind refused along with the entity - see {@link #reportRefusedValues(List)}.</li>
 * </ul>
 *
 * <h2>Type-registry staleness</h2>
 * The byte-edge captures the {@link ConfigFactory} registrations present at construction, exactly like the
 * factory's own codecs: a type registered AFTER this codec is built is not seen by it. This is acceptable
 * because a codec is built when its section binds, after the bulk of registrations have run.
 *
 * <h2>Ref-aware variants</h2>
 * The factories that take a {@link RefRegistry} layer a {@link RefModule} bound to that registry onto the
 * byte-edge (after the modules above), so a {@link Ref} field is serialized as its key and read back
 * <b>bound</b> to the registry - a ref inside a persisted entity then resolves an entity that lives in a
 * manager registered in the same registry. A {@code null} registry is the plain bridge (no ref support).
 * This is the fast path only: the lifecycle path binds through EveryConfig, which does not know {@code Ref},
 * so a type that is a {@code ConfigLifecycle} <b>and</b> carries a {@code Ref} field is rejected at
 * construction rather than silently producing an unbound ref.
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

    private ConfigFactoryCodec(final Class<V> type, final ObjectMapper byteEdge, final String contentType,
                               final RefRegistry registry) {
        this.type = type;
        this.byteEdge = byteEdge;
        this.contentType = contentType;
        boolean lifecyclePath = LifecycleGraphWalker.mayContainHooks(type);
        if (registry != null && graphContainsRefField(type)) {
            // A Ref only decodes bound to the registry through the byte-edge's RefModule, which fires ONLY
            // on the fast path - the lifecycle path binds through EveryConfig, which does not know Ref. So a
            // ref-bearing type must take the fast path. The walker over-reports such a type as "may contain
            // hooks" (a Ref's erased key field is Object), so re-decide here: force the fast path, unless the
            // type actually reaches a ConfigLifecycle hook - then the two needs collide and we fail loud.
            if (reachesLifecycleHook(type)) {
                throw new CodecException("A Ref inside a ConfigLifecycle type is not supported yet: "
                        + type.getName() + " reaches a ConfigLifecycle hook and declares a Ref field. Remove"
                        + " the Ref, or drop ConfigLifecycle from this type, so the ref-aware codec can bind it.");
            }
            lifecyclePath = false;
        }
        this.lifecycle = lifecyclePath;
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
            final BindResult<V> bound = host.bind(type).readResult("");  // fires PRE/POST_LOAD + nested walk
            reportRefusedValues(bound.issues());
            return bound.value();
        } catch (final Exception e) {
            throw new CodecException("Failed to decode " + type.getSimpleName(), e);
        }
    }

    /**
     * Says out loud what the lenient bind kept to itself. A value the bind refuses is replaced by the
     * default a fresh entity carries and the load goes on - which on the fast path would have been a
     * {@link CodecException}, and which here would otherwise be indistinguishable from bytes that never
     * held the value at all, right up until the next save writes the defaults over what is stored.
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

    // ---- graph inspection for the ref-aware path decision ----
    //
    // Both scans walk only the fields Jackson serializes (instance, non-transient, non-@JsonIgnore) up the
    // hierarchy, resolving a collection/map/array to its element type, with an identity-visited set for cycles.

    /** Whether {@code type}'s serialized-field graph declares a {@link Ref} anywhere (direct, or as a generic
     *  collection/map element, or nested in a user POJO field). */
    private static boolean graphContainsRefField(final Class<?> type) {
        return graphContainsRefField(type, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean graphContainsRefField(final Class<?> type, final Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return false;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (final Field field : c.getDeclaredFields()) {
                if (isSkippedField(field)) {
                    continue;
                }
                if (typeMentionsRef(field.getGenericType())) {
                    return true;
                }
                if (isUserType(field.getType()) && graphContainsRefField(field.getType(), visited)) {
                    return true;
                }
                //a Ref one level down inside a collection or map: the field's own class is java.util.List,
                //so only its element type can say the graph carries one
                final Class<?> element = elementType(field);
                if (element != null && element != field.getType()
                        && isUserType(element) && graphContainsRefField(element, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether {@code type}'s serialized-field graph reaches a genuine {@code ConfigLifecycle} hook, treating
     * a {@link Ref} as an opaque scalar (the byte-edge's {@link RefModule} serializes it as a key, never
     * descending into it). This is the honest hook signal the ref-aware path decision needs, unlike the
     * walker's gate, which a {@code Ref} poisons into a false "may contain hooks". A polymorphic dead-end
     * (interface or {@code Object}) is treated conservatively as reaching a hook.
     */
    private static boolean reachesLifecycleHook(final Class<?> type) {
        return !provablyHookFree(type, new HashSet<>());
    }

    private static boolean provablyHookFree(final Class<?> type, final Set<Class<?>> onPath) {
        if (type == null || isRefType(type) || !isUserType(type)) {
            return true;   // Ref is opaque; a JDK/enum/primitive leaf cannot implement the app hook
        }
        if (ConfigLifecycle.class.isAssignableFrom(type)) {
            return false;
        }
        if (!onPath.add(type)) {
            return true;   // already being proven on this path - contributes no new hook by itself
        }
        try {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (final Field field : c.getDeclaredFields()) {
                    if (isSkippedField(field)) {
                        continue;
                    }
                    if (!elementHookFree(elementType(field), onPath)) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            onPath.remove(type);
        }
    }

    private static boolean elementHookFree(final Class<?> c, final Set<Class<?>> onPath) {
        if (c == null) {
            return false;   // a raw or wildcard element could hold anything at runtime
        }
        if (isRefType(c) || !isUserType(c)) {
            return true;
        }
        if (c.isInterface() || c == Object.class) {
            return false;   // a polymorphic dead-end could hold a hook the static type does not reveal
        }
        return provablyHookFree(c, onPath);
    }

    /** The type a field contributes to the graph: a collection/array element, a map value, or the field itself. */
    private static Class<?> elementType(final Field field) {
        final Class<?> raw = field.getType();
        if (raw.isArray()) {
            return raw.getComponentType();
        }
        if (Collection.class.isAssignableFrom(raw)) {
            return typeArgument(field.getGenericType(), 0);
        }
        if (Map.class.isAssignableFrom(raw)) {
            return typeArgument(field.getGenericType(), 1);
        }
        return raw;
    }

    /** The raw class of the {@code index}-th type argument of {@code t}, or {@code null} if unresolved. */
    private static Class<?> typeArgument(final Type t, final int index) {
        if (t instanceof ParameterizedType) {
            final Type[] args = ((ParameterizedType) t).getActualTypeArguments();
            if (index < args.length) {
                return rawClassOf(args[index]);
            }
        }
        return null;
    }

    /**
     * The class behind a type, unwrapping its own type arguments - {@code List<Ref<K, V>>} contributes
     * {@code Ref}, not {@code null}. Answers {@code null} for a wildcard or a type variable, which name no
     * class at all and so stay unresolved.
     */
    private static Class<?> rawClassOf(final Type t) {
        if (t instanceof Class) {
            return (Class<?>) t;
        }
        if (t instanceof ParameterizedType) {
            return rawClassOf(((ParameterizedType) t).getRawType());
        }
        return null;
    }

    /** A field Jackson never serializes - so it contributes no persisted Ref and no fired hook. */
    private static boolean isSkippedField(final Field field) {
        final int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isSynthetic()) {
            return true;
        }
        final JsonIgnore ignore = field.getAnnotation(JsonIgnore.class);
        return ignore != null && ignore.value();
    }

    /** Whether a reflected type is, or has a type argument that is, a {@link Ref}. */
    private static boolean typeMentionsRef(final Type t) {
        if (t instanceof Class) {
            return isRefType((Class<?>) t);
        }
        if (t instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) t;
            if (typeMentionsRef(pt.getRawType())) {
                return true;
            }
            for (final Type arg : pt.getActualTypeArguments()) {
                if (typeMentionsRef(arg)) {
                    return true;
                }
            }
            return false;
        }
        if (t instanceof GenericArrayType) {
            return typeMentionsRef(((GenericArrayType) t).getGenericComponentType());
        }
        if (t instanceof WildcardType) {
            for (final Type bound : ((WildcardType) t).getUpperBounds()) {
                if (typeMentionsRef(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRefType(final Class<?> c) {
        return Ref.class.isAssignableFrom(c);
    }

    /** A caller-defined type worth recursing into - not a JDK, Jackson, or framework class. */
    private static boolean isUserType(final Class<?> c) {
        if (c.isPrimitive() || c.isEnum() || c.isArray()) {
            return false;
        }
        final String name = c.getName();
        return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("com.fasterxml.")
                || name.startsWith("br.com.finalcraft.everydatabase.")
                || name.startsWith("br.com.finalcraft.everyconfig."));
    }
}
