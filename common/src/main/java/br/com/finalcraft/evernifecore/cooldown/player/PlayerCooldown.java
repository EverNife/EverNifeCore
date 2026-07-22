package br.com.finalcraft.evernifecore.cooldown.player;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownBucket;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A cooldown owned by one player, plus the shortcuts to reach that player.
 *
 * <p>Built over a {@link CooldownBucket} it is a real route: every mutation files the entry into the
 * bucket and dirties the row behind it, so the change is stored and, on a replicated bucket, seen by
 * the merge. Built without one it is MEMORY-ONLY - nothing survives the object and {@code persist}
 * buys nothing.</p>
 */
public class PlayerCooldown extends Cooldown {

    /** The player this cooldown belongs to - runtime wiring, never part of the stored value. */
    @JsonIgnore
    private transient UUID uuid;

    /** The bucket this cooldown is filed in; null makes the handle memory-only. */
    @JsonIgnore
    private transient CooldownBucket bucket;

    /** The row to dirty when this cooldown changes; null when there is no row behind it. */
    @JsonIgnore
    private transient IDirtyable ownerRow;

    public PlayerCooldown(String identifier, UUID uuid) {
        super(identifier);
        this.uuid = uuid;
    }

    /** Takes over another cooldown's state (a copy of it) as {@code uuid}'s own, memory-only. */
    public PlayerCooldown(Cooldown cooldown, UUID uuid) {
        super(cooldown.getIdentifier(), cooldown.getEntry().copy());
        this.uuid = uuid;
    }

    /**
     * A handle over the entry filed under {@code identifier} in {@code bucket}: every mutation files
     * it back and dirties {@code ownerRow}. The seam a bucket-backed player-cooldown route is built on.
     */
    PlayerCooldown(String identifier, UUID uuid, CooldownBucket bucket, IDirtyable ownerRow) {
        super(identifier, bucket.resolveCooldown(identifier));
        this.uuid = uuid;
        this.bucket = bucket;
        this.ownerRow = ownerRow;
    }

    public PlayerData getPlayerData() {
        return PlayerController.getLoaded(uuid);
    }

    /**
     * Keeps the bucket in agreement with this cooldown: the bucket decides which map the entry belongs
     * in (a stop lands in the stored map on a replicated bucket, is forgotten on a local one), then the
     * row is dirtied so the change is flushed. A memory-only handle has no bucket and does nothing.
     */
    @Override
    protected void onMutated() {
        if (bucket == null) {
            return;
        }
        bucket.fileCooldown(getIdentifier(), getEntry());
        if (ownerRow != null) {
            ownerRow.markDirty();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Factories (the call-site API - the reach is chosen here, not in any config)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * {@code uuid}'s LOCAL (this-server-only) cooldown {@code identifier}: a handle over the player's
     * {@link PlayerCooldownsLocal} row. Async because the row may have to be read from the backend on a
     * cache miss; {@code .join()} is a cache hit for an online player (the row is hot-loaded at login).
     *
     * <p>Like the pre-rewrite player cooldown it is NOT persistent by default - call
     * {@code .setPersist(true)} to make it outlive a restart. A cooldown never started stays free, and
     * merely resolving one never grows the row.</p>
     */
    public static CompletableFuture<PlayerCooldown> of(UUID uuid, String identifier) {
        return PlayerController.getPDSection(uuid, PlayerCooldownsLocal.class)
                .thenApply(section -> section.cooldown(identifier));
    }

    /**
     * {@code uuid}'s NETWORK-wide cooldown {@code identifier}: a handle over the human's account-shared
     * {@link PlayerCooldownsNetwork} row, seen by every server of the network. Async, same as
     * {@link #of(UUID, String)}.
     *
     * <p>Born PERSISTENT, unlike the local one: a network cooldown only means anything if it replicates,
     * and the route to storage is gated on the entry being persistent - a non-persistent one would
     * silently never propagate. The flag is set on the entry directly (not through {@code setPersist},
     * which would file a still-blank entry), so the row still only grows once the cooldown is actually
     * started, never on a bare read.</p>
     */
    public static CompletableFuture<PlayerCooldown> network(UUID uuid, String identifier) {
        return PlayerController.getAccountSection(uuid, PlayerCooldownsNetwork.class)
                .thenApply(section -> {
                    PlayerCooldown handle = section.cooldown(uuid, identifier);
                    handle.markBornPersistent();
                    return handle;
                });
    }
}
