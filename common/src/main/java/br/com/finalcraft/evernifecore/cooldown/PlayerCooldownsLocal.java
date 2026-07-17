package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
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
     * A handle over this player's {@code identifier} cooldown, routed to this row: every mutation
     * files the entry here and dirties the row. The seam the player-cooldown factory binds to.
     */
    PlayerCooldown cooldown(String identifier) {
        pruneExpired(System.currentTimeMillis());
        return new PlayerCooldown(identifier, getUniqueId(), this, this);
    }

    /** Drops every cooldown past its retention horizon and dirties the row when any went away. */
    void pruneExpired(long now) {
        boolean removed = CooldownRetention.prune(cooldowns, now);
        removed |= CooldownRetention.prune(transientCooldowns, now);
        if (removed) {
            markDirty();
        }
    }

    /**
     * Builds a player's local cooldown row out of a legacy v3 {@code Cooldown:} block - the adapter
     * behind the {@code legacyYaml("Cooldown", ...)} registration that lets the first-boot import claim
     * the block instead of leaving it forever pending. Each child key holds an {@code identifier}
     * (optional, falling back to the key itself), a {@code timeStart} and a {@code timeDuration}; only
     * persistent cooldowns were ever written to the v3 file, and the mutation clock did not exist, so
     * {@code updatedAt} is the start.
     */
    public static PlayerCooldownsLocal fromLegacyYaml(ConfigSection cooldownBlock) {
        PlayerCooldownsLocal section = new PlayerCooldownsLocal();
        for (String key : cooldownBlock.getKeys()) {
            String identifier = cooldownBlock.getString(key + ".identifier", key);
            long timeStart = cooldownBlock.getLong(key + ".timeStart", 0L);
            long timeDuration = cooldownBlock.getLong(key + ".timeDuration", 0L);
            section.cooldowns.put(identifier, new CooldownEntry(timeStart, timeDuration, timeStart, true));
        }
        return section;
    }
}
