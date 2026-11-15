package br.com.finalcraft.evernifecore.hytale.integration;

import br.com.finalcraft.evernifecore.api.common.player.IOfflinePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The permissions module answers by uuid from its in-memory grants (user and group), online or not: every player is
 * always loaded and a load has nothing to do. A node is "set" whenever it is granted, as {@code HytaleFPlayer} reads it.
 */
public final class HyOfflinePermissions implements IOfflinePermissions {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean isLoaded(UUID playerId) {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> load(UUID playerId) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public boolean holds(UUID playerId, String node) {
        return PermissionsModule.get().hasPermission(playerId, node);
    }
}
