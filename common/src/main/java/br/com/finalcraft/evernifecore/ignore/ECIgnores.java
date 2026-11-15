package br.com.finalcraft.evernifecore.ignore;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link IIgnoreProvider} per {@link IgnoreKind}, each owned by the plugin that set it and gone when that plugin
 * is released. A kind nobody implements answers {@link IIgnoreProvider#NOBODY}.
 */
public final class ECIgnores {

    private static final ECIgnores GLOBAL = new ECIgnores();

    private final Map<IgnoreKind, IIgnoreProvider> providers = new ConcurrentHashMap<>();
    private final Map<IgnoreKind, ECPluginData> owners = new ConcurrentHashMap<>();

    public static ECIgnores global() {
        return GLOBAL;
    }

    /** Whether {@code ignorer} hides {@code ignored} for this kind or any broader kind that covers it. */
    public boolean isIgnoring(UUID ignorer, UUID ignored, IgnoreKind kind) {
        if (ignorer.equals(ignored)) {
            return false;
        }
        for (IgnoreKind k = kind; k != null; k = k.getBroader()) {
            if (getProvider(k).isIgnoring(ignorer, ignored)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #isIgnoring} through each provider's {@link IIgnoreProvider#isIgnoringAsync}: the providers
     * are asked one after the other, completing {@code true} on the first that says so and {@code false}
     * only after every one in the chain answered. A provider that fails fails the future.
     */
    public CompletableFuture<Boolean> isIgnoringAsync(UUID ignorer, UUID ignored, IgnoreKind kind) {
        if (ignorer.equals(ignored)) {
            return CompletableFuture.completedFuture(false);
        }
        return askFrom(kind, ignorer, ignored);
    }

    private CompletableFuture<Boolean> askFrom(IgnoreKind kind, UUID ignorer, UUID ignored) {
        if (kind == null) {
            return CompletableFuture.completedFuture(false);
        }
        return getProvider(kind).isIgnoringAsync(ignorer, ignored)
                .thenCompose(ignoring -> ignoring
                        ? CompletableFuture.completedFuture(true)
                        : askFrom(kind.getBroader(), ignorer, ignored));
    }

    public IIgnoreProvider getProvider(IgnoreKind kind) {
        return providers.getOrDefault(kind, IIgnoreProvider.NOBODY);
    }

    public boolean hasProvider(IgnoreKind kind) {
        return providers.containsKey(kind);
    }

    /** Makes {@code provider}, owned by {@code owner}, the answer for {@code kind}; it leaves when {@code owner} is released. */
    public synchronized void setProvider(ECPluginData owner, IgnoreKind kind, IIgnoreProvider provider) {
        IIgnoreProvider previous = providers.put(kind, provider);
        owners.put(kind, owner);
        EverNifeCore.getLog().info("[ECIgnores] {} is now answered by {}", kind, provider.getClass().getName());
        if (previous != null && previous != provider) {
            EverNifeCore.getLog().warning("[ECIgnores] {} replaced {} - two plugins claim the same ignore kind",
                    kind, previous.getClass().getName());
        }
    }

    /**
     * Removes every kind {@code owner} still owns, so a plugin that was already replaced never evicts its successor.
     * Run by the core when the plugin is released.
     */
    public synchronized void unregisterAll(ECPluginData owner) {
        owners.entrySet().removeIf(entry -> {
            if (entry.getValue() != owner) {
                return false;
            }
            providers.remove(entry.getKey());
            return true;
        });
    }
}
