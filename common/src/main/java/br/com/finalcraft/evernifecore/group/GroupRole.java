package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.region.RegionId;

/**
 * A free tag with a rank on the ladder of a {@link GroupKind}; a member's rank is the highest of its roles. The leader is
 * not a role. Equal by name and rank. Immutable.
 */
public final class GroupRole {

    private final String name;
    private final int rank;

    private GroupRole(String name, int rank) {
        this.name = name;
        this.rank = rank;
    }

    /** @throws IllegalArgumentException when the name breaks the region name rule */
    public static GroupRole of(String name, int rank) {
        return new GroupRole(RegionId.requireName("role", name), rank);
    }

    public String getName() {
        return name;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupRole && ((GroupRole) other).name.equals(name) && ((GroupRole) other).rank == rank;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + rank;
    }

    @Override
    public String toString() {
        return name + "(" + rank + ")";
    }
}
