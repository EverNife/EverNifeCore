package br.com.finalcraft.evernifecore.ignore;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link IIgnoreProvider} per {@link IgnoreKind}, each possibly owned by a different plugin.
 * A kind nobody implements answers {@link IIgnoreProvider#NOBODY}.
 */
public final class ECIgnores {

    private static final ECIgnores GLOBAL = new ECIgnores();

    private final Map<IgnoreKind, IIgnoreProvider> providers = new ConcurrentHashMap<>();

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

    public IIgnoreProvider getProvider(IgnoreKind kind) {
        return providers.getOrDefault(kind, IIgnoreProvider.NOBODY);
    }

    public boolean hasProvider(IgnoreKind kind) {
        return providers.containsKey(kind);
    }

    public void setProvider(IgnoreKind kind, IIgnoreProvider provider) {
        IIgnoreProvider previous = providers.put(kind, provider);
        EverNifeCore.getLog().info("[ECIgnores] {} is now answered by {}", kind, provider.getClass().getName());
        if (previous != null && previous != provider) {
            EverNifeCore.getLog().warning("[ECIgnores] {} replaced {} - two plugins claim the same ignore kind",
                    kind, previous.getClass().getName());
        }
    }

    /** Removes {@code provider} only if it still owns {@code kind}, so a disabling plugin never evicts its successor. */
    public boolean removeProvider(IgnoreKind kind, IIgnoreProvider provider) {
        return providers.remove(kind, provider);
    }
}
