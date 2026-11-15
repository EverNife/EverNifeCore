package br.com.finalcraft.evernifecore.cooldown.player;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownBucket;
import br.com.finalcraft.evernifecore.cooldown.CooldownEntry;
import br.com.finalcraft.evernifecore.cooldown.CooldownRetention;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One player's cooldowns as they exist on THIS server only: a per-player row keyed by the player's
 * uuid, holding every cooldown that player has scoped to this server. The local quadrant of a
 * player cooldown - the default, and the common case.
 *
 * <p>Being a per-player section, it loads with the player and evicts a short grace after quit; the
 * whole point of the local scope is that an absence simply means "not on cooldown here", so a
 * stopped cooldown is dropped from the row outright (the persist filter of {@link CooldownBucket}
 * does exactly that). The network counterpart, where an absence is ambiguous, is
 * {@link PlayerCooldownsNetwork}.</p>
 */
public class PlayerCooldownsLocal extends PDSection implements CooldownBucket {

    /** The stored cooldowns, keyed by identifier. */
    private Map<String, CooldownEntry> cooldowns = new LinkedHashMap<>();

    /** The memory-only cooldowns (persist == false): never encoded, never conflict-copied. */
    @JsonIgnore
    private transient Map<String, CooldownEntry> transientCooldowns = new LinkedHashMap<>();

    public PlayerCooldownsLocal() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
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
     * Internal: a handle over this player's {@code identifier} counter under {@code policy}, routed to
     * this row - every mutation files the entry here and dirties the row. The seam of
     * {@link Cooldown#of(java.util.UUID, String, RepeatPolicy)}.
     */
    public Cooldown cooldown(String identifier, RepeatPolicy policy) {
        pruneExpired(ECClock.now().toEpochMilli());
        return new Cooldown(identifier, sharedEntry(identifier), policy, cooldown -> {
            fileCooldown(cooldown.getIdentifier(), cooldown.getEntry());
            markDirty();
        });
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
