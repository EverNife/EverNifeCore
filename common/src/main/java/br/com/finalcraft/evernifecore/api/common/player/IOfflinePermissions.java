package br.com.finalcraft.evernifecore.api.common.player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The permissions of a player who is not online, as the platform's permission store keeps them: LuckPerms on Bukkit,
 * the permissions module on Hytale. A store may keep only some players in memory; {@link #holds} reads what is in
 * memory and never blocks, {@link #load} brings a player in without blocking the caller. Any thread.
 */
public interface IOfflinePermissions {

    /** The answer of a platform that reads no offline permission: nothing is ever loaded, nothing is held. */
    IOfflinePermissions NONE = new IOfflinePermissions() {
        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public boolean isLoaded(UUID playerId) {
            return false;
        }

        @Override
        public CompletableFuture<Boolean> load(UUID playerId) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public boolean holds(UUID playerId, String node) {
            return false;
        }
    };

    /** Whether this server can read an offline player's permissions at all; on Bukkit, whether LuckPerms is enabled. */
    boolean isSupported();

    /** Whether {@code playerId}'s permissions are in memory now, so {@link #holds} answers for them. Never blocks. */
    boolean isLoaded(UUID playerId);

    /**
     * Brings {@code playerId}'s permissions into memory, off the calling thread; completes true once {@link #isLoaded}
     * would answer true, false when this server cannot read them. Never blocks.
     */
    CompletableFuture<Boolean> load(UUID playerId);

    /**
     * Whether {@code node} is set on {@code playerId} and granted - by the player, a group or a wildcard - read from
     * what is in memory; false when they are not loaded. Never blocks.
     */
    boolean holds(UUID playerId, String node);
}
