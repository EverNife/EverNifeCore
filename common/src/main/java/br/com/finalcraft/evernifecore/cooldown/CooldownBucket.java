package br.com.finalcraft.evernifecore.cooldown;

import java.util.Map;

/**
 * A set of cooldowns owned by one storage row, keyed by cooldown identifier.
 *
 * <p>The two maps ARE the storage filter: {@link #getPersistedCooldowns()} is what the row encodes,
 * {@link #getTransientCooldowns()} holds the entries a caller asked not to persist, so an entry whose
 * {@link CooldownEntry}{@code .isPersist()} is false can never reach a backend by accident. Both must be real
 * fields of the implementing row - the transient one declared {@code @JsonIgnore transient} so neither
 * the encoder nor the conflict machinery can pick it up - and {@link #fileCooldown} is what moves an
 * entry between them when its persist flag changes.</p>
 */
public interface CooldownBucket {

    /** The entries this row stores. Never null, mutable, keyed by cooldown identifier. */
    Map<String, CooldownEntry> getPersistedCooldowns();

    /** The memory-only entries. Never null, mutable, keyed by cooldown identifier. */
    Map<String, CooldownEntry> getTransientCooldowns();

    /** The entry filed under {@code identifier} in either map, or null when this bucket has none. */
    default CooldownEntry findCooldown(String identifier) {
        CooldownEntry stored = getPersistedCooldowns().get(identifier);
        return stored != null ? stored : getTransientCooldowns().get(identifier);
    }

    /**
     * The entry filed under {@code identifier}, or a fresh blank one. The fresh entry is deliberately
     * NOT filed: a bucket only grows once something actually mutates a cooldown (see
     * {@link #fileCooldown}), so merely asking whether an id is in cooldown never adds to the row.
     */
    default CooldownEntry resolveCooldown(String identifier) {
        CooldownEntry existing = findCooldown(identifier);
        return existing != null ? existing : new CooldownEntry();
    }

    /**
     * Files {@code entry} under {@code identifier} in the map its persist flag calls for, taking it out
     * of the other one - the single place an entry crosses the storage filter.
     */
    default void fileCooldown(String identifier, CooldownEntry entry) {
        if (entry.isPersist()) {
            getTransientCooldowns().remove(identifier);
            getPersistedCooldowns().put(identifier, entry);
        } else {
            getPersistedCooldowns().remove(identifier);
            getTransientCooldowns().put(identifier, entry);
        }
    }

    /** Forgets {@code identifier} entirely, in both maps. */
    default void dropCooldown(String identifier) {
        getPersistedCooldowns().remove(identifier);
        getTransientCooldowns().remove(identifier);
    }
}
