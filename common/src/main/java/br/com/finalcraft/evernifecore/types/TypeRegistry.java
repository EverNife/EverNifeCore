package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The types of one kind ({@code condition}, {@code effect}, {@code variable}...) by {@code owner:name} key. A type
 * registers at any time and describes itself ({@link TypeDescriptor}); from that description the registry validates a
 * definition with a message that teaches, and refuses at load a type this platform cannot run.
 *
 * <p>A definition is a node, written {@code {type: <name>, <param>: <value>...}}, or {@code <name>: <value>} for a type
 * with a single required parameter. A bare name resolves first to the caller's own type, then to the only type that
 * has it (name or alias); {@code owner:name} always resolves to that exact key. A definition that cites a type nobody
 * registered can be kept {@link #parseLater pending}: it resolves when the type registers. A child definition
 * ({@link ParamSpec.Kind#NODE}) is always read that way, so one absent type never discards the definition around it.</p>
 */
public final class TypeRegistry<T> {

    /** The field of a node that names its type. */
    public static final String TYPE_FIELD = "type";

    private final Class<T> kind;
    private final String kindName;
    private final Map<String, Registration<T>> byKey = new LinkedHashMap<>();
    private final List<PendingDefinition<T>> pending = new ArrayList<>();
    private final Set<String> reservedKeys = new LinkedHashSet<>();

    private TypeRegistry(Class<T> kind, String kindName) {
        this.kind = kind;
        this.kindName = kindName;
    }

    /**
     * A new registry of {@code kind}, named {@code kindName} in every message ("No condition 'x' is registered") and
     * in the exported reference.
     */
    public static <T> TypeRegistry<T> of(Class<T> kind, String kindName) {
        TypeRegistry<T> registry = new TypeRegistry<>(kind, kindName);
        TypeRegistries.track(registry);
        return registry;
    }

    public Class<T> getKind() {
        return kind;
    }

    public String getKindName() {
        return kindName;
    }

    /**
     * Node keys this registry's caller reads itself (an effect's {@code delay}, {@code chance}...): no type may declare
     * them, and a node may carry them without being refused as an unknown parameter.
     */
    public synchronized TypeRegistry<T> reserveKeys(String... keys) {
        for (String key : keys) {
            reservedKeys.add(key.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    public synchronized Set<String> getReservedKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(reservedKeys));
    }

    /**
     * Registers {@code descriptor} under {@code owner}, the plugin its key names, then reads again every pending
     * definition that waited for it. One that reads resolves; one that still does not stays pending with the cause,
     * and {@code owner}'s log says so - it is kept raw, nothing is deleted.
     *
     * @throws IllegalArgumentException when the key names another plugin, or a parameter takes a reserved key
     * @throws IllegalStateException when the key is already registered, naming both owners
     */
    public void register(ECPluginData owner, TypeDescriptor<T> descriptor) {
        List<PendingDefinition<T>> waiting = new ArrayList<>();
        synchronized (this) {
            String expected = RegionId.requireOwner(owner.getMetaInfo().getName());
            if (!descriptor.getOwner().equals(expected)) {
                throw new IllegalArgumentException("The " + kindName + " " + descriptor.getKey() + " cannot be registered by "
                        + owner.getMetaInfo().getName() + ": a type is registered by the plugin its key names. Register it from that"
                        + " plugin, or key it TypeDescriptor.of(\"" + expected + ":" + descriptor.getName() + "\").");
            }
            Registration<T> existing = byKey.get(descriptor.getKey());
            if (existing != null) {
                throw new IllegalStateException("The " + kindName + " " + descriptor.getKey() + " is already registered by "
                        + existing.owner.getMetaInfo().getName() + ", so " + owner.getMetaInfo().getName() + " cannot register it"
                        + " too. Unregister it there first, or give the new " + kindName + " another name.");
            }
            for (ParamSpec param : descriptor.getParams()) {
                if (reservedKeys.contains(param.getName())) {
                    throw new IllegalArgumentException("The " + kindName + " " + descriptor.getKey() + " declares parameter '"
                            + param.getName() + "', a key every " + kindName + " node reserves (" + reservedKeys + "). Rename it.");
                }
            }
            byKey.put(descriptor.getKey(), new Registration<>(owner, descriptor));
            for (PendingDefinition<T> each : pending) {
                String missing = each.getMissingType();
                if (missing != null && answersTo(descriptor, missing)) {
                    waiting.add(each);
                }
            }
        }
        for (PendingDefinition<T> each : waiting) {
            try {
                PendingDefinition<T> again = read(each.getCaller(), each.getRaw(), each.getZone(), true);
                again.get().ifPresent(value -> {
                    synchronized (this) {
                        pending.remove(each);
                    }
                    each.resolve(value);
                });
            } catch (TypeRegistryException unreadable) {
                each.keepPending(unreadable.getMessage());
                owner.getLog().warning("A definition of " + each.getCaller().getMetaInfo().getName() + " waiting for the " + kindName
                        + " " + descriptor.getKey() + " does not read: " + unreadable.getMessage() + " It is kept raw, nothing was"
                        + " deleted - fix it in " + each.getCaller().getMetaInfo().getName() + "'s files and reload.");
            }
        }
    }

    /** Takes out every type {@code owner} registered and every pending definition {@code owner} wrote. */
    public synchronized void unregisterAll(ECPluginData owner) {
        byKey.values().removeIf(registration -> registration.owner == owner);
        pending.removeIf(each -> each.getCaller() == owner);
    }

    /** Takes out the type keyed {@code key} when {@code owner} registered it; false when it did not. */
    public synchronized boolean unregister(ECPluginData owner, String key) {
        Registration<T> registration = key == null ? null : byKey.get(key.trim().toLowerCase(Locale.ROOT));
        if (registration == null || registration.owner != owner) {
            return false;
        }
        byKey.remove(registration.descriptor.getKey());
        return true;
    }

    /** By {@code owner:name}, or by a bare name or alias that exactly one registered type has. */
    public synchronized Optional<TypeDescriptor<T>> find(String keyOrAlias) {
        if (keyOrAlias == null) {
            return Optional.empty();
        }
        String written = keyOrAlias.trim().toLowerCase(Locale.ROOT);
        if (written.indexOf(':') >= 0) {
            Registration<T> exact = byKey.get(written);
            return exact == null ? Optional.<TypeDescriptor<T>>empty() : Optional.of(exact.descriptor);
        }
        List<Registration<T>> candidates = byBareName(written);
        return candidates.size() == 1 ? Optional.of(candidates.get(0).descriptor) : Optional.<TypeDescriptor<T>>empty();
    }

    /** In registration order. */
    public synchronized Collection<TypeDescriptor<T>> getAll() {
        List<TypeDescriptor<T>> all = new ArrayList<>(byKey.size());
        for (Registration<T> registration : byKey.values()) {
            all.add(registration.descriptor);
        }
        return Collections.unmodifiableList(all);
    }

    /** The plugin that registered {@code descriptor}; empty when it is not registered here. */
    public synchronized Optional<ECPluginData> getOwner(TypeDescriptor<?> descriptor) {
        Registration<T> registration = byKey.get(descriptor.getKey());
        return registration == null || registration.descriptor != descriptor ? Optional.<ECPluginData>empty() : Optional.of(registration.owner);
    }

    /** The definitions still waiting for a type, in the order they were read. */
    public synchronized List<PendingDefinition<T>> getPending() {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }

    /**
     * Why this platform cannot run {@code descriptor} - the same sentence {@link #parse} throws - or empty when it can.
     * Asks the platform only what the descriptor restricts: its id for a platform list, the player state for a
     * capability.
     */
    public Optional<String> getUnsupported(TypeDescriptor<?> descriptor) {
        if (descriptor.getPlatforms().isEmpty() && descriptor.getNeededCapabilities().isEmpty()) {
            return Optional.empty();
        }
        IPlatform platform = EverNifeCore.getPlatform();
        String platformId = platform.getPlatformProviderId();
        if (!descriptor.getPlatforms().isEmpty() && !descriptor.getPlatforms().contains(platformId)) {
            return Optional.of("The " + kindName + " '" + descriptor.getKey() + "' runs only on " + descriptor.getPlatforms()
                    + ", not on this platform (" + platformId + "). Remove it from the definition or guard it with a"
                    + " platform-specific file.");
        }
        List<IPlayerStateAccess.Capability> lacking = new ArrayList<>();
        if (!descriptor.getNeededCapabilities().isEmpty()) {
            IPlayerStateAccess state = platform.getPlayerState();
            for (IPlayerStateAccess.Capability capability : descriptor.getNeededCapabilities()) {
                if (!state.supports(capability)) {
                    lacking.add(capability);
                }
            }
        }
        if (lacking.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("The " + kindName + " '" + descriptor.getKey() + "' needs " + join(lacking) + " on a player, which this"
                + " platform (" + platformId + ") cannot read or change. Remove it from the definition or guard it with a"
                + " platform-specific file.");
    }

    /** {@link #parse(ECPluginData, JsonNode, ZoneId)} with the server's zone ({@link Window#getServerZone()}). */
    public T parse(ECPluginData caller, JsonNode node) throws TypeRegistryException {
        return parse(caller, node, Window.getServerZone());
    }

    /**
     * Reads {@code node} as one definition of {@code caller}; {@code zone} is what a window parameter without its own
     * zone is read in.
     *
     * @throws TypeRegistryException when the type is absent, ambiguous or unsupported here, or a parameter is missing,
     * unknown or unreadable - the message names the type, the parameter, the value and the expected format
     */
    public T parse(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        PendingDefinition<T> read = read(caller, node, zone, false);
        return read.get().orElseThrow(() -> new IllegalStateException("a strict read always resolves or throws"));
    }

    /** {@link #parseLater(ECPluginData, JsonNode, ZoneId)} with the server's zone. */
    public PendingDefinition<T> parseLater(ECPluginData caller, JsonNode node) throws TypeRegistryException {
        return parseLater(caller, node, Window.getServerZone());
    }

    /**
     * {@link #parse}, except that a type nobody registered yet is not an error: the definition comes back pending and
     * resolves when that type registers. Already resolved when the type is here.
     *
     * @throws TypeRegistryException for every other failure {@link #parse} reports
     */
    public PendingDefinition<T> parseLater(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        return read(caller, node, zone, true);
    }

    private PendingDefinition<T> read(ECPluginData caller, JsonNode node, ZoneId zone, boolean keepAbsent) {
        if (node == null || !node.isObject()) {
            throw new TypeRegistryException("A " + kindName + " is written as {type: <name>, <parameter>: <value>...}, or as"
                    + " <name>: <value> for a type with one parameter (e.g. permission: shop.vip) - this one is "
                    + (node == null ? "empty" : node.toString()) + ".");
        }
        String written;
        boolean shortcut;
        JsonNode typeNode = node.get(TYPE_FIELD);
        if (typeNode != null) {
            if (!typeNode.isTextual() || typeNode.asText().trim().isEmpty()) {
                throw new TypeRegistryException("The " + TYPE_FIELD + " of a " + kindName + " names it, like type: compare or"
                        + " type: finaljobs:has-job - this one is " + typeNode + ".");
            }
            written = typeNode.asText().trim();
            shortcut = false;
        } else if (node.size() == 1) {
            written = node.fieldNames().next();
            shortcut = true;
        } else {
            throw new TypeRegistryException("This " + kindName + " names no type: " + node + ". Write {type: <name>, <parameter>: <value>...},"
                    + " or <name>: <value> for a type with one parameter (e.g. permission: shop.vip).");
        }

        TypeDescriptor<T> descriptor = lookup(caller, written);
        if (descriptor == null) {
            String absent = "No " + kindName + " '" + written + "' is registered. The registered ones are: " + registeredNames() + "."
                    + " A plugin adds its own by registering TypeDescriptor.of(\"<plugin>:" + bareName(written) + "\") here.";
            if (!keepAbsent) {
                throw new TypeRegistryException(absent);
            }
            PendingDefinition<T> waiting = new PendingDefinition<>(caller, node, zone, written.toLowerCase(Locale.ROOT),
                    absent + " The definition is kept pending and resolves when that plugin registers it.", null);
            // ponytail: a pending entry lives until its caller unregisters, so a reload that re-reads keeps the old one too
            synchronized (this) {
                pending.add(waiting);
            }
            return waiting;
        }

        Optional<String> unsupported = getUnsupported(descriptor);
        if (unsupported.isPresent()) {
            throw new TypeRegistryException(unsupported.get());
        }

        Map<String, Object> values = new LinkedHashMap<>();
        if (shortcut) {
            List<ParamSpec> required = new ArrayList<>();
            for (ParamSpec param : descriptor.getParams()) {
                if (param.isRequired()) {
                    required.add(param);
                }
            }
            if (required.size() != 1) {
                throw new TypeRegistryException("The " + kindName + " '" + written + "' cannot be written as " + written + ": <value>,"
                        + " since it takes " + required.size() + " required parameters. Write {type: " + written + ", ...}.\n"
                        + paramsLine(descriptor));
            }
            ParamSpec only = required.get(0);
            values.put(only.getName(), readParam(caller, written, descriptor, only, node.get(written), zone));
        } else {
            Iterator<String> fields = node.fieldNames();
            Set<String> reserved = getReservedKeys();
            while (fields.hasNext()) {
                String field = fields.next();
                String lower = field.toLowerCase(Locale.ROOT);
                if (lower.equals(TYPE_FIELD) || reserved.contains(lower)) {
                    continue;
                }
                if (descriptor.getParam(lower) == null) {
                    throw new TypeRegistryException("The " + kindName + " '" + written + "' has no parameter '" + field + "'.\n"
                            + paramsLine(descriptor));
                }
            }
            for (ParamSpec param : descriptor.getParams()) {
                JsonNode value = fieldIgnoringCase(node, param.getName());
                if (value == null || value.isNull()) {
                    if (param.isRequired()) {
                        throw new TypeRegistryException("The " + kindName + " '" + written + "' is missing " + param.getName()
                                + ": it expects " + param.getExpected() + ".\n" + paramsLine(descriptor));
                    }
                    values.put(param.getName(), param.getDefault());
                } else {
                    values.put(param.getName(), readParam(caller, written, descriptor, param, value, zone));
                }
            }
        }

        T created;
        try {
            created = descriptor.create(new ParsedParams(descriptor, values, node, caller));
        } catch (TypeRegistryException refused) {
            throw refused;
        } catch (RuntimeException refused) {
            throw new TypeRegistryException("The " + kindName + " '" + written + "' refused its parameters: " + refused.getMessage()
                    + "\n" + paramsLine(descriptor), refused);
        }
        return new PendingDefinition<>(caller, node, zone, null, null, created);
    }

    private Object readParam(ECPluginData caller, String written, TypeDescriptor<T> descriptor, ParamSpec param, JsonNode value, ZoneId zone) {
        if (param.getKind() == ParamSpec.Kind.NODE) {
            if (!value.isObject()) {
                throw unreadable(written, descriptor, param, value, param.getExpected());
            }
            return param.getNested().parseLater(caller, value, zone);
        }
        if (param.getKind() == ParamSpec.Kind.NODE_LIST) {
            if (!value.isArray()) {
                throw unreadable(written, descriptor, param, value, param.getExpected());
            }
            List<PendingDefinition<?>> children = new ArrayList<>();
            for (JsonNode child : value) {
                children.add(param.getNested().parseLater(caller, child, zone));
            }
            return Collections.unmodifiableList(children);
        }
        Object read;
        try {
            read = ParamSpec.readScalarKind(param.getKind(), value, zone);
        } catch (IllegalArgumentException wrongShape) {
            throw unreadable(written, descriptor, param, value, wrongShape.getMessage());
        }
        if (!param.getAccepted().isEmpty()) {
            for (String accepted : param.getAccepted()) {
                if (accepted.equalsIgnoreCase((String) read)) {
                    return accepted;
                }
            }
            throw unreadable(written, descriptor, param, value, param.getExpected());
        }
        return read;
    }

    private TypeRegistryException unreadable(String written, TypeDescriptor<T> descriptor, ParamSpec param, JsonNode value, String expected) {
        return new TypeRegistryException("The " + kindName + " '" + written + "' cannot read " + param.getName() + " = " + value
                + ": it expects " + expected + ".\n" + paramsLine(descriptor));
    }

    private static String paramsLine(TypeDescriptor<?> descriptor) {
        return "Parameters of " + descriptor.getKey() + ": " + descriptor.describeParams() + ".";
    }

    /** The type {@code written} names for {@code caller}; null when none has it. */
    private synchronized @Nullable TypeDescriptor<T> lookup(ECPluginData caller, String written) {
        String lower = written.toLowerCase(Locale.ROOT);
        if (lower.indexOf(':') >= 0) {
            Registration<T> exact = byKey.get(lower);
            return exact == null ? null : exact.descriptor;
        }
        List<Registration<T>> candidates = byBareName(lower);
        if (candidates.isEmpty()) {
            return null;
        }
        for (Registration<T> candidate : candidates) {
            if (candidate.owner == caller) {
                return candidate.descriptor;
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0).descriptor;
        }
        List<String> registeredBy = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Registration<T> candidate : candidates) {
            registeredBy.add("'" + candidate.descriptor.getKey() + "' by " + candidate.owner.getMetaInfo().getName());
            keys.add("type: " + candidate.descriptor.getKey());
        }
        throw new TypeRegistryException("The " + kindName + " " + String.join(" and ", registeredBy) + " both answer to '" + written
                + "': write " + String.join(" or ", keys) + " - the bare name '" + written + "' is ambiguous for "
                + caller.getMetaInfo().getName() + ".");
    }

    private List<Registration<T>> byBareName(String lower) {
        List<Registration<T>> found = new ArrayList<>();
        for (Registration<T> registration : byKey.values()) {
            if (registration.descriptor.getBareNames().contains(lower)) {
                found.add(registration);
            }
        }
        return found;
    }

    private static boolean answersTo(TypeDescriptor<?> descriptor, String missing) {
        return missing.indexOf(':') >= 0 ? descriptor.getKey().equals(missing) : descriptor.getBareNames().contains(missing);
    }

    private synchronized String registeredNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Registration<T> registration : byKey.values()) {
            names.add(registration.descriptor.getName());
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static String bareName(String written) {
        int colon = written.indexOf(':');
        return (colon < 0 ? written : written.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }

    private static @Nullable JsonNode fieldIgnoringCase(JsonNode node, String name) {
        JsonNode exact = node.get(name);
        if (exact != null) {
            return exact;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().equalsIgnoreCase(name)) {
                return field.getValue();
            }
        }
        return null;
    }

    private static String join(List<?> items) {
        List<String> names = new ArrayList<>();
        for (Object item : items) {
            names.add(String.valueOf(item));
        }
        return String.join(" and ", names);
    }

    @Override
    public String toString() {
        return "TypeRegistry(" + kindName + ")";
    }

    private static final class Registration<T> {
        final ECPluginData owner;
        final TypeDescriptor<T> descriptor;

        Registration(ECPluginData owner, TypeDescriptor<T> descriptor) {
            this.owner = owner;
            this.descriptor = descriptor;
        }
    }
}
