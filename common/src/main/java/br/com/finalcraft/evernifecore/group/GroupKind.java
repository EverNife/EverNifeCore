package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a group is: how many groups of it a player may be in, the ladder of roles its members can hold and the flag
 * values its groups start from. The owner builds it and registers it on its {@link GroupManager} before
 * {@link GroupManager#load()}. Equal by key. Immutable.
 *
 * <pre>{@code
 * GroupKind party = GroupKind.of(plugin, "party").cardinality(GroupKind.Cardinality.ONE)
 *         .roles(GroupRole.of("member", 0), GroupRole.of("officer", 10)).build();
 * }</pre>
 */
public final class GroupKind {

    public enum Cardinality {
        /** A player is in at most one group of the kind. */
        ONE,
        /** A player may be in any number of groups of the kind. The default. */
        MANY
    }

    /** The kind of every group local to a region: {@code evernifecore:region-local}, MANY, no roles, no defaults. */
    public static final GroupKind REGION_LOCAL = new GroupKind(FlagKey.of("evernifecore", "region-local"), Cardinality.MANY,
            Collections.<GroupRole>emptyList(), Collections.<FlagKey, Object>emptyMap());

    private final FlagKey key;
    private final Cardinality cardinality;
    private final List<GroupRole> roles;
    private final Map<FlagKey, Object> flagDefaults;

    private GroupKind(FlagKey key, Cardinality cardinality, List<GroupRole> roles, Map<FlagKey, Object> flagDefaults) {
        this.key = key;
        this.cardinality = cardinality;
        this.roles = roles;
        this.flagDefaults = flagDefaults;
    }

    /** @throws IllegalArgumentException when the name breaks the region name rule */
    public static Builder of(ECPluginData owner, String name) {
        return new Builder(FlagKey.of(owner, name));
    }

    /** {@code owner:name}: the same rule as a flag key. */
    public FlagKey getKey() {
        return key;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    /** The ladder, lowest rank first. Read-only. */
    public List<GroupRole> getRoles() {
        return roles;
    }

    /** The role of the ladder named {@code name}, case-insensitively; null when the kind has none. */
    public @Nullable GroupRole getRole(String name) {
        for (GroupRole role : roles) {
            if (role.getName().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return null;
    }

    /** The values a group of this kind starts from, by flag key. Read-only. */
    public Map<FlagKey, Object> getFlagDefaults() {
        return flagDefaults;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupKind && ((GroupKind) other).key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }

    public static final class Builder {

        private final FlagKey key;
        private Cardinality cardinality = Cardinality.MANY;
        private List<GroupRole> roles = Collections.emptyList();
        private final Map<FlagKey, Object> flagDefaults = new LinkedHashMap<>();

        private Builder(FlagKey key) {
            this.key = key;
        }

        /** Default {@link Cardinality#MANY}. */
        public Builder cardinality(Cardinality cardinality) {
            this.cardinality = Objects.requireNonNull(cardinality, "cardinality");
            return this;
        }

        /** @throws IllegalArgumentException naming the repeated role name or rank */
        public Builder roles(GroupRole... ladder) {
            Set<String> names = new HashSet<>();
            Set<Integer> ranks = new HashSet<>();
            for (GroupRole role : ladder) {
                if (!names.add(role.getName()) || !ranks.add(role.getRank())) {
                    throw new IllegalArgumentException("The roles of group kind " + key + " repeat " + role + ": every role of a ladder"
                            + " needs its own name and its own rank, e.g. visitor(0), builder(10), manager(20).");
                }
            }
            List<GroupRole> sorted = new ArrayList<>();
            Collections.addAll(sorted, ladder);
            sorted.sort(Comparator.comparingInt(GroupRole::getRank));
            this.roles = Collections.unmodifiableList(sorted);
            return this;
        }

        public <T> Builder flagDefault(Flag<T> flag, T value) {
            flagDefaults.put(flag.getKey(), Objects.requireNonNull(value, "value"));
            return this;
        }

        public GroupKind build() {
            return new GroupKind(key, cardinality, roles, Collections.unmodifiableMap(new LinkedHashMap<>(flagDefaults)));
        }
    }
}
