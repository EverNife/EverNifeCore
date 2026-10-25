package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Everything a {@link TypeRegistry} knows about one type: its {@code owner:name} key, the bare aliases it also answers
 * to, one sentence of description, its parameters, the platforms it runs on, the player capabilities it needs, the
 * event sources it listens to, and the factory that builds it. The registry validates a definition against the
 * parameters before the factory runs; the exported reference and the platform warning come from the same description.
 */
public final class TypeDescriptor<T> {

    private final String key;
    private final String owner;
    private final String name;
    private final List<String> aliases;
    private final String description;
    private final Map<String, ParamSpec> params;
    private final Set<String> platforms;
    private final Set<IPlayerStateAccess.Capability> capabilities;
    private final Set<EventSource> needs;
    private final Function<ParsedParams, T> factory;

    private TypeDescriptor(Builder<T> builder) {
        this.key = builder.owner + ":" + builder.name;
        this.owner = builder.owner;
        this.name = builder.name;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(builder.aliases));
        this.description = builder.description;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(builder.params));
        this.platforms = Collections.unmodifiableSet(new LinkedHashSet<>(builder.platforms));
        this.capabilities = builder.capabilities.isEmpty() ? Collections.<IPlayerStateAccess.Capability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.capabilities));
        this.needs = Collections.unmodifiableSet(new LinkedHashSet<>(builder.needs));
        this.factory = builder.factory;
    }

    /**
     * Starts the descriptor of the type keyed {@code key}, {@code owner:name} with the rule of a region id
     * ({@code [a-z0-9_-]{1,64}} per side, case-insensitive); the owner is the plugin that registers it.
     *
     * @throws IllegalArgumentException when the key is not one owner and one name around a single {@code :}
     */
    public static <T> Builder<T> of(String key) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon < 0 || key.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid type key '" + key + "': write it as owner:name with exactly one ':', the owner"
                    + " being the plugin that registers it, e.g. 'finaljobs:has-job'.");
        }
        return new Builder<>(RegionId.requireOwner(key.substring(0, colon)), RegionId.requireName("type", key.substring(colon + 1)));
    }

    /** The lowercase {@code owner:name}. */
    public String getKey() {
        return key;
    }

    /** The owner part of the key: the lowercase name of the plugin that registers the type. */
    public String getOwner() {
        return owner;
    }

    /** The name part of the key, the bare name a definition may write. */
    public String getName() {
        return name;
    }

    /** Other bare names the type answers to, resolved like the name part. */
    public List<String> getAliases() {
        return aliases;
    }

    /** The bare name and every alias. */
    public List<String> getBareNames() {
        List<String> names = new ArrayList<>(aliases.size() + 1);
        names.add(name);
        names.addAll(aliases);
        return names;
    }

    public String getDescription() {
        return description;
    }

    /** In declaration order. */
    public List<ParamSpec> getParams() {
        return Collections.unmodifiableList(new ArrayList<>(params.values()));
    }

    public @Nullable ParamSpec getParam(String name) {
        return name == null ? null : params.get(name.toLowerCase(Locale.ROOT));
    }

    /** {@code a (number, required), b (text, optional)}, or {@code none}. */
    public String describeParams() {
        if (params.isEmpty()) {
            return "none";
        }
        List<String> described = new ArrayList<>();
        for (ParamSpec param : params.values()) {
            described.add(param.describe());
        }
        return String.join(", ", described);
    }

    /** The platform ids ({@code IPlatform.getPlatformProviderId()}) the type runs on; empty means every platform. */
    public Set<String> getPlatforms() {
        return platforms;
    }

    /** What the type reads or changes on a player; a platform without one of these cannot run it. */
    public Set<IPlayerStateAccess.Capability> getNeededCapabilities() {
        return capabilities;
    }

    /** The event sources the type listens to - the default answer of what it needs to be reevaluated. */
    public Set<EventSource> getNeeds() {
        return needs;
    }

    /**
     * Builds the type from validated parameters.
     *
     * @throws TypeRegistryException when the factory refuses them
     */
    public T create(ParsedParams params) throws TypeRegistryException {
        T created = factory.apply(params);
        if (created == null) {
            throw new IllegalStateException("The factory of type " + key + " returned null: a factory builds a value or throws a"
                    + " TypeRegistryException that says why it cannot.");
        }
        return created;
    }

    @Override
    public String toString() {
        return key;
    }

    public static final class Builder<T> {

        private final String owner;
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String description;
        private final Map<String, ParamSpec> params = new LinkedHashMap<>();
        private final Set<String> platforms = new LinkedHashSet<>();
        private final Set<IPlayerStateAccess.Capability> capabilities = EnumSet.noneOf(IPlayerStateAccess.Capability.class);
        private final Set<EventSource> needs = new LinkedHashSet<>();
        private Function<ParsedParams, T> factory;

        private Builder(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        public Builder<T> alias(String... aliases) {
            for (String alias : aliases) {
                String checked = RegionId.requireName("type alias", alias);
                if (!checked.equals(name) && !this.aliases.contains(checked)) {
                    this.aliases.add(checked);
                }
            }
            return this;
        }

        public Builder<T> describe(String description) {
            this.description = description;
            return this;
        }

        /** @throws IllegalArgumentException when another parameter already has the name */
        public Builder<T> param(ParamSpec param) {
            if (params.containsKey(param.getName())) {
                throw new IllegalArgumentException("Type " + owner + ":" + name + " declares parameter '" + param.getName() + "' twice.");
            }
            params.put(param.getName(), param);
            return this;
        }

        public Builder<T> platforms(String... platformIds) {
            platforms.addAll(Arrays.asList(platformIds));
            return this;
        }

        public Builder<T> needsCapability(IPlayerStateAccess.Capability... capabilities) {
            this.capabilities.addAll(Arrays.asList(capabilities));
            return this;
        }

        public Builder<T> needs(EventSource... sources) {
            needs.addAll(Arrays.asList(sources));
            return this;
        }

        public Builder<T> factory(Function<ParsedParams, T> factory) {
            this.factory = factory;
            return this;
        }

        /** @throws IllegalStateException when the description or the factory is missing, or a node parameter has no nested registry */
        public TypeDescriptor<T> build() {
            String key = owner + ":" + name;
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalStateException("Type " + key + " has no description: call describe(\"...\") with one sentence for the"
                        + " admin - the error messages and the exported reference show it.");
            }
            if (factory == null) {
                throw new IllegalStateException("Type " + key + " has no factory: call factory(params -> ...) with what builds it.");
            }
            for (ParamSpec param : params.values()) {
                if ((param.getKind() == ParamSpec.Kind.NODE || param.getKind() == ParamSpec.Kind.NODE_LIST) && param.getNested() == null) {
                    throw new IllegalStateException("Parameter '" + param.getName() + "' of type " + key + " is " + param.getKind()
                            + " but names no registry for its children: declare it with .nested(theRegistry).");
                }
            }
            return new TypeDescriptor<>(this);
        }
    }
}
