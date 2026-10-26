package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.region.Persistence;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/** How {@link GroupManager#create(String, GroupKind, GroupOptions)} makes a group. Immutable: every {@code with} returns a copy. */
public final class GroupOptions {

    private static final GroupOptions DEFAULTS = new GroupOptions(null, null, null, null);

    private final @Nullable UUID leader;
    private final @Nullable Persistence persistence;
    private final @Nullable String displayName;
    private final @Nullable JsonNode metadata;

    private GroupOptions(@Nullable UUID leader, @Nullable Persistence persistence, @Nullable String displayName, @Nullable JsonNode metadata) {
        this.leader = leader;
        this.persistence = persistence;
        this.displayName = displayName;
        this.metadata = metadata;
    }

    /** No leader, the manager's persistence (PERSISTENT with a storage, TRANSIENT without), no display name, no metadata. */
    public static GroupOptions defaults() {
        return DEFAULTS;
    }

    /** The leader, added as the group's first member. */
    public GroupOptions withLeader(UUID uuid) {
        return new GroupOptions(Objects.requireNonNull(uuid, "uuid"), persistence, displayName, metadata);
    }

    public GroupOptions withPersistence(Persistence persistence) {
        return new GroupOptions(leader, Objects.requireNonNull(persistence, "persistence"), displayName, metadata);
    }

    public GroupOptions withDisplayName(String displayName) {
        return new GroupOptions(leader, persistence, displayName, metadata);
    }

    public GroupOptions withMetadata(JsonNode metadata) {
        return new GroupOptions(leader, persistence, displayName, metadata);
    }

    public @Nullable UUID getLeader() {
        return leader;
    }

    /** Null = the manager's default. */
    public @Nullable Persistence getPersistence() {
        return persistence;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public @Nullable JsonNode getMetadata() {
        return metadata;
    }
}
