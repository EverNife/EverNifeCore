package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.playerdata.AccountSection;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One human's cooldowns as they exist across the WHOLE interconnected network: an account-keyed row,
 * shared by every identity linked into that account and read/written by every server, whatever its
 * platform. The network quadrant of a player cooldown - opt-in, for the cooldown that must not be
 * dodged by hopping servers. The local, this-server-only counterpart is {@link PlayerCooldownsLocal}.
 *
 * <p>Because the row is replicated, an absent entry does not mean "free": it means this replica has
 * no opinion, and a peer still holding an older start would win by omission. So a stop is stored as a
 * zeroed anchor rather than forgotten - see {@link #fileCooldown} - and the merge keeps the newer
 * fact ahead of a lagging peer.</p>
 */
public class PlayerCooldownsNetwork extends AccountSection<PlayerCooldownsNetwork> implements CooldownBucket {

    /** The stored cooldowns, keyed by identifier. */
    private Map<String, CooldownEntry> cooldowns = new LinkedHashMap<>();

    /** The memory-only cooldowns (persist == false, still running): never encoded nor merged. */
    @JsonIgnore
    private transient Map<String, CooldownEntry> transientCooldowns = new LinkedHashMap<>();

    public PlayerCooldownsNetwork() {
        //Jackson no-arg constructor - the framework attaches the accountId afterwards when seeding
    }

    @Override
    public Map<String, CooldownEntry> getPersistedCooldowns() {
        return cooldowns;
    }

    @Override
    public Map<String, CooldownEntry> getTransientCooldowns() {
        return transientCooldowns;
    }

    /**
     * Files a cooldown where its absence would not be misread. A persistent one is stored as usual;
     * a stop (a zeroed anchor) is stored too, not forgotten, so it travels to out-vote a peer that
     * still holds the old start. Only a running non-persistent cooldown - which by definition is not
     * meant to outlive this process - stays memory-only.
     */
    @Override
    public void fileCooldown(String identifier, CooldownEntry entry) {
        if (entry.isPersist() || entry.getTimeStart() == 0) {
            getTransientCooldowns().remove(identifier);
            getPersistedCooldowns().put(identifier, entry);
        } else {
            getPersistedCooldowns().remove(identifier);
            getTransientCooldowns().put(identifier, entry);
        }
    }

    /**
     * Combines this row with the replicas in {@code others}: each id settles on its
     * {@link CooldownEntry#latest} state. Pure (a fresh instance, no input mutated), and
     * order-independent because {@code latest} is a total order - which is what lets replicas across
     * the network converge no matter the merge order. Retention is a separate concern applied when the
     * row is read (see {@link #pruneExpired}), not folded into the combine.
     */
    @Override
    public PlayerCooldownsNetwork merge(List<PlayerCooldownsNetwork> others) {
        PlayerCooldownsNetwork merged = new PlayerCooldownsNetwork();
        merged.cooldowns.putAll(this.cooldowns);
        for (PlayerCooldownsNetwork other : others) {
            other.cooldowns.forEach((identifier, incoming) ->
                    merged.cooldowns.merge(identifier, incoming, CooldownEntry::latest));
        }
        return merged;
    }

    /**
     * A handle over {@code playerUuid}'s network cooldown {@code identifier}, routed to this row:
     * every mutation files the entry here and dirties the row. The seam the player-cooldown factory
     * binds to; the uuid comes from the caller, since the row itself is account-shared.
     */
    PlayerCooldown cooldown(UUID playerUuid, String identifier) {
        pruneExpired(System.currentTimeMillis());
        return new PlayerCooldown(identifier, playerUuid, this, this);
    }

    /** Drops every cooldown past its retention horizon and dirties the row when any went away. */
    void pruneExpired(long now) {
        boolean removed = CooldownRetention.prune(cooldowns, now);
        removed |= CooldownRetention.prune(transientCooldowns, now);
        if (removed) {
            markDirty();
        }
    }
}
