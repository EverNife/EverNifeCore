package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.storage.Persistence;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * How {@link GroupManager#create(String, GroupKind, GroupOptions)} makes a group. The edges, {@code kickWhen} and the
 * quit policy are live behaviour, never stored: the owner passes them again on every enable. Immutable: every
 * {@code with} returns a copy.
 */
public final class GroupOptions {

    private static final GroupOptions DEFAULTS = new GroupOptions();

    private @Nullable UUID leader;
    private @Nullable Persistence persistence;
    private @Nullable String displayName;
    private @Nullable JsonNode metadata;
    private @Nullable Edge joinEdge;
    private @Nullable Edge leaveEdge;
    private @Nullable Condition kickWhen;
    private @Nullable FancyText kickReason;
    private @Nullable QuitPolicy quitPolicy;

    private GroupOptions() {
    }

    private GroupOptions copy() {
        GroupOptions copy = new GroupOptions();
        copy.leader = leader;
        copy.persistence = persistence;
        copy.displayName = displayName;
        copy.metadata = metadata;
        copy.joinEdge = joinEdge;
        copy.leaveEdge = leaveEdge;
        copy.kickWhen = kickWhen;
        copy.kickReason = kickReason;
        copy.quitPolicy = quitPolicy;
        return copy;
    }

    /** No leader, the manager's persistence (PERSISTENT with a storage, TRANSIENT without), nothing live. */
    public static GroupOptions defaults() {
        return DEFAULTS;
    }

    /** The leader, added as the group's first member. */
    public GroupOptions withLeader(UUID uuid) {
        GroupOptions copy = copy();
        copy.leader = Objects.requireNonNull(uuid, "uuid");
        return copy;
    }

    public GroupOptions withPersistence(Persistence persistence) {
        GroupOptions copy = copy();
        copy.persistence = Objects.requireNonNull(persistence, "persistence");
        return copy;
    }

    public GroupOptions withDisplayName(String displayName) {
        GroupOptions copy = copy();
        copy.displayName = displayName;
        return copy;
    }

    public GroupOptions withMetadata(JsonNode metadata) {
        GroupOptions copy = copy();
        copy.metadata = metadata;
        return copy;
    }

    /** @see Group#setJoinEdge */
    public GroupOptions withJoinEdge(Edge edge) {
        GroupOptions copy = copy();
        copy.joinEdge = edge;
        return copy;
    }

    /** @see Group#setLeaveEdge */
    public GroupOptions withLeaveEdge(Edge edge) {
        GroupOptions copy = copy();
        copy.leaveEdge = edge;
        return copy;
    }

    /** @see Group#setKickWhen */
    public GroupOptions withKickWhen(Condition condition, @Nullable FancyText reason) {
        GroupOptions copy = copy();
        copy.kickWhen = condition;
        copy.kickReason = reason;
        return copy;
    }

    /** @see Group#setQuitPolicy */
    public GroupOptions withQuitPolicy(QuitPolicy policy) {
        GroupOptions copy = copy();
        copy.quitPolicy = Objects.requireNonNull(policy, "policy");
        return copy;
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

    public @Nullable Edge getJoinEdge() {
        return joinEdge;
    }

    public @Nullable Edge getLeaveEdge() {
        return leaveEdge;
    }

    public @Nullable Condition getKickWhen() {
        return kickWhen;
    }

    public @Nullable FancyText getKickReason() {
        return kickReason;
    }

    /** Null = the group's default: LEAVE for TRANSIENT, KEEP otherwise. */
    public @Nullable QuitPolicy getQuitPolicy() {
        return quitPolicy;
    }
}
