package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IPlayerData extends IDirtyable {

    PlayerData getPlayerData();

    String getName();

    UUID getUniqueId();

    boolean isPlayerOnline();

    FPlayer getPlayer();

    long getFirstSeen();

    long getLastSeen();

    long getLastSaved();

    /**
     * This player's LOCAL (this-server-only) cooldown for {@code identifier}, resolved through the
     * player's {@link PlayerCooldown} route. Async because the cooldown bucket may have to be read from
     * the backend when it is not cached (an offline target, or a cache miss); for an online player the
     * bucket is hot-loaded, so {@code .join()} is a cache hit and the safe pattern on the main thread.
     * For an offline target, bridge the result back with
     * {@link PlayerController#whenCompleteOnMainThread(CompletableFuture, java.util.function.BiConsumer)}
     * instead of blocking.
     */
    default CompletableFuture<PlayerCooldown> getCooldown(String identifier) {
        return PlayerCooldown.of(getUniqueId(), identifier);
    }

    /**
     * Resolves one of this player's PDSections, creating a TRANSIENT default when the backend has
     * nothing yet (seeded cache-only, no write - it persists only once {@code markDirty()} is called).
     * Async-only API: the returned future is already completed on the hot path (cached section); call
     * {@code .join()} for explicit synchronous access.
     *
     * <p>Per-online-player by design: it caches an instance for this player. Do NOT loop it over the
     * whole player base for an aggregate (that seeds a default per key) - use
     * {@link #getPDSectionIfPresent(Class)} for a bulk-safe presence read, or the controller's indexed
     * query API for a real aggregate.</p>
     */
    <T extends PDSection> CompletableFuture<T> getPDSection(Class<T> pdSectionClass);

    /**
     * Resolves one of this player's PDSections WITHOUT creating a default: completes with the stored
     * (or already-cached) section, or {@link java.util.Optional#empty()} on a true miss. Unlike
     * {@link #getPDSection(Class)} it never seeds a transient default, so it is the bulk-safe primitive
     * for "does this player have this section?" reads.
     */
    <T extends PDSection> CompletableFuture<Optional<T>> getPDSectionIfPresent(Class<T> pdSectionClass);

    /**
     * Whether this player has a stored (or cached) row for {@code pdSectionClass}. Async because there
     * is no synchronous existence check against the backend - the cache is consulted first, then the
     * backend on a miss. Use {@link #hasPDSectionIfLoaded(Class)} for a sync, cache-only peek.
     */
    CompletableFuture<Boolean> hasPDSection(Class<? extends PDSection> pdSectionClass);

    /**
     * Sync, cache-only: whether this player's {@code pdSectionClass} is currently loaded in memory.
     * Never touches storage - a {@code false} means "not loaded", not "does not exist".
     */
    boolean hasPDSectionIfLoaded(Class<? extends PDSection> pdSectionClass);

    /**
     * Returns one of this player's PDSections only if it is already loaded in memory, never
     * touching storage. Returns null when the section is not cached (or not registered).
     */
    <T extends PDSection> T getPDSectionIfLoaded(Class<T> pdSectionClass);

}
