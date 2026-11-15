package br.com.finalcraft.evernifecore.minecraft.integration;

import br.com.finalcraft.evernifecore.api.common.player.IOfflinePermissions;
import br.com.finalcraft.evernifecore.integration.LuckPermsIntegration;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit keeps no permission of a player who left: LuckPerms does, when it is enabled - in memory for a player online
 * or loaded a moment ago, from its storage otherwise. Without LuckPerms nothing is read. Every LuckPerms class is
 * touched only after the plugin is known to be enabled.
 */
public final class McOfflinePermissions implements IOfflinePermissions {

    @Override
    public boolean isSupported() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    @Override
    public boolean isLoaded(UUID playerId) {
        return isSupported() && LuckPermsIntegration.getLoadedUser(playerId) != null;
    }

    @Override
    public CompletableFuture<Boolean> load(UUID playerId) {
        if (!isSupported()) {
            return CompletableFuture.completedFuture(false);
        }
        return LuckPermsIntegration.loadUser(playerId).thenApply(user -> user != null);
    }

    @Override
    public boolean holds(UUID playerId, String node) {
        return isSupported() && LuckPermsIntegration.holdsLoaded(playerId, node);
    }
}
