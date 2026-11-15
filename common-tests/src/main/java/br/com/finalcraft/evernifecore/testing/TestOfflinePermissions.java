package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.IOfflinePermissions;
import br.com.finalcraft.evernifecore.time.BlockingJoinWarning;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The permission store {@link Platforms#offlinePermissions()} puts behind {@code getOfflinePermissions}, shaped like
 * LuckPerms: {@link #grant} writes nodes to the store, a player is read only once loaded, and {@link #load} loads on
 * another thread, a little later - so a {@code join()} on it from the server thread is a wait the core warns about.
 */
public final class TestOfflinePermissions implements IOfflinePermissions {

    private static final long LOAD_MILLIS = 50;

    private final Map<UUID, Set<String>> stored = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    /** Stores {@code nodes} on {@code playerId}; they count once the player is loaded. */
    public TestOfflinePermissions grant(UUID playerId, String... nodes) {
        stored.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).addAll(Arrays.asList(nodes));
        return this;
    }

    /** Puts {@code playerId} in memory at once, as a store does for a player who just left. */
    public TestOfflinePermissions preload(UUID playerId) {
        loaded.add(playerId);
        return this;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean isLoaded(UUID playerId) {
        return loaded.contains(playerId);
    }

    @Override
    public CompletableFuture<Boolean> load(UUID playerId) {
        return BlockingJoinWarning.watch(CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(LOAD_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            loaded.add(playerId);
            return true;
        }));
    }

    @Override
    public boolean holds(UUID playerId, String node) {
        return loaded.contains(playerId) && stored.getOrDefault(playerId, Collections.<String>emptySet()).contains(node);
    }

    /** Forgets every node and every loaded player. */
    public void reset() {
        stored.clear();
        loaded.clear();
    }
}
