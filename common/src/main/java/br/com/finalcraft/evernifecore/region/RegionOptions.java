package br.com.finalcraft.evernifecore.region;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** How {@link RegionManager#create} sets a new region up. Immutable: every {@code with} returns a copy. */
public final class RegionOptions {

    private static final RegionOptions DEFAULTS = new RegionOptions(0, null, Persistence.PERSISTENT, null,
            Collections.emptySet(), JsonNodeFactory.instance.objectNode(), RegionPolicy.allowAll());

    private final int priority;
    private final @Nullable Region parent;
    private final Persistence persistence;
    private final @Nullable Instant expiresAt;
    private final Set<String> tags;
    private final JsonNode metadata;
    private final RegionPolicy policy;

    private RegionOptions(int priority, @Nullable Region parent, Persistence persistence, @Nullable Instant expiresAt,
                          Set<String> tags, JsonNode metadata, RegionPolicy policy) {
        this.priority = priority;
        this.parent = parent;
        this.persistence = persistence;
        this.expiresAt = expiresAt;
        this.tags = tags;
        this.metadata = metadata;
        this.policy = policy;
    }

    /** Priority 0, no parent, {@link Persistence#PERSISTENT}, no deadline, no tags, empty metadata, {@link RegionPolicy#allowAll()}. */
    public static RegionOptions defaults() {
        return DEFAULTS;
    }

    public RegionOptions withPriority(int priority) {
        return new RegionOptions(priority, parent, persistence, expiresAt, tags, metadata, policy);
    }

    /** A region of the same owner and world; null for none. */
    public RegionOptions withParent(@Nullable Region parent) {
        return new RegionOptions(priority, parent, persistence, expiresAt, tags, metadata, policy);
    }

    public RegionOptions withPersistence(Persistence persistence) {
        return new RegionOptions(priority, parent, Objects.requireNonNull(persistence, "persistence"), expiresAt, tags, metadata, policy);
    }

    /** When the region is removed on its own; null for never. */
    public RegionOptions withExpiresAt(@Nullable Instant expiresAt) {
        return new RegionOptions(priority, parent, persistence, expiresAt, tags, metadata, policy);
    }

    public RegionOptions withTags(String... tags) {
        return new RegionOptions(priority, parent, persistence, expiresAt,
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(tags))), metadata, policy);
    }

    public RegionOptions withMetadata(JsonNode metadata) {
        return new RegionOptions(priority, parent, persistence, expiresAt, tags, Objects.requireNonNull(metadata, "metadata").deepCopy(), policy);
    }

    public RegionOptions withPolicy(RegionPolicy policy) {
        return new RegionOptions(priority, parent, persistence, expiresAt, tags, metadata, Objects.requireNonNull(policy, "policy"));
    }

    public int getPriority() {
        return priority;
    }

    public @Nullable Region getParent() {
        return parent;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<String> getTags() {
        return tags;
    }

    /** A copy: the options stay immutable. */
    public JsonNode getMetadata() {
        return metadata.deepCopy();
    }

    public RegionPolicy getPolicy() {
        return policy;
    }
}
