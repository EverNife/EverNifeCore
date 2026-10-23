package br.com.finalcraft.evernifecore.consolefilter;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The one {@link IConsoleFilterProvider} the server's console asks, answering
 * {@link IConsoleFilterProvider#CORE_COMMANDS} until a plugin (FinalConsoleFilter) replaces it. The core
 * owns the hook into the logging stack; a provider only decides.
 *
 * <p>On Bukkit the hook is a Log4j filter on the root logger, installed with the core. Hytale installs no
 * hook yet, so a provider registered there is never asked.</p>
 */
public final class ECConsoleFilter {

    private static final ECConsoleFilter GLOBAL = new ECConsoleFilter();

    private final AtomicReference<IConsoleFilterProvider> provider = new AtomicReference<>(IConsoleFilterProvider.CORE_COMMANDS);

    public static ECConsoleFilter global() {
        return GLOBAL;
    }

    /**
     * Whether the console hides {@code message}. Never throws: a provider that fails lets the line
     * through, since a filter that breaks logging would hide every line after it.
     */
    public boolean isHidden(String message) {
        if (message == null) {
            return false;
        }
        try {
            return provider.get().isHidden(message);
        } catch (Throwable failure) {
            return false;
        }
    }

    public IConsoleFilterProvider getProvider() {
        return provider.get();
    }

    /** Whether a plugin replaced the core's own answer. */
    public boolean hasProvider() {
        return provider.get() != IConsoleFilterProvider.CORE_COMMANDS;
    }

    /**
     * Makes {@code newProvider} the console's answer. To keep the core's own lines hidden as well, it can
     * consult {@link IConsoleFilterProvider#CORE_COMMANDS} itself.
     */
    public void setProvider(IConsoleFilterProvider newProvider) {
        IConsoleFilterProvider previous = provider.getAndSet(newProvider);
        EverNifeCore.getLog().info("[ECConsoleFilter] The console is now filtered by {}", newProvider.getClass().getName());
        if (previous != IConsoleFilterProvider.CORE_COMMANDS && previous != newProvider) {
            EverNifeCore.getLog().warning("[ECConsoleFilter] {} replaced {} - two plugins claim the console filter",
                    newProvider.getClass().getName(), previous.getClass().getName());
        }
    }

    /**
     * Puts the core's own answer back, only if {@code ownProvider} is still the one registered, so a
     * disabling plugin never evicts its successor.
     */
    public boolean removeProvider(IConsoleFilterProvider ownProvider) {
        return ownProvider != IConsoleFilterProvider.CORE_COMMANDS
                && provider.compareAndSet(ownProvider, IConsoleFilterProvider.CORE_COMMANDS);
    }
}
